# R9 Markdown 文内搜索 V1 — 设计审计报告

**Active object:** `R9_MARKDOWN_IN_DOCUMENT_SEARCH_V1`  
**Phase:** design_audit only（**本轮不实现搜索代码**）  
**Baseline:** `9d8a32a`  
**Closed prior:** `R8_MARKDOWN_RELATIVE_ASSET_RESOLUTION_V1` → `closed_device_accepted`  
**Date:** 2026-07-16  

---

## 0. 状态对齐

| 对象 | 状态 |
|------|------|
| R8 相对资源 V1 | **closed / device_accepted**（checkpoint `9d8a32a`） |
| R9 文内搜索 V1 | **design_audit / design_in_progress** |
| External Open | device_accepted；生命周期 **do_not_change** |
| H1.5A / ThreePageStrip / Tile | **frozen** |
| 本轮 git | 仅 `.memory/*` + 本设计报告（无功能代码、无 commit 要求） |

---

## 1. 执行摘要

仓库中 **已存在可用的文内搜索骨架**，不是从零设计：

| 能力 | 现状 |
|------|------|
| 当前文档搜索 | `DocumentSearch.search(blocks, query)` |
| 输入框 / 计数 / 上一个下一个 | `SearchTopBar` + `DocumentSearchUiState.counterLabel` |
| 打开/关闭搜索 | `ViewerViewModel.openSearch/closeSearch` |
| 返回优先关搜索 | `BackHandler(enabled = searchState.isActive)` |
| 文档切换清理 | `DisposableEffect` onDispose → `closeSearch()`；ViewModel 按 `documentId` 创建 |
| 滚动到匹配块 | `scrollToBlockIndex` → `animateScrollToItem` / `scrollToItem` |
| 当前匹配高亮 | 块级 `searchBlockHighlight`；标题另有 `highlightPlainText` 字级 |

**V1 产品目标大多已可落地在现有路径上。** 推荐策略：

> **在现有 `DocumentSearch` + ViewModel + LazyColumn 上做「可见文本对齐 + 高亮/滚动硬化」**，  
> **禁止** 复制第二套 Markdown 渲染器、禁止 Room FTS、禁止全库索引。

本报告给出：生产调用链、数据源方案选择、映射/高亮/滚动/性能/交互、与现状差距清单、建议实现阶段。

---

## 2. 生产调用链（证据）

### 2.1 端到端

```
DocumentRepository.readDocumentText(uri)
  → ViewerViewModel.load()  [Dispatchers.Default]
      → MarkdownParser.parse(source) : ParseResult
          blocks: List<MarkdownBlock>
          toc / anchorRegistry / blockIds
  → ViewerUiState.Success(document, blocks, …)
  → ViewerSuccessScreen
      LazyColumn(items = blocks, key = documentId:blockId)
        → MarkdownBlockView
            → HeadingBlockView | ParagraphBlockView | QuoteBlockView
            → ListBlockView | CodeBlockView | TableBlockView | ImageBlockView
                → InlineMarkdownText / Text / CodeHighlighter
```

搜索并行支路：

```
TopBar search icon → ViewerViewModel.openSearch()
SearchTopBar onQueryChange → onSearchQueryChange(query)
  → cancel previous Job
  → withContext(Dispatchers.Default) { DocumentSearch.search(blocks, query) }
  → DocumentSearchUiState(matches, currentIndex=0)
LaunchedEffect(currentIndex, matches)
  → scrollToBlockIndex(listState, match.blockIndex, listHeaderCount, …)
MarkdownBlockView(isSearchActive, activeMatch)
  → searchBlockHighlight / highlightPlainText (heading)
```

### 2.2 审计问答（逐项）

| # | 问题 | 答案 | 证据 |
|---|------|------|------|
| 1 | 解析器 | **自研** `MarkdownParser`，无第三方 MD 库 | `markdown/MarkdownParser.kt` |
| 2 | 稳定 block model | **是** `sealed class MarkdownBlock` | `MarkdownBlock.kt` |
| 3 | 多 Compose Text | 每 block 一个 Lazy item；块内多为 1 个 Text/InlineMarkdownText | `ViewerScreen` itemsIndexed |
| 4 | 粗体/链接/行内代码 | **AnnotatedString** via `InlineMarkdownParser.parse` | `InlineMarkdownText.kt` |
| 5 | 各块组件 | Heading→`HeadingBlockView`；Paragraph→`ParagraphBlockView`；List→`ListBlockView`；Quote→`QuoteBlockView`；Code→`CodeBlockView`；Table→`TableBlockView`；Image→`ImageBlockView`；Divider→`HorizontalDivider` | `MarkdownBlockView.kt` |
| 6 | 滚动状态 | `LazyListState`（`rememberLazyListState`，阅读位置恢复） | `ViewerScreen.kt` |
| 7 | 按 block index 滚动 | **是** `scrollToBlockIndex` = headerCount + blockIndex | ~1120–1141 |
| 8 | 块内字符位置 | **否**（无 TextLayoutResult / BringIntoViewRequester 绑定） | 现状仅到 item |
| 9 | 高亮/AnnotatedString | 有 `highlightPlainText`；块级 `searchBlockHighlight`；**段落未做字级搜索 span** | `SearchHighlight.kt`、`SearchHighlightStyle.kt` |
| 10 | 文档 key | **`documentId`**（ViewModel 工厂）；Lazy key 前缀 `document.id`；URI 仅相对图用 | `ViewerViewModel`、`NavGraph` |

### 2.3 关键类型

| 类型 | 路径 | 职责 |
|------|------|------|
| `MarkdownBlock` | `markdown/MarkdownBlock.kt` | 块模型 |
| `MarkdownParser.ParseResult` | `MarkdownParser.kt` | blocks + toc + anchors + blockIds |
| `InlineMarkdownParser` | `InlineMarkdownParser.kt` | 行内 → AnnotatedString；`toPlainText` |
| `SearchMatch` | `search/SearchMatch.kt` | blockIndex, matchStart/End, preview, listItemIndex |
| `DocumentSearchUiState` | `ViewerViewModel.kt` | isActive, query, matches, currentIndex, counterLabel |
| `AnchorRegistry` | `markdown/AnchorRegistry.kt` | 目录锚点（可复用滚动路径） |

### 2.4 调用关系（搜索）

```
ViewerScreen.openSearch
  → ViewModel.openSearch
SearchTopBar
  → onSearchQueryChange / goToNextMatch / goToPreviousMatch / closeSearch
DocumentSearch.search
  → List<SearchMatch>
LaunchedEffect → scrollToBlockIndex
MarkdownBlockView → *BlockView(search highlight)
```

---

## 3. 搜索数据源：方案比较

### 方案 A：搜索 Markdown 原文

- **优：** 实现极简。  
- **缺：** 命中 `#`、`**`、链接 URL、图片 path；offset **无法** 对齐 Lazy block。  
- **结论：** **否决** 作为 V1 主路径。

### 方案 B：解析后「可见文本快照」+ offset 映射

- **优：** 接近用户所见；可挂 stable map。  
- **缺：** 需维护 plain offset → block/inline。  
- **结论：** **推荐作为 DocumentSearch 的文本提取层**（在现有 block 循环内用 plain 文本，而非原始 MD 串）。

### 方案 C：逐 block 搜索片段（现状骨架）

- **优：** 已实现；易映射 `blockIndex`。  
- **缺：** 当前用的是 **块内原始字符串**（仍含 `**`、`[label](url)` 等），**不是** 纯可见文本。  
- **结论：** **保留 C 的框架，将文本源升级为 B 的 plain 提取**。

### 推荐组合

**方案 C 架构 + 方案 B 文本语义**

```
for each block:
  plain = visiblePlainText(block)   // InlineMarkdownParser.toPlainText 等
  find all case-insensitive substrings
  emit SearchMatch(blockIndex, start, end, …)
```

**V1 明确不支持跨 block 匹配**（例如上一段末 + 下一段首连成一词）。

### 3.1 各内容是否搜索（V1 产品表）

| 内容 | V1 | 说明 |
|------|:--:|------|
| 标题 | **是** | `Heading.text`（解析后已无 `#`） |
| 段落 | **是** | plain via `toPlainText` |
| 列表项 | **是** | 每 item plain；`listItemIndex` |
| 引用 | **是** | plain |
| 表格单元格 | **是** | header/cell 字符串（当前无复杂 inline 解析则整格 plain） |
| 代码块正文 | **是** | `CodeBlock.code`；性能见 §7 |
| 行内代码 | **是** | 包含在 plain 可见文本中 |
| 链接显示文字 | **是** | `toPlainText` 只保留 label |
| 链接 URL | **否** | 不进 plain |
| 图片 alt | **是**（若显示） | 搜索 alt；UI 失败态也常显示 alt |
| 图片 title | **可选** | 成功态 caption 可能显示；V1 可搜 title |
| 图片 source URI | **否** | **现状 bug：正在搜索 source** → 实现阶段删除 |
| Divider | 否 | |
| HTML 注释 | 否 | `stripHtmlComments` 后已不存在 |
| YAML front matter | **否** | 解析器未建模；若将来作为段落出现再议 |
| Markdown 控制符 `**` `#` | **否** | 靠 plain 提取 |

---

## 4. 位置映射与高亮

### 4.1 建议数据模型（在现有上演进）

```kotlin
// 现状已基本满足；建议补充字段（实现阶段）：
data class SearchMatch(
    val blockIndex: Int,
    val matchStart: Int,      // plain 文本坐标
    val matchEnd: Int,
    val preview: String = "",
    val listItemIndex: Int = -1,
    // 建议新增：
    // val stableKey: String,  // "$blockIndex:$matchStart:$matchEnd:$listItemIndex"
    // val tableCell: TableCellRef? = null  // 若表格需精确定位
)

data class DocumentSearchUiState(
    val isActive: Boolean = false,
    val query: String = "",
    val matches: List<SearchMatch> = emptyList(),
    val currentIndex: Int = 0,
    // 建议：documentKey 校验
    // val documentId: String = "",
)
```

Offset 语义：**相对于该 block（或 list item）的 plain 可见字符串**，与 `InlineMarkdownParser.toPlainText` 一致。

### 4.2 跨 span / 跨行 / 跨 block

| 场景 | V1 行为 |
|------|---------|
| `hello **world**` 搜 `hello world` | plain 为 `hello world` → **应命中**；高亮需在 AnnotatedString 上映射 plain→source 或对 plain 再布局（见下） |
| 段落内软换行 | block 内 `\n` 保留；子串可跨行 |
| 两段落之间 | **不支持**；文档化产品语义 |
| 同一 Text 多匹配 | 支持；`indexOf` 循环（现状已有） |
| 当前 vs 其他匹配 | **推荐：** 非当前：淡背景 span；当前：更深背景 + 可选块边框。现状仅当前块背景，字级仅标题 |

### 4.3 plain offset → 渲染 AnnotatedString

难点：`InlineMarkdownParser` 从 **源串** 建 AnnotatedString，长度含标记时与 plain 不同。

**V1 可行路径（两档）：**

1. **MVP 硬化（优先）：**  
   - 标题 / 代码：继续 `highlightPlainText`（源=plain）。  
   - 段落 / 列表 / 引用：  
     - **短期：** 保持块级 `searchBlockHighlight`（已有）+ 滚动到块；  
     - **目标：** 给 `InlineMarkdownText` 增加可选 `highlightRanges: List<IntRange>`，在 **toPlainText 对齐的副本** 上高亮，或扩展 parser 输出 plain→source map。  

2. **完整映射（若实现阶段时间允许）：**  
   - `InlineMarkdownParser` 输出 `PlainProjection`（plain 字符 → source range）。  
   - 搜索在 plain 上；高亮用 source range 打 SpanStyle，保留 link annotation。

**禁止：** 为搜索再写一套 block 渲染。

### 4.4 不得破坏

| 能力 | 风险 | 缓解 |
|------|------|------|
| 链接点击 | 高亮覆盖 ClickableText | 高亮仅 background SpanStyle，保留 LINK_ANNOTATION_TAG |
| 行内代码背景 | 双重背景 | 搜索 span 用半透明 tertiary |
| 表格布局 | 单元格重测 | 仅单元格 Text 内 span 或整表块级高亮 |
| a11y | 无匹配通告 | contentDescription / 计数 TalkBack 可读 |
| 关闭搜索 | 残留 | `activeMatch=null` → 走无高亮分支（现状） |

---

## 5. 滚动定位

### 5.1 现有机制

| 机制 | 是否存在 | 用途 |
|------|:--------:|------|
| `LazyListState.scrollToItem` | 是 | 远距 TOC / 搜索 |
| `animateScrollToItem` | 是 | 近距（≤4 items） |
| `scrollToBlockIndex` | 是 | 搜索 + TOC 共用 |
| `BringIntoViewRequester` | **否** | — |
| `TextLayoutResult` | **否** | — |
| `AnchorRegistry` | 是 | 标题锚点，非搜索字级 |

### 5.2 V1 推荐滚动策略

1. **先** `scrollToBlockIndex(match.blockIndex)`（已实现）。  
2. **可选增强：** 块内 `BringIntoViewRequester` 挂在当前匹配 span 的布局节点（长代码块底部）。  
3. 快速连点 prev/next：`LaunchedEffect` 键变化取消前一 suspend（Compose 已取消前 effect）；`scrollToBlockIndex` 已 catch Exception。  
4. **不必** 排队多个滚动 Job；以最新 `currentIndex` 为准。  
5. 旋转：Lazy 恢复 + 可对 `currentMatch` 再触发一次 scroll（`LaunchedEffect` 已依赖 matches/index）。  
6. 键盘弹出：搜索激活时 `topBarScrollState.forceShow()`；contentPadding 含 top inset（`searchActive`）。可再加 IME padding（实现阶段评估 WindowInsets）。  
7. **关闭搜索不强制恢复搜索前阅读位置**（V1）：用户可能已用 next 浏览；阅读位置仍由正常滚动持久化。

---

## 6. 性能设计

| 项 | 设计 |
|----|------|
| 防抖 | **建议 200ms**（现状 **无防抖**，每键 Default 全扫 → 实现阶段必须加） |
| 线程 | 保持 `Dispatchers.Default`（现状正确） |
| 取消 | `searchJob?.cancel()`（现状有）；debounce 与 Job 一并取消 |
| 不重复 parse | 搜索只用已有 `Success.blocks` |
| 空 query | 不扫全文（现状：trim empty → clear matches） |
| 文档切换 | ViewModel 随 documentId；onDispose closeSearch |
| 匹配上限 | 建议 **硬上限 2000**（或 5000）条，超出截断并 UI 提示「仅显示前 N 条」 |
| 超长代码块 | 单块扫描 O(n)；可接受；避免为每匹配 clone 整个 AnnotatedString 缓存 |
| 中文 | `Locale` lower case；中文不受大小写影响 |
| 索引持久化 | **不做** |

**层级：** ViewModel 持有状态；`DocumentSearch` 为纯 domain/object（无 Android UI）；UI 只消费 StateFlow。

---

## 7. 交互设计

### 7.1 现状

- 顶栏搜索图标 → `openSearch`  
- `SearchTopBar`：返回关搜索、输入框、计数、上/下  
- 自动 focus  
- 循环导航：`% size` / 到 0 绕到 last  
- BackHandler 关搜索  

### 7.2 V1 统一语义

| 行为 | 规格 |
|------|------|
| 循环 | **是**（与现状一致） |
| query 变化 | `currentIndex = 0`（现状） |
| 清空 query | matches 空；保持搜索模式 UI；显示 `0 / 0` |
| 无匹配 | `0 / 0`；禁用上/下（现状 `hasMatches`） |
| 文档切换 | 清空（dispose closeSearch） |
| 进程重建 | **不恢复** 搜索（可接受；无 SavedState） |
| 横竖屏 | 保持 isActive + query；重新 scroll 当前 match |
| 返回键 | **先关搜索**（现状）；不要求「先收键盘再关」两步，但实现可用 `FocusManager.clearFocus` 再 close |
| TalkBack | 搜索框 label、计数 `stringResource`、上/下/关闭已有 contentDescription；无结果时应有可读状态文案（建议 `R.string.search_no_matches`） |

### 7.3 无结果文案

现状计数 `0 / 0` 克制可用；可选 snackbar/占位「无匹配结果」——**不弹风暴**。

---

## 8. 与现状差距（实现阶段 backlog）

| ID | 差距 | 优先级 |
|----|------|:------:|
| G1 | 搜索用块源串而非 `toPlainText` → 可命中 `**` / URL | P0 |
| G2 | **搜索图片 `source` URI** | P0 删除 |
| G3 | 无输入防抖 | P0 |
| G4 | 段落/列表/引用无字级高亮（仅块背景） | P1 |
| G5 | 非当前匹配无任何高亮 | P1 |
| G6 | 无块内 BringIntoView | P2 |
| G7 | 无匹配数上限 | P1 |
| G8 | query 与 documentId 未显式绑定校验 | P1 |
| G9 | 自动化测试几乎缺失（无 search unit tests 目录） | P0 |
| G10 | 表格单元格 match 无 cell 坐标，仅块级高亮 | P2 |

**不属于 V1：** 全库搜索、正则、历史、FTS、PDF、改 R8/External Open/H1。

---

## 9. 建议实现阶段（后续 object，本轮不做）

1. **P0 正确性：** plain 文本搜索 + 去掉 image source + 防抖 + 单测矩阵。  
2. **P1 高亮：** 扩展 `InlineMarkdownText` 或 plain 投影；当前/非当前 span；匹配上限。  
3. **P2 滚动：** BringIntoView；长块底部；IME inset。  
4. **门禁：** JVM 搜索单测 + 相关 UI 不回归；可选 QA 人工清单。  
5. **真机：** 中文、大文件、快速 next、切换文档、返回键。

---

## 10. 风险与边界再确认

| 风险 | 缓解 |
|------|------|
| 双渲染器 | 禁止；只扩展现有组件 |
| 改 PDF / H1 / Tile | frozen |
| 改 External Open | 禁止 |
| 改 R8 相对图语义 | 禁止 |
| schema / FTS | 禁止 |
| 跨 block 搜索 | V1 明确不支持 |

---

## 11. 结论

| 项 | 判定 |
|----|------|
| 架构可行性 | **可行**；骨架已在生产路径 |
| 推荐数据源 | Block 可见 plain 文本（方案 C+B） |
| 推荐滚动 | 现有 block scroll；后续 BringIntoView |
| 推荐实现姿态 | **硬化现有搜索，而非重写** |
| 本轮交付 | 设计审计完成；**无搜索代码实现** |
| 下一动作 | 用户确认后开 `R9` 实现 object（P0→P1） |

**设计审计状态：DESIGN_AUDIT_COMPLETE**  
**实现：未开始（按任务禁止）**
