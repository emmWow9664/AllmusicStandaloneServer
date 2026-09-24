package com.example.standalone.gui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.util.function.Consumer;

/**
 * 左侧垂直导航菜单：固定展开，按钮为长方形、尺寸紧凑
 */
public class SideNav extends JPanel {
    private int selected = 0;
    private final String[] labels = {"仪表盘", "性能", "统计", "设置", "控制台"};
    private final String[] icons = {"▦", "⚡", "▤", "⚙", "❯"};
    private final Consumer<Integer> onSelect;
    private final JButton[] buttons = new JButton[labels.length];

    public SideNav(Consumer<Integer> onSelect) {
        this.onSelect = onSelect;
        setLayout(new java.awt.GridLayout(0, 1, 0, 4));
        setBorder(BorderFactory.createEmptyBorder(10, 8, 10, 8));

        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            JButton b = new JButton(icons[i] + "   " + labels[i]);
            b.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            b.setHorizontalAlignment(JButton.LEFT);
            b.setFocusPainted(false);
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.setMargin(new java.awt.Insets(6, 10, 6, 10));
            b.setPreferredSize(new Dimension(0, 32));
            b.setToolTipText(labels[i]);
            b.addActionListener(e -> {
                selected = idx;
                refreshSelection();
                onSelect.accept(idx);
            });
            buttons[i] = b;
            add(b);
        }
        refreshSelection();
    }

    private void refreshSelection() {
        for (int i = 0; i < buttons.length; i++) {
            buttons[i].setSelected(i == selected);
        }
    }
}
