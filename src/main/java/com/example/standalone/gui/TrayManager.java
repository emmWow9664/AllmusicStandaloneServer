package com.example.standalone.gui;

import com.coloryr.allmusic.server.core.AllMusic;
import com.example.standalone.web.WebServer;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 系统托盘：关闭控制台窗口后服务继续在后台运行，托盘可重新打开控制台、打开 Web 面板或退出服务端。
 * 支持 Windows 与 Linux（需桌面环境提供系统托盘）。
 */
public final class TrayManager {
    private static TrayIcon trayIcon;
    private static javax.swing.JFrame frame;

    private TrayManager() {
    }

    /** 托盘是否可用（不可用时关闭窗口即退出进程） */
    public static boolean isAvailable() {
        return trayIcon != null;
    }

    public static void init(javax.swing.JFrame consoleFrame) {
        frame = consoleFrame;
        if (!SystemTray.isSupported()) {
            AllMusic.log.data("<light_purple>[AllMusic]<yellow>系统托盘不可用，关闭窗口将直接退出服务端");
            return;
        }
        try {
            // 托盘菜单统一使用英文，避免各平台中文字体渲染差异（JDK bug JDK-4769623）
            PopupMenu menu = new PopupMenu();
            MenuItem open = new MenuItem("Open Console");
            open.addActionListener(e -> showWindow());
            MenuItem web = new MenuItem("Open Web Panel");
            web.addActionListener(e -> openWebPanel());
            MenuItem exit = new MenuItem("Exit");
            exit.addActionListener(e -> System.exit(0));
            menu.add(open);
            menu.add(web);
            menu.add(exit);

            trayIcon = new TrayIcon(drawIcon(), "AllMusic 独立服务端（双击打开控制台）", menu);
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

    /** 用系统默认浏览器打开 Web 面板 */
    private static void openWebPanel() {
        if (!WebServer.INSTANCE.isRunning()) {
            AllMusic.log.data("<light_purple>[AllMusic]<red>Web 面板未启动，无法打开");
            return;
        }
        try {
            Desktop.getDesktop().browse(new URI(WebServer.INSTANCE.getUrlText()));
        } catch (Exception e) {
            AllMusic.log.data("<light_purple>[AllMusic]<red>打开浏览器失败：" + e.getMessage()
                    + "（请手动访问 " + WebServer.INSTANCE.getUrlText() + "）");
        }
    }

    /** 程序绘制的音符图标（避免外部素材依赖） */
    private static Image drawIcon() {
        int size = 64;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x39C5BB));
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