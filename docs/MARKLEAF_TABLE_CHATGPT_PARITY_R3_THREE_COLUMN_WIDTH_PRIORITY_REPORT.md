# MARKLEAF_TABLE_CHATGPT_PARITY_R3_THREE_COLUMN_WIDTH_PRIORITY

**Date:** 2026-07-14
**Status:** implementation complete; host/build/configuration/data-safety pass; QA device
instrumentation blocked; human visual acceptance pending.

## Scope and preserved behavior

R3 changes only the width policy for tables with three or more columns. The accepted two-column
solver and cost function are unchanged. The 27 reader-setting combinations, table padding, line
height, typography, horizontal dividers, parser/AST, inline rendering, outer vertical scroll and
horizontal gesture implementation are unchanged.

## Root cause

The previous wide-table path proportionally compressed every column toward its measured readable
minimum whenever the preferred total exceeded the viewport. Its objective was to approach the
viewport width, which made the second primary-content column narrower solely to reveal more of the
third column. In the fixed fixture this caused `rsR′、M 形、兔耳` to wrap while most of the third
header was already visible.

## R3 width policy

- The first identifier column uses its compact measured requirement and receives no primary-column
  safety or meaningless wide-screen expansion.
- Each later primary-content column derives preferred content width from its header and unwrapped
  short cells. An 8dp safety allowance protects glyph and measurement rounding.
- Measured readable and maximum bounds still apply. Long cells naturally wrap; no ellipsis or
  maximum line count was introduced.
- If preferred total exceeds the viewport, preferred widths remain intact and the whole table
  overflows through the existing `horizontalScroll`.
- If every column fits, remaining width is shared only by primary-content columns.
- No fixed third-column preview, fade, arrow, spacer, custom pointer input or fixture-specific
  production condition exists.

## Fixed parity geometry

The host golden uses the unchanged `markdown-table-human-acceptance.md` fixture and default parity
viewport metrics. It verifies:

1. The first column ends inside the viewport.
2. The second header ends inside the viewport.
3. The second-column width holds the unwrapped `rsR′、M 形、兔耳` measurement plus cell padding.
4. The third column starts before and ends after the viewport right edge.
5. Initial scroll offset is zero and the table is horizontally scrollable.
6. At maximum scroll the third-column end equals the viewport end, leaving no tail whitespace.
7. Production table source contains no fixture-text special case.

The isolated Compose test fixture now matches all three rows of the real parity fixture and checks
the same start/end geometry plus the single-line row height.

## Validation

- `:app:testDebugUnitTest`: PASS, 602/602.
- Table JVM/structural suite: PASS, 33/33.
- Reader configuration matrix: PASS, 27/27.
- `:app:lintDebug`: PASS.
- `:app:assembleDebug`: PASS.
- `:app:assembleQa`: PASS.
- `:app:compileQaAndroidTestKotlin`: PASS.
- `:app:assembleQaAndroidTest`: PASS.

One isolated QA attempt was made through the guarded QA command. The target was
`com.markleaf.app.qa`; the HyperOS runner did not complete within 124 seconds. The QA app/test
processes were force-stopped. This is recorded as `blocked`, not passed. No instrumentation ran
against `com.markleaf.app`.

## Main-package data safety

- Device: `25098PN5AC / db04f865`.
- Installation: `adb install -r` only.
- Before: `.local-backups/markleaf-debug/com-markleaf-app-20260714T062519Z`.
- After: `.local-backups/markleaf-debug/com-markleaf-app-20260714T062536Z`.
- Both backups: 8 payload files; 837,632-byte archive.
- Both archive SHA-256 values:
  `C810EAEAB7782A97FA427550533D3CA749C98B1EF40556CC5B32F313DBB0F5EF`.
- Changed payload hashes: 0.

## Artifact

- Path: `dist/markleaf-debug-markdown-table-chatgpt-parity-r3.apk`
- Size: 74,841,758 bytes
- Modified: 2026-07-14 14:21:52 +08:00
- SHA-256: `47C8E9F813F2F318B94D49ACCE492D8585772BC2833EF3E67CE83BDB0B49E4A4`

## R3 files

- `app/src/main/java/com/markleaf/app/ui/viewer/components/MarkdownTableLayout.kt`
- `app/src/test/java/com/markleaf/app/ui/viewer/components/MarkdownTableLayoutEngineTest.kt`
- `app/src/test/resources/fixtures/markdown-table-human-acceptance.md`
- `app/src/qa/java/com/markleaf/app/ui/viewer/components/MarkdownTableQaActivity.kt`
- `app/src/androidTest/java/com/markleaf/app/ui/viewer/components/MarkdownResponsiveTableInstrumentedTest.kt`
- `.memory/active_object.yaml`
- `.memory/CURRENT_STATE.md`
- `docs/MARKLEAF_TABLE_CHATGPT_PARITY_R3_THREE_COLUMN_WIDTH_PRIORITY_REPORT.md`

## Real main-package screenshots

1. `dist/performance/markdown-table-parity-r3-main-two-short.png`
2. `dist/performance/markdown-table-parity-r3-main-two-long.png`
3. `dist/performance/markdown-table-parity-r3-main-three-start.png`
4. `dist/performance/markdown-table-parity-r3-main-three-middle.png`
5. `dist/performance/markdown-table-parity-r3-main-three-right.png`
6. `dist/performance/markdown-table-parity-r3-main-three-landscape.png`

The initial screenshot visibly keeps `rsR′、M 形、兔耳` on one line and naturally clips the third
column. The right-end screenshot shows the complete third column. Landscape displays all columns
without an artificial preview or fade.

## Acceptance state

```yaml
implementation: complete
host_automated_validation: pass
build_validation: pass
configuration_matrix: pass
main_package_data_safety: pass
qa_device_instrumentation: blocked
human_visual_acceptance: pending
```

Do not mark `device_accepted` until the user confirms the R3 real-reader screenshots and gesture
behavior.
