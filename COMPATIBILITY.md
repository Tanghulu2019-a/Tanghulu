# Tanghulu 1.0.5

本次仅适配 Minecraft **1.21.11** 和 **1.21.4**，各自使用独立 JAR。原始输入文件保留不变。

## 安装

把与游戏版本相同的 `Tanghulu-mc版本-1.0.5.jar` 放进该实例的 `mods` 文件夹，移除同实例中旧的 Tanghulu JAR，保留对应版本的 Fabric 和 Meteor。

| 游戏版本 | 验证使用的 Meteor | Fabric Loader | 可选地图 API 的编译基线 |
|---|---|---|---|
| 1.21.11 | 1.21.11-86 | 0.18.2 | Xaero Minimap 26.4.2、World Map 1.44.2、XaeroPlus 2.35.1 |
| 1.21.4 | 1.21.4-42 | 0.18.2 | Xaero Minimap 26.4.2、World Map 1.44.2、XaeroPlus 2.35.1 的 1.21.4 版本 |

游戏运行需要 Java 21 或更新版本。使用原有 26.x 独立预测功能时仍需要 Java 25。
地图、Baritone 等可选模组请安装与游戏版本相同的发行包；编译依赖没有打入插件 JAR。

Minecraft 的加载条件严格限定到对应版本。Meteor 的依赖条件改为 `*`，取消原来仅接受 `1.21.11-86` 的限制；是否可加载也受 Meteor 自身的游戏版本声明约束。
兼容范围以 Meteor 的标准 API 为基础；上表是本次实际验证的构建，不表示逐一测试过所有历史构建或第三方分支。

## 实现

- 两个版本使用同一套功能源码，通过 `-PmcVersion` 选择依赖和 Minecraft ABI 转换。
- `build-support/legacy1214.py` 明确处理 1.21.4 的 NBT、背包包体、玩家渲染、村民职业、设置列表、天气、提示通知、航点清理和独立地形环境。
- 音符机器人关闭时通过 Meteor 的 `ActiveModulesChangedEvent` 清理等待中的操作，不再向其内部类新增 `onDeactivate()` 覆盖方法。
- 系统聊天恢复同时识别两种版本的悬浮提示字段，并分别用对应协议的样本验证。
- `ancient` 在开发运行中也作为父插件的库加载，避免重复注册同名 Mixin。
- 历史原版预测证书按实际生成的源码重新核对；1.21.11 的旧证书不会被误当作 1.21.4 的验证结果。

## 构建

推荐使用 JDK 25、Python 3 和随附的 Gradle Wrapper。源码包不附带第三方编译 JAR；以下脚本按固定 URL 和 SHA-256 下载依赖，Gradle/Minecraft 的其他依赖在首次构建时下载。

在项目目录内运行，PowerShell 中保留版本参数的引号：

```powershell
python build-support/fetch-compat-deps.py 1.21.11
.\gradlew.bat '-PmcVersion=1.21.11' build

python build-support/fetch-compat-deps.py 1.21.4
.\gradlew.bat '-PmcVersion=1.21.4' build
```

默认目标是 1.21.11。产物位于 `build/libs/`，文件名包含游戏版本；请逐个版本构建。
依赖恢复脚本优先遵循 `libs/<版本>/dependencies.json` 中的固定 URL 和 SHA-256，不自动更换现有锁定版本。
生成的 Java 文件在 `build/generated/compat/java/`；修改功能时编辑 `src/main/java/` 和明确的兼容转换文件。

## 验证

1.0.5 统一修正了发布文件名、Fabric 元数据和插件内版本常量，并重新构建、验证两个发布 JAR 的加载。功能回归结果沿用上一兼容构建，日志与本次版本修正检查分别保留。

两个版本均运行完整 `build`：原有 107 项 JUnit 单元测试、音符机器人调音/队列/重试/交互/材料检查、古城战利品、导航、骨架、聊天、3C、记录持久化等回归，以及新增 83 项 Mixin API 检查。

另外两个版本均验证了最终发布 JAR 的 Fabric 生产加载：实际使用 intermediary 命名空间，应用 18 个核心目标上的 Mixin，并解析插件类；在 preLaunch 阶段退出，不打开游戏窗口。
加载检查没有安装可选地图和 Baritone，其专属类型不进行反射签名检查；这些功能已针对对应版本的真实依赖编译，但未进行游戏内操作验证。

```powershell
.\gradlew.bat '-PmcVersion=1.21.4' runPackagedSmoke
.\gradlew.bat '-PmcVersion=1.21.4' terrainCompatibilityChecks
```

独立原版地形检查在两个版本中均生成种子 0 的古城箱子，读到相同的战利品种子 `-6933813245192795891`，验证重复读取和空位置返回值，并正常保存关闭。
历史的完整原版对照工具 `:ancient:validatePrediction`、`:ancient:validateLootProfiles` 保留为 1.21.11 开发工具，不属于常规 `build`；新增的地形兼容检查同时支持两个目标。

以上完整构建与加载结果来自输入版本的验证说明，本次发布未重新执行完整构建。尚未手动进入服务器测试、播放歌曲或检查实际画面。
