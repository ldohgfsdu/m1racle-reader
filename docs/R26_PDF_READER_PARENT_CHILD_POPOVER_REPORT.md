# R26 — 固定 ParentCard + 原位 morph Foreground ChildCard 交付报告

**Date:** 2026-08-07
**Scope:** 重做 PDF Reader 右上更多菜单及 4 个高级二级界面为「两层浮动卡片 popover」。
**Status:** implemented + automated_verified（971 unit PASS / lint PASS / assembleRelease PASS，APK 已交付 Download）。
**Device 5×截图 + 4×慢速录屏: PENDING**（`adb devices` empty — 不得宣布实机验收完成）。

---

## 1. 修改过的文件清单

| 文件 | 改动 |
|---|---|
| `app/src/main/java/com/markleaf/app/ui/pdf/ReaderChromeGlass.kt` | 单一材质 token：主题感知 hairline border、elevation 分层、muted alpha 统一入口 |
| `app/src/main/java/com/markleaf/app/ui/pdf/ReaderMoreHost.kt` | ParentCard 尺寸/字体/图标列 token；source row 单份内容占位；移除 backButtonRightRoot |
| `app/src/main/java/com/markleaf/app/ui/pdf/AnchoredNestedPopover.kt` | leftExpand 96dp、minLeft 16dp、body 淡入/收起先淡 body、pointer 消费、删除 autoFocus 参数 |
| `app/src/main/java/com/markleaf/app/ui/pdf/ReaderAdvancedCard.kt` | 设置页码无自动 focus + placeholder + 56/16 输入框；目录/缩略图/导出 body 规格对齐 r26 §9–§12 |
| `app/src/main/java/com/markleaf/app/ui/pdf/PdfViewerScreen.kt` | 左右边距 12→20dp；文档切换关闭 child；删除 backButtonRightRoot 传递 |
| `app/src/main/java/com/markleaf/app/pdf/AnchoredNestedGeometry.kt` | minLeft 16dp 替代 back 约束；全 LTRB 线性插值（删 height-first 分段）；移入 initialRow 助手 |
| 测试：`AnchoredNestedGeometryTest` / `AdvancedOriginFidelityTest` / `ReaderMoreHostUiTest` / `MotionContinuityGeometryTest` / `AdvancedGeometryTest` | 锚定新 API |

## 2. 旧错误实现删除清单

| 删除项 | 位置 | 替代 |
|---|---|---|
| `AdvancedGeometry.kt`（`advancedMorphRect` / `computeAdvancedTargetRect` / `rootRectToOverlay` / `RootAdvancedRect` 等整套 dead 几何） | 全仓 | `AnchoredNestedGeometry.kt`（唯一生产几何） |
| `AdvancedCoordinateContractTest.kt`（锚旧坐标转换） | test | 删除 |
| `ReaderAdvancedCard` 死 import（AdvancedGeometryInput / advancedMorphRect / computeAdvancedTargetRect） | ReaderAdvancedCard.kt | 删除 |
| `backButtonRightRoot` / `backGapPx` 参数链（PdfViewerScreen → Host → Popover → Geometry） | 4 文件 | 固定 16dp 屏幕左边距 |
| `autoFocus` 参数 + 展开后自动 `focus.requestFocus()` | AnchoredNestedPopover / ReaderAdvancedCard | 彻底删除（r26 §9） |
| height-first 分段 morph（`NestedHorizontalStartP`） | AnchoredNestedGeometry | r26 §4/§7 全 LTRB 线性插值 |
| R25.3 phase-1 的「仅 TOC 真实 body、其余 no-op」 | — | 四 body 全量启用（active_object 前序已完成，本轮保留） |

## 3. ParentCard / ChildCard 当前架构

```
PdfChrome root Box (boundsInRoot 唯一坐标系)
└── ReaderMoreHost (fillMaxSize when active)
      ├── ParentCard (常驻, TopEnd anchor, 宽 232dp)
      │     └── MainMenuContent: 6 行, 每行 onGloballyPositioned → rowRectsRoot
      │          阅读方式/阅读界面 → INLINE 卡内展开
      │          设置页码/目录/缩略图/导出 → CHILD_*（row 保留占位 contentAlpha=0）
      └── AnchoredNestedPopover (独立 overlay, 唯一 ChildCard shell)
            ├── NestedSourceHeader (icon + title + chevron-down, 52dp)
            └── NestedChildBody(state) → 4 个真实 body
```

- `ReaderMoreState` 等价 sealed AdvancedMenuItem（CLOSED/MAIN/INLINE_*/CHILD_*）；`rowRectsRoot: Map<Int, Rect>` 等价 sourceBounds map。
- ChildCard 是独立 overlay，不是 ParentCard Column 内的内容块；ParentCard bounds 全程冻结（x/y/w/h 不变，测试 G 项覆盖）。
- 无 Popup / Dialog / BottomSheet / 第二 Window / full dim。

## 4. 统一材质 token 实际参数（ReaderChromeGlass.kt）

| Token | 值 |
|---|---|
| `ReaderGlassBaseAlpha` | 0.82（Child +0.03 → 0.85，均 clamp 0.78–0.86） |
| border | 1dp：浅色 `Color.Black α=0.06` / 深色 `Color.White α=0.10`（主题感知，仅在此文件计算） |
| elevation | Chrome 6dp / Main 10dp / Child 14dp（r26: 主卡 8–12、Child 12–16） |
| contentColor | `colorScheme.onSurface`（浅 ≈#111 系 / 深 ≈#F3F3F3 系） |
| contentMutedAlpha | 浅 0.62 / 深 0.75（55–65% 黑 / 70–78% 白） |
| corner 族 | Parent/Child 终态 28dp；圆 22dp；Child corner morph 14→28dp |

所有浮层（返回圆、三点圆、ParentCard、ChildCard）经 `readerChromeGlassSpec(level)` 唯一入口。

## 5. 4 个 source row 如何记录 bounds

- `MainMenuContent` 中每个 task row（index 2..5）挂 `onGloballyPositioned { onRowBoundsRoot(rowIndex, coords.boundsInRoot()) }`。
- 点击行 → `onOpenChild(state, CHILD_*)` → `LaunchedEffect` 把 `rowRectsRoot[childRowIndex(state)]` 冻结为 `frozenSourceRoot`，同时冻结 `frozenParentRoot`（Main 卡 bounds），**child 身份即状态**（无 origin fallback）。
- 收起时以同一 frozen source 为目标；四个 child 各自回自己行（TOC 回 TOC、THUMB 回 THUMB…）。

## 6. 展开/收起动画参数

| 参数 | 值 |
|---|---|
| 展开 | 200ms（190–220 带内） |
| 收起 | 180ms（160–200 带内） |
| easing | `MoreMorphEasing = CubicBezierEasing(0.2,0,0,1)`（无 overshoot / 无 spring） |
| 几何 | 全 LTRB 线性插值 + corner 14→28dp + clip bounds；right edge 恒定 = Parent right |
| body 内容 | 展开：alpha 0.30→0.80（r26 0.25–0.35 开始、0.7–0.85 前完成）+ translateY 6dp→0；收起：body 在 p 0.45 前先淡出，容器再收回 |
| 状态机 | 单一 `Animatable(progress)`；开/关同轨迹（same p → same rect，测试覆盖） |

## 7. IME focus 修复

- **删除** `TextbookPageAdvancedContent` 内 `LaunchedEffect { delay(MorphExpandMs+60); focus.requestFocus() }` 及 FocusRequester —— 之前「一展开就弹键盘」的根因。
- 打开设置页码 / 导出 ChildCard：不 requestFocus、不弹键盘、无光标；首焦点留在 overlay/root。
- 仅用户点击输入框时 BasicTextField 原生请求焦点（数字键盘）。
- 关闭 ChildCard：`focusManager.clearFocus(force=true)` + `keyboard.hide()`（closing 分支既有，保留）。

## 8–10. 实机截图 / 慢速录屏 / 逐帧检查

**PENDING** — `adb devices` 为空。adb 恢复后执行：

```bash
adb install -r /storage/emulated/0/Download/markleaf-release-latest.apk
# 5 张截图: MAIN 默认 / 设置教材页码 / 教材目录 / 页面缩略图 / 导出章节
# 4 条慢速录屏: Main→PageMapping→Main, Main→TOC→Main, Main→Thumbs→Main, Main→Export→Main
# 逐帧 0/25/50/75/100%: 检查重影 / 横向 pill / 坐标跳变 / 材质切换 / 白闪
adb logcat -s MORE_SURFACE   # Main top/right 在 child 展开前后恒等
```

## 11. 构建 / 测试结果

- `:app:testDebugUnitTest` — **971 PASS**（102 suites, 0 failures；含 AnchoredNestedGeometryTest 11、MotionContinuityGeometryTest 8、AdvancedOriginFidelityTest 6、ReaderMoreHostUiTest、ReaderMoreStateMachineTest、R25ReaderFoundationTest 等）
- `:app:lintDebug` — PASS（EXIT=0）
- `:app:assembleRelease` — PASS
- APK: `.external_outbox/markleaf-release-latest.apk` → `/storage/emulated/0/Download/markleaf-release-latest.apk`
- SHA-256: `3f72198ec75828c8656a61abdee8a13ac88b070ab99e577d7bfce6c50aef23ad`

## 关键回归门禁（automated）

- morph p=0 帧恒等于各自来源 row（4 行不同 Y，无公共 anchor）；
- 全 LTRB 连续插值：任意采样帧四边都在 source→target 轨迹上、right 恒定、无 bottom-only / 横条相位；
- 四个 CHILD_* 各映射 row 2..5；收起目标 = 各自 source；
- 无 `parentContentAlpha` / `backButtonRightRoot` / dead 几何符号残留（结构门禁）；
- 单份内容：`contentAlpha=0` 占位在 MainMenuContent 中按 `childRowIndex(state)` 生效。
