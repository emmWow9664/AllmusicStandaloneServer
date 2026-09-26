package com.example.standalone.gui;

import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.music.PlayMusic;
import com.example.standalone.LogStandalone;
import com.example.standalone.MusicServer;
import com.example.standalone.SideStandalone;
import com.example.standalone.web.WebServer;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * 控制台窗口：本服务端唯一的图形界面（日志 + 指令输入）。
 * <p>
 * 关闭窗口不会退出进程：只要系统托盘可用就只隐藏窗口，服务继续在后台运行，
 * 双击托盘图标或托盘菜单「Open Console」可再次打开本窗口；
 * 托盘不可用时（无桌面环境）关闭窗口视为退出服务端。
 */
public class ConsoleFrame extends JFrame {
    private final ConsolePanel console = new ConsolePanel();
    private final JLabel statusLabel = new JLabel(" ");

    static {
        // 采用系统外观（此前 GUI 使用的第三方主题已随界面一起移除）
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
    }

    public ConsoleFrame() {
        super("AllMusic 独立服务端 - 控制台");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeOrHide();
            }
        });

        buildUI();
        setSize(920, 620);
        setMinimumSize(new Dimension(560, 360));
        setLocationRelativeTo(null);

        // 日志监听：注册时会回放启动阶段已产生的日志
        LogStandalone.INSTANCE.addListener(console::append);
        AllMusic.log.data("<light_purple>[AllMusic]<yellow>控制台窗口已就绪：可直接输入 AllMusic 指令"
                + "（如 list、play 歌名），输入 server help 查看服务端指令；"
                + "关闭窗口后服务端仍驻留系统托盘运行");

        new Timer(1000, e -> refreshStatus()).start();
        refreshStatus();
    }

    private void buildUI() {
        JPanel status = new JPanel(new BorderLayout());
        status.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        status.add(statusLabel, BorderLayout.WEST);

        setLayout(new BorderLayout());
        add(console, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
    }

    /**
     * 关闭窗口：托盘可用时只隐藏（服务继续运行），否则直接退出进程
     */
    private void closeOrHide() {
        if (TrayManager.isAvailable()) {
            setVisible(false);
            AllMusic.log.data("<light_purple>[AllMusic]<yellow>窗口已隐藏，服务端仍在运行；"
                    + "可从系统托盘重新打开控制台或退出服务端");
            return;
        }
        System.exit(0);
    }

    private void refreshStatus() {
        int players = SideStandalone.INSTANCE.getClientSessions().size();
        int queue = PlayMusic.getList().size();
        statusLabel.setText("端口: " + MusicServer.getPortText()
                + "    在线玩家: " + players
                + "    歌曲队列: " + queue
                + "    Web 面板: " + (WebServer.INSTANCE.isRunning() ? WebServer.INSTANCE.getUrlText() : "未启动"));
    }

    /** 把窗口从托盘重新调出来 */
    public void showWindow() {
        if (!isVisible()) {
            setVisible(true);
        }
        setExtendedState(NORMAL);
        toFront();
        requestFocus();
    }
}