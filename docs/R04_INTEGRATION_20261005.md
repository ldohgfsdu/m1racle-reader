# EPUB R04 合入与正式包验收

日期：2026-10-05（Asia/Shanghai）。用户授权接手验证、修复、合入和正式包交付。

## 结果

R04 已快进合入主工作区 `reader` 的 `feature/epub-anchor`。正式包对应源码提交 `0fe7064a0c624779a28fd95e7097caa8d388dd67`，版本 **1.0.0 / 32**，Room **v15**。未推送、未创建发布或 tag。`reader-r04` 保留为已合入的工作区，不再是未合入任务。

- implemented：同章完整文本 / 前缀匹配、降级恢复原锚点保护、主动导航解锁、每次打开一次的降级提示。
- automated_verified：生产代码、QA 仪器测试源码编译、QA JVM 测试和 Release Lint。
- device_verified：API 35 x86_64 模拟器上的正式包覆盖升级及下述 13 项检查。
- pending：真实手机验收；其他设备、长书性能、深色与旋转专项；降级提示的设备视觉验收。

## 本轮修复与测试

1. R04 测试错误导入 `androidx.compose.ui.test.assertDoesNotExist` 导致无法编译。删除该错误导入，保留原来的成员断言。
2. `ReaderChromeTokensContractTest` 仍把 Dock 高光写作 0.60，与已批准的 `design.md` 底栏外观裁决冲突。仅把 Dock 浅 / 深预期明确改为 0.24 / 0.28，原容差 0.004 不变，其他角色断言不变；没有修改生产玻璃效果来迎合旧测试。
3. 新增 `EpubAnchorPersistenceTest`，使用真实 Room SQLite，通过仓库保存链验证 Quote / QuotePrefix / ChapterStart / Progress 四种恢复后，连续自动保存更新位置但保留数据库中的原始锚点；用户主动导航后才更新锚点。

首次 `:app:testQaUnitTest :app:compileQaAndroidTestKotlin :app:lintRelease` 因上述测试导入错误失败。修正后完整 QA 单测 **1696 项，1 失败，0 errors，2 跳过**；唯一失败为本轮新增测试的 `@Before` 返回了 insertDocument 的 Long，已显式返回 Unit。该用例随后定向重跑 **1 项通过**。

因此最终去重验证覆盖为 **1696 项，1694 通过，2 项既有跳过，0 个未解决失败**；这是完整运行加修复项复测的汇总，不能表述为一次完整运行全绿。原 R04 交互用例、玻璃配方断言均在完整运行中通过。测试结果分别保留，不覆盖初次失败证据。

Release Lint：**0 errors / 144 warnings / 4 hints**。`compileQaAndroidTestKotlin` 通过；本轮未运行 connected 仪器套件，不把编译通过写成仪器测试运行通过。

日志：工作区 `output/r04-verification.log`、`r04-verification-retry.log`、`r04-persistence-verification.log`、`r04-release.log`。测试 XML 和汇总：`output/r04-integration/full-unit-results/`、`persistence-unit-results/`、`unit-summary.json`。

## 正式产物

- 唯一交付入口：`.external_outbox/m1racle-reader-release-latest.apk`。
- 快照：`m1racle-reader-release-v1.0.0-20261005-001455.apk`。
- 大小：**7,024,976 bytes**。
- SHA-256：`4aae1477cee6902e13ed0a89ee45a11b2f9f542f73290f3ee0be8ac4d78097c9`。
- 正式证书 SHA-256：`2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`。
- `assembleRelease` 成功，apksigner v2 验证及 zipalign 16 KiB 对齐通过，无 INTERNET 权限。
- 构建输出、Outbox、模拟器实际 base.apk、模拟器 Download 摘要一致。
- `adb install -r` 覆盖安装，无卸载或清除正式应用数据。设备 Download：`/storage/emulated/0/Download/m1racle-reader-release-latest.apk`。
- 没有交付 Debug / QA；没有向真实手机交付。

## 正式包模拟器验收

`emulator-5554` / Android 15 / API 35 / x86_64。使用 UI XML 定位操作，逐步保存截图，首页、EPUB 和 PDF 已视觉检查。

| 检查 | 结果 |
| --- | --- |
| 测试书覆盖升级 | 第 2 / 18 页及完整可见正文与升级前相同 |
| 既有 EPUB 数据保留 | 既有测试书第 2 / 3 页及正文与升级前相同 |
| 大字号进程重启 | 第 3 / 26 页，正文完全一致 |
| 字号真正重排 | 大字号 26 页，小字号 18 页 |
| 小字号保留原文本位置 | 原大字号页首唯一标记 `C1P05W19` 仍在恢复页内 |
| 小字号进程重启 | 第 2 / 18 页与重排后的正文一致 |
| 目录跳转 | 第二章标题位于第 10 / 18 页页首 |
| 目录跳转后重启 | 正文及第 10 / 18 页一致 |
| Markdown | 既有测试文档正文正确 |
| PDF | 页面可见，原第 2 / 3 页进度保留 |
| Release 工作台开关 | 设置没有“液态玻璃工作台”入口；既有材质对比入口保留 |
| 产物一致性 | 四处 APK SHA-256 一致 |
| 崩溃 | 本轮 crash buffer 为空 |

机器断言见工作区 `output/r04-integration/verification.json`，可用同目录 `verify.py` 复核；该目录保存 XML、截图、签名 / 对齐结果、日志和唯一文字标记 EPUB 夹具。快照工具遇到 UI 切换的空根节点时停止，等待稳定后重新抓取；没有把旧 XML 当成新证据。

测试后字号恢复原来的“小”，行高与边距保持原值，模拟器停在正式包首页。新增 `R04 Anchor Check` 测试书保留便于复验。未为触发降级恢复而篡改正式应用数据库；降级保护和提示目前由 JVM 逻辑 / 交互测试验证，不宣称设备上覆盖了这些故障注入场景。

## 后续边界

真实手机未连接，仍需正式包覆盖升级、实际书阅读位置 / 字号 / 目录恢复及观感验收。当前未做公开发布决策；历史 tag 不代表本轮正式包已发布。
