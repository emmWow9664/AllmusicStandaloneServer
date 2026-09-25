*本项目由AI生成
# AllmusicStandaloneServer

AllMusic 独立音乐服务器（Standalone Server）：实现 [AllMusic](https://github.com/Coloryr/AllMusic) 服务端插件的全部功能，可脱离 Minecraft 独立运行，作为第三方音乐服务端供 [AllMusicConnect](https://github.com/emmWow9664/AllmusicConnect) 客户端模组连接。

## 功能

- 完整的 AllMusic 服务端功能：点歌、切歌、搜索、歌单、投票、静音、封禁、HUD 控制等
- 图形界面（GUI）：玩家列表、歌曲列表、日志、指令输入框、可视化配置编辑、性能监视、点歌统计
- 内嵌 Web 面板：浏览器查看播放 / 队列 / 玩家（含今日连接人数）/ 统计 / 性能，管理员可登录管理
- 无图形环境时以控制台模式运行
- 音乐 API 热加载（`netapi` jar，如 `netapi-1.0.1-SNAPSHOT.jar`）
- 歌曲与歌词保存、点赞置顶（TopLyric）、经济 / 点歌花费扩展接口

## 运行要求

- **Java 25+**（推荐）
- 服务端本体兼容 Java 21，但常用的 `netapi` 音乐 API 是 Java 25 编译的（class 版本 69），
  用 Java 21 启动会在加载 API 时报 `UnsupportedClassVersionError`，服务端随后退出（原因写入 `allmusic_server/crash.log`）
- 如果必须在 Java 21 下运行，请改用按 Java 21 编译的 API jar
- 双击启动使用的是系统对 `.jar` 的文件关联，请确保该关联指向期望的 JDK（如 `Zulu 25\bin\javaw.exe`）

## 使用

**方式一（推荐）：直接双击 jar 运行** —— 无需终端，双击 `AllmusicStandaloneServer-<版本>.jar` 即可启动图形界面。
若双击无效，说明系统未把 `.jar` 关联到 Java，可用方式二，或右键 jar →「打开方式」选择 `javaw.exe`。

**方式二：命令行运行**

```bash
java -jar build/libs/AllmusicStandaloneServer-1.1.jar
```

- 服务端默认监听 `0.0.0.0:5223`，可在 `standalone_config.json` 中修改 `port` / `bindHost`
- 数据与配置文件存放于 jar 同级的 `allmusic_server/` 目录（`config.json`、`message.json`、`music.json`、`ban.json`、`cookie.json`、`hud.json` 等），与启动时的工作目录无关
- 启动失败时会弹窗提示，并写入 `allmusic_server/crash.log`
- 玩家通过 AllMusic Client + AllMusicConnect 模组执行 `/music connect <ip> [端口]` 接入（端口默认 `5223`）

## 音乐 API（netapi）配置

服务端通过「音乐 API」实现歌曲搜索与播放，`netapi` 为默认的音乐 API 实现：

1. 下载 `netapi-1.0.1-SNAPSHOT.jar`（见 [Releases](https://github.com/emmWow9664/AllmusicStandaloneServer/releases/latest) 中的资产，与服务器 jar 在同一页面）
2. 将 `netapi-*.jar` 放入服务端 jar 同级的 `allmusic_server/api/` 目录（首次启动会自动创建该目录）
3. 启动服务端，日志出现「注册音乐API：\<id\>」即加载成功
4. API 的运行时配置存放于 `allmusic_server/netapi.json`：

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

## 内嵌 Web 面板

服务端自带一个零依赖的内嵌 Web 展示与管理面板，浏览器打开即可使用。

- 默认地址：`http://127.0.0.1:8080/`（默认绑定 `0.0.0.0`，局域网内可用本机 IP 访问）
- 端口、绑定地址、是否启用：在 GUI「设置 → 独立服务端」中修改，**改动需重启服务端**
- **普通用户无需登录，只能查看**：当前播放、歌曲队列、在线玩家（含**今日连接人数**）、点歌统计、**性能监视**（CPU / 内存 / 网络，可展开查看各核心使用率）
- **管理员**需先在「设置」页设置管理员密码（只保存 PBKDF2 加盐哈希，不保存明文），登录后可以：
  切歌、移除队列项、封禁/解封歌曲与玩家、执行服务端指令、查看服务端日志
- 出于安全考虑，Web 面板**不允许执行 `/music stop`**（会直接关闭整个服务端）
- 同一 IP 连续输错 5 次密码将锁定 5 分钟；登录凭证 12 小时绝对过期、闲置 30 分钟失效

> ⚠️ 面板默认绑定所有网卡，相当于把管理页面暴露在网络上，安全性完全依赖密码强度。
> 请设置足够复杂的密码；公网使用建议只放行必要端口，并置于反向代理（如 nginx）之后。

## 构建

```bash
gradlew build
```

产物（ShadowJar，已重定位 httpclient 依赖以兼容官方音乐 API jar）：

- `build/libs/AllmusicStandaloneServer-1.1.jar` —— 独立服务端主程序（Main-Class: `com.example.standalone.Main`，可直接双击运行）
- `releases/AllmusicStandaloneServer-<版本>.jar` —— 各版本产物归档

> 注意：Gradle 7.6+ 会清理 `build/` 目录下的"陈旧任务输出"，历史版本 jar 放在 `build/libs` 里会被删掉；
> 构建时会自动归档一份到 `releases/`（位于 `build/` 之外，不会被清理），请以 `releases/` 作为长期保留的产物目录。

音乐 API（`netapi-*.jar`）可从 [Releases](https://github.com/emmWow9664/AllmusicStandaloneServer/releases/latest) 下载，放入 `allmusic_server/api/` 即可，详见下方配置说明。

## 说明

- 本项目为 [AllMusic](https://github.com/Coloryr/AllMusic) 的衍生作品，修改了其服务端核心以独立于 Minecraft 运行
- 许可证：GPL-3.0（详见 [NOTICE.md](NOTICE.md) 与 [LICENSE](LICENSE)）
