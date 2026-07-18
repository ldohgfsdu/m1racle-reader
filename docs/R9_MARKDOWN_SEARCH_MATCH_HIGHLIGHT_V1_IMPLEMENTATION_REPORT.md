# R9 Markdown Search Match Highlight V1 — 实现报告

**Object:** `R9_MARKDOWN_SEARCH_MATCH_HIGHLIGHT_V1`  
**Date:** 2026-07-16  
**Baseline:** `a7da58c` (P0_DEVICE_ACCEPTED)  
**Status:** **READY_FOR_INDEPENDENT_REVIEW**（**未 commit**）

---

## 实施前确认

| 项 | 结论 |
|----|------|
| SearchMatch | `documentKey`, `blockId`, `blockIndex`, `matchStart`/`matchEnd`（别名 `start`/`end`）, `listItemIndex`, `stableKey` |
| Offset 权威 | `MarkdownSearchTextExtractor` → `toPlainText` / code 原文，Kotlin UTF-16 |
| AnnotatedString | Paragraph/Quote/List/Table：`InlineMarkdownParser.parse`；Heading：改为 parse；Code：`CodeHighlighter` |
| `parse` vs `toPlainText` | 常见 inline 用例 parity 测试通过（含 bold/link/code/中文/emoji） |
| 整块黄 | `searchBlockHighlight` 已改为 **no-op**；调用链已移除业务依赖 |
| 设计冲突 | **无** → 未输出 `BLOCKED_BY_OFFSET_MAPPING` |

---

## 行为变更

1. **普通匹配**：克制 `tertiaryContainer` α≈0.38 背景，仅盖字符。  
2. **当前匹配**：同色 α≈0.82，后叠加以保证视觉优先。  
3. **同 block 多命中**：两处均有普通高亮；active 仅一处更强。  
4. **不再整块涂黄**（搜索结果路径）。  
5. **样式保留**：bold/italic/code/link annotation 与点击保留。  
6. **P2 未做**：无 BringIntoView / 字符精滚。

---

## 数据与重组

- `groupMatchesByBlockIndex(matches)`：仅在 `matches` / documentKey / isActive 变化时 `remember`。  
- Item 只取 `matchesByBlock[index]` +（仅当本 block 为 active 时）`activeStableKey`。  
- 非法 range：**跳过**，不 `coerceIn` 静默错高亮。

---

## 分 block 支持

| Block | 实现 |
|-------|------|
| Paragraph | `InlineMarkdownText` + ranges |
| Heading | `parse` + `withSearchHighlights`（修 P0 raw 错位） |
| List | 仅正文字符；bullet/序号不高亮 |
| Quote | 有命中时整段单 Text 保 offset；无命中时保留 attribution 拆分 |
| Code | 在 syntax highlight 上叠 span；语言栏/复制钮不参与 |
| Table | cell ordinal=`listItemIndex`，cell-local offset |
| Image | 不参与 |
| Link | label 高亮 + annotation 保留 |

---

## 主要文件

| 文件 | 变更 |
|------|------|
| `search/SearchHighlight.kt` | multi-range overlay、group helpers |
| `SearchHighlightStyle.kt` | normal/active 色；block 黄 no-op |
| `InlineMarkdownText.kt` | `searchRanges` |
| `MarkdownBlockView` + 各 *BlockView | 接 `BlockSearchHighlights` |
| `ViewerScreen.kt` | 按 block 分发高亮 |
| `SearchHighlightTest.kt` | 映射/中文/emoji/非法 range/表格 ordinal |

---

## 测试

- `./gradlew :app:testDebugUnitTest --tests 'com.markleaf.app.search.*'` **PASS**  
- markdown 相关 unit tests **PASS**  
- `lintDebug` **PASS**  
- `assembleQa` **PASS**

---

## 已知限制（P1）

- 仍为 **block 级滚动**（非字符精滚）。  
- Quote 在搜索命中时暂时不拆 attribution 行（offset 安全）。  
- `parse`/`toPlainText` 对 image-in-inline 等边角仍可能 divergence；非法 range 跳过。  
- 未做真机人工验收（交给独立审查 / 后续）。

---

## 未做（边界）

- commit / push  
- PDF / H1 / Tile / R8 / External Open / schema  
- P2 块内精滚  

---

## 结论

**READY_FOR_INDEPENDENT_REVIEW**
