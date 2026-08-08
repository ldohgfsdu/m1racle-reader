# CURRENT_STATE

**Updated:** 2026-08-08

**A0 repo baseline:** 2026-08-08 — HEAD 80fbf69（R10 close）之后的全量未提交工作（R11–R26、HOME 系列、外部打开、PDF 稳定性）已按 AUDIT_FULL_20260808_REVIEWED.md §4 分类，分 4 个 commit 提交（hygiene / production+tests / build+version / docs+governance）。Session 过程报告（docs/R*、HOME_*）未入库，留待决策。后续批次 A1（P0 正确性）待批准后开始。

## Active: Home alignment + unified liquid floating controls (24dp rail, FAB/menu = Reader liquid)

### Delivered (code = source of truth)
- **ONE 24dp content rail**: `PageHorizontal` 16→24dp — brand block (墨阅 MarkLeaf / subtitle / 最近文件), section title, card surfaces all start/end at 24dp via LazyColumn contentPadding. Empty-state header bug fixed (was flush at x=0; now padded to same rail).
- **FAB = 56dp rounded square** (corner 20, plus 26dp onSurface) using SHARED `markLeafLiquidSurface` + `LiquidSurfaceLevel.Control` — the exact PDF reader chrome material (α0.50 / blur 14 / lens 14·28 / shadow 8 / highlight 0.5·0.55 / pressed boost), real backdrop sampling from Scaffold slot; no home-specific material invented. Position right 20dp / bottom safe+20dp; list bottom inset nav+96dp.
- **··· menu = same-source liquid glass**: Popup + shared surface (Control soft plate, `sampleBackdrop = false`, no cross-window sampling) + Control edge-highlight border (0.5dp/α0.55), corner 10dp = PDF chrome menu-row corner, 140×48dp single row, right-edge aligned to anchor expanding left.
- **Cards stay solid tonal** (surfaceContainerLow, no backdrop) — liquid only on FAB/menu.
- **KEPT**: single LazyColumn header item, `key = { doc -> doc.id }`, no animateItem, optimistic delete reflow, no fixed header/nested scroll.
- **UNTOUCHED**: `MarkLeafLiquidSurface.kt`, `ReaderChromeMaterial.kt`, all Reader chrome, `backdrop:2.0.0` dependency.

### Verification
- `HomeLayoutContractTest` (9, incl. 24dp rail + empty-state padding + liquid-only-on-floating-controls negatives + menu corner/highlight/right-align) / `HomeListUpdateTest` (6) / `ReaderChromeMaterialTest` (7, home assertion updated to reuse-shared-liquid) PASS; full unit suite green (107 suites).
- assembleRelease PASS → Download `markleaf-release-latest.apk` (sha256 c1607650…).
- Device visual calibration (rail alignment / title start lines / ··· right edges / FAB rounded-square liquid / no glass-wave regressions): PENDING — adb offline.
- Report: `docs/HOME_ALIGN_LIQUID_CONTROLS_REPORT.md`

### Key files
HomeScreen.kt, HomeDesignTokens.kt, HomeLayoutContractTest.kt, ReaderChromeMaterialTest.kt

## Previous: Home final polish — design frozen (section gap + card −5~8% + FAB 56dp)

### Delivered (code = source of truth)
- **Section continuity**: removed global `spacedBy`; per-item spacing — header item bottom padding `HeaderToFirstCardGap = 2dp` (tightened 6dp from 8), cards keep `CardGap = 8dp`. Brand typography untouched.
- **Card −5.4%** (93 → 88dp single-line, gate 5–8%): v-padding top 12→11 / bottom 10→9 (−2dp), title lineHeight 22→21sp (font stays 18sp, maxLines 2), title→type 6→5, meta→progress 8→7; radius 20 / gap 8 / progress 3 unchanged. No forced equal heights.
- **FAB 56dp** (58→56), radius 20, white plus 26dp, right & bottom insets both 20dp (Scaffold 16 + extra 4); list bottom inset nav+96dp clears it.
- **Overflow**: unchanged single composable — 48dp invisible touch / 22dp glyph, uniform by construction.
- **KEPT**: single LazyColumn header item, `key = { doc -> doc.id }`, no animateItem, optimistic delete reflow, no liquid on home.
- **UNTOUCHED**: `MarkLeafLiquidSurface.kt`, `ReaderChromeMaterial.kt`, all Reader chrome, `backdrop:2.0.0` dependency.

### Verification
- `HomeLayoutContractTest` (9, incl. gap-tightening math + density gate: computed card height 88dp, 5–8% band, title 18sp assertion) / `HomeListUpdateTest` (6) / `ReaderChromeMaterialTest` (7) PASS; full unit suite green (107 suites).
- assembleRelease PASS → Download `markleaf-release-latest.apk` (sha256 f24898b7…).
- Device visual + fast-scroll verification (default / menu / mid-scroll / bottom FAB + scroll video): PENDING — adb offline. NO further home design iterations.
- Report: `docs/HOME_FINAL_POLISH_REPORT.md`

### Key files
HomeScreen.kt, HomeDesignTokens.kt, HomeLayoutContractTest.kt

## Previous: Home density + popup closure (no restructure, no Reader)

### Delivered (code = source of truth)
- **Card density -15%**: ~109dp → ~93dp (single-line title). Radius 24→20dp, gap 12→8dp, padding 16h / top 12 / bottom 10, title 18sp/22sp lh maxLines=2, type/meta 12sp with lh14, progress 3dp. Overflow switched from Row (40dp touch inflated row 1) to **Box overlay**: text block end-padded 48dp + bare ··· aligned TopEnd → 48dp invisible touch / 22dp glyph never inflates card height.
- **FAB**: 58dp / 20dp radius / white plus 26dp, right 20dp, bottom = safe area +20dp, list bottom inset nav+96dp.
- **Delete menu = utility popup**: M3 DropdownMenu 140dp wide (min 140 / max 160), single 48dp row, 16dp row padding (M3 12 + 4), 22dp icon, 12dp icon gap (M3), 12dp radius, `surfaceContainer` + 2dp shadow; right-edge aligned to ··· anchor via `offset = DpOffset(-MenuAnchorOverhang=92dp)` expanding leftward — never covers large title span.
- **KEPT**: single LazyColumn (header item scrolls away), `key = { doc -> doc.id }`, no animateItem, optimistic delete reflow, `wic.show(all)`, no liquid on home.
- **UNTOUCHED**: `MarkLeafLiquidSurface.kt`, `ReaderChromeMaterial.kt`, all Reader chrome, `backdrop:2.0.0` dependency.

### Verification
- `HomeLayoutContractTest` (8, new density/popup bands + overlay layout + right-align math) / `HomeListUpdateTest` (6) / `ReaderChromeMaterialTest` (7) PASS; full unit suite green (107 suites).
- assembleRelease PASS → Download `markleaf-release-latest.apk` (sha256 e2400987…).
- Device screenshots (default / menu open / mid-scroll / bottom FAB) + scroll video: PENDING — adb offline.
- Report: `docs/HOME_DENSITY_POPUP_CLOSURE_REPORT.md`

### Key files
HomeScreen.kt, HomeDesignTokens.kt, HomeLayoutContractTest.kt

## Previous: Home plain Material restore (remove Home Liquid, keep Reader Liquid)

### Delivered (code = source of truth)
- **Home has NO Liquid Glass anymore** (targeted UI revert, no git reset): bare `MoreVert` overflow icon (40dp touch / 24dp glyph, no surface/highlight/shadow), M3 `DropdownMenu` delete menu (12dp corner, `surfaceContainer`, 2dp shadow, anchored at ···), M3 `FloatingActionButton` 62dp / 22dp corner / `scheme.primary` (near-black #171717 in light) + white 28dp +, right margin 20dp, bottom = safe area +20dp.
- `HomeLiquidControls.kt` DELETED (LiquidFab / LiquidIconButton / LiquidPopover / LiquidMenuItem gone). Home backdrop host (`rememberLayerBackdrop` / `layerBackdrop` / `LocalLiquidBackdropHost`) removed from HomeScreen.
- Cards: tonal `surfaceContainerLow`, 24dp radius, compact 4-row (title 20sp ≤1 line → type 12sp → meta 12sp → 3dp progress), padding 16h/8v, gap 12dp.
- **KEPT (not rolled back)**: single LazyColumn with header item scrolling away; stable `key = { doc -> doc.id }`; no animateItem; optimistic delete + `HomeListUpdate` reflow; `wic.show(all)` system bars.
- **UNTOUCHED**: `MarkLeafLiquidSurface.kt`, `ReaderChromeMaterial.kt`, all Reader chrome, `io.github.kyant0:backdrop:2.0.0` dependency.

### Verification
- `HomeLayoutContractTest` (7) / `HomeListUpdateTest` (6) / `ReaderChromeMaterialTest` (7, Reader assertions unchanged) PASS; full unit suite green (107 suites).
- assembleRelease PASS → Download `markleaf-release-latest.apk` (sha256 78df3216…).
- Device screenshots (default / delete menu open / bottom FAB): PENDING — adb offline.
- Report: `docs/HOME_PLAIN_CONTROLS_RESTORE_REPORT.md`

### Key files
HomeScreen.kt, HomeDesignTokens.kt, HomeLayoutContractTest.kt (Reader/common liquid files untouched)

## Previous: Home visual closure (Quiet/Primary/Popover Liquid levels + single LazyColumn) — SUPERSEDED by plain restore

### Delivered (code = source of truth)
- **Named Liquid control levels** in shared `MarkLeafLiquidSurface.kt`: `QuietControl` (home ···, α0.36/shadow3dp/quiet highlight) → `PrimaryControl` (home FAB, α0.44/shadow6dp/lens depth) → `Popover` (α0.56/shadow10dp). All home floating controls reference the named levels; no per-component hardcoded material. Reader Control/Parent/Foreground tokens untouched (frozen). Shared `pressed` boost (α +0.06, highlight +0.18) on `markLeafLiquidSurface`.
- **Single LazyColumn home**: brand header (墨阅 MarkLeaf / subtitle / 最近文件) is `item(key = "home_brand_header")` inside the same LazyColumn as cards — no fixed header, no clip boundary; old fixed-header + separate-list structure removed; dead `HomeHeaderScrollState` (+test) deleted.
- **Card polish**: 24dp radius, 16/14dp padding, title 20sp → type 14sp (8dp) → meta 14sp (6dp) → progress (12dp). Quiet ··· = 40dp touch / 36dp glass core / 18dp icon; pressed/selected enhances.
- **FAB**: 60dp, corner 24dp, icon 28dp, right 20dp, bottom = safe area + 20dp; list bottom inset nav+96dp clears it.
- Popover stays Liquid Glass (Popover level, soft plate in Popup window).

### Verification
- `HomeLayoutContractTest` / `HomeListUpdateTest` / `ReaderChromeMaterialTest` PASS; full unit suite green (107 suites).
- assembleRelease PASS → Download `markleaf-release-latest.apk` (sha256 bd7954c8…).
- Device screenshots (default / scrolling brand leaving / popover open / bottom FAB clearance): PENDING — adb offline.
- Report: `docs/HOME_VISUAL_CLOSURE_REPORT.md`

### Key files
HomeScreen.kt, HomeDesignTokens.kt, HomeLiquidControls.kt, MarkLeafLiquidSurface.kt, HomeLayoutContractTest.kt

## Previous: Liquid-only Reader chrome + unified anchored advanced child navigation (r26.1)

### Delivered (code = source of truth)
- **Liquid only**: `ReaderChromeMaterial.kt` — Liquid is the only surface language; FROSTED/LIQUID dual material, material prefs key, and 阅读界面 material rows **removed** (r26.1). All Reader chrome goes through `Modifier.readerChromeSurface` → `markLeafLiquidSurface`.
- **Glass-on-glass**: `io.github.kyant0:backdrop:2.0.0` — root `layerBackdrop` on PDF content, Parent `exportedBackdrop`, Child samples export (official pattern).
- **Unified ReaderMore nav** (no BottomSheet handoff): exclusive `ReaderMoreState` — `CLOSED / MAIN / INLINE_READING_MODE / INLINE_READING_UI / PREPARING_CHILD_* / CHILD_*`.
- **Four advanced tasks** (page mapping / TOC / thumbnails / export) open as `AnchoredNestedPopover` child-cards morphing from their own canonical Main source row (2..5); collapse returns to the same row. Death of BottomSheet handoff for catalog/thumbnails/export.
- **Two-phase**: Basic → `PREPARING_*` → wait Main six-row layout stable → re-capture canonical source bounds → `CHILD_*`. No BasicExpanded temp coordinates, no fixed-offset compensation.
- ParentCard stays 220dp compact six-row geometry in AdvancedOpen (`childRowIndex` rows only keep layout slot; Child header owns the visible instance).
- Back priority: `AdvancedOpen → Main`, `BasicExpanded → Main`, `Main → close`. Page tap closes topmost surface.
- Non-bottom-sheet system dialogs remain: `ChapterExportProgressDialog`, `OutlineEditDialog`, `EmbeddedOutlineImportDialog` (only export progress is the system-flow dialog).

### Verification
- Unit `*ReaderMore*` / `*Chrome*` / `*Geometry*` filters: PASS (`unit-reader-more.log`)
- Extended transition matrix: Main→4 Advanced direct, Basic→4 Advanced via PREPARING, AdvancedA→AdvancedB direct, Back chain (see tests)
- assembleRelease: PASS
- APK: Download `markleaf-release-latest.apk`
- Device screenshots / recordings: PENDING (`adb` empty / devices offline)

### Stale memory/doc corrections
- `.memory/active_object.yaml`: removed `READER_CHROME_MATERIAL_DUAL` (FROSTED default / LIQUID optional) — old memory no longer drives implementation.
- `docs/READER_CHROME_MATERIAL_REPORT.md`: rewritten as Liquid-only current facts (code-first).
- Dead BottomSheet imports / material preference strings removed from `PdfViewerScreen.kt` / `strings.xml`.

### Key files
ReaderChromeMaterial.kt, ReaderMoreHost.kt, ReaderMoreTransitions.kt, AnchoredNestedPopover.kt, ReaderMoreStateMachine(.kt + test), PdfViewerScreen.kt, MarkLeafLiquidSurface.kt