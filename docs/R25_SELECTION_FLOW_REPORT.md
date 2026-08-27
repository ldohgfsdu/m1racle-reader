# R25 Selection & Annotation Interaction Flow 报告

**日期：** 2026-08-26
**性质：** 标注交互基础。零 Reader 架构改动、零 GlassMaterial 改动、零 Home 改动；
不实现完整编辑 UI。
**验证：** `:app:testQaUnitTest`（JDK17）**1301 tests / 0 failures / 0 errors**
（基线 1289 + 新增 12）。

---

## 1. SelectionModel（`ui/annotation/SelectionModel.kt`）

```
sealed class TextSelection
 ├─ PdfSelection(page, leftPx, topPx, rightPx, bottomPx, pageWidthPx, pageHeightPx, excerpt?)
 └─ MarkdownSelection(blockIndex, charStart, charEnd, excerpt?)
```

- **PDF**：手势像素矩形在**捕获点即归一化**（除以捕获时刻页面像素尺寸）——
  选择结果与缩放/密度天然无关，是 R24 归一化不变性的上游保证；
  零面积/倒序矩形无效；页面尺寸必须为正。
- **Markdown**：块索引 + 半开字符区间 [charStart, charEnd)；空区间/倒序无效。
- `toAnchor(): DocumentAnchor?`：非法选择返回 null（不带病进入存储层）。

## 2. AnnotationCreationFlow（状态机）

```
IDLE ──select(合法)──→ SELECTION_READY ──prepare(type[,content])──→ DRAFT_READY
 ↑                        │ select(非法)→FAILED(INVALID_SELECTION)      │ commit()
 └─── cancel() ───────────┴─ prepare(NOTE 空内容)→FAILED(EMPTY_NOTE)────┘→ COMMITTED → IDLE
```

实现要点：
- `select` 在入口就解析锚点（非法直接失败，不带病前进）；NOTE 内容 trim 后非空校验；
- **终态语义**：Committed/Failed 作为返回值交付，内部立即回 Idle——可观察、
  不留脏态、天然幂等（重复 commit → Failed(ALREADY_COMMITTED)，不重复落库）；
- 持久化只发生在 `commit()` 一步：经 ReadingRepository 的 anchor-based API
  （R24），投影列由解析器单点派生。

## 3. TransientSelectionHolder

纯内存瞬时选择持有器：set/clear 语义，无任何 Room/DataStore 路径；
宿主负责在翻页/滚动越界/离开组合时 clear。

## 4. Overlay 数据契约（仅契约，无绘制实现）

```kotlin
AnnotationOverlayItem(id, type, anchor, excerpt)
  .pdfRectNormalized(): NormalizedRect?   // × 当前页渲染尺寸 = 屏幕矩形
  .markdownRange(): Pair<Int, Int>?       // 块内字符区间

interface AnnotationOverlaySource {
    suspend fun itemsFor(documentId, locationKind, location): List<AnnotationOverlayItem>
}
RepositoryAnnotationOverlaySource   // 默认实现：过滤 kind+location 并恢复锚点
```

## 5. 测试（新增 12）

| 文件 | 数量 | 覆盖 |
|---|---|---|
| `SelectionAnchorConversionTest.kt` | 5 | PDF 转换数值正确性、**同一视觉区域多尺寸捕获 → 同一锚点**、MD 区间保留、非法选区拒绝（MD 空/倒序 + PDF 零面积/倒序）、瞬态持有器清空即消失 |
| `AnnotationCreationFlowTest.kt` | 5 | PDF 高亮全链路（落库字段+anchor 列+仓库恢复往返）、NOTE 空内容拒绝→重走→trim 落库、非法选择三段封锁、cancel 任意阶段重置、双 commit 幂等防护 |
| `AnnotationOverlayContractTest.kt` | 2 | (kind,location) 过滤与锚点恢复、PDF 归一化矩形/MD 区间只读映射、未知 kind 安全空快照、NOTE 无矩形安全 null |

## 变更面

```
A ui/annotation/{SelectionModel,TransientSelection,AnnotationCreationFlow,AnnotationOverlay}.kt
± 测试 ×3 新增
```

生产 Reader/Home/GlassMaterial 文件零触碰（SHA 复核一致）。

## 未做项（明确声明）

- 手势捕获接入（PDF 长按选区 / MD 文本选择）与覆盖层绘制属后续 UI 任务——
  本轮交付其全部输入输出契约；
- 选区摘录自动提取（excerpt 目前由调用方传入）；
- device_pending：无（本轮无可视变更）。
