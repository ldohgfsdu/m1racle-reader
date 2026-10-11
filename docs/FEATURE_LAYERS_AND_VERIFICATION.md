# 功能分层与验证入口

当前交付版本、摘要和真机结论只看[CURRENT_STATUS.md](CURRENT_STATUS.md)；本轮各项证据看[FULL_BACKLOG_20261009.md](FULL_BACKLOG_20261009.md)。本表说明职责，不代表所有新功能已经验收。

| 层级 | 职责与唯一状态 | 实际入口 |
|---|---|---|
| 文件导入与身份 | 校验内容、建立受管副本、按内容摘要去重、保留原位置 | `data/repository/DocumentRepository.kt`、`share/ExternalDocumentImporter.kt` |
| 持久化 | 文档、位置、书签、标注和设置；历史数据通过必要迁移保留 | `data/local/AppDatabase.kt`、`data/settings/ReaderSettingsRepository.kt` |
| 定位 | 规范化文档锚点；书签额外保存视口、缩放、段内偏移 | `data/anchor/`、`pdf/PdfViewportSessionState.kt` |
| Markdown | 解析块与行内文本；列表负责虚拟化，正文和搜索消费同一文本投影 | `markdown/`、`ui/viewer/ViewerScreen.kt`、`ui/viewer/components/` |
| PDF | 原生页面/图块渲染、连续视口、分页和选字坐标 | `pdf/`、`ui/pdf/PdfViewerViewModel.kt`、`ui/pdf/PdfViewerScreen.kt` |
| EPUB | 单一原书文本、文字锚点、排版；滑动/连续/卷页消费同一分页结果 | `epub/`、`ui/epub/EpubTextLayout.kt`、`ui/epub/EpubReaderContent.kt` |
| 批注 | 选区→草稿→确认→仓库；高亮样式、笔记和管理列表不另建阅读位置 | `ui/annotation/`、`ui/reader/ReaderHighlightController.kt`、`data/repository/ReadingRepository.kt` |
| 玻璃与动效 | 背景只录制正文，控件在同级采样；按压与回弹使用统一材质参数 | `ui/material/`、`ui/hub/`、`ui/viewer/ReaderGradient.kt` |
| 文件工具与输出 | 创建、合并、加密、提取；显式选择新文件并回读核验，原文件不被处理工具覆盖 | `documents/DocumentTools.kt`、`ui/documents/DocumentToolsScreen.kt`、`ui/home/DocumentOperations.kt` |
| 系统长截图 | 复用 Compose/Android ScrollCapture；自定义 PDF 视口暴露同一滚动状态的系统语义，捕获期间不保存临时位置；没有应用内截图工具 | `ui/viewer/ViewerScreen.kt`、`ui/pdf/ContinuousDocumentViewport.kt`、`ui/epub/EpubReaderContent.kt` |
| Markdown 编辑 | 按用户最新决定等待 M1racle Note；保存研发代码，当前无导航入口 | `ui/documents/MarkdownEditorScreen.kt`、`documents/MarkdownDraftStore.kt` |

所有路径均相对于 `app/src/main/java/com/m1racle/reader/`。正式产品保持离线，无网络权限；所有文件访问由用户在系统设置主动授予。扫描不进入其他应用私有目录。

## 开发与发布

Windows 本机统一入口 `scripts/verify-reader.ps1`，复用仓库外已配置的运行环境和正式签名注入，不复制签名材料。

| 模式 | 使用时机 | 检查 |
|---|---|---|
| `-Mode Reading` | 阅读定位、标注、顶栏和进度的小改动 | 对应快速回归；不能替代发布门禁 |
| `-Mode Full` | 跨层变更、迁移或真机测试准备 | 全量 QA 单测、Release Lint、隔离 QA 及设备测试包 |
| `-Mode Release` | 全部实现已冻结、真机场景已通过 | 全量 QA 单测、Release Lint、正式签名/混淆构建；成功后自动导出并向唯一在线设备 Download 交付 |

脚本将输出保存到工作区 `output/verification/`，遇到错误立即失败。单进程/单工作线程控制内存，同入口文件锁避免重复启动；编译期间冻结参与编译的源码。增量检查通过后，只有新变更或新问题才重复相应检查。

设备检查必须先确认 `adb devices` 状态，并核对 QA 包名 `com.m1racle.reader.qa`。生产包只允许 `install -r`，不卸载、不清库。仪器测试结果不能借用上一次 APK；迁移及所有新增 UI 必须分别记录实现、自动验证和真机验证。发布前仍需核对正式证书、16KiB 对齐、Manifest、版本、实际体积和构建/Outbox/设备三份摘要。

具体操作遵循[设备协议](DEVICE_INSTALL_AND_ADB_TEST.md)与[发布规则](APK_DELIVERY.md)。
