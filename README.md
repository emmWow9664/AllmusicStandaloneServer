*本项目由AI生成，并同步到仓库
# AllmusicStandaloneServer

AllMusic 独立音乐服务器（Standalone Server）：实现 [AllMusic](https://github.com/Coloryr/AllMusic) 服务端插件的全部功能，可脱离 Minecraft 独立运行，作为第三方音乐服务端供 [AllMusicConnect](https://github.com/emmWow9664/AllmusicConnect) 客户端模组连接。

## 功能

- 完整的 AllMusic 服务端功能：点歌、切歌、搜索、歌单、投票、静音、封禁、HUD 控制等
- 图形界面（GUI）：玩家列表、歌曲列表、日志、指令输入框、可视化配置编辑
- 无图形环境时以控制台模式运行
- 音乐 API 热加载（`netapi` jar，如 `netapi-1.0.1-SNAPSHOT.jar`）
- 歌曲与歌词保存、点赞置顶（TopLyric）、经济 / 点歌花费扩展接口

## 运行要求

- Java 21+

## 使用

```bash
java -jar build/libs/AllmusicStandaloneServer-1.0.0.jar
```

- 服务端默认监听 `0.0.0.0:5223`，可在 `standalone_config.json` 中修改 `port` / `bindHost`
- 数据与配置文件存放于 `allmusic_server/` 目录（`config.json`、`message.json`、`music.json`、`ban.json`、`cookie.json`、`hud.json` 等）
- 玩家通过 AllMusic Client + AllMusicConnect 模组执行 `/music connect <ip> <端口>` 接入

## 构建

```bash
gradlew build
```

产物（ShadowJar，已重定位 httpclient 依赖以兼容官方音乐 API jar）：

- `build/libs/AllmusicStandaloneServer-1.0.0.jar` —— 独立服务端主程序（Main-Class: `com.example.standalone.Main`）
- `build/libs/netapi-*.jar` —— 音乐 API 接口实现

## 说明

- 本项目为 [AllMusic](https://github.com/Coloryr/AllMusic) 的衍生作品，修改了其服务端核心以独立于 Minecraft 运行
- 许可证：GPL-3.0（详见 [NOTICE.md](NOTICE.md) 与 [LICENSE](LICENSE)）
