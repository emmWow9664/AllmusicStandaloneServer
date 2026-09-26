package com.example.standalone.web;

import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.music.LyricSave;
import com.coloryr.allmusic.server.core.music.PlayMusic;
import com.coloryr.allmusic.server.core.objs.music.LyricItemObj;
import com.coloryr.allmusic.server.core.objs.music.SongInfoObj;
import com.coloryr.allmusic.server.core.saves.BanSave;
import com.example.standalone.ClientSession;
import com.example.standalone.ConfigCatalog;
import com.example.standalone.LogStandalone;
import com.example.standalone.Main;
import com.example.standalone.SideStandalone;
import com.example.standalone.monitor.PerfReader;
import com.example.standalone.monitor.StatsManager;
import com.example.standalone.monitor.SysInfo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Web 面板的数据接口：只读数据全部取自服务端核心的现有快照方法。
 */
final class WebApi {
    /** 日志环形缓冲上限 */
    private static final int LOG_LIMIT = 500;

    private static final Deque<LogLine> LOGS = new ArrayDeque<>();
    private static final AtomicLong LOG_SEQ = new AtomicLong();
    private static volatile Consumer<String> logListener;

    /** 各核心使用率缓存时长（避免频繁起子进程） */
    private static final long CORES_TTL_MS = 3000;
    private static final Object CORES_LOCK = new Object();
    private static final Object NET_LOCK = new Object();
    private static volatile String cpuModelCache;
    private static volatile String ramModelCache;
    private static volatile double[] coresCache;
    private static volatile long coresAt;
    private static long lastRx;
    private static long lastTx;
    private static long lastNetTime;

    private WebApi() {
    }

    /** 当前播放状态 */
    static Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        SongInfoObj now = PlayMusic.nowPlayMusic;
        boolean playing = now != null && !now.isNull();
        result.put("playing", playing);
        if (playing) {
            Map<String, Object> song = new LinkedHashMap<>();
            song.put("id", text(now.getId()));
            song.put("name", text(now.getName()));
            song.put("author", text(now.getAuthor()));
            song.put("album", text(now.getAl()));
            song.put("alia", text(now.getAlia()));
            song.put("player", text(now.getCall()));
            song.put("picUrl", text(now.getPicUrl()));
            song.put("length", now.getLength());
            result.put("song", song);
            result.put("nowTime", PlayMusic.musicNowTime);
            result.put("allTime", PlayMusic.musicAllTime);
        } else {
            result.put("song", null);
            result.put("nowTime", 0);
            result.put("allTime", 0);
        }
        result.put("queueSize", PlayMusic.getListSize());
        result.put("playerCount", SideStandalone.INSTANCE.getClientSessions().size());
        result.put("api", defaultApi());
        result.put("version", version());
        return result;
    }

    /** 歌曲队列 */
    static Map<String, Object> queue() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<SongInfoObj> list = PlayMusic.getList();
        String api = defaultApi();
        List<Map<String, Object>> items = new ArrayList<>();
        int index = 1;
        for (SongInfoObj song : list) {
            if (song == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("index", index++);
            item.put("id", text(song.getId()));
            item.put("name", text(song.getName()));
            item.put("author", text(song.getAuthor()));
            item.put("player", text(song.getCall()));
            item.put("length", song.getLength());
            item.put("banned", isMusicBanned(song.getId(), api));
            items.add(item);
        }
        result.put("total", items.size());
        result.put("items", items);
        return result;
    }

    /** 在线玩家 */
    static Map<String, Object> players() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (ClientSession session : SideStandalone.INSTANCE.getClientSessions()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", text(session.getName()));
            item.put("connectMs", Math.max(0, now - session.getConnectTime()));
            item.put("banned", isPlayerBanned(session.getName()));
            items.add(item);
        }
        result.put("total", items.size());
        result.put("todayPlayers", StatsManager.getTodayPlayerCount());
        result.put("items", items);
        return result;
    }

    /** 点歌统计：最近记录 + 热门排行 + 玩家排行 */
    static Map<String, Object> stats(int limit) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<StatsManager.SongRecord> songs = StatsManager.getSongs();

        Map<String, Integer> counter = new LinkedHashMap<>();
        for (StatsManager.SongRecord song : songs) {
            counter.merge(text(song.name), 1, Integer::sum);
        }

        List<Map<String, Object>> recent = new ArrayList<>();
        for (StatsManager.SongRecord song : songs) {
            if (recent.size() >= limit) {
                break;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", text(song.name));
            item.put("player", text(song.player));
            item.put("time", song.time);
            item.put("id", text(song.id));
            recent.add(item);
        }

        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counter.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        List<Map<String, Object>> top = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : sorted) {
            if (top.size() >= limit) {
                break;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", entry.getKey());
            item.put("count", entry.getValue());
            top.add(item);
        }

        List<Map<String, Object>> playerList = new ArrayList<>();
        for (StatsManager.PlayerRecord record : StatsManager.getPlayers()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", text(record.name));
            item.put("songCount", record.songCount);
            item.put("totalConnectMs", record.totalConnectMs);
            item.put("firstConnect", record.firstConnect);
            playerList.add(item);
        }
        playerList.sort((a, b) -> Integer.compare((Integer) b.get("songCount"), (Integer) a.get("songCount")));

        result.put("songs", recent);
        result.put("top", top);
        result.put("players", playerList);
        return result;
    }

    /** 性能监视：CPU / 内存 / 网络速率 + 型号与各核心使用率 */
    static Map<String, Object> perf() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cpuPercent", round(PerfReader.cpuPercent()));
        double ram = PerfReader.ramPercent();
        result.put("ramPercent", round(ram));
        result.put("ramUsedGb", round(PerfReader.usedGb()));
        result.put("ramTotalGb", round(PerfReader.totalGb()));

        // 网络速率需要与上一次调用做差分
        long rx = PerfReader.rxBytes();
        long tx = PerfReader.txBytes();
        long now = System.currentTimeMillis();
        synchronized (NET_LOCK) {
            if (lastNetTime > 0 && now > lastNetTime) {
                double seconds = (now - lastNetTime) / 1000.0;
                result.put("netRxKbps", round(Math.max(0, rx - lastRx) / 1024.0 / seconds));
                result.put("netTxKbps", round(Math.max(0, tx - lastTx) / 1024.0 / seconds));
            } else {
                result.put("netRxKbps", 0.0);
                result.put("netTxKbps", 0.0);
            }
            lastRx = rx;
            lastTx = tx;
            lastNetTime = now;
        }

        result.put("cpuModel", cpuModel());
        result.put("ramModel", ramModel());
        result.put("coreCount", Runtime.getRuntime().availableProcessors());
        double[] cores = coreLoads();
        if (cores == null) {
            result.put("cores", null);
        } else {
            List<Double> list = new ArrayList<>(cores.length);
            for (double core : cores) {
                list.add(round(core));
            }
            result.put("cores", list);
        }
        return result;
    }

    /**
     * 当前歌词：上一句 / 当前 / 下一句（供仪表盘做滚动歌词）
     * <p>
     * 取的是服务端已经解析好的歌词表（{@link LyricSave}），按当前行的时间键在有序键表里取前后各一句，
     * 因此和客户端 HUD 上显示的歌词是同一份数据。
     */
    static Map<String, Object> lyric() {
        Map<String, Object> result = new LinkedHashMap<>();
        SongInfoObj song = PlayMusic.nowPlayMusic;
        boolean playing = song != null && !song.isNull();
        result.put("playing", playing);

        String prev = "";
        String cur = "";
        String next = "";
        String tlyric = "";
        LyricSave save = PlayMusic.lyric;
        LyricItemObj now = save == null ? null : save.getNow();
        if (now != null) {
            cur = text(now.lyric);
            tlyric = text(now.tlyric);
            Map<Long, LyricItemObj> map = save.getLyricMap();
            if (map != null && !map.isEmpty()) {
                List<Long> keys = new ArrayList<>(map.keySet());
                keys.sort(Long::compareTo);
                int index = keys.indexOf(save.getNowKey());
                if (index > 0) {
                    prev = text(map.get(keys.get(index - 1)).lyric);
                }
                if (index >= 0 && index < keys.size() - 1) {
                    next = text(map.get(keys.get(index + 1)).lyric);
                }
            }
        }
        result.put("prev", prev);
        result.put("cur", cur);
        result.put("next", next);
        result.put("tlyric", tlyric);
        result.put("nowTime", PlayMusic.musicNowTime);
        return result;
    }

    /**
     * 全部可配置项（分组 + 当前值），供 Web 设置页读取
     */
    static Map<String, Object> config() {
        List<Map<String, Object>> groups = new ArrayList<>();
        for (ConfigCatalog.Group group : ConfigCatalog.groups()) {
            Map<String, Object> groupData = new LinkedHashMap<>();
            groupData.put("title", group.title);
            List<Map<String, Object>> items = new ArrayList<>();
            for (ConfigCatalog.Item entry : group.items) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("path", entry.path);
                item.put("cn", entry.cn);
                item.put("en", entry.en);
                item.put("desc", entry.desc);
                item.put("type", entry.type);
                Object value = ConfigCatalog.read(entry.path);
                item.put("value", value == null ? "" : String.valueOf(value));
                // 独立服务端自身的配置（端口、绑定地址、Web 开关等）改动后需重启进程才生效
                item.put("restart", entry.path.startsWith("standalone."));
                items.add(item);
            }
            groupData.put("items", items);
            groups.add(groupData);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("groups", groups);
        return result;
    }

    /** 封禁快照 */
    static Map<String, Object> bans() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> players = new ArrayList<>();
        try {
            players.addAll(BanSave.getBanPlayers());
        } catch (Exception ignored) {
        }
        players.sort(String::compareToIgnoreCase);
        result.put("players", players);
        result.put("musics", BanSave.snapshotBanMusics());
        return result;
    }

    // ---------- 日志缓冲 ----------

    /** 开始收集日志（会回放已有历史日志，正好用于填充缓冲） */
    static synchronized void startLogCapture() {
        if (logListener != null) {
            return;
        }
        Consumer<String> listener = WebApi::onLog;
        logListener = listener;
        LogStandalone.INSTANCE.addListener(listener);
    }

    static synchronized void stopLogCapture() {
        Consumer<String> listener = logListener;
        if (listener != null) {
            LogStandalone.INSTANCE.removeListener(listener);
            logListener = null;
        }
        synchronized (LOGS) {
            LOGS.clear();
        }
    }

    /**
     * 增量获取日志（去掉 MiniMessage 颜色标签，仅作纯文本展示）
     */
    static Map<String, Object> logs(long since, int limit) {
        List<Map<String, Object>> lines = new ArrayList<>();
        long next = since;
        synchronized (LOGS) {
            Deque<LogLine> tail = new ArrayDeque<>();
            for (LogLine line : LOGS) {
                if (line.seq > since) {
                    tail.addLast(line);
                    if (tail.size() > limit) {
                        tail.removeFirst();
                    }
                }
            }
            for (LogLine line : tail) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("seq", line.seq);
                item.put("text", line.text);
                lines.add(item);
                next = Math.max(next, line.seq);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nextSeq", next);
        result.put("lines", lines);
        return result;
    }

    static void clearLogs() {
        synchronized (LOGS) {
            LOGS.clear();
        }
    }

    private static void onLog(String line) {
        String plain = stripTags(line);
        synchronized (LOGS) {
            LOGS.addLast(new LogLine(LOG_SEQ.incrementAndGet(), plain));
            while (LOGS.size() > LOG_LIMIT) {
                LOGS.removeFirst();
            }
        }
    }

    private static String stripTags(String line) {
        if (line == null) {
            return "";
        }
        return line.replaceAll("<[^<>]{1,40}>", "");
    }

    // ---------- 小工具 ----------

    /** 四舍五入到一位小数 */
    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }

    /** CPU 型号：取一次即可，缓存复用 */
    private static String cpuModel() {
        String cached = cpuModelCache;
        if (cached == null) {
            cached = SysInfo.cpuModel();
            cpuModelCache = cached;
        }
        return cached;
    }

    /** 内存型号：Windows 下会起 PowerShell 子进程，必须缓存 */
    private static String ramModel() {
        String cached = ramModelCache;
        if (cached == null) {
            cached = SysInfo.ramModel();
            ramModelCache = cached;
        }
        return cached;
    }

    /**
     * 各核心使用率：Windows 下每次都要起子进程，因此做 TTL 缓存；
     * 首次采样通常返回 null（需要两次采样才能算出差值）。
     */
    private static double[] coreLoads() {
        long now = System.currentTimeMillis();
        double[] cached = coresCache;
        if (cached != null && now - coresAt < CORES_TTL_MS) {
            return cached;
        }
        synchronized (CORES_LOCK) {
            if (coresCache != null && System.currentTimeMillis() - coresAt < CORES_TTL_MS) {
                return coresCache;
            }
            double[] loads = PerfReader.perCoreLoads();
            if (loads != null) {
                coresCache = loads;
                coresAt = System.currentTimeMillis();
            }
            return loads;
        }
    }

    private static String defaultApi() {
        try {
            return AllMusic.getConfig() == null ? "" : text(AllMusic.getConfig().defaultApi);
        } catch (Exception e) {
            return "";
        }
    }

    private static boolean isMusicBanned(String id, String api) {
        if (id == null || id.isEmpty() || api == null || api.isEmpty()) {
            return false;
        }
        try {
            return BanSave.checkBanMusic(id, api);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isPlayerBanned(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        try {
            return BanSave.checkBanPlayer(name);
        } catch (Exception e) {
            return false;
        }
    }

    private static String version() {
        try {
            String v = Main.class.getPackage().getImplementationVersion();
            return v == null ? "dev" : v;
        } catch (Exception e) {
            return "dev";
        }
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    /** 带序号的日志行 */
    private static final class LogLine {
        final long seq;
        final String text;

        LogLine(long seq, String text) {
            this.seq = seq;
            this.text = text;
        }
    }
}