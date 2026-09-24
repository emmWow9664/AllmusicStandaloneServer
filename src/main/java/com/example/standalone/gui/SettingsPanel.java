package com.example.standalone.gui;

import com.coloryr.allmusic.server.core.AllMusic;
import com.example.standalone.StandaloneConfig;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * 设置页：原版 AllMusic 全部配置 + 独立服务端配置
 * 配置项名称含中文与英文原名，悬停展示介绍，过长文本跑马灯
 */
public class SettingsPanel extends JPanel {
    /** 配置项描述 */
    private static final class Item {
        final String path;      // 字段路径，如 limit.messageLimit
        final String cn;
        final String en;
        final String desc;
        final String type;     // bool / int / str
        JCheckBox check;
        JTextField field;

        Item(String path, String cn, String en, String desc, String type) {
            this.path = path;
            this.cn = cn;
            this.en = en;
            this.desc = desc;
            this.type = type;
        }
    }

    private final List<Item> items = new ArrayList<>();
    private final JPanel listPanel = new JPanel();

    public SettingsPanel() {
        setLayout(new BorderLayout());
        JScrollPane sp = new JScrollPane(listPanel);
        sp.setBorder(null);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        add(sp, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 6));
        JButton save = new JButton("保存配置");
        save.addActionListener(e -> saveAll());
        bottom.add(save);
        add(bottom, BorderLayout.SOUTH);

        buildItems();
        refreshValues();
    }

    private void addGroup(String title) {
        JLabel g = new JLabel(title);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        listPanel.add(g);
        listPanel.add(Box.createVerticalStrut(2));
    }

    private void addItem(Item it) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        MarqueeLabel name = new MarqueeLabel();
        name.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        name.setText(it.cn + "  (" + it.en + ")");
        name.setToolTipText(it.desc);
        row.add(name, BorderLayout.CENTER);
        if (it.type.equals("bool")) {
            it.check = new JCheckBox();
            it.check.setToolTipText(it.desc);
            row.add(it.check, BorderLayout.EAST);
        } else {
            it.field = new JTextField(18);
            it.field.setToolTipText(it.desc);
            row.add(it.field, BorderLayout.EAST);
        }
        listPanel.add(row);
        items.add(it);
    }

    private void buildItems() {
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        addGroup("基础");
        addItem(new Item("lyricDelay", "歌词延迟", "lyricDelay", "歌词显示延迟毫秒数", "int"));
        addItem(new Item("ktvLyricDelay", "KTV 歌词延迟", "ktvLyricDelay", "KTV 歌词显示延迟毫秒数", "int"));
        addItem(new Item("defaultAddMusic", "默认点歌数量", "defaultAddMusic", "默认每次点歌添加的歌曲数量", "int"));
        addItem(new Item("defaultApi", "默认音乐 API", "defaultApi", "默认使用的音乐 API 名称", "str"));
        addItem(new Item("sendDelay", "发送延迟", "sendDelay", "数据包发送间隔毫秒数", "int"));
        addItem(new Item("joinDelay", "加入延迟", "joinDelay", "玩家加入时同步播放的延迟毫秒数", "int"));
        addItem(new Item("fixSongTime", "歌曲时长修正", "fixSongTime", "播放超时修正秒数", "int"));
        addItem(new Item("playListSwitch", "歌单切歌", "playListSwitch", "空闲歌单播放完后是否自动切换", "bool"));
        addItem(new Item("playListRandom", "歌单随机", "playListRandom", "空闲歌单是否随机播放", "bool"));
        addItem(new Item("playListEscapeDeep", "歌单逃避深度", "playListEscapeDeep", "空闲歌单连续跳过深度", "int"));
        addItem(new Item("sendLyric", "发送歌词", "sendLyric", "是否向客户端发送歌词", "bool"));
        addItem(new Item("needPermission", "需要权限", "needPermission", "点歌是否需要权限", "bool"));
        addItem(new Item("topAPI", "API 置顶", "topAPI", "是否将 API 置顶显示", "bool"));
        addItem(new Item("mutePlayMessage", "静默播放消息", "mutePlayMessage", "播放歌曲时是否不广播消息", "bool"));
        addItem(new Item("muteAddMessage", "静默点歌消息", "muteAddMessage", "点歌时是否不广播消息", "bool"));
        addItem(new Item("showInBar", "物品栏显示", "showInBar", "消息是否显示在物品栏上方", "bool"));
        addItem(new Item("ktvMode", "KTV 模式", "ktvMode", "是否启用 KTV 歌词模式", "bool"));

        addGroup("限制");
        addItem(new Item("limit.messageLimit", "消息长度限制", "messageLimit", "是否限制消息长度", "bool"));
        addItem(new Item("limit.messageLimitSize", "消息长度上限", "messageLimitSize", "消息最大长度", "int"));
        addItem(new Item("limit.listLimit", "点歌队列限制", "listLimit", "是否限制点歌队列长度", "bool"));
        addItem(new Item("limit.listLimitSize", "队列长度上限", "listLimitSize", "队列最大长度", "int"));
        addItem(new Item("limit.infoLimit", "歌曲信息限制", "infoLimit", "是否限制歌曲信息显示", "bool"));
        addItem(new Item("limit.infoLimitSize", "歌曲信息上限", "infoLimitSize", "歌曲信息最大长度", "int"));
        addItem(new Item("limit.musicTimeLimit", "歌曲时长限制", "musicTimeLimit", "是否限制歌曲时长", "bool"));
        addItem(new Item("limit.maxMusicTime", "歌曲最大时长", "maxMusicTime", "歌曲最大时长秒数", "int"));
        addItem(new Item("limit.limitText", "限制提示文本", "limitText", "触发限制时的提示文本", "str"));
        addItem(new Item("limit.maxPlayList", "最大点歌列表", "maxPlayList", "每个玩家最大点歌数", "int"));
        addItem(new Item("limit.maxPlayerList", "最大玩家列表", "maxPlayerList", "最大玩家数量", "int"));

        addGroup("投票");
        addItem(new Item("vote.minVote", "最小投票数", "minVote", "切歌投票所需最小人数", "int"));
        addItem(new Item("vote.voteTime", "投票时间", "voteTime", "投票持续秒数", "int"));
        addItem(new Item("vote.voteListSize", "投票列表大小", "voteListSize", "参与投票的最大列表长度", "int"));

        addGroup("经济");
        addItem(new Item("cost.useCost", "启用花费", "useCost", "点歌是否消耗经济", "bool"));
        addItem(new Item("cost.searchCost", "搜索花费", "searchCost", "每次搜索消耗的金额", "int"));
        addItem(new Item("cost.addMusicCost", "点歌花费", "addMusicCost", "每点一首歌消耗的金额", "int"));
        addItem(new Item("economy.backend", "经济后端", "backend", "经济系统后端", "str"));

        addGroup("趣味");
        addItem(new Item("funConfig.rain", "下雨", "rain", "播放歌曲时是否下雨", "bool"));
        addItem(new Item("funConfig.rainRate", "下雨概率", "rainRate", "下雨触发概率(百分比)", "int"));

        addGroup("独立服务端");
        addItem(new Item("standalone.port", "监听端口", "port", "独立服务端 TCP 监听端口", "int"));
        addItem(new Item("standalone.bindHost", "绑定地址", "bindHost", "绑定地址(0.0.0.0 为所有网卡)", "str"));

        addGroup("关于");
        listPanel.add(info("AllmusicStandaloneServer", "AllMusic 独立音乐服务器（Standalone Server）"));
        listPanel.add(info("版本", "1.0.11"));
        listPanel.add(info("作者", "emmWow9664、DeepseekV4Flash"));
        listPanel.add(info("协议", "GPL-3.0（AllMusic 的衍生作品）"));
        listPanel.add(info("仓库", "https://github.com/emmWow9664/AllmusicStandaloneServer"));
        listPanel.add(info("说明", "实现 AllMusic 服务端插件的全部功能，可脱离 Minecraft 独立运行，"
                + "配合 AllMusicConnect 客户端模组使用。音乐解析依赖 allmusic_server/api 文件夹下的音乐 API jar。"));
    }

    private JPanel info(String name, String value) {
        JPanel p = new JPanel(new BorderLayout(8, 0));
        p.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        JLabel n = new JLabel(name);
        n.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        MarqueeLabel v = new MarqueeLabel();
        v.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        v.setText(value);
        p.add(n, BorderLayout.WEST);
        p.add(v, BorderLayout.CENTER);
        listPanel.add(p);
        return p;
    }

    private void refreshValues() {
        for (Item it : items) {
            Object val = read(it.path);
            if (val == null) {
                continue;
            }
            if (it.check != null) {
                it.check.setSelected(Boolean.TRUE.equals(val));
            } else if (it.field != null) {
                it.field.setText(String.valueOf(val));
            }
        }
    }

    private void saveAll() {
        for (Item it : items) {
            if (it.check != null) {
                write(it.path, it.check.isSelected());
            } else if (it.field != null) {
                if (it.type.equals("int")) {
                    try {
                        write(it.path, Integer.parseInt(it.field.getText().trim()));
                    } catch (NumberFormatException ignored) {
                    }
                } else {
                    write(it.path, it.field.getText());
                }
            }
        }
        AllMusic.saveConfig();
        AllMusic.log.data("<light_purple>[AllMusic]<yellow>配置已保存");
    }

    /** 读取嵌套路径字段值，如 limit.messageLimit / standalone.port */
    private static Object read(String path) {
        try {
            if (path.startsWith("standalone.")) {
                StandaloneConfig sc = MainHolder.config();
                Field f = StandaloneConfig.class.getField(path.substring(11));
                return f.get(sc);
            }
            Object obj = AllMusic.getConfig();
            for (String part : path.split("\\.")) {
                Field f = obj.getClass().getField(part);
                obj = f.get(obj);
                if (obj == null) {
                    return null;
                }
            }
            return obj;
        } catch (Exception e) {
            return null;
        }
    }

    /** 写入嵌套路径字段值 */
    private static void write(String path, Object value) {
        try {
            if (path.startsWith("standalone.")) {
                StandaloneConfig sc = MainHolder.config();
                Field f = StandaloneConfig.class.getField(path.substring(11));
                f.set(sc, value);
                sc.save();
                return;
            }
            Object obj = AllMusic.getConfig();
            String[] parts = path.split("\\.");
            for (int i = 0; i < parts.length - 1; i++) {
                Field f = obj.getClass().getField(parts[i]);
                obj = f.get(obj);
            }
            Field last = obj.getClass().getField(parts[parts.length - 1]);
            last.set(obj, value);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static final class MainHolder {
        static StandaloneConfig config() {
            return com.example.standalone.Main.getStandaloneConfig();
        }
    }
}
