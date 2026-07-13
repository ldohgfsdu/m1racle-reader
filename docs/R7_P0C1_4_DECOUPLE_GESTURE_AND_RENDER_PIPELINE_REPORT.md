# R7 P0C1.4 Decouple Gesture and Render Pipeline

**Date:** 2026-07-13  
**Implementation:** PASS  
**Physical-device acceptance:** PASS

## Local checkpoint classification

`MARKLEAF_R7_P0C1_4_ACCEPTED_LOCAL_CHECKPOINT` records the accepted P0C1.4 implementation and
tests in a local Git commit. Scope boundaries at checkpoint time:

- P0C1.4 gesture/render decoupling: `device_accepted` on `db04f865` / `25098PN5AC` at 2.5x;
- P0C1.1 page-entry visual validation: `pending`;
- H1 horizontal zoom/pan: `device_fail_tuning_required` and unchanged by this checkpoint;
- P0A database device tests: `pending_not_executed`; their androidTest sources compile, but no
  physical-device Room migration/transaction result is inferred;
- APKs, raw `dist/performance` logs, build directories and local configuration are not committed.

## Original coupling audit

Production call chain before this task:

```text
ContinuousDocumentViewport pointerInput
→ documentState mutable state
→ recomposition
→ LaunchedEffect(transform/focus/rebase/geometry keys)
→ PdfTilePlanner.requests
→ PdfViewerViewModel.requestPredictiveTiles
→ TileWorkQueue.submit
→ temporary tileWorkerJob
→ PdfPageRenderer.renderTile (IO + renderer mutex)
→ ReadyBase(current, tileEntries=...)
→ Canvas
```

Answers to the required audit:

1. **Planner during DRAGGING/PINCHING:** there was no explicit phase rejection in the tile
   planner, but planning depended on a per-transform `LaunchedEffect`. Rapid pointer frames could
   replace/cancel an effect before it executed, so “nominally allowed” did not mean continuously
   scheduled.
2. **Pointer-frame cancellation:** no running `PdfRenderer.Page.render` was forcibly cancelled,
   but the planner effect was recreated every frame. `submit()` replaced `activeTileKeys`, removed
   pending keys outside the latest exact 32 and could enqueue the same key again after the worker
   removed it from pending to render it.
3. **Ready commit gate:** detail tile ready was not blocked by PINCHING/DRAGGING. It was blocked by
   exact active-key and generation checks. The swap log incorrectly hard-coded `interactionPhase=IDLE`.
4. **Active set replacement:** yes. Every submit assigned `activeTileKeys = bounded.keys` and
   immediately removed other pending items.
5. **collectLatest:** the tile path did not use `collectLatest`; the keyed `LaunchedEffect` supplied
   equivalent cancellation/restart pressure at the planner boundary.
6. **Settle dependency:** detail tiles did not intentionally wait for settle. Whole-page bitmap
   identity did. Device behavior arose because useful tile plans/work were repeatedly lost, making
   release/settle the first quiet interval where work survived.
7. **Absolute freeze:** `DisplayMergePolicy` freezes whole-page bitmap identity and the composed
   page structure in PINCHING/SETTLING. Detail tiles were not directly frozen. P0C1.4 preserves the
   structural freeze and isolates detail overlay publication from it.
8. **Pointer coroutine scheduling:** pointer input did not call PdfRenderer directly. It wrote
   Compose transform state, and rendering was indirectly launched by recomposition effects.
9. **Shared lock/wait chain:** pointer input and PdfRenderer did not share the renderer mutex. The
   coupling was lifecycle/state invalidation, not mutex contention. PdfRenderer remains serial.
10. **Old pointer-down logs:** device evidence showed no sustained useful request/render/swap while
    held. The old logging could not prove phase-at-swap because it always printed IDLE.

## New producer / consumer architecture

```text
Pointer/transform update
→ PdfViewportSnapshot
→ AtomicReference latest-slot + Channel.CONFLATED wakeup
→ one 16 ms planner actor
→ PdfTilePlan
→ TileWorkQueue priority/hysteresis update
→ one ViewModel-lifetime renderer actor
→ serial PdfRenderer
→ 16 ms overlay-only batch
→ stable page Canvas
```

`PdfViewportSnapshot` contains document/geometry generation, document-space viewport, scale,
focal point, motion/zoom velocity, phase, timestamp, sequence, pointer state and rebase marker. It
contains no bitmap and publication is non-suspending.

The producer calls only lock-free `LatestViewportConflator.offer()` and `trySend(Unit)`. Sixty
pointer frames still create one actor and at most one pending snapshot. Pointer input never waits
for planning, queue work or PdfRenderer.

The planner is one `LaunchedEffect(documentId, featureFlag)` actor. It does not use
`collectLatest`; after its 16 ms throttle it polls the newest snapshot and discards intermediate
snapshots before planning. A new viewport does not cancel an already started tile render.

The renderer actor starts once in `PdfViewerViewModel.init`, waits on a conflated wake signal and
drains the serial queue. Pointer down/up does not start or cancel it. Document open increments the
document generation and clears old queue/cache state without converting pointer lifecycle into a
renderer lifecycle.

## Cancellation, hysteresis and priority

Queue priority is:

1. focal and viewport center;
2. other current visible tiles;
3. current leading edge;
4. near corridor;
5. page-ahead;
6. margin/speculative.

Admission reserves current visible work before leading/near and page-ahead. Page-ahead cannot fill
all slots while current P0/P1 work is missing.

Each planner tick supplies the latest priorities, but `TileWorkQueue` retains up to eight previous
active keys when they remain in the latest region. Small motion therefore updates priority instead
of clearing all work. A key currently owned by PdfRenderer is tracked as `runningTileKey` and
cannot be enqueued again by a new snapshot.

Running work is allowed to finish and is validated afterward. Full incompatibility is limited to
document generation, geometry generation, page, rotation, fitted-width version or zoom bucket.
Tiles outside current/near/predicted usefulness can still be dropped.

## Gesture-period swap and Compose publication

`TileRenderPipelinePolicy.swapAllowed` permits valid detail tiles in:

- IDLE
- DRAGGING
- PINCHING
- SETTLING
- FLINGING

The whole-page base bitmap and composed page structure may remain frozen, but the detail overlay
can sharpen. Tile ready changes only the tile cache and pending overlay batch. It never changes
scale, scroll, page index, geometry, pointer epoch, restore state or zoom session.

The old per-tile `ReadyBase(current, tileEntries=...)` emission was removed. Completed tiles are
published through a dedicated `StateFlow<TileOverlaySnapshot>` once per 16 ms batch. Tile overlay
updates therefore do not copy unrelated `Ready` fields and do not trigger a new viewport snapshot.

## Logs

Debug builds now emit:

- `VIEWPORT_SNAPSHOT`
- `RENDER_PLANNER_TICK`
- `TILE_REQUEST`
- `TILE_READY`
- `TILE_SWAP`
- `CURRENT_TILE_LIFECYCLE`
- `GESTURE_RENDER_HEALTH`

Lifecycle records include target/request/render-start/ready/swap times, drop reason, snapshot
sequence and phase. Health records pointer state, actor activity, real newly-enqueued requests and
swaps in the last 500 ms, visible coverage, center p50/p95 and worker start count.

## Tests

New JVM tests: 23, covering the requested 20 cases plus leading-edge ordering, compatibility-only
invalidation and exact overlay bitmap identity. They also found and locked a real duplicate
in-flight render defect during implementation.

New production-viewport Compose/androidTest cases: 8:

- held drag plans before release;
- held pause permits tile swap;
- slow drag advances snapshot sequences;
- fast drag keeps planner/transform responsive;
- detail exists before release;
- release does not reset overlay to zero;
- current visible priority precedes page-ahead;
- tile swap does not move the document transform.

## Verification

```text
:app:testDebugUnitTest
PASS — 508 tests, 0 failures, 0 errors, 0 skipped

:app:lintDebug
PASS

:app:assembleDebug
PASS

:app:compileDebugAndroidTestKotlin
PASS
```

Build-time ADB result before device attachment:

```text
adb devices -l: no attached devices
Android emulator executable: not installed
```

The Android tests compiled but were not run through UTP. Physical-device acceptance was completed
later through direct production-app interaction and log capture, documented below.

## Physical-device acceptance

Device: `db04f865` (`25098PN5AC`). The freshly assembled debug APK was installed; the stale
P0C1.1/H1 APK was not used. The existing vertical-continuous PDF session was restored at 2.5x.

The 3-to-5-second hold-drag sequence included a pause without pointer-up. Production logs proved:

- `pointerDown=true`, `scale=2.5`, planner active, `workerStarts=1`;
- 4 `RENDER_PLANNER_TICK` events while held;
- 37 `TILE_REQUEST`, 37 `TILE_READY`, and 37 `TILE_SWAP` events with phase `DRAGGING`;
- current coverage fell to 0.6667 after exposing a new region, then rose to 1.0 before release;
- cumulative current-center request-to-swap p50/p95 was 61/102 ms;
- the acceptance batch's current-visible tiles (n=14) were p50/p95 55/55 ms;
- all held-gesture work (n=37) was p50/p95 55/126 ms;
- CENTER/VISIBLE requests were admitted on snapshot sequences 8 and 10; PAGE_AHEAD first appeared
  on sequence 11, after current visible work;
- a final confirmation batch recorded 5 held-gesture planner ticks and 8 request/ready/swap pairs,
  with a new center tile at 59 ms.

Raw final confirmation log:
`dist/performance/r7-p0c1-4-device-acceptance-20260713.log`.

## Modified files

- `app/build.gradle.kts`
- `app/src/main/java/com/markleaf/app/pdf/PdfTilePipeline.kt`
- `app/src/main/java/com/markleaf/app/pdf/PdfTileRendering.kt`
- `app/src/main/java/com/markleaf/app/pdf/TileWorkQueue.kt`
- `app/src/main/java/com/markleaf/app/ui/pdf/ContinuousDocumentViewport.kt`
- `app/src/main/java/com/markleaf/app/ui/pdf/PdfViewerViewModel.kt`
- `app/src/main/java/com/markleaf/app/ui/pdf/PdfViewerScreen.kt`
- `app/src/test/java/com/markleaf/app/pdf/R7P0c14GestureRenderDecouplingTest.kt`
- `app/src/androidTest/java/com/markleaf/app/ui/pdf/R7GestureLifecycleInstrumentedTest.kt`
- `.memory/active_object.yaml`
- `.memory/CURRENT_STATE.md`

The geometry visual sample is recorded as passed, so the P0C1.4 artifact enables the detail tile
overlay by default. Geometry math itself was not changed in this task.

## APK

```text
Path: dist/markleaf-debug-r7-p0c1-4-decoupled-render.apk
Size: 74,788,598 bytes
Timestamp: 2026-07-13T11:12:11.8029210+08:00
SHA-256: 432D835147ED64013DB2FF30F16B2F900D73E90011AC9CB777791604F34FE6DF
Detail tile overlay default: enabled
```

## Device status

```text
gesture/render decoupling:
  implementation: PASS
  device: PASS

current-page tile latency:
  device: PASS
  center p50: 61 ms
  center p95: 102 ms
  current-visible acceptance-batch p50/p95: 55/55 ms (n=14)

page-ahead regression:
  device: PASS
```

H1 horizontal physics was not modified. Persistence/Room/final flusher, Pointer Epoch math, tile
geometry, page-ahead corridor math, tile size, the 56 MB cache, R6 legacy and UI polish were also
left unchanged by P0C1.4.
