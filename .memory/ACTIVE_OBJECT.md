# ACTIVE_OBJECT

## Current

**HOME_ALIGN_UNIFIED_LIQUID_CONTROLS** — Home alignment + unified liquid floating controls（24dp rail + FAB/menu = Reader liquid，无重设计）

## Machine-readable

See `.memory/active_object.yaml` (source of truth for id/priority/status/scope).

## Status

`implemented_automated_verified_device_pending` — Home 对齐（24dp 内容 rail）与悬浮控件材质统一已落地：FAB 与 ··· 菜单复用共享液态玻璃（markLeafLiquidSurface + LiquidSurfaceLevel.Control）；单测绿（107 suites）；assembleRelease PASS（sha256 c1607650…）；设备视觉校准 PENDING（adb offline）。

## Goal

以当前代码为唯一事实源：Home 内容与悬浮控件统一到 24dp rail；FAB（56dp 圆角方）与 ··· 菜单使用 Reader 同一液态材质，不发明 home 专属材质；卡片保持实色 tonal（永不液态）。

## Handoff

下一位 Agent **必须先读**：

1. `.memory/active_object.yaml`
2. `.memory/CURRENT_STATE.md`
3. `docs/HOME_ALIGN_LIQUID_CONTROLS_REPORT.md`

## Scope

- Home 24dp 内容 rail（brand / section title / cards / menu；空态 header 修复）
- FAB = 56dp 液态（共享 surface）；··· 菜单 = 液态 Popup（Control soft plate，无跨窗口采样）
- 卡片实色 tonal（不液态）
- Memory / doc 同步（代码优先于旧 memory）

## Out of scope

- Reader chrome / PDF 渲染 / 手势 / ThreePageStrip / Tile
- Markdown reader
- commit / push / remote
