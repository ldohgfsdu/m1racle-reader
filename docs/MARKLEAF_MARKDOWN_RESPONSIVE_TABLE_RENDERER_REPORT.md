# MarkLeaf Markdown responsive table renderer report

Date: 2026-07-14
Baseline HEAD: `4241b3b4a0a9af6d99e6f83a96092d19b6e2065e`
Status:

```yaml
implementation: complete
host_automated_validation: pass
build_validation: pass
main_package_data_safety: pass
qa_device_instrumentation: blocked
human_visual_acceptance: accepted
overall: device_accepted
```

## Scope lock

This object changes only Markdown table rendering and its test/QA support. It does not modify the
PDF viewer, H1 gesture physics, Room/persistence, Markdown source, title normalization, editor,
other Markdown block components, or Git history.

## 1. Existing implementation audit and root cause

The real production chain is:

```text
ViewerViewModel
  -> MarkdownParser.parse                         (in-house, no third-party Markdown library)
  -> MarkdownBlock.Table(headers, rows, alignments)
  -> ViewerScreen.ReaderDocumentBody LazyColumn  (page Y scroll)
  -> MarkdownBlockView
  -> TableBlockView                              (table X scroll)
  -> InlineMarkdownParser                        (bold/italic/code/link spans in each cell)
```

Before this object, `TableBlockView` already had a `horizontalScroll` container and did not use
equal `weight(1f)`. It also did not explicitly set `maxLines=1` or ellipsis; Compose Text therefore
allowed wrapping by default. The defect was the width contract:

- every header/body cell independently used only `widthIn(min=96.dp)`;
- there was no whole-table preferred-width pass;
- the same logical column could have different widths in different rows;
- the Column/Rows had no stable shared table total width;
- the outer rounded clip was correct, but unstable row widths made the right-side scroll extent and
  perceived clipping/compression unpredictable;
- a full-height edge fade could also stretch a short table container; it is now a non-sizing
  `matchParentSize` overlay.

There was no fixed cell width, no IntrinsicSize, no cell ellipsis and no `clipToBounds` on the text.
The outer rounded clip remains intentional so scrolled content stays inside the table shape.

## 2. Modified files and symbols

| File | Symbols/change |
|---|---|
| `ui/viewer/components/MarkdownTableLayout.kt` | New `MarkdownTableLayoutEngine`, centralized tokens, `TableColumnMeasurement`, `TableColumnMetrics` |
| `ui/viewer/components/TableBlockView.kt` | Whole-table remembered measurement, fixed shared column vector, responsive scroll container, explicit multiline cell text, themed compact visuals |
| `ui/viewer/components/MarkdownTableLayoutEngineTest.kt` | 12 JVM/structural cases |
| `src/test/resources/fixtures/markdown-responsive-tables.md` | Fixed two/three/five-column and mixed-inline fixture |
| `src/androidTest/.../MarkdownResponsiveTableInstrumentedTest.kt` | QA scroll, remeasurement, vertical ownership, theme and screenshot checks |
| `src/qa/.../MarkdownTableQaActivity.kt` + QA manifest | QA-only isolated preview/test host; absent from debug/release |

`MarkdownParser`, `MarkdownBlock`, `InlineMarkdownText`, PDF/H1, Room and editor files were not
modified by this object.

## 3. Column-width algorithm

For each logical column, one measurement pass considers the header and every body cell's inline
Markdown result, single-line TextMeasurer width, plain character count and horizontal padding.
Tokens are centralized:

```text
absolute minimum       72dp
normal minimum         96dp
preferred minimum     136dp
normal preferred max  240dp
long-text max          280dp
cell padding         10x6dp (horizontal x vertical)
two-column first cap  34% of viewport
wide-table next peek  24dp
```

For two columns, the first column is treated as the compact role/label column: its actual measured
need is clamped to a 72dp floor and a 34% viewport ceiling. The second column receives every
remaining viewport pixel, so verbose medical descriptions get the dominant reading width and the
table never becomes an equal split. For three or more columns, preferred widths remain scrollable,
but the first two columns are compacted to their readable floor when needed so both fit initially
with a 24dp glimpse of the next column as a scroll cue. Later columns keep their preferred width.

The calculation is remembered by table content, body/header typography, inline styles, viewport
width and density. Scroll state is deliberately not a key; device automation confirmed the
measurement counter did not change during horizontal scroll.

## 4. Cell typography and gesture ownership

Every cell uses the same fixed column width for every row, top row alignment, `softWrap=true`,
`TextOverflow.Clip`, and `maxLines=Int.MAX_VALUE`. It keeps the reader font family/line-height and
`InlineMarkdownParser` annotations. Header text is semibold at body scale. The mobile-density pass
reduces padding to 10x6dp, outer vertical spacing to 4dp, corner radius to 3dp and border to 0.5dp.
The body no longer has a card fill; only the header keeps a light theme-derived tint.

There is no custom table `pointerInput`. The table's orientation-specific `horizontalScroll`
consumes X only when its total width exceeds the viewport. The existing outer vertical reader
container continues consuming Y. A QA gesture beginning inside the table moved the page vertically,
while a horizontal gesture moved the wide table and did not rerun column measurement.

## 5. Automated verification

| Gate | Result |
|---|---|
| Full JVM | PASS, 581/581; 0 failed/errors/skipped |
| Responsive-table JVM/structural | PASS, 12/12 |
| lintDebug | PASS |
| assembleDebug | PASS |
| assembleQa | PASS |
| compileQaAndroidTestKotlin | PASS |
| assembleQaAndroidTest | PASS |
| QA physical-device Compose scenes | PASS, 3/3 via direct isolated runner |

The three device scenes covered two-column fit, three-column start/end scroll and no remeasurement;
a vertical swipe starting inside the table moving the page; and both MarkLeaf light/dark themes.
Gradle UTP remained unreliable on this HyperOS device, so the already installed isolated QA
instrumentation was run directly after explicitly foregrounding its QA-only host. No main-package
instrumentation or data operation occurred.

## 6. Screenshot evidence

- Two columns, fitted with natural wrapping:
  `dist/performance/markdown-table-device-two-column.png`
- Three columns, initial position with right-edge scroll cue:
  `dist/performance/markdown-table-device-three-start.png`
- Three columns, scrolled to the end with the last column fully visible:
  `dist/performance/markdown-table-device-three-end.png`

UI-tree evidence after the end swipe placed the final-column cells at `[735, ...][1122, ...]`
inside the table viewport `[52,85][1168,699]`.

## 7. Debug artifact

```text
Path: dist/markleaf-debug-markdown-responsive-table.apk
Bytes: 74,841,758
Modified: 2026-07-14 08:57:01 +08:00
SHA-256: 2CA9DD1CE8AD4E85E1F1CC3F939A1C772FC0EB363802D76CBEBD8D3B5F50B79F
```

Host automation and build validation are complete. QA device instrumentation for the current
round is blocked at HyperOS runner startup. Human confirmation is still required for visual
density, horizontal/vertical gesture feel and dark-mode clarity; final visual acceptance is
therefore **PENDING**.

## 8. No-partial-glyph scroll affordance (2026-07-14)

The forced 24dp third-column text peek was removed. For a portrait table with three or more
columns, if the preferred widths of columns one and two fit the viewport, the primary second
column expands to the viewport boundary and column three begins at or beyond that boundary. If
the first two preferred widths already exceed the viewport, they retain readable preferred
widths instead of being compressed into narrow columns. Total table width and end reachability
remain unchanged.

Scrollable wide tables now draw a 22dp theme-background edge fade as a non-measuring overlay.
The right fade follows `canScrollForward`, the left fade follows `canScrollBackward`; neither
overlay has pointer input or contributes a spacer, height, or column width. Two-column tables and
wide viewports that fit every column create neither affordance.

Host/build verification: 584/584 JVM tests and 15/15 table tests passed; lintDebug,
assembleDebug, assembleQa, compileQaAndroidTestKotlin and assembleQaAndroidTest passed. The
isolated QA test APK compiled, but the direct runner on device `db04f865` again hung after
instrumentation start and was force-stopped without touching the main package. Therefore QA
device instrumentation is **BLOCKED**, and this round is not a complete automated-validation pass.

The main package was installed only with `adb install -r`. Pre/post backups each contained seven
payload files and had identical archive SHA-256
`EFCB08EF764E89E8EC9E4FBD69FC1A5C9CF0CA72D8F08A376DCD7EAEF50CE018`, proving the database,
WAL/SHM, preferences and imported documents were unchanged. Real-reader screenshots are under
`dist/performance/markdown-table-affordance-main-*.png`; the device's multiple-display screenshot
service intermittently produced incomplete frames, so human acceptance remains pending.

Artifact: `dist/markleaf-debug-markdown-table-scroll-affordance.apk`, 74,841,758 bytes,
SHA-256 `96599FE51D63DC1AB4497ED17649AEBBCE1FB500F5267B505C35B5679450A3B9`.

## 9. Two-column content classification (2026-07-14)

The two-column path now classifies original plain-text character distributions before constrained
layout. `COMPACT_LABEL_VALUE` requires first-column median <= 8, P90 <= 14 and no more than 15%
of cells over 20 Unicode code points. Other two-column tables use `VERBOSE_COMPARISON`.

Compact label columns use the longest unwrapped label measurement plus existing 10dp-per-side
padding and an 8dp safety margin, with an 88dp floor and 38% viewport cap. The descriptive column
owns the remainder. Verbose comparison columns use their content-led preferred widths, 140dp and
200dp readable minima, and the existing 280dp long-content maximum. They scroll in portrait when
their readable total exceeds the viewport and distribute spare width when a landscape/tablet
viewport fits both columns. Three-or-more-column sizing and edge fades were not changed.

Host/build evidence: JVM 589/589 and table-specific 20/20 passed; lintDebug, assembleDebug,
assembleQa, compileQaAndroidTestKotlin and assembleQaAndroidTest passed. The isolated QA direct
runner again hung at startup on HyperOS and was stopped after 54 seconds, so
`qa_device_instrumentation` remains `blocked`.

The main package was installed only with `adb install -r`. Pre/post backups each contained seven
payload files; no payload hash changed, and both archive SHA-256 values were
`003BC1EAB49B17864DD0780D09429B0E73FCB7ECBEE67E2B250D35662E6C5E66`.

Artifact: `dist/markleaf-debug-markdown-table-two-column-classification.apk`, 74,841,758 bytes,
SHA-256 `272D1C508C9E06761C85D06DD01C80696B77ACD0CFF63E5905996C4AB3843B72`.

Acceptance remains: implementation complete; host automation PASS; build validation PASS;
main-package data safety PASS; QA device instrumentation BLOCKED; human visual acceptance PENDING.

## 10. R4 final acceptance (2026-07-14)

R4 supersedes the earlier pending visual statuses. Human review accepted the short and long
two-column layouts, three-column width priority, unified header typography and first-baseline
alignment, single-line `I、V6` and `rsR′、M 形、兔耳`, natural third-column overflow, and complete
right endpoint. All 27 reader-setting combinations remain PASS.

Final evidence: JVM 606/606 PASS, table-specific 37/37 PASS, configuration matrix 27/27 PASS,
lintDebug PASS, and all requested Debug/QA build and AndroidTest compilation tasks PASS. Main
package data safety is PASS. The HyperOS QA runner remains BLOCKED and is not reported as PASS.

Final artifact SHA-256:
`9EB5B982E3AFD58848C99D3646E03B856CF3D3C2EB960B3BDDFAD10F1A6B23C7`.

Final status: `human_visual_acceptance: accepted`; `overall: device_accepted`.
