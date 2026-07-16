# R9 Markdown Search Match Highlight V1 设计审计

**Object:** `R9_MARKDOWN_SEARCH_MATCH_HIGHLIGHT_V1`  
**Phase:** design_audit_only  
**Date:** 2026-07-16  
**Baseline:** P0 checkpoint（文内搜索可见文本 + block 级高亮）  
**结论:** **DESIGN_READY**

---

## 当前高亮调用链

```
ViewerSuccessScreen
  searchState.collectAsStateWithLifecycle()
  ReaderDocumentBody(searchState)
    activeMatch = searchState.currentMatch.takeIf { isActive }
    activeMatchBlockIndex = activeMatch?.blockIndex
    LazyColumn itemsIndexed(blocks)
      isActiveMatch = (index == activeMatchBlockIndex)   // O(1)，不遍历 matches
      MarkdownBlockView(isSearchActive, activeMatch)
        highlight = isSearchActive && activeMatch != null
        → Paragraph/Quote/Table/Image: Modifier.searchBlockHighlight(整块黄底)
        → Heading/Code: 同上 + highlightPlainText(raw source, matchStart/End)  // 仅 active
        → List: 整块 + 可选 listItemIndex 子项高亮
```

**P0 问题（产品已确认）：** 仅 `currentMatch` 驱动整块背景；同 block 内 1/2 与 2/2 视觉相同。

**已有部分能力：** `SearchHighlight.kt` / `highlightPlainText` + Heading/Code 对 **active** 做过字符背景，但：

- 输入是 **raw Markdown 源**，不是 `toPlainText` 可见串；
- 仅 active，无「普通匹配」多层；
- Paragraph/Quote/List/Table **仍整块涂底**。

---

## SearchMatch offset 与 AnnotatedString 映射

### 语义（P0 已定义）

`SearchMatch.matchStart/matchEnd`：UTF-16 半开区间，落在 **searchable visibleText**（`MarkdownSearchTextExtractor` → `InlineMarkdownParser.toPlainText` 或 code 原文）。

### 渲染路径

| Block | 搜索文本 | 渲染 | 映射风险 |
|-------|----------|------|----------|
| Paragraph / Quote / List item | `toPlainText(source)` | `InlineMarkdownParser.parse` → `AnnotatedString` | **低**：`parse` 与 `toPlainText` 对 strong/em/link/code 同结构剥标记；`hello **world**` → 二者长度均为 `hello world` |
| Heading | `toPlainText` | 今日 `highlightPlainText(block.text raw)` | **中高**：raw 与 plain 长度不一致时 active 字符高亮错位 |
| CodeBlock | `block.code` 原文 | monospaced 原文 | **无**（1:1） |
| Table cell | `toPlainText(cell)` | 布局测量 plain + 部分 styled | **中**：需与 cell 渲染路径对齐 |
| Image | 不索引 | n/a | P1 不索引 / 不高亮 body |
| Divider | 无 | n/a | 跳过 |

### 推荐映射策略（不新开 offset 表，除非测试失败）

1. **规范：** 所有 inline 型 block 以 `toPlainText(source)` 为 offset 权威。  
2. **渲染：** `parse(source)` 得到 styled `AnnotatedString`；若 `annotated.length == plain.length` 且 `annotated.text == plain`，直接在 `[start,end)` 叠 `SpanStyle(background=…)`。  
3. **JVM 强制测试：** 大量 fixture 断言 `parse(s).text == toPlainText(s)`；失败类型再引入 span map。  
4. **已知 divergence 点（审计）：**  
   - `toPlainText` 含 image alt / HTML entity / soft-break 归一；`appendInline` 对 `![`、部分 entity 处理不完全一致。  
   - P1 实现前加 **parity 测试**；不一致则统一 walk 或在 extractor 与 UI 共用同一 builder。  

**不**采用「对 raw Markdown 做 offset」作为主路径。

---

## 各 block 类型支持情况

| 类型 | P1 字符级 | 说明 |
|------|-----------|------|
| Paragraph | **必须** | 主战场；同 block 多命中 |
| Quote | **必须** | 同 Paragraph |
| List item | **必须** | 用 `listItemIndex` 限定 item；item 内多 match |
| Heading | **必须** | 改走 plain-aligned AnnotatedString，去掉错误 raw 路径 |
| CodeBlock | **必须** | 1:1 原文 span |
| Table cell | **应支持** | cell 级 segment；布局不破坏 |
| Image | 跳过 | P0 不索引 |
| Divider | 跳过 | |

---

## 普通匹配与当前匹配样式

| 角色 | 样式意图 | 实现建议 |
|------|----------|----------|
| 普通匹配 | 克制浅底，**仅字符** | `SpanStyle(background = tertiaryContainer.copy(α≈0.35))` 或 surfaceVariant 系；**无**整块 `searchBlockHighlight` |
| 当前匹配 | 更明显浅底 + 可选细边 | 更高 alpha 或 secondaryContainer；仍只盖字符 |
| 共存 | 不毁 bold/italic/code/link | 在现有 span **之上**叠加 background；不替换 fontWeight/link annotation |

**移除 / 降级：**

- `Modifier.searchBlockHighlight(true)` 在 Paragraph/Quote/List/Table/Image **默认关闭**（P1 不再整块涂黄）。  
- 可选：仅当该 block **无法**字符映射时 fallback 整块（应极少）。

**同一 block 内：**  
普通 matches 全部 span + current match 用更强 span（后写覆盖或分层 alpha）。

---

## 重组隔离方案

### 目标

- next/prev 只让 **旧 active block** 与 **新 active block** 重组；同 block 内切换只重组 **该一项**。  
- 可见列表 **不得** 订阅完整 `matches` 列表。

### 推荐数据面

```text
// ViewModel 或 remember(documentKey, matches) 在 Default/组合边界外
data class BlockSearchHighlight(
  val blockIndex: Int,
  val blockId: String,
  val ranges: List<IntRange>,      // 普通匹配 UTF-16
  val activeRange: IntRange?,      // 当前项，可 null
  val listItemIndex: Int = -1,
)

// 预构建：Map<blockIndex, BlockSearchHighlight> 或 sparse list
// activeIndex 变化时只更新两个 block 的 activeRange
```

### Compose 边界

1. `ReaderDocumentBody` **不** collect 整个 `matches` 进每个 item。  
2. 传入：  
   - `highlightByBlockIndex: Map<Int, BlockSearchHighlight>`（`remember(matches, documentKey)` 一次构建）  
   - `activeBlockIndex: Int?`（`derivedStateOf` / 单独 State）  
3. item 只读 `highlightByBlockIndex[index]` 与 `activeBlockIndex == index`。  
4. `key` / `contentType` 保持稳定。  
5. `searchState.isActive == false` → 不构建 map、不进高亮分支。

### 禁止

- 每 item `matches.filter { it.blockIndex == index }` 扫全表（matches 可达 2000）。  
- 每次 next 全量 `DocumentSearch.search` 或 `MarkdownParser.parse`。  
- 搜索关闭仍保留 highlight map。

---

## 性能风险

| 风险 | 缓解 |
|------|------|
| active 变化触发全列表 recompose | 拆 `activeBlockIndex`；item 只依赖本 block 数据 |
| 2000 matches 构建 map 卡顿 | Default 线程构建；debounce 后一次；ArrayList per block |
| 每帧 re-parse Markdown | `remember(text, styles, ranges)` |
| 与滑动基线回归 | 关闭搜索路径零开销；A/B 对比 9d8a32a / P0 checkpoint |
| Span 过多 | 单 block 内 ranges 有上限（与 MAX_MATCHES 一致） |

---

## 预计修改文件

| 文件 | 变更 |
|------|------|
| `ViewerViewModel.kt` / `DocumentSearchUiState` | 可选：预聚合 highlight map 或 selector |
| `ViewerScreen.kt` `ReaderDocumentBody` | 传 per-block highlight；去掉仅 active 整块逻辑 |
| `MarkdownBlockView.kt` | 传 `BlockSearchHighlight?` |
| `ParagraphBlockView` / `QuoteBlockView` / `ListBlockView` | 字符 span；去整块黄 |
| `HeadingBlockView` / `CodeBlockView` | plain-aligned 多 range |
| `TableBlockView` | cell 级 span（若可行） |
| `SearchHighlightStyle.kt` | 普通/当前颜色；废弃或收窄 `searchBlockHighlight` |
| `SearchHighlight.kt` | 扩展：多 range + overlay on AnnotatedString |
| `InlineMarkdownText.kt` | 可选：接受 highlight ranges 参数 |
| 新测试 | offset 映射、同 block 双命中、recompose 契约、parity parse/plain |

**不修改：** PDF / H1.5A / Tile / R8 / External Open / schema。

---

## JVM / Compose UI / 真机测试矩阵

### JVM

- `parse(s).text == toPlainText(s)` 矩阵（bold/link/code/italic/混合）  
- `hello **world**` 搜 `hello world`：range 落在 AnnotatedString  
- 同 block 两 match：ranges size=2；active 切换  
- 跨 span：`**ab**cd` 搜 `bc`  
- CodeBlock 原文 1:1  
- 搜索关闭无 highlight 输入  
- MAX_MATCHES 截断后 UI 不越界  

### Compose UI（可选 instrumentation）

- next 同 block：仅该 item recomposition marker（若有）  
- 关闭搜索：无 background span  

### 真机

- 用户场景：`临床检索` 同 block 1/2↔2/2 **视觉可分**  
- 大文档 SEARCHNEEDLE next 不掉帧感（对照 P0 基线）  
- 表格/代码块/标题抽检  

---

## P1 与 P2 边界

| | P1 | P2 |
|--|----|----|
| 字符级普通/当前高亮 | **是** | 保持 |
| 同 block 视觉区分 | **是** | — |
| 去整块黄底 | **是** | — |
| BringIntoView / 字符坐标滚 | **否** | **是** |
| TextLayoutResult 定位 | **否** | **是** |
| 跨 block 匹配 | **否** | 另议 |
| 全库/正则/历史/PDF | **否** | **否** |

---

## 结论

**DESIGN_READY**

- 渲染模型允许在现有 `AnnotatedString` 上叠加 background span。  
- offset 权威保持 P0 visible UTF-16；需 **parity 测试** 锁 `parse`/`toPlainText`。  
- Heading raw 路径必须在 P1 纠正。  
- 重组隔离可通过 **预聚合 Map + activeBlockIndex** 满足，无需重写 LazyColumn。  
- **不** blocked by render model；**不**要求先做全量 mapping refactor，但实现首周必须落地 parity 门禁。

**下一步（未在本轮实现）：** P1 实现 PR，严格 measure → 单点 → 复测。
