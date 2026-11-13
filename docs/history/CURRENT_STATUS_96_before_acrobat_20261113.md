# 当前状态

2026-11-13：当前开发与构建仓库为 `D:/03_M1racle Reader/reader-ui-release-20261025`。当前源码与正式交付均为 **1.0.0 / 96，Room17**。

正式包：[M1racle-Reader-1.0.0-96.apk](</D:/03_M1racle Reader/apk/M1racle-Reader-1.0.0-96.apk>)，**26.45 MiB**（27732545 bytes）。SHA-256：`C21099A31C65A4B08B8D96630ECF5D08D2C81C4C3E4E213AD13150D002DBB33A`。生产证书与 95 一致，代码与资源压缩开启，签名、APK ZIP 及全部 8 份原生库的 ELF 16 KiB 对齐通过。构建输出、Outbox、本机交付、模拟器 Download 和已安装 base.apk 的摘要一致。包名 `com.m1racle.reader`，minSdk 31 / targetSdk 35，无 INTERNET；保留 `profileable android:shell=true`。

已处理 A1、A2、B1–B23 审查清单，详见[审查修复报告](AUDIT_FIXES_20261113.md)。包括 Markdown 快照时序、共享块映射及标注/书签/搜索事务重定位、图片路径解码、PDF 手柄与文本就绪手势、EPUB 延迟绘制、脚本门禁、文档导航及资源核查。95 版选字样式、外移手柄、44dp 触控与放大镜继续保留。

本轮为 **READY_WITH_LIMITATIONS**：implemented 完成；automated_verified 为 QA **1483** / Release **1304** 项单测，零失败、零错误，各 1 项 Windows 不支持符号链接的既有环境跳过。core/database 48、core/pdf 465、独立 pdf-tool 51 项均通过；core/model 无 JVM 测试源。PowerShell 11、Python 8、定向宿主 66 项通过，编辑器 28 个打包文件契约通过。全部模块 lint 零错误；Reader 为 98 warnings、6 hints，core/database 4、core/pdf 8、pdf-tool 16 warnings。没有关闭 lint 或降低断言来消除失败。

测试与正式包绑定同一份工作树编译输入：`output/audit-fixes-20261113/candidate-3-sha256.json`，共 762 项（core 141 项），复核零新增、零缺失、零变化。测试结果保存在同目录 `verification-summary.json` 与 `unit-results/`。活动对象通过引用结果文件读取测试证据，不再维护另一份易漂移的数量字符串。

本轮 device_verified 仅限 **API35 软件图形模拟器**：正式包保留数据升级、冷启动、首页、既有三页 PDF 渲染、Markdown 阅读、进入 WebView 编辑器和未改正文的完成返回均通过，崩溃缓冲为空。检查使用 540×1200 / density210，结束后已恢复默认显示参数并关闭本轮启动的模拟器。**物理手机未连接**：手机 Download 传输、安装及人工验收仍 pending。PDF 单字、整句、反向/跨行/放大拖动、复制准确性、IME、滚动手感及大文档性能仍需实机确认；宿主重组采样不等于真机帧率验收。放大镜继续复用当前页位图，低清档位的清晰度仍待实机确认。

现有 WIP 与两个旧工作树保留，未暂存、提交或推送。95 状态见[归档](history/CURRENT_STATUS_95_before_audit_20261113.md)与[95 选字报告](PDF_SELECTION_INTERACTION_20261113.md)。
