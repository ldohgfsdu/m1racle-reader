# R7_CONTINUOUS_VIEWPORT_HANDOFF

**Active object:** `R7_CONTINUOUS_VIEWPORT_STABILIZATION`  
**Current sub-object:** `R7_P0A_AUTHORITY_GESTURE_LIFECYCLE_CONTRACT`  
**Priority/status:** P0 / `implementation_complete_device_pending`  
**Authority date:** 2026-07-12  
**Source decision:** `/storage/emulated/0/inbox/MarkLeaf/r7.md`

This is the executable handoff for the current PDF work. It supersedes `docs/NEXT_AGENT_HANDOFF_R6.md` as authority. The R6 documents remain unchanged as historical failure evidence.

## 1. Product and architecture decision

R7 `ContinuousDocumentViewport` formally replaces R6 and is the only production implementation for vertical continuous reading.

Production routing evidence at the authority snapshot:

```text
PdfViewerScreen.kt:344
PdfReadingMode.VERTICAL_CONTINUOUS -> ContinuousDocumentViewport(...)
```

- Production implementation: `app/src/main/java/com/markleaf/app/ui/pdf/ContinuousDocumentViewport.kt`
- Production router: `app/src/main/java/com/markleaf/app/ui/pdf/PdfViewerScreen.kt`
- Legacy implementation: `PdfVerticalZoomLockArea` in `PdfViewerScreen.kt`

`PdfVerticalZoomLockArea` is retained but uncalled by the production continuous branch. Do not delete, refactor, or modify it in P0A. R6 tests remain historical regression evidence and cannot be cited as proof that R7 works at runtime.

## 2. Scope boundary

Implement only P0A:

- fresh state for the long-lived pointer detector
- gesture/settle lifecycle ownership and cleanup
- UI/ViewModel zoom ownership contract
- one 1×–4× continuous scale contract
- meaningful R7 pure-logic tests
- minimum R7 Compose instrumentation

Explicitly do not implement:

- R6 legacy refactor or removal
- reverse prefetch direction
- page-scoped render error identity
- in-flight HIGH cancellation
- textbook outline P1
- horizontal pager changes
- Markdown, textbook package, or export changes
- PDFium migration

The three rendering/scheduling defects above are P0B. Observation is allowed; implementation is not. Finish P0A and stop.

## 3. P0A contract

### 3.1 Fresh gesture-start snapshot

Root cause: the detector intentionally uses the stable key `pointerInput(documentId)`, but a long-lived pointer coroutine can directly capture the `List`/`Set`/`Map` and derived viewport values from the composition that created it. After scrolling, the next pinch may therefore freeze old pages and temporarily remove the current page from composition.

Required design:

1. Keep `pointerInput(documentId)` stable. Page scrolling must not restart the detector.
2. Publish the latest gesture inputs through `rememberUpdatedState` or one stable state holder.
3. The holder must cover, as one coherent snapshot:
   - monotonically identifiable generation/version
   - document identity
   - composed pages
   - visible pages
   - viewport geometry and viewport/primary page
   - current `ContinuousDocumentState`
   - displayed bitmap identities
   - page bounds used by the gesture
4. At gesture start, read the holder once and freeze that exact snapshot into the interaction transaction.
5. Never directly read a detector-start `List`, `Set`, `Map`, geometry object, bitmap map, page-bounds map, primary page, or document state from inside the long-lived pointer coroutine.
6. Reject a snapshot if its document/generation no longer matches the current owner. Do not partially combine values from different generations.
7. A settling interaction may retain its own frozen pages, but a genuinely new interaction after scroll must begin from the latest published snapshot.

Acceptance:

- scrolling changes the published generation and page collections;
- the following pinch captures that new generation;
- `stableVisiblePages` comes from the gesture-start snapshot;
- the current visible page is not removed from composition;
- no stale initial page collection is frozen.

Required DEBUG evidence:

```text
POINTER_DETECTOR_START documentId=...
GESTURE_SNAPSHOT generation=... composedPages=... visiblePages=... viewportPage=...
STALE_SNAPSHOT_REJECTED capturedGeneration=... currentGeneration=...
```

Log the captured version, composed pages, visible pages, viewport page, and interaction id. Logs diagnose behavior; they do not replace tests.

### 3.2 Gesture owner and exactly-once cleanup

Invariant:

```text
Each logical onZoomGestureActive(true)
must eventually have exactly one matching onZoomGestureActive(false).
```

This must hold for:

- normal debounce/settle completion
- settle canceled by a second pinch
- reading-mode switch
- composable disposal
- document-id change
- configuration recreation
- coroutine/job cancellation
- exceptions

Required design:

1. Give every active interaction an explicit owner/token.
2. Centralize release in one idempotent cleanup operation guarded by that owner.
3. Cleanup cancels the owner's settle job, clears/restores VM gesture-active state once, and marks the owner released.
4. Call cleanup from normal settle completion and from cancellation/exception `finally` paths.
5. Add `DisposableEffect` cleanup for composition removal and key/document replacement.
6. A second pinch must cancel and release the old settling transaction before the new interaction assumes ownership. The old transaction must not later commit.
7. A stale settle callback must fail its owner/token check and perform no bitmap swap, scale commit, render request, page-settled callback, or viewport jump.
8. Repeated cleanup calls are no-ops after the first release.
9. Disposal/cancellation cleanup must not synthesize a new settle, render, or page jump.

The lifecycle must be testable without relying on elapsed wall-clock timing. Prefer a small pure owner/state machine that Compose wiring invokes.

Required pure-logic cases:

1. normal settle emits `false` once;
2. a second pinch cancels/releases the old owner;
3. dispose releases the owner;
4. mode switch releases the owner;
5. document change releases the owner;
6. duplicate cleanup still emits one release;
7. exception/cancellation cannot leave page-settled suspension active.

### 3.3 UI/ViewModel zoom ownership

The single ownership contract is:

- `ContinuousDocumentState` owns the live visual `scale`, `scrollX`, and `scrollY`.
- `ViewModel.zoomScale` stores only the most recent stable settled scale for render-quality selection, session restoration, and diagnostics.
- During an active gesture, UI updates the transform at gesture frequency. VM state must not write scale back into the viewport or change viewport geometry.
- Settle commits one final stable scale from UI to VM. VM may choose quality from it, but any return/state emission must not reset the UI transform.
- An old VM value must never override an active UI interaction.
- One interaction may commit at most one final scale; canceled/stale owners commit none.

The selected mode-switch/configuration strategy is restore, not reset:

1. VM retains the latest stable vertical `ContinuousDocumentState(scale, scrollX, scrollY)` for the current document and exposes it through Ready state.
2. R7 UI initializes/recreates from that stable snapshot.
3. Active high-frequency transforms remain UI-local; only a stable settle commits the full state once.
4. Switching to horizontal resets the shared active zoom/quality fields to `1×/PREVIEW`, while preserving the saved vertical session.
5. Switching back to vertical restores the saved state and synchronizes quality selection to its settled scale.
6. Document replacement must not restore another document's state.

This route is implemented. Pure tests pass and the real Compose instrumentation source compiles; device execution remains pending because the configured ADB bridge was unavailable. Never permit UI=1× while VM still reports an incompatible 2×/HIGH state.

### 3.4 One continuous scale limit

The product range is exactly 1×–4×:

```text
MAX_CONTINUOUS_SCALE = 4f
```

There must be one authoritative definition imported by:

- `ContinuousDocumentGeometry`
- gesture clamp/zoom logic
- render-quality scale clamp/selection
- logs/diagnostics where a maximum is reported
- JVM and instrumentation tests

Do not retain separate geometry 4× and quality 3× clamps. At scales above 3×, the existing highest HIGH tier may remain the selected tier; the stored scale must still be the true settled value through 4×. Do not add a new quality tier in P0A.

## 4. Test evidence taxonomy

Classify all evidence in reports and test names/comments:

### `R6_LEGACY`

R6 overlay/session/hit-test tests and source guards. Preserve them, but never cite them as R7 runtime evidence.

### `R7_PURE_LOGIC`

At minimum add real assertions for:

1. updated snapshot reads the newest page collection;
2. transaction start freezes that newest snapshot;
3. interaction cleanup is idempotent;
4. canceled settle restores VM gesture state;
5. active UI interaction ignores old VM scale;
6. settle commits one final scale only;
7. reset or restore keeps UI and VM consistent;
8. the entire clamp path uses 1×–4×.

### `R7_RUNTIME_REQUIRED`

Under `app/src/androidTest`, add minimum Compose lifecycle/gesture coverage for:

1. disposal clears gesture active;
2. mode switch clears gesture active;
3. after the page collection changes, the next gesture uses the new snapshot;
4. a rapid second pinch cancels the old settle without permanent suspension;
5. recreation keeps UI and VM scale consistent according to the chosen strategy;
6. old VM scale does not overwrite an active gesture.

Use fake page geometry, fake bitmap providers, and fake callbacks if real `PdfRenderer` makes instrumentation impractical. Keep real Compose state/lifecycle. Do not substitute source-text assertions.

Existing R7/R7.1 tests based on `File.readText().contains(...)` are structural guards only. Replace those that can become state-machine tests; label the rest honestly. Do not mass-delete tests.

## 5. Permitted implementation surface

P0A may narrowly touch the R7 viewport, its pure state/geometry/quality helpers, VM zoom-lifecycle wiring, vertical routing needed for cleanup/restore, and relevant unit/androidTest/build test configuration.

Do not change R6 implementation behavior, R3/P0B scheduler behavior, horizontal gesture/navigation behavior, outline, Markdown, package, or export behavior. If P0A appears to require any of those, stop and report the dependency instead of expanding scope.

## 6. Verification gates

Run in order and record command plus exit code:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.markleaf.app.pdf.*'
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:lintDebug --no-daemon --console=plain
./gradlew :app:assembleDebug --no-daemon --console=plain
```

Rules:

- A failed PDF test stops progression to the later build gates until fixed.
- If there is no device/emulator, `connectedDebugAndroidTest` is **NOT RUN**, not PASS. Continue the remaining runnable gates.
- Unit/lint/assemble PASS does not establish physical-device behavior.
- Do not create a delivery APK when a runnable gate is failing.

After all runnable gates pass, copy the debug APK to:

```text
/storage/emulated/0/outbox/MarkLeaf/markleaf-debug-r7-p0a.apk
```

Report absolute path, byte size, and SHA-256. Do not commit the APK.

## 7. P0A closure report

The final implementation report must contain:

1. evidence that R7 is the production continuous entry;
2. the updated memory and R7 handoff files;
3. the exact stale-capture root cause and fix;
4. the complete gesture `true`/`false` ownership lifecycle;
5. disposal/cancellation/mode-switch cleanup behavior;
6. the UI/VM zoom-owner contract and chosen recreate strategy;
7. the single 4× constant location and consumers;
8. R6 legacy, R7 pure-logic, and R7 runtime test classification;
9. added JVM and androidTest cases;
10. every Gradle command result and exit code;
11. `connectedDebugAndroidTest`: PASS, FAIL, or NOT RUN;
12. APK path, bytes, and SHA-256;
13. physical-device acceptance: PENDING until real acceptance;
14. explicit confirmation that P0B was not implemented.

## 8. Stop conditions

Stop and report rather than broaden work if:

- fresh snapshot or lifecycle cleanup would require restarting the detector on every scroll;
- the chosen scale strategy cannot keep UI and VM consistent;
- the fix requires modifying R6, horizontal Pager, P0B scheduling/error behavior, outline, Markdown, package/export, or migrating PDFium;
- any runnable verification gate remains failing;
- no device is present: mark connected tests NOT RUN, finish other gates, and leave device acceptance PENDING;
- P0A is complete: deliver the report and stop before P0B and textbook outline P1.

## 9. Current acceptance truth

- R7 production authority: **APPROVED**
- P0A implementation: **COMPLETE / DEVICE PENDING**
- Latest recorded device defects: **FAILED** on the earlier behavior
- R7 physical-device acceptance: **PENDING**
- R6 JVM tests as R7 runtime proof: **INVALID**
- P0B: **DEFERRED / MUST NOT START**

## 10. 2026-07-12 implementation and gate record

Implemented P0A only:

- The stable `pointerInput(documentId)` captures one versioned `ContinuousGestureSnapshotPayload` published from the latest composition. The payload includes document identity, composed/visible pages, viewport page, geometry, full transform, bitmap identities, and page bounds.
- `ContinuousGestureCoordinator` owns generation, exact `true`/`false` pairing, stale-owner rejection, one final stable-state commit, and side-effect-free repeated cleanup.
- Dispose, document replacement, pointer cancellation, exception, and mode removal cancel settle/fling work; motion generation prevents canceled fling from committing in `finally`.
- `ContinuousDocumentSession` stores stable transform plus stable page. Vertical recreation/mode return restores it; one-shot page jumps are acknowledged so a stale `StateFlow` jump cannot overwrite restored `scrollY`.
- `MAX_CONTINUOUS_SCALE = 4f` is defined once in `ContinuousDocumentGeometry.kt`; geometry, session clamp, VM continuous commit, and continuous quality resolution consume it. Horizontal 3× behavior remains separate.
- Test taxonomy is recorded in `docs/R7_TEST_CLASSIFICATION.md`. The new JVM contract suite is `R7P0aGestureContractTest`; real Compose tests are `R7GestureLifecycleInstrumentedTest` and `R7ConfigurationRecreationInstrumentedTest` with a debug-only host Activity/ViewModel.

Verification:

```text
./gradlew :app:testDebugUnitTest --tests 'com.markleaf.app.pdf.*'
  exit 0 — PASS

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

Delivered artifact:

```text
Path: /storage/emulated/0/outbox/MarkLeaf/markleaf-debug-r7-p0a.apk
Size: 74,422,622 bytes
SHA-256: 5609226c372f6d80d21ce90e083192c8d4ca5a26e8f4e6ea025df93f3019fd1d
Physical-device acceptance: PENDING
```

No P0B item was implemented: reverse prefetch direction, page-scoped render error identity, and in-flight HIGH cancellation remain deferred. R6 legacy, horizontal Pager behavior, Markdown, outline, package/export, and PDFium were not expanded.
