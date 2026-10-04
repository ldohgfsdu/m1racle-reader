# 分段控件统一与文库截断修复 — 2026-10-03

按用户提供的 [Apple Segmented controls](https://developer.apple.com/design/human-interface-guidelines/segmented-controls) 参考，统一横向单选组并修复红圈中的文库筛选条。最终正式签名侧载包 **1.0.0 / 29** 已复制到手机 Download 并覆盖安装。

## 根因与修改

原组件以最长标签加 26dp 为每项固定宽度，四项总宽度超过手机内容轨道；横向视口裁出了方形右端，EPUB 需要滚动才能看到。现在普通字号下等宽选项受页面可用宽度约束，标签水平内边距收至 4dp，轨道在视口层绘制并保留两端圆角。窄屏可换行，大字号仍可滚动，保持完整文字和至少 48dp 点击范围。按压预览、松手提交、取消恢复、RTL 和长按滚动轨道的选择手势保留。

文库、Markdown / EPUB 阅读设置的字号 / 行高 / 页边距共用组件；材质对比页的图案 / 文字单选由分散 Chip 改为同一组件。PDF 原有纵向菜单单选保持原有布局。

首个 28 包在手机 1.1 倍字号下仍把 Markdown 最后一个字母换行，真机截图发现后进一步收紧内边距并增加单行文字回归，最终 29 包四项均单行。保留 28 包截图用于比较。

## 验证

- fast：`gradlew.bat :app:testQaUnitTest` 加七个定向类过滤，**76 tests / 0 failures / 0 errors / 0 skipped**，退出 0。覆盖整组无水平溢出、272dp 窄屏、当前手机字号单行完整文字、两倍字号滚动、点击、拖动取消和 RTL；组件测试使用 Robolectric Native 字体测量。完整命令及 XML 位于外部 `verification.json` 与 `test-results/`。
- release：JDK 17；`gradlew.bat :app:assembleRelease --console=plain` 退出 0，包含 lintVital、R8 和资源压缩。签名、证书连续性、16 KiB 对齐、版本 29、Manifest 无 INTERNET / QA Activity 检查通过。
- emulator：隔离 QA 检查四项可见、EPUB 可选、材质背景分段布局。QA 仅作验证，没有交付用户。
- device：`db04f865` / 25098PN5AC，1220×2656、520 dpi、fontScale 1.1。真机截图 / XML 确认全部、PDF、Markdown、EPUB 均在内容轨道内且单行，EPUB 点击选中，右侧圆角完整；材质背景分段已显示。安装使用 `adb install -r`，继续阅读 4%、PDF 5 / 384 与 85 / 403 可见。验证后停留文库“全部”。
- full：本轮未重跑完整套件或 full lint。27 包完整基线仍在 [EPUB 报告](EPUB_READING_REPAIR_20261003.md)，不当作 29 包的全量验证。

期间其他两个 Reader 聊天并行修改源码与执行 Gradle，曾导致 QA 测试类输出缺失；获得用户明确消息授权后协调暂停，重新完成定向验证。保留其滑块 / EPUB 改动；这些独立修改的功能验收由对应聊天继续。本轮不将它们的未验证功能标成通过；滑块聊天提示其最终源码可能晚于 29 编译，故不能据构建后的源码摘要声称最终蓝色滑块已包含在 29。

## 产物与交接

主机 `.external_outbox/m1racle-reader-release-latest.apk`，来源本轮 `app/build/outputs/apk/release/app-release.apk`；**6,994,053 bytes**。

SHA-256：`5f8926e07b414f1c7b7888db2d5496fd3680a8e7ef3c43986a06aa3e9c1b5c7c`。

证书 SHA-256：`2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`。用户本轮明确允许沿用既有正式签名配置，临时材料只在仓库外使用并随构建清理。

交付手机 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk`，时间 **2026-10-03 17:04:55 +08:00**；手机 Download、实际安装 base.apk 与主机构建摘要一致。

unmanaged；runId / projectId / machineId unknown；Codex / GPT-6 / version unknown，Windows。契约和中央工程公约在本机不可用。分支 `codex/fix-project-audit`，HEAD `a314247d9cc27f7b3adc1edb3074b65eee3afbfe`，构建 dirty fingerprint `c2e2e3dd2ec0ff6701a4f435dee0af18b39c6ff7dac3fcd309181426fa6bd759`。保留既有修改，无 reset / stash / clean / commit / push。

外部证据及 handoff：`C:/Users/21234/.devhub/runs/unmanaged-reader-hig-segments-20261003/`。implemented / automated_verified / device_verified；人工视觉 / 手感、独立聊天功能验收与后续全量回归 pending，未公开发布 / tag。
