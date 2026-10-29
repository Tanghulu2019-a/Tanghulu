# 启动兼容修复（1.0.7）

修复与 Meteor Miku 同装时，Baritone 挖矿 Mixin 冲突引发的启动崩溃。此冲突并非 Android 独有。

将独占的 `@Redirect` 改为 MixinExtras `@WrapOperation`；未启用种子挖矿时保留上游判定，启用时保留预测目标过滤。

移除旧版并安装 `Tanghulu-1.0.7.jar`，使用对应 Minecraft 1.21.11 的 Fabric、Meteor 和 Baritone。

源码提供 `runPackagedSmoke -PsmokeBaritone -PsmokeMikuConflict` 冲突回归，以及 900/1000/1100 优先级检查。测试模组不进入发布包。

桌面 JVM 的自动化检查不等于 Android 真机或游戏内人工测试。
