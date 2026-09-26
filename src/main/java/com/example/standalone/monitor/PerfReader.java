package com.example.standalone.monitor;

import java.lang.management.ManagementFactory;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * 跨平台性能数据读取：CPU / 内存 / 网络
 */
public final class PerfReader {
    private PerfReader() {
    }

    public static double cpuPercent() {
        try {
            var os = ManagementFactory.getOperatingSystemMXBean();
            if (os instanceof com.sun.management.OperatingSystemMXBean sun) {
                double v = sun.getCpuLoad();
                if (v >= 0) {
                    return v * 100;
                }
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    public static double ramPercent() {
        long total = totalBytes();
        long used = total - freeBytes();
        if (total <= 0) {
            return 0;
        }
        return used * 100.0 / total;
    }

    public static long totalBytes() {
        try {
            var os = ManagementFactory.getOperatingSystemMXBean();
            if (os instanceof com.sun.management.OperatingSystemMXBean sun) {
                return sun.getTotalMemorySize();
            }
        } catch (Exception ignored) {
        }
        return Runtime.getRuntime().maxMemory();
    }

    public static long freeBytes() {
        try {
            var os = ManagementFactory.getOperatingSystemMXBean();
            if (os instanceof com.sun.management.OperatingSystemMXBean sun) {
                return sun.getFreeMemorySize();
            }
        } catch (Exception ignored) {
        }
        return Runtime.getRuntime().freeMemory();
    }

    public static double usedGb() {
        return (totalBytes() - freeBytes()) / 1024.0 / 1024.0 / 1024.0;
    }

    public static double totalGb() {
        return totalBytes() / 1024.0 / 1024.0 / 1024.0;
    }

    /** 累计接收字节（getTrafficCounters 在新版 JDK 已移除，使用反射调用） */
    public static long rxBytes() {
        return traffic(false);
    }

    /** 累计发送字节 */
    public static long txBytes() {
        return traffic(true);
    }

    private static long traffic(boolean tx) {
        long total = 0;
        try {
            java.lang.reflect.Method m = NetworkInterface.class.getMethod("getTrafficCounters");
            Enumeration<NetworkInterface> it = NetworkInterface.getNetworkInterfaces();
            while (it != null && it.hasMoreElements()) {
                NetworkInterface ni = it.nextElement();
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                Object tc = m.invoke(ni);
                if (tc != null) {
                    if (tx) {
                        total += (long) tc.getClass().getMethod("getTxBytes").invoke(tc);
                    } else {
                        total += (long) tc.getClass().getMethod("getRxBytes").invoke(tc);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return total;
    }

    // ---------- 各核心 CPU 使用率 ----------

    private static final boolean IS_LINUX =
            System.getProperty("os.name", "").toLowerCase().contains("linux");
    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase().contains("win");

    private static long[] linuxTotal;
    private static long[] linuxIdle;

    /**
     * 各核心使用率（%），Linux 读 /proc/stat，Windows 用 PowerShell 查询；
     * 首次采样返回 null，不支持时返回 null。
     */
    public static double[] perCoreLoads() {
        if (IS_LINUX) {
            return perCoreLinux();
        }
        if (IS_WINDOWS) {
            return perCoreWindows();
        }
        return null;
    }

    private static double[] perCoreLinux() {
        try {
            java.util.List<String> lines = java.nio.file.Files.readAllLines(java.nio.file.Paths.get("/proc/stat"));
            java.util.List<long[]> stats = new java.util.ArrayList<>();
            for (String line : lines) {
                if (!line.startsWith("cpu") || line.startsWith("cpu ")) {
                    continue;
                }
                String[] parts = line.trim().split("\\s+");
                if (!parts[0].matches("cpu\\d+") || parts.length < 5) {
                    continue;
                }
                long total = 0;
                for (int i = 1; i < parts.length; i++) {
                    total += Long.parseLong(parts[i]);
                }
                long idle = Long.parseLong(parts[4]);
                stats.add(new long[]{total, idle});
            }
            if (stats.isEmpty()) {
                return null;
            }
            int n = stats.size();
            double[] res = new double[n];
            if (linuxTotal == null || linuxTotal.length != n) {
                linuxTotal = new long[n];
                linuxIdle = new long[n];
                for (int i = 0; i < n; i++) {
                    linuxTotal[i] = stats.get(i)[0];
                    linuxIdle[i] = stats.get(i)[1];
                }
                return null;
            }
            for (int i = 0; i < n; i++) {
                long dt = stats.get(i)[0] - linuxTotal[i];
                long di = stats.get(i)[1] - linuxIdle[i];
                res[i] = dt <= 0 ? 0 : (dt - di) * 100.0 / dt;
                linuxTotal[i] = stats.get(i)[0];
                linuxIdle[i] = stats.get(i)[1];
            }
            return res;
        } catch (Exception e) {
            return null;
        }
    }

    private static double[] perCoreWindows() {
        try {
            Process p = new ProcessBuilder("powershell", "-NoProfile", "-Command",
                    "(Get-CimInstance Win32_PerfFormattedData_PerfOS_Processor | Where-Object {$_.Name -ne '_Total'} | ForEach-Object { $_.Name + ':' + $_.PercentProcessorTime }) -join ';'")
                    .redirectErrorStream(true).start();
            String s = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
            String[] parts = s.split(";");
            double[] res = new double[parts.length];
            for (int i = 0; i < parts.length; i++) {
                String v = parts[i].contains(":") ? parts[i].substring(parts[i].indexOf(':') + 1) : parts[i];
                res[i] = Double.parseDouble(v.trim());
            }
            return res;
        } catch (Exception e) {
            return null;
        }
    }
}
