# R25 Reader Foundation Closure — 收尾报告

**Date:** 2026-08-06
**Scope:** R25 阅读基座收尾（Reader Foundation Closure）。冻结横向功能扩展，对阅读界面做
只读审计（PdfViewerScreen、ReaderAdvancedCard / PdfExpandableMenu / AdvancedGeometry、
ChromeStateMachine / PdfViewerViewModel 状态源），输出 P0/P1/P2 问题清单并逐项关闭。
**Status:** 实现完成 + 自动化验证通过（unit 全量 PASS + lint PASS + assembleRelease PASS，
Release APK 10.95MB 已交付 Download）。**实机验收 PENDING（adb 离线）**。

---

## 0. 定位与判定

本次会话只读审计范围：
- `PdfViewerScreen.kt`（全量 3824 行通读）
- `ReaderAdvancedCard.kt` / `PdfExpandableMenu.kt` / `AdvancedGeometry.kt`（Renderer 形态）
- `PdfViewerViewModel.kt` + `ChromeStateMachine.kt`（状态源、事件流、single source of truth）
- `docs/PDF_VIEWER_HANDOFF.md`、`docs/PRD.md`、R25 系列报告作基线对照

定位方法：状态只以 `chromeStates`（ChromeStateMachine）+ `state.pageIndex`
（PdfViewerViewModel）为源，任何由 UI 重新推导的状态迁移一律视为缺陷。

---

## 1. 审计问题清单

| ID | 级别 | 问题 | 处置 |
|----|------|------|------|
| P0-1 | P0 | Advanced 卡来源行漂移：`parentRowTop` 用 `MenuVerticalPad + rowIndex*MenuRowHeight` 硬编码，与 InlineExpanded 展开的 96dp 折叠区、可滚动列表不符。Inline→Advanced 开卡时源 rect 瞬移 ~96–192dp，违反 R25.2 空间连续性 | 已修复（见 §2） |
| P1-1 | P1 | `PdfInfoCapsule` 展开 body 用 `AnimatedVisibility(expandVertically+fadeIn / shrinkVertically+fadeOut)` 双阶段动画（类 R25.2 已修掉的 section 白尾） | 已修复（见 §3） |
| P1-2 | P1 | 遗留死路径：`PdfVerticalZoomLockArea`(~795 行)、`PdfPageScrubber`(无调用点)、`PdfFastScroller`、`TwoFingerGestureArbitration`、VM `scrub`/`fastScroll` 方法 | 已删除（用户确认，见 §4） |
| P2-1 | P2 | `JumpPageBody` composition 立即 `focus.requestFocus()`，frame1 与 body morph 竞争 IME | 已修复：`delay(200)` 后申请 |
| P2-2 | P2 | 胶囊页码文字在真机上的居中态 | 待真机（未改，标注 pending） |
| P2-3 | P2 | 滚轮选择器（WheelPicker）是否存在 | grep 全库无 → **N/A**，不新建 |

---

## 2. P0-1 修复：Advanced 来源行用实测 rect，不再硬编码

**根因：** `PdfExpandableMenu` 的 `parentRowTop(rowIndex)` 用 `MenuVerticalPad + rowIndex*
MenuRowHeight` 硬推导行的 Y。该公式只在「全部折叠、无任何 inline 展开」时才与实测一致。
阅读方式/阅读界面两个 inline 区各自展开会占用 ~96dp，列表滚动也会改变行位置，因此从
InlineExpanded 状态点开的任何任务卡都会从一个悬空位置（行列上方）开始 morph。

**修改（`PdfExpandableMenu.kt` + `ReaderAdvancedCard.kt`）：**

1. `MoreMorphSurface` 根 Box 挂 `onGloballyPositioned { it }` 记录 `overlayCoords`，并新建
   `remember { mutableStateMapOf<Int, Float>() }` `taskRowTopPx`。
2. `MainMenuContent` 增加参数 `overlayCoords: LayoutCoordinates?` 与
   `onTaskRowTop: (rowIndex: Int, topPx: Float) -> Unit`；渲染任务行时用
   `Modifier.onGloballyPositioned { onTaskRowTop(rowIndex, it.localBoundingBoxOf(overlay).top) }`
   上报**实测** top（px），行位置随折叠/滚动实时更新。
3. `ReaderAdvancedCard` 来源参数由 `sourceRowTop: Dp` 改为 `sourceRowTopPx: Float`，
   调用点 `sourceRowTopPx = taskRowTopPx[rowIndex] ?: parentRowTop(rowIndex).toPx()`
   （实测优先，硬编码仅作首帧兜底）。删除自定义 `Density.toPx()`。
4. **父级几何冻结门禁：** `LaunchedEffect(state, advancedProgress.value)` 的 `else` 分支
   仅在 `advancedProgress.value <= 0f`（高级卡完全收起）时才折叠阅读方式/顶栏折叠区。
   即高级卡在 opening/open/collapsing 期间 parent 几何完全不变——"source 几何 unchanged"
   契约真正成立，源行不再边走边 morph。

**防复发测试（`R25ReaderFoundationTest.kt`，3 例）**：
- `taskRowTopPx` 上报的行 top 直接驱动高级卡起始 top（实测覆盖兜底）；
- 高级卡 float 期间 parent 折叠区保持组合、不提前折叠；
- `sourceRowTopPx: Float`（px 参数契约，杜绝 Dp 硬编码回潮）。

`ReaderMoreMorphSurfaceUiTest` 相应断言从「parent 折叠」改为「parent 区保持展开」。

---

## 3. P1-1 修复：胶囊 body 单 progress，消灭双时钟

**根因：** `PdfInfoCapsule` 的 body（阅读方式按钮体 / JumpPage）原用
`AnimatedVisibility(EnterTransition.expandVertically() + fadeIn(), ExitTransition.shrinkVertically() + fadeOut())`。
该双阶段系统在收起时先 shrink 后 fade，是 R25.2 已经在 SimpleInline 消灭的白尾同款问题。

**修改（`PdfViewerScreen.kt`）：**
- 删除双阶段系统，改单一 `Animatable bodyProgress ∈ [0,1]`：
  - `bodyNaturalHeightPx` 由 `reportNaturalHeight { it.toFloat() }` 实测；
  - body 区 `MeasureRect.height = naturalHeightPx * bodyProgress`，`clipToBounds`；
  - children 在 `bodyProgress > 0f` 或展开期间保持 composition（避免测量高度丢失）；
  - `LaunchedEffect(expanded) { snapTo(if ) 0 ; animateTo(1f, 200ms) / animateTo(0f, 180ms) }`。
- 保留外层 `capsuleVisible && !menuActiveLayer` 的端到端 fade（会议室场景），删除
  `expandVertically`/`shrinkVertically` 导入。

---

## 4. P1-2 删除：遗留路径全面清理

**P1-2 在用户确认下按「现在删除（推荐）」执行。**

| 删除内容 | 文件位置 |
|----------|----------|
| `PdfVerticalZoomLockArea`（~720 行） | `PdfViewerScreen.kt` |
| `PdfPageScrubber`（无调用点） | `PdfViewerScreen.kt` |
| `PdfFastScroller` | `PdfViewerScreen.kt` |
| `TwoFingerGestureArbitration`（产物仅旧 lock 区用） | `TwoFingerGestureArbitration.kt`（整文件） |
| `TwoFingerGestureArbitrationTest` | `R63TwoFingerGestureArbitrationTest.kt` |
| 锚定旧符号的测试 | `R61PersistentZoomSessionTest.kt` / `R62ZoomTargetHitTest.kt` / `R6PdfInteractionTest.kt` |
| `scrubPreviewIndex`、`fastScrollDragging` 状态，`onFastScrollPreview/Finished`、`onScrubPreview/Finished`、`cancelScrub` 方法，`displayPageIndex` | `PdfViewerViewModel.kt` |
| horizontal scrubbing 分册预览 UI 与 `state.scrubPreviewIndex` 读取 | `PdfViewerScreen.kt` |

### 4.1 ZoomPageOverlayMath 收拢
`ZoomPageOverlayMath.kt` 精简为：水平 tap 区 + `PagerRequestPolicy` + `OverlayRect` +
`PageTap`。`OverlayRect` 仍被 `ContinuousDocumentGeometry` / `ContinuousGestureContract`
使用（保留）。删除 `ContinuousZoomSession`/`VisiblePageTarget`/`hitTestPage`/
`targetScale`/`anchoredOffset`/`clampOffset`/`fitCenter`/`replaceBitmap`。

### 4.2 ViewModel 状态收拢
- `Ready` data class：删除 `scrubPreviewIndex`（`displayPageIndex` 内聚为 `pageIndex`）、
  `fastScrollDragging`。
- `pageLabel` / `printResolvePageIndex` / `deepestContaining` 去 `displayPageIndex`。
- builders（`publishCache`、`ReadyBase`）、`openDocument` / `switchReadingMode` /
  `onVerticalPageSettled` / `onHorizontalPageSettled` / `goToPage` /
  `schedulePrimaryHiRes` 全部去掉 scrub/fastScroll 参数；`goToPage` 守卫、2 处
  `prune` 守卫去 fastScroll；`jumpToHumanPage` 保留。
- `PdfViewerScreen` 同步删除对 `onFastScroll*` / `onScrub*` 的调用（PdfChrome→Capsule
  路径不接受这些参数）。

### 4.3 有效断言迁移
`R7ContinuousViewportTest.kt` 新增 ported 断言（新路径锚点）：
- 冷启动为白底非 spinner（无 `CircularProgressIndicator` / 背景为 `Color.White`）；
- 不包含 `scrollToItem` / `LazyColumn`（未回到旧锁定页路径）；
- 无 `lockedPage` / `ZoomPageOverlay` / `dispatchRawDelta`。

`PdfViewerScreen.kt` 行数：3824 → ~2966。

---

## 5. 测试与构建验证

| 项 | 结果 |
|----|------|
| `:app:testDebugUnitTest --offline` | **BUILD SUCCESSFUL**，977 testcases PASS |
| 新增测试 | `R25ReaderFoundationTest.kt`（P0-1 门禁 3 个）+ `R7ContinuousViewportTest`（迁移断言） |
| 删除测试 | R61 / R62 / R63 / R6PdfInteractionTest |
| `:app:lintDebug --offline` | EXIT=0 |
| `:app:assembleRelease --offline` | **BUILD SUCCESSFUL** → markleaf-release-latest.apk（10953239 bytes，SHA `a12b8355…`） |
| 交付 | `/storage/emulated/0/Download/markleaf-release-latest.apk`（覆盖旧版） |

---

## 6. 交付物协议对照

| 交付物 | 状态 |
|--------|------|
| 根因 + 删除/修改路径 | ✅ §2/§3/§4 |
| 最终状态模型与事件流 | ✅ 删除文档化（`Ready` 字段、事件签名、`PdfChrome` 前置） |
| UI/交互/动画逐项修复表 | ✅ §2/§3 |
| 性能前后对比 | ⚠️ 未做运行时压测（无真机）；静态成本降低：`PdfViewerScreen` 减少 ~860 行，单双时钟动画减少 |
| 测试与实机验收矩阵 | ✅ 自动化全绿；实机 pending（见 §7） |
| 未完成项 | §7 |
| 独立更新日志 | 本文档（`docs/R25_READER_CLOSURE_REPORT.md`），版本化构建记录见 `dist/APK_DELIVERY.md` |

---

## 7. 未完成 / PENDING（实机验收）

adb 离线、无 device，按 `docs/DEVICE_INSTALL_AND_ADB_TEST.md` 与 README 协议**不宣称**
device 验收通过、不做渲染静态截图对照。以下全部标注 pending：

1. **P0-1 实机**：四种 Advanced 卡（教材页码/导出章节/页面缩略图/教材目录）从
   InlineExpanded（阅读方式/阅读界面展开态）打开，源行与卡起点**精确贴合、无瞬移**；
   行在列表滚动位置也能贴合。
2. **P1-1 实机**：胶囊展开/收起无白尾（尤其收起那一帧），且与菜单层级协商一致。
3. **P2-1 实机**：JumpPage IME 弹出时机（200ms delay 在真机上的体感）。
4. **P2-2 实机**：胶囊页码文字居中。
5. 60fps 横向无瞬移视频（源 row → Advanced → 源 row）。

---

## 8. 备注

- 保险起见再次复核：`R7H1HorizontalZoomPanPhysics` / `R7H14UnifiedGestureController`
  等 R7 相关既有测试仍在、无英符号联动（本次未触碰）。`proguard` / build 不变。
- 本次全部改动**未 commit**（沿用提交状态 of repo as-is）；如需提交请明确告知。