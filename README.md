# Tanghulu-1.0.2

面向 Minecraft 1.21.11 的 Fabric / Meteor Client 插件，包含种子工具、古城定位、蘑菇岛定位等功能，使用统一的 Tanghulu 分类。

## 1.0.2 更新

- 移除陀螺旋转、种子矿透、防火行走、自动顶头跳、自动种树、挂机原版飞行、坐标直行。
- 智能 Log 的“耐久与位置检测”整体移动至 Baritone助手，包含耐久、Y 轴和坐标保护；独立于寻路工作，开关默认关闭。
- 升级后请在 Baritone助手中重新设置这三类保护并开启助手；智能 Log 的其他检测保留。
- 作者名称改为 Tanghulu2019，贡献名单增加 Jefff。

## 1.0.1 历史更新

- 末地鞘翅定位联动 Xaero 小地图，自动保存全部搜索结果，重复搜索不会重复添加同名同坐标航点。航点为结构预测点，不保证鞘翅尚未被取走，也不标定安全落点高度。
- 智能 Log 移除防 32K、智能预测；新增耐久、Y 轴、坐标和自定义物品数量检测，均默认关闭。
- 耐久检测针对主副手和穿戴装备，剩余百分比小于等于阈值触发；Y 轴检测在安全上下限之外触发；坐标检测按当前维度的 X/Z 中心与半径，选择进入或离开触发。
- 自定义物品可选多种，逐种统计背包、快捷栏和副手；任一种小于等于下限或大于等于上限即触发。新增个人安全检测不受“仅当有玩家时断开”限制。
- 一键清除优先取消本插件的古城、蘑菇岛和鞘翅定位，再删除 Xaero（全部来源）与 Meteor 航点，清除后定位模块保持关闭，重新定位需手动开启。
- 新区块探测介绍增加“测试版”；更名为“图腾粒子自定义”和“告示牌文字漂浮”。重命名模块的旧配置可能需要重新设置。
- Fabric 模组信息和 Meteor 插件网站显示 [GitHub 仓库](https://github.com/Tanghulu2019-a/Tanghulu)。

## 安装与兼容性

- Minecraft **1.21.11**，Meteor Client **1.21.11-86**，Fabric Loader **0.18.2+**，Java **21+**。
- 将 `Tanghulu-1.0.2.jar` 放入 `mods`，移除旧版 Tanghulu，避免重复加载。
- 相关模块需要另外安装匹配版本的 Meteor Baritone、Litematica / MaLiLib、Xaero Minimap / World Map / XaeroLib或 MiniHUD。
- 26.x 世界预测辅助程序需要 Java 25；主模组字节码目标仍为 Java 21。首次获取官方运行资源需要联网。

## 功能与使用

在 Meteor 菜单的 **Tanghulu** 分类中配置功能。支持结构与鞘翅定位、古城战利品预测、蘑菇岛定位、玩家骨架显示、3C 玩家时长查询，航点清理和原有工具功能。

保留的 Numby 功能：**图腾粒子自定义、告示牌文字漂浮、新区块探测**。
模块名称、介绍与详细设置已汉化，使用说明见 [Numby 功能移植](NUMBY-FEATURES.md)。

- [玩家骨架显示](PLAYER-SKELETON.md)、[蘑菇岛定位](MUSHROOM-LOCATOR.md)、[3C 玩家查询](3C-NEWBIE-CHECK.md)。
- 古城命令使用 `.aah`，例如 `.aah seed <种子>`、`.aah loot <版本>`、`.aah scan`。
- 保留既有默认世界种子 `-7346913998703726680`；使用其他世界时请自行修改。已有 Meteor 配置可能保留旧设置。
- 原 `config/ancient-apple-hunter` 数据目录继续使用，升级前请备份个人游戏配置。

## 从源码构建

构建需要 **JDK 25、Python 3.9+** 和网络。Gradle Wrapper 固定为 9.2.0；主模组使用 `--release 21`，隔离的预测辅助程序使用 `--release 25`。

```powershell
python build-support/fetch-dependencies.py
.\gradlew.bat --no-daemon build
python build-support/verify-merge.py
python build-support/verify-stash.py
python build-support/verify-release.py
python build-support/verify-numby.py
python build-support/package-release.py
```

Linux / macOS 将构建命令替换为 `./gradlew --no-daemon build`。下载脚本从固定官方分发地址获取 5 个编译依赖，并校验 SHA-256；第三方模组 JAR 不包含在发布源码包内，也不打入最终插件。版本、下载地址及校验值见 `build-support/dependencies/downloads.json`。

主构建产物为 `build/libs/Tanghulu-1.0.2.jar`；正式候选包与校验文件输出到 `dist/`。`ancient/` 是内部子项目，正常安装只使用主项目 JAR。

正常 `build` 包含古城的 100 项单元检查，以及种子兼容、蘑菇岛扫描、系统聊天、航点清理、3C 查询逻辑与骨架显示检查。联网查询测试须显式提供玩家名：

```powershell
.\gradlew.bat threeCLiveCheck -PplayerName=ExamplePlayer
```

`ExamplePlayer` 仅为参数示例，不保证服务中存在。普通构建不执行这项联网查询。

## 隐私与联网行为

开源版移除了本机构建路径日志、QQ 群邀请、未使用的旧认证和校时工具，以及未使用的凭据保存类。玩家测试数据为虚构样本。

启用 3C 查询时会将所查询的玩家名发送给 `3c3u.info`。世界预测功能可能从 Mojang 官方地址下载并校验运行资源。个人配置、令牌、游戏数据和本机日志不应提交到仓库。Stash Webhook 默认关闭且地址为空；主动配置并启用后发送仓库坐标、容器信息，普通通知还包含玩家名。

自动检查不能代替游戏内验证；当前发布检查结果与已知限制见 [发布验证记录](RELEASE-VALIDATION.md)。正式打包入口为 `package-release.py`。不再附带旧整合版的源码、脚本和验证记录。

## Stash 恢复整合

按维护者确认的作者授权（覆盖修改、公开源码和随 GPL-3.0-only 项目分发及引入的第三方部分），新增 5 个模块和 2 个 HUD。上游标签没有 LICENSE；此处记录维护者的授权声明。详见 [恢复说明](STASH-RESTORE.md)。

- Baritone助手、增强仓库搜索、鞘翅飞行增强、40度飞行辅助、装饰透视。
- Weather / EntityList HUD，以及模块汉化和飞行钩子。
- Baritone助手保留一键寻路、鞘翅起飞、到达清除目的地、传送门滞留和无响应断线/重连。耐久、Y 轴、坐标保护已移入助手；耐久新增仅检查护甲及忽略鞘翅选项。
- 增强仓库搜索需同时安装 Xaero Minimap 26.4.2、World Map 1.44.2、XaeroPlus 2.35.1 及其依赖；鞘翅飞行增强和助手寻路需 Meteor Baritone。缺少依赖时不会注册对应飞行/仓库模块；助手按钮会给出提示。
- 一键清除航点保留 1.0.1 的定位任务取消逻辑，并停止增强仓库搜索、清除 JSON/CSV 记录及内存缓存。

结构定位在聊天中输出最近 20 个坐标及总数。末地鞘翅定位默认将全部结果保存至当前服务器的末地 Xaero 航点，同时在聊天显示最近 20 个。古城和蘑菇岛各自的地图功能保留。一键清除航点优先停止本插件的三种定位模块，清除当前服务器/存档全部维度、分组及来源的 Xaero 与 Meteor 航点。独立存储的其他模组航点需要对应适配；持续生成航点的外部模组需关闭其生成功能。

## 许可证与来源

项目沿用 **GPL-3.0-only**，见 [LICENSE](LICENSE)。Tanghulu2019 为本模组作者及主要贡献者；贡献名单为 Cqb13、Mikumiku、Jefff。上游源码中的作者署名及第三方许可证声明保留，见 [NOTICE.md](NOTICE.md) 与 `ancient/licenses/`。
