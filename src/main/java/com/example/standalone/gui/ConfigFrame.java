package com.example.standalone.gui;

import com.coloryr.allmusic.server.core.AllMusic;
import com.example.standalone.Main;
import com.example.standalone.MusicServer;
import com.example.standalone.StandaloneConfig;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 可视化配置编辑窗口：编辑 AllMusic 的 config.json 以及独立服务端的端口配置
 */
public class ConfigFrame extends JFrame {
    private final List<Row> rows = new ArrayList<>();
    private final StandaloneConfig standaloneConfig;
    private final JTextField portField = new JTextField(10);
    private final JTextField bindHostField = new JTextField(10);
    private final JPanel formPanel = new JPanel();

    private static class Row {
        Field field;
        Object target;
        JComponent editor;
        String type;
    }

    public ConfigFrame() {
        super("配置编辑");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(560, 720);
        setLocationRelativeTo(null);

        standaloneConfig = Main.getStandaloneConfig() == null
                ? new StandaloneConfig() : Main.getStandaloneConfig();

        buildForm();
        buildButtons();
        setVisible(true);
    }

    private void buildForm() {
        formPanel.setLayout(new GridBagLayout());
        rows.clear();

        // 独立服务端配置（端口）
        JPanel serverPanel = new JPanel(new GridBagLayout());
        serverPanel.setBorder(BorderFactory.createTitledBorder("独立服务端配置"));
        portField.setText(String.valueOf(standaloneConfig.port));
        bindHostField.setText(standaloneConfig.bindHost);
        addRow(serverPanel, "服务端口", portField);
        addRow(serverPanel, "绑定地址", bindHostField);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 4, 4, 4);
        formPanel.add(serverPanel, gbc);

        // AllMusic 配置
        JPanel musicPanel = buildObject(AllMusic.getConfig(), "AllMusic 配置 (config.json)");
        gbc.gridy = 1;
        formPanel.add(musicPanel, gbc);

        setLayout(new BorderLayout());
        add(new JScrollPane(formPanel), BorderLayout.CENTER);
    }

    private void buildButtons() {
        JPanel bottom = new JPanel();
        JButton saveBtn = new JButton("保存");
        saveBtn.addActionListener(e -> save());
        JButton reloadBtn = new JButton("重新加载配置");
        reloadBtn.addActionListener(e -> {
            AllMusic.side.reload();
            buildForm();
            pack();
        });
        bottom.add(saveBtn);
        bottom.add(reloadBtn);
        add(bottom, BorderLayout.SOUTH);
    }

    /**
     * 递归构建一个对象的配置表单
     */
    private JPanel buildObject(Object obj, String title) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 4, 2, 4);

        for (Field field : obj.getClass().getFields()) {
            try {
                Object value = field.get(obj);
                if (isSimple(value)) {
                    addFieldRow(panel, obj, field, value, gbc);
                } else if (value instanceof Set) {
                    addSetRow(panel, obj, field, (Set<?>) value, gbc);
                } else if (value instanceof Map) {
                    addMapRow(panel, obj, field, (Map<?, ?>) value, gbc);
                } else if (value != null) {
                    gbc.gridy++;
                    JPanel sub = buildObject(value, field.getName());
                    gbc.weighty = 0;
                    gbc.fill = GridBagConstraints.HORIZONTAL;
                    panel.add(sub, gbc);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return panel;
    }

    private void addFieldRow(JPanel panel, Object obj, Field field, Object value, GridBagConstraints gbc) {
        String type;
        JComponent editor;
        if (value instanceof Boolean) {
            type = "bool";
            JCheckBox box = new JCheckBox();
            box.setSelected((Boolean) value);
            editor = box;
        } else if (value instanceof Integer) {
            type = "int";
            editor = new JTextField(String.valueOf(value), 12);
        } else {
            type = "string";
            editor = new JTextField(value == null ? "" : String.valueOf(value), 20);
        }
        Row row = new Row();
        row.field = field;
        row.target = obj;
        row.editor = editor;
        row.type = type;
        rows.add(row);

        addRow(panel, field.getName(), editor);
    }

    private void addSetRow(JPanel panel, Object obj, Field field, Set<?> value, GridBagConstraints gbc) {
        JTextArea area = new JTextArea(4, 30);
        for (Object o : value) {
            area.append(String.valueOf(o) + "\n");
        }
        Row row = new Row();
        row.field = field;
        row.target = obj;
        row.editor = area;
        row.type = "set";
        rows.add(row);
        addRow(panel, field.getName() + " (每行一个)", area);
    }

    private void addMapRow(JPanel panel, Object obj, Field field, Map<?, ?> value, GridBagConstraints gbc) {
        JTextArea area = new JTextArea(4, 30);
        for (Map.Entry<?, ?> e : value.entrySet()) {
            area.append(e.getKey() + "=" + e.getValue() + "\n");
        }
        Row row = new Row();
        row.field = field;
        row.target = obj;
        row.editor = area;
        row.type = "map";
        rows.add(row);
        addRow(panel, field.getName() + " (key=value 每行一个)", area);
    }

    private void addRow(JPanel panel, String label, JComponent editor) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = panel.getComponentCount();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 4, 2, 4);
        panel.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        if (editor instanceof JTextArea) {
            editor.setBorder(BorderFactory.createLineBorder(java.awt.Color.GRAY));
            panel.add(new JScrollPane(editor), gbc);
        } else {
            panel.add(editor, gbc);
        }
    }

    private boolean isSimple(Object value) {
        return value instanceof Boolean || value instanceof Integer || value instanceof String;
    }

    /**
     * 保存所有配置
     */
    private void save() {
        try {
            for (Row row : rows) {
                Object val = readEditor(row);
                if (val != null) {
                    row.field.set(row.target, val);
                }
            }
            AllMusic.saveConfig();

            int port = Integer.parseInt(portField.getText().trim());
            if (port <= 0 || port > 65535) {
                JOptionPane.showMessageDialog(this, "端口必须在 1-65535 之间");
                return;
            }
            standaloneConfig.port = port;
            standaloneConfig.bindHost = bindHostField.getText().trim();
            standaloneConfig.save();

            // 端口变化时自动重启监听
            if (MusicServer.INSTANCE.isRunning() && !String.valueOf(port).equals(MusicServer.getPortText())) {
                try {
                    MusicServer.INSTANCE.start(standaloneConfig.bindHost, standaloneConfig.port);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(this, "端口监听失败：" + e.getMessage());
                }
            }

            JOptionPane.showMessageDialog(this, "配置已保存");
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "保存失败：" + e.getMessage());
        }
    }

    private Object readEditor(Row row) {
        try {
            switch (row.type) {
                case "bool":
                    return ((JCheckBox) row.editor).isSelected();
                case "int":
                    String text = ((JTextField) row.editor).getText().trim();
                    return Integer.parseInt(text);
                case "string":
                    return ((JTextField) row.editor).getText();
                case "set": {
                    Set<String> set = new java.util.HashSet<>();
                    String[] lines = ((JTextArea) row.editor).getText().split("\\n");
                    for (String line : lines) {
                        String v = line.trim();
                        if (!v.isEmpty()) {
                            set.add(v);
                        }
                    }
                    return set;
                }
                case "map": {
                    Map<String, String> map = new java.util.HashMap<>();
                    String[] lines = ((JTextArea) row.editor).getText().split("\\n");
                    for (String line : lines) {
                        String v = line.trim();
                        if (v.isEmpty()) {
                            continue;
                        }
                        int idx = v.indexOf('=');
                        if (idx > 0) {
                            map.put(v.substring(0, idx), v.substring(idx + 1));
                        }
                    }
                    return map;
                }
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "字段解析失败，请检查输入格式");
            return null;
        }
        return null;
    }
}
