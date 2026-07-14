# MARKLEAF_MARKDOWN_TABLE_CONTENT_DRIVEN_LAYOUT_V2

**Date:** 2026-07-14
**Status:** implementation complete; host/build validation pass; main-package data safety pass;
QA device instrumentation blocked; human visual acceptance pending.

## Scope and result

The Markdown parser, table AST, inline strong/code/link semantics, reader vertical scrolling,
PDF/H1, Room/persistence, and all other Markdown blocks are unchanged.

- Two-column tables use one content-driven candidate optimizer. The diagnostic
  `COMPACT_LABEL_VALUE` / `VERBOSE_COMPARISON` classification remains available but no longer
  selects widths or scrolling behavior.
- The finite first-column candidates are 30%, 35%, 40%, 45%, 50%, 55%, 60%, 65%, and 70%.
  `TextMeasurer` measures every header/body cell at every candidate's constrained width. Cost
  includes total row height (row maximum), maximum line count, short-label wrapping, narrow text
  pillars, content-volume balance, and unnecessary header wrapping.
- Each two-column text area has a centralized 112dp readable minimum plus the existing 10dp
  horizontal padding on each side. Horizontal scrolling is enabled only when both readable column
  minima cannot fit the viewport. Normal phone portrait widths fit both columns.
- Three-or-more-column tables derive content-led readable/preferred/maximum widths. A table that
  fits expands to the viewport; an overflowing table preserves those widths and scrolls. There is
  no fixed preview spacer, first-two-column compression, fade overlay, or reachability affordance.
- The visual container has no complete border, rounded card clip, body/header card background, or
  vertical divider. Only subdued horizontal row dividers remain. Body text is explicitly
  `FontWeight.Normal`; the header is `Medium`. Inline `strong` spans remain bold.
- The existing Compose `horizontalScroll` remains the only table gesture modifier. No
  `pointerInput`, WebView, fixed scrollbar, or vertical gesture interception was added.

## Performance and cache identity

The whole-table measurement and optimization are inside one Compose `remember`. Its identity
includes the table value and content hash, viewport width, density, font scale, body/header
typography and inline styles, and layout direction. Scroll state is deliberately excluded. The
optimizer evaluates only the nine configured candidates and cannot rerun from horizontal scroll.

## Changed files

- `app/src/main/java/com/markleaf/app/ui/viewer/components/MarkdownTableLayout.kt` — unified pure
  sizing policy, candidate cost model, readable minima, and natural 3+ column overflow.
- `app/src/main/java/com/markleaf/app/ui/viewer/components/TableBlockView.kt` — constrained
  TextMeasurer candidate measurement, stable cache identity, natural scroll clip, and正文-style
  visual structure.
- `app/src/test/java/com/markleaf/app/ui/viewer/components/MarkdownTableLayoutEngineTest.kt` — 22
  dedicated JVM/structural tests for the V2 behavior.
- `app/src/androidTest/java/com/markleaf/app/ui/viewer/components/MarkdownResponsiveTableInstrumentedTest.kt`
  — QA assertions/screenshots for fitted two-column tables, natural three-column overflow, no
  fades, landscape fit, dark mode, scroll stability, and table-origin vertical page scrolling.

## Host and build verification

- `:app:testDebugUnitTest`: PASS, 591/591 (table-specific 22/22).
- `:app:lintDebug`: PASS.
- `:app:assembleDebug`: PASS.
- `:app:assembleQa`: PASS.
- `:app:compileQaAndroidTestKotlin`: PASS.
- `:app:assembleQaAndroidTest`: PASS.
- Main manifest remains local-only and has no `INTERNET` permission.

## Device validation and data safety

- Device: `25098PN5AC / db04f865`.
- QA target applicationId: `com.markleaf.app.qa`.
- The isolated QA instrumentation runner hung during HyperOS runner startup and produced no test
  completion. It was stopped after 69 seconds. Status is `blocked`; it is not recorded as a pass.
- No instrumentation was run against `com.markleaf.app`.
- The main package was updated only with
  `adb install -r dist/markleaf-debug-markdown-table-content-driven-v2.apk`; no uninstall or clear
  was used.
- Pre-install backup: `.local-backups/markleaf-debug/com-markleaf-app-20260714T050604Z`.
- Post-install backup: `.local-backups/markleaf-debug/com-markleaf-app-20260714T050621Z`.
- Each backup contains seven files totaling 836,096 bytes. Both archive SHA-256 values are
  `8F4E6D9E089A24DEC8F630EAF35BF9E9760F899F549E2C287A58271A833EBD82`; payload hash changes: 0.

## Real-reader visual evidence

All images were captured from the real main-package reading page using the existing
`markdown-table-human-acceptance.md` fixture:

1. `dist/performance/markdown-table-v2-main-two-short.png`
2. `dist/performance/markdown-table-v2-main-two-long.png`
3. `dist/performance/markdown-table-v2-main-three-start.png`
4. `dist/performance/markdown-table-v2-main-three-middle.png`
5. `dist/performance/markdown-table-v2-main-three-right.png`
6. `dist/performance/markdown-table-v2-main-three-landscape.png`
7. `dist/performance/markdown-table-v2-main-dark.png`

Observed evidence: `PR 间期` remains on one line; the long two-column fixture gives both columns
normal reading width and fits without horizontal scrolling; the three-column table scrolls through
start/middle/right without the removed fixed preview or fade overlay; the last column is fully
reachable; landscape fits all three columns; dark mode has no bright card/fade block. These images
are evidence for human review, not automatic visual acceptance.

## Artifact

- Path: `dist/markleaf-debug-markdown-table-content-driven-v2.apk`
- Bytes: 74,841,758
- SHA-256: `9D5457E3FDC05E473C8B03616DB90280569934F58BF1CEAE7C92B855F40FB817`

## Acceptance state

```yaml
implementation: complete
host_automated_validation: pass
build_validation: pass
main_package_data_safety: pass
qa_device_instrumentation: blocked
human_visual_acceptance: pending
```

Do not mark `device_accepted` until the user confirms two-column readability, wide-table scrolling,
typography, vertical page gesture feel, landscape behavior, and dark-mode clarity.
