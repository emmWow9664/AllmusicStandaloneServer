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
import java.awt.Stroke;
import java.util.LinkedList;

/**
 * 轻量折线图（性能监视用），可绘制百分比曲线与文本标注
 */
public class ChartPanel extends JPanel {
    private final LinkedList<Double> values = new LinkedList<>();
    private final String unit;
    private final Color lineColor;
    private volatile String text = "";
    private volatile double current = 0;

    public ChartPanel(String unit, Color lineColor) {
        this.unit = unit;
        this.lineColor = lineColor;
        setPreferredSize(new Dimension(200, 120));
        setOpaque(true);
    }

    public void push(double v) {
        values.addLast(v);
        if (values.size() > 180) {
            values.removeFirst();
        }
        current = v;
        repaint();
    }

    public void setText(String t) {
        this.text = t;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();

        // 背景
        Color bg = getBackground();
        g2.setColor(bg);
        g2.fillRect(0, 0, w, h);

        // 网格线（25%/50%/75%）
        g2.setColor(lineColor.darker());
        for (int i = 1; i <= 3; i++) {
            int y = h - (int) (h * i / 4.0);
            g2.drawLine(0, y, w, y);
        }

        // 曲线
        int n = values.size();
        if (n > 1) {
            Stroke old = g2.getStroke();
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(lineColor);
            for (int i = 1; i < n; i++) {
                int x1 = (int) ((i - 1) * w / (double) (180 - 1));
                int x2 = (int) (i * w / (double) (180 - 1));
                double v1 = Math.max(0, Math.min(100, values.get(i - 1)));
                double v2 = Math.max(0, Math.min(100, values.get(i)));
                int y1 = h - (int) (h * v1 / 100.0);
                int y2 = h - (int) (h * v2 / 100.0);
                g2.drawLine(x1, y1, x2, y2);
            }
            g2.setStroke(old);
        }

        // 文本（字号随高度自适应，便于小尺寸展示各核心）
        int fs = Math.max(9, Math.min(20, h / 4));
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fs));
        FontMetrics fm = g2.getFontMetrics();
        String label = String.format("%.0f%%", current);
        g2.setColor(getForeground());
        g2.drawString(label, 6, fm.getAscent() + 2);
        if (text != null && !text.isEmpty() && h >= 56) {
            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            g2.setColor(getForeground().darker());
            g2.drawString(text, 8, h - 6);
        }
        g2.dispose();
    }
}
