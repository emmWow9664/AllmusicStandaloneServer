package com.example.standalone.gui;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * 系统信息：CPU 型号 / 内存型号（跨平台尽力获取）
 */
public final class SysInfo {
    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase().contains("win");
    private static final boolean IS_LINUX =
            System.getProperty("os.name", "").toLowerCase().contains("linux");
    private static final boolean IS_MAC =
            System.getProperty("os.name", "").toLowerCase().contains("mac");

    private SysInfo() {
    }

    public static String cpuModel() {
        // Windows：环境变量 PROCESSOR_IDENTIFIER
        if (IS_WINDOWS) {
            String id = System.getenv("PROCESSOR_IDENTIFIER");
            if (id != null && !id.trim().isEmpty()) {
                return id.trim();
            }
        }
        // Linux：/proc/cpuinfo
        if (IS_LINUX) {
            try {
                for (String line : Files.readAllLines(Paths.get("/proc/cpuinfo"))) {
                    if (line.startsWith("model name")) {
                        return line.substring(line.indexOf(':') + 1).trim();
                    }
                }
            } catch (Exception ignored) {
            }
        }
        // macOS：sysctl
        if (IS_MAC) {
            try {
                Process p = new ProcessBuilder("sysctl", "-n", "machdep.cpu.brand_string")
                        .redirectErrorStream(true).start();
                String s = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                if (!s.isEmpty()) {
                    return s;
                }
            } catch (Exception ignored) {
            }
        }
        return "未知 CPU";
    }

    public static String ramModel() {
        // Windows：PowerShell 读取物理内存条信息
        if (IS_WINDOWS) {
            String s = exec("powershell", "-NoProfile", "-Command",
                    "$m=Get-CimInstance Win32_PhysicalMemory | Select-Object -First 1; "
                            + "if($m){$m.Manufacturer + ' ' + $m.PartNumber + ' ' + [math]::Round($m.Capacity/1GB,0) + 'GB'}");
            if (s != null && !s.isEmpty() && !s.toLowerCase().contains("error")) {
                return s;
            }
        }
        // Linux：dmidecode（可能需要 root，失败则回退容量）
        if (IS_LINUX) {
            String s = exec("sh", "-c", "dmidecode -t memory 2>/dev/null | grep -m1 -i 'Part Number'");
            if (s != null && !s.isEmpty() && !s.toLowerCase().contains("unknow")) {
                return s.replace("Part Number:", "").trim();
            }
        }
        return "RAM " + (long) PerfReader.totalGb() + " GB";
    }

    private static String exec(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            if (!p.waitFor(3, TimeUnit.SECONDS)) {
                p.destroy();
                return null;
            }
            return new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return null;
        }
    }
}
