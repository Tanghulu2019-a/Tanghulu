# Stash 整合说明（1.0.2）

本次相对 GitHub 公开 1.0.1 新增 5 个模块、2 个 HUD，来源为维护者提供的源码；上游参考 [jeff mod 0.16.7](https://github.com/miles352/meteor-stashhunting-addon/releases/tag/0.16.7)。版本为 1.0.2，安装前移除旧版。

模块：Baritone助手（AutoLogPlus）、增强仓库搜索（BetterStashFinder）、鞘翅飞行增强（ElytraFlyPlusPlus）、40度飞行辅助（Pitch40Util）、装饰透视（VanityESP）。HUD：Weather、EntityList。挂机原版飞行和坐标直行不在本版内。

Baritone助手提供一键寻路、目的地与鞘翅选择、到达清除、快捷键、传送门滞留、服务器无响应保护、超时重连和可选原版断线方式。耐久、Y 轴、坐标保护从智能 Log 迁入助手，默认关闭、独立于寻路；升级后须重新设置并开启助手。耐久可选仅检查护甲、忽略鞘翅。其余设置沿用源码中的标识。

助手输入 X Z 或 X Y Z 后点击“一键起飞”；非鞘翅模式为普通寻路。鞘翅寻路只支持下界，并检查鞘翅、烟花和本地寻路库。

一键清除先取消古城、蘑菇岛和鞘翅定位，再停止仓库搜索并清除当前服务器记录。修复 CSV 列与坐标转换、重复加载、界面清空保存；磁盘写入失败尝试回滚。地图操作使用当前实际世界。

基础环境：Minecraft 1.21.11、Meteor 1.21.11-86、Fabric Loader 0.18.2+、Java 21+。仓库搜索另需 Xaero Minimap 26.4.2、World Map 1.44.2、XaeroPlus 2.35.1 及其依赖；飞行增强和助手寻路另需 Meteor Baritone。缺少依赖时部分模块不注册，助手按钮给出提示。外部模组不打入插件。

Webhook 默认关闭，地址与提及 ID 为空。仅用户主动配置并开启后发送仓库坐标与容器信息，普通通知含玩家名。本次检查未发送通知。

构建需要 JDK 25、Python 3.9+；按 README 获取固定哈希依赖、执行 Gradle build 和发布检查。未做真实游戏/服务器操作验证。授权范围、来源及署名见 NOTICE.md，更新内容见 RELEASE-NOTES.md。
