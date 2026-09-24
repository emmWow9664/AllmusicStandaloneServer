package com.example.standalone.gui;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * 圆环图：用于展示 CPU / RAM / 网络使用率
 */
public class RingChart extends JPanel {
    private double percent = 0;
    private String caption = "";
    private final Color ringColor;
    private int thickness = 11;

    public RingChart(Color ringColor) {
        this.ringColor = ringColor;
        setOpaque(true);
        setPreferredSize(new Dimension(92, 92));
    }

    /** 设置圆环粗细（像素） */
    public void setThickness(int thickness) {
        this.thickness = Math.max(2, thickness);
        repaint();
    }

    public void setValue(double percent, String caption) {
        this.percent = Math.max(0, Math.min(100, percent));
        this.caption = caption == null ? "" : caption;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int d = Math.min(w, h) - 12;
        int x = (w - d) / 2;
        int y = (h - d) / 2;

        // 背景环
        g2.setColor(getBackground().darker());
        g2.fillOval(x, y, d, d);

        // 前景弧（从 12 点方向顺时针）
        g2.setColor(ringColor);
        g2.setStroke(new BasicStroke(thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int inset = thickness / 2 + 1;
        g2.drawArc(x + inset, y + inset, d - 2 * inset, d - 2 * inset,
                90, (int) (-360 * percent / 100.0));

        // 中心文本：百分比
        g2.setColor(getForeground());
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
        String pct = String.format("%.0f%%", percent);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(pct, x + (d - fm.stringWidth(pct)) / 2, y + (d - fm.getHeight()) / 2 + fm.getAscent());

        // 下方说明文字
        if (caption != null && !caption.isEmpty()) {
            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            g2.setColor(getForeground().darker());
            fm = g2.getFontMetrics();
            g2.drawString(caption, x + (d - fm.stringWidth(caption)) / 2, y + d - 6);
        }
        g2.dispose();
    }
}
