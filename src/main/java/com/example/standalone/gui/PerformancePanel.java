package com.example.standalone.gui;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 性能页：Windows 11 任务管理器同款 —— CPU（可切换各核心）/ 内存 / 网络，
 * 顶部展示 CPU 型号与内存型号。
 */
public class PerformancePanel extends JPanel {
    private final ChartPanel cpu = new ChartPanel("%", new Color(0x00B0FF));
    private final ChartPanel ram = new ChartPanel("%", new Color(0x69F0AE));
    private final ChartPanel net = new ChartPanel("KB/s", new Color(0xFFAB40));
    private final JLabel cpuDetail = new JLabel(" ");
    private final JLabel ramDetail = new JLabel(" ");
    private final JLabel netDetail = new JLabel(" ");
    private final MarqueeLabel cpuModel = new MarqueeLabel();
    private final MarqueeLabel ramModel = new MarqueeLabel();

    private final JToggleButton coreSwitch = new JToggleButton("显示各核心");
    private final JLabel coreStatus = new JLabel(" ");
    private JPanel cpuSection;
    private JPanel coreSection;
    private final JPanel corePanel = new JPanel();
    private final List<ChartPanel> coreCharts = new ArrayList<>();
    private volatile ScheduledExecutorService coreService;

    private long lastRx = -1;
    private long lastTx = -1;
    private long lastTime = 0;

    public PerformancePanel() {
        setLayout(new BorderLayout());
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(javax.swing.BorderFactory.createEmptyBorder(16, 20, 16, 20));

        // 顶部：CPU / RAM 型号
        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.add(row("CPU 型号: ", cpuModel));
        info.add(row("内存型号: ", ramModel));
        root.add(info);
        root.add(Box.createVerticalStrut(14));

        cpu.setPreferredSize(new Dimension(0, 200));
        ram.setPreferredSize(new Dimension(0, 200));
        net.setPreferredSize(new Dimension(0, 200));

        cpuSection = section("CPU", cpu, cpuDetail);
        root.add(cpuSection);

        // 各核心区域：开启时替换 CPU 总数图表，占用同一块空间
        coreSection = new JPanel(new BorderLayout());
        coreSection.setOpaque(false);
        corePanel.setOpaque(false);
        coreSection.add(corePanel, BorderLayout.CENTER);
        coreSection.setVisible(false);
        root.add(coreSection);

        root.add(Box.createVerticalStrut(6));
        // 各核心切换开关（常驻显示）
        JPanel coreBar = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 2));
        coreBar.setOpaque(false);
        coreBar.add(coreSwitch);
        coreBar.add(coreStatus);
        root.add(coreBar);
        root.add(Box.createVerticalStrut(8));

        root.add(section("内存", ram, ramDetail));
        root.add(Box.createVerticalStrut(14));
        root.add(section("网络", net, netDetail));

        // 内容过多时可滚动，避免窗口较小时被裁剪
        JScrollPane pageScroll = new JScrollPane(root);
        pageScroll.setBorder(null);
        pageScroll.getVerticalScrollBar().setUnitIncrement(16);
        add(pageScroll, BorderLayout.CENTER);

        // 型号信息
        cpuModel.setText(SysInfo.cpuModel());
        ramModel.setText(SysInfo.ramModel());

        // 各核心开关：开 → 显示各核心、隐藏 CPU 总数；关 → 反之
        coreSwitch.addActionListener(e -> applyCoreToggle(coreSwitch.isSelected()));

        new Timer(1000, e -> refresh()).start();
        refresh();
    }

    private void applyCoreToggle(boolean on) {
        if (on) {
            startCoreMonitor();
            cpuSection.setVisible(false);
            coreSection.setVisible(true);
            coreSwitch.setText("显示总使用率");
        } else {
            stopCoreMonitor();
            coreSection.setVisible(false);
            cpuSection.setVisible(true);
            coreSwitch.setText("显示各核心");
        }
        revalidate();
        repaint();
    }

    private JPanel row(String label, MarqueeLabel value) {
        JPanel p = new JPanel(new BorderLayout(8, 0));
        p.setOpaque(false);
        JLabel l = new JLabel(label);
        l.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        p.add(l, BorderLayout.WEST);
        value.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        p.add(value, BorderLayout.CENTER);
        return p;
    }

    private JPanel section(String title, ChartPanel chart, JLabel detail) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        JLabel t = new JLabel(title);
        t.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        p.add(t);
        p.add(Box.createVerticalStrut(6));
        p.add(chart);
        detail.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        p.add(Box.createVerticalStrut(4));
        p.add(detail);
        return p;
    }

    private void startCoreMonitor() {
        stopCoreMonitor();
        coreCharts.clear();
        corePanel.removeAll();
        int n = Runtime.getRuntime().availableProcessors();
        // 网格布局：所有核心同时可见（列数随核心数调整）
        int cols = n <= 4 ? 2 : (n <= 8 ? 4 : (n <= 24 ? 6 : 8));
        int rows = (int) Math.ceil(n / (double) cols);
        corePanel.setLayout(new GridLayout(rows, cols, 10, 8));
        // 每行高度按核心数压缩，保证整块区域能容纳全部核心
        int rowH = Math.max(26, Math.min(78, 300 / rows));
        corePanel.setPreferredSize(new Dimension(0, rows * rowH));
        for (int i = 0; i < n; i++) {
            // 与 CPU 总数相同的折线图展示方式，尺寸按行高自动缩放
            ChartPanel cp = new ChartPanel("%", new Color(0x00B0FF));
            cp.setPreferredSize(new Dimension(0, rowH - 16));
            JLabel lb = new JLabel("核心 " + i);
            lb.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            JPanel item = new JPanel(new BorderLayout());
            item.setOpaque(false);
            item.add(lb, BorderLayout.NORTH);
            item.add(cp, BorderLayout.CENTER);
            coreCharts.add(cp);
            corePanel.add(item);
        }
        coreStatus.setText("等待采样…");
        coreSection.revalidate();
        coreSection.repaint();

        coreService = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "per-core-monitor");
            t.setDaemon(true);
            return t;
        });
        coreService.scheduleAtFixedRate(() -> {
            double[] loads = PerfReader.perCoreLoads();
            SwingUtilities.invokeLater(() -> {
                if (loads == null) {
                    coreStatus.setText("首次采样或当前平台不支持单核查看");
                    return;
                }
                if (coreCharts.size() != loads.length) {
                    return;
                }
                for (int i = 0; i < loads.length && i < coreCharts.size(); i++) {
                    coreCharts.get(i).push(loads[i]);
                }
                coreStatus.setText(" ");
            });
        }, 2000, 2000, TimeUnit.MILLISECONDS);
    }

    private void stopCoreMonitor() {
        if (coreService != null) {
            coreService.shutdownNow();
            coreService = null;
        }
    }

    private void refresh() {
        double c = PerfReader.cpuPercent();
        cpu.push(c);
        cpuDetail.setText(String.format("使用率 %.1f%%", c));

        double r = PerfReader.ramPercent();
        ram.push(r);
        ramDetail.setText(String.format("使用率 %.1f%%   已用 %.1f GB / 共 %.1f GB",
                r, PerfReader.usedGb(), PerfReader.totalGb()));

        long rx = PerfReader.rxBytes();
        long tx = PerfReader.txBytes();
        if (lastRx >= 0) {
            long dt = System.currentTimeMillis() - lastTime;
            if (dt > 0) {
                double dk = (rx - lastRx) / 1024.0 / (dt / 1000.0);
                double uk = (tx - lastTx) / 1024.0 / (dt / 1000.0);
                net.push(dk);
                netDetail.setText(String.format("下载 %.1f KB/s   上传 %.1f KB/s", dk, uk));
            }
        } else {
            net.push(0);
            netDetail.setText("统计中…");
        }
        lastRx = rx;
        lastTx = tx;
        lastTime = System.currentTimeMillis();
    }
}
