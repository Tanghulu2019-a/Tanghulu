# Ancient Apple Hunter 0.1.19

古城附魔金苹果箱定位插件。以用户提供的 **0.1.17 JAR 和配套源码**为基线，精简代码结构，保留定位、手动搜取记录与地图显示行为。

适配 **Minecraft 1.21.11 / Meteor 1.21.11-86 / Java 21+ / Fabric Loader 0.18.2+**。无需 Baritone；Xaero Minimap / World Map 为可选联动。

## 安装与使用

退出游戏，将旧版插件移出 `mods`，放入 `ancient-apple-hunter-0.1.19.jar`，只保留一个版本。保留原 `config/ancient-apple-hunter`，无需清空缓存、种子或黑名单。

填写完整世界种子，选择服务器实际使用的战利品版本，点击“定位箱子”。自行移动、开箱和拿取；插件根据服务端同步结果隐藏已打开且含苹果的箱子，确认拿完后记录搜取黑名单。

默认值：种子 `-7346913998703726680`、服务器战利品模型 `26.1`、搜索半径 `10000`；Xaero 航点、穿墙图标、航标光束默认开启，已处理箱子默认隐藏。图标与光束距离分别默认为 `2048` 格，每种世界标记各保留最近 `128` 个符合条件的目标。已有 Meteor 配置优先。

- 只定位主世界古城；搜索半径最大 `131072` 格，支持安全取消。
- 保留 16 个战利品模型：1.21.1～1.21.11、26.1、26.1.1、26.1.2、26.2、26.3。客户端版本仍为 1.21.11。
- 保留世界内苹果和光束、小地图苹果、全屏地图苹果、附魔闪光及悬停/右键行为；保留原大小、颜色、平面光照与光照恢复。
- 黑名单窗口支持搜索、排序、分页、复制坐标、单条恢复、跨页批量恢复和确认清空；记录仍按服务器、维度、种子与 Xaero 世界隔离，并跨战利品模型保持。
- 保留双箱实际相连两半的识别、服务器确认取物、诊断导出、缺少服务器附魔注册表时的本地规则及协作式取消。

服务器改变世界生成或战利品规则时，以实际开箱结果为准。大范围搜索的时间与内存占用随面积增长。

## 命令

```text
.aah seed <完整世界种子>
.aah loot <版本>
.aah scan
.aah cancel
.aah diagnose
.aah blacklist
.aah blacklist list [页码]
.aah blacklist on
.aah blacklist off
.aah blacklist clear
```

命令 `blacklist clear` 直接清除当前世界和种子的记录；窗口内批量恢复和清空保留二次确认。

## 结构与精简

- `HunterModule`：设置、世界上下文、预测调度和命令/界面入口；统一保存与航点同步流程。
- `render/WorldMarkerRenderer`：世界内苹果、文字和光束；复用苹果物品栈。
- `core/MarkerSelection`：世界渲染和 Xaero 共用的筛选规则；先排除范围外目标，再排序、遮挡检查和截取上限。
- `navigation/ManualCollectionObserver`：观察手动操作和服务器同步，按坐标 Map 直接查找箱子。
- `prediction`、`storage`、`mixin`、黑名单界面与规则资源保持基线内容。
- 9 个定位功能未引用的历史导航模型移入 `src/test`，原代码与回归测试保留，不打入生产 JAR。

本版基于 0.1.18 精简重构版，仅将服务器战利品版本默认值改为 **26.1**。新安装或重置该项时生效；已有 Meteor 保存配置继续优先，可手动切换到 26.1。未添加运行依赖。

## 构建与验证

将 Meteor 1.21.11-86 JAR 放入 `libs`，使用 JDK 21 或更新版本：

```powershell
./gradlew.bat build
```

`test` 通过 `unitChecks` 使用 JUnit Platform 执行，兼容 Windows 中文目录；失败会使构建失败。原版 93 项和本版新增 7 项共 **100 项全部通过**。发布检查核对 Java 21 字节码、9 处 Minecraft 和 5 处可选 Xaero 注入、预测证书与最终 JAR。

本次未修改预测和存储源码、战利品资源；与原始 JAR 比较，732 个条目逐字节一致。原版预测/战利品 oracle 证据继承自 0.1.17，未重新执行。没有启动可视 Minecraft 客户端，实际显示及模组组合仍需游戏内检查。方法和范围见 `VALIDATION.md`。

## 许可

GPL-3.0-only。保留 AncientCityGenerator、SeedFinding 等原有许可和重定位规则；不分发 Minecraft、Meteor 或 Xaero 的完整 JAR。
