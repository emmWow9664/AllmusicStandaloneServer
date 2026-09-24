package com.example.standalone.web;

import com.coloryr.allmusic.server.core.AllMusic;
import com.google.gson.GsonBuilder;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Web 管理员凭据与登录会话。
 * <p>
 * 密码只以 PBKDF2 加盐哈希保存在数据目录的 web.json 中（明文既不落盘也不写进源码），
 * 校验使用常量时间比较；登录成功后发放随机 token，配合 Authorization: Bearer 使用。
 */
public final class WebAuth {
    public static final int OK = 200;
    public static final int DENIED = 401;
    public static final int NO_PASSWORD = 403;
    public static final int LOCKED = 429;

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int DEFAULT_ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    /** 会话绝对有效期 */
    private static final long SESSION_ABSOLUTE_MS = 12L * 60 * 60 * 1000;
    /** 会话闲置超时 */
    private static final long SESSION_IDLE_MS = 30L * 60 * 1000;
    /** 同一 IP 连续失败次数上限与锁定时长 */
    private static final int MAX_FAILURES = 5;
    private static final long LOCK_MS = 5L * 60 * 1000;
    /** 登录失败固定延时，降低爆破速率 */
    private static final long FAIL_DELAY_MS = 300;

    /** web.json 结构：只有盐与哈希，不含明文 */
    private static final class Store {
        int version = 1;
        String algorithm = ALGORITHM;
        int iterations = DEFAULT_ITERATIONS;
        String salt;
        String hash;
        long updatedAt;
    }

    private static final class Session {
        final long createdAt = System.currentTimeMillis();
        volatile long lastSeen = System.currentTimeMillis();
    }

    private static final class Attempt {
        int count;
        long lockedUntil;
    }

    private final File file;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();
    private volatile Store store;

    public WebAuth(File dataDir) {
        this.file = new File(dataDir, "web.json");
        load();
    }

    /** 是否已设置管理员密码 */
    public boolean hasPassword() {
        Store s = store;
        return s != null && s.hash != null && s.salt != null;
    }

    /**
     * 设置管理员密码（仅保存哈希），并作废已有会话
     */
    public synchronized void setPassword(char[] password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        Store s = new Store();
        s.salt = Base64.getEncoder().encodeToString(salt);
        s.hash = Base64.getEncoder().encodeToString(derive(password, salt, DEFAULT_ITERATIONS));
        s.updatedAt = System.currentTimeMillis();
        store = s;
        sessions.clear();
        save();
    }

    /**
     * 清除管理员密码（Web 管理功能随之关闭）
     */
    public synchronized void clearPassword() {
        store = null;
        sessions.clear();
        try {
            Files.deleteIfExists(file.toPath());
        } catch (Exception ignored) {
        }
    }

    /**
     * 登录校验
     *
     * @return {@link #OK} 成功（token 写入 out[0]）、{@link #DENIED}、{@link #NO_PASSWORD}、{@link #LOCKED}
     */
    public int login(String password, String ip, String[] out) {
        if (!hasPassword()) {
            return NO_PASSWORD;
        }
        if (isLocked(ip)) {
            return LOCKED;
        }
        boolean ok = password != null && verify(password);
        if (!ok) {
            recordFailure(ip);
            sleep(FAIL_DELAY_MS);
            log("<light_purple>[AllMusic]<red>Web 管理员登录失败（来自 " + safeIp(ip) + "）");
            return DENIED;
        }
        recordSuccess(ip);
        String token = newToken();
        sessions.put(token, new Session());
        if (out != null && out.length > 0) {
            out[0] = token;
        }
        log("<light_purple>[AllMusic]<yellow>Web 管理员登录成功（来自 " + safeIp(ip) + "）");
        return OK;
    }

    /**
     * 校验 token（同时刷新闲置时间）
     */
    public boolean valid(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        Session session = sessions.get(token);
        if (session == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - session.createdAt > SESSION_ABSOLUTE_MS || now - session.lastSeen > SESSION_IDLE_MS) {
            sessions.remove(token);
            return false;
        }
        session.lastSeen = now;
        cleanSessions(now);
        return true;
    }

    public void logout(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    /** 该 IP 是否处于登录失败锁定中 */
    public boolean isLocked(String ip) {
        Attempt a = attempts.get(key(ip));
        return a != null && a.lockedUntil > System.currentTimeMillis();
    }

    // ---------- 内部实现 ----------

    private void load() {
        try {
            if (!file.exists()) {
                return;
            }
            Store s = new com.google.gson.Gson().fromJson(
                    new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8), Store.class);
            if (s != null && s.hash != null && s.salt != null) {
                store = s;
            }
        } catch (Exception e) {
            log("<light_purple>[AllMusic]<red>Web 凭据文件读取失败：" + e.getMessage());
        }
    }

    private synchronized void save() {
        try {
            File dir = file.getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(store);
            Files.write(file.toPath(), json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log("<light_purple>[AllMusic]<red>Web 凭据文件保存失败：" + e.getMessage());
        }
    }

    private boolean verify(String password) {
        Store s = store;
        byte[] salt;
        byte[] expected;
        try {
            salt = Base64.getDecoder().decode(s.salt);
            expected = Base64.getDecoder().decode(s.hash);
        } catch (Exception e) {
            return false;
        }
        int iterations = s.iterations <= 0 ? DEFAULT_ITERATIONS : s.iterations;
        byte[] actual = derive(password.toCharArray(), salt, iterations);
        return MessageDigest.isEqual(expected, actual);
    }

    private byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (Exception e) {
            return new byte[0];
        } finally {
            spec.clearPassword();
            Arrays.fill(password, '\0');
        }
    }

    private String newToken() {
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private void recordFailure(String ip) {
        Attempt a = attempts.computeIfAbsent(key(ip), k -> new Attempt());
        synchronized (a) {
            a.count++;
            if (a.count >= MAX_FAILURES) {
                a.lockedUntil = System.currentTimeMillis() + LOCK_MS;
                a.count = 0;
                log("<light_purple>[AllMusic]<red>Web 管理员登录失败次数过多，已锁定 5 分钟（来自 "
                        + safeIp(ip) + "）");
            }
        }
    }

    private void recordSuccess(String ip) {
        attempts.remove(key(ip));
    }

    private void cleanSessions(long now) {
        if (sessions.size() < 2) {
            return;
        }
        sessions.entrySet().removeIf(e -> {
            Session s = e.getValue();
            return now - s.createdAt > SESSION_ABSOLUTE_MS || now - s.lastSeen > SESSION_IDLE_MS;
        });
    }

    private static String key(String ip) {
        return ip == null ? "" : ip;
    }

    private static String safeIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return "未知地址";
        }
        return ip.replaceAll("[^0-9a-fA-F:.]", "");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void log(String message) {
        try {
            AllMusic.log.data(message);
        } catch (Exception ignored) {
        }
    }
}