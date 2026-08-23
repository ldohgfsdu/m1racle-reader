# R25.3 — STOP PATCHING / Exact ChatGPT Nested Popover Rebuild (Phase-1 TOC Demo)

**Date:** 2026-08-07  
**Ground truth:** user ChatGPT Android「主菜单 → 添加到项目」录屏 only  
**Status:** phase-1 **implemented + automated_verified**.  
**Device 五帧 / 慢录屏: PENDING**（`adb devices` empty — **不得宣布 phase-1 完成**）。

---

## 1. 旧错误 overlay 删除清单

| 删除项 | 原因（对照 ground truth） |
|---|---|
| `ParentContentContextAlpha` / `parentContentAlpha` | 父菜单整体变暗 / 幽灵背景 — ChatGPT 父卡不改 alpha |
| 整卡 de-emphasis / surface 降权叠色 | 双层调色；PDF/父文字透出 child |
| `NestedCardHost` 全量 morph（x/y/w/h 同时插值） | 像从左侧铺开 / full-rect morph，非 top-anchored reveal |
| `siblingFade` / 单 surface replace（R25.1） | 父菜单消失或替换 |
| `localBoundingBoxOf(overlay)` 混用 | 与 root 坐标混用 |
| `NestedDefaultWidth` 过大横向板倾向 | 大横板风险 |
| Phase-1 对 PAGE_MAPPING / THUMBNAILS / EXPORT 开真实 body | 禁止一次重写四套内容 |

**未改：** PDF renderer、阅读滚动、返回圆、中间胶囊、三点圆逻辑。

---

## 2. 唯一坐标系说明

```
PdfChrome root Box (fillMaxSize)
  └── Reader more layer (TopEnd pad; host fillMaxSize when open)
        └── ReaderMoreOverlayHost = same Box
              ├── MAIN card  → onGloballyPositioned { boundsInRoot() } → parentRect
              ├── each task row → onGloballyPositioned { boundsInRoot() } → sourceRect
              └── AnchoredNestedPopover
                    placement = rootRect − hostOriginRoot  (convert exactly once)
```

- **所有** `sourceRect` / `parentRect` / `childRect` 仅 `boundsInRoot()`  
- Host origin 同为 `boundsInRoot()`；local offset = root − hostOrigin  
- **禁止** Popup / Dialog / DropdownMenu / 第二 Window / 第二套 TopEnd 坐标  
- **禁止** boundsInParent 与屏幕坐标混用、禁止 dp 猜位置

纯几何（可测）: `com.markleaf.app.pdf.AnchoredNestedGeometry`  
- `childRight = parentRect.right`  
- `childTop = sourceRect.top`  
- `childLeft = max(backButtonRight + gap, parentRect.left − leftExpand)` leftExpand≈60dp (48–72)  
- height clamp to viewportBottom − 14dp  

Reveal 动画: `anchoredRevealRect` — **left/right/top 固定**，仅 **bottom** 从 `source.bottom` → `target.bottom`；180ms；可选 scaleY 0.96→1，origin TopEnd。

---

## 3. Phase-1 行为（仅教材目录 demo）

| 项 | 实现 |
|---|---|
| 阅读方式 / 阅读界面 | MAIN 内 INLINE accordion（不变） |
| 教材目录 | AnchoredNestedPopover：header = icon+「教材目录」+↓；body = 3 假章节 |
| 设置教材页码 / 缩略图 / 导出 | 入口保留，**点击 no-op**（等 TOC 验收） |
| 父菜单 under nested | **不移动、不缩放、不改 alpha**；仅被 child 物理遮挡 |
| Back / header chevron | 同一 reverse reveal |

---

## 4. 自动化验证

- `:app:testDebugUnitTest` PASS  
- 新增 `AnchoredNestedGeometryTest`（right/top/left/back/viewport/reveal）  
- 结构门禁：无 ParentContentAlpha、有 boundsInRoot、有 AnchoredNestedPopover  
- UI：TOC 打开后 MAIN「阅读方式」等仍在；假章节可见；关闭回 MAIN  
- `assembleRelease` PASS →  
  `.external_outbox/markleaf-release-latest.apk` (SHA `48151fb2…`)  
  → `/storage/emulated/0/Download/markleaf-release-latest.apk`

---

## 5. 实机验收（PENDING — 未过不得进入 phase-2）

必须提交：

1. **Opening 慢录屏** MAIN → 教材目录  
2. **Closing 慢录屏** 教材目录 → MAIN  
3. **0 / 25 / 50 / 75 / 100% 五帧**（opening + closing 逆序）

硬 FAIL 若出现：

- 父菜单整体变暗  
- 父菜单文字在 child 内半透明透出  
- child 从固定左上角 / 屏幕左侧飞入  
- child top 与「教材目录」row 无关  
- child 横跨成大黑板  
- MAIN 自己移动/缩放  
- Popup/Dialog  
- 仅 alpha fade 无 top-anchored vertical reveal  

**本阶段未提供五帧与慢录** → **phase-1 未完成**；**禁止**泛化 PAGE_MAPPING / THUMBNAILS / EXPORT。

---

## 6. 文件

| 文件 | 角色 |
|---|---|
| `AnchoredNestedGeometry.kt` | 纯几何 |
| `AnchoredNestedPopover.kt` | TOC demo + vertical reveal |
| `ReaderMoreHost.kt` | MAIN + overlay host；boundsInRoot |
| `ReaderAdvancedCard.kt` | NestedCardHost **删除**；body 残留待 phase-2 |
| `PdfViewerScreen.kt` | 仅 TOC 打开 child；back/viewport root px |
