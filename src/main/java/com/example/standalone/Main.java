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

    public static Optional<MainFrame> getFrame() {
        return Optional.ofNullable(frame);
    }

    public static StandaloneConfig getStandaloneConfig() {
        return standaloneConfig;
    }

    public static void main(String[] args) {
        // 初始化核心
        AllMusic.log = LogStandalone.INSTANCE;
        AllMusic.side = SideStandalone.INSTANCE;

        standaloneConfig = StandaloneConfig.load();

        // 初始化 AllMusic 核心（读取 config.json / message.json / cookie.json / api）
        AllMusic.init(new File(AllMusic.SERVER_DIR));
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
