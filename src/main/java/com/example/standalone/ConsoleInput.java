package com.example.standalone;

import com.coloryr.allmusic.server.core.AllMusic;
import com.example.standalone.web.WebAuth;
import com.example.standalone.web.WebServer;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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