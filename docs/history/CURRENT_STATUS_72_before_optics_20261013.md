# 当前状态

当前交付为 **1.0.0 / 72，Room 17**。按用户提供的 iOS App Store 日间 / 夜间视频完成 Dock 适配，浅色左端帽两项历史失败已修复；自动检查与 API35 模拟器复验通过。物理手机未连接，实机颜色、手感和用户最终视觉验收 pending。

当前证据见 [72 版适配报告](DOCK_IOS_ADAPTATION_20261013.md)、[日夜参考标准](IOS_MATERIAL_STANDARD_20261013.md)及 [Code Wiki](code-wiki/README.md)。独立玻璃工作台已取消并删除，历史范围调整见 [取消与复验报告](WORKBENCH_REMOVAL_20261013.md)。

## 当前产物

[本次正式签名 APK](../.external_outbox/m1racle-reader-release-latest.apk)：27,121,558 bytes。

SHA-256：c82688159dbe9dbce68a5fb7da3bdd7305a6e0f63a3b5778c89acc32c6be219e。

构建快照：m1racle-reader-release-v1.0.0-20261013-221210.apk。正式证书连续、16 KiB 对齐、minSdk31 / targetSdk35、无 INTERNET；保留 profileable shell=true。构建输出、Outbox、已安装 APK、Download 和回读摘要一致。QA 包只供隔离验证。

## 当前自动验证

| 检查 | 结果 | 边界 |
|---|---|---|
| 全量 QA JVM | 1,780 项，0 失败 / 错误，2 环境跳过 | 包含保留的 QA UI 测试 |
| 全量 Release JVM | 1,637 项，0 失败 / 错误，2 环境跳过 | 公共逻辑与生产变体契约 |
| Release lint / 签名构建 | 0 errors / 170 warnings / 4 hints；构建成功 | 警告保留，未关闭检查 |
| QA 原生 Dock 像素 | 32/32 通过，152.233 秒 | API35；保留原 29 项断言，增加三项日夜回归 |
| 同测试 APK 负向验证 | 旧材质三项失败，新材质通过 | 浅色端帽两项、夜间黑底层级 |
| 正式 72 日夜流程 | 左右端 / 中央、局部切色、跨栏往返、释放、导入取消通过 | 模拟器实际录屏与关键帧已核对 |
| Wiki 静态校验 | 20 篇、3,127 相对链接、113 关键 API，通过 | 不等于物理手机验收 |

两项既有环境跳过分别为外部老子夹具未挂载、环境不支持符号链接。没有新增忽略或跳过。构建与验证期间 534 个应用 / 测试 / 配置文件摘要已核对；数据库、依赖和阅读逻辑未改。

## 视觉与设备状态

- 日间 / 夜间参考均已保存；72 实现夜间炭灰轨道、灰色紧凑选中态、明亮蓝色图文与浅色真实端帽细轮廓。真实采样、细色散、局部放大 / 切色及双向拖动保持。
- 历史 lightPickupShowsCapturedRailOutlineInsideLens、lightLeftCapHasClearSpaceInsideHeldLens 现均通过；同场景左端对比达标行数 16/42 → 38/42，阈值未变。
- API35 模拟器已保留数据 71 → 72 升级，firstInstallTime 未变；正式包已自动交付到 Download。结束为密度 420、夜间 no、正式应用首页；未发现本次主包崩溃。
- 原参考与 Reader 内容 / 入口数量不同，已分别核对画面和自动行为，不宣称逐像素等同或物理触摸时序一致。物理手机安装、实际屏幕颜色和手感仍 pending。
- 独立工作台维持删除；其他能力的完整用户流程边界见 [原始验收矩阵](STABILIZATION_20261013.md)，不将本次 Dock 检查扩大为所有功能的实机验收。
- 修改前完整源码、旧正式包与 QA、失败及最终证据均保留；未 commit / push。此前状态已 [归档](history/CURRENT_STATUS_71_before_ios_adaptation_20261013.md)。
