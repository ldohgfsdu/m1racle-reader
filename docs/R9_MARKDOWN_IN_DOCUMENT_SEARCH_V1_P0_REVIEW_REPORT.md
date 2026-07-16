# R9 Markdown 文内搜索 V1 — P0 独立审查报告

**Object:** `R9_MARKDOWN_IN_DOCUMENT_SEARCH_V1`  
**Phase:** P0 independent review + device readiness  
**Baseline:** `9d8a32a`  
**Date:** 2026-07-16  
**Commit:** 未提交  

---

## 1. 审查结论

**APPROVED_FOR_DEVICE_ACCEPTANCE**

生产链路使用 `MarkdownSearchTextExtractor` + `DocumentSearch`；图片 URI/不可见 alt 已排除；debounce/generation/documentKey 迟到保护到位；门禁 **182 PASS** + lint/assemble PASS。  
可进入真机验收（P0 范围：语义与状态隔离；滚动仍为 **block 级**）。

---

## 2. 生产调用链证据

```
MarkdownParser.parse → List<MarkdownBlock>
  → ViewerViewModel.onSearchQueryChange (debounce 200ms)
      → Dispatchers.Default
          DocumentSearch.search(documentKey, blocks, query, blockIds)
            → MarkdownSearchTextExtractor.extract(...)
            → non-overlapping foldCase match
      → identity checks (generation, query, documentKey)
      → DocumentSearchUiState
  → SearchTopBar (query / counter / prev / next)
  → LaunchedEffect → scrollToBlockIndex(match.blockIndex)  // block only
```

| 确认项 | 结果 | 证据 |
|--------|------|------|
| 使用 Extractor | **是** | `DocumentSearch.kt` L43 |
| 旁路扫 source 串 | **无** | 唯一入口 `DocumentSearch.search` |
| 图片 URI / fence lang | **不进入** | Extractor Image → `Unit`；Code 仅 `block.code` |
| 重新 parse MD | **否** | 使用已有 `Success.blocks` |
| 主线程扫描 | **否** | `withContext(Dispatchers.Default)` |
| 重组重复搜索 | **否** | 仅 `onSearchQueryChange`；`lastScannedQuery` 去重 |
| 文档隔离 | **是** | ViewModel 按 `documentId`；generation + key 校验；dispose `closeSearch` |

---

## 3. 可见文本语义

| 内容 | 预期 | 审查结果 |
|------|------|----------|
| 标题/段落/列表/引用 | plain | `toPlainText` |
| 粗体跨 span `hello world` | 命中 | 测试覆盖 |
| 链接 URL | 不搜 | 测试 |
| 代码块正文 | 搜 | language 不在 code 字段 |
| 表格单元格 | 搜 | 分 segment |
| YAML `---` 围栏 | 跳过 | Extractor |
| 图片 source | 不搜 | **PASS** |
| 图片 alt/title | 见下 | **审查后修正为不搜** |

### 图片 alt/title（审查修复）

**UI 证据（`ImageBlockView`）：**

- Loading：无 alt 正文  
- Success：`Image` + `contentDescription=alt`（无障碍，非可见 Text）；caption 仅在 decode **成功后**显示 alt/title  
- Error：可见 Text 显示 alt  

P0 要求与加载状态解耦 → **不可把仅错误态/成功后 caption 的字符串当作稳定可见搜索源**。  

**修复：** `MarkdownBlock.Image` **整块不进入** searchable segments（source/alt/title 均不搜）。  

---

## 4. 匹配 / Unicode

- `Locale.ROOT` lower case  
- 非重叠 advance by needle length  
- 空 query：不扫描  
- 首尾空格保留（测试 ` y `）  
- emoji 单测  
- offset = UTF-16，与 `String`/`AnnotatedString` 一致（P1 字级高亮可用）  
- 跨 block 不命中  

---

## 5. 并发与 documentKey

| 机制 | 实现 |
|------|------|
| Debounce | `SEARCH_DEBOUNCE_MS = 200` |
| 取消 | `searchJob?.cancel()` on new query / close / open |
| Generation | `searchGeneration++`；写入前比对 |
| 校验 | query + documentId + result.documentKey + generation |
| onCleared | cancel job |
| closeSearch | cancel + generation bump + 清空 matches |

测试：`DocumentSearchIsolationTest`（同文不同 key、截断、200KB、1200 blocks、20 次快速 query、并发 async）。

ViewModel 无 Robolectric 全量测；domain + generation 语义由单元测试覆盖。

---

## 6. MAX_MATCHES=2000 与 UI

| 项 | 行为 |
|----|------|
| 范围 | **整文档**总结果上限 |
| 停止 | 达上限 break，不再扫后续 segment |
| 截断标记 | `resultsTruncated` |
| 计数 | 截断时 `i / n+`；空 query 为 `—`；无匹配 `0 / 0` |
| index | query 变重置 0；counter coerce 防越界 |
| 循环 next/prev | 保持 `% size` |

---

## 7. 滚动（P0 限制）

- 仅 `scrollToBlockIndex`  
- 写入前校验 isActive / documentKey / non-empty / blockIndex in range  
- **不保证** 长段内字符进视口  

---

## 8. 审查中最小修复清单

1. 排除 Image 块（alt/title/source）于 searchable text  
2. `searchGeneration` 迟到结果保护  
3. `onCleared` 取消 job  
4. 滚动前 documentKey / 空结果 / 越界校验  
5. 空 query 计数 `—`  
6. 补 Isolation 与相关断言测试  

---

## 9. 门禁

| 门禁 | 结果 | 摘要 |
|------|------|------|
| JVM search+markdown+image+package+share | **PASS** | **182** tests, 0 fail ~（含 Isolation 11 + Search 20 + Extractor 13 + UiState 5） |
| lintDebug | **PASS** | 0 errors |
| assembleDebug / assembleQa | **PASS** | |
| 总耗时 | ~58s | |
| 真机 | **未跑** | 本轮仅就绪判定 |

---

## 10. 真机验收建议清单（P0）

1. 打开含 `hello **world**` 文档，搜 `hello world` → 有结果并滚到该段  
2. 搜链接 URL / 图片路径 → 无结果  
3. 搜中文、空格、标点  
4. 快速输入多字符 → 仅最终 query 生效  
5. 搜 A 文档后切 B → 无 A 结果残留  
6. 空搜索栏显示 `—`；无匹配 `0/0`；上/下禁用  
7. 返回键关搜索不退出阅读  
8. 截断：极重复字符文档计数 `2000+`（可选）  
9. 确认：**仅 block 定位，非字级**  

---

## 11. 已知限制（进真机前写明）

- 无块内精确定位  
- 无全部匹配同时字级高亮  
- 图片 alt 不搜（P0）  
- ViewModel 无 instrumentation 全覆盖  

---

## 12. 边界

未改 PDF/H1/Tile/External Open/R8；无 schema；未 commit/push。

---

## 最终结论

**APPROVED_FOR_DEVICE_ACCEPTANCE**
