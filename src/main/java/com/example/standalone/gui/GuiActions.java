package com.example.standalone.gui;

import com.example.standalone.ClientSession;
import com.example.standalone.ConsoleSender;

/**
 * GUI 操作封装：通过控制台身份执行服务端指令
 */
public final class GuiActions {
    private GuiActions() {
    }

    public static void exec(String cmd) {
        ClientSession.handleCommand(ConsoleSender.INSTANCE, cmd);
    }

    public static void next() {
        exec("/music next");
    }

    public static void deleteQueue(int index) {
        exec("/music delete " + index);
    }

    public static void banMusic(String id) {
        exec("/music ban " + id);
    }

    public static void unbanMusic(String id) {
        exec("/music unban " + id);
    }

    public static void banPlayer(String name) {
        exec("/music banplayer " + name);
    }

    public static void unbanPlayer(String name) {
        exec("/music unbanplayer " + name);
    }
}
