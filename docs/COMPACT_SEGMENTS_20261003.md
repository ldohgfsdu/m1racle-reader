# 紧凑分段控件与按压反馈 — 2026-10-03

用户反馈初版过大，并指定 RiseDiary 参考。已阅读仓库主页及本机参考源码 LiquidGlassNavigation.kt（参考 HEAD 95d3f362a3fa8d95d8971b0f3c0f4081c95443f1）。CompactRangeSwitcher 使用 38dp / 3dp / 12sp；设置分段控件使用 40dp / 3dp / 12sp。参考：https://github.com/sky-shunfengjun/RiseDiary/blob/master/app/src/main/java/com/risediary/app/ui/components/LiquidGlassNavigation.kt 。

本项目原版 56dp 可见高度和整行等宽导致用户认为过大。现在默认视觉高度 38dp，文字 13sp、滑块内缩 3dp，宽度按最长标签测量、不再拉满整行，保留至少 48dp 透明点击区域。按住未选项时滑块预览移动并膨胀、叠加渐变高光、文字轻微放大；松手才提交，取消还原。Spring 分别驱动滑动与按压回弹；沿用标准 selectable 和 MutableInteractionSource，未新增手势消费路径或依赖。此处为表面与形变效果，不声称复刻 RiseDiary 的实时背景折射。大字号按行高扩展并可横向滚动；标签单行且测量宽度含舍入余量。

改动范围：PillSegmentedControl.kt、PillSegmentedControlTest.kt，以及 design.md、CHANGELOG.md、候选版本说明、当前状态与本报告。文库、字号 / 行高 / 边距所有调用自动应用；没有更改原过滤、设置值或底栏实现，保留用户其他未提交改动。

验证：

- :app:compileReleaseKotlin :app:testQaUnitTest --tests '*PillSegmentedControlTest' --tests '*HomeLayoutContractTest' --tests '*HomeViewModelTest' :app:assembleQa：退出码 0。26 项 / 0 失败 / 错误 / 跳过。涵盖尺寸收紧但保持触摸区域、按压不提前提交、取消、互斥选择、大字号末项可达及原文库 / 页面契约。
- 初次新增尺寸测试失败：Robolectric 字形度量将高度推至 51px。修复根因为使用主题行高计算轨道高度，保持 38dp 默认比例；模拟器发现 Markdown 末字换行，补充单行限制与舍入余量，之后上述测试及构建通过。未削弱尺寸断言。
- QA 隔离包 com.m1racle.reader.qa 安装 emulator-5554 成功。浅色文库、紧凑阅读设置、按住 PDF 的透镜预览、松手 PDF 筛选空态、深色 200% 字号检查通过；系统恢复浅色 / 100%。截图和 UI 树在外部证据目录。不是实机验收。
- :app:validatePublicReleaseConfiguration：退出码 1，缺少正式签名环境。未生成或交付正式 Release，未访问私钥。
- 未执行完整套件、真机、TalkBack 与性能专项。版本仍为未公开候选 1.0.0 / 23。

本机项目契约及中央工程公约不可用；unmanaged，受监管 runId / projectId / taskId / machineId 均 unknown。分支 codex/fix-project-audit，HEAD a314247d9cc27f7b3adc1edb3074b65eee3afbfe。命令、日志、机器时间、dirty fingerprint、QA 大小与 SHA-256、handoff 在用户目录外部账本 .devhub/runs/unmanaged-reader-compact-segments-20261003/。

剩余：安全注入原正式签名，构建并交付 Release；真机验收。此前旧 APK 不含当前修改。