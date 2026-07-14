# MARKLEAF_TABLE_HEADER_TYPOGRAPHY_AND_ALIGNMENT_PARITY_FIX

**Date:** 2026-07-14
**Status:** DEVICE_ACCEPTED; host/build/configuration and main-package data-safety validation pass;
QA device instrumentation blocked; human visual acceptance accepted.

## Acceptance correction

The latest visual decision supersedes the R3 screenshot acceptance:

```yaml
three_column_width_priority: pass
three_column_horizontal_scroll: pass
three_column_header_typography: pass
three_column_header_alignment: pass
prior_human_visual_acceptance: rejected
human_visual_acceptance: accepted
overall: device_accepted
```

R3 width priority and horizontal scrolling are preserved. No table width solver, compression rule,
body typography, padding token, parser/AST, inline semantics or gesture path changed.

## R4 correction after human rejection

The first post-fix screenshot was explicitly rejected because the header still appeared uneven and
the gap from the first identifier column to the second content column was too large.

- Header cells are now direct children of the header `Row` and use `alignByBaseline()`. This makes
  the shared first baseline a real layout constraint rather than an inferred consequence of three
  independently top-aligned Boxes.
- The excessive gap was not unequal padding. The compact identifier column inherited the wide
  primary-column 64dp readable text floor, which became 84dp after the unchanged 10+10dp cell
  padding. Compact identifiers now use measured content width, a 56dp hard safety minimum, and 2dp
  pixel-rounding safety. The fixed fixture resolves to approximately 72dp.
- The compact rule applies only when the first column is genuinely label-like. A long-text first
  column retains the existing primary-column readable floor.
- Final device evidence keeps `I、V6` and `rsR′、M 形、兔耳` on one line while moving the second
  column visibly closer to the first. All three header first baselines are aligned.

## Root-cause audit

The real rendering chain before this fix was:

- `TableBlockView.bodyStyle` rendered body cells.
- `bodyStyle.copy(fontWeight = SemiBold)` rendered header cells.
- There was no compact-column header branch and no per-column font-size calculation.
- All header and body strings passed through `InlineMarkdownParser.parse`. Strong spans changed only
  weight; code spans retain their existing code semantics.
- There was no `widthIn`, `minimumInteractiveComponentSize`, `scaleX`, text autosize or ellipsis.
  Display cells used `softWrap=true` and unlimited `maxLines`.
- Fixture alignments were all `Start`, and padding values were already shared.

The weak points were structural: each cell performed another local TextStyle `copy`, while
`letterSpacing`, `platformStyle` and `textDirection` remained implicit. Header cells reused the
generic body-row composable, so each Text owned its own cell height rather than participating in an
explicit shared header-row geometry contract. The row reached the tallest child, but equal full
cell height and baseline metadata were not directly verifiable.

## Implementation

One `resolvedHeaderTextStyle` is created per table and passed unchanged to every header cell. It
explicitly resolves:

- the same table font size and line height for every column;
- `FontWeight.SemiBold`;
- `FontFamily.Default`;
- `letterSpacing = 0sp`;
- `TextAlign.Start`;
- `TextDirection.ContentOrLtr`;
- `PlatformTextStyle(includeFontPadding = false)`.

The dedicated `TableHeaderRow` uses `IntrinsicSize.Min` for one shared row height. Every cell fills
that height, uses identical horizontal/vertical padding, and places text at `TopStart`. A single
divider follows the shared row. Body rows remain on the existing renderer and typography.

An optional diagnostic callback exposes actual resolved header metrics and `TextLayoutResult`
baseline/height/line-count data to the isolated QA host. It does not use OCR and does not alter
measurement when absent.

## Automated coverage

- All header cells share font size, weight, line height, letter spacing, family, platform style,
  text direction and start alignment.
- Header source contains no autosize, `scaleX`, `widthIn`, interaction-minimum or per-cell style
  copy branch.
- Inline strong retains unspecified font size/letter spacing/family and changes weight only.
- QA structural assertions compare all three header top/bottom coordinates, global first baselines,
  divider span, header/body content-left positions, and metrics before/after horizontal scroll.
- R3 width geometry, accepted two-column behavior and all 27 reader-setting combinations remain in
  their existing suites.

## Validation

- `:app:testDebugUnitTest`: PASS, 606/606.
- Table JVM/structural suite: PASS, 37/37.
- Reader configuration matrix: PASS, 27/27.
- `:app:lintDebug`: PASS.
- `:app:assembleDebug`: PASS.
- `:app:assembleQa`: PASS.
- `:app:compileQaAndroidTestKotlin`: PASS.
- `:app:assembleQaAndroidTest`: PASS.

After `db04f865` reconnected, the isolated QA APKs were installed with `adb install -r` and the
single table instrumentation targeted only `com.markleaf.app.qa`. The HyperOS runner produced no
completion and was safely stopped after 124 seconds. QA device instrumentation is `blocked`, not
passed. No instrumentation targeted `com.markleaf.app`.

## Main-package data safety

- Device: `25098PN5AC / db04f865`.
- Installation method: `adb install -r dist/markleaf-debug-markdown-table-header-parity.apk`.
- Pre-install backup: `.local-backups/markleaf-debug/com-markleaf-app-20260714T080259Z`.
- Post-install backup: `.local-backups/markleaf-debug/com-markleaf-app-20260714T080327Z`.
- Each backup: 8 payload files, 837,632 bytes.
- Pre/post archive SHA-256:
  `5C02FDF09575F039B512EEC8E233E391649C7193C117E1813C20A10C58F9F88C`.
- Changed payload hashes: 0.

Main-package data safety is PASS. The main package was not uninstalled or cleared.

The final R4 installation used fresh immediate backups:

- Before: `.local-backups/markleaf-debug/com-markleaf-app-20260714T083358Z`.
- After: `.local-backups/markleaf-debug/com-markleaf-app-20260714T083413Z`.
- Each: 8 payload files, 837,632 bytes.
- Both archive SHA-256:
  `9B289DDCC328B6D3BAFADCF53CAC3A619E168D3218B7AE22893B35685F1FBFC6`.
- Changed payload hashes: 0.

## Real-reader visual evidence

- Two-column short: `dist/performance/markdown-table-header-parity-two-short.png`.
- Two-column long: `dist/performance/markdown-table-header-parity-two-long.png`.
- Three-column initial: `dist/performance/markdown-table-header-parity-three-start.png`.
- Three-column middle: `dist/performance/markdown-table-header-parity-three-middle.png`.
- Three-column right: `dist/performance/markdown-table-header-parity-three-right.png`.
- R4 final initial position: `dist/performance/markdown-table-header-parity-r4-final.png`.

Objective inspection confirms that all visible three-column headers use the same apparent size and
weight, share the same top alignment, and retain stable row/divider geometry at the initial,
middle, and right scroll positions. Accepted two-column and R3 width behavior remain intact.
These images are evidence for user review, not automatic human acceptance.

## Artifact

- Path: `dist/markleaf-debug-markdown-table-header-parity.apk`
- Size: 74,841,758 bytes
- Modified: 2026-07-14 16:32:53 +08:00
- SHA-256: `9EB5B982E3AFD58848C99D3646E03B856CF3D3C2EB960B3BDDFAD10F1A6B23C7`

## Files

- `app/src/main/java/com/markleaf/app/ui/viewer/components/TableBlockView.kt`
- `app/src/test/java/com/markleaf/app/ui/viewer/components/TableHeaderParityTest.kt`
- `app/src/test/java/com/markleaf/app/ui/viewer/components/MarkdownTableLayoutEngineTest.kt`
- `app/src/qa/java/com/markleaf/app/ui/viewer/components/MarkdownTableQaActivity.kt`
- `app/src/androidTest/java/com/markleaf/app/ui/viewer/components/MarkdownResponsiveTableInstrumentedTest.kt`
- `.memory/active_object.yaml`
- `.memory/CURRENT_STATE.md`
- `docs/MARKLEAF_TABLE_HEADER_TYPOGRAPHY_AND_ALIGNMENT_PARITY_FIX_REPORT.md`

## Final acceptance

The user explicitly accepted R4. Two-column short/long tables, three-column width priority,
unified header size, shared first baseline, single-line `I、V6` and
`rsR′、M 形、兔耳`, natural third-column overflow, complete right endpoint, and the 27/27
configuration matrix are PASS. `human_visual_acceptance` is `accepted` and overall status is
`device_accepted`. QA instrumentation remains `blocked`, not PASS.
