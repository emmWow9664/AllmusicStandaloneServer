package com.example.standalone.gui;

import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.music.PlayMusic;
import com.coloryr.allmusic.server.core.objs.music.SongInfoObj;
import com.coloryr.allmusic.server.core.saves.BanSave;
import com.example.standalone.ClientSession;
import com.example.standalone.SideStandalone;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 仪表盘：歌曲 / 歌曲队列 / 在线玩家 / 性能监视 四模块同时展示
 */
public class DashboardPanel extends JPanel {
    // 歌曲模块
    private final MarqueeLabel songName = new MarqueeLabel();
    private final JLabel songCall = new JLabel(" ");
    private final JProgressBar progress = new JProgressBar(0, 10000);
    private final JLabel timeLabel = new JLabel("0:00 / 0:00");

    // 队列模块
    private final DefaultListModel<QueueRow> queueModel = new DefaultListModel<>();
    private final JList<QueueRow> queueList = new JList<>(queueModel);

    // 玩家模块
    private final DefaultListModel<PlayerRow> playerModel = new DefaultListModel<>();
    private final JList<PlayerRow> playerList = new JList<>(playerModel);
    /** 玩家卡片标题：附带展示今日连接人数 */
    private final JLabel playerCardTitle = new JLabel("👤 在线玩家");

    // 性能模块（圆环）
    private final RingChart cpuRing = new RingChart(new Color(0x00B0FF));
    private final RingChart ramRing = new RingChart(new Color(0x69F0AE));
    private final RingChart netRing = new RingChart(new Color(0xFFAB40));
    private long lastRx = -1;
    private long lastTx = -1;
    private long lastNetTime = 0;

    // 交互状态：悬停/按下的行与操作列
    private int hoverRow = -1;
    private int hoverCol = -1;
    private int pressRow = -1;
    private int pressCol = -1;
    private boolean hoverOnQueue = true;

    private static final Color BTN_RED = new Color(0xE53935);
    private static final Color BTN_GREEN = new Color(0x43A047);
    private static final Color BTN_GRAY = new Color(0x6C757D);

    public DashboardPanel() {
        setLayout(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 6, 6, 6);
        g.fill = GridBagConstraints.BOTH;
        g.weightx = 1;
        g.weighty = 1;

        g.gridx = 0;
        g.gridy = 0;
        g.gridwidth = 2;
        g.weighty = 0.9;
        add(card("♪ 当前歌曲", buildSongPanel()), g);

        g.gridx = 2;
        g.gridy = 0;
        g.gridwidth = 1;
        g.weighty = 0.9;
        g.weightx = 0.8;
        add(card("⚡ 性能监视", buildPerfPanel()), g);

        g.gridx = 0;
        g.gridy = 1;
        g.gridwidth = 2;
        g.weighty = 1.6;
        g.weightx = 1.4;
        add(card("▤ 歌曲列表", buildQueuePanel()), g);

        g.gridx = 2;
        g.gridy = 1;
        g.gridwidth = 1;
        g.weighty = 1.6;
        g.weightx = 0.8;
        playerCardTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        add(card(playerCardTitle, buildPlayerPanel()), g);

        // 刷新
        new Timer(500, e -> refresh()).start();
        refresh();
    }

    private JPanel card(String title, Component content) {
        return card(cardTitle(title), content);
    }

    private JPanel card(JLabel title, Component content) {
        JPanel p = new JPanel(new BorderLayout(6, 6));
        p.add(title, BorderLayout.NORTH);
        p.add(content, BorderLayout.CENTER);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0, 0, 0, 40)),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        return p;
    }

    private JLabel cardTitle(String text) {
        JLabel t = new JLabel(text);
        t.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        return t;
    }

    private JPanel buildSongPanel() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        songName.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        songCall.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));

        JButton nextBtn = new JButton("▶ 切歌");
        nextBtn.setToolTipText("执行 /music next");
        nextBtn.addActionListener(e -> GuiActions.next());

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 4));
        right.setOpaque(false);
        right.add(nextBtn);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(songName, BorderLayout.CENTER);
        top.add(right, BorderLayout.EAST);
        p.add(top, BorderLayout.NORTH);
        p.add(songCall, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(8, 0));
        bottom.setOpaque(false);
        bottom.add(progress, BorderLayout.CENTER);
        bottom.add(timeLabel, BorderLayout.EAST);
        p.add(bottom, BorderLayout.SOUTH);
        return p;
    }

    private JScrollPane buildQueuePanel() {
        queueList.setCellRenderer(new QueueRenderer());
        queueList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int idx = queueList.locationToIndex(e.getPoint());
                if (idx >= 0 && idx < queueModel.size()) {
                    int col = queueActionCol(e.getX(), queueList.getWidth());
                    if (col >= 0) {
                        pressRow = idx;
                        pressCol = col;
                        queueList.repaint();
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                int idx = queueList.locationToIndex(e.getPoint());
                if (idx >= 0 && idx < queueModel.size() && pressRow == idx) {
                    QueueRow row = queueModel.get(idx);
                    int col = queueActionCol(e.getX(), queueList.getWidth());
                    if (col == 0) {
                        GuiActions.deleteQueue(idx + 1);
                    } else if (col == 1) {
                        GuiActions.banMusic(row.id);
                    }
                }
                pressRow = -1;
                pressCol = -1;
                queueList.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoverRow = -1;
                hoverCol = -1;
                queueList.repaint();
            }
        });
        queueList.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int idx = queueList.locationToIndex(e.getPoint());
                if (idx >= 0 && idx < queueModel.size()) {
                    hoverRow = idx;
                    hoverCol = queueActionCol(e.getX(), queueList.getWidth());
                } else {
                    hoverRow = -1;
                    hoverCol = -1;
                }
                queueList.repaint();
            }
        });
        JScrollPane sp = new JScrollPane(queueList);
        sp.setBorder(null);
        return sp;
    }

    private JScrollPane buildPlayerPanel() {
        playerList.setCellRenderer(new PlayerRenderer());
        playerList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int idx = playerList.locationToIndex(e.getPoint());
                if (idx >= 0 && idx < playerModel.size()
                        && e.getX() > playerList.getWidth() - 70) {
                    pressRow = idx;
                    pressCol = 0;
                    playerList.repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                int idx = playerList.locationToIndex(e.getPoint());
                if (idx >= 0 && idx < playerModel.size() && pressRow == idx
                        && e.getX() > playerList.getWidth() - 70) {
                    PlayerRow row = playerModel.get(idx);
                    if (isBanned(row.name)) {
                        GuiActions.unbanPlayer(row.name);
                    } else {
                        GuiActions.banPlayer(row.name);
                    }
                }
                pressRow = -1;
                pressCol = -1;
                playerList.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoverRow = -1;
                hoverCol = -1;
                playerList.repaint();
            }
        });
        playerList.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int idx = playerList.locationToIndex(e.getPoint());
                boolean onBtn = idx >= 0 && idx < playerModel.size()
                        && e.getX() > playerList.getWidth() - 70;
                if (onBtn) {
                    hoverRow = idx;
                    hoverCol = 0;
                } else {
                    hoverRow = -1;
                    hoverCol = -1;
                }
                playerList.repaint();
            }
        });
        JScrollPane sp = new JScrollPane(playerList);
        sp.setBorder(null);
        return sp;
    }

    /** 队列操作列命中：0=移除，1=BAN，-1=未命中 */
    private int queueActionCol(int x, int width) {
        if (x > width - 130 && x < width - 66) {
            return 0;
        }
        if (x > width - 62) {
            return 1;
        }
        return -1;
    }

    private boolean isBanned(String name) {
        try {
            return BanSave.checkBanPlayer(name);
        } catch (Exception e) {
            return false;
        }
    }

    private JPanel buildPerfPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        // 放大圆环、调细线条
        cpuRing.setThickness(6);
        ramRing.setThickness(6);
        netRing.setThickness(6);
        cpuRing.setPreferredSize(new Dimension(0, 150));
        ramRing.setPreferredSize(new Dimension(0, 150));
        netRing.setPreferredSize(new Dimension(0, 150));
        p.add(row("CPU"));
        p.add(cpuRing);
        p.add(Box.createVerticalStrut(8));
        p.add(row("内存"));
        p.add(ramRing);
        p.add(Box.createVerticalStrut(8));
        p.add(row("网络"));
        p.add(netRing);
        return p;
    }

    private JPanel row(String name) {
        JPanel r = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        r.setOpaque(false);
        JLabel l = new JLabel(name);
        l.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        r.add(l);
        return r;
    }

    private void refresh() {
        // 歌曲
        SongInfoObj now = PlayMusic.nowPlayMusic;
        if (now != null) {
            songName.setText(now.getName() + "  —  " + now.getAuthor());
            songCall.setText("点歌玩家：" + now.getCall());
            long cur = PlayMusic.musicNowTime;
            long all = PlayMusic.musicAllTime;
            if (all > 0) {
                progress.setValue((int) (cur * 10000 / all));
            } else {
                progress.setValue(0);
            }
            timeLabel.setText(fmt(cur) + " / " + fmt(all));
        } else {
            songName.setText("没有正在播放的歌曲");
            songCall.setText(" ");
            progress.setValue(0);
            timeLabel.setText("0:00 / 0:00");
        }

        // 队列
        List<SongInfoObj> list = PlayMusic.getList();
        List<QueueRow> rows = new java.util.ArrayList<>();
        for (SongInfoObj s : list) {
            rows.add(new QueueRow(s.getName(), s.getAuthor(), s.getCall(), s.getId(), s.getLength()));
        }
        syncQueue(rows);

        // 玩家
        long nowMs = System.currentTimeMillis();
        java.util.Collection<ClientSession> sessions = SideStandalone.INSTANCE.getClientSessions();
        List<PlayerRow> prows = new java.util.ArrayList<>();
        for (ClientSession c : sessions) {
            prows.add(new PlayerRow(c.getName(), nowMs - c.getConnectTime()));
        }
        syncPlayers(prows);
        playerCardTitle.setText("👤 在线玩家 · 今日连接 " + StatsManager.getTodayPlayerCount() + " 人");

        // 性能
        refreshPerf();
    }

    private void syncQueue(List<QueueRow> rows) {
        if (queueModel.size() != rows.size()) {
            queueModel.clear();
            for (QueueRow r : rows) {
                queueModel.addElement(r);
            }
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            queueModel.set(i, rows.get(i));
        }
    }

    private void syncPlayers(List<PlayerRow> rows) {
        if (playerModel.size() != rows.size()) {
            playerModel.clear();
            for (PlayerRow r : rows) {
                playerModel.addElement(r);
            }
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            playerModel.set(i, rows.get(i));
        }
    }

    private void refreshPerf() {
        double cpu = PerfReader.cpuPercent();
        cpuRing.setValue(cpu, String.format("%.1f%%", cpu));
        double ram = PerfReader.ramPercent();
        ramRing.setValue(ram, String.format("%.1f GB / %.1f GB", PerfReader.usedGb(), PerfReader.totalGb()));
        long rx = PerfReader.rxBytes();
        long tx = PerfReader.txBytes();
        if (lastRx >= 0) {
            long dt = System.currentTimeMillis() - lastNetTime;
            if (dt > 0) {
                double dk = (rx - lastRx) / 1024.0 / (dt / 1000.0);
                double uk = (tx - lastTx) / 1024.0 / (dt / 1000.0);
                double total = dk + uk;
                netRing.setValue(Math.min(100, total), String.format("↓%.1f ↑%.1f KB/s", dk, uk));
            }
        } else {
            netRing.setValue(0, "统计中…");
        }
        lastRx = rx;
        lastTx = tx;
        lastNetTime = System.currentTimeMillis();
    }

    static String fmt(long ms) {
        return String.format("%d:%02d", TimeUnit.MILLISECONDS.toMinutes(ms),
                TimeUnit.MILLISECONDS.toSeconds(ms) % 60);
    }

    /** 创建一个"按钮样式的标签" */
    private JLabel actionBtn(String text, Color base) {
        JLabel b = new JLabel(text);
        b.setOpaque(true);
        b.setForeground(Color.WHITE);
        b.setHorizontalAlignment(SwingConstants.CENTER);
        b.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        b.setBackground(base);
        b.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        return b;
    }

    public static class QueueRow {
        final String name;
        final String author;
        final String call;
        final String id;
        final long length;

        QueueRow(String name, String author, String call, String id, long length) {
            this.name = name == null ? "" : name;
            this.author = author == null ? "" : author;
            this.call = call == null ? "" : call;
            this.id = id == null ? "" : id;
            this.length = length;
        }
    }

    public static class PlayerRow {
        final String name;
        final long onlineMs;

        PlayerRow(String name, long onlineMs) {
            this.name = name == null ? "" : name;
            this.onlineMs = onlineMs;
        }
    }

    /** 队列行渲染：跑马灯歌名 + 点歌者 + 时长 + 文字按钮（移除 | BAN/UNBAN） */
    private class QueueRenderer extends JPanel implements ListCellRenderer<QueueRow> {
        private final MarqueeLabel nameLbl = new MarqueeLabel();
        private final JLabel infoLbl = new JLabel();
        private final JLabel delBtn;
        private final JLabel banBtn;

        QueueRenderer() {
            setLayout(new BorderLayout(6, 0));
            setOpaque(true);
            nameLbl.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            infoLbl.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            delBtn = actionBtn("移除", BTN_GRAY);
            banBtn = actionBtn("BAN", BTN_RED);
            JPanel op = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
            op.setOpaque(false);
            op.add(delBtn);
            op.add(banBtn);
            add(nameLbl, BorderLayout.CENTER);
            add(infoLbl, BorderLayout.SOUTH);
            add(op, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends QueueRow> list, QueueRow value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            nameLbl.setText(value.name);
            infoLbl.setText("点歌: " + value.call + "   时长: " + fmt(value.length));
            String defaultApi = AllMusic.getConfig() == null ? "" : AllMusic.getConfig().defaultApi;
            boolean banned = !value.id.isEmpty() && defaultApi != null
                    && BanSave.checkBanMusic(value.id, defaultApi);
            if (banned) {
                banBtn.setText("UNBAN");
                banBtn.setBackground(btnColor(BTN_GREEN, index, 1));
            } else {
                banBtn.setText("BAN");
                banBtn.setBackground(btnColor(BTN_RED, index, 1));
            }
            delBtn.setBackground(btnColor(BTN_GRAY, index, 0));
            // 悬停/按下反馈
            applyFeedback(delBtn, index, 0);
            applyFeedback(banBtn, index, 1);
            if (isSelected) {
                setBackground(list.getSelectionBackground());
                nameLbl.setForeground(list.getSelectionForeground());
                infoLbl.setForeground(list.getSelectionForeground().darker());
            } else {
                setBackground(list.getBackground());
                nameLbl.setForeground(list.getForeground());
                infoLbl.setForeground(list.getForeground().darker());
            }
            return this;
        }
    }

    /** 玩家行渲染：名字 + 在线时长 + 文字按钮（BAN/UNBAN） */
    private class PlayerRenderer extends JPanel implements ListCellRenderer<PlayerRow> {
        private final JLabel nameLbl = new JLabel();
        private final JLabel timeLbl = new JLabel();
        private final JLabel banBtn;

        PlayerRenderer() {
            setLayout(new BorderLayout(6, 0));
            setOpaque(true);
            nameLbl.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            timeLbl.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            banBtn = actionBtn("BAN", BTN_RED);
            JPanel op = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
            op.setOpaque(false);
            op.add(banBtn);
            add(nameLbl, BorderLayout.CENTER);
            add(timeLbl, BorderLayout.SOUTH);
            add(op, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends PlayerRow> list, PlayerRow value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            nameLbl.setText(value.name);
            timeLbl.setText("连接时长: " + fmt(value.onlineMs));
            boolean banned = isBanned(value.name);
            if (banned) {
                banBtn.setText("UNBAN");
                banBtn.setBackground(btnColor(BTN_GREEN, index, 0));
            } else {
                banBtn.setText("BAN");
                banBtn.setBackground(btnColor(BTN_RED, index, 0));
            }
            applyFeedback(banBtn, index, 0);
            if (isSelected) {
                setBackground(list.getSelectionBackground());
                nameLbl.setForeground(list.getSelectionForeground());
                timeLbl.setForeground(list.getSelectionForeground().darker());
            } else {
                setBackground(list.getBackground());
                nameLbl.setForeground(list.getForeground());
                timeLbl.setForeground(list.getForeground().darker());
            }
            return this;
        }
    }

    /** 悬停/按下反馈：按下取更暗、悬停取更亮 */
    private void applyFeedback(JLabel btn, int row, int col) {
        if (pressRow == row && pressCol == col) {
            btn.setBackground(btn.getBackground().darker());
        } else if (hoverRow == row && hoverCol == col) {
            btn.setBackground(btn.getBackground().brighter());
        }
    }

    private Color btnColor(Color base, int row, int col) {
        return base;
    }
}
