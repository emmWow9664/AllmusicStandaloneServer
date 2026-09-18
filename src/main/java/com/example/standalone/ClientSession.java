package com.example.standalone;

import com.coloryr.allmusic.codec.MusicPack;
import com.coloryr.allmusic.codec.MusicPacketCodec;
import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.command.CommandEX;
import com.coloryr.allmusic.server.core.music.MusicSearch;
import com.coloryr.allmusic.server.core.music.PlayMusic;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * 一个连接到独立服务端的客户端（虚拟玩家）会话
 *
 * 协议（服务端 -> 客户端）：
 *   1 字节 kind + 4 字节长度 + 内容
 *   kind=1 内容为 MusicPack 二进制数据（与 AllMusic 客户端模组兼容）
 *   kind=2 内容为 JSON：{"type":"chat"|"bar","json":"adventure组件json","text":"纯文本"}
 *
 * 协议（客户端 -> 服务端）：
 *   1 字节 kind(0) + 4 字节长度 + JSON
 *   {"type":"handshake","name":"玩家名"}
 *   {"type":"command","text":"/music ..."}
 */
public class ClientSession {
    private static final Gson gson = new Gson();

    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private volatile String name;
    private volatile boolean closed;

    public ClientSession(Socket socket) throws IOException {
        this.socket = socket;
        this.socket.setTcpNoDelay(true);
        this.in = new DataInputStream(socket.getInputStream());
        this.out = new DataOutputStream(socket.getOutputStream());
    }

    public String getName() {
        return name;
    }

    /**
     * 启动会话（注册、握手、读线程）
     */
    public void start() {
        Thread thread = new Thread(this::readLoop, "allmusic-client-" + socket.getRemoteSocketAddress());
        thread.setDaemon(true);
        thread.start();
    }

    private void readLoop() {
        try {
            while (!closed) {
                int kind = in.readUnsignedByte();
                int length = in.readInt();
                if (length < 0 || length > 1024 * 1024 * 16) {
                    break;
                }
                byte[] data = new byte[length];
                in.readFully(data);
                if (kind == 0) {
                    handleJson(new String(data, StandardCharsets.UTF_8));
                }
            }
        } catch (EOFException e) {
            // 正常断开
        } catch (Exception e) {
            if (!closed) {
                AllMusic.log.data("<light_purple>[AllMusic]<red>客户端连接出错：" + socket.getRemoteSocketAddress());
                e.printStackTrace();
            }
        } finally {
            close();
        }
    }

    private void handleJson(String json) {
        try {
            JsonObject obj = gson.fromJson(json, JsonObject.class);
            if (obj == null || !obj.has("type")) {
                return;
            }
            String type = obj.get("type").getAsString();
            switch (type) {
                case "handshake": {
                    String playerName = obj.has("name") ? obj.get("name").getAsString() : "";
                    if (playerName.isEmpty()) {
                        close();
                        return;
                    }
                    if (name != null) {
                        return;
                    }
                    if (SideStandalone.INSTANCE.havePlayer(playerName)) {
                        sendJson("chat", "{\"text\":\"同名玩家已连接，请先断开\",\"color\":\"red\"}", "同名玩家已连接，请先断开");
                        close();
                        return;
                    }
                    name = playerName;
                    SideStandalone.INSTANCE.register(this);
                    LogStandalone.INSTANCE.append("<light_purple>[AllMusic]<yellow>玩家加入：" + name);
                    Main.getFrame().ifPresent(frame -> frame.onPlayerJoin(name));
                    // 玩家加入后，如果正在播放，立即同步当前状态
                    AllMusic.joinPlay(name);
                    break;
                }
                case "command": {
                    if (name == null) {
                        return;
                    }
                    String text = obj.has("text") ? obj.get("text").getAsString() : "";
                    LogStandalone.INSTANCE.append("<light_purple>[AllMusic]<yellow>玩家 " + name + " 执行指令：" + text);
                    handleCommand(this, text);
                    break;
                }
                case "ping": {
                    // 心跳，暂时不处理
                    break;
                }
            }
        } catch (Exception e) {
            AllMusic.log.data("<light_purple>[AllMusic]<red>指令解析出错");
            e.printStackTrace();
        }
    }

    /**
     * 解析并执行一条指令
     */
    public static void handleCommand(Object sender, String text) {
        String line = text.trim();
        if (line.startsWith("/music")) {
            line = line.substring(6).trim();
        }
        if (line.isEmpty()) {
            AllMusic.side.sendMessage(sender, "请输入 /music help 查看帮助");
            return;
        }
        String[] temp = line.split("\\s+");
        java.util.List<String> list = new java.util.ArrayList<>();
        for (String s : temp) {
            if (!s.isEmpty()) {
                list.add(s);
            }
        }
        String[] args = list.toArray(new String[0]);
        String senderName = AllMusic.side.getPlayerName(sender);
        if (senderName == null) {
            senderName = ConsoleSender.NAME;
        }
        CommandEX.execute(sender, senderName, args);
    }

    /**
     * 发送一条聊天消息（adventure 组件 -> Minecraft 兼容 json）
     */
    public void sendChat(Component component) {
        String json = GsonComponentSerializer.gson().serialize(component);
        String text = PlainTextComponentSerializer.plainText().serialize(component);
        sendJson("chat", json, text);
    }

    /**
     * 发送一条 bar（物品栏上方）消息
     */
    public void sendBar(Component component) {
        String json = GsonComponentSerializer.gson().serialize(component);
        String text = PlainTextComponentSerializer.plainText().serialize(component);
        sendJson("bar", json, text);
    }

    private void sendJson(String type, String json, String text) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"type\":\"").append(type)
                    .append("\",\"json\":").append(gson.toJson(json))
                    .append(",\"text\":").append(gson.toJson(text))
                    .append("}");
            write(2, sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            AllMusic.log.data("<light_purple>[AllMusic]<red>消息发送出错");
        }
    }

    /**
     * 发送 MusicPack 数据包（与 AllMusic 客户端模组完全兼容的二进制格式）
     */
    public void sendPack(MusicPack pack) {
        try {
            ByteBuf buf = MusicPacketCodec.pack(pack);
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            buf.release();
            write(1, bytes);
        } catch (Exception e) {
            AllMusic.log.data("<light_purple>[AllMusic]<red>数据包发送出错");
        }
    }

    private void write(int kind, byte[] data) throws IOException {
        synchronized (this) {
            if (closed) {
                return;
            }
            out.writeByte(kind);
            out.writeInt(data.length);
            out.write(data);
            out.flush();
        }
    }

    /**
     * 关闭连接并清理玩家数据
     */
    public void close() {
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
        }
        try {
            socket.close();
        } catch (Exception ignored) {
        }
        if (name != null) {
            String playerName = name;
            SideStandalone.INSTANCE.unregister(this);
            PlayMusic.removeNowPlayPlayer(playerName);
            MusicSearch.removeSearch(playerName);
            LogStandalone.INSTANCE.append("<light_purple>[AllMusic]<yellow>玩家离开：" + playerName);
            Main.getFrame().ifPresent(frame -> frame.onPlayerLeave(playerName));
        }
    }
}
