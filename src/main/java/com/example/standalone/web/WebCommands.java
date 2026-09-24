package com.example.standalone.web;

import com.coloryr.allmusic.server.core.command.CommandEX;
import com.example.standalone.ClientSession;
import com.example.standalone.ConsoleSender;
import com.example.standalone.LogStandalone;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Web 面板的命令执行：白名单校验后以控制台身份（天然具备管理员权限）执行服务端指令。
 */
final class WebCommands {
    private static final int MAX_LENGTH = 200;
    /** 禁止通过 Web 执行的高危指令（会直接关闭整个服务端） */
    private static final Set<String> BLOCKED = Set.of("stop");

    private WebCommands() {
    }

    /**
     * 是否允许执行：必须以 /music 开头（可省略），子命令必须在服务端已注册的指令表中
     */
    static boolean allowed(String command) {
        String cmd = normalize(command);
        if (cmd == null || cmd.length() > MAX_LENGTH) {
            return false;
        }
        String[] parts = cmd.split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) {
            return false;
        }
        String sub = parts[0].toLowerCase(Locale.ROOT);
        if (BLOCKED.contains(sub)) {
            return false;
        }
        return CommandEX.commandList.containsKey(sub) || CommandEX.commandAdminList.containsKey(sub);
    }

    /**
     * 执行命令并返回服务端输出（无法解析则为空串）
     */
    static String exec(String command) {
        String cmd = normalize(command);
        if (cmd == null) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        Consumer<String> collector = lines::add;
        LogStandalone.INSTANCE.addListener(collector);
        // addListener 会回放历史日志，这里立即丢弃，只保留本次命令产生的输出
        lines.clear();
        try {
            ClientSession.handleCommand(ConsoleSender.INSTANCE, "/music " + cmd);
            // 部分指令（点歌、封禁等）会派发到保存线程异步执行，稍作等待以收集其输出
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } finally {
            LogStandalone.INSTANCE.removeListener(collector);
        }
        return String.join("\n", lines);
    }

    /**
     * 规范化命令：去掉可能存在的 /music 前缀与首尾空白，并拒绝换行注入
     */
    private static String normalize(String command) {
        if (command == null) {
            return null;
        }
        String cmd = command.trim();
        if (cmd.isEmpty() || cmd.indexOf('\n') >= 0 || cmd.indexOf('\r') >= 0) {
            return null;
        }
        if (cmd.startsWith("/")) {
            if (!cmd.startsWith("/music")) {
                return null;
            }
            cmd = cmd.substring(6).trim();
        }
        return cmd.isEmpty() ? null : cmd;
    }
}