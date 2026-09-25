package com.example.standalone;

import com.coloryr.allmusic.server.core.AllMusic;
import com.example.standalone.gui.StatsManager;
import com.example.standalone.web.WebAuth;
import com.example.standalone.web.WebServer;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 控制台模式下的终端指令输入。
 * <p>
 * 无图形环境时（SSH / systemd / 终端里运行 jar）读取标准输入：
 * <ul>
 *   <li>{@code server ...} —— 独立服务端自身的指令（配置、管理员密码、状态、关闭），见 {@link #printServerHelp()}</li>
 *   <li>其它输入 —— 视作 AllMusic 指令，以控制台身份（天然具备管理员权限）执行，与 GUI 控制台一致</li>
 * </ul>
 * 输入线程是守护线程：双击运行（javaw）或 stdin 不可用时读到 EOF 便自行退出，不影响服务端运行。
 */
public final class ConsoleInput {

    /** AllMusic 数据目录名（Web 凭据等也在这里） */
    private static final String DATA_DIR = AllMusic.SERVER_DIR;
    /** 统计列表每页条数 */
    private static final int PAGE_SIZE = 10;
    /** 控制台启动时间（用于 about 里显示运行时长） */
    private static final long START_TIME = System.currentTimeMillis();
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private ConsoleInput() {
    }

    /**
     * 启动终端读取线程
     */
    public static void start() {
        Thread thread = new Thread(ConsoleInput::loop, "allmusic-console-input");
        thread.setDaemon(true);
        thread.start();
    }

    private static void loop() {
        log("<light_purple>[AllMusic]<yellow>控制台模式：可直接输入 AllMusic 指令（如 list、play 歌名），"
                + "输入 server help 查看服务端指令");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    handle(line);
                } catch (Exception e) {
                    log("<light_purple>[AllMusic]<red>指令执行出错：" + e);
                }
            }
        } catch (Exception e) {
            log("<light_purple>[AllMusic]<red>控制台输入不可用：" + e.getMessage());
        }
    }

    /**
     * 处理一行终端输入（留出可见性便于测试）
     */
    static void handle(String rawLine) {
        String line = strip(rawLine);
        if (line.isEmpty()) {
            return;
        }
        String[] parts = line.split("\\s+");
        if (parts[0].equalsIgnoreCase("music")) {
            // 兼容 /music xxx 写法
            if (parts.length == 1) {
                help();
                return;
            }
            line = line.substring(parts[0].length()).trim();
            parts = line.split("\\s+");
        }
        switch (parts[0].toLowerCase(Locale.ROOT)) {
            case "server" -> server(parts);
            case "help" -> help();
            // 直接输入 exit / quit 也可关闭（AllMusic 指令中没有这两个词，不会冲突）
            case "exit", "quit" -> shutdown();
            default -> ClientSession.handleCommand(ConsoleSender.INSTANCE, line);
        }
    }

    // ---------- 服务端指令 ----------

    private static void server(String[] parts) {
        if (parts.length < 2) {
            printServerHelp();
            return;
        }
        switch (parts[1].toLowerCase(Locale.ROOT)) {
            case "help" -> printServerHelp();
            case "status" -> status();
            case "about" -> about();
            case "stats" -> stats(parts);
            case "config" -> config(parts);
            case "password" -> password(parts);
            case "stop", "exit", "quit" -> shutdown();
            default -> log("<light_purple>[AllMusic]<red>未知的服务端指令：" + parts[1]
                    + "，输入 server help 查看");
        }
    }

    /**
     * 帮助：先列服务端指令，再列 AllMusic 指令
     */
    private static void help() {
        printServerHelp();
        log("<light_purple>[AllMusic]<yellow>以下为 AllMusic 指令（以控制台身份执行，拥有管理员权限）：");
        ClientSession.handleCommand(ConsoleSender.INSTANCE, "help");
    }

    private static void printServerHelp() {
        log("<light_purple>[AllMusic]<yellow>服务端指令：");
        log("  server help                    显示本帮助");
        log("  server status                  运行状态（监听端口、在线玩家、Web 面板、密码状态）");
        log("  server about                   关于信息（版本、作者、协议、项目仓库、运行环境）");
        log("  server stats [页码]            统计信息：概览 + 玩家点歌排行（分页，每页 10 条）");
        log("  server stats songs [页码]      点歌历史（分页）");
        log("  server config list [关键字]     列出全部配置项（可按关键字过滤）");
        log("  server config get <配置项>      查看配置项当前值");
        log("  server config set <配置项> <值> 修改并保存配置（端口 / 绑定地址 / Web 面板立即生效）");
        log("  server password <新密码>        设置 Web 管理员密码（至少 4 位，只保存哈希）");
        log("  server password clear          清除 Web 管理员密码");
        log("  server stop                    关闭服务端（也可直接输入 exit / quit，或按 Ctrl+C）");
        log("<light_purple>[AllMusic]<yellow>配置项可用「路径」或「原名」指定，例如："
                + "standalone.port、port、limit.maxPlayList、maxPlayList");
        log("<light_purple>[AllMusic]<yellow>其它输入一律按 AllMusic 指令执行，例如：list、play 歌名、ban xxx");
    }

    private static void status() {
        StandaloneConfig config = Main.getStandaloneConfig();
        int players = SideStandalone.INSTANCE.getClientSessions().size();
        log("<light_purple>[AllMusic]<yellow>TCP 监听端口：" + MusicServer.getPortText());
        log("<light_purple>[AllMusic]<yellow>绑定地址：" + (config == null ? "未知" : config.bindHost));
        log("<light_purple>[AllMusic]<yellow>在线玩家：" + players);
        log("<light_purple>[AllMusic]<yellow>Web 面板：" + (WebServer.INSTANCE.isRunning()
                ? WebServer.INSTANCE.getUrlText() : "未启动"));
        log("<light_purple>[AllMusic]<yellow>Web 管理员密码：" + (webAuth().hasPassword() ? "已设置" : "未设置"));
        log("<light_purple>[AllMusic]<yellow>数据目录：" + new File(Main.getBaseDir(), DATA_DIR).getAbsolutePath());
    }

    private static void config(String[] parts) {
        if (parts.length < 3 || parts[2].equalsIgnoreCase("list")) {
            listConfig(parts.length >= 4 ? parts[3] : null);
            return;
        }
        switch (parts[2].toLowerCase(Locale.ROOT)) {
            case "get" -> {
                if (parts.length < 4) {
                    log("<light_purple>[AllMusic]<red>用法：server config get <配置项>");
                    return;
                }
                ConfigCatalog.Item item = ConfigCatalog.find(parts[3]);
                String path = item == null ? parts[3] : item.path;
                Object value = ConfigCatalog.read(path);
                if (value == null) {
                    log("<light_purple>[AllMusic]<red>没有这个配置项：" + parts[3]);
                    return;
                }
                log("<light_purple>[AllMusic]<yellow>" + path + " = " + value
                        + (item == null ? "" : "  （" + item.cn + "：" + item.desc + "）"));
            }
            case "set" -> {
                if (parts.length < 5) {
                    log("<light_purple>[AllMusic]<red>用法：server config set <配置项> <值>");
                    return;
                }
                ConfigCatalog.Item item = ConfigCatalog.find(parts[3]);
                String path = item == null ? parts[3] : item.path;
                String value = String.join(" ", Arrays.copyOfRange(parts, 4, parts.length));
                String error = checkRange(path, value);
                if (error == null) {
                    error = ConfigCatalog.write(path, value);
                }
                if (error != null) {
                    log("<light_purple>[AllMusic]<red>" + error);
                    return;
                }
                ConfigCatalog.saveAll();
                log("<light_purple>[AllMusic]<yellow>" + path + " = " + ConfigCatalog.read(path) + "（已保存）");
                apply(path);
            }
            default -> log("<light_purple>[AllMusic]<red>用法：server config list [关键字]"
                    + " / server config get <配置项> / server config set <配置项> <值>");
        }
    }

    private static void listConfig(String keyword) {
        String filter = keyword == null ? null : keyword.toLowerCase(Locale.ROOT);
        for (ConfigCatalog.Group group : ConfigCatalog.groups()) {
            List<ConfigCatalog.Item> hit = group.items.stream()
                    .filter(item -> filter == null
                            || item.path.toLowerCase(Locale.ROOT).contains(filter)
                            || item.en.toLowerCase(Locale.ROOT).contains(filter)
                            || item.cn.contains(filter))
                    .toList();
            if (hit.isEmpty()) {
                continue;
            }
            log("<light_purple>[AllMusic]<yellow>【" + group.title + "】");
            for (ConfigCatalog.Item item : hit) {
                log("  " + item.path + " = " + ConfigCatalog.read(item.path) + "    （" + item.cn + "：" + item.desc + "）");
            }
        }
    }

    /**
     * 端口类配置的范围校验（配置文件里的非法端口会在下次启动时被静默改回默认值，这里直接拒绝）
     */
    private static String checkRange(String path, String value) {
        if (!path.equals("standalone.port") && !path.equals("standalone.webPort")) {
            return null;
        }
        try {
            int port = Integer.parseInt(value.trim());
            if (port < 1 || port > 65535) {
                return "端口必须在 1 ~ 65535 之间：" + port;
            }
        } catch (NumberFormatException e) {
            return "端口需要整数：" + value;
        }
        return null;
    }

    /**
     * 让改动立即生效（无需重启进程）
     */
    private static void apply(String path) {
        StandaloneConfig config = Main.getStandaloneConfig();
        if (config == null) {
            return;
        }
        switch (path) {
            case "standalone.port", "standalone.bindHost" -> {
                try {
                    MusicServer.INSTANCE.start(config.bindHost, config.port);
                } catch (Exception e) {
                    log("<light_purple>[AllMusic]<red>重新监听 TCP 端口失败（端口可能已被占用）：" + e.getMessage());
                }
            }
            case "standalone.webEnabled", "standalone.webPort", "standalone.webBindHost" -> {
                try {
                    if (config.webEnabled) {
                        WebServer.INSTANCE.start(config.webBindHost, config.webPort,
                                new File(Main.getBaseDir(), DATA_DIR));
                    } else {
                        WebServer.INSTANCE.stop();
                        log("<light_purple>[AllMusic]<yellow>Web 面板已关闭");
                    }
                } catch (Exception e) {
                    log("<light_purple>[AllMusic]<red>Web 面板重启失败（端口可能已被占用）：" + e.getMessage());
                }
            }
            default -> {
                // 核心配置由 AllMusic 运行时直接读取，无需额外动作
            }
        }
    }

    /**
     * 关于信息（与 GUI「设置 → 关于」一致）
     */
    private static void about() {
        log("<light_purple>[AllMusic]<yellow>【关于】");
        log("  名称：AllmusicStandaloneServer（AllMusic 独立音乐服务器）");
        log("  版本：" + Main.getVersion());
        log("  作者：emmWow9664、DeepseekV4Flash");
        log("  协议：GPL-3.0（AllMusic 的衍生作品）");
        log("  仓库：https://github.com/emmWow9664/AllmusicStandaloneServer");
        log("  说明：实现 AllMusic 服务端插件的全部功能，可脱离 Minecraft 独立运行，"
                + "配合 AllmusicConnect 客户端模组使用；音乐解析依赖数据目录下 api/ 里的音乐 API jar");
        log("  运行环境：Java " + System.getProperty("java.version") + " / "
                + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        log("  运行时长：" + formatDuration(System.currentTimeMillis() - START_TIME));
        log("  数据目录：" + new File(Main.getBaseDir(), DATA_DIR).getAbsolutePath());
        log("  Web 面板：" + (WebServer.INSTANCE.isRunning() ? WebServer.INSTANCE.getUrlText() : "未启动"));
    }

    /**
     * 统计信息：概览 + 分页列表（server stats [页码] / server stats songs [页码]）
     */
    private static void stats(String[] parts) {
        String view = "players";
        int page = 1;
        if (parts.length >= 3) {
            if (isNumber(parts[2])) {
                page = parsePage(parts[2]);
            } else {
                String sub = parts[2].toLowerCase(Locale.ROOT);
                if (sub.equals("songs") || sub.equals("song")) {
                    view = "songs";
                } else if (sub.equals("players") || sub.equals("player")) {
                    view = "players";
                } else {
                    log("<light_purple>[AllMusic]<red>用法：server stats [页码] / server stats songs [页码]");
                    return;
                }
                if (parts.length >= 4) {
                    page = parsePage(parts[3]);
                }
            }
        }
        overview();
        if (view.equals("songs")) {
            printPage("点歌历史", songLines(), page, "server stats songs <页码>");
        } else {
            printPage("玩家点歌排行", playerLines(), page,
                    "server stats <页码>；输入 server stats songs [页码] 查看点歌历史");
        }
    }

    private static void overview() {
        List<StatsManager.PlayerRecord> players = StatsManager.getPlayers();
        int totalSongs = 0;
        for (StatsManager.PlayerRecord p : players) {
            totalSongs += p.songCount;
        }
        log("<light_purple>[AllMusic]<yellow>【统计概览】");
        log("  在线玩家：" + SideStandalone.INSTANCE.getClientSessions().size()
                + "    今日连接：" + StatsManager.getTodayPlayerCount()
                + "    累计玩家：" + players.size());
        log("  累计点歌次数：" + totalSongs
                + "    点歌记录：" + StatsManager.getSongs().size() + " 条（最多保留 500 条）");
    }

    /** 玩家点歌排行（按点歌次数降序） */
    private static List<String> playerLines() {
        List<StatsManager.PlayerRecord> players = StatsManager.getPlayers();
        players.sort(Comparator.comparingInt((StatsManager.PlayerRecord p) -> p.songCount).reversed()
                .thenComparing(p -> p.name == null ? "" : p.name));
        List<String> lines = new ArrayList<>();
        for (StatsManager.PlayerRecord p : players) {
            lines.add(pad(p.name, 18) + " 点歌 " + p.songCount + " 次      累计连接 "
                    + formatDuration(p.totalConnectMs) + "      首次连接 " + formatTime(p.firstConnect));
        }
        return lines;
    }

    /** 点歌历史（最新的在前，统计存储里已按时间倒序） */
    private static List<String> songLines() {
        List<String> lines = new ArrayList<>();
        for (StatsManager.SongRecord s : StatsManager.getSongs()) {
            lines.add(pad(s.name, 18) + " 由 " + pad(s.player, 14) + " 点播  " + formatTime(s.time)
                    + (s.id == null || s.id.isEmpty() ? "" : "  id=" + s.id));
        }
        return lines;
    }

    /**
     * 分页输出一列内容
     *
     * @param hint 翻页提示（多页时显示）
     */
    private static void printPage(String title, List<String> lines, int page, String hint) {
        int total = lines.size();
        int pages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.min(Math.max(page, 1), pages);
        log("<light_purple>[AllMusic]<yellow>【" + title + "】（共 " + total + " 条，第 "
                + current + "/" + pages + " 页）");
        if (total == 0) {
            log("  暂无记录");
            return;
        }
        int from = (current - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, total);
        for (int i = from; i < to; i++) {
            log("  " + (i + 1) + ". " + lines.get(i));
        }
        if (pages > 1) {
            log("<light_purple>[AllMusic]<yellow>  翻页：" + hint + "（每页 " + PAGE_SIZE + " 条）");
        }
    }

    private static int parsePage(String text) {
        try {
            return Math.max(1, Integer.parseInt(text.trim()));
        } catch (Exception e) {
            return 1;
        }
    }

    private static boolean isNumber(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** 时长格式化：1 天 2 小时 3 分 / 5 分 6 秒 / 7 秒 */
    private static String formatDuration(long millis) {
        long seconds = Math.max(0, millis) / 1000;
        long days = seconds / 86400;
        long hours = seconds % 86400 / 3600;
        long minutes = seconds % 3600 / 60;
        if (days > 0) {
            return days + " 天 " + hours + " 小时 " + minutes + " 分";
        }
        if (hours > 0) {
            return hours + " 小时 " + minutes + " 分";
        }
        if (minutes > 0) {
            return minutes + " 分 " + (seconds % 60) + " 秒";
        }
        return seconds + " 秒";
    }

    private static String formatTime(long millis) {
        if (millis <= 0) {
            return "-";
        }
        return TIME_FORMAT.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
    }

    /** 简单对齐：中文按 2 个字符宽计算（等宽终端里能对齐），不足补空格 */
    private static String pad(String text, int width) {
        String value = text == null ? "" : text;
        int length = 0;
        for (int i = 0; i < value.length(); i++) {
            length += value.charAt(i) > 0x2E80 ? 2 : 1;
        }
        StringBuilder sb = new StringBuilder(value);
        for (int i = length; i < width; i++) {
            sb.append(' ');
        }
        return sb.toString();
    }

    private static void password(String[] parts) {
        if (parts.length < 3) {
            log("<light_purple>[AllMusic]<red>用法：server password <新密码>（至少 4 位） / server password clear");
            return;
        }
        if (parts[2].equalsIgnoreCase("clear")) {
            webAuth().clearPassword();
            log("<light_purple>[AllMusic]<yellow>Web 管理员密码已清除（Web 管理功能将无法登录）");
            return;
        }
        String pwd = String.join(" ", Arrays.copyOfRange(parts, 2, parts.length));
        if (pwd.length() < 4) {
            log("<light_purple>[AllMusic]<red>密码至少 4 位");
            return;
        }
        webAuth().setPassword(pwd.toCharArray());
        log("<light_purple>[AllMusic]<yellow>Web 管理员密码已更新（已登录的会话已失效）");
    }

    /**
     * Web 凭据管理器（Web 面板未启动时也可设置密码）
     */
    private static WebAuth webAuth() {
        return WebServer.INSTANCE.auth(new File(Main.getBaseDir(), DATA_DIR));
    }

    private static void shutdown() {
        log("<light_purple>[AllMusic]<yellow>正在关闭服务端 ...");
        System.exit(0);
    }

    private static String strip(String rawLine) {
        String line = rawLine == null ? "" : rawLine.trim();
        while (line.startsWith("/")) {
            line = line.substring(1).trim();
        }
        return line;
    }

    private static void log(String message) {
        AllMusic.log.data(message);
    }
}