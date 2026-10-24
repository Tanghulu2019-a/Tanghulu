# Tanghulu-1.0.2

适用于 Minecraft **1.21.11**、Meteor Client **1.21.11-86**、Fabric Loader **0.18.2+**，主模组需要 Java **21+**。以下变化对比 GitHub 已发布的 **Tanghulu-1.0.1**。

## 新增 Stash 功能

引入并适配 [meteor-stashhunting-addon / jeff mod 0.16.7](https://github.com/miles352/meteor-stashhunting-addon/releases/tag/0.16.7) 的部分源码，保留 jefff、xqyet 等原作者署名，提供中文名称、设置与说明：

- **Baritone助手**：普通寻路、下界鞘翅起飞、目的地输入与到达清除，传送门滞留、无响应断线及超时重连保护。
- **增强仓库搜索**：已加载区块搜索、Xaero 航点、JSON/CSV 记录与可选 Webhook 通知。
- **鞘翅飞行增强、40度飞行辅助、装饰透视**。
- **Weather / EntityList HUD**。

最终版本包含上述 **5 个模块、2 个 HUD**，并非引入上游全部功能。增强仓库搜索需 Xaero Minimap 26.4.2、World Map 1.44.2、XaeroPlus 2.35.1 及其依赖；鞘翅飞行增强与助手寻路需匹配的 Meteor Baritone。外部模组不随插件打包。

## 迁移、移除与修复

- 智能 Log 的耐久、Y 轴与 X/Z 坐标保护移至 **Baritone助手**，独立于寻路执行，默认关闭。升级后需在助手重新配置保护并开启助手；智能 Log 其他检测保留。
- 相对公开 1.0.1 移除 **陀螺旋转、种子矿透、防火行走、自动顶头跳、自动种树**，清理种子矿透专用注入。挂机原版飞行、坐标直行不包含在本版中。
- 修复仓库 CSV 分隔、方块/区块坐标混淆、木桶/合成器列、重复加载及界面清空不保存的问题。
- 一键清除航点新增停止仓库搜索、清除当前服务器 JSON/CSV 与内存记录，写入失败尝试回滚。地图操作使用当前实际世界。
- 贡献来源名单增加 Jefff；保留 Numby 的图腾粒子自定义、告示牌文字漂浮及新区块探测。

- 移除启动聊天中残留的群号，并将该标识加入源码与 JAR 递归检查。

## 安装、隐私与许可

移除旧版 JAR，将附件 `Tanghulu-1.0.2.jar` 放入 `mods`。升级前备份配置和航点；一键清除会删除当前服务器/存档各维度、分组及来源的 Xaero、Meteor 航点和本插件仓库记录。预测点不保证资源仍存在或落点安全。26.x 世界预测辅助程序另需 Java 25。

Webhook 默认关闭、地址和提及 ID 为空；用户主动配置并开启后，会向指定地址发送仓库坐标、容器信息，普通通知还包含玩家名。启用 3C 查询会向 `3c3u.info` 发送查询的玩家名；预测功能会获取 Mojang 官方资源。

项目继续按 **GPL-3.0-only** 发布。Stash 上游标签未附 LICENSE；本次分发依据维护者确认取得的授权，范围覆盖引入代码及其第三方部分的修改、公开源码和随 GPL-3.0-only 项目分发。此为维护者声明，不是上游公开许可证或作者直接参与本项目的证明。来源、修改说明与保留许可证见 [NOTICE.md](NOTICE.md)。

附件包含 JAR、完整对应源码与构建脚本 `Tanghulu-1.0.2-source.zip`、`SHA256SUMS.txt`。自动检查结果见 [RELEASE-VALIDATION.md](RELEASE-VALIDATION.md)；未进行游戏内人工测试，自动隐私检查不能保证零风险。

[完整源码差异](https://github.com/Tanghulu2019-a/Tanghulu/compare/Tanghulu-1.0.1...Tanghulu-1.0.2)
