# EPUB 原书排版修复（2026-10-05）

## 输入与根因

用户提供《学习观》EPUB 和 Apple Books / Android 应用对照截图。原书由 Pandoc 生成，12 个 spine 文件、1008 个 TOC 条目；书名本身带 `.md`。书籍内容未纳入仓库，也未改写源文件。

- `ch002.xhtml` 的目录项用 `<br />` 分行；解析器将它变成空格。
- `ch003.xhtml` 的“01”“何以避害”“什么不能少”是连续 h2，并分别出现在导航中。旧实现把所有 TOC 目标当作强制新页，造成孤立标题页。
- 原书 p 样式 `text-indent: 0`，应用却统一施加两字缩进。原书只为 h1 指定换页，并要求标题不与后文分离。
- 对应章节没有 b/strong；应用正文原本就是 Normal。模拟器旧包正文未复现用户手机的明显加粗，不能归因于书籍或宣称已修复手机字体问题。

## 实现

- 显式换行作为独立 inline 标记通过空白归一化，普通源码换行仍折叠为空格；嵌套行内样式、重复 br 和块锚点保留。
- TOC 只负责定位目标所在页面。文件边界 / h1 负责章节分页与章节页脚范围；h2–h6 自然流排。
- 逆序线性计算连续标题与后文首行需要的高度；能在一页容纳时一起移动，超长标题链允许正常分页，不生成空页或丢字。
- 删除强制两字缩进。保持共同字号、行距、Normal 正文字重、既有固定顶栏避让和图片排版。
- 不增加完整 CSS 引擎；属于现有有限 EPUB 渲染范围修复。块编号规则不变，阅读锚点仍按原文坐标及归一化 quote 校验 / 恢复。
- versionCode 32 → 33，versionName 1.0.0，Room v15 不变。

## 验证

EPUB 定向测试：105 项，0 失败 / 错误 / 跳过。新增显式换行、连续标题留同行、超长标题链、Pandoc 结构实测换行与字重回归。原“所有嵌套 TOC 单独起页”测试按用户此次明确修正改为断言目标共享自然流排页面且仍可导航；没有删除目录定位检查。

完整 QA JVM 测试最终 **1700 项，1698 通过，2 项既有跳过，0 失败 / 错误**。首次完整运行只有发布检查硬编码 versionCode 32 与新版本 33 不符，更新精确断言后完整复跑通过。初次结果和失败日志保留，没有跳过或削弱验证。

Release Lint **0 errors / 149 warnings / 4 hints**。签名、原证书一致性、16 KiB 对齐、Manifest 包名 / 33 / 1.0.0 / minSdk 31 / targetSdk 35 和无 INTERNET 校验通过。

最终构建命令：根目录 `reader-build-local.ps1 -Release :app:testQaUnitTest :app:lintRelease :app:assembleRelease --offline --no-daemon -Dorg.gradle.jvmargs=-Xmx1536m`，退出码 0。本机证据：`output/epub-layout-tests.log`、`output/epub-layout-release.log`（首次失败）、`output/epub-layout-release-final.log`（最终成功）、`output/epub-layout/`。

旧正式包在 API 35 模拟器、相同字号 / 视口下原书 1094 页；目录被压成段落，第 13 页只有“01”。已保留 `before-directory`、`before-heading` 截图和 UI 树供对照。

新正式包在同一模拟器 / 原书 / 字号下 **464 页**。第 7 页目录逐项换行；第 10 页显示“第一部分”引言、“01”“何以避害”“什么不能少”和正文。目录中这些标题都指向同一包含目标的第 10 页。覆盖升级后原“01”锚点仍在该页，结束进程重开后可见正文完全相同。第 8 页第二部分目录也保留逐行内容与原顺序。

模拟器 `emulator-5554`、API 35、x86_64，`adb install -r` 成功并保留数据。`verification.json` 的 8 项检查通过，崩溃缓冲为空；未运行 connected 仪器套件。`after-directory`、`after-heading`、`after-upgrade`、`after-restart` 为原书实际 UI 树 / 截图。

## 产物与状态

- `.external_outbox/m1racle-reader-release-latest.apk`：**7,024,972 bytes**。
- SHA-256：`332ba04499f4fcab606db21963b377c9ebe0b8a0cc3bc8d688c1dc7b9407e114`。
- 快照：`m1racle-reader-release-v1.0.0-20261005-011231.apk`。
- 构建输出、Outbox、模拟器安装包、模拟器 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk` 摘要一致。
- 构建基于 `a57c324` 加本轮工作区修改；构建期间未修改生产源码，随后只同步发布测试断言 / 文档。修改快照 `output/epub-layout/source-changes.patch` 的 SHA-256 为 `eafc39635c8acdb84af4807633ec870ed6bfd860b7652328c4ae0337edda6f41`。
- implemented / automated_verified / emulator_verified；physical_device_pending。

真实手机未连接；不能将模拟器验证写成用户手机验证。完整出版方 CSS、用户手机字重观感仍有验证 / 支持边界。
