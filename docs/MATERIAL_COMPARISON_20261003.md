# 设置独立材质对比页面

2026-10-03 用户明确要求设置页增加独立材质对比入口；此前禁止把旧 H0 / H1 / H2 实验面板留在设置中的要求由本次明确范围补充，不恢复材质切换开关。

设置 → 外观预览 → 材质对比。三组生产配方：PDF 原版 Capsule、底栏 Dock、选中滑块 SelectionLens。背景图与控件几何相同，滑杆同步移动背景，按住控件触发真实按压光学状态。预览变量仅存在页面中，退出不写设置，不修改其他页面实际配方。

每个样本使用独立稳定 LayerBackdrop；只捕获普通背景 Canvas，玻璃作为后续兄弟层采样。页面已在 Hub 内容捕获内部，因此为样本提供自身局部宿主，避免采样到自己的祖先捕获层产生递归。

验证：62 项现有材质 / GlassDock UI 单测通过；Release 源码编译及 QA 构建通过。模拟器 emulator-5554 核对设置入口、同步背景移动、长按高光折射、深色 200% 字号滚动及系统返回；系统恢复浅色和 100% 字号。仅安装隔离 QA；正式 Release 签名配置仍缺失，未交付 Release，未执行真机或完整套件验收。

主要实现：HubSettingsScreen.kt、MaterialComparisonScreen.kt、strings.xml。文档更新 design.md、CHANGELOG.md、docs/releases/v1.0.0.md 和 CURRENT_STATUS.md。

机器外部证据：C:/Users/21234/.devhub/runs/unmanaged-reader-material-comparison-20261003，含 light / moved / pressed / dark-large-bottom 截图、UI XML 与检查日志。本会话 unmanaged，runId / projectId / machineId unknown。

## 文字背景补充

用户要求增加文字背景切换。页面新增图案 / 文字两项；三个样本同步切换，文字由 Compose TextMeasurer / drawText 绘制在普通采样源，保留材质遮挡和真实模糊折射。文字背景随主题提供纸面与墨色，移动背景滑杆同步平移文字。预览状态 rememberSaveable，仅页面内保存。

本补充 Release 源码编译及 QA 构建通过；模拟器核对图案切至文字、背景移动，以及深色 200% 字号下文字采样与滚动布局，恢复浅色 100%。未重跑单测；此前 62 项为前阶段结果。新证据位于 C:/Users/21234/.devhub/runs/unmanaged-reader-material-text-20261003，正式签名配置仍缺失、未交付 Release。
