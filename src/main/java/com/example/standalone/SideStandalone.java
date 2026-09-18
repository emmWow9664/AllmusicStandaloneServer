package com.example.standalone;

import com.coloryr.allmusic.codec.MusicPack;
import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.objs.music.PlayerAddMusicObj;
import com.coloryr.allmusic.server.core.objs.music.SongInfoObj;
import com.coloryr.allmusic.server.core.side.BaseSide;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 独立服务端的 Side 实现：把 AllMusic 核心的所有数据包通过 TCP 发送给连接的客户端模组
 */
public class SideStandalone extends BaseSide {
    public static final SideStandalone INSTANCE = new SideStandalone();

    /**
     * 已连接的客户端（玩家名小写 -> 会话）
     */
    private final Map<String, ClientSession> clients = new ConcurrentHashMap<>();
    /**
     * 主线程任务执行器（模拟 Minecraft 主线程）
     */
    private final ScheduledExecutorService mainExecutor = Executors.newSingleThreadScheduledExecutor();

    private SideStandalone() {
    }

    /**
     * 注册一个已连接的客户端
     */
    public void register(ClientSession session) {
        clients.put(session.getName().toLowerCase(Locale.ROOT), session);
    }

    /**
     * 移除一个已断开的客户端
     */
    public void unregister(ClientSession session) {
        String key = session.getName() == null ? null : session.getName().toLowerCase(Locale.ROOT);
        if (key != null) {
            clients.remove(key, session);
        }
    }

    /**
     * 获取所有连接的客户端会话
     */
    public Collection<ClientSession> getClientSessions() {
        return new ArrayList<>(clients.values());
    }

    /**
     * 是否已有同名的客户端连接
     */
    public boolean havePlayer(String name) {
        return clients.containsKey(name.toLowerCase(Locale.ROOT));
    }

    @Override
    public void runTask(Runnable run) {
        mainExecutor.execute(run);
    }

    @Override
    public void runTask(Runnable run1, int delay) {
        mainExecutor.schedule(run1, Math.max(0, delay), TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean checkPermission(Object player) {
        return player instanceof ConsoleSender;
    }

    @Override
    public boolean isPlayer(Object source) {
        return source instanceof ClientSession;
    }

    @Override
    public boolean checkPermission(Object player, String permission) {
        return checkPermission(player);
    }

    @Override
    public boolean needPlay(boolean islist) {
        for (ClientSession c : clients.values()) {
            if (!AllMusic.isSkip(c.getName(), null, false, islist)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Collection<?> getPlayers() {
        return getClientSessions();
    }

    @Override
    public String getPlayerName(Object player) {
        if (player instanceof ClientSession session) {
            return session.getName();
        }
        return null;
    }

    @Override
    public String getPlayerServer(Object player) {
        return null;
    }

    @Override
    public void send(Object player, MusicPack pack) {
        if (player instanceof ClientSession session) {
            session.sendPack(pack);
        }
    }

    @Override
    public Object getPlayer(String player) {
        if (player == null) {
            return null;
        }
        return clients.get(player.toLowerCase(Locale.ROOT));
    }

    @Override
    public void sendBar(Object player, Component data) {
        if (player instanceof ClientSession session) {
            session.sendBar(data);
        }
    }

    @Override
    public File getFolder() {
        return new File(AllMusic.SERVER_DIR);
    }

    @Override
    public void sendMessage(Object obj, Component message) {
        if (obj instanceof ClientSession session) {
            session.sendChat(message);
        } else if (obj instanceof ConsoleSender) {
            LogStandalone.INSTANCE.data(message);
        }
    }

    @Override
    public boolean onMusicPlay(SongInfoObj obj) {
        return false;
    }

    @Override
    public boolean onMusicAdd(Object obj, PlayerAddMusicObj music) {
        return false;
    }

    @Override
    public void broadcast(Component message) {
        for (ClientSession c : clients.values()) {
            c.sendChat(message);
        }
    }

    @Override
    public Component miniMessage(String input) {
        return MiniMessage.miniMessage().deserialize(input);
    }

    @Override
    public Component miniMessageRun(String input, String command) {
        return miniMessage(input).clickEvent(ClickEvent.runCommand(command));
    }

    @Override
    public Component miniMessageSuggest(String input, String command) {
        return miniMessage(input).clickEvent(ClickEvent.suggestCommand(command));
    }

    @Override
    public void updateInfo() {
        List<String> names = new ArrayList<>();
        for (ClientSession c : clients.values()) {
            names.add(c.getName());
        }
        Main.getFrame().ifPresent(frame -> frame.updateNowPlaying());
    }

    /**
     * 关闭主线程执行器
     */
    public void shutdown() {
        mainExecutor.shutdownNow();
    }
}
