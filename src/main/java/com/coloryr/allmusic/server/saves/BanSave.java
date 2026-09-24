package com.coloryr.allmusic.server.core.saves;

import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.objs.config.BanObj;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class BanSave {
    private static BanObj ban = new BanObj();
    private static File banFile;

    public static boolean haveBanPlayer(String player) {
        return ban.banPlayers.contains(player);
    }

    public static boolean haveBanServer(String name) {
        name = name.toLowerCase();
        return ban.banServer.contains(name);
    }

    public static void init(File file) throws IOException {
        banFile = new File(file, "ban.json");
        if (!banFile.exists()) {
            banFile.createNewFile();
        }
    }

    private static void banCheck() {
        if (ban == null) {
            ban = BanObj.make();
            AllMusic.log.data("<light_purple>[AllMusic]<red>配置文件ban.json错误，已覆盖");
            saveBan();
        } else if (ban.check()) {
            AllMusic.log.data("<light_purple>[AllMusic]<red>配置文件ban.json错误，已覆盖");
            saveBan();
        }
    }

    public static void saveBan() {
        try {
            String data = AllMusic.gson.toJson(ban);
            FileOutputStream out = new FileOutputStream(banFile);
            OutputStreamWriter write = new OutputStreamWriter(
                    out, StandardCharsets.UTF_8);
            write.write(data);
            write.close();
            out.close();
        } catch (Exception e) {
            AllMusic.log.data("<light_purple>[AllMusic]<red>配置文件ban.json保存错误");
            e.printStackTrace();
        }
    }

    public static void loadBan() throws IOException {
        InputStreamReader reader = new InputStreamReader(
                Files.newInputStream(banFile.toPath()), StandardCharsets.UTF_8);
        BufferedReader bf = new BufferedReader(reader);
        ban = AllMusic.gson.fromJson(bf, BanObj.class);
        bf.close();
        reader.close();
        banCheck();
    }

    public static void addBanMusic(String music, String api) {
        SaveTask.task(() -> {
            List<String> ids = ban.banMusics.get(api);
            if (ids == null) {
                ids = new ArrayList<>();
            }
            // 去重：重复封禁同一首歌会产生多条记录，导致解封后仍被判为封禁
            if (!ids.contains(music)) {
                ids.add(music);
            }
            ban.banMusics.put(api, ids);
            saveBan();
        });
    }

    public static void removeBanMusic(String id, String api) {
        SaveTask.task(() -> {
            List<String> ids = ban.banMusics.get(api);
            if (ids == null) {
                return;
            }
            // 用 removeAll 清掉所有重复项：只删一条时 contains 仍为 true，解封会看起来无效
            ids.removeIf(id::equals);
            if (ids.isEmpty()) {
                ban.banMusics.remove(api);
            }
            saveBan();
        });
    }

    /**
     * @param id 歌曲ID
     * @return 结果
     */
    public static boolean checkBanMusic(String id, String api) {
        List<String> ids = ban.banMusics.get(api);
        if (ids == null) {
            return false;
        }

        return ids.contains(id);
    }

    public static void clearBan() {
        SaveTask.task(() -> {
            ban.banMusics.clear();
            saveBan();
        });
    }

    public static void addBanPlayer(String player) {
        SaveTask.task(() -> {
            String player1 = player.toLowerCase(Locale.ROOT);
            ban.banPlayers.add(player1);
            saveBan();
        });
    }

    public static void removeBanPlayer(String player) {
        SaveTask.task(() -> {
            String player1 = player.toLowerCase(Locale.ROOT);
            ban.banPlayers.remove(player1);
            saveBan();
        });
    }

    /**
     * 检查玩家是否在数据库
     *
     * @param name 用户名
     * @return 结果
     */
    public static boolean checkBanPlayer(String name) {
        name = name.toLowerCase(Locale.ROOT);
        return ban.banPlayers.contains(name);
    }

    public static void clearBanPlayer() {
        SaveTask.task(() -> {
            ban.banPlayers.clear();
            saveBan();
        });
    }

    public static void addMutePlayer(String player) {
        SaveTask.task(() -> {
            String player1 = player.toLowerCase(Locale.ROOT);
            ban.mutePlayers.add(player1);
            saveBan();
        });
    }

    public static void removeMutePlayer(String player) {
        SaveTask.task(() -> {
            String player1 = player.toLowerCase(Locale.ROOT);
            ban.mutePlayers.remove(player1);
            saveBan();
        });
    }

    public static boolean checkMutePlayer(String name) {
        name = name.toLowerCase(Locale.ROOT);
        return ban.mutePlayers.contains(name);
    }

    public static void addMuteListPlayer(String player) {
        SaveTask.task(() -> {
            String player1 = player.toLowerCase(Locale.ROOT);
            ban.muteListPlayers.add(player1);
            saveBan();
        });
    }

    public static void removeMuteListPlayer(String player) {
        SaveTask.task(() -> {
            String player1 = player.toLowerCase(Locale.ROOT);
            ban.muteListPlayers.remove(player1);
            saveBan();
        });
    }

    public static boolean checkMuteListPlayer(String name) {
        name = name.toLowerCase(Locale.ROOT);
        return ban.muteListPlayers.contains(name);
    }

    public static Set<String> getBanPlayers() {
        return ban.banPlayers;
    }

    /**
     * 音乐封禁快照（API 名 → 歌曲 ID 列表），供 Web 面板等只读展示使用。
     * <p>
     * 写入方在 SaveTask 线程上直接修改原 Map，这里做防御性拷贝并捕获并发修改异常。
     */
    public static Map<String, List<String>> snapshotBanMusics() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        Map<String, List<String>> src = ban == null ? null : ban.banMusics;
        if (src == null) {
            return result;
        }
        try {
            synchronized (src) {
                for (Map.Entry<String, List<String>> entry : src.entrySet()) {
                    List<String> ids = entry.getValue();
                    result.put(entry.getKey(), ids == null ? new ArrayList<>() : new ArrayList<>(ids));
                }
            }
        } catch (Exception ignored) {
        }
        return result;
    }
}
