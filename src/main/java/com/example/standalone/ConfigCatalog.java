package com.example.standalone;

import com.coloryr.allmusic.server.core.AllMusic;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * 服务端全部可配置项的清单与读写（GUI 设置页与控制台指令共用）。
 * <p>
 * 配置分两份：
 * <ul>
 *   <li>{@code standalone.*} —— 独立服务端自身配置（{@code standalone_config.json}）；
 *       如 {@code standalone.port}、{@code standalone.webPort}</li>
 *   <li>其它 —— AllMusic 核心配置（{@code allmusic_server/config.json}），
 *       按嵌套字段路径访问，如 {@code limit.maxPlayList}、{@code vote.voteTime}</li>
 * </ul>
 * Web 管理员密码不在这里（只保存哈希，见 {@code WebAuth}）。
 */
public final class ConfigCatalog {

    public static final String BOOL = "bool";
    public static final String INT = "int";
    public static final String STR = "str";

    /** 一个配置项 */
    public static final class Item {
        /** 字段路径，如 limit.messageLimit / standalone.port */
        public final String path;
        public final String cn;
        public final String en;
        public final String desc;
        public final String type;

        Item(String path, String cn, String en, String desc, String type) {
            this.path = path;
            this.cn = cn;
            this.en = en;
            this.desc = desc;
            this.type = type;
        }
    }

    /** 一组配置项（GUI 与控制台都按组展示） */
    public static final class Group {
        public final String title;
        public final List<Item> items = new ArrayList<>();

        Group(String title) {
            this.title = title;
        }
    }

    private static final List<Group> GROUPS = new ArrayList<>();
    private static final List<Item> ITEMS = new ArrayList<>();

    static {
        Group base = group("基础");
        add(base, "lyricDelay", "歌词延迟", "lyricDelay", "歌词显示延迟毫秒数", INT);
        add(base, "ktvLyricDelay", "KTV 歌词延迟", "ktvLyricDelay", "KTV 歌词显示延迟毫秒数", INT);
        add(base, "defaultAddMusic", "默认点歌数量", "defaultAddMusic", "默认每次点歌添加的歌曲数量", INT);
        add(base, "defaultApi", "默认音乐 API", "defaultApi", "默认使用的音乐 API 名称", STR);
        add(base, "sendDelay", "发送延迟", "sendDelay", "数据包发送间隔毫秒数", INT);
        add(base, "joinDelay", "加入延迟", "joinDelay", "玩家加入时同步播放的延迟毫秒数", INT);
        add(base, "fixSongTime", "歌曲时长修正", "fixSongTime", "播放超时修正秒数", INT);
        add(base, "playListSwitch", "歌单切歌", "playListSwitch", "空闲歌单播放完后是否自动切换", BOOL);
        add(base, "playListRandom", "歌单随机", "playListRandom", "空闲歌单是否随机播放", BOOL);
        add(base, "playListEscapeDeep", "歌单逃避深度", "playListEscapeDeep", "空闲歌单连续跳过深度", INT);
        add(base, "sendLyric", "发送歌词", "sendLyric", "是否向客户端发送歌词", BOOL);
        add(base, "needPermission", "需要权限", "needPermission", "点歌是否需要权限", BOOL);
        add(base, "topAPI", "API 置顶", "topAPI", "是否将 API 置顶显示", BOOL);
        add(base, "mutePlayMessage", "静默播放消息", "mutePlayMessage", "播放歌曲时是否不广播消息", BOOL);
        add(base, "muteAddMessage", "静默点歌消息", "muteAddMessage", "点歌时是否不广播消息", BOOL);
        add(base, "showInBar", "物品栏显示", "showInBar", "消息是否显示在物品栏上方", BOOL);
        add(base, "ktvMode", "KTV 模式", "ktvMode", "是否启用 KTV 歌词模式", BOOL);

        Group limit = group("限制");
        add(limit, "limit.messageLimit", "消息长度限制", "messageLimit", "是否限制消息长度", BOOL);
        add(limit, "limit.messageLimitSize", "消息长度上限", "messageLimitSize", "消息最大长度", INT);
        add(limit, "limit.listLimit", "点歌队列限制", "listLimit", "是否限制点歌队列长度", BOOL);
        add(limit, "limit.listLimitSize", "队列长度上限", "listLimitSize", "队列最大长度", INT);
        add(limit, "limit.infoLimit", "歌曲信息限制", "infoLimit", "是否限制歌曲信息显示", BOOL);
        add(limit, "limit.infoLimitSize", "歌曲信息上限", "infoLimitSize", "歌曲信息最大长度", INT);
        add(limit, "limit.musicTimeLimit", "歌曲时长限制", "musicTimeLimit", "是否限制歌曲时长", BOOL);
        add(limit, "limit.maxMusicTime", "歌曲最大时长", "maxMusicTime", "歌曲最大时长秒数", INT);
        add(limit, "limit.limitText", "限制提示文本", "limitText", "触发限制时的提示文本", STR);
        add(limit, "limit.maxPlayList", "最大点歌列表", "maxPlayList", "每个玩家最大点歌数", INT);
        add(limit, "limit.maxPlayerList", "最大玩家列表", "maxPlayerList", "最大玩家数量", INT);

        Group vote = group("投票");
        add(vote, "vote.minVote", "最小投票数", "minVote", "切歌投票所需最小人数", INT);
        add(vote, "vote.voteTime", "投票时间", "voteTime", "投票持续秒数", INT);
        add(vote, "vote.voteListSize", "投票列表大小", "voteListSize", "参与投票的最大列表长度", INT);

        Group cost = group("经济");
        add(cost, "cost.useCost", "启用花费", "useCost", "点歌是否消耗经济", BOOL);
        add(cost, "cost.searchCost", "搜索花费", "searchCost", "每次搜索消耗的金额", INT);
        add(cost, "cost.addMusicCost", "点歌花费", "addMusicCost", "每点一首歌消耗的金额", INT);
        add(cost, "economy.backend", "经济后端", "backend", "经济系统后端", STR);

        Group fun = group("趣味");
        add(fun, "funConfig.rain", "下雨", "rain", "播放歌曲时是否下雨", BOOL);
        add(fun, "funConfig.rainRate", "下雨概率", "rainRate", "下雨触发概率(百分比)", INT);

        Group standalone = group("独立服务端");
        add(standalone, "standalone.port", "监听端口", "port", "独立服务端 TCP 监听端口", INT);
        add(standalone, "standalone.bindHost", "绑定地址", "bindHost", "绑定地址(0.0.0.0 为所有网卡)", STR);
        add(standalone, "standalone.webEnabled", "Web 面板", "webEnabled",
                "是否启用内嵌 Web 展示与管理面板", BOOL);
        add(standalone, "standalone.webPort", "Web 端口", "webPort",
                "内嵌 Web 面板监听端口，默认 8080", INT);
        add(standalone, "standalone.webBindHost", "Web 绑定地址", "webBindHost",
                "Web 面板绑定地址，0.0.0.0 为所有网卡", STR);
    }

    private ConfigCatalog() {
    }

    private static Group group(String title) {
        Group group = new Group(title);
        GROUPS.add(group);
        return group;
    }

    private static void add(Group group, String path, String cn, String en, String desc, String type) {
        Item item = new Item(path, cn, en, desc, type);
        group.items.add(item);
        ITEMS.add(item);
    }

    public static List<Group> groups() {
        return GROUPS;
    }

    public static List<Item> items() {
        return ITEMS;
    }

    /**
     * 查找配置项：依次按 完整路径 / 英文原名 / 中文名（不区分大小写）匹配
     *
     * @return 未找到返回 null
     */
    public static Item find(String key) {
        if (key == null) {
            return null;
        }
        String k = key.trim();
        for (Item item : ITEMS) {
            if (item.path.equalsIgnoreCase(k)) {
                return item;
            }
        }
        for (Item item : ITEMS) {
            if (item.en.equalsIgnoreCase(k)) {
                return item;
            }
        }
        for (Item item : ITEMS) {
            if (item.cn.equalsIgnoreCase(k)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 读取配置值
     *
     * @return 读取失败返回 null
     */
    public static Object read(String path) {
        try {
            if (path.startsWith("standalone.")) {
                StandaloneConfig config = Main.getStandaloneConfig();
                if (config == null) {
                    return null;
                }
                return StandaloneConfig.class.getField(path.substring("standalone.".length())).get(config);
            }
            Object obj = AllMusic.getConfig();
            if (obj == null) {
                return null;
            }
            for (String part : path.split("\\.")) {
                obj = obj.getClass().getField(part).get(obj);
                if (obj == null) {
                    return null;
                }
            }
            return obj;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 按字段类型解析并写入配置（不落盘，需再调用 {@link #saveAll()}）
     *
     * @return 成功返回 null，失败返回错误说明
     */
    public static String write(String path, String rawValue) {
        try {
            Object target;
            Field field;
            if (path.startsWith("standalone.")) {
                StandaloneConfig config = Main.getStandaloneConfig();
                if (config == null) {
                    return "独立服务端配置尚未初始化";
                }
                target = config;
                field = StandaloneConfig.class.getField(path.substring("standalone.".length()));
            } else {
                Object obj = AllMusic.getConfig();
                if (obj == null) {
                    return "核心配置尚未初始化";
                }
                String[] parts = path.split("\\.");
                for (int i = 0; i < parts.length - 1; i++) {
                    obj = obj.getClass().getField(parts[i]).get(obj);
                }
                target = obj;
                field = obj.getClass().getField(parts[parts.length - 1]);
            }

            String value = rawValue == null ? "" : rawValue.trim();
            Class<?> type = field.getType();
            if (type == boolean.class || type == Boolean.class) {
                Boolean bool = parseBoolean(value);
                if (bool == null) {
                    return "布尔值只能是 true / false（on / off、1 / 0 亦可）";
                }
                field.set(target, bool);
            } else if (type == int.class || type == Integer.class) {
                try {
                    field.set(target, Integer.parseInt(value));
                } catch (NumberFormatException e) {
                    return "需要整数：" + value;
                }
            } else {
                field.set(target, rawValue);
            }
            return null;
        } catch (NoSuchFieldException e) {
            return "没有这个配置项：" + path;
        } catch (Exception e) {
            return "写入失败：" + e.getMessage();
        }
    }

    /**
     * 保存全部配置到磁盘
     */
    public static void saveAll() {
        StandaloneConfig config = Main.getStandaloneConfig();
        if (config != null) {
            config.save();
        }
        AllMusic.saveConfig();
    }

    private static Boolean parseBoolean(String value) {
        String v = value.toLowerCase();
        if (v.equals("true") || v.equals("on") || v.equals("yes") || v.equals("1")) {
            return Boolean.TRUE;
        }
        if (v.equals("false") || v.equals("off") || v.equals("no") || v.equals("0")) {
            return Boolean.FALSE;
        }
        return null;
    }
}