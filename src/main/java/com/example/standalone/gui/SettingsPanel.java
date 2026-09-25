package com.example.standalone.gui;

import com.coloryr.allmusic.server.core.AllMusic;
import com.example.standalone.ConfigCatalog;
import com.example.standalone.web.WebAuth;
import com.example.standalone.web.WebServer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 设置页：原版 AllMusic 全部配置 + 独立服务端配置
 * 配置项名称含中文与英文原名，悬停展示介绍，过长文本跑马灯
 */
public class SettingsPanel extends JPanel {
    /** 一行配置对应的界面控件（配置项清单与读写来自 {@link ConfigCatalog}，与控制台指令共用） */
    private static final class Row {
        final ConfigCatalog.Item item;
        JCheckBox check;
        JTextField field;

        Row(ConfigCatalog.Item item) {
            this.item = item;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private final JPanel listPanel = new JPanel();
    private final JPasswordField webPwdField = new JPasswordField(14);
    private final JLabel webPwdState = new JLabel();

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

    private void addItem(Row row) {
        ConfigCatalog.Item it = row.item;
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        MarqueeLabel name = new MarqueeLabel();
        name.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        name.setText(it.cn + "  (" + it.en + ")");
        name.setToolTipText(it.desc);
        panel.add(name, BorderLayout.CENTER);
        if (it.type.equals(ConfigCatalog.BOOL)) {
            row.check = new JCheckBox();
            row.check.setToolTipText(it.desc);
            panel.add(row.check, BorderLayout.EAST);
        } else {
            row.field = new JTextField(18);
            row.field.setToolTipText(it.desc);
            panel.add(row.field, BorderLayout.EAST);
        }
        listPanel.add(panel);
        rows.add(row);
    }

    private void buildItems() {
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        // 配置项清单来自 ConfigCatalog（与控制台 server config 指令共用同一份定义）
        for (ConfigCatalog.Group group : ConfigCatalog.groups()) {
            addGroup(group.title);
            for (ConfigCatalog.Item item : group.items) {
                addItem(new Row(item));
            }
        }
        // Web 管理员密码不写进配置文件（只保存哈希），因此不属于 ConfigCatalog
        addWebPasswordItem();

        addGroup("关于");
        listPanel.add(info("AllmusicStandaloneServer", "AllMusic 独立音乐服务器（Standalone Server）"));
        listPanel.add(info("版本", com.example.standalone.Main.getVersion()));
        listPanel.add(info("作者", "emmWow9664、DeepseekV4Flash"));
        listPanel.add(info("协议", "GPL-3.0（AllMusic 的衍生作品）"));
        listPanel.add(info("仓库", "https://github.com/emmWow9664/AllmusicStandaloneServer"));
        listPanel.add(info("Web 面板", WebServer.INSTANCE.isRunning()
                ? WebServer.INSTANCE.getUrlText() : "未启动（可在上方「Web 面板」开启后重启）"));
        listPanel.add(info("说明", "实现 AllMusic 服务端插件的全部功能，可脱离 Minecraft 独立运行，"
                + "配合 AllMusicConnect 客户端模组使用。音乐解析依赖 allmusic_server/api 文件夹下的音乐 API jar。"));
    }

    /**
     * Web 管理员密码设置行。
     * <p>
     * 密码不参与配置文件的明文读写（{@link Item} 会把内容写进 json），
     * 这里只把哈希交给 {@link WebAuth} 保存。
     */
    private void addWebPasswordItem() {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        MarqueeLabel name = new MarqueeLabel();
        name.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        name.setText("Web 管理员密码  (webAdminPassword)");
        name.setToolTipText("设置 Web 面板的管理员密码，仅保存 PBKDF2 加盐哈希，不保存明文");

        JButton setBtn = new JButton("设置密码");
        setBtn.setToolTipText("保存当前输入的密码（至少 4 位）");
        setBtn.addActionListener(e -> {
            char[] pwd = webPwdField.getPassword();
            if (pwd.length < 4) {
                Arrays.fill(pwd, '\0');
                webPwdField.setText("");
                setWebState("密码至少 4 位", false);
                return;
            }
            webAuth().setPassword(pwd);
            Arrays.fill(pwd, '\0');
            webPwdField.setText("");
            refreshWebPasswordState();
        });

        JButton clearBtn = new JButton("清除");
        clearBtn.setToolTipText("清除管理员密码，Web 管理功能将无法登录");
        clearBtn.addActionListener(e -> {
            webAuth().clearPassword();
            refreshWebPasswordState();
        });

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);
        right.add(webPwdField);
        right.add(setBtn);
        right.add(clearBtn);
        right.add(webPwdState);

        row.add(name, BorderLayout.CENTER);
        row.add(right, BorderLayout.EAST);
        listPanel.add(row);
        refreshWebPasswordState();
    }

    private void refreshWebPasswordState() {
        boolean set = webAuth().hasPassword();
        setWebState(set ? "已设置" : "未设置", set);
    }

    private void setWebState(String text, boolean ok) {
        webPwdState.setText(text);
        webPwdState.setForeground(ok ? new Color(0x2E7D32) : new Color(0xC62828));
    }

    /** Web 凭据管理器（Web 服务未启动时也能设置密码） */
    private static WebAuth webAuth() {
        return WebServer.INSTANCE.auth(
                new java.io.File(com.example.standalone.Main.getBaseDir(), AllMusic.SERVER_DIR));
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
        for (Row row : rows) {
            Object val = ConfigCatalog.read(row.item.path);
            if (val == null) {
                continue;
            }
            if (row.check != null) {
                row.check.setSelected(Boolean.TRUE.equals(val));
            } else if (row.field != null) {
                row.field.setText(String.valueOf(val));
            }
        }
    }

    private void saveAll() {
        for (Row row : rows) {
            if (row.check != null) {
                ConfigCatalog.write(row.item.path, String.valueOf(row.check.isSelected()));
            } else if (row.field != null) {
                ConfigCatalog.write(row.item.path, row.field.getText());
            }
        }
        ConfigCatalog.saveAll();
        AllMusic.log.data("<light_purple>[AllMusic]<yellow>配置已保存");
    }
}
