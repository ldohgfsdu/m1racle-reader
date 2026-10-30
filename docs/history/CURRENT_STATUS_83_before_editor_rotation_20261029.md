# 当前状态

2026-10-25：正式签名 Release **1.0.0 / 83，Room17**。当前工作树为 `D:\03_M1racle Reader\reader-ui-release-20261025`；本机构建、模拟调试入口统一从 `reader-env-local.ps1` 读取该路径。原 `reader` 和 82 版工作树保留，不作为当前构建入口。

83 在完整 82 版基线上合入界面整理：Radix 黑白语义色、简化文案与层级、实际 0.5px 边框、减少转场。启动图标及设置页纯 M 母标沿用 82 版，APK 内两份向量与 82 版逐字节一致；设置页下方只显示一次 Reader。当前页 Markdown 编辑和文档处理代码保留。详见 [83 交付报告](UI_RELEASE_20261025.md)。

交付文件 `D:\03_M1racle Reader\apk\M1racle-Reader-1.0.0-83.apk`，27,603,994 字节（26.33 MiB），SHA-256 `7C8F6541A85C192C8BBE0593D51E6FF88ECD607DDE1CE1FB6C90C9BD8817BF27`。本次构建输出、Outbox 与交付文件摘要一致；正式生产证书、16KiB 对齐通过，无 INTERNET 权限。Release 保留 `profileable android:shell=true`，用于 ADB 性能采样。

同一份冻结源码的完整 QA 1836 项、Release 1683 项，均 0 failures / 0 errors，各 2 项既有环境跳过；lintRelease 0 errors、179 warnings、6 hints。六张原生 Canvas 截图来自 QA JVM 的实色材质回退，不能替代设备上的玻璃、动态与手势验收。

本轮 ADB 无在线设备。未安装至设备、未复制至设备 Download，模拟器和物理手机均未进行本轮验收；82 版历史设备结论不延续为 83 版证据。设备验收 pending。未提交、未推送、未公开发布。

证据目录 `D:\03_M1racle Reader\output\apk-83-20261025`。82 状态保存在 [历史](history/CURRENT_STATUS_82_before_ui_release_20261025.md)。
