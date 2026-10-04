# 胶囊分段控件 — 2026-10-03

用户要求把图示的分散标签统一成连体胶囊分段控件。范围为文库全部 / PDF / Markdown / EPUB，以及阅读显示设置中的字号、行高、页面边距。沿用原状态和回调，共用 PillSegmentedControl；移除旧 FilterChip 渲染。内部选中滑块使用弹簧动画，主题表面颜色适配浅深色，单选可访问语义与最小 48dp 点击区域保留，大字号超宽时横向滚动。

涉及 app/src/main/java/com/m1racle/reader/ui/components/PillSegmentedControl.kt、ui/hub/LibraryScreen.kt、ui/viewer/components/ReaderSettingsSheet.kt；新增 ui/components/PillSegmentedControlTest.kt，并同步 design.md、CHANGELOG、候选版本说明及当前状态。未更改阅读设置数值或文库过滤规则。已有用户改动保持。

验证：

- :app:compileReleaseKotlin：退出码 0。
- :app:testQaUnitTest --tests '*PillSegmentedControlTest' --tests '*HomeViewModelTest' --tests '*HomeLayoutContractTest'：退出码 0，24 项，0 失败 / 错误 / 跳过。验证互斥切换、窄屏双倍字号末项可达，以及已有文库过滤 / 页面契约。
- :app:assembleQa：退出码 0；隔离测试包 com.m1racle.reader.qa 安装到 emulator-5554 成功。浅色文库与阅读设置截图、实际 Markdown 筛选 / 字号切换、深色 200% 字号设置检查通过；系统主题与字号恢复浅色 / 100%。不是实机验收。
- :app:assembleRelease：退出码 1。validatePublicReleaseConfiguration 缺少正式签名环境注入而拒绝；未读取私钥、未生成或交付正式 Release。
- 完整单测、TalkBack、RTL 实机显示、真机验收未执行。版本仍为未公开候选 1.0.0 / 23。

本机缺少 .devhub/project.json 和中央工程公约，为 unmanaged；受监管 runId / projectId / taskId / machineId 未提供，记 unknown。HEAD a314247d9cc27f7b3adc1edb3074b65eee3afbfe，分支 codex/fix-project-audit。日志、截图、产物摘要和可恢复 handoff 位于用户目录外部账本 .devhub/runs/unmanaged-reader-segments-20261003/，不在仓库保存动态记录。

剩余：安全注入原正式签名后重新构建 Release 并按交付规则处理；真机验收。现有旧 Release 不代表本轮修改。