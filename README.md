# Tanghulu

Minecraft **1.21.4 / 1.21.11** 的 Fabric / Meteor 插件，包含种子与结构定位、古城战利品、Baritone 助手、音符机器人修复等。

## 安装

从 [Releases](https://github.com/Tanghulu2019-a/Tanghulu/releases/latest) 下载对应游戏版本的 JAR，放入 `mods` 并移除旧版。需要 Java 21+、Fabric Loader 0.18.2+ 和对应版本的 Meteor。
地图、Baritone 等依赖另装；26.x 预测辅助程序需 Java 25。详见 [兼容说明](COMPATIBILITY.md)。

## 1.0.5 更新

- 新增 1.21.4 适配，两个游戏版本独立打包。
- 音符机器人新增中文提示、调音重试、同刻和弦及歌曲材料预览。
- 修复聊天兼容与音符机器人停用清理。

## 构建

需要 JDK 25、Python 3 和网络，以 1.21.4 为例：

```sh
python build-support/fetch-compat-deps.py 1.21.4
./gradlew -PmcVersion=1.21.4 build
```

Windows 使用 `gradlew.bat`；构建 1.21.11 时替换版本参数。产物位于 `build/libs/`。

3C 查询会向 `3c3u.info` 发送查询的玩家名；世界预测可能下载 Mojang 官方资源。Webhook 默认关闭，地址为空，开启并配置后发送仓库信息。升级前备份配置。

[更新说明](RELEASE-NOTES.md) · [音符机器人](NOTEBOT-FIX.md) · [验证记录](RELEASE-VALIDATION.md) · [GPL-3.0-only](LICENSE) · [来源与署名](NOTICE.md)
