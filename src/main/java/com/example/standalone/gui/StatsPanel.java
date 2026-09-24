package com.example.standalone.gui;

import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.saves.BanSave;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 统计页：顶部歌曲/玩家选项卡
 */
public class StatsPanel extends JPanel {
    private final JTabbedPane tabs = new JTabbedPane();
    private final JTextField songSearch = new JTextField(16);
    private final JToggleButton songBanOnly = new JToggleButton("仅显示被 BAN");
    private final JTextField playerSearch = new JTextField(16);
    private final JToggleButton playerBanOnly = new JToggleButton("仅显示被 BAN");
    private final DefaultTableModel songModel;
    private final DefaultTableModel playerModel;
    private final JTable songTable;
    private final JTable playerTable;
    private static final SimpleDateFormat DATE = new SimpleDateFormat("MM-dd HH:mm");

    public StatsPanel() {
        setLayout(new BorderLayout());
        songModel = new DefaultTableModel(new String[]{"歌曲", "歌曲ID", "点歌玩家", "时间", "状态", "操作"}, 0) {
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        playerModel = new DefaultTableModel(new String[]{"玩家", "点歌次数", "累计连接时长", "首次连接", "状态", "操作"}, 0) {
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        songTable = new JTable(songModel);
        playerTable = new JTable(playerModel);
        songTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        playerTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // 操作列按钮样式（歌曲/玩家均为第 5 列）
        songTable.getColumnModel().getColumn(5).setCellRenderer(new ActionRenderer());
        playerTable.getColumnModel().getColumn(5).setCellRenderer(new ActionRenderer());

        // 歌曲操作列点击：BAN / UNBAN
        songTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = songTable.rowAtPoint(e.getPoint());
                int col = songTable.columnAtPoint(e.getPoint());
                if (row < 0 || col != 5) {
                    return;
                }
                String id = (String) songModel.getValueAt(row, 1);
                String action = String.valueOf(songModel.getValueAt(row, 5));
                if ("UNBAN".equals(action)) {
                    GuiActions.unbanMusic(id);
                } else if ("BAN".equals(action)) {
                    GuiActions.banMusic(id);
                }
            }
        });
        playerTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = playerTable.rowAtPoint(e.getPoint());
                int col = playerTable.columnAtPoint(e.getPoint());
                if (row < 0 || col != 5) {
                    return;
                }
                String name = (String) playerModel.getValueAt(row, 0);
                String action = String.valueOf(playerModel.getValueAt(row, 5));
                if ("UNBAN".equals(action)) {
                    GuiActions.unbanPlayer(name);
                } else if ("BAN".equals(action)) {
                    GuiActions.banPlayer(name);
                }
            }
        });

        tabs.addTab("歌曲", buildSongTab());
        tabs.addTab("玩家", buildPlayerTab());
        add(tabs, BorderLayout.CENTER);

        new javax.swing.Timer(1000, e -> refresh()).start();
        refresh();
    }

    private JPanel buildSongTab() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        bar.add(new JLabel("搜索歌曲:"));
        bar.add(songSearch);
        bar.add(songBanOnly);
        p.add(bar, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(songTable);
        sp.setBorder(null);
        p.add(sp, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildPlayerTab() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        bar.add(new JLabel("搜索玩家:"));
        bar.add(playerSearch);
        bar.add(playerBanOnly);
        p.add(bar, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(playerTable);
        sp.setBorder(null);
        p.add(sp, BorderLayout.CENTER);
        return p;
    }

    private void refresh() {
        refreshSongs();
        refreshPlayers();
    }

    private void refreshSongs() {
        List<StatsManager.SongRecord> list = new ArrayList<>(StatsManager.getSongs());
        String kw = songSearch.getText().trim().toLowerCase();
        boolean onlyBan = songBanOnly.isSelected();
        String defaultApi = AllMusic.getConfig() == null ? "" : AllMusic.getConfig().defaultApi;
        songModel.setRowCount(0);
        for (StatsManager.SongRecord s : list) {
            if (!kw.isEmpty() && !s.name.toLowerCase().contains(kw)) {
                continue;
            }
            boolean banned = defaultApi != null && BanSave.checkBanMusic(s.id, defaultApi);
            if (onlyBan && !banned) {
                continue;
            }
            songModel.addRow(new Object[]{
                    s.name,
                    s.id,
                    s.player,
                    DATE.format(new Date(s.time)),
                    banned ? "已 BAN" : "正常",
                    banned ? "UNBAN" : "BAN"
            });
        }
    }

    private void refreshPlayers() {
        List<StatsManager.PlayerRecord> list = new ArrayList<>(StatsManager.getPlayers());
        String kw = playerSearch.getText().trim().toLowerCase();
        boolean onlyBan = playerBanOnly.isSelected();
        playerModel.setRowCount(0);
        for (StatsManager.PlayerRecord s : list) {
            if (!kw.isEmpty() && !s.name.toLowerCase().contains(kw)) {
                continue;
            }
            boolean banned = BanSave.checkBanPlayer(s.name);
            if (onlyBan && !banned) {
                continue;
            }
            playerModel.addRow(new Object[]{
                    s.name,
                    s.songCount,
                    fmtDuration(s.totalConnectMs),
                    DATE.format(new Date(s.firstConnect)),
                    banned ? "已 BAN" : "正常",
                    banned ? "UNBAN" : "BAN"
            });
        }
    }

    /** 时长格式化：x天x小时x分 */
    private static String fmtDuration(long ms) {
        long s = ms / 1000;
        long d = s / 86400;
        long h = (s % 86400) / 3600;
        long m = (s % 3600) / 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) {
            sb.append(d).append("天");
        }
        if (h > 0 || d > 0) {
            sb.append(h).append("小时");
        }
        sb.append(m).append("分");
        return sb.toString();
    }

    /** 操作列按钮样式渲染：BAN 红底白字、UNBAN 绿底白字 */
    private static class ActionRenderer extends javax.swing.table.DefaultTableCellRenderer {
        private static final Color RED = new Color(0xE53935);
        private static final Color GREEN = new Color(0x43A047);

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            JLabel l = (JLabel) super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
            String v = String.valueOf(value);
            l.setHorizontalAlignment(JLabel.CENTER);
            l.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            if ("BAN".equals(v)) {
                l.setOpaque(true);
                l.setBackground(RED);
                l.setForeground(Color.WHITE);
            } else if ("UNBAN".equals(v)) {
                l.setOpaque(true);
                l.setBackground(GREEN);
                l.setForeground(Color.WHITE);
            } else {
                l.setOpaque(true);
                l.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
                l.setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
            }
            l.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
            return l;
        }
    }
}
