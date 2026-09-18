*本项目由AI生成
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

## 音乐 API（netapi）配置

服务端通过「音乐 API」实现歌曲搜索与播放，`netapi` 为默认的音乐 API 实现：

1. 将 `netapi-*.jar`（如 `netapi-1.0.1-SNAPSHOT.jar`）放入 `allmusic_server/api/` 目录
2. 启动服务端，日志出现「注册音乐API：\<id\>」即加载成功
3. API 的运行时配置存放于 `allmusic_server/netapi.json`：

```json
{
  "level": "exhigh",
  "encodeType": "aac"
}
```

| 配置项 | 说明 |
| --- | --- |
| `level` | 音质等级（如 `exhigh`） |
| `encodeType` | 音频编码格式（如 `aac`） |

- API jar 内需包含 `version` 文件（内容为字符 `2`），否则会被判定为旧版 API 跳过加载
- 使用 `/music api <API名> [参数]` 调用 API 的扩展命令（如查看 / 设置 Cookie）
- 修改配置或更换 API jar 后需重启服务端生效

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
