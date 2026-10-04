# 分段控件按压拖动 — 2026-10-03

用户指出前一版没有按压拖动。根因：PillSegmentedControl 只监听每项的 selectable PressInteraction，预览停在按下项，没有连续指针坐标处理。此前膨胀与点击效果存在，但不能视为拖动已实现。

在共用控件 Row 接入标准 Compose 手势检测。正常宽度 detectHorizontalDragGestures；超宽轨道为避免抢走滚动，采用 detectDragGesturesAfterLongPress。用实际横坐标生成连续逻辑位置，支持 RTL；拖动时持续保持膨胀 / 高光，松手取最近目标并回调，取消清除预览。领域 selectedIndex 仍为唯一选中状态，拖动预览不提前写入过滤或设置状态。现有点击与无障碍 selectable 保留。

范围：PillSegmentedControl.kt、PillSegmentedControlTest.kt、design.md、CHANGELOG.md、候选版本说明、当前状态及本报告；共用组件覆盖文库与阅读字号 / 行高 / 边距。未更改底栏或原设置 / 筛选口径，保留其他用户未提交修改。

验证：

- :app:compileReleaseKotlin :app:testQaUnitTest --tests '*PillSegmentedControlTest' --tests '*HomeLayoutContractTest' --tests '*HomeViewModelTest' :app:assembleQa：退出码 0。
- 添加超宽滚动 / 长按拖动用例后，重跑同范围 :app:testQaUnitTest：退出码 0，30 项 / 0 失败 / 错误 / 跳过。控件 8 项：跨项松手目标、跨项取消、RTL、超宽普通滑动和长按拖动，以及此前点击、尺寸、按压取消和大字号末项可达。
- QA 隔离包 com.m1racle.reader.qa 安装 emulator-5554 成功。实际 4 秒从全部拖至 EPUB：中途透镜处于选项间的连续位置且文档列表保持原过滤；松手 UI 树明确 checked EPUB，列表仅含 EPUB。反向从 EPUB 拖回全部的 6 秒录屏已保存。不是实机验收。
- :app:validatePublicReleaseConfiguration：退出码 1，缺少正式签名注入。未生成 / 交付正式 Release，未接触私钥。
- git diff --check：退出码 0。完整套件、真机、TalkBack、性能专项未执行。版本仍为未公开候选 1.0.0 / 23。

unmanaged；本机项目契约及中央工程公约不可用。受监管 runId / projectId / taskId / machineId 皆 unknown。HEAD a314247d9cc27f7b3adc1edb3074b65eee3afbfe，分支 codex/fix-project-audit。外部证据在用户目录 .devhub/runs/unmanaged-reader-segment-drag-20261003/：日志、前 / 中 / 后截图、UI 树、drag-demo.mp4、Git 快照指纹、产物摘要与 handoff。

剩余：注入原正式签名后构建并交付 Release；真机验收。旧正式包不含本轮修改。