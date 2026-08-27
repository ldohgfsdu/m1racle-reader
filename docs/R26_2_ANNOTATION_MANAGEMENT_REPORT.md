# R26.2 Annotation Management 报告

**日期：** 2026-08-26
**性质：** 标注管理层。Anchor 协议零改动；GlassMaterial 零改动（SHA 复核一致）；
Home 零改动；无 Note 编辑。
**验证：** `:app:testQaUnitTest`（JDK17）**1309 tests / 0 failures / 0 errors**
（基线 1305 + 新增 4）。

---

## 1. AnnotationList 面板

- 入口：More 菜单新增「标注列表」行（`Icons.Outlined.Notes`，closeOnClick），
  打开 `AnnotationsManagementSheet`（**ModalBottomSheet**——独立于 More morph
  体系，不触碰 ReaderMoreState 机器与行索引 Child 锚定契约）。
- 内容：当前文档全部高亮，按 **location ASC → createdAt ASC** 排序（与 DAO SQL
  同序，Fake 与生产一致）；每行显示「第 N 页」（1-based 人读页）、摘录（≤2 行）、
  创建时间（同日 HH:mm / 历史 yyyy-MM-dd）；空态文案。
- 数据通道：`ReaderHighlightController.listForDocument()` → Repository；
  UI 不直访 DAO（权威钉沿用 R23 结构测试）。

## 2. 跳转 Anchor

点击列表行 → 关闭面板 → `viewModel.goToPage(row.location)`（复用 TOC 同款跳转
管线）。location 即锚点所在 0-based 页；`resolveHighlightAnchor` 恢复完整
PdfPage 锚点（含矩形），测试锁定「row.location == anchor.page」恒等。

## 3. 删除 Highlight

- 列表行删除按钮：`deleteAnnotation(id, page)` → `controller.deleteHighlight(id)`
  （→ Repository.removeHighlight）→ `annotationListVersion++` 触发面板重查 +
  `forceLoadHighlights(page)` 刷新该页覆盖层 → snackbar「已删除标注」。
- UI 全程只经 Controller/Repository；DAO 零直访。

## 4. Overlay Selected State

`PdfHighlightLayer` 升级：
- **点按选中**：tap（移动 ≤ touchSlop 且抬起）命中高亮矩形 → 选中态
  （填充加深 + 描边加强 F57C00 3.5f）；点空白取消选中且**不消费事件**
  ——chrome 点按行为不受影响；
- **删除角标**：选中时右上角绘制 ✕ 圆标（白底红面），命中圆域回调
  `onDeleteRequested(itemId, page)`；事件被消费；
- **滚动放行**：移动超 touchSlop 的手势完全不干预（原样交给滚动系统）；
- 长按拖选路径不变；项集变化自动清理失效选中。

## 测试（新增 4，`ui/reader/AnnotationManagementTest.kt`）

| 用例 | 覆盖 |
|---|---|
| 列表查询排序 | 跨页+同页乱序创建 → location ASC, createdAt ASC |
| 跳转 anchor | row.location == anchor.page 恒等 + 矩形随锚点恢复 |
| 删除后消失 | 列表与 overlay 双清空 |
| 跨页面恢复 | 各页覆盖互不可见；新 OverlaySource 实例（模拟重开）仍读全量 |

## 变更面

```
M ui/annotation/PdfHighlightGeometry.kt   (+hitTest / deleteBadgeHit)
M ui/annotation/PdfHighlightLayer.kt      (点按选中态 + ✕ 角标 + 滚动放行)
A ui/annotation/AnnotationsManagementSheet.kt
M ui/reader/{ReaderHighlightController}.kt (+listForDocument)
M ui/pdf/PdfViewerScreen.kt               (菜单行/面板状态/删除跳转接线)
M res/values/strings.xml                  (+5)
± 测试 ×1 新增文件
```

## 未做项（明确声明）

- **device_pending**：Sheet 手感、选中态视觉、✕ 命中区真机尺寸、与长按拖选的
  并发边界实拍。
- Note 编辑/展示不在本轮；面板仅列 Highlight（Note 数据层已就绪，后续任务接入）。
- 面板未做搜索/筛选（单文档高亮量级小，保持轻量）。
