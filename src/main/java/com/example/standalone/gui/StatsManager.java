package com.example.standalone.gui;

import com.example.standalone.Main;
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

    private StatsManager() {
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

    /** 累计在线时长（由界面刷新周期调用） */
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
