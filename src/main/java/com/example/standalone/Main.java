package com.example.standalone;

import com.coloryr.allmusic.server.core.AllMusic;
import com.example.standalone.gui.MainFrame;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.io.File;
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
                String path = Main.class.getProtectionDomain()
                        .getCodeSource().getLocation().toURI().getPath();
                baseDir = new File(path).getParentFile();
            } catch (Exception e) {
                baseDir = new File(".");
            }
        }
        return baseDir;
    }

    public static void main(String[] args) {
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
            AllMusic.stop();
            MusicServer.INSTANCE.stop();
            SideStandalone.INSTANCE.shutdown();
        }));

        // 图形界面
        if (GraphicsEnvironment.isHeadless()) {
            LogStandalone.INSTANCE.append("<light_purple>[AllMusic]<red>无图形环境，仅以控制台模式运行");
            return;
        }
        SwingUtilities.invokeLater(() -> frame = new MainFrame());
    }
}
