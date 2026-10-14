# 当前状态

当前交付仍为既有 **1.0.0 / 71，Room 17**。用户已取消工作台，并指定 iOS App Store 录屏为 Dock 新标准。本轮完成工作台删除、测试归位和全量 QA / Release host 复验；未构建或分发新正式 APK，视觉发布门禁尚未通过。

详见[工作台取消与复验](WORKBENCH_REMOVAL_20261013.md)、[新视频视觉标准](IOS_MATERIAL_STANDARD_20261013.md)及[Code Wiki](code-wiki/README.md)。上午首轮核查的原始结果见 [STABILIZATION](STABILIZATION_20261013.md)。

## 当前产物

[既有正式签名 APK](../.external_outbox/m1racle-reader-release-latest.apk)：27,131,998 bytes。

SHA-256：e67a0a9701b0879c627698b1d07c47fd313b4d9ad9bb8f94983be17607e71cde。

原构建快照：m1racle-reader-release-v1.0.0-20261013-002125.apk。该文件摘要本轮未变，不能作为当前源码的新构建证明。旧正式签名、16 KiB 对齐和模拟器升级证据见 [71 版报告](DOCK_SUBTLE_DISPERSION_20261013.md)。QA 产物只供隔离验证。

## 当前自动验证

| 检查 | 结果 | 边界 |
|---|---|---|
| 全量 QA JVM | 1,780 项，0 失败 / 错误，2 环境跳过 | 所有保留 UI 测试继续在 QA 执行 |
| 全量 Release JVM | 1,637 项，0 失败 / 错误，2 环境跳过 | 公共逻辑、生产开关及工作台类 / 路由 / 资源缺席契约 |
| Release lint | 0 errors / 170 warnings / 4 hints | 未禁用检查 |
| Full + PrepareDeviceTests | 成功，退出码 0 | 单测、lint 及当前 QA 应用 / instrumentation APK 构建 |
| QA 模拟器流程 | 设置无工作台入口、文档工具往返、Dock 恢复和返回首页通过 | API 35；截图与界面树已核对；未执行 instrumentation |
| Wiki 静态校验 | 20 篇、3,126 相对链接、113 关键 API，通过 | 不等于应用视觉验收 |

每种 JVM 构建各有两项既有跳过：外部老子夹具未挂载、环境不支持符号链接。本轮没有新增忽略或跳过条件。

24 个依赖 Compose QA 宿主的 UI 文件已归入 testQa，移动前后摘要一致；正式版公共测试仍执行。本轮材质 / Dock 五个源文件及版本文件摘要未变；生产包安装信息前后一致，无生产包安装、清库或 instrumentation。

## 视觉与设备状态

- 新参考已提取并保存，优先于旧截图 / 视频；当前材质尚未按新标准完成匹配验收。
- 上午原生基线为核心 24/24、Dock 27/29；本轮未重跑，不能当作当前源码的新原生通过证明。
- Dock 未通过项仍为 lightPickupShowsCapturedRailOutlineInsideLens、lightLeftCapHasClearSpaceInsideHeldLens；继续定位真实采样 / 折射与左端帽轮廓，不放宽像素断言。
- 当前 API 35 模拟器已恢复，既有生产应用回到前台。物理手机未连接，手机安装、动态视觉与手感 pending。
- 工作台需求冲突已按用户取消决定解除；其他能力的完整用户流程验收范围仍见[验收矩阵](STABILIZATION_20261013.md)。
- 修改前源码检查点与相关文件 ZIP 均保留；未 commit / push。此前状态见[取消工作台前快照](history/CURRENT_STATUS_71_before_workbench_removal_20261013.md)。
