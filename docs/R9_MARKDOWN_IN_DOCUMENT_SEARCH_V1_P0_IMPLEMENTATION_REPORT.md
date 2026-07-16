# R9 Markdown 文内搜索 V1 — P0 实施报告

**Object:** `R9_MARKDOWN_IN_DOCUMENT_SEARCH_V1`  
**Phase:** P0 搜索语义与状态隔离  
**Baseline:** `9d8a32a`  
**Date:** 2026-07-16  
**Commit:** 未提交  

---

## 实施前确认

| 项 | 现状 |
|----|------|
| DocumentSearch | 直接扫 `block.text` / `code` / **含 image.source** |
| toPlainText | `InlineMarkdownParser.toPlainText` 已有（label-only 链接） |
| 防抖 | 无 |
| documentKey | 无；仅 ViewModel 构造期 `documentId` |
| 文档切换 | 新 ViewModel + onDispose `closeSearch` |

---

## 修改摘要

### 新增
- `search/MarkdownSearchTextExtractor.kt` — 可见文本提取
- `search` 测试：`DocumentSearchTest`、`MarkdownSearchTextExtractorTest`
- `DocumentSearchUiStateTest`

### 更新
- `DocumentSearch.kt` — plain 文本、documentKey、MAX_MATCHES、非重叠、不搜 URI
- `SearchMatch.kt` — documentKey / blockId / matchedText / stableKey
- `ViewerViewModel.kt` — 200ms 防抖、取消、documentKey 绑定、结果迟到丢弃、`resultsTruncated`

### 可见文本规则（已实现）

| 类型 | 行为 |
|------|------|
| 标题/段落/引用/列表 | `toPlainText`（无 `**` / URL） |
| 链接 | 仅 label |
| 图片 | **不搜 source**；alt + title（UI 可见） |
| 代码块 | 正文 only |
| 表格 | 每单元格独立 segment |
| YAML `---` 围栏 | 跳过围栏内 block（若解析为段落） |
| Divider | 排除 |
| 跨 block | 不支持 |

### 匹配
- Case-insensitive（`Locale.ROOT`）
- 非重叠子串；UTF-16 索引
- 空 query（`isEmpty()`）不扫描；**保留首尾空格**用于匹配
- 上限 `MAX_MATCHES = 2000`，`resultsTruncated` + 计数 `n / m+`

### 并发 / 隔离
- 新 query → cancel Job + debounce 200ms  
- 结果应用前校验 `query` 与 `documentId`  
- `lastScannedQuery` 跳过重复扫描  
- `openSearch`/`closeSearch` 绑定 `documentKey`  

### 未做（P0 已知限制）
- 块内精确滚动 / BringIntoView  
- 全匹配同时字级高亮  
- 段落字级高亮（仍块级）  

---

## 门禁

| 门禁 | 命令 | 结果 | 摘要 |
|------|------|------|------|
| JVM 搜索+相关 | `testDebugUnitTest` search/markdown/image/package/share | **PASS** | **170** tests, 0 fail（含 DocumentSearch 20 + Extractor 13 + UiState 4） |
| lintDebug | `:app:lintDebug` | **PASS** | BUILD SUCCESSFUL |
| assembleDebug | `:app:assembleDebug` | **PASS** | |
| assembleQa | `:app:assembleQa` | **PASS** | |
| 总耗时 | | ~84s | |

真机验收：本轮不要求。

---

## 边界

- 未改 PDF / H1 / Tile / External Open / R8 相对图语义  
- 无 schema / FTS  
- 未 commit / push  
- 无关工作区保持原样  

---

## 结论

**P0_IMPLEMENTATION_COMPLETE** — 待人工审查；可进入 P1 高亮或真机清单。
