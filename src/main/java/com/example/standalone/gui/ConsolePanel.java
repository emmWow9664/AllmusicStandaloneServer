package com.example.standalone.gui;

import com.example.standalone.ClientSession;
import com.example.standalone.ConsoleSender;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 控制台界面：显示带 MiniMessage 颜色标记的日志文本（解析上色、隐藏标记），
 * 底部提供指令输入栏。
 */
public class ConsolePanel extends JPanel {
    private final JTextPane output = new JTextPane();
    private final StyledDocument doc = output.getStyledDocument();
    private final JTextField input = new JTextField();

    private static final Pattern TAG = Pattern.compile("<([^>]+)>");
    private static final Map<String, Color> COLORS = new HashMap<>();

    static {
        COLORS.put("black", new Color(0x000000));
        COLORS.put("dark_blue", new Color(0x0000AA));
        COLORS.put("dark_green", new Color(0x00AA00));
        COLORS.put("dark_aqua", new Color(0x00AAAA));
        COLORS.put("dark_red", new Color(0xAA0000));
        COLORS.put("dark_purple", new Color(0xAA00AA));
        COLORS.put("gold", new Color(0xFFAA00));
        COLORS.put("gray", new Color(0xAAAAAA));
        COLORS.put("dark_gray", new Color(0x555555));
        COLORS.put("blue", new Color(0x5555FF));
        COLORS.put("green", new Color(0x55FF55));
        COLORS.put("aqua", new Color(0x55FFFF));
        COLORS.put("red", new Color(0xFF5555));
        COLORS.put("light_purple", new Color(0xFF55FF));
        COLORS.put("yellow", new Color(0xFFFF55));
        COLORS.put("white", new Color(0xFFFFFF));
    }

    public ConsolePanel() {
        setLayout(new BorderLayout());
        output.setEditable(false);
        output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        output.setAutoscrolls(true);

        JScrollPane sp = new JScrollPane(output);
        sp.setBorder(null);
        sp.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        add(sp, BorderLayout.CENTER);

        // 底部指令输入栏
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        input.addActionListener(e -> runCommand());
        JButton send = new JButton("执行");
        send.addActionListener(e -> runCommand());
        bar.add(new JLabel("指令: "), BorderLayout.WEST);
        bar.add(input, BorderLayout.CENTER);
        bar.add(send, BorderLayout.EAST);
        add(bar, BorderLayout.SOUTH);
    }

    private void runCommand() {
        String text = input.getText();
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        input.setText("");
        ClientSession.handleCommand(ConsoleSender.INSTANCE, text);
    }

    /** 追加一行日志（日志监听回调，线程安全） */
    public void append(String line) {
        SwingUtilities.invokeLater(() -> {
            try {
                appendStyled(line);
                output.setCaretPosition(doc.getLength());
            } catch (Exception ignored) {
            }
        });
    }

    /** 解析 MiniMessage 颜色标签，隐藏标记并对文本上色 */
    private void appendStyled(String line) {
        Matcher m = TAG.matcher(line);
        int last = 0;
        Color cur = output.getForeground();
        while (m.find()) {
            String plain = line.substring(last, m.start());
            // 处理 "\<tag>" 转义：按普通文本原样显示
            if (plain.endsWith("\\")) {
                appendText(plain.substring(0, plain.length() - 1) + "<" + m.group(1) + ">", cur);
                last = m.end();
                continue;
            }
            if (!plain.isEmpty()) {
                appendText(plain, cur);
            }
            String tag = m.group(1);
            if (tag.startsWith("/") || tag.equalsIgnoreCase("reset")) {
                cur = output.getForeground();
            } else if (tag.length() == 7 && tag.charAt(0) == '#') {
                try {
                    cur = Color.decode(tag);
                } catch (Exception ignored) {
                }
            } else {
                Color c = COLORS.get(tag.toLowerCase());
                if (c != null) {
                    cur = c;
                }
            }
            last = m.end();
        }
        if (last < line.length()) {
            appendText(line.substring(last), cur);
        }
        appendText("\n", output.getForeground());
    }

    private void appendText(String text, Color color) {
        try {
            Style s = output.addStyle(null, null);
            StyleConstants.setFontFamily(s, Font.MONOSPACED);
            StyleConstants.setForeground(s, color);
            doc.insertString(doc.getLength(), text, s);
        } catch (BadLocationException ignored) {
        }
    }
}
