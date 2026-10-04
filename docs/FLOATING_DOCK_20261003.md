# 悬浮底栏与按压修正

后续用户反馈确认四页静止材质仍不一致；本页的首页观感不能作为四页通过证据。根因及当前修复见 [四页采样一致性](DOCK_CAPTURE_CONSISTENCY_20261003.md)。

用户指出底栏偏灰，并要求仔细参考 RiseDiary 按压行为、悬浮底栏下方不要有铺层。本轮仅调整底栏；保留此前审查、排版与阅读器改动。

## 改动

- 删除整片 `SoftScrollEdge` 模糊层及其采样支路。外层底栏直接采样页面，底栏之外没有 App 绘制的模糊或灰色铺底；Android 系统手势指示条仍由系统显示。
- 静止选中胶囊使用浅色黑 / 深色白的单一灰色覆盖；按压时覆盖退去。保留共享 Capsule 材质，避免影响 PDF 阅读控件。
- 新增隔离的蓝色实心图标 / 标签采样层，通过现有受保护 `GlassBackdropSource` 捕获；不增加可访问导航节点。前景在透镜区域挖空，选中透镜绘制在前景之上，避免重复图标。
- 对照 [RiseDiary 实现](https://github.com/sky-shunfengjun/RiseDiary/blob/master/app/src/main/java/com/risediary/app/ui/components/LiquidGlassBottomBar.kt)：按压透镜约放大至 1.39 倍，采样图标 / 标签至 1.2 倍；拖动速度影响横纵形变，释放后弹簧复原。沿用 10dp / 14dp 透镜及交互高光。
- 本轮未复制参考项目源码、增加依赖，未实现完整 Apple 原生材质自适应或滚动收缩。

## 验证

JDK 17，material / hub / home 针对性 QA 单测 **111 项、0 失败 / 错误 / 跳过**；移除废弃模糊功能的三个用例，替换为底栏下方页面触摸回归，并更新采样拓扑契约。实际采样层的标签 / 四入口语义检查通过。

`:app:assembleQa`、`:app:lintRelease` 退出 0；Lint 0 errors / 156 warnings / 4 hints。未重跑完整套件。

Android 15 `emulator-5554`：检查浅色静止 / 按住选中入口、横向拖动到设置并释放、深色及系统 200% 字号；未裁切底栏标签，底栏外页面背景保持原色。恢复 100% 字号、浅色、首页。捕获日志未见 FATAL EXCEPTION。未进行真机与性能验收。

外部证据和交接在 `~/.devhub/runs/unmanaged-reader-floating-20261003/`。会话 unmanaged；受监管身份 unknown，HEAD `a314247d9cc27f7b3adc1edb3074b65eee3afbfe`，分支 `codex/fix-project-audit`。保留未提交现场；QA 仅为模拟器验收包，正式签名 Release 仍待注入环境。
