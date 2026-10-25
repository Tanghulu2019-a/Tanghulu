# Stash 恢复说明

以提供的 1.0.1 源码为基线，仅从 1.0.0 恢复缺失功能。版本保持 1.0.1，不能与旧版同时加载。

## 恢复范围

7 个模块：AFKVanillaFly、AutoLogPlus（Baritone助手）、BetterStashFinder、ElytraFlyPlusPlus、GotoPosition、Pitch40Util、VanityESP。
2 个 HUD：Weather、EntityList。恢复中文模块/设置显示及必要 Mixin，沿用原模块配置标识。

AutoLogPlus 只保留一键寻路、目的地/鞘翅选择、到达清除、快捷键、传送门滞留、服务器无响应保护、超时重连及原版可选断线方式。
耐久、高度、坐标检测统一使用智能Log，避免重复设置和两套判断。护甲检查可选只检查护甲、忽略鞘翅。
旧版 AutoLogPlus 的 log-on-y/log-armor/log-position 设置不再生效；请在智能Log重新设置并开启。其余恢复模块沿用原来的设置标识。

一键清除保留原有三种异步定位取消逻辑，额外停止仓库搜索、清空其当前服务器 JSON/CSV 和内存缓存；磁盘写入失败时尝试回滚。
修复仓库 CSV 列分隔、方块/区块坐标混淆、木桶/合成器列丢失及重复加载；修复界面清空不保存的问题。
地图写入使用当前实际世界，避免地图界面浏览其他服务器时操作错误世界。
修复坐标直行退出世界时的空玩家访问，以及助手重连完成后恢复临时重连设置的时机。

## 依赖及使用

基础：Minecraft 1.21.11、Meteor 1.21.11-86、Fabric Loader 0.18.2+、Java 21+。
增强仓库搜索：Xaero Minimap 26.4.2、World Map 1.44.2、XaeroPlus 2.35.1，以及这些模组所需的 XaeroLib 等依赖。
鞘翅飞行增强和助手寻路：匹配版本的 Meteor Baritone。助手鞘翅寻路仅支持下界，并检查鞘翅、烟花和本地寻路库。
上述外部模组不打入 Tanghulu JAR。缺少依赖时相关模块不注册或按钮提示缺少 Baritone。
助手输入 X Z 或 X Y Z 后点击“一键起飞”；非鞘翅模式为普通寻路。“坐标直行”仅直线前进，不负责绕障。
Webhook 默认关闭，只有用户启用并设置地址后才发送仓库通知；未在本次验证中向任何地址发送通知。

## 构建与检查

JDK 25、Python 3.9+；运行 README 的依赖下载、Gradle build 与发布检查命令。
普通 build 包含原有检查，并增加寻路生命周期和仓库 CSV 回归检查。
verify-stash.py 检查打包内容、可选依赖隔离、实际 Minecraft 飞行注入签名和无依赖飞行钩子生命周期。
自动检查不能保证服务器反作弊兼容性；尚未启动 Minecraft 做真实游戏操作测试。

作者来源及用户提供的授权声明见 NOTICE.md，保留已有项目许可证和文件署名。
