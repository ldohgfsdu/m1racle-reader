# Markdown 文件头 BOM 阅读解析修复

2026-10-30，在当前 87 工作树继续处理 85 报告留下的首行标题解析问题。

## 根因与修复

ViewerViewModel 将仓库读出的原文传入 MarkdownParser.parse；解析器此前没有移除文档开头的 U+FEFF，导致首行标题、代码围栏不能按块语法识别。现在仅在阅读解析副本中移除一个文件头 BOM，再执行换行规范化和注释处理。编辑、草稿、保存路径没有改动；正文及代码中的 U+FEFF 保留，原始行号映射不变。

新增 MarkdownBomTest 三项行为回归，覆盖标题、目录、锚点、块标识、混合换行位置、首行代码围栏内注释保留、正文 U+FEFF 和仅含 BOM 的空文档。修复前 3/3 失败，修复后全部通过。

## 修复阶段证据（打包前历史）

- Release JVM 定向测试：Markdown 包及 MarkdownEditorDraftRecoveryTest，共 126 项，0 failures / 0 errors，2 skipped。未运行全量测试或 lint。
- 编辑器 contract / source 测试：7/7，通过 BOM 与混合换行保真检查。
- 编辑器完整测试尝试中，history.test.mjs 因本机缺少 @codemirror/state 未能启动；npm 不在当前 PATH，未将完整编辑器回归称为通过。
- XML 结果及本轮两个源码文件摘要：D:/03_M1racle Reader/output/markdown-bom-20261030。
- adb 当前仅 emulator-5554 在线，未连接物理手机。本轮未构建新 APK、未安装、未验证修复后的原生阅读界面；现有 87 APK 不包含本次修复。

状态：implemented / automated_verified（定向），原生界面及新正式包验证 pending。现有六项 PDF 扩展原生失败未在本轮处理。未提交、未推送。


## 88 交付更新

本修复已包含于正式签名、压缩 Release 88。最终同源完整 QA 1847 项、Release 1690 项均零失败，各两项既有环境跳过；lint 零错误。上面的未打包状态仅描述修复当时阶段。原生 BOM 阅读补充检查仍未完成。交付与限制见 [88 报告](PDF_RENDER_METADATA_20261030.md)。
