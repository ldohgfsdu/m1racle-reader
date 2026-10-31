# 20261101-more-issues（只读审查，HEAD 9921c0a + 未提交 PDF WIP）

## 高
H1 编辑目录节点会把 sortOrder 重置为 0 → 章节导出范围错乱
- ui/pdf/PdfViewerViewModel.kt saveOutlineNode(sortOrder: Int = 0)，PdfViewerScreen.kt ~1368 onSave 未传 sortOrder；
  DocumentRepository.kt ~712 upsertOutlineNode 直接写入。新建手工节点也都是 0。
- pdf/export/ExportRangeResolver.kt siblingsOf 先按 sortOrder 排序；computeEndPage 取"下一同级起始页-1"，
  被改到前面的节点其"下一同级"可能在它前面 → coerceAtLeast(start) → 只导出 1 页，或范围错位。
- 与自动导入目录（sortOrder = 书签 order）混用时同样发生。
H2 手工父章节默认 end = start（单页），子节点导出被夹到父范围
- ReaderAdvancedCard.kt ~380 新建章节 start=end=当前页；PdfViewerScreen.kt ~1395 "使用当前页" 同理。
- ExportRangeResolver.plan 用父节点存储的 endPageIndex 夹紧：子节点 start > 父 end 时变成 start=end=父起始页，静默导出父章首页；
  用户在确认框手工改的范围也会被静默夹掉，且仍标 USER_OVERRIDE。
## 中
M1 saveOutlineNode 吞掉所有异常（// ignore），保存失败无提示。
M2 自动目录：无法解析的书签落到"上一书签页"（PdfEmbeddedOutlineParser.kt ~121-128），只计数不标记；
  乱序书签时 PdfEmbeddedOutlineModels.kt endFor 得到 end=start。导入后这些章节导出为错误/单页范围，无提示。
M3 MERGE 导入（PdfEmbeddedOutlineImporter.buildMerge）挂到已有父节点时不更新父 end；sortOrder 与手工节点同空间混排（加重 H1）。
M4 导出失败（非取消）不删除 SAF 目标文件：ChapterPackageExporter.kt / PdfAnnotatedExporter.kt 仅 CancellationException 分支调用
  deleteSafDocumentBestEffort；fail() 只清临时目录 → 用户目录留下 0 字节/半截 zip/pdf。
## 低
L1 ChapterPackageExporter 中 validateRange 结果 err 未使用（死代码，以为有校验）。
L2 PdfAnnotatedExporter 源文件复制 src.copyTo 不可取消，大文件取消要等复制完。
L3 MarkdownEditor export() 用 "wt" 模式，部分云盘 provider 不支持；失败同样遗留空文件。
L4 Markdown 保存成功后仍写入一份与正文相同的草稿（无害，打开时被忽略，但残留文件）。
## 已确认没问题
- 保存到原文件：PdfSourceWriteStore.prepare/replace 会校验 sha，选错文件不会覆盖；失败会恢复。
- 编辑器返回/放弃对话框、busy 期间禁止返回：逻辑正确。
## PDF WIP（未改）
- PdfViewerScreen.kt ±1074 行、PdfViewerViewModel.kt、PdfEmbeddedOutlineParser.kt 大改未提交；上次全量单测(含 WIP) 1886/0 失败，
  但没有真机验证。风险：大文件、混合提交困难；建议单独提交 + 真机回归目录/导出。
## 未覆盖
- 玻璃/设置 UI：只统计到 GlassMaterial/QaGlassVariant 等文件，未逐项复查视觉问题（需要真机截图）。