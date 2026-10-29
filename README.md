# Tanghulu

Minecraft **1.21.11** 的 Fabric / Meteor 插件。

## 安装

从 [Releases](https://github.com/Tanghulu2019-a/Tanghulu/releases/latest) 下载 `Tanghulu-1.0.7.jar`，放入 `mods` 并移除旧版。需要 Java 21+、Fabric Loader 0.18.2+ 和对应版本 Meteor。自动砍树、挖矿需 Baritone，地图模组另装；26.x 预测需 Java 25。Minecraft 1.21.4 请使用 1.0.5。

## 1.0.7 更新

- 新增自动砍树：支持九种树木、自动搭高回收和原木拾取，遇到障碍自动跳过。
- 古城战利品新增快速拿取与附近自动搜箱，可勾选需要的物品。
- 修复与 Meteor Miku 同装时 Baritone 冲突导致的启动崩溃。

## 构建

需要 JDK 25、Python 3 和网络：

```sh
python build-support/fetch-compat-deps.py 1.21.11
./gradlew build
```

Windows 使用 `gradlew.bat`，产物位于 `build/libs/`。

3C 查询会向 `3c3u.info` 发送玩家名；世界预测会下载 Mojang 资源。Webhook 默认关闭，地址为空。

[自动砍树](AUTO-TREE-CHOP.md) · [快速拿取](QUICK-LOOT.md) · [兼容说明](COMPATIBILITY.md) · [文字过滤](INFORMATION-TEXT-FILTER.md) · [验证记录](RELEASE-VALIDATION.md) · [许可证](LICENSE) · [来源署名](NOTICE.md)
