# Tanghulu

Minecraft **1.21.11** 的 Fabric / Meteor 插件，提供种子与结构定位、古城战利品、自动挖矿、音符机器人等功能。

## 安装

从 [Releases](https://github.com/Tanghulu2019-a/Tanghulu/releases/latest) 下载 `Tanghulu-1.0.6.jar`，放入 `mods` 并移除旧版。需要 Java 21+、Fabric Loader 0.18.2+ 和对应版本 Meteor。Baritone、地图模组另装；26.x 预测需 Java 25。Minecraft 1.21.4 请使用 1.0.5。

## 1.0.6 更新

- 信息文字过滤：支持隐藏或星号替换、自定义规则和白名单。
- 恢复种子矿透、自动顶头跳；支持搜索进度和自动挖掘控制，移除密集区与高度限制。
- 功能名称添加英文首字母，方便排序。

种子矿透需填写种子、选择区块生成版本和矿物，点击“开始搜索”，完成后可点击“开始自动挖”（需 Baritone）。信息文字过滤默认关闭，启用后在本地处理文字。

## 构建

需要 JDK 25、Python 3 和网络：

```sh
python build-support/fetch-compat-deps.py 1.21.11
./gradlew build
```

Windows 使用 `gradlew.bat`，产物位于 `build/libs/`。

3C 查询会向 `3c3u.info` 发送查询的玩家名；世界预测会下载 Mojang 资源。Webhook 默认关闭、地址为空，配置启用后发送仓库信息。

[更新说明](RELEASE-NOTES.md) · [兼容说明](COMPATIBILITY.md) · [文字过滤](INFORMATION-TEXT-FILTER.md) · [验证记录](RELEASE-VALIDATION.md) · [GPL-3.0-only](LICENSE) · [来源署名](NOTICE.md)
