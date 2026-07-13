# R7 P0C1.1 Page-Ahead Prefetch Handoff

## Baseline evidence

P0C1 tiles visibly sharpen during pinch, but baseline planning was reactive. The captured log
contained 300 `TILE_TARGET` and 58 rebase events but omitted `MarkLeafPdfZoom`, so request,
mutex, render and swap p50/p95 cannot be claimed. Rebase-to-target samples were p50 ~42ms and
p95 ~327ms because a keyed `LaunchedEffect` repeatedly cancelled its fixed 24ms delay.
`visiblePages(..., prefetchPages=0)` never targeted a page before intersection, and 173 target
lines exceeded the 32-key limit (maximum 78).

## Implementation

- Removed cancellable 24ms delay; stable-key dedup remains.
- `PdfViewportMotion`: EMA document velocity, abnormal-sample rejection, rebase skip,
  reversal confidence reset, 100ms near and 240ms far horizons.
- Corridor is current + near + far. Forward selects the next-page top band; backward selects
  the previous-page bottom band while preserving page-local X range.
- At 1x, idle warms only two 1.4x center tiles plus the directional page-ahead current bucket.
- Priority order is focal/center, visible, near, page-ahead, margin, speculative.
- Capacity reserves 20 urgent and 8 near/page-ahead slots within 32 active keys.
- Tile scheduling now uses one reorderable pending map and one worker. New urgent work can
  precede unstarted low-priority tiles; PdfRenderer remains serialized and running render is
  never interrupted.
- Current/page-ahead keys stay pinned by the existing 56MB byte LRU.

Logs added: `PREFETCH_CORRIDOR`, `PAGE_AHEAD_TARGET`, `PAGE_AHEAD_TILE`, and actual Canvas
`TILE_FRAME_METRIC`. New device capture must include both `MarkLeafR71` and `MarkLeafPdfZoom`.
Real center p50/p95, entry coverage and 1x frame impact remain device pending.

## Verification artifact

- JVM: PASS (full suite; unified total 446 tests)
- lintDebug: PASS
- assembleDebug: PASS
- androidTest compilation: PASS
- directed device instrumentation: PASS, 2/2 logic tests
- APK: `dist/markleaf-debug-r7-p0c1-1-h1.apk`
- Size: 74,757,306 bytes
- SHA-256: `A4A68C33849757B57A3AA3DD5011B80A12B45189CAC2741E4F8652920376A138`
- Visual/performance device acceptance: PENDING
