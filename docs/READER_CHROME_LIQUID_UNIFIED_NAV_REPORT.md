# Reader Chrome — Liquid-only + Unified Anchored Advanced Child Navigation (Closure Report)

**Task:** READER_CHROME_LIQUID_UNIFIED_NAV
**Date:** 2026-08-07
**Status:** implemented_automated_verified_device_pending
**Principle:** 以当前代码为唯一事实源 — code is the single source of truth. Obsolete `.memory`/docs must not drive implementation.

---

## 1) Memory / doc corrections (stale → code-first reality)

| Artifact | Before (stale) | After (code-first) |
|----------|----------------|--------------------|
| `.memory/active_object.yaml` | `READER_CHROME_MATERIAL_DUAL`: "FROSTED default, LIQUID optional", `KEY_MATERIAL_STYLE`, dual-material renderer | `READER_CHROME_LIQUID_UNIFIED_NAV`: Liquid is the **only** surface language; unified anchored advanced child navigation; new build sha |
| `.memory/ACTIVE_OBJECT.md` | R9 markdown-scroll object (older) | Liquid-only Reader + unified nav object, handoff pointers |
| `.memory/CURRENT_STATE.md` | Home liquid controls only | Reader chrome closure + nav unification, corrections listed |
| `docs/READER_CHROME_MATERIAL_REPORT.md` | Dual-material (FROSTED/LIQUID) how-to | Liquid-only current facts + unified nav model + BottomSheet deletions (this report is the code-first source) |
| `docs/READER_CHROME_LIQUID_UNIFIED_NAV_REPORT.md` | — | this file |

**Explicitly NOT re-added:** FROSTED, material style switching, `KEY_MATERIAL_STYLE` pref, any compat layer, any old preference, any dual path.

## 2) BottomSheet paths deleted / stopped

- `PdfViewerScreen.kt`: removed unused `ModalBottomSheet` / `rememberModalBottomSheetState` imports; removed unused `Icons.Outlined.MenuBook`, `sheetContainer`, `dialogContainer` imports; updated the stale overlay-contract comment that described "BottomSheets handed over after card morph-closed".
- `strings.xml`: removed `pdf_chrome_material_frosted` / `pdf_chrome_material_liquid`.
- The three advanced tasks (教材目录 / 页面缩略图 / 导出章节) never hand off to BottomSheet — they open as anchored foreground child-cards.
- Only the export **progress** stays a Dialog (`ChapterExportProgressDialog`) — a system-flow status surface, explicitly not a Reader More BottomSheet. `OutlineEditDialog` / `EmbeddedOutlineImportDialog` are dialogs, also not sheets.
- `R25ReaderFoundationTest.no Popup Dialog BottomSheet for reader more nested path` still gates: no `Popup`, no `ModalBottomSheet`, no `DropdownMenu` in the nested path.

## 3) Final ReaderMoreState state graph

```
CLOSED ──toggleOpen──► MAIN
MAIN ──section──► INLINE_READING_MODE | INLINE_READING_UI
MAIN ──openChild──► CHILD_PAGE_MAPPING | CHILD_TOC | CHILD_THUMBNAILS | CHILD_EXPORT
INLINE_* ──openChild──► PREPARING_CHILD_* (two-phase: collapse Basic → wait Main six-row → freeze canonical bounds)
PREPARING_CHILD_* ──onPreparingLayoutReady──► CHILD_*
CHILD_* ──reverse morph──► MAIN (collapses to its OWN source row first)
CHILD_A ──openChild(B)──► CHILD_B (direct switch, no PREPARING)
MAIN ──dismiss──► CLOSED
```

Invariants (tested):
- A state is never inline AND child/preparing simultaneously (single enum value).
- `onOpenChild(Basic, advanced)` → `PREPARING_*`; never simultaneous Basic-expanded + Advanced.
- `onOpenChild(Main, advanced)` → `CHILD_*` directly (canonical Main bounds ready).
- `onOpenChild(childA, childB)` → `childB` directly.
- `onPreparingReady` promotes `PREPARING_* → CHILD_*`.
- `onChildCollapse` → `MAIN`; Back: `AdvancedOpen → Main`, `BasicExpanded → Main`, `Main → close`.
- Canonical Main source bounds are captured only when `state == MAIN` and both inline progresses are 0 — Basic-expanded temporary coordinates are never used for child morphs; no fixed-offset compensation.

## 4) Advanced entry / source-row / return mapping

| Entry | State | Source row (parent index) | Return |
|-------|-------|---------------------------|--------|
| 设置教材页码 | `CHILD_PAGE_MAPPING` | 2 | row 2 |
| 教材目录 | `CHILD_TOC` | 3 | row 3 |
| 页面缩略图 | `CHILD_THUMBNAILS` | 4 | row 4 |
| 导出章节 | `CHILD_EXPORT` | 5 | row 5 |

ParentCard stays at compact width (220dp, band 212–224) and standard six-row
geometry while any advanced child is open; the source row keeps only its layout
slot (visual replaced by the Child header — no blank hole, no double title).
ChildCard may be wider than Parent; right edge aligned, left edge expands
leftward (clamped ≥16dp from screen left).

## 5) What was NOT changed (already correct in code)

- Liquid material chain: `readerChromeSurface` → `markLeafLiquidSurface` (Control/Parent/Foreground), root `layerBackdrop` on PDF, Parent `exportedBackdrop` → Child samples export (official glass-on-glass); stable `rememberLayerBackdrop()`; lens gated by `shapeSupportsLiquidLens`; CapsulePopoverShape skips lens.
- Four advanced bodies reuse the single `AnchoredNestedPopover` shell (header + divider + `NestedChildBody` switching): `TextbookPageAdvancedContent` / `OutlineAdvancedContent` / `ThumbnailsAdvancedContent` / `ExportAdvancedContent`.
- Back priority + page-tap-closes-topmost behavior in `ReaderMoreTransitions` / `PdfViewerScreen` (BackHandler + `requestClosePdfMenu`).

## 6) Test additions (this session)

`app/src/test/java/com/markleaf/app/ui/pdf/ReaderMoreStateMachineTest.kt`:
- `advanced to advanced switches directly - never preparing` (4×4 matrix)
- `advanced to same child keeps child`
- `main to each advanced opens child directly`

Fixed stale assertion in `ReaderChromeMaterialTest.home restores system bars and uses liquid surface` — `markLeafLiquidSurface` lives in `HomeLiquidControls.kt` (shared liquid controls), asserted against the real file.

Existing coverage retained: Basic→4 Advanced matrix, mutual exclusion, preparing promotion, Back chain, canonical bounds geometry, source-row fidelity, no-BottomSheet structural gates.

## 7) Verification

| Gate | Result |
|------|--------|
| `:app:testDebugUnitTest` (full suite incl. Robolectric `ReaderMoreHostUiTest`) | PASS |
| `:app:assembleRelease` | PASS |
| APK | `.external_outbox/markleaf-release-latest.apk` → `/storage/emulated/0/Download/markleaf-release-latest.apk` |
| SHA-256 | `fd0412191cce56d5a713d8a248389b4914cdce40ee8233db70cfdd93047a9098` |
| Device screenshots / recordings | **PENDING** — `adb devices` offline (10.153.213.51:5555/5556 offline); install & human acceptance remain |

## 8) Related

- `.memory/active_object.yaml`, `.memory/CURRENT_STATE.md`, `.memory/ACTIVE_OBJECT.md`
- `docs/READER_CHROME_MATERIAL_REPORT.md` (Liquid-only current facts)
- `R25ReaderFoundationTest`, `ReaderMoreStateMachineTest`, `ReaderMoreTransitionsTest`, `ReaderChromeGeometryTest`, `AdvancedOriginFidelityTest`, `AdvancedChildLayoutSpecTest`
