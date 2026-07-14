# MARKLEAF_MARKDOWN_TABLE_CHATGPT_MOBILE_PARITY

**Date:** 2026-07-14
**Status:** implementation complete; host/build/data-safety pass; QA device instrumentation
blocked; human visual acceptance pending.

## Result

The renderer now uses one content-driven sizing path. The Markdown parser, table AST, fixture
source, inline parser, outer reader gesture system, and every non-table component are unchanged.

- Every column exposes measured readable, preferred, and maximum widths derived from header and
  body single-line `TextMeasurer` results.
- Two-column tables evaluate twelve finite first-column candidates from 28% through 72%; the second
  column receives the remainder. Their total target is measured preferred content width plus 16dp
  safety, capped at the viewport, so compact tables stay compact while verbose tables use the
  available reading width. Cost covers real row maximum/total height, maximum line count,
  unnecessary short-label/header wrapping, narrow text pillars, row-height imbalance, content
  allocation mismatch, and first-column width beyond its measured need.
- Two-column horizontal scrolling is allowed only when the two measured readable minima exceed the
  viewport. It is not selected by `COMPACT_LABEL_VALUE` or `VERBOSE_COMPARISON`.
- Three-or-more-column tables derive the same content metrics. Preferred widths may compress toward
  readable widths, but no more than 75% of their reducible range; content that would require
  excessive compression remains naturally wider than the viewport and scrolls.
- Initial scroll offset is zero. There is no 24dp preview, 22dp fade, reachability overlay, arrow,
  spacer, or artificial third-column shaping.
- `horizontalScroll` remains the only table scroll modifier. No custom `pointerInput`, WebView, or
  horizontal scrollbar was added.

## ChatGPT mobile visual parity

- Cell padding is 10dp horizontal and 10dp vertical.
- Table text uses a table-only 0.88 scale while preserving the reader's configured line-height
  ratio.
- Header weight is `SemiBold`; body weight is `Normal`.
- Body background is transparent.
- There is no complete outer border, rounded card, or vertical divider.
- Only subdued horizontal row dividers remain; the table aligns to the reader content edges.
- Natural wrapping uses unlimited lines and `TextOverflow.Clip`; no ellipsis is present.

## Fixed-fixture device observations

The original `/storage/emulated/0/inbox/MarkLeaf/markdown-table-human-acceptance.md` was used without
modification.

- Short two-column table: after comparison with the supplied ChatGPT screenshot, it uses a compact
  intrinsic total width instead of filling the viewport. `PR 间期` and `未见明显增宽` stay on one
  line, and the table is not horizontally scrollable.
- Long two-column table: measured approximately 60:40. Both headers and both body columns remain
  simultaneously readable in portrait without horizontal scrolling.
- Three-column table: portrait width naturally clips the real table and remains horizontally
  scrollable. No artificial reveal width or fade is present. The middle and right captures show
  horizontal movement, and the right endpoint makes the last column fully readable without added
  tail whitespace.
- Landscape: all three columns fit and no meaningful horizontal overflow remains.
- Dark mode: transparent body and subtle dividers blend with the reader background; no bright card
  or fade block appears.
- A vertical swipe beginning inside the table moved the outer reading page, confirming Y remains
  owned by the reader.

The fixed fixture contains no `**inline strong**` token. Therefore a truthful main-reader
inline-strong screenshot cannot be produced from this document. The eighth image is an honest
body/header weight reference. Existing host coverage confirms that inline strong parsing remains
bold; the fixture was not altered solely for screenshots.

## Automated validation

- The reader exposes three font sizes, three line-height ratios, and three page-margin levels. A
  Cartesian-product regression covers all 27 combinations and verifies compact-label readability,
  readable minima, viewport fit, and scroll decisions.
- `:app:testDebugUnitTest`: PASS, 601/601.
- Table JVM/structural suite: PASS, 32/32.
- `:app:lintDebug`: PASS.
- `:app:assembleDebug`: PASS.
- `:app:assembleQa`: PASS.
- `:app:compileQaAndroidTestKotlin`: PASS.
- `:app:assembleQaAndroidTest`: PASS.

The isolated QA APK and test APK were installed with target `com.markleaf.app.qa`. The selected
table instrumentation produced no completion output and timed out after 79 seconds during HyperOS
runner startup. QA device instrumentation is recorded as `blocked`, not passed. No instrumentation
was run against `com.markleaf.app`.

## Main-package data safety

- Device: `25098PN5AC / db04f865`.
- Install command: `adb install -r` only.
- Before: `.local-backups/markleaf-debug/com-markleaf-app-20260714T060052Z`.
- After: `.local-backups/markleaf-debug/com-markleaf-app-20260714T060056Z`.
- Each backup: 8 payload files, 837,632-byte archive.
- Both archive SHA-256 values:
  `AAD456F0148C3B71EAD1FF366A34D17FC4E11989F6616A9373B3D28B54444D73`.
- Changed payload hashes: 0.

## Artifact

- Path: `dist/markleaf-debug-markdown-table-chatgpt-parity.apk`
- Size: 74,841,758 bytes
- Modified: 2026-07-14 13:59:55 +08:00
- SHA-256: `E36F63563242F182991BE28632C02AB14C3343873EC922058B3742CDC92914AF`

## Real-reader screenshots

The three captures made after comparison with the supplied ChatGPT Android reference are:

1. `dist/performance/markdown-table-parity-r2-main-two-short.png`
2. `dist/performance/markdown-table-parity-r2-main-two-long.png`
3. `dist/performance/markdown-table-parity-r2-main-three-start.png`

Earlier full-position coverage remains available:

1. `dist/performance/markdown-table-parity-main-two-short.png`
2. `dist/performance/markdown-table-parity-main-two-long.png`
3. `dist/performance/markdown-table-parity-main-three-start.png`
4. `dist/performance/markdown-table-parity-main-three-middle.png`
5. `dist/performance/markdown-table-parity-main-three-right.png`
6. `dist/performance/markdown-table-parity-main-three-landscape.png`
7. `dist/performance/markdown-table-parity-main-dark.png`
8. `dist/performance/markdown-table-parity-main-body-weight-reference.png`

## Acceptance state

```yaml
implementation: complete
host_automated_validation: pass
build_validation: pass
main_package_data_safety: pass
qa_device_instrumentation: blocked
human_visual_acceptance: pending
```

Do not mark `device_accepted` until the user compares the real-reader captures and confirms visual
and gesture parity with the supplied ChatGPT Android reference.
