# 0.1.19 默认版本验证

本版以 0.1.18 精简重构版为基础，仅把“服务器战利品版本”的默认配置从 1.21.11 改为 26.1。已有 Meteor 保存配置继续优先，客户端要求仍为 Minecraft 1.21.11。

## 本次检查

- 重新执行离线 Gradle test/build，100 项 JUnit 回归通过。
- check_release.py 核对最终生产 JAR 中默认枚举为 MC_26_1，且模块不再引用旧默认 MC_1_21_11；保留 Java 21、9 处 Minecraft 和 5 处可选 Xaero 注入检查。
- check_refactor.py 与用户原始 0.1.17 JAR/源码比较，预测源码、存储、命令、界面、Mixin 和规则资源保持一致。最新体积与哈希见 refactor-comparison.json。
- 发布脚本核对当前 JAR 哈希和报告，打包源码与验证证据。

## 证据范围

本次构建和检查报告见发布包 validation 中的 release-build.log、unit-checks.json、release-checks.json 和 refactor-comparison.json。

baseline-build.log 与 baseline-unit-checks.json 为此前原版 0.1.17 的基线重建和 93 项测试证据。预测及战利品 oracle 报告继承此前版本，本次仅核对源码及成品哈希，没有重新执行 oracle。0.1.18 曾执行源码包独立重建；本次没有重复该项，不在本版发布包中包含那次重建报告。

本次未启动可视 Minecraft 客户端，游戏内显示和交互未实测。默认配置修改不强制覆盖已有用户配置；已有配置仍显示旧值时，可手动选择 26.1 或重置此项。
