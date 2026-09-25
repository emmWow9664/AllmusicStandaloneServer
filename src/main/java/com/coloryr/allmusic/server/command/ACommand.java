package com.coloryr.allmusic.server.core.command;

import com.coloryr.allmusic.server.core.AllMusic;

import java.util.Collections;
import java.util.List;

public abstract class ACommand implements ICommand {
    @Override
    public List<String> tab(Object player, String name, String[] args, int index) {
        return Collections.emptyList();
    }

    /**
     * HUD 配置修改后立即向在线玩家重发一次 HUD_DATA。
     * <p>
     * 直接调用 {@code AllMusic.side.sendHudPos(String)}，不经过主线程任务队列，改完即生效；
     * 控制台 / 非玩家操作时拿不到玩家名，直接跳过。
     *
     * @param sender 指令发送者
     */
    protected final void resendHud(Object sender) {
        try {
            String player = AllMusic.side.getPlayerName(sender);
            if (player != null) {
                AllMusic.side.sendHudPos(player);
            }
        } catch (Exception ignored) {
        }
    }
}
