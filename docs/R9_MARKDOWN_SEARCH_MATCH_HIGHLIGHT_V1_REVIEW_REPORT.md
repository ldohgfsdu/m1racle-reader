# R9 Markdown Search Match Highlight V1 — 独立审查报告

**Object:** `R9_MARKDOWN_SEARCH_MATCH_HIGHLIGHT_V1`  
**Date:** 2026-07-16  
**Baseline HEAD:** `a7da58c` (P0_DEVICE_ACCEPTED)  
**实现状态:** 未 commit 工作区  
**审查方式:** 直接读生产代码 + 补测 + 门禁，不默认相信实施报告  

---

## 结论

### **APPROVED_FOR_DEVICE_ACCEPTANCE**

P1 字符级高亮实现与设计一致；offset 映射、整块黄退出、重组隔离与样式叠加在代码与 JVM 证据下可成立。  
**未 commit**；可进入真机字符高亮验收（非 P2 精滚）。

---

## 一、生产调用链（有文件证据）

```
ViewerViewModel._searchState
  matches / currentIndex / isActive / documentKey
    ↓
ViewerScreen.ViewerSuccessScreen
  searchState.collectAsStateWithLifecycle()
    ↓
ReaderDocumentBody (ViewerScreen.kt ~512–680)
  remember(isActive, documentKey, matches) {
      groupMatchesByBlockIndex(matches)   // SearchHighlight.kt
  }
  activeStableKey = currentMatch?.stableKey
    ↓
LazyColumn itemsIndexed(
  key = documentId:blockId,
  contentType = block.blockContentType()
)
  blockMatches = matchesByBlock[index]           // 不扫全表
  activeKeyForBlock = only if active block
  blockHighlightsFromGrouped(...)                // O(本 block hits)
    ↓
MarkdownBlockView(searchHighlights)
    ↓
Paragraph/Heading/List/Quote/Code/Table
  InlineMarkdownParser.parse | CodeHighlighter
  AnnotatedString.withSearchHighlights(...)
    ↓
Text / ClickableText
```

| 要求 | 证据 | 判定 |
|------|------|------|
| 每 block 不扫完整 matches | item 仅 `matchesByBlock[index]` | **PASS** |
| group 仅在 matches 集合变化 | `remember(..., searchState.matches)`，不含 `currentIndex` | **PASS** |
| active 变不重新 group | 同上 + 测试 `groupMap_reusedAcrossActiveKeys` | **PASS** |
| 关闭/空 matches 无 span 构造 | `!isActive \|\| matches.isEmpty() → emptyMap()`；`withSearchHighlights` early return | **PASS** |
| 文档切换清理 | ViewModel per `documentId`；`closeSearch`/`openSearch` 重置；`documentKey` 门禁 | **PASS** |
| 旧 documentKey 不污染 | DocumentSearch 结果 + ViewModel generation/key 检查（P0） | **PASS** |
| 稳定 key/contentType | `itemsIndexed` key + `blockContentType()` | **PASS** |
| 整块黄退出搜索路径 | 生产侧无有效 `searchBlockHighlight` 调用；API 为 **identity no-op** | **PASS** |

**残留：** `SearchHighlightStyle.searchBlockHighlight` 仍为 no-op 函数（防旧调用）；**无生产副作用**。

---

## 二、Offset parity

| 类型 | 搜索文本 | 渲染 | 审查 |
|------|----------|------|------|
| 段落 | `toPlainText` | `parse` + overlay | parity 测试 PASS |
| 粗体 `hello **world**` | plain `hello world` | 全范围高亮 + bold 保留 | PASS |
| 链接 label | plain label only | annotation 保留 | PASS |
| 隐藏 URL | 不进索引 | — | PASS |
| 行内代码 | plain 含 code 字符 | fontFamily span 保留 | PASS |
| 标题 | plain | **P1 改为 `parse`+overlay**（修 P0 raw 错位） | PASS |
| 列表 | 每 item plain | 仅正文 `forListItem`；bullet 不高亮 | PASS |
| 引用 | 整段 plain | 有命中时单 Text 保 offset | PASS（有意降级 attribution 拆分） |
| 代码块 | `block.code` 1:1 | CodeHighlighter 全文 append + overlay | PASS |
| 表格 cell | cell-local + `listItemIndex` ordinal | 与 parser `padCells` 矩形对齐 | PASS（非 NEEDS_TABLE_MAPPING_FIX） |
| emoji | UTF-16 2 units | 双命中 + 不截断 | PASS |
| 非法 range | skip | 不 coerce | PASS |

**表格细节：**

- Extractor：`listItemIndex` = cell ordinal（header 先行，再 row-major；空 cell 仍 `cellOrdinal++`）。  
- UI：`rowBaseOrdinal = columnCount + rowIndex * columnCount`。  
- Parser：`padCells` → 矩形表。  
- 重复 `"same"` 两 cell：两个 match、不同 ordinal、active 可单独命中第二格 — 测试 `table_duplicateCellText_usesOrdinalNotSharedOffset`。

---

## 三、Span 叠加与交互

`withSearchHighlights`（SearchHighlight.kt）：

1. `AnnotatedString.Builder(this)` 拷贝既有 span + **StringAnnotation**  
2. 普通 match 先 `addStyle(background=normal)`  
3. active 后加，重叠时 active 视觉优先  
4. **不**改文本内容  

颜色（SearchHighlightStyle.kt）：

| 主题 | tertiaryContainer | normal α0.38 | active α0.82 |
|------|-------------------|--------------|--------------|
| Light Paper | `#FFF1B8` | 浅黄可读 | 更亮黄，onSurface 仍可读 |
| Dark Ink | `#3A3520` | 暗金底 | 更高不透明，onSurface 浅字仍可读 |

**风险（非阻断）：** 极高对比主题下 α0.82 可能偏抢眼；真机浅/深各确认一次即可。

链接：`ClickableText` + `LINK_ANNOTATION_TAG` 在 overlay 后仍可查 — 测试 PASS。  
代码块：header/复制钮不进高亮；body 保留 syntax + 搜索底。

---

## 四、同 block / 跨 block active

| 场景 | 行为 | 判定 |
|------|------|------|
| 同段两次「临床检索验证词」 | 两 range；active 仅一；不 rescan | PASS（JVM） |
| next 同 block | 只改 isActive 标志；grouped map 复用 | PASS |
| 跨 block TOKEN | 旧/新 block active 翻转；位置不变 | PASS |
| 相同文字多处 | `stableKey` 含 start/end 唯一 | PASS |

**Compose 重组说明：**  
`currentIndex` 变化会让 `ReaderDocumentBody` 重组，但：

- **不**调用 `DocumentSearch.search`  
- **不**重跑 `groupMatchesByBlockIndex`（matches 引用不变）  
- 非 active 且无 hits 的 item：`searchHighlights=null` 稳定  
- 有 hits 的非 active block：`activeKeyForBlock=null`，`BlockSearchHighlights` 内容相等可跳过子重组  

真机需确认无「全列表闪一下」观感。

---

## 五、典型门禁汇总

| # | 用例 | 结果 |
|---|------|------|
| 1 | `hello **world**` / `hello world` | PASS |
| 2 | Visible Link / URL 隐藏 | PASS |
| 3 | 行内代码 | PASS |
| 4 | emoji `😀` ×2 | PASS |
| 5 | 重复 `xx` stableKey | PASS |
| 6 | 空 matches / inactive | PASS |
| 7 | 表格映射 | PASS |

---

## 六、性能与 smoke

| 项 | 结果 |
|----|------|
| 搜索关闭路径 | 无 group、无 withSearchHighlights（empty） |
| next 不重搜 | ViewModel 仅 `currentIndex++` |
| 设备 smoke | 安装 QA，主页连续 swipe×5；**无 FATAL/ANR** |
| 完整性能 Goal | **未重跑**（符合范围） |

**未自动化：** 500 命中下的 Compose recomposition 计数器（无生产埋点）；逻辑上满足 O(hits_in_block) 而非 O(blocks×matches)。

---

## 七、自动门禁

| 命令 | 结果 | 耗时 |
|------|------|------|
| `./gradlew :app:testDebugUnitTest --tests 'com.markleaf.app.search.*' ... markdown.* packageimport.* share.* image.* DocumentSearchUiStateTest` | **PASS** | ~15–55s（含后续 assemble） |
| SearchHighlightTest（含补齐用例） | **PASS** | 含在上 |
| `lintDebug` | **PASS** |  |
| `assembleDebug` | **PASS** |  |
| `assembleQa` | **PASS** |  |
| 全部门禁批 | **BUILD SUCCESSFUL ~54s** |  |

补齐测试（本审查轮）：跨 block active、stableKey、表格 duplicate/empty ordinal、group 复用、inline code、hidden URL、empty highlights。

**无 instrumentation** 专用 Search UI test（仓库无既有 androidTest 覆盖本对象）。

---

## 八、边界检查

**P1 相关 diff（意图）：**

- `SearchHighlight.kt`、`ViewerScreen.kt`、各 `*BlockView`、`InlineMarkdownText`、`SearchHighlightStyle`  
- `SearchHighlightTest.kt`（新）  
- 实现/审查报告 docs  

**工作区另有既有脏文件（非本 P1 引入，未纳入审查通过范围）：**  
`docs/PDF_VIEWER_HANDOFF.md`、`docs/R7_H1_*`、`gradle.properties`、`dist/*` 等 — **保持原样，未为审查修改。**

| 冻结域 | 代码改动 |
|--------|----------|
| PDF / H1.5A / Tile / ThreePageStrip | **无** |
| External Open / R8 | **无** |
| schema | **无** |
| P2 精滚 | **无** |
| commit/push | **无** |

---

## 九、缺陷与最小处理

| 发现 | 严重度 | 处理 |
|------|--------|------|
| `searchBlockHighlight` 仍为 no-op API | 低 | 保留无害；真机后可删 |
| Quote 有命中时不拆 attribution | 低/已知 | 正确性优先；可接受 |
| 父级随 `searchState` 重组 | 信息 | 非全表 group/search；真机观察 |
| Compose 无 recompose 计数测试 | 中低 | 逻辑证明 + 真机验收 |

**审查轮未发现必须阻断的生产缺陷；补测已合入工作区。**

---

## 十、真机验收建议清单

1. 同段两次「临床检索验证词」：仅词语高亮；1/2↔2/2 可区分；**无整段黄**。  
2. `hello **world**`：整词高亮 + world 仍粗。  
3. 链接 label 高亮后仍可点。  
4. 代码块内匹配字符高亮，复制仍可用。  
5. 表格两 cell 同字：next 只加强当前 cell。  
6. 关闭搜索 / 空 query：高亮全消。  
7. 搜索关大文档快滑 + 有结果快 next：主观无卡死/ANR。

---

## 最终判定

```text
APPROVED_FOR_DEVICE_ACCEPTANCE
```

**不**进入 P2。  
**不** commit（按任务）。  
下一步：真机字符高亮人工验收 → 通过后再考虑 checkpoint。
