# R25.1 — Reader More Menu Architecture Correction (Single Surface)

**Date:** 2026-08-07  
**Scope:** 删除错误的 advanced-foreground + parent-behind 双层模型；收敛为任意时刻只存在一个主要 ReaderMore surface。  
**Status:** implemented + automated_verified。**实机六条链路录屏 PENDING（adb 无 device）。**

---

## 1. 硬规则（本轮验收门禁）

| 门禁 | 实现 |
|---|---|
| 任意时刻只存在一个主要 ReaderMore surface | `showMainSurface` XOR `showAdvancedSurface`；advanced morph 期间 MAIN 不入组合树 |
| INLINE（阅读方式/阅读界面）只在 MAIN 卡内 accordion | 无第二 card/popup/overlay |
| ADVANCED replace/morph | sibling 淡出 → sourceBounds→targetBounds 扩展 → MAIN 从视觉树消失 |
| 关闭严格逆向回各自来源 row | 状态在逆变换期间保持 `ADVANCED_*`；`advancedRowIndex` 固定 2..5 |
| 禁止 parent-content-alpha / dual surface / L 型 | `ParentContextAlpha` / `parentRecede` / 父层 blocker **已删除** |
| target 不横跨全屏、不侵入返回圆 | 宽 276–292dp（默认 284），右对齐 MAIN；左边界受 back-clearance 约束 |
| 材质 alpha 0.88–0.92 | `readerChromeGlassSpec` 默认 `0.90f` |
| INLINE child 与 title column 对齐 | `OptionIndent = pad + icon + gap`（50dp，无 +20 漂移） |

**未改：** PDF renderer、滚动方向状态机、返回圆、中间胶囊、阅读 chrome 逻辑。

---

## 2. 状态机（保持 8 态，语义收紧）

```
CLOSED ↔ MAIN ↔ INLINE_READING_MODE / INLINE_READING_UI
MAIN / INLINE_* → ADVANCED_* → (reverse morph) → MAIN → CLOSED
```

| State | Surface |
|---|---|
| CLOSED | 44dp 三点圆 |
| MAIN / INLINE_* | **唯一** MAIN 卡（inline 只改高度） |
| ADVANCED_* + sibling fade 阶段 | **唯一** MAIN 卡（非 source row alpha→0） |
| ADVANCED_* + morph/open/close | **唯一** advanced surface（MAIN 不组合） |

---

## 3. 打开 / 关闭时序

### Open（MAIN → ADVANCED）

1. 点击 task row：同步捕获 `sourceBounds`（overlay-root 全 Rect）
2. **Phase A**（~80ms，`SiblingFadeMs`）：MAIN 仍为唯一 surface；sibling rows 淡出，source row 保持
3. **Phase B**（190ms，`AdvancedMorphMs`）：MAIN 从树中移除；advanced 从 `sourceBounds` 连续 morph 到 `targetBounds`；body 在 p≥0.40 后出现
4. p=1：视觉树仅 advanced surface

### Close（ADVANCED → MAIN）

1. `advancedClosing=true`，状态保持 `ADVANCED_*`
2. advanced reverse morph → sourceBounds；body 先退
3. p=0 → `onAdvancedCollapsed` → MAIN
4. MAIN 重新出现在对应来源 row 的菜单中

---

## 4. 几何与材质

| Token | 值 |
|---|---|
| MAIN 宽 | 218dp（212–224） |
| Advanced 宽 | 284dp 默认（276–292），`min(284, 292, screen−12−64)` |
| Advanced 高 | header 52dp + body；TOC/THUMBS 统一 maxHeight 内滚；PAGE_MAPPING/EXPORT 内容自适应 |
| 右边缘 | 与 MAIN TopEnd 对齐 |
| 动画 | 190ms，`CubicBezierEasing(0.2,0,0,1)`；HeaderRevealP=0.35；BodyRevealStartP=0.40 |
| Glass | alpha **0.90** / 1dp border / 6dp elevation |

IME：`computeAdvancedTargetRect` 顶边用 no-IME bottom clamp；键盘只压底部高度。

---

## 5. 删除清单

| 删除项 | 替代 |
|---|---|
| `ParentContextAlpha` / `parentRecede` | 单 surface；advanced 时 MAIN 不组合 |
| 父卡交互 blocker（`detectTapGestures` 空 onTap） | 无父卡残留 |
| z0 MAIN + z1 advanced 双绘 | `showMainSurface` / `showAdvancedSurface` 互斥 |
| OptionIndent +20dp 右漂 | 与 title column 对齐（50dp） |
| glass 0.84 过透 | 0.90 |
| advanced 默认 320dp 过宽 | 284dp + back clearance |

---

## 6. 验证

### Automated

- `:app:testDebugUnitTest --offline` PASS（全量）
- 结构门禁 `R25ReaderFoundationTest`：单 surface、sibling fade、无 ParentContextAlpha、indent/width/glass 带
- UI 门禁 `ReaderMoreHostUiTest`：advanced 打开后 MAIN 行不存在；header count=1；collapse 保身份
- `AdvancedOriginFidelityTest` / geometry / coordinate contract 保持

### Release

- `assembleRelease` PASS → `.external_outbox/markleaf-release-latest.apk`
- 已复制 `/storage/emulated/0/Download/markleaf-release-latest.apk`

### Device（PENDING）

`adb devices` 无连接设备。以下链路 **不得** 标 PASS，直至实机逐帧验收：

1. MAIN → PAGE_MAPPING → MAIN  
2. MAIN → TOC → MAIN  
3. MAIN → THUMBNAILS → MAIN  
4. MAIN → EXPORT → MAIN  
5. MAIN ↔ READING_MODE  
6. MAIN ↔ READING_UI  

四条 advanced 链路需 0/25/50/75/100% 帧：

- 任何一帧不得同时存在完整 MAIN surface 与 advanced surface  
- 任何一帧不得看到父菜单文字透在 advanced 后  
- close 必须回各自来源 row  
- target 不得覆盖返回圆主点击区或突破 viewport  

---

## 7. 文件

- `app/src/main/java/com/markleaf/app/ui/pdf/ReaderMoreHost.kt` — 单 surface 宿主  
- `app/src/main/java/com/markleaf/app/ui/pdf/ReaderAdvancedCard.kt` — replace/morph 内容  
- `app/src/main/java/com/markleaf/app/ui/pdf/ReaderChromeGlass.kt` — alpha 0.90  
- `app/src/main/java/com/markleaf/app/ui/pdf/PdfViewerScreen.kt` — advanced 宽度/back clearance  
- 测试：`R25ReaderFoundationTest`、`ReaderMoreHostUiTest`、`ReaderChromeGeometryTest`  
