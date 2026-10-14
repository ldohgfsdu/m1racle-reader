# 12 端到端调用链

[返回目录](README.md)

本篇把跨目录流程串起来。详细接口职责分别见各子系统篇；精确符号/行号见 [源码索引](reference/source-index.md)。

## 从首页导入并打开

```text
GlassDock / Home 空状态的导入动作
  → NavGraph 的 ActivityResultContracts.OpenDocument
  → HomeViewModel.onImportDocument(uri)
  → DocumentRepository.importDocument(uri)
  → 元数据/DocumentType + ContentIdentityResolver.resolve
  → DocumentRepository.commitResolvedImport
  → 文件物化 + Room 事务去重/插入/别名/搜索
  → HomeEvent.OpenDocument
  → NavGraph.openDocument
  → viewer / pdf / epub 路由
```

重要检查点：文件选择取消不调用导入；重复导入保留 ID 与子资产；失败路径释放本次文件；Home 只消费 ImportResult，不重新推导哈希身份。

## 在现有阅读器上接收外部文件

```text
MainActivity.onNewIntent
  → clearEventHandledForNewDelivery
  → ExternalOpenCoordinator.onIncomingIntent
  → 必要时确认 current/incoming 文档信息
  → ExternalDocumentImporter prepare
  → ContentIdentityResolver 暂存
  → progressFlushHook（旧文档）
  → DocumentRepository.commitResolvedImport
  → pendingNavigation(requestId)
  → MainActivity.navigateReplaceViewer
  → ackNavigation(requestId)
```

prepare 和确认时保留原阅读器；提交成功后执行导航替换。编辑页有额外保护：同文档不会直接绕过编辑会话，其他文档可先导入并等待退出编辑后再导航。

## Markdown 搜索与跳转

```text
SearchTopBar 输入
  → ViewerViewModel.onSearchQueryChange
  → 200ms 防抖 + generation/query/documentKey 校验
  → Dispatchers.Default / DocumentSearch.search
  → MarkdownSearchTextExtractor → SearchMatch（原文 UTF-16 区间）
  → searchState
  → ViewerScreen 跳至目标 block、组件绘制当前/其他命中
```

关闭搜索、切换 query 或销毁 ViewModel 会取消/废弃旧结果。跨文档搜索则是 SearchScreen→SearchViewModel→SearchRepository→SearchIndexDao，不走此解析块扫描链路。

## 创建高亮或笔记

```text
Markdown 文本选择 / PDF 文字几何选择
  → 临时 SelectionModel
  → AnnotationCreationFlow / 操作菜单
  → DocumentAnchor.MarkdownBlock 或 PdfPage(rects)
  → ReaderHighlightController / ReaderNoteController
  → ReadingRepository.addHighlight / addNote
  → AnchorResolver.project
  → 事务写 highlights/notes + SearchRepository.index
  → 列表刷新与 AnnotationOverlay / PdfHighlightLayer
```

视口像素仅用于交互和绘制；保存前转换到格式稳定锚点。批注删除和搜索索引删除在同一业务事务执行。

## 捕获和打开书签

```text
阅读器稳定读点
  → ReaderBookmarkController
  → BookmarkTarget(anchor, scrollOffset, 可选 PDF viewport)
  → ReadingRepository.createBookmark
  → BookmarksScreen / BookmarksViewModel
  → Routes.bookmark(document, target)
  → Markdown/PDF 阅读器按 initialBookmark 恢复
```

BookmarkTarget 是创建时快照；日后继续阅读不会移动书签。EPUB 当前未完整接入上述消费链路，勿把底层支持的锚点类型当作已开放的全部 UI 能力。

## PDF 缩放、渲染和退出

```text
指针输入 → PdfGestureTransformController / ZoomPanPhysics
  → 变换更新与 ContinuousViewportSnapshot
  → PdfViewerViewModel.onContinuousViewportChanged / requestPredictiveTiles
  → 最新视口合并 / planner / TileWorkQueue
  → PdfPageRenderer.renderTile（IO 串行原生访问）
  → generation 验证 / 缓存 / overlay 发布
  → 稳定后 commitViewportSession
  → FinalPdfProgressFlusher
  → DocumentRepository.updatePdfProgressSnapshotAtomic
```

退出/替换时刷新最终稳定状态，关闭主/缩略图 renderer 并清理缓存。旧 document generation 的结果不得出现在新文档。

## PDF 正文编辑与保存

```text
PdfViewerScreen 编辑动作
  → PdfViewerViewModel.releaseForEditing
  → pdf-edit 路由 / PdfBodyEditorViewModel
  → inspect source → 用户 edits → writeCopy candidate → 预览
  → saveCurrent(sourceTarget?)
  → DocumentRepository.saveDocumentEdits(expectedHash, candidate)
  → 可选 PdfSourceWriteStore prepare/replace/校验
  → Room 提交读取版本/身份与源哈希
  → 清票据 / 返回 PDF 阅读器
```

失败时保留编辑和/或恢复备份；启动 `recoverPdfSourceWrites()` 根据日志与已提交状态收敛。另存副本走显式目标并验证输出，再按现有导入机制处理。

## PDF 章节导出

```text
目录节点/当前章节 → beginExportNode / beginExportCurrent
  → ExportRangeResolver → 用户确认范围与输出类型
  → CreateDocument 取得 outputUri
  → runExportToUri
  → ChapterPackageExporter 或 PdfAnnotatedExporter
  → 复制页 / 元数据与校验 / 输出流
  → Completed / Cancelled / Failed
```

ViewModel 的导出门禁处理运行中任务与取消重入；输出提交前的取消要清临时文件并尽力删除未完成目的文档。不要仅以 coroutine.cancel() 推断所有文件都已清理。

## EPUB 重排后的恢复

```text
EpubReaderViewModel 加载书与 readingAnchor
  → EpubReadingAnchors.restore → EpubRestoreSession
  → 字体/尺寸驱动 EpubTextLayout 与 paginateEpub
  → anchor 的源块/字符映射到新 page/slice
  → Slide / Continuous / Curl 显示
  → 稳定读点 → EpubPosition
  → restoreSession.anchorForSave 的降级保护
  → ReadingPositionSaver → updateEpubReadingPosition
```

恢复页码是布局派生结果，持久化的原文锚才是跨字号恢复依据。历史 locator 在打开时只做必要的一次转换。
