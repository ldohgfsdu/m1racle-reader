# R7 P0B1.1 Pointer Epoch and Zoom Session Report

## Previous-device result and audited failure frame

P0B1 v2 is **DEVICE FAIL**, not pending. On device, page 63 jumped near page 60, the
page-57 blue figure moved substantially under pinch, pointer-count transitions jumped, and
page 62 reopened at 1x with its precise local position lost.

The pre-fix production call chain was `ContinuousDocumentViewport.pointerInput` ->
`awaitEachGesture` -> `awaitPointerEvent` -> `ContinuousInteractionReducer.rebase` ->
`event.calculateCentroid/calculateZoom` -> `ContinuousDocumentGeometry.zoomAndPanFromFocal`.
The failing transition event first rebased on pointer count, then continued in the same event:

- old lines 628-645: 2->3/3->2 rebased using the centroid of **all** pressed pointers;
- old lines 646-660: that same event applied `documentState.scale * calculateZoom()`;
- old lines 676-704: 2->1 rebased and then immediately applied a single-finger focal transform.

No stable pointer IDs or span baseline existed. Existing logs had scale/focal values but no
pointer IDs or span, so they cannot identify a unique timestamp as the first bad frame. The
source audit does identify the bad class of frame: the first event whose pressed-pointer set
changed, because it was both a baseline reset and a visual transform.

## New pointer epoch data flow

`ContinuousPointerEpochReducer.frame` owns a pure state machine. Each epoch records the active
IDs, locked primary/secondary pair, base scale/scroll, pair midpoint, base span and focal
document coordinate. A pointer-set change (1->2, 2->1, 2->3, 3->2 or pair replacement):

1. preserves the current `ContinuousDocumentState` exactly;
2. chooses/retains a stable pair;
3. creates a new baseline from the current transform;
4. emits `POINTER_EPOCH_REBASE` and `POINTER_EPOCH_FRAME_SKIPPED`;
5. returns without zoom, pan or settle.

Within a fixed two-pointer epoch, extra pointers are ignored. Scale is
`baseScale * currentSpan / baseSpan`; centroid is the locked pair midpoint; scale and scroll
are applied together through one `zoomAndPanFromFocal` call and one geometry clamp.

For 2->1->2, both transitions are skipped frames. Subsequent single-pan uses baseline scroll
minus pointer displacement. For 2->3->2, the original pair remains locked while present; both
set changes are skipped. A missing member is replaced and rebased. SETTLING begins only after
all pointers are up and commits the last transform once; no page jump or restore request is
emitted by pinch.

## Semantic continuous viewport session

`PdfViewportSessionState` v1 stores mode, page index, page-local X/Y fractions, viewport-anchor
X/Y fractions, scale and generation. It never stores absolute scroll pixels. Geometry converts
between that schema and `ContinuousDocumentState`, so viewport resizing/orientation changes
restore the same page-local content.

Room database v6 adds nullable `pdfViewport*` and `pdfPageAnchor*` columns through
`MIGRATION_5_6`. Null columns are legacy-compatible: existing page/fraction history restores at
1x. Invalid scale rejects only the session and preserves the existing safe page. Out-of-range
session pages fall back to the existing persisted page. Continuous sessions are mode-isolated.
Restore priority is external navigation > mode switch > zoom session > startup > placeholder.

Only stable viewport callbacks update `PdfViewportSessionTracker`. Pinch/rebase frames cannot
write it. Settle, ordinary pan/scroll settle, ON_STOP, dispose and onCleared share an idempotent
generation-based flush; duplicate flushes do not rewrite the same session.

## Zoom quality diagnostics and tile design

New low-frequency logs are `ZOOM_QUALITY_TARGET`, `ZOOM_QUALITY_REQUEST`,
`ZOOM_QUALITY_READY` (including render duration), and `ZOOM_QUALITY_SWAP` (ready-to-swap delay).
Device timing remains unmeasured until the new APK is run. The code-imposed settle/idle delay is
200 ms, after which a lower-tier in-flight job may also be joined.

At the last measured 1220 px viewport width, continuous hysteresis selects HIGH at 2x/3x/4x,
so the current whole-page target is capped at 3072 px for all three scales. For a typical
1.414 aspect page, one ARGB_8888 3072x4344 bitmap is about 53.4 MB. Uncapped 3x and 4x whole
pages would be roughly 75.8 MB and 134.7 MB respectively, which is not an acceptable solution.

WPS-like fast sharpness therefore requires visible-region tiling. Minimal design:

- key: document ID, page, scale bucket, tile X/Y, render generation;
- tile: 512 px (optionally 768 on high-memory devices), one-tile visible margin;
- budget: about 48-64 MB foreground, LRU by distance from visible region;
- retain a whole-page preview underneath, atomically replace individual ready tiles;
- cancel stale generation/off-margin work while preserving PdfRenderer serialization.

No tile renderer was implemented in this task.
