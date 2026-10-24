# Stash 编译依赖

`XaeroPlus-2.35.1-fabric-1.21.11.jar` 来自本机已安装的 XaeroPlus 2.35.1，目标为 Minecraft 1.21.11、Xaero World Map 1.44.2、Xaero Minimap 26.4.2。

- 原文件 SHA-256：`c884b6085bdc1c82545b44a39f6f2c29e15576f59724a36546bf0fca94d6df70`
- 上游项目：https://github.com/rfresh2/XaeroPlus
- 上游声明许可证：MIT。
- 原 JAR 完整保留，未改写清单或字节码。

此依赖使用较新的 Loom 构建，Tanghulu 仍沿用原有 Loom 1.15.5 / Gradle 9.2.0。
根项目的 `prepareXaeroPlusCompileJar` 使用 Loom 的 RemapJarTask 将它从 intermediary 映射至 named，
输出仅供 `compileOnly` 使用。该构建步骤不应用第三方 Mixin，也不将 XaeroPlus、LambdaEvents 或地图类打入最终插件。
游戏运行时使用玩家另行安装的 XaeroPlus 及 Xaero 地图依赖。

原来的 `jefff-mod-0.16.6-1.21.1.jar` 编译占位依赖已由本次内置源码替代并移除。
