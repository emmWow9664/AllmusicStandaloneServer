package com.example.standalone.gui;

import com.example.standalone.LogStandalone;
import com.example.standalone.Main;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.DefaultListModel;
import javax.swing.Timer;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Font;

/**
 * 独立服务端主窗口
 */
public class MainFrame extends JFrame {
    private final DefaultListModel<String> playerModel = new DefaultListModel<>();
    private final DefaultListModel<String> songModel = new DefaultListModel<>();
    private final JTextArea logArea = new JTextArea();
    private final JTextField commandField = new JTextField();
    private final JLabel statusLabel = new JLabel(" ");

    public MainFrame() {
        super("AllMusic 独立服务端");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1100, 700);
        setLocationRelativeTo(null);
        buildMenu();
        buildUI();
        LogStandalone.INSTANCE.addListener(this::appendLog);
        new Timer(500, e -> refreshLists()).start();
        setVisible(true);
    }

    private void buildMenu() {
        JMenuBar bar = new JMenuBar();

        JMenu fileMenu = new JMenu("文件");
        JMenuItem exitItem = new JMenuItem("退出");
        exitItem.addActionListener(e -> System.exit(0));
        fileMenu.add(exitItem);

        JMenu toolMenu = new JMenu("配置");
        JMenuItem configItem = new JMenuItem("配置编辑");
        configItem.addActionListener(e -> new ConfigFrame().setVisible(true));
        toolMenu.add(configItem);

        JMenu helpMenu = new JMenu("帮助");
        JMenuItem helpItem = new JMenuItem("指令帮助");
        helpItem.addActionListener(e -> showHelp());
        JMenuItem aboutItem = new JMenuItem("关于");
        aboutItem.addActionListener(e -> showAbout());
        helpMenu.add(helpItem);
        helpMenu.add(aboutItem);

        bar.add(fileMenu);
        bar.add(toolMenu);
        bar.add(helpMenu);
        setJMenuBar(bar);
    }

    private void buildUI() {
        // 左侧：玩家列表 / 歌曲列表
        JList<String> playerList = new JList<>(playerModel);
        JList<String> songList = new JList<>(songModel);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("玩家", new JScrollPane(playerList));
        tabs.addTab("歌曲列表", new JScrollPane(songList));
        tabs.setPreferredSize(new java.awt.Dimension(300, 0));

        // 右侧：日志
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("日志"));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tabs, logScroll);
        split.setResizeWeight(0.25);

        // 底部：命令输入
        JPanel cmdPanel = new JPanel(new BorderLayout());
        commandField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        commandField.addActionListener(e -> executeCommand());
        JButton sendBtn = new JButton("执行");
        sendBtn.addActionListener(e -> executeCommand());
        cmdPanel.add(new JLabel(" 指令: "), BorderLayout.WEST);
        cmdPanel.add(commandField, BorderLayout.CENTER);
        cmdPanel.add(sendBtn, BorderLayout.EAST);

        // 状态栏
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        statusPanel.add(statusLabel, BorderLayout.WEST);

        setLayout(new BorderLayout());
        add(split, BorderLayout.CENTER);
        add(cmdPanel, BorderLayout.SOUTH);
        add(statusPanel, BorderLayout.NORTH);
    }

    /**
     * GUI 指令输入框执行指令（控制台身份）
     */
    private void executeCommand() {
        String text = commandField.getText();
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        commandField.setText("");
        com.example.standalone.ClientSession.handleCommand(
                com.example.standalone.ConsoleSender.INSTANCE, text);
    }

    private void appendLog(String line) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(line + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    /**
     * 刷新玩家列表和歌曲列表
     */
    private void refreshLists() {
        java.util.List<String> players = new java.util.ArrayList<>();
        for (com.example.standalone.ClientSession c : com.example.standalone.SideStandalone.INSTANCE.getClientSessions()) {
            players.add(c.getName());
        }
        if (!same(playerModel, players)) {
            playerModel.clear();
            for (String p : players) {
                playerModel.addElement(p);
            }
        }

        java.util.List<com.coloryr.allmusic.server.core.objs.music.SongInfoObj> list =
                com.coloryr.allmusic.server.core.music.PlayMusic.getList();
        java.util.List<String> songs = new java.util.ArrayList<>();
        com.coloryr.allmusic.server.core.objs.music.SongInfoObj now = com.coloryr.allmusic.server.core.music.PlayMusic.nowPlayMusic;
        if (now != null) {
            songs.add("[正在播放] " + now.getName() + " - " + now.getAuthor() + " (点歌: " + now.getCall() + ")");
        }
        int index = 1;
        for (com.coloryr.allmusic.server.core.objs.music.SongInfoObj s : list) {
            songs.add((index++) + ". " + s.getName() + " - " + s.getAuthor() + " (点歌: " + s.getCall() + ")");
        }
        if (!same(songModel, songs)) {
            songModel.clear();
            for (String s : songs) {
                songModel.addElement(s);
            }
        }

        statusLabel.setText("端口: " + com.example.standalone.MusicServer.getPortText()
                + "  |  在线玩家: " + players.size()
                + "  |  队列: " + list.size());
    }

    private boolean same(DefaultListModel<String> model, java.util.List<String> list) {
        if (model.size() != list.size()) {
            return false;
        }
        for (int i = 0; i < model.size(); i++) {
            if (!model.get(i).equals(list.get(i))) {
                return false;
            }
        }
        return true;
    }

    public void updateNowPlaying() {
        refreshLists();
    }

    public void onPlayerJoin(String name) {
        refreshLists();
    }

    public void onPlayerLeave(String name) {
        refreshLists();
    }

    private void showHelp() {
        String help =
                "普通玩家指令\n" +
                "/music [音乐ID/网易云分享链接] 点歌\n" +
                "/music [音乐API] [音乐ID] 点歌\n" +
                "/music stop 停止播放歌曲\n" +
                "/music list 查看歌曲队列\n" +
                "/music cancel [序号] 取消你的点歌\n" +
                "/music vote 投票切歌\n" +
                "/music vote cancel 取消发起的切歌\n" +
                "/music push [序号] 投票将歌曲插入到队列头\n" +
                "/music push cancel 取消发起的插歌\n" +
                "/music mute 不再参与点歌，再输入一次恢复\n" +
                "/music mute list 不接收空闲列表点歌，再输入一次恢复\n" +
                "/music search [歌名] 搜索歌曲\n" +
                "/music searchapi [音乐API] [歌名] 搜索歌曲\n" +
                "/music select [序列] 选择歌曲\n" +
                "/music nextpage 切换下一页歌曲搜索结果\n" +
                "/music lastpage 切换上一页歌曲搜索结果\n" +
                "/music hud ... 界面相关设置\n" +
                "管理员指令\n" +
                "/music reload 重读配置文件\n" +
                "/music next 强制切歌\n" +
                "/music ban [ID] 禁止点这首歌\n" +
                "/music unban [ID] 解禁点这首歌\n" +
                "/music banplayer [ID] 禁止某位玩家点歌\n" +
                "/music unbanplayer [ID] 解禁某位玩家点歌\n" +
                "/music delete [序号] 删除队列中的歌曲\n" +
                "/music addlist [歌单ID] 添加歌单到空闲列表\n" +
                "/music clearlist 清空空闲歌单\n" +
                "/music clearban 清空禁止点歌列表\n" +
                "/music clearbanplayer 清空禁止点歌玩家列表\n" +
                "/music test [ID] 测试歌曲内容解析\n" +
                "更多信息请查看项目仓库下的 README.md";
        JOptionPane.showMessageDialog(this, help, "指令帮助", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showAbout() {
        JOptionPane.showMessageDialog(this,
                "AllMusic 独立服务端\n\n" +
                "本程序实现了 AllMusic 服务端插件的全部功能，\n" +
                "并提供一个图形界面用于管理。\n\n" +
                "配合客户端模组 AllMusicConnect（/music connect 地址 端口）\n" +
                "以及 AllMusic 客户端模组使用。\n\n" +
                "音乐解析依赖 allmusic_server/api 文件夹下的音乐 API jar。\n" +
                "参考 API：https://github.com/Coloryr/netapi\n\n" +
                "基于 GPL v3 开源协议，代码来自 https://github.com/Coloryr/AllMusic",
                "关于", JOptionPane.INFORMATION_MESSAGE);
    }
}
