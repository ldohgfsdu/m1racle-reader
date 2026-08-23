# R7 H1.5 Platform Scroll Physics and Single-Owner Page Transition

Status: `implementation_complete_human_device_validation_pending`  
Baseline commit: `f94ce56919f3794e74626f32a1b2b6179cb40cf5`  
Previous H1.4 R3 human result: **DEVICE FAIL**

This task replaces the zoomed live-Pager handoff and custom exponential fling. It does not change
Markdown, Room/persistence, Pointer Epoch semantics, tile geometry, cache size or quality policy.
The main package received no install, clear, uninstall, instrumentation or data operation.

## Root-cause audit

Changing the old `3.2/s` or `5.2/s` decay could never fix held-contact drag: decay runs only after
`ACTION_UP`. The production pointer-down chains are now explicit:

- continuous: pointer lifecycle -> `PdfGestureTransformController.pointerFrame` ->
  `ZoomPanPhysics.applyPan` -> document transform -> graphics/tile snapshot;
- horizontal above 1.01x: the same controller -> legal clamp -> horizontal transform; only the true
  X residual enters the adapter-owned page strip.

There is no scale multiply/divide, low-pass filter, velocity smoothing, `animateTo`, spring or Pager
consumption in legal held-contact motion. The old horizontal nested-scroll/live-Pager handoff split
one pointer stream between two owners. Its Pager offset and custom edge offset could compete and
leave the viewport between pages.

A final real-device short-stroke check exposed a second concrete post-release bug: the adapter
started `OverScroller`, then `awaitPdfGestureLifecycle`'s `finally` called `endGesture()`, which
unconditionally reset the controller phase to `IDLE`. Every spline delta was consequently rejected.
`endGesture()` now clears pointer identity but preserves a newly-started `FLINGING` or `SPRINGING`
generation. A new down still cancels it synchronously through `beginGesture -> cancelMotion`.

## Direct contact and platform fling

Legal movement is viewport-pixel 1:1; resistance sees residual only. A bounded 240-sample ring is
written in memory during the gesture and summarized only after release. The final QA controlled
horizontal gesture reported:

- legal drag gain X/Y: `1.0 / 1.0`;
- event-to-main-observer latency: p50 `9 ms`, p95 `15 ms`, max `16 ms`;
- no per-frame JSON or logcat output.

Both adapters call the same `PlatformPdfFlingRunner`. It creates Android `OverScroller` with the
device density and platform scroll friction, consumes the real Compose `VelocityTracker` result,
and uses the native vertical-list spline as one scalar curve over the velocity magnitude. That
scalar distance is projected back onto the X/Y release vector, then each axis is clipped by current
document bounds. This avoids a measured HyperOS vendor asymmetry where equal X/Y velocities were
given materially different `OverScroller` plans, without adding a decay constant. The summary
records release velocity, platform planned final position, spline position, displayed position,
duration, interruption and bound termination. The removed `exp(-decayRate * dt)` path no longer
exists in production.

Final real HyperOS samples show both axis parity and why planned/displayed positions are separate:

| Adapter/sample | Release velocity | Platform planned final | Displayed | Duration | Bounds |
| --- | ---: | ---: | ---: | ---: | --- |
| horizontal X | `-1071 px/s` | `-474 px` | `-461.5 px` | `1238 ms` | terminated at page bound |
| continuous Y | `-1064 px/s` | `-471 px` | `-471 px` | `1433 ms` | not bound-limited |

The release-speed difference is about 0.7%; planned-distance difference is 3 px, about 0.6%.
Both adapters therefore follow one platform curve. Their displayed distance/duration may differ only
when one mode reaches its legal bound earlier. No subjective multiplier was added.

## Single owner and two-page strip

At scale above 1.01 the shared controller owns the pointer stream from down through up.
`HorizontalPager` is not enabled mid-stream. Horizontal mode maintains one adapter state machine:

`IDLE -> EDGE_PREVIEW -> COMMITTING/CANCELLING -> SETTLED`

The legal page offset stops at the real edge. Subsequent true residual moves the current and frozen
adjacent page together at 1:1. Cancel settles to exactly `0`; commit settles to exactly
`+/-viewportWidth`, then synchronizes the page index. The real QA 2x page-2-to-page-3 run logged:

```text
settleTargetPx=-1220.0 settleFinalPx=-1220.0 viewportWidthPx=1220
scaleBefore=2.0 scaleAfter=2.0
```

The final offset domain therefore contains only the current-page anchor or an adjacent-page anchor;
there is no settled between-pages state. Unit tests also cover exact cancel, exact commit, reverse to
zero, velocity commit and one-page-only navigation. Native 1x `HorizontalPager` behavior is intact.

## Page and mode continuity

Page commit preserves the continuous display scale and normalized page-local Y anchor; the target
page enters from the corresponding edge. A cancelled preview preserves the source transform and
does not add a second X rubber offset.

Mode switching now captures one `CanonicalPdfAnchor` containing page index, page U/V, viewport
focal X/Y, source viewport dimensions and display scale. The target mode restores that one record
after geometry is ready. Host round-trip tests require scale delta zero and anchor drift no more
than 4 px/dp; the fixed conversion round trip is exact within floating-point assertion tolerance.
There is no default 1x intermediate restore path.

## Rendering and frame evidence

Active page and strip transforms are read inside `graphicsLayer`; high-frequency page-transition
offset is no longer a parent layout offset. The adjacent target session freezes when edge preview
starts. Gesture telemetry is a bounded ring and Tile planning/rendering remains on the existing
background pipeline.

The device supports 120 Hz, but `dumpsys display` showed the physical display actively rendering at
60 Hz during these captures. The task forbade changing system refresh settings, so no 120 Hz pass is
claimed. Automated ADB input is also not a substitute for human feel.

| Capture | Frames | p50 | p95 | >50 ms | >100 ms | modern jank |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| warm repeated strokes | 308 | 9 ms | 20 ms | 0 | 0 | 13 / 4.22% |
| graphics-layer run | 200 | 10 ms | 21 ms | 0 | 0 | 15 / 7.50% |

Compose recomposition/layout counters were not exposed by the non-instrumented QA run, so no fake
count is reported. Structural evidence confirms transform application moved to `graphicsLayer`;
human smoothness remains an explicit pending gate.

## Automated validation

| Gate | Result |
| --- | --- |
| `:app:testDebugUnitTest` | PASS - 650/650, 0 failed, 0 skipped |
| H1.5 | PASS - 10/10 |
| existing H1.4 | PASS - 18/18 |
| P0C1 series | PASS - 65/65 |
| table-matched suites | PASS - 39 |
| configuration matrix | PASS - 27/27 |
| `:app:lintDebug` | PASS |
| `:app:assembleDebug` | PASS |
| `:app:assembleQa` | PASS |
| `:app:compileQaAndroidTestKotlin` | PASS |
| `:app:assembleQaAndroidTest` | PASS |

No connected suite or main-package instrumentation was run.

## Deliverable and device safety

- APK: `dist/markleaf-qa-r7-h1-5-platform-scroll-page-strip.apk`
- size: `74,210,007 bytes`
- modified: `2026-07-15 10:08:42 +08:00`
- SHA-256: `0227521DE313C8D82B81F63C1268D15C167CA14561F91A92307DB79B6E994B88`
- APK metadata: `com.markleaf.app.qa` / `MarkLeaf QA`
- device: `25098PN5AC / db04f865`

`adb install -r` targeted only the QA package. `com.markleaf.app` and
`com.markleaf.app.qa` coexist, and the main package path remained unchanged. The fixed three-page QA
fixture is open in horizontal mode at page 2/3 and 1x. Final scene evidence is outside Git at
`dist/performance/h1-5-final-human-scene.png`.

## Human status

Automated and structural gates pass, but the H1.4 R3 human failure is not overwritten.
Human device validation is **PENDING**. Only explicit user confirmation can promote this object to
`device_accepted`.

No commit, push, reset, clean or stash was performed.
