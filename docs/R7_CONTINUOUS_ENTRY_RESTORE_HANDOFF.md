# R7_CONTINUOUS_ENTRY_RESTORE_HANDOFF

**Active object:** `R7_RESTORE_TRACKING_AND_READER_VIEWPORT`  
**Sub-object:** `R7_P0A1_1_RESTORE_READY_RELEASE` (Phase A complete)  
**Status:** implementation complete / physical-device acceptance pending  
**Authority:** `/storage/emulated/0/inbox/MarkLeaf/r7.md`

## Scope (Phase A complete, Phase B deferred)

Phase A: Atomic READY transition, user-scroll escape hatch, viewport intersection restore validation, comprehensive DEBUG logging, 18 JVM tests.

Phase B (DEFERRED): compact window, fast scroller, page scrubber, horizontal page top alignment.

Do not modify R6 `PdfVerticalZoomLockArea`, pinch/zoom math, interaction/bitmap transactions, render queue/HIGH jobs, reverse prefetch, horizontal Pager gesture UX, outline P1, Markdown, package/export, or PrintPageResolver.

## Audited pre-fix failure chain (from P0A1)

1. Room stores PDF page progress in `DocumentEntity.lastScrollIndex`; the home card reads that same field in `HomeScreen.formatPdfProgress`, so cards correctly showed e.g. `47 / 982`.
2. `PdfViewerViewModel.openDocument()` read `entity.lastScrollIndex`, published `Ready(pageIndex = restored)`, and selected the saved reading mode.
3. For vertical mode, the old code emitted a `PageJumpRequest` after publishing Ready. `ContinuousDocumentViewport` meanwhile initialized local `ContinuousDocumentState` with `scrollY = 0`.
4. Before viewport size/geometry and the jump were ready, `pageAtViewportCenter(scrollY=0)` produced page 0; `LaunchedEffect` called `onVerticalPageSettled(0)`.
5. `onVerticalPageSettled` called the old page-only debounce persistence, overwriting the correct database progress with page 0. `flushProgress()` also wrote the current transient `Ready.pageIndex`.
6. Horizontal recovery did not suffer this: `PdfHorizontalPagedArea` observes `pagerState.settledPage`, calls `onHorizontalPageSettled`, and does not use transient continuous scrollY.

**This was fixed in P0A1** by suppressing pre-READY feedback.

## NEW failure chain (P0A1_1 — this fix)

### True runtime evidence (supplementary testing, 2026-07-12)

**Positive:** The 112 roundtrip works:
- 112 → horizontal 112 → continuous 112 → home → reopen 112 → horizontal 112 — all correct.

**Negative (earlier recording):**
1. Home card shows 114 / 451.
2. Open continuous mode correctly restores to 114.
3. User scrolls down and up multiple pages.
4. Top bar page number stays frozen at 114.
5. Current chapter doesn't track actual page.
6. Exit may persist old restore target instead of user's last-viewed position.

### Root cause (3 confirmed failure modes)

1. **Stuck APPLYING_TARGET**: If the restore anchor validation failed (old `abs(actual-target)<=1` was too strict), `completeRestore()` was never called. The state machine permanently stayed in `APPLYING_TARGET`, suppressing ALL viewport→VM tracking, scheduling, persistence, and chapter resolution. The restore LaunchedEffect only retried on geometry changes; once geometry stabilized, the user was permanently stuck.

2. **Overly strict validation**: The fixed page-difference check failed when page gaps, varying page heights, or anchor positions pushed the viewport-center page 1-2 pages away even though the target page was intersecting the viewport.

3. **No user escape**: When the restore target was visually near the viewport but validation failed, the user could scroll (changing scrollY and primaryPage) but every tracking gate silently dropped the update because `phase != READY`.

### Fix summary

| Component | Change |
|---|---|
| `ContinuousDocumentGeometry` | Added `pageIntersectsViewport()` and `anchorIsInViewport()` — replaces brittle page-difference check with geometric intersection |
| `ContinuousEntryRestoreState` | Added `cancelRestore()` (user escape hatch), `isReady()`, `getConsumedGeneration()`, idempotent `completeRestore()` with `consumedRestoreGeneration` guard |
| `PdfViewerViewModel` | `onContinuousViewportChanged` detects user DRAG during APPLYING_TARGET → cancels restore. `onVerticalPageSettled` has belated escape hatch. Comprehensive DEBUG logging at every suppression gate. |
| `ContinuousDocumentViewport` | Updated restore LaunchedEffect to use viewport intersection + anchor visibility. Added ENTRY_PHASE, VIEWPORT_PAGE_CANDIDATE, VIEWPORT_PAGE_REJECT logs. |
| `R7P0a1RestoreReadyReleaseTest` | 18 new JVM pure-logic tests covering atomic READY, escape hatch, idempotency, tracking data flow, geometry replay protection, 112 roundtrip regression. |

### Atomic READY transition

`completeRestore()` and `cancelRestore()` both atomically perform:
1. `phase = READY`
2. `pending = null`
3. `consumedRestoreGeneration = generation`
4. tracking gates open (all LaunchedEffect + VM gates check `phase==READY`)
5. persistence gate open (`canPersist` returns true)
6. `lastStable` updated (completeRestore) or preserved (cancelRestore)

Each generation can only complete/cancel once — second calls are no-ops.

### User-scroll escape hatch

Two independent paths:

1. **Primary**: `onContinuousViewportChanged` detects `snapshot.phase == DRAGGING && entryPhase != READY` → calls `cancelRestore()` → enters READY atomically.

2. **Belated**: `onVerticalPageSettled` detects `phase != READY` at settle time (the viewport drag callback missed it) → if source is not `INITIAL_PLACEHOLDER`, calls `cancelRestore()` → enters READY → falls through to normal tracking.

Both preserve `lastStable` (the pre-restore seeded position), so an immediate onStop/dispose writes the prior real position, never page 0.

### Restore validation (improved)

Old: `abs(actual.pageIndex - target.pageIndex) <= 1`
New: `pageIntersectsViewport(targetPage) && anchorIsInViewport(targetPage, intraPageFraction)`

This tolerates ±1-2 page viewport-center drift when the target is actually visible, and correctly rejects restores where the anchor is genuinely outside the viewport.

## Position authority and state machine

Same as P0A1:
- `ReadingPosition(pageIndex, intraPageFraction, source, generation)` is 0-based.
- `ContinuousEntryRestoreState` owns `lastStable`, `pending`, `consumedRestoreGeneration`, and phases.
- Pre-READY page/viewport/progress feedback is suppressed. Page 0 is never persisted.
- `flushProgress()` writes only `lastStableReadingPosition`.

## Main implementation points

- `app/src/main/java/com/markleaf/app/pdf/ReadingPosition.kt` — hardened state machine
- `app/src/main/java/com/markleaf/app/pdf/ContinuousDocumentGeometry.kt` — viewport intersection methods
- `app/src/main/java/com/markleaf/app/ui/pdf/PdfViewerViewModel.kt` — escape hatch + DEBUG logging
- `app/src/main/java/com/markleaf/app/ui/pdf/ContinuousDocumentViewport.kt` — improved restore validation + DEBUG logging

## Tests

- New JVM: `R7P0a1RestoreReadyReleaseTest` (18 test cases)
- Existing JVM unchanged: `ContinuousEntryRestoreStateTest`, `R7P0aGestureContractTest`, etc.
- Compose instrumentation: `R7GestureLifecycleInstrumentedTest` (existing, NOT RUN on device)

## Gate record (2026-07-12)

```text
./gradlew :app:testDebugUnitTest --tests 'com.markleaf.app.pdf.*'  exit 0  PASS
./gradlew :app:testDebugUnitTest                                      exit 0  PASS
./gradlew :app:connectedDebugAndroidTest                              exit 1  NOT RUN
./gradlew :app:lintDebug --no-daemon --console=plain                 exit 0  PASS
./gradlew :app:assembleDebug --no-daemon --console=plain             exit 0  PASS
```

`connectedDebugAndroidTest` compiled and packaged application/test APKs, then stopped before device discovery because the configured Termux ADB bridge could not supply a version. This is **NOT RUN**, never PASS.

## Artifact and next device acceptance

```text
Path: /storage/emulated/0/outbox/MarkLeaf/markleaf-debug-r7-p0a1-1.apk
Size: 74,703,886 bytes
SHA-256: 83695882cdd019e8571afe85a300a2a75747ec8d7048de112a19d75557630805
```

Physical-device acceptance remains PENDING. Required checks:
- 114 → continuous opens near 114 without page-0 persistence
- After scroll to 120, top bar shows 120, chapter updates
- Return home saves 120, reopen restores 120
- 112 → horizontal 112 → continuous 112 → home → reopen 112 no regression
- Rapid scroll then immediate back/home persists correct last-viewed position
- Scroll during stuck APPLYING_TARGET (if reproducible) triggers escape hatch

Phase B (reader UI: compact window, scrollbars, horizontal layout) is DEFERRED until Phase A device acceptance completes.
