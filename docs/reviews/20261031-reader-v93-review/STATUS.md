# READY_WITH_LIMITATIONS

TASK: Reader 正式版 93 工作区审查（接续）
RISK: MEDIUM
CHANGES: 无源码改动；只新增本文件夹
APK: Release latest SHA-256 08f2b9e9…（未安装、未改设备数据）
COMMIT: 未提交

## 发现

### F1（HIGH）Markdown 编辑保存后批注可能错位
- DocumentRepository.kt:112-150 `saveDocumentEdits`：保留文档 id、只换 uri/hash，不处理批注行。
- MarkdownEditorScreen.kt:128-129：保存后提示“阅读记录及批注保留”。
- DocumentAnchor.kt:27-40 / AnnotationAnchorResolver.kt `MarkdownBlockAnchorResolver`：Markdown 锚点只有 blockIndex + 块内字符区间，没有原文片段兜底（EPUB 有 quote 兜底，Markdown 没有）。
- 结果：在已高亮段落之前插入/删除段落，旧 blockIndex 指向别的段落，高亮和笔记落到错误文字上。
- 验证：STATIC（调用链完整核对）；全仓测试中没有覆盖“编辑后批注重定位”的用例（saveDocumentEdits 只在 ContentIdentityImportWiringTest 测身份/哈希）。未做设备复现。

### F2（MEDIUM）独立 Markdown 的相对图片在受管副本下找不到
- ViewerScreen.kt:1226：`MarkdownBlockView(documentUri = state.document.uri)` 传的是书库受管副本路径。
- LocalImageLoader.kt:47 → MarkdownAssetResolver.fromDocumentUri：按该路径找相对图片。
- MarkdownAssetResolver.kt:101-106 `fromDocument()` 已经会对 MARKDOWN 优先用 sourceUri，但全仓没有调用者。
- 结果：`![](img/a.png)` 这类相对图片在 library/<hash>/<uuid>/ 下解析，原目录的图片不会被找到；编辑保存后（换新修订目录）同样。
- 限制：sourceUri 为 content:// 时，即使改用 fromDocument 也只得到 SingleContentDocument，仍无法解析相对路径，修复需另定方案。
- 验证：STATIC。未做设备复现。

### F3 章节导出失败 / 自动目录解析边界
- 前一代理只提到，未给出具体位置；本轮未完成复核，列为待查。

## 测试
- 前一代理：QA 1882、Release 1719 单测，0 失败，各 2 项环境跳过（未在本轮重跑）。
- 编辑器单测：node --test tests/*.test.mjs，9/9 通过，exit 0（evidence/editor-node-test.log）。
- 编辑器浏览器测试：tests/browser.mjs，用本机 Edge（READER_CHROME_EXECUTABLE），passed=true，exit 0；截图 evidence/editor-browser/*.png。之前失败是因 Playwright 自带浏览器未安装，不是代码问题。

implemented: 无
automated_verified: 编辑器单测 + 浏览器测试
emulator_verified: 无
device_verified: 无
visual_verified: 编辑器浏览器截图（Edge，非设备）
pending: F1/F2 的隔离复现测试（需写进 app/src/test，超出本次只读范围）；F3 复核
