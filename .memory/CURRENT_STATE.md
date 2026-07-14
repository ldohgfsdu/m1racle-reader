# CURRENT_STATE

**Updated:** 2026-07-13  
**Authority decision:** R7 `ContinuousDocumentViewport` formally replaces R6 for vertical continuous reading.  
**Active object:** `MARKLEAF_TABLE_HEADER_TYPOGRAPHY_AND_ALIGNMENT_PARITY_FIX` (`device_accepted_qa_device_instrumentation_blocked`)
**Current sub-object:** Markdown responsive mobile table R4 device accepted; QA instrumentation remains blocked.

## Markdown responsive table R4 final acceptance (2026-07-14)

- Human visual acceptance: **ACCEPTED**; overall: **DEVICE_ACCEPTED**.
- PASS: two-column short/long layouts, three-column width priority, unified header typography,
  shared first baseline, single-line `I、V6` and `rsR′、M 形、兔耳`, natural third-column
  horizontal overflow, complete right endpoint, and the 27/27 reader-setting matrix.
- Verification: JVM 606/606 PASS; table-specific 37/37 PASS; lint and requested Debug/QA builds
  PASS. Main-package data safety PASS. HyperOS QA instrumentation remains **BLOCKED**, not PASS.
- Final APK SHA-256:
  `9EB5B982E3AFD58848C99D3646E03B856CF3D3C2EB960B3BDDFAD10F1A6B23C7`.
- Reports: `docs/MARKLEAF_TABLE_HEADER_TYPOGRAPHY_AND_ALIGNMENT_PARITY_FIX_REPORT.md` and
  `docs/MARKLEAF_MARKDOWN_RESPONSIVE_TABLE_RENDERER_REPORT.md`.

## Local accepted checkpoint (2026-07-13)

- Checkpoint id: `MARKLEAF_R7_P0C1_4_ACCEPTED_LOCAL_CHECKPOINT`.
- `P0C1_4_GESTURE_RENDER_DECOUPLING`: **device_accepted** on `db04f865`
  (`25098PN5AC`) in the required 2.5x continuous-reading acceptance range.
- `P0C1_1_PAGE_ENTRY_VISUAL_VALIDATION`: **pending**. P0C1.4 acceptance does not imply that
  page-entry appearance has been visually accepted.
- `H1_HORIZONTAL_ZOOM_PAN`: **device_fail_tuning_required**. No H1 code or tuning is part of
  this checkpoint.
- `P0A_DATABASE_DEVICE_TESTS`: **pending_not_executed**. JVM and compilation coverage do not
  count as Room migration/transaction execution on a physical device.
- This checkpoint is local only. APKs, raw device logs, build output, local configuration and
  temporary tool output are excluded from Git.

## R7 P0C1.4 gesture/render pipeline decoupling (2026-07-13)

- Pre-fix device result: tile geometry visual sample passed, but current-page detail rendering
  failed while a finger remained down; visible content stayed blurry until release.
- Root coupling: per-transform `LaunchedEffect` acted as the planner lifecycle, so rapid pointer
  frames could cancel effects before they ran. `TileWorkQueue.submit` also replaced the exact
  active set every frame, dropped pending work outside that set and could requeue the same key
  already owned by the renderer.
- New producer: pointer/transform updates publish a bitmap-free `PdfViewportSnapshot` through a
  lock-free one-slot conflator and non-suspending `trySend`. No pointer frame starts a coroutine or
  waits for PdfRenderer.
- New consumers: one long-lived 16ms planner actor in the viewport and one ViewModel-lifetime
  renderer actor. Neither restarts on pointer down/up; `PdfRenderer` remains mutex-serialized.
- Queue uses latest priority plus an 8-key active-region hysteresis set, keeps still-useful
  in-flight tiles, prevents duplicate enqueue of `runningTileKey`, and only fully invalidates for
  incompatible document/geometry/page/rotation/fitted-width/bucket identity.
- Priority is focal/center, visible, leading edge, near corridor, page-ahead, margin/speculative.
  Current visible work is admitted before page-ahead.
- Detail tile ready is accepted in IDLE, DRAGGING, PINCHING, SETTLING and FLINGING. Whole-page
  bitmap/composed-page freeze remains separate. Tile swaps update a dedicated overlay flow in one
  16ms batch and no longer copy the full `PdfViewerUiState.Ready`.
- Logs: `VIEWPORT_SNAPSHOT`, `RENDER_PLANNER_TICK`, `TILE_REQUEST`, `TILE_READY`, `TILE_SWAP`,
  `CURRENT_TILE_LIFECYCLE`, `GESTURE_RENDER_HEALTH`.
- Verification rerun: 508 JVM tests PASS (23 P0C1.4 tests; 0 failed/skipped), lintDebug PASS,
  assembleDebug PASS, compileDebugAndroidTestKotlin PASS. Eight Compose/androidTest hold-drag
  scenarios compile.
- Physical device `db04f865` (`25098PN5AC`) at 2.5x: while the pointer remained down, 4 planner
  ticks produced 37 requests, 37 ready events and 37 swaps. Coverage recovered from 0.6667 to
  1.0 before release; the single renderer actor remained `workerStarts=1`.
- Latency: cumulative current-center health p50/p95 = 61/102 ms; the acceptance batch's current
  visible tiles (n=14) were 55/55 ms and all work (n=37) was 55/126 ms. CENTER/VISIBLE work was
  requested on sequences 8 and 10; PAGE_AHEAD first appeared on sequence 11.
- Device status: gesture/render decoupling PASS, current-page tile latency PASS, page-ahead
  regression PASS. Raw confirmation log: `dist/performance/r7-p0c1-4-device-acceptance-20260713.log`.
- APK: `dist/markleaf-debug-r7-p0c1-4-decoupled-render.apk`, 74,788,598 bytes,
  SHA-256 `432D835147ED64013DB2FF30F16B2F900D73E90011AC9CB777791604F34FE6DF`.

## R7 P0C1.2 tile geometry corruption fix (2026-07-13)

- New physical-device video evidence reclassified P0C1 as `DEVICE FAIL / TILE_GEOMETRY_CORRUPTION`:
  the whole-page base layer was correct, while arriving detail tiles split and displaced QR,
  NOTES, labels, icons and rules.
- Code-backed root cause: provisional default-aspect tile requests could survive a real-aspect
  geometry change because tile keys did not include geometry generation. The renderer then used
  the old fitted page height for page-local-to-PDF Y conversion, and the result overlaid the new
  base geometry. The Canvas path also inferred a fixed symmetric bleed and rounded origin + size
  independently instead of using carried content crop and rounded destination edges.
- Safety gate was implemented as `DETAIL_TILE_OVERLAY_ENABLED=false` for the P0C1.2 artifact.
  After the later geometry visual sample passed, P0C1.4 re-enabled the overlay by default so the
  decoupled pipeline can be device-accepted; the PREVIEW/HIGH base path remains unchanged.
- Tile contract is now explicit `PageLayoutRect` (unscaled page-local), with separate
  `DocumentViewportRect`, `PdfPageRect`, `TilePixelRect`, `PageLayoutSize`, and `PdfPageSize`.
- Tile key now binds document generation, geometry generation, page, rotation, fitted-width
  version, zoom bucket, tile X and tile Y. In-flight old geometry emits
  `TILE_DROP(STALE_GEOMETRY)` and stale cache entries are filtered again before Canvas.
- Renderer matrix directly encodes
  `bitmap=(pageLocal-pageRect.origin)*renderScale+bleed`; Canvas crops the request's exact
  content rect and rounds destination edges, preventing bleed/stretch and 1 px seams.
- Debug builds support base-only/tile-only/combined, 50% alpha and per-tile border/metadata via
  Gradle properties; release diagnostics remain gated off.
- Verification: 485 JVM tests PASS (including 30 zero-diff synthetic composition combinations),
  lintDebug PASS, assembleDebug PASS, androidTest APK compile/package PASS. The real
  PdfRenderer whole-page-vs-tile pixel-diff test is present but runtime NOT RUN because no ADB
  device/emulator is available. `connectedDebugAndroidTest` was terminated during device wait.
- APK: `dist/markleaf-debug-r7-p0c1-2-tile-geometry-safe.apk`, 74,761,374 bytes,
  SHA-256 `BA785800507407D5968F0B8267C231BC0865BED428FB82320210A9C17A86B3BC`.
- P0C1 geometry status is now `visual_sample_pass`; current-page gesture rendering and latency
  remain device pending. P0B2 remains `DEVICE ACCEPTED`.

## R7 P0C1 predictive visible-region tiles

- Active object: `R7_P0C1_PREDICTIVE_VISIBLE_REGION_TILE_RENDERING` (`implementation_complete_device_pending`).
- `R7_P0B2_ZOOM_SESSION_RESTORE`: **device_accepted**.
  Evidence: `home_return_restore_pass`, `task_card_kill_restore_pass`.
- `R7_P0B1_1_POINTER_EPOCH`: **device_pending**; no acceptance inferred.
- P0C1 implements a stable base-page layer plus independent predictive detail-tile overlay.
- Physical-device performance and visual acceptance remain PENDING.

## R7 P0C1.1 page-ahead + H1 horizontal physics

- P0C1 detail tiles are confirmed working during pinch, but device tuning is required:
  `implementation_complete_device_tuning_required`.
- P0C1.1 adds immediate target calculation, EMA document-space motion, 100/240ms corridor,
  directional next/previous-page first-screen bands, 1x idle warmup, reserved urgent/page-ahead
  queue slots, and one reorderable serial tile worker.
- `R7_H1_HORIZONTAL_ZOOM_PAN_PHYSICS`: `device_fail_tuning_required`; no H1 changes are included
  in the accepted P0C1.4 checkpoint.
- P0B2 remains `device_accepted`; Pointer Epoch remains `device_pending`.

## R7 P0B1.1 pointer epoch + zoom session (2026-07-12)

- P0B1 v2 physical-device result: **DEVICE FAIL**. Pinch at page 63 jumped near page 60;
  page-57 content focal point moved sharply; 1/2-pointer transitions jumped; page 62 reopened
  at the correct page but lost scale and local anchor.
- Active object is now `R7_P0B1_1_POINTER_EPOCH_CORRECTION_AND_ZOOM_SESSION_RESTORE`.
- Implementation complete: stable two-pointer pair, pointer-set transition frame skip,
  span-baseline zoom, settle only after all pointers are up, semantic viewport-session Room v6
  persistence, startup zoom-session restore, and zoom-quality timing logs.
- JVM/lint/assemble: PASS. androidTest compilation: PASS. Runtime instrumentation: NOT RUN
  because no ADB device was connected at the final gate.
- Physical-device acceptance: **PENDING** for the new P0B1.1 artifact.
- Technical report: `docs/R7_P0B1_1_POINTER_EPOCH_SESSION_REPORT.md`.

## Authoritative read order

1. `AGENTS.md`
2. `.memory/active_object.yaml`
3. `.memory/CURRENT_STATE.md`
4. `docs/R7_CONTINUOUS_ENTRY_RESTORE_HANDOFF.md` — current executable technical handoff
5. `docs/R7_CONTINUOUS_VIEWPORT_HANDOFF.md` — completed earlier R7 P0A handoff
6. `docs/NEXT_AGENT_HANDOFF_R6.md` — retained R6 historical handoff only
7. `docs/PDF_VIEWER_HANDOFF.md` — earlier PDF history only
8. `git status --short`

The R6 handoff files remain useful failure history, but they no longer define the production continuous architecture.

## Current P0A1_1: restore READY release

### Root cause

After `completeRestore()` set `phase=READY`, every suppression gate independently checked `phase==READY`. While nominally correct, three failure modes existed:

1. **Stuck APPLYING_TARGET**: If the restore anchor validation (`abs(actual-target)<=1`) failed, `completeRestore` was never called. The state machine permanently stayed in `APPLYING_TARGET`, suppressing all viewport→VM tracking, scheduling, persistence, and chapter resolution. The `LaunchedEffect` only retried on geometry changes; once geometry stabilized, the user was stuck with no escape.

2. **Overly strict validation**: The fixed `abs(actual-target)<=1` check failed when page gaps, varying page heights, or anchor positions caused a 1-2 page viewport-center offset even though the target page was visible. This made validation brittle across different page aspect ratios.

3. **No user escape**: Even when the restore target was visually near the viewport, a failed validation left the user unable to break out — scrolling changed `scrollY` and `primaryPage` but every tracking gate silently dropped the update.

### Fix (Phase A — completed)

| Change | File |
|---|---|
| Viewport intersection + anchor visibility restore validation | `ContinuousDocumentGeometry.kt` +53 lines |
| Atomic, idempotent READY transition with `cancelRestore()` escape hatch | `ReadingPosition.kt` (ContinuousEntryRestoreState) +40 lines |
| User drag → `cancelRestore()` in `onContinuousViewportChanged` | `PdfViewerViewModel.kt` |
| Belated escape hatch in `onVerticalPageSettled` | `PdfViewerViewModel.kt` |
| Comprehensive DEBUG logging at every gate | `PdfViewerViewModel.kt`, `ContinuousDocumentViewport.kt` |
| 18 new JVM pure-logic tests | `R7P0a1RestoreReadyReleaseTest.kt` |

**Restore validation** now requires BOTH:
1. Target page intersects viewport (`pageIntersectsViewport`)
2. Target anchor document-Y is inside visible region (`anchorIsInViewport`)

**CancelRestore** atomically: `phase→READY`, `pending→null`, `consumedGeneration=generation`, preserves `lastStable`.

**User escape hatch**: `onContinuousViewportChanged` detects `DRAGGING` phase during `APPLYING_TARGET` → cancels restore → enters READY. `onVerticalPageSettled` has a belated fallback.

**All gates released on READY**:
- Viewport flatten (`:163-168`)
- Viewport scheduling (`:362-368`)
- Page settled callback (`:383-396`)
- PageJump consume (`:398-399`)
- VM render scheduling (`onContinuousViewportChanged`)
- VM page tracking (`onVerticalPageSettled`)
- VM progress persist (`canPersist`)

### Phase A verification gates (2026-07-12)

```text
./gradlew :app:testDebugUnitTest --tests 'com.markleaf.app.pdf.*'
  exit 0 — PASS (all existing + 18 new R7P0a1RestoreReadyReleaseTest)

./gradlew :app:testDebugUnitTest
  exit 0 — PASS

./gradlew :app:connectedDebugAndroidTest
  exit 1 — NOT RUN
  Application/test APK compilation and packaging succeeded; execution stopped before
  device discovery because the configured Termux adb version/bridge could not be read.

./gradlew :app:lintDebug --no-daemon --console=plain
  exit 0 — PASS

./gradlew :app:assembleDebug --no-daemon --console=plain
  exit 0 — PASS
```

### Delivered artifact

```text
Path: /storage/emulated/0/outbox/MarkLeaf/markleaf-debug-r7-p0a1-1.apk
Size: 74,703,886 bytes
SHA-256: 83695882cdd019e8571afe85a300a2a75747ec8d7048de112a19d75557630805
Physical-device acceptance: PENDING
```

### Test evidence classification

- `R6_LEGACY`: R6 overlay/session/hit-test tests — historical regression only
- `R7_PURE_LOGIC`: `R7P0aRestoreReadyReleaseTest` (18 cases), `ContinuousEntryRestoreStateTest`, `ContinuousDocumentGeometryTest`, `R7P0aGestureContractTest`, etc.
- `R7_RUNTIME_REQUIRED`: `R7GestureLifecycleInstrumentedTest`, `R7ConfigurationRecreationInstrumentedTest` — NOT RUN
- `STRUCTURAL_GUARD_ONLY`: `R7ContinuousViewportTest`, `R71FrameStabilityTest`

### Phase B (DEFERRED)

- compact window layout
- continuous vertical fast scroller
- horizontal page scrubber
- horizontal page top alignment

Do not start Phase B until physical-device acceptance completes for Phase A.

## Production architecture truth

- `PdfViewerScreen.kt:344` routes `PdfReadingMode.VERTICAL_CONTINUOUS` to `ContinuousDocumentViewport(...)`.
- `app/src/main/java/com/markleaf/app/ui/pdf/ContinuousDocumentViewport.kt` is the only production vertical-continuous implementation.
- `PdfVerticalZoomLockArea` in `PdfViewerScreen.kt` is legacy R6 code. It is retained for historical/rollback comparison, is not the production path, and must not be deleted, refactored, or modified.

## Explicitly deferred (P0B + Phase B)

Do not address:
- reverse prefetch direction
- page-scoped render error identity
- in-flight HIGH cancellation
- compact window / responsive layout
- continuous vertical fast scroller
- horizontal page scrubber
- horizontal page top alignment
- R6 legacy refactor/removal
- textbook outline P1
- horizontal pager
- Markdown / textbook package / export
- PDFium migration

## Repository safety

The repository is a large dirty worktree containing user work and untracked PDF sources/tests. Preserve unrelated changes. Never use `git reset --hard`, `git clean`, destructive checkout, broad regeneration, or an unsolicited commit. Do not add inbox/outbox assets, APKs, PDFs, ZIPs, caches, or large fixtures to git.

## Next action

P0C1.4 is complete and device accepted. Stop here. Do not start H1, persistence/Room/final
flusher, Pointer Epoch, tile geometry, page-ahead math, cache sizing, R6 legacy or UI-polish work
without a new active object.

## R7_P0B1_CONTINUOUS_PINCH_ANCHOR_STABILITY (implementation_complete_device_pending)

### Problem

Two issues identified in R7 continuous viewport:

**A. Pinch anchor drift:** Old pinch handler used `zoomAt(centroid) + panBy(delta)` — two
steps with independent clamps. `zoomAt` recomputed the focal document point from the current
centroid each frame, then `panBy` applied finger delta separately. This produced
`(Z-1) × P` accumulated anchor error across multi-frame pinches with moving centroids.

**B. Drag blur:** `interactionActive` excluded `DRAGGING` — bitmap `LaunchedEffect`
unconditionally replaced displayed bitmaps with `state.pageBitmaps` during drag, allowing
low-tier renders to overwrite high-tier displayed bitmaps.

### Fix (P0B1 — completed)

**Pinch anchor — old formula:**
```
// Per frame:
step1 = zoomAt(state, newScale, currentCentroid)  // focal = (scroll + centroid) / oldScale
step2 = panBy(step1, fingerDx, fingerDy)           // independent clamp
// Two clamps, focal recomputed each frame → (Z-1)*P drift
```

**Pinch anchor — new formula:**
```
// Per frame (single pass):
newScroll = focalDocumentX * newScale - centroidX
newScroll = focalDocumentY * newScale - centroidY
state = clamp(state.copy(scale=newScale, scrollX=newScrollX, scrollY=newScrollY))
// focalDocument captured at gesture start / rebase; one clamp per frame
```

**Pointer rebase timing:**
- 2→3, 3→2 inside pinch: rebase `focalDocumentX/Y` from current transform + new centroid
- 2→1 (pinch → single finger): rebase, continue as single-finger pan via `zoomAndPanFromFocal` with same scale
- 1→2 (new pinch): rebase again
- Each rebase: `focalDocument = (scroll + newCentroid) / scale` — preserves visual transform

**Display merge policy (per phase):**

| Phase | Behavior |
|---|---|
| IDLE | Accept new page; same-page upgrade only; reject downgrade |
| DRAGGING | New page accepts any tier; displayed page rejects downgrade; upgrade allowed |
| PINCHING | Freeze displayed bitmap identity; new results → pending |
| SETTLING | Continue freeze; accumulate best pending per page |
| SETTLE COMMIT | best-of(displayed, pending) per page; atomic; clear pending |

Quality ordering: `HIGH > MEDIUM > PREVIEW > SCROLL_PREVIEW > PLACEHOLDER(null)`
Same tier: higher `widthBucket` wins.

### P0B1 verification gates (2026-07-12)

```text
./gradlew :app:testDebugUnitTest --no-daemon --console=plain
  exit 0 — PASS (383 tests, including 24 new P0B1 tests)

./gradlew :app:lintDebug --no-daemon --console=plain
  exit 0 — PASS

./gradlew :app:assembleDebug --no-daemon --console=plain
  exit 0 — PASS

./gradlew :app:connectedDebugAndroidTest --no-daemon --console=plain
  UTP (Gradle managed): BLOCKED — "Starting 7 tests" but 0 completed after 9 min.
    No instrumentation process started on device (pm list shows test APK installed
    and instrumentation registered, but UTP never launches am instrument).
    UTP/MIUI incompatibility suspected.
  Direct am instrument: PARTIAL — first test started but never finished.
    Test: startupAndModeEntryRestoreApplyTargetBeforeAnyPageZeroFeedback
    Status: INSTRUMENTATION_STATUS_CODE=1 (started), no finished/failed in logcat.
    logcat (270k lines) contains NO markleaf app logs (no MarkLeafR1 tag).
    composeRule.setContent may not be executing; createComposeRule() idle
    detection suspected incompatible with MIUI/HyperOS.
    Device state after force-stop: clean (0 markleaf windows, 0 processes).
    NOT a window leak (confirmed: 0 markleaf windows before and after).
```
</remarks>

### P0B1 delivered artifact

```text
Path: dist/markleaf-debug-r7-p0b1-pinch-anchor.apk
Size: 74,680,790 bytes (72,930.5 KB)
SHA-256: deadf6fbe7f87fcb975652f6aecaa0b2262a150b89e658a24530949717af1225
Physical-device acceptance: PENDING
```

### P0B1 modified files

| File | Change |
|---|---|
| `ContinuousDocumentViewport.kt` | Display merge policy per page; pinch handler replaced `zoomAt+panBy` with `zoomAndPanFromFocal`; pointer rebase (2→3, 3→2, 2→1); `dragStart`/`dragEnd` phase tracking; settle commit uses `DisplayMergePolicy.settle`; DEBUG logs (DISPLAY_BITMAP_ACCEPT/REJECT/PENDING, BITMAP_SWAP, PINCH_REBASE) |
| `ContinuousInteractionTransaction.kt` | P0B1 transaction fields (gestureId, pointerCount, baseScale/Scroll/Centroid, focalDocumentX/Y); `dragStart`, `dragEnd`, `rebase` reducers; `BitmapMetadata`, `DisplayedBitmap`, `DisplayMergeResult`, `DisplayMergePolicy` |
| `ContinuousDocumentGeometry.kt` | `zoomAndPanFromFocal` — single-step focal-point-preserving transform |
| `PdfViewerViewModel.kt` | `pageBitmapMetadata` in `Ready` state; `snapshotPageMetadata()` helper; `publishCache` sets metadata |
| `R7P0b1PinchAnchorTest.kt` | 6 JVM tests: focal stability, 2→1→2 rebase, settle, scale-back-to-1x |
| `R7P0b1DisplayPolicyTest.kt` | 18 JVM tests: tier monotonicity, widthBucket, DRAGGING accept, PINCHING freeze, settle best-of |
| `R7ContinuousViewportTest.kt` | Structural assertion updated: `zoomAt` → `zoomAndPanFromFocal` |
| `R71FrameStabilityTest.kt` | Structural assertion updated: `state.pageAspects + displayedAspects` → `displayedAspects + acceptedAspects` |

### P0B1 drag blur runtime confirmation

**Status: PENDING (requires successful device instrumentation run)**

Low-frequency DEBUG logs added but NOT YET observed on device. Instrumentation
blocked by UTP/MIUI incompatibility (see verification gates above). No conclusion
yet on which of three cases occurs:
- A. Same-page HIGH/PREVIEW bitmap replaced by SCROLL_PREVIEW
- B. New page entering viewport only has SCROLL_PREVIEW (expected behavior)
- C. Bitmap identity unchanged but transform/resampling causes visual softness

### P0B1 not modified (per user instruction)

- `PdfPageBitmapCache.kt` — `putIfCurrent` generation guard is potential risk, not direct trigger
- `PdfPageRenderer` / R3 render queue / `ContinuousRenderScheduling`
- `PdfViewerViewModel` render scheduling
- P0A1/P0A1-1 restore state machine
- Horizontal pager, R6 legacy, compact UI, fast scroller, page scrubber
