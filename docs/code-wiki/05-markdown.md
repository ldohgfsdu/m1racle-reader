# 05 Markdown 阅读引擎

[返回目录](README.md)

## 读取与解析

Markdown 采用项目自己的块模型和 Compose 渲染组件，没有 WebView 或第三方完整 Markdown 引擎。

```text
ViewerViewModel.load
  → DocumentRepository.readDocumentText（大小/可选内容哈希验证）
  → MarkdownParser.parse
  → ParseResult：blocks / toc / anchorRegistry 等
  → ViewerUiState.Success
  → ViewerScreen 的块列表
  → MarkdownBlockView 按类型分发
```

[MarkdownParser.parse(source)](../../app/src/main/java/com/m1racle/reader/markdown/MarkdownParser.kt) 处理标题、段落、引用、围栏代码块、列表、pipe table、独立图片与分隔线，构建目录和锚点注册表。`stripHtmlComments()` 清理 HTML 注释；空 HTML 锚点与标题 slug 纳入页内跳转。它不提供完整 CommonMark/GFM/HTML/CSS 兼容保证。

## 关键类型与函数

| 源文件/符号 | 职责、输入输出与注意点 |
|---|---|
| [MarkdownBlock](../../app/src/main/java/com/m1racle/reader/markdown/MarkdownBlock.kt) | sealed 模型：Heading、Paragraph、Quote、CodeBlock、ListBlock、Table、Image、Divider |
| `blockContentType()` | 为 LazyColumn 复用提供类型标签 |
| `blockKey(documentId, index)` | 把文档 ID 纳入列表键，防止切换文档后错误复用旧块状态 |
| [InlineMarkdownParser](../../app/src/main/java/com/m1racle/reader/markdown/InlineMarkdownParser.kt) | 行内加粗、斜体、代码、链接文本等；由 InlineMarkdownText 消费 |
| [AnchorRegistry.resolve()](../../app/src/main/java/com/m1racle/reader/markdown/AnchorRegistry.kt) | 根据 anchor ID 查找解析块目标，处理重复/显式锚点 |
| [MarkdownLinkRouter.classify()](../../app/src/main/java/com/m1racle/reader/markdown/MarkdownLinkRouter.kt) | 分出页内 fragment 与外部 URI；页内 fragment 由阅读器导航 |
| [DocumentListTitle](../../app/src/main/java/com/m1racle/reader/markdown/DocumentListTitle.kt) | 列表中显示标题的规则，与原始文件名区分 |
| [CodeHighlighter](../../app/src/main/java/com/m1racle/reader/highlight/CodeHighlighter.kt) | 轻量语法着色，配合 CodeLanguage/HighlightTheme；不是编译器语义分析 |
| [MarkdownSource](../../app/src/main/java/com/m1racle/reader/markdown/MarkdownSource.kt) | 原文替换等编辑辅助；存在此工具不表示编辑页面已接入 |

## UI 渲染

[ViewerScreen](../../app/src/main/java/com/m1racle/reader/ui/viewer/ViewerScreen.kt) 负责块列表、阅读工具栏、搜索、目录、排版设置、选择与批注入口。`ui/viewer/components` 中的 `MarkdownBlockView` 按类型委派给 Paragraph/Heading/Quote/List/Code/Table/Image 组件。

代码块支持复制、语言标签和横向滚动。表格由 `MarkdownTableLayout` 与 `TableLayoutCache` 协助计算和复用布局，支持列对齐和横向滚动。图片采用项目本地图加载器，不产生网络请求。

正文选择通过 `MarkdownSelectionContext`/`MarkdownSelectedText` 等将显示字符范围转为 `DocumentAnchor.MarkdownBlock`，再交给 `ReaderHighlightController`/`ReaderNoteController`。搜索高亮与持久批注是两条状态来源，不应互相覆盖。

## 两种搜索必须区分

文内搜索位于 [DocumentSearch](../../app/src/main/java/com/m1racle/reader/search/DocumentSearch.kt)，输入是已解析块与 query；`MarkdownSearchTextExtractor` 提取可见文本段，返回携带 documentKey、blockId、blockIndex 和字符范围的 `SearchMatch`。

- 忽略大小写的非重叠子串匹配，保留用户输入前后空格。
- 结果字符范围是**原始文本的 UTF-16 偏移**；大小写折叠产生扩展字符时建立回映射。
- 不支持跨块拼接匹配；最大 2,000 个命中，通过 `truncated` 告知截断。
- `ViewerViewModel.onSearchQueryChange()` 防抖 200 ms，取消旧任务并检查 documentKey/query/generation，拒绝迟到结果。
- `goToNextMatch()` / `goToPreviousMatch()` 更新当前命中，由 UI 跳转和绘制。

跨文档搜索则是 Room 派生表的标题/摘录/笔记查询，详见 [数据篇](03-data-and-anchors.md)，它不扫描全部 Markdown 正文。

## 本地图片与路径

[MarkdownAssetResolver](../../app/src/main/java/com/m1racle/reader/markdown/MarkdownAssetResolver.kt) 根据文档来源建立 `MarkdownDocumentBase`，区分 AppOwnedDirectory、FileDirectory、SafTreeDirectory、SingleContentDocument 和 Unknown；解析返回 Absolute/Resolved/Inaccessible/Invalid，而非随意拼接路径。

`normalizeRelativeSegments()`、`percentDecodeOnce()` 和 canonical containment 检查负责限制编码路径与目录穿越。单个 SAF 文档授权不能推导出同目录任意 sibling 文件授权；没有目录能力时返回不可访问结果。应用私有包内相对图可在包根约束下解析。

[LocalImageLoader](../../app/src/main/java/com/m1racle/reader/image/LocalImageLoader.kt) 支持内置 `asset://`、已授权 `content://` 和本地 `file://` 路径，并以最大边 1,600 px 下采样。HTTP/HTTPS 返回网络图片禁用状态。当前独立图片块支持不等于支持任意行内 HTML 图片。

## 阅读位置与性能

`ViewerScreen` 观察稳定滚动位置并交给 `ViewerViewModel.saveReadingPosition()`；`ReadingPositionSaver<ScrollPosition>` 串行、合并为最新版本保存，最终退出/销毁时补写。该 Saver 自身没有定时防抖，调用层负责控制高频输入。

解析后的块在阅读会话内保持顺序稳定。`MarkdownPerformance`、`ReaderPerformanceExperiments`、表格缓存和块 contentType 用于性能观测与降低重复工作。性能实验开关只在 QA 源集/变体启用。

回归入口包括 MarkdownParser/AnchorRegistry/AssetResolver、DocumentSearchUnicodeOffset/Isolation、表格布局和标注创建测试。完整类名见 [测试索引](reference/test-index.md)。
