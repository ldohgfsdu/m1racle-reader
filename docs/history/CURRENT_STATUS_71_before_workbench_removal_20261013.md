# 当前状态

当前交付仍为已签名 **1.0.0 / 71，Room 17**。2026-10-13 本轮完成源码检查点、验证入口及测试契约整改，并建立全量基线；**发布门禁未通过，未构建或分发新生产 APK**。详见 [稳定性核查与验收矩阵](STABILIZATION_20261013.md)。

## 当前产物

[正式签名 APK](../.external_outbox/m1racle-reader-release-latest.apk)，**27,131,998 bytes**。
SHA-256：`e67a0a9701b0879c627698b1d07c47fd313b4d9ad9bb8f94983be17607e71cde`。
原构建快照：`m1racle-reader-release-v1.0.0-20261013-002125.apk`。

该文件是既有 71 版，本轮摘要未变，不作为本轮新构建证明。此前正式签名、16 KiB 对齐、模拟器保留数据升级与交付证据见 [71 版阶段报告](DOCK_SUBTLE_DISPERSION_20261013.md)及[本轮前状态快照](history/CURRENT_STATUS_71_before_stabilization_20261013.md)。

## 本轮验证

| 检查 | 结果 | 边界 |
|---|---|---|
| 全量 QA JVM | 1,787 项 / 0 失败 / 0 错误 / 2 环境跳过 | 归位前全量证据已归档；随后 QA 专属测试归位，定向补验通过 |
| 独立验证入口及源集归位 | Reading + 两类 QA 专属测试 56/56 | 包含归位测试 6 项；入口不依赖仓库外 helper |
| 核心原生回归 | 24/24，通过 | API 35 隔离 QA：迁移、选字、字体、系统滚动截图、删除与位置事务 |
| Dock 原生像素 | 27/29，两项失败 | 浅色左端帽 16/42 行达到既有对比要求；断言未放宽 |
| Release host tests | 编译失败，未执行 | 已显式启用任务；工作台正式范围与当前 QA 隔离实现冲突 |
| Release lint | 0 errors / 198 warnings / 4 hints | 当前复验通过，未禁用检查 |
| Wiki 静态校验 | 20 篇 / 3,146 相对链接 / 113 关键 API，通过 | 不等于应用或视觉验收 |

全量中的两个跳过：外部老子夹具未挂载、当前环境不支持符号链接。本轮未新增跳过条件。

本轮 main / production 的 260 个文件与检查点一致，QA 应用 APK 摘要一致；删除事务测试修复仅重建 instrumentation。没有对生产包执行卸载、清库或 instrumentation。

## 设备与未决项

- 当前仅 emulator-5554 / API 35 在线；模拟器原生结果单独记录，物理手机未连接，本轮手机安装与视觉/手感 pending。
- Dock 尚未通过：lightPickupShowsCapturedRailOutlineInsideLens、lightLeftCapHasClearSpaceInsideHeldLens。继续定位实际采样/折射根因，不能以测试或截图代替参考动态验收。
- 工作台要求待明确：design.md 要求正式设置提供独立工作台，当前实现与源集却只留 QA。已向用户提出目标选择；没有擅自改变正式行为或删除正式版验收要求。
- 其他积压功能的逐项实现、自动验证、模拟器及物理手机覆盖见 [验收矩阵](STABILIZATION_20261013.md)；本轮部分核心原生通过不代表全部用户流程已验收。
- 检查点位于工作区 backups/20261013-075653-stabilization；现有改动完整保留，未 commit / push。
