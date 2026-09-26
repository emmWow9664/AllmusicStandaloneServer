package com.example.standalone;

import com.coloryr.allmusic.server.core.AllMusic;
import com.example.standalone.gui.ConsoleFrame;
import com.example.standalone.gui.TrayManager;
import com.example.standalone.monitor.StatsManager;
import com.example.standalone.web.WebServer;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * AllMusic 独立服务端入口
 *
 * 实现 AllMusic 服务端插件的全部功能，可脱离 Minecraft 独立运行。
 * 管理方式有两种：
 *  - 内嵌 Web 面板（浏览器访问，提供仪表盘 / 性能 / 统计 / 设置 / 控制台）
 *  - 桌面控制台窗口（日志 + 指令输入）：关闭窗口后驻留系统托盘，服务继续在后台运行
 * 无图形环境（SSH / systemd）时改为读取终端指令。
 */
public class Main {
    private static volatile StandaloneConfig standaloneConfig;
    private static volatile File baseDir;

    public static StandaloneConfig getStandaloneConfig() {
        return standaloneConfig;
    }

    /**
     * 服务端版本号：取自 jar 清单里的 Implementation-Version（由构建脚本写入），
     * 非 jar 方式运行时回退为 dev
     */
    public static String getVersion() {
        Package pkg = Main.class.getPackage();
        String version = pkg == null ? null : pkg.getImplementationVersion();
        return version == null || version.isEmpty() ? "dev" : version;
    }

    /**
     * 服务端所在文件夹（jar 所在目录）。
     * <p>
     * 所有数据目录（allmusic_server/ 等）都基于此路径创建/读取，
     * 保证无论从哪个工作目录启动 `java -jar ...`，文件始终位于 jar 旁边。
     */
    public static File getBaseDir() {
        if (baseDir == null) {
            try {
                // 用 Paths.get(URI) 解析，正确处理 Windows 盘符前导斜杠与空格（%20）转义
                URI uri = Main.class.getProtectionDomain().getCodeSource().getLocation().toURI();
                File src = Paths.get(uri).toFile();
                baseDir = src.isDirectory() ? src : src.getParentFile();
            } catch (Exception e) {
                baseDir = new File(".");
            }
        }
        return baseDir;
    }

    public static void main(String[] args) {
        // 接管标准输出/错误，使 SLF4J 等第三方输出也能进入 GUI 控制台
        LogStandalone.installStreamCapture();
        try {
            start();
        } catch (Throwable t) {
            reportFatal(t);
        }
    }

    /** 启动失败时把错误暴露出来：双击运行（javaw）没有控制台，否则用户只会看到"没反应" */
    private static void reportFatal(Throwable t) {
        try {
            File dir = new File(getBaseDir(), AllMusic.SERVER_DIR);
            dir.mkdirs();
            try (PrintWriter w = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(new File(dir, "crash.log"), true),
                    StandardCharsets.UTF_8), true)) {
                w.println("[" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "] 启动失败");
                t.printStackTrace(w);
            }
        } catch (Exception ignored) {
        }
        if (GraphicsEnvironment.isHeadless()) {
            System.exit(1);
        }
        try {
            JOptionPane.showMessageDialog(null,
                    "服务端启动失败：\n" + t + "\n\n详细信息见 " + AllMusic.SERVER_DIR + "/crash.log",
                    "AllmusicStandaloneServer", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ignored) {
        }
        // 必须显式退出：核心留下的非守护线程会让 JVM 一直存活，导致双击失败后残留僵尸进程
        System.exit(1);
    }

    private static void start() {
        // 初始化核心
        AllMusic.log = LogStandalone.INSTANCE;
        AllMusic.side = SideStandalone.INSTANCE;

        standaloneConfig = StandaloneConfig.load();

        // 初始化 AllMusic 核心（读取 config.json / message.json / cookie.json / api），
        // 数据目录固定在服务端所在文件夹下，与启动时的工作目录无关
        AllMusic.init(new File(getBaseDir(), AllMusic.SERVER_DIR));
        AllMusic.start();

        // 恢复统计（点歌/玩家/今日连接名单）：与界面无关，任何模式下都要生效
        StatsManager.load();
        // 启动每秒统计线程：控制台模式与窗口模式都会启动（内部有防重复标志）
        StatsManager.startTicker();

        // 启动 TCP 服务端
        try {
            MusicServer.INSTANCE.start(standaloneConfig.bindHost, standaloneConfig.port);
        } catch (Exception e) {
            AllMusic.log.data("<light_purple>[AllMusic]<red>端口监听失败：" + e.getMessage());
        }

        // 启动内嵌 Web 面板（失败不影响其它功能）
        if (standaloneConfig.webEnabled) {
            try {
                WebServer.INSTANCE.start(standaloneConfig.webBindHost, standaloneConfig.webPort,
                        new File(getBaseDir(), AllMusic.SERVER_DIR));
            } catch (Exception e) {
                AllMusic.log.data("<light_purple>[AllMusic]<red>Web 面板启动失败（端口 "
                        + standaloneConfig.webPort + " 可能已被占用）：" + e.getMessage());
            }
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            StatsManager.save(); // 退出时保存统计
            WebServer.INSTANCE.stop();                       // 先释放 Web 端口
            AllMusic.stop();
            MusicServer.INSTANCE.stop();
            SideStandalone.INSTANCE.shutdown();
        }));

        // 图形界面（控制台窗口）
        if (GraphicsEnvironment.isHeadless()) {
            LogStandalone.INSTANCE.append("<light_purple>[AllMusic]<red>无图形环境，仅以控制台模式运行");
            // 控制台模式：读取终端指令（server ... 为本服务端指令，其它按 AllMusic 指令执行）
            ConsoleInput.start();
            return;
        }
        SwingUtilities.invokeLater(() -> {
            ConsoleFrame console = new ConsoleFrame();
            console.setVisible(true);
            TrayManager.init(console); // 关闭窗口后驻留托盘，托盘可重新打开控制台
        });
    }
}
