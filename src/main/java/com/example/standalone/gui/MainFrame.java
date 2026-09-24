package com.example.standalone.gui;

import com.example.standalone.MusicServer;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

/**
 * 重构后的主窗口：无边框 + 左侧可折叠导航 + 顶部标题栏 + 四个页面
 */
public class MainFrame extends JFrame {
    private final CardLayout cards = new CardLayout();
    private final JPanel center = new JPanel(cards);
    private final JLabel statusLabel = new JLabel(" ");
    private static final int MIN_W = 800;
    private static final int MIN_H = 600;

    public MainFrame() {
        super("AllMusic 独立服务端");
        setUndecorated(true); // 无系统标题栏
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE); // 关闭由标题栏/托盘控制

        buildUI();
        // 启动时窗口大小略小于屏幕（约 88%），居中显示
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int w = Math.max(MIN_W, (int) (screen.width * 0.88));
        int h = Math.max(MIN_H, (int) (screen.height * 0.88));
        setSize(w, h);
        setLocationRelativeTo(null);

        // 状态栏刷新
        new Timer(1000, e -> refreshStatus()).start();
        refreshStatus();
    }

    private void buildUI() {
        TitleBar titleBar = new TitleBar(this);

        // 左侧导航（默认展开）
        SideNav nav = new SideNav(idx -> {
            String[] names = {"dashboard", "perf", "stats", "settings", "console"};
            cards.show(center, names[idx]);
        });
        nav.setPreferredSize(new Dimension(170, 0));

        // 页面
        ConsolePanel console = new ConsolePanel();
        center.add(new DashboardPanel(), "dashboard");
        center.add(new PerformancePanel(), "perf");
        center.add(new StatsPanel(), "stats");
        center.add(new SettingsPanel(), "settings");
        center.add(console, "console");
        cards.show(center, "dashboard"); // 启动时在仪表盘

        // 日志转发到控制台界面
        com.example.standalone.LogStandalone.INSTANCE.addListener(console::append);

        // 状态栏
        JPanel status = new JPanel(new BorderLayout());
        status.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 12, 2, 12));
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        status.add(statusLabel, BorderLayout.WEST);

        JPanel body = new JPanel(new BorderLayout());
        body.add(nav, BorderLayout.WEST);
        body.add(center, BorderLayout.CENTER);

        setLayout(new BorderLayout());
        add(titleBar, BorderLayout.NORTH);
        // 左右下三边拉伸条（顶边由标题栏处理）
        add(resizeEdge(BorderLayout.EAST), BorderLayout.EAST);
        add(resizeEdge(BorderLayout.WEST), BorderLayout.WEST);
        JPanel southWrap = new JPanel(new BorderLayout());
        southWrap.add(status, BorderLayout.CENTER);
        southWrap.add(resizeEdge(BorderLayout.SOUTH), BorderLayout.SOUTH);
        add(southWrap, BorderLayout.SOUTH);
        add(body, BorderLayout.CENTER);
    }

    /**
     * 边框拉拽条：无边框窗口通过该组件调整大小
     */
    private JPanel resizeEdge(Object position) {
        JPanel edge = new JPanel();
        int cursor;
        boolean horizontal;
        if (position == BorderLayout.EAST) {
            edge.setPreferredSize(new Dimension(6, 0));
            cursor = Cursor.E_RESIZE_CURSOR;
            horizontal = true;
        } else if (position == BorderLayout.WEST) {
            edge.setPreferredSize(new Dimension(6, 0));
            cursor = Cursor.W_RESIZE_CURSOR;
            horizontal = true;
        } else {
            edge.setPreferredSize(new Dimension(0, 6));
            cursor = Cursor.S_RESIZE_CURSOR;
            horizontal = false;
        }
        edge.setCursor(Cursor.getPredefinedCursor(cursor));

        final boolean isEast = position == BorderLayout.EAST;
        final boolean isWest = position == BorderLayout.WEST;
        final boolean isSouth = position == BorderLayout.SOUTH;

        final int[] start = new int[2];           // sx, sy
        final Rectangle[] startBounds = new Rectangle[1];

        edge.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                start[0] = e.getXOnScreen();
                start[1] = e.getYOnScreen();
                startBounds[0] = getBounds();
            }
        });

        edge.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                int dx = e.getXOnScreen() - start[0];
                int dy = e.getYOnScreen() - start[1];
                Rectangle sb = startBounds[0];
                if (isEast) {
                    setBounds(sb.x, sb.y, Math.max(MIN_W, sb.width + dx), sb.height);
                } else if (isWest) {
                    int newW = Math.max(MIN_W, sb.width - dx);
                    setBounds(sb.x + (sb.width - newW), sb.y, newW, sb.height);
                } else if (isSouth) {
                    setBounds(sb.x, sb.y, sb.width, Math.max(MIN_H, sb.height + dy));
                }
            }
        });
        return edge;
    }

    private void refreshStatus() {
        int players = com.example.standalone.SideStandalone.INSTANCE.getClientSessions().size();
        int queue = com.coloryr.allmusic.server.core.music.PlayMusic.getList().size();
        statusLabel.setText("端口: " + MusicServer.getPortText()
                + "    在线玩家: " + players
                + "    歌曲队列: " + queue
                + "    服务运行中(关闭窗口后驻留托盘)");
    }

    /** 托盘打开窗口 */
    public void showWindow() {
        TrayManager.showWindow();
    }

    // 兼容旧回调（新 UI 由定时器自动刷新，无需额外处理）
    public void onPlayerJoin(String name) {
    }

    public void onPlayerLeave(String name) {
    }

    public void updateNowPlaying() {
    }
}
