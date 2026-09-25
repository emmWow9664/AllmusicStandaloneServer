package com.example.standalone.gui;

import com.coloryr.allmusic.server.core.music.PlayMusic;
import com.example.standalone.ClientSession;
import com.example.standalone.Main;
import com.example.standalone.SideStandalone;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 统计存储：点歌历史与玩家统计。
 * <p>
 * 记录保存在服务端所在文件夹 stats.json，重新打开后可恢复。
 */
public final class StatsManager {
    public static class SongRecord {
        public String name;
        public String player;
        public long time;
        public String id;

        public SongRecord() {
        }

        public SongRecord(String name, String player, long time, String id) {
            this.name = name;
            this.player = player;
            this.time = time;
            this.id = id;
        }
    }

    public static class PlayerRecord {
        public String name;
        public long firstConnect;
        public int songCount;
        /** 累计连接时长（毫秒） */
        public long totalConnectMs;

        public PlayerRecord() {
        }

        public PlayerRecord(String name, long firstConnect) {
            this.name = name;
            this.firstConnect = firstConnect;
        }
    }

    private static final List<SongRecord> songs = Collections.synchronizedList(new ArrayList<>());
    private static final Map<String, PlayerRecord> players = Collections.synchronizedMap(new LinkedHashMap<>());
    /** 今日已连接的玩家（小写名），跨天自动清零 */
    private static final java.util.Set<String> todayPlayers = Collections.synchronizedSet(new java.util.LinkedHashSet<>());
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** 今日日期（ISO，如 2026-09-25） */
    private static volatile String todayDate = today();
    /** 点歌去重：记录上次已入库的歌曲 id，仅当歌曲变化时记录一次 */
    private static volatile String lastRecordedSong = null;
    /** 统计线程是否已启动（防止重复启动） */
    private static volatile boolean tickerStarted = false;

    private StatsManager() {
    }

    /**
     * 启动每秒统计线程（守护线程，GUI / 控制台模式都会运行）。
     * <p>
     * 每秒记录新点歌（按歌曲 id 去重）与在线玩家的连接时长累计，
     * 使控制台模式下统计同样有效。
     */
    public static void startTicker() {
        if (tickerStarted) {
            return;
        }
        tickerStarted = true;
        Thread thread = new Thread(StatsManager::tickLoop, "amc-stats");
        thread.setDaemon(true);
        thread.start();
    }

    private static void tickLoop() {
        long lastTick = System.currentTimeMillis();
        boolean logged = false;
        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return;
            }
            try {
                long nowMs = System.currentTimeMillis();
                long delta = nowMs - lastTick;
                lastTick = nowMs;

                // 新点歌：仅当当前歌曲变化时记录一次，避免重复计数
                var now = PlayMusic.nowPlayMusic;
                if (now == null || now.getId() == null) {
                    lastRecordedSong = null;
                } else if (!now.getId().equals(lastRecordedSong)) {
                    recordSong(now.getName(), now.getCall(), now.getId());
                    lastRecordedSong = now.getId();
                }

                // 在线玩家：记录首次连接 + 累计本次 tick 的真实间隔
                for (ClientSession c : SideStandalone.INSTANCE.getClientSessions()) {
                    recordPlayer(c.getName());
                    addConnectTime(c.getName(), delta);
                }
            } catch (Throwable t) {
                // 任何异常都不能让统计线程退出，仅打印一次日志
                if (!logged) {
                    logged = true;
                    try {
                        if (com.coloryr.allmusic.server.core.AllMusic.log != null) {
                            com.coloryr.allmusic.server.core.AllMusic.log
                                    .data("<light_purple>[AllMusic]<red>统计线程发生错误：" + t);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
    }

    static {
        // 定期自动保存（后台守护线程）
        ScheduledExecutorService svc = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "stats-save");
            t.setDaemon(true);
            return t;
        });
        svc.scheduleAtFixedRate(StatsManager::save, 60, 60, TimeUnit.SECONDS);
    }

    /** 记录一次点歌（入队）事件 */
    public static void recordSong(String name, String player, String id) {
        songs.add(0, new SongRecord(name == null ? "" : name, player == null ? "" : player,
                System.currentTimeMillis(), id == null ? "" : id));
        if (songs.size() > 500) {
            synchronized (songs) {
                if (songs.size() > 500) {
                    songs.remove(songs.size() - 1);
                }
            }
        }
        // 去重键用小写（同一玩家不区分大小写视为一人），展示名保留原始大小写
        String key = player == null ? "" : player.toLowerCase();
        PlayerRecord p;
        synchronized (players) {
            p = players.computeIfAbsent(key, k -> new PlayerRecord(player == null ? "" : player, System.currentTimeMillis()));
        }
        p.songCount++;
    }

    /** 记录玩家连接（保留原始大小写显示），并计入今日连接名单 */
    public static void recordPlayer(String player) {
        if (player == null) {
            return;
        }
        ensureToday();
        String key = player.toLowerCase();
        synchronized (players) {
            players.computeIfAbsent(key, k -> new PlayerRecord(player, System.currentTimeMillis()));
        }
        todayPlayers.add(key);
    }

    /** 今日连接过的玩家数量 */
    public static int getTodayPlayerCount() {
        ensureToday();
        return todayPlayers.size();
    }

    /** 今日连接过的玩家名（小写） */
    public static List<String> getTodayPlayers() {
        ensureToday();
        synchronized (todayPlayers) {
            return new ArrayList<>(todayPlayers);
        }
    }

    private static String today() {
        return java.time.LocalDate.now().toString();
    }

    /** 跨天则清空今日名单 */
    private static void ensureToday() {
        String now = today();
        if (!now.equals(todayDate)) {
            todayDate = now;
            todayPlayers.clear();
        }
    }

    /** 累计在线时长（由每秒统计线程调用） */
    public static void addConnectTime(String player, long deltaMs) {
        if (player == null || deltaMs <= 0) {
            return;
        }
        PlayerRecord p;
        synchronized (players) {
            p = players.get(player.toLowerCase());
        }
        if (p != null) {
            p.totalConnectMs += deltaMs;
        }
    }

    public static List<SongRecord> getSongs() {
        synchronized (songs) {
            return new ArrayList<>(songs);
        }
    }

    public static List<PlayerRecord> getPlayers() {
        synchronized (players) {
            return new ArrayList<>(players.values());
        }
    }

    public static PlayerRecord getPlayer(String name) {
        if (name == null) {
            return null;
        }
        return players.get(name.toLowerCase());
    }

    // ---------- 持久化 ----------

    private static Path statsFile() {
        return Paths.get(new java.io.File(Main.getBaseDir(), "stats.json").getAbsolutePath());
    }

    public static void save() {
        try {
            List<SongRecord> s;
            List<PlayerRecord> p;
            synchronized (songs) {
                s = new ArrayList<>(songs);
            }
            synchronized (players) {
                p = new ArrayList<>(players.values());
            }
            Store store = new Store();
            store.songs = s;
            store.players = p;
            store.todayDate = todayDate;
            store.todayPlayers = getTodayPlayers();
            Files.write(statsFile(), GSON.toJson(store).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    public static void load() {
        try {
            Path f = statsFile();
            if (!Files.exists(f)) {
                return;
            }
            Store store = GSON.fromJson(new String(Files.readAllBytes(f), StandardCharsets.UTF_8), Store.class);
            if (store == null) {
                return;
            }
            synchronized (songs) {
                songs.clear();
                if (store.songs != null) {
                    songs.addAll(store.songs);
                }
            }
            synchronized (players) {
                players.clear();
                if (store.players != null) {
                    for (PlayerRecord pr : store.players) {
                        if (pr != null && pr.name != null) {
                            players.put(pr.name.toLowerCase(), pr);
                        }
                    }
                }
            }
            // 同一天重启时恢复今日连接名单，跨天则丢弃
            if (today().equals(store.todayDate) && store.todayPlayers != null) {
                todayPlayers.clear();
                todayPlayers.addAll(store.todayPlayers);
            }
        } catch (Exception ignored) {
        }
    }

    /** Gson 序列化容器 */
    private static class Store {
        List<SongRecord> songs;
        List<PlayerRecord> players;
        String todayDate;
        List<String> todayPlayers;
    }
}
