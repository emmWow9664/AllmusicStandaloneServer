package com.example.standalone.gui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

/**
 * 自定义无边框标题栏：
 * - 拖拽移动窗口（最大化状态下拖拽自动还原并跟随鼠标）
 * - 顶部边缘拉拽调整高度
 * - 双击最大化/还原（不覆盖任务栏）
 * - 最小化/最大化/关闭按钮
 */
public class TitleBar extends JPanel {
    private final Window window;
    private boolean maximized = false;
    private Rectangle normalBounds;
    private boolean resizeTop = false;
    private int dragStartX, dragStartY, dragStartScreenX, dragStartScreenY;
    private Rectangle dragStartBounds;
    private final JButton minimizeBtn = makeBtn("─");
    private final JButton maximizeBtn = makeBtn("□");
    private final JButton closeBtn = makeBtn("✕");
    private static final int MIN_W = 800;
    private static final int MIN_H = 600;

    public TitleBar(Window window) {
        this.window = window;
        setLayout(new java.awt.BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 4));
        setPreferredSize(new Dimension(0, 40));

        // 左侧：标题
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        left.setOpaque(false);
        JLabel icon = new JLabel("♪");
        icon.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        JLabel title = new JLabel("AllMusic 独立服务端");
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        left.add(icon);
        left.add(title);
        add(left, java.awt.BorderLayout.WEST);

        // 右侧：窗口控制
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 4));
        right.setOpaque(false);
        minimizeBtn.setToolTipText("最小化");
        minimizeBtn.addActionListener(e -> {
            if (window instanceof java.awt.Frame f) {
                f.setState(java.awt.Frame.ICONIFIED);
            }
        });
        maximizeBtn.setToolTipText("最大化/还原");
        maximizeBtn.addActionListener(e -> toggleMaximize());
        closeBtn.setToolTipText("关闭到托盘（服务继续运行）");
        closeBtn.addActionListener(e -> {
            if (window instanceof javax.swing.JFrame f) {
                f.setVisible(false);
            }
        });
        right.add(minimizeBtn);
        right.add(maximizeBtn);
        right.add(closeBtn);
        add(right, java.awt.BorderLayout.EAST);

        // 鼠标事件：拖拽 / 顶部拉伸 / 双击最大化
        MouseAdapter press = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragStartScreenX = e.getXOnScreen();
                dragStartScreenY = e.getYOnScreen();
                dragStartBounds = window.getBounds();
                dragStartX = e.getX();
                dragStartY = e.getY();
                resizeTop = e.getY() <= 6;
                if (resizeTop) {
                    return;
                }
                // 最大化状态下拖动 → 先还原窗口并让鼠标位置随窗口移动
                if (maximized) {
                    restoreFromMax();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                resizeTop = false;
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && e.getY() > 6) {
                    toggleMaximize();
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                setCursor(Cursor.getPredefinedCursor(
                        e.getY() <= 6 ? Cursor.N_RESIZE_CURSOR : Cursor.DEFAULT_CURSOR));
            }
        };
        addMouseListener(press);

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                // 顶部边缘拉伸（窗口底部保持不动）
                if (resizeTop) {
                    int dy = e.getYOnScreen() - dragStartScreenY;
                    int newH = Math.max(MIN_H, dragStartBounds.height - dy);
                    int newY = dragStartBounds.y + (dragStartBounds.height - newH);
                    window.setBounds(dragStartBounds.x, newY, dragStartBounds.width, newH);
                    return;
                }
                if (maximized) {
                    return;
                }
                window.setLocation(e.getXOnScreen() - dragStartX, e.getYOnScreen() - dragStartY);
            }
        });
    }

    /** 最大化状态下拖拽：还原到普通大小，并按鼠标在窗口中的水平位置定位 */
    private void restoreFromMax() {
        if (normalBounds == null) {
            normalBounds = window.getBounds();
        }
        Rectangle n = normalBounds;
        double ratio = (double) dragStartX / Math.max(1, window.getWidth());
        int nx = dragStartScreenX - (int) (n.width * ratio);
        nx = Math.max(0, Math.min(nx, dragStartScreenX - 20));
        window.setBounds(n);
        window.setLocation(nx, dragStartScreenY - dragStartY);
        maximized = false;
    }

    /**
     * 最大化/还原：使用系统可用区域（自动避开任务栏），不覆盖整个屏幕。
     */
    private void toggleMaximize() {
        if (window instanceof java.awt.Frame f) {
            if (maximized) {
                if (normalBounds != null) {
                    f.setBounds(normalBounds);
                }
                maximized = false;
            } else {
                normalBounds = f.getBounds();
                Rectangle max = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
                f.setBounds(max);
                maximized = true;
            }
        }
    }

    private JButton makeBtn(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        b.setFocusable(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }
}
