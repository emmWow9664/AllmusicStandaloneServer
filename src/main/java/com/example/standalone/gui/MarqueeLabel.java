package com.example.standalone.gui;

import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.awt.event.ActionEvent;

/**
 * 跑马灯标签：文本过长时循环滚动显示
 */
public class MarqueeLabel extends JLabel {
    private final Timer timer;
    private String fullText;
    private int offset = 0;
    private boolean active = false;

    public MarqueeLabel() {
        super("", SwingConstants.LEFT);
        timer = new Timer(120, this::tick);
        timer.setRepeats(true);
    }

    @Override
    public void setText(String text) {
        this.fullText = text == null ? "" : text;
        offset = 0;
        updateDisplay();
    }

    @Override
    public String getText() {
        return fullText;
    }

    private void updateDisplay() {
        int avail = getWidth();
        if (avail <= 0) {
            super.setText(fullText);
            return;
        }
        int textWidth = getFontMetrics(getFont()).stringWidth(fullText);
        if (textWidth <= avail) {
            stopScroll();
            super.setText(fullText);
        } else {
            startScroll();
            int len = fullText.length();
            int show = Math.max(1, (int) ((double) avail / Math.max(1, textWidth) * len));
            int end = Math.min(len, offset + show);
            String head = offset > 0 ? "… " : "";
            super.setText(head + fullText.substring(offset, end));
        }
    }

    private void startScroll() {
        if (!active) {
            active = true;
            timer.start();
        }
    }

    private void stopScroll() {
        if (active) {
            active = false;
            timer.stop();
        }
    }

    private void tick(ActionEvent e) {
        if (fullText == null || fullText.isEmpty()) {
            return;
        }
        offset++;
        if (offset >= fullText.length()) {
            offset = 0;
        }
        updateDisplay();
    }

    @Override
    public void doLayout() {
        super.doLayout();
        updateDisplay();
    }
}
