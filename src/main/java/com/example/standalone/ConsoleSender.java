package com.example.standalone;

/**
 * 控制台发送者：GUI 命令输入框执行指令时作为发送者，拥有管理员权限
 */
public class ConsoleSender {
    public static final ConsoleSender INSTANCE = new ConsoleSender();
    public static final String NAME = "Console";

    private ConsoleSender() {
    }
}
