# 兼容与构建

Tanghulu 1.0.6 仅支持 Minecraft **1.21.11**；1.21.4 请使用 1.0.5。

- 运行：Java 21+、Fabric Loader 0.18.2+、Meteor 1.21.11（编译基线 1.21.11-86）。
- 自动挖矿需要对应版本的 Baritone；地图功能另装 Xaero。编译基线：Minimap 26.4.2、World Map 1.44.2、XaeroPlus 2.35.1。
- 26.x 世界预测辅助程序需要 Java 25；历史预测版本不代表支持对应版本客户端。
- 同一实例只保留一个 Tanghulu JAR，升级前备份配置。

构建需要 JDK 25、Python 3 和网络：

```sh
python build-support/fetch-compat-deps.py 1.21.11
./gradlew build
```

Windows 使用 `gradlew.bat`。依赖按锁定 URL 和 SHA-256 校验下载，产物位于 `build/libs/`。可追加 `runPackagedSmoke` 检查打包加载，安装 Baritone 的加载检查追加 `-PsmokeBaritone`。
