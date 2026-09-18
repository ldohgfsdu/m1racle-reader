# PDF 控件可用性完善（2026-09-15）

## 用户目标与范围

用户要求完善 PDF 控件并明确继续。沿用既有 PDF 控件及磨砂材质，聚焦更多菜单的可读性、选择状态和子面板进入流程。只构建 Release，不构建或交付 Debug。

## implemented

1. 阅读方式/阅读界面的选项使用 Compose `selectable` 和 `Role.RadioButton`，由既有 selected 值直接驱动语义与勾选图标；普通菜单项为 Button。展开标题提供“已展开/已收起”语义，状态来自现有 ReaderMoreState。
2. 菜单标签允许两行；行高按系统字号缩放且不低于原尺寸，主容器与菜单行共享行高计算。保留已有宽度、锚点、滚动、磨砂、投影和阅读业务。
3. 修复行内选项转子面板时可能停在 PREPARING 的问题：旧逻辑比较 `mainCardRectRoot.height` 与自然内容高度；受窗口约束时两者永远不同。改为等待容器动画高度恢复到主菜单目标，实际绘制继续遵守窗口约束。

参考 [Android Compose 默认无障碍行为](https://developer.android.com/develop/ui/compose/accessibility/api-defaults) 与 [语义](https://developer.android.com/develop/ui/compose/accessibility/semantics)。

## 回归证据

- 修改前，选中状态测试失败；同批文字布局测试通过，不能声称已通过该测试复现裁字问题。
- 修复后首轮 27 tests / 0 failures / 0 errors / 0 skipped。
- 高度判定反向验证：仅临时还原旧高度比较，同一两倍字号/260dp 矮视口测试失败，实际状态 PREPARING_CHILD_PAGE_MAPPING，期望 CHILD_PAGE_MAPPING；已恢复修复。
- 最终 PDF 相关回归 **179 / 0 / 0 / 0**；compileReleaseKotlin 与 lintVitalRelease 退出成功。lintVitalAnalyzeRelease 仍打印既有 ASM NegativeArraySizeException，不声称干净/完整 lint 通过。未执行 assemble，未生成本轮 APK。

证据 `.external_outbox/pdf-controls-polish/`：`before-tests.log`、`tests.log`、`first-pass-summary.json`、`before-short-viewport.log`、`final-verification.log`。风险：中（菜单布局和准备阶段判定）；验证采用 Compose 行为回归及相关状态/几何/材质检查。

## 限制

- device_verified：NOT_RUN；本会话单次 ADB 探测为空。需要实机验证菜单正常/大字号/横屏、展开收起、教材页码/目录/缩略图/导出、TalkBack 以及既有磨砂和阴影。
- Release 正式签名环境四项均未注入，本机已知签名凭据目录为空。不能生成可覆盖安装的新版 Release；未生成替代 Debug，也未将旧 Release 作为本轮交付。
