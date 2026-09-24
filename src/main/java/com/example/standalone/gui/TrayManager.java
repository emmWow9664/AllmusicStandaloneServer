package com.example.standalone.gui;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 系统托盘：关闭窗口后服务继续在后台运行，托盘右键菜单可打开窗口/退出。
 * 支持 Windows 与 Linux（需桌面环境提供系统托盘）。
 */
public final class TrayManager {
    private static TrayIcon trayIcon;
    private static javax.swing.JFrame frame;

    private TrayManager() {
    }

    public static void init(javax.swing.JFrame mainFrame) {
        frame = mainFrame;
        if (!SystemTray.isSupported()) {
            return;
        }
        try {
            // 托盘菜单统一使用英文，避免各平台中文字体渲染差异（JDK bug JDK-4769623）
            PopupMenu menu = new PopupMenu();
            MenuItem open = new MenuItem("Open Window");
            open.addActionListener(e -> showWindow());
            MenuItem exit = new MenuItem("Exit");
            exit.addActionListener(e -> System.exit(0));
            menu.add(open);
            menu.add(exit);

            trayIcon = new TrayIcon(drawIcon(), "AllMusic 独立服务端", menu);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(e -> showWindow());
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException ex) {
            Logger.getLogger("Tray").log(Level.WARNING, "无法创建系统托盘图标", ex);
        }
    }

    public static void showWindow() {
        if (frame == null) {
            return;
        }
        java.awt.EventQueue.invokeLater(() -> {
            frame.setVisible(true);
            frame.setExtendedState(java.awt.Frame.NORMAL);
            frame.toFront();
            frame.requestFocus();
        });
    }

    /** 程序绘制的音符图标（避免外部素材依赖） */
    private static Image drawIcon() {
        int size = 64;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x1E88E5));
        g.fillOval(8, 8, size - 16, size - 16);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 40));
        String note = "♪";
        java.awt.FontMetrics fm = g.getFontMetrics();
        int x = (size - fm.stringWidth(note)) / 2;
        int y = (size - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(note, x, y);
        g.dispose();
        return img;
    }
}
