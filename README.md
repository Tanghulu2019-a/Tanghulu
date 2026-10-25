# Tanghulu

Minecraft 1.21.11 的 Fabric / Meteor 插件：种子与结构定位、古城战利品、蘑菇岛定位、Baritone 助手等。

## 安装

1. 安装 Java 21+、Fabric Loader 0.18.2+、Meteor Client 1.21.11-86。
2. 从 [Releases](https://github.com/Tanghulu2019-a/Tanghulu/releases/latest) 下载 JAR，放入 `mods`，移除旧版。
3. 在 Meteor 的 **Tanghulu** 分类中设置。地图、寻路等功能需另装对应依赖；26.x 预测辅助程序需 Java 25。

## 1.0.4 更新

- 古城支持附魔金苹果 / 迅捷潜行 III 筛选，地图标记同步更新。
- 结构搜索自动保存全部 Xaero 航点，按维度去重。
- 自动搭路统一方块黑白名单，精简设置。
- 移除自动走路、新区块探测、快捷烟花。

升级注意与完整变更见 [更新说明](RELEASE-NOTES.md)。

## 构建

需要 JDK 25、Python 3.9+ 和网络：

```sh
python build-support/fetch-dependencies.py
./gradlew --no-daemon build
```

Windows 使用 `gradlew.bat`；产物为 `build/libs/Tanghulu-1.0.4.jar`。

## 说明

- [功能与依赖](STASH-RESTORE.md) · [古城](ancient/README.md) · [蘑菇岛](MUSHROOM-LOCATOR.md) · [3C 查询](3C-NEWBIE-CHECK.md)
- 3C 查询会向 `3c3u.info` 发送查询的玩家名；世界预测可能下载 Mojang 官方资源。
- 默认种子沿用旧版，使用其他世界时请修改；升级前备份个人配置。
- [验证记录](RELEASE-VALIDATION.md) · [GPL-3.0-only](LICENSE) · [来源与署名](NOTICE.md)
