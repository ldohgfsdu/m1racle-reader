# 当前状态

2026-11-19：当前开发与构建仓库为 `D:/03_M1racle Reader/reader-ui-release-20261025`，分支 `refactor/pdf-only`。当前源码与正式交付均为 **1.0.0 / 100，Room17**。

正式包：[M1racle-Reader-1.0.0-100.apk](</D:/03_M1racle Reader/apk/M1racle-Reader-1.0.0-100.apk>)，**6.39 MiB**（6,695,644 bytes）。SHA-256：`60FA31E02B5EAEBC8E0E1C7DB13134655C40FE2A506D0666E30B0A007A7273CD`。签名证书沿用正式身份（`2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`），`apksigner verify` 通过；`zipalign -c 4` 与 `zipalign -c -P 16 4` 均 Verification successful，8 份原生库 16 KiB 对齐。包名 `com.m1racle.reader`，minSdk31 / targetSdk35，无 INTERNET、非 debuggable。构建输出、Outbox 与本机交付三处摘要一致。

体积从 97 的 26.45 MiB 降到 6.39 MiB，原因是 97 包里还有那个 23.41 MiB 的 CJK 字体资产：97 有 1 个 `fonts/` 条目、98 为 0。这不是压缩率变化。

本轮内容：EPUB 收尾（阅读字体导入下线、EPUB 专属设置枚举下线、导入门禁的 EPUB 分类收敛到 PDF-only、Manifest/扫描/文案停用 EPUB）、PDF 长按选择阈值修正（arm 4dp / 抬指死区 6dp，修「稍微滑动就选中」）、连续视口鼠标滚轮。本轮另按外部审查修掉导入类型错配（`.pdf` 名 + `application/zip` MIME 曾被写成已退役的 MARKDOWN_PACKAGE）、提交锁取消时的暂存泄漏、发布脚本调用已删除任务、CI 未覆盖独立模块、清单仍公布退役格式；选择手势改用平台规则（touchSlop + long-press timeout + 被消费即取消），只保留两处真实偏差；详见 [100 交付报告](RELEASE_100_20261119.md)。

状态为 **READY_WITH_LIMITATIONS**：implemented 完成；automated_verified 为 app QA 996 / app Release 864 / core:database 58 / core:pdf 465 / pdf-tool 51，合计 **2434 用例，零失败、零跳过**；`:app:compileQaKotlin` 通过，本轮新增代码零新增编译警告（`PdfHighlightLayer` 仅剩既有的 `LocalClipboardManager` 弃用提示）。

lint 已跑：`:app:lintRelease` 0 error / 80 warning / 3 hint，本次新增代码零新增告警。**未执行**：Macrobenchmark / Baseline Profile 生成、设备安装与真机验收。`adb devices` 本轮为空，手机传输、安装、选字手感、滚轮与滚动性能均为 pending；模拟器与宿主结果不替代真机结论。

已知边界：横向分页模式（`HORIZONTAL_PAGED`）不响应鼠标滚轮，仅连续滚动模式支持。96 / 97 的历史证据见 `history/`。
