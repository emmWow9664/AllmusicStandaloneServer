package com.example.standalone;

import com.coloryr.allmusic.server.core.AllMusic;
import com.example.standalone.gui.MainFrame;
import com.example.standalone.gui.ThemeManager;
import com.example.standalone.gui.TrayManager;

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
import java.util.Optional;

/**
 * AllMusic 独立服务端入口
 *
 * 实现 AllMusic 服务端插件的全部功能，并提供图形界面：
 *  - 玩家列表
 *  - 歌曲列表
 *  - 日志（玩家指令 + 服务端日志）
 *  - 指令输入框
 *  - 可视化配置编辑
 */
public class Main {
    private static volatile MainFrame frame;
    private static volatile StandaloneConfig standaloneConfig;
    private static volatile File baseDir;

    public static Optional<MainFrame> getFrame() {
        return Optional.ofNullable(frame);
    }

    public static StandaloneConfig getStandaloneConfig() {
        return standaloneConfig;
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

        // 启动 TCP 服务端
        try {
            MusicServer.INSTANCE.start(standaloneConfig.bindHost, standaloneConfig.port);
        } catch (Exception e) {
            AllMusic.log.data("<light_purple>[AllMusic]<red>端口监听失败：" + e.getMessage());
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            com.example.standalone.gui.StatsManager.save(); // 退出时保存统计
            AllMusic.stop();
            MusicServer.INSTANCE.stop();
            SideStandalone.INSTANCE.shutdown();
        }));

        // 图形界面
        if (GraphicsEnvironment.isHeadless()) {
            LogStandalone.INSTANCE.append("<light_purple>[AllMusic]<red>无图形环境，仅以控制台模式运行");
            return;
        }
        SwingUtilities.invokeLater(() -> {
            com.example.standalone.gui.StatsManager.load(); // 启动时恢复统计
            ThemeManager.init();
            frame = new MainFrame();
            frame.setVisible(true);
            TrayManager.init(frame);
        });
    }
}
