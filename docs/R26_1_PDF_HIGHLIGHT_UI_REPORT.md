# R26.1 PDF Highlight UI Integration 报告

**日期：** 2026-08-26
**性质：** 第一个真实标注闭环。GlassMaterial/Anchor 协议/Home 零改动；阅读器核心
渲染逻辑零改动（仅增量挂载点）。
**验证：** `:app:testQaUnitTest`（JDK17）**1305 tests / 0 failures / 0 errors**
（基线 1301 + 新增 4）。GlassMaterial.kt SHA 复核一致。

---

## 闭环链路

```
长按拖选（页面局部坐标）                    ← PdfHighlightLayer 手势
   → PdfHighlightGeometry.selectionOrNull    （门槛过滤 + 倒序归一）
   → TextSelection.PdfSelection.toAnchor()   （捕获点即归一化，R25）
   → AnnotationCreationFlow select→prepare→commit   （R25 状态机）
      → ReadingRepository.addHighlight       （R24 anchor-based API）
   → RepositoryAnnotationOverlaySource.itemsFor(PAGE_INDEX, page)
   → AnnotationOverlayItem.pdfRectNormalized() × 当前渲染尺寸 = 屏幕矩形
```

## 实现

### 新增

| 文件 | 职责 |
|---|---|
| `ui/annotation/PdfHighlightGeometry.kt` | 纯几何：拖拽起止点 → 合法选区（8px 边缘门槛 + 归一化面积门槛）；`toScreenRect` 恢复端换算 |
| `ui/annotation/PdfHighlightLayer.kt` | 页面级覆盖层 composable：单指长按拖选手势（实时预览矩形）+ 已存高亮按归一化 × onSizeChanged 尺寸绘制 |
| `ui/reader/ReaderHighlightController.kt` | 编排门面：内部驱动 AnnotationCreationFlow；itemsForPage / commitHighlight / deleteHighlight |

### 接线（全部增量、可选参数，null = 能力关闭）

- `ContinuousDocumentViewport`/`DocumentPage`：新增 `pageAnnotation: @Composable
  BoxScope.(Int) -> Unit` 槽位，渲染在页面 Canvas **之后**（内容之上）；
  行索引契约（2–5 Child 锚定等 R24/R22 结构）不受影响。
- `PdfHorizontalPagedArea`：同样槽位插入横向页面 Box 内 tiles 之后。
- 两处页面 lambda：`LaunchedEffect(page){ loadHighlightsIfNeeded(page) }`
  （懒加载守卫）+ 提交后 `forceLoadHighlights(page)` 刷新当前页。
- NavGraph PDF 路由构造 `ReaderHighlightController(readingRepo, documentId)`
  并传入屏幕；屏幕新增可选参数默认 null。

### 手势与既有交互的隔离

- `awaitLongPressOrCancellation`：第二指针加入或移动越过 touch slop 即取消
  ——pinch 缩放（R7 三层焦点契约）与滚动/fling 零干扰；
- 拖拽期间消费指针仅在长按触发之后；最小面积/边缘双门槛滤除误触。

## 高亮三保证的实现依据

| 保证 | 机制 |
|---|---|
| 缩放不漂移 | 锚点在捕获时归一化；显示端 `normalized × 当前渲染尺寸`——比例恒定，任何 scale 下几何一致（测试 §2 以 1×/3× 及任意尺寸验证逐分量相等） |
| 翻页不残留 | items 按 pageIndex 键控于 `mutableStateMapOf<Int,…>`；每页只读自己的键，离屏页面组合销毁即无残留；跨页数据互不可见 |
| 重开可恢复 | 持久化在 Room highlights 表（anchor 列权威）；页面组合时经 OverlaySource 从仓库重载（懒加载守卫防重复查询） |

## 测试（新增 4）

`ui/reader/PdfHighlightLoopTest.kt`：
1. **完整链路**：selection → flow commit → repository 行字段（PAGE_INDEX/location/
   anchor 列前缀 `pdf:9|`）→ overlay item 的 pdfRectNormalized == 选择时锚点；
2. **多尺寸恢复**：同一锚点在 595×842 / 1785×2526 / 300×500 三种渲染尺寸下
   换算的相对几何逐分量相等；
3. **删除高亮**：controller.deleteHighlight → overlay 快照为空；
4. 微小误触拖拽被门槛过滤（不落库）。

## 变更面

```
A ui/annotation/{PdfHighlightGeometry,PdfHighlightLayer}.kt
A ui/reader/ReaderHighlightController.kt
M ui/pdf/ContinuousDocumentViewport.kt     (+pageAnnotation 槽位穿透)
M ui/pdf/PdfViewerScreen.kt                (+highlightController 参数/状态/两处挂载)
M ui/navigation/NavGraph.kt                (PDF 路由注入控制器)
M res/values/strings.xml                   (+2)
± 测试 ×1 新增文件
```

## 未做项（明确声明）

- **device_pending**：长按拖选手感、高亮颜色观感、连续/横翻两模式实拍、与 pinch
  共存的真机验证（手势隔离已用 awaitLongPressOrCancellation 取消语义从结构上保证，
  但仍需实机确认）。
- 高亮删除的 UI 入口未做（本轮覆盖层只读；repository/controller 删除 API 与测试
  已就绪，随编辑 UI 任务接入）。
- Note 创建入口未做（任务边界明确排除）。
