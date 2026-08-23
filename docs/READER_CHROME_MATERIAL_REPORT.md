# Reader Chrome (Liquid-only) — code-first current facts

**Date:** 2026-08-07
**Scope:** Reader chrome visual material + unified ReaderMore navigation. Corrected from the previous "dual-material" report: **FROSTED / 材质切换 no longer exist** — the code is the single source of truth.

## 0) One fact: Liquid is the only surface language

- `ReaderChromeMaterial.kt` states: *"Frosted / dual-material paths were removed (r26.1) — Liquid is the only UI surface language."*
- `PdfViewerScreen.kt` topBarModeOptions: **material rows removed** (阅读界面 offers only 普通 / 全屏沉浸).
- There is **no** `ReaderChromeMaterialStyle`, no `chromeMaterialStyle` in the VM, no `KEY_MATERIAL_STYLE` pref, no FROSTED/LIQUID toggle UI any more. Old `.memory` describing `READER_CHROME_MATERIAL_DUAL` / "FROSTED default, LIQUID optional" is obsolete and must not drive implementation (this report documents the code-first reality).

## 1) Backdrop dependency

| Item | Value |
|------|--------|
| Artifact | `io.github.kyant0:backdrop:2.0.0` (Maven Central) |
| Transitive | `backdrop-android:2.0.0`, `shapes-android:1.2.0` |
| Vendored sources | **None** |
| INTERNET permission | **Not added** |

Compat: Kotlin stdlib stays 2.0.21 (artifact kotlin-stdlib excluded) with
`-Xskip-metadata-version-check` + `-Xskip-prerelease-check`; Compose UI bump to
1.11.1 via transitive; compileSdk 36 with AAR metadata task disabled.

## 2) Glass-on-glass chain (official exportedBackdrop)

| Role | Backdrop source | Export |
|------|-----------------|--------|
| Root (PDF) | `rememberLayerBackdrop()` + `layerBackdrop(rootBackdrop)` | — |
| ParentCard | `markLeafLiquidSurface(Parent, exportForChild=true)` | replicates to `parentExport` |
| ChildCard | `markLeafLiquidSurface(Foreground)` samples `parentExport` | none |
| Chrome circles / capsule | `markLeafLiquidSurface(Control)` | none |

Single entry `Modifier.readerChromeSurface` → `markLeafLiquidSurface` in
`ui/common/MarkLeafLiquidSurface.kt`. Shells never call `drawBackdrop`/
`layerBackdrop` ad-hoc (forbidden recursive loop → RenderThread SIGSEGV).
Stable `rememberLayerBackdrop()` (default onDraw, not a per-recomposition
lambda).

Liquid tokens: Parent 0.48/18dp blur, Foreground 0.58/20 blur, Control 0.50/14
blur + lens (API 33+); lens gated by `shapeSupportsLiquidLens` (CornerBasedShape
only — CapsulePopoverShape skips lens).

## 3) Unified ReaderMore navigation (single model, no BottomSheet handoff)

`ReaderMoreState` is an exclusive machine:

```
CLOSED -> MAIN
MAIN -> INLINE_*            (Basic: 阅读方式 / 阅读界面)
MAIN -> CHILD_*             (canonical Main source-row bounds ready)
INLINE_* -> PREPARING_*     (collapse Basic first)
PREPARING_* -> CHILD_*      (onPreparingLayoutReady after six-row layout stable)
CHILD_* -> MAIN             (reverse morph to the SAME source row)
CHILD_A -> CHILD_B          (direct switch, none of the PREPARING phase)
```

- Basic inline expansions stay inside ParentCard only.
- The four advanced tasks (`设置教材页码 / 教材目录 / 页面缩略图 / 导出章节`) open as
  **anchored foreground child-cards** (`AnchoredNestedPopover`) from their own
  canonical Main source row — **no BottomSheet handoff, no Popup, no dim**.
  `R25ReaderFoundationTest.no Popup Dialog BottomSheet for reader more nested path`
  gates this.
- ParentCard stays 220dp compact six-row in AdvancedOpen; source rows keep only
  the layout slot (visual taken by the Child header). Left edge of Child expands
  leftward from Parent (right edge aligned).
- Canonical bounds captured only when `state == MAIN` with both inline progress
  at 0 — Basic-expanded temporary coordinates are never used for the child.
- Back: Advanced → Main, Basic → Main, Main → close menu (system Back + tap).
- Only the **export progress** is a system-flow dialog (`ChapterExportProgressDialog`);
  it is a Dialog, not a Reader More BottomSheet.

## 4) Settings persistence

| Item | Location |
|------|----------|
| Prefs file | `pdf_chrome_prefs` (`PdfChromePrefs`) |
| Keys | `top_bar_mode` (NORMAL \| IMMERSIVE) only — **no material key** |
| UI | 阅读界面 → 普通 / 全屏沉浸 |

## 5) Which BottomSheet paths were deleted / stopped

- `PdfViewerScreen.kt`: removed unused `ModalBottomSheet`/`rememberModalBottomSheetState` imports; stale comments describing BottomSheet handoff updated.
- `strings.xml`: removed `pdf_chrome_material_frosted` / `pdf_chrome_material_liquid`.
- Advanced task flows (TOC / thumbnails / export) never hand off to BottomSheet3 anymore — unified anchored children.
- Export progress stays an AlertDialog (system-flow status, kept).

## 6) Entry/source-row mapping (advanced children)

| State | Row (parent index) | Source label | Return |
|-------|--------------------|--------------|--------|
| `CHILD_PAGE_MAPPING` | 2 | 设置教材页码 | row 2 |
| `CHILD_TOC` | 3 | 教材目录 | row 3 |
| `CHILD_THUMBNAILS` | 4 | 页面缩略图 | row 4 |
| `CHILD_EXPORT` | 5 | 导出章节 | row 5 |

Everything else collapses to the same row via `AnchoredNestedPopover.closing`.

## 7) Device / performance

- adb devices: **offline** at build time → device screenshots / gfxinfo PENDING.
- Code gates: Liquid effects only on visible chrome shells; Frosted removed means
  no deduped no-op path to switch; no p-PDF-frame material branching.

## Verification summary

| Gate | Status |
|------|--------|
| Dependency resolve + compile | PASS |
| Unit/static material tests (Liquid-only) | PASS |
| ReaderMore transition matrix + geometry tests | PASS |
| assembleRelease | see build log |
| Device screenshots / gfxinfo | PENDING (adb offline) |

## Related artifacts

- `.memory/active_object.yaml` (single active object: Liquid-only + unified nav)
- `.memory/CURRENT_STATE.md`
- `docs/READER_CHROME_LIQUID_UNIFIED_NAV_REPORT.md` (the closure report)
- Status: implemented_automated_verified_device_pending