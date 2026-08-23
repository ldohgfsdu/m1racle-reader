# R7 H1.3 real gesture path and render regression recovery

Date: 2026-07-14  
HEAD inspected: `f94ce56919f3794e74626f32a1b2b6179cb40cf5`  
Device: `25098PN5AC / db04f865`  
Package under test: `com.markleaf.app.qa` (`MarkLeaf QA`)

## Status

- implementation: complete
- host validation: pass
- synthetic automation: pass, but not representative of the complete production path
- real UI automated validation: blocked by the HyperOS multi-display instrumentation runner
- human device validation: pending retest
- overall: implementation complete, device retest pending

This report does not upgrade the feature to `device_accepted`. The previous H1.2 human run remains
a device failure until a person accepts this build.

## Why the old automation produced a false positive

The existing MotionEvent suite did reach the production `PdfHorizontalPagedArea` pointer input, but
used a blank in-memory bitmap and asserted mostly against `HorizontalInteractionProbe` state. Its
tile regression case was a separate continuous-mode harness: it converted plans into fake
`PdfTileEntry` counters without using the horizontal page, `PdfRenderer`, the production work queue,
or the final overlay. Pager handoff assertions observed the policy state and not an actual
`HorizontalPager` page transition. The runner also did not verify the active Android display; on this
HyperOS device an attempted injection initially landed on the secondary-display launcher.

The result is retained as component/synthetic validation only.

## Root causes and corrections

| Human failure | Root cause | Correction |
| --- | --- | --- |
| Legal pan felt heavy | The test evidence did not prove end-to-end displayed movement, while ownership and pointer transition paths could suppress or rebase deltas. | The shared kernel consumes legal X/Y delta exactly 1:1; only residual enters resistance. A new pointer cancels spring/fling/settle ownership. |
| Edge pull felt hard | Early rubber-band response was too small and spring duration was outside the requested window. | Continuous exponential curve now has 1.35 initial gain. A 12px pull displays 11.78px; 24/20px spring settles at about 250ms at 120Hz. Legal offset is unchanged by spring. |
| Zoomed edge could not turn page | `HANDOFF_ACTIVE` only dispatched residual nested scroll while the real Pager remained unavailable to that path. | A single handoff generation now directly animates the real `PagerState` to the adjacent page; cancel retains the current transform and commit still resets both pages. |
| 2→1→2 jumped | Previous probe asserted only internal state on the rebase frame, not content-anchor continuity. | Pointer-id set changes are rebase-only frames. Centroid/span history is rebuilt from stable IDs and scale/offset are unchanged on the transition frame. |
| No horizontal tiles during pinch/hold | Horizontal mode had no tile-plan callback or tile overlay; the ViewModel explicitly rejected non-continuous requests. | Horizontal snapshots feed a conflated 16ms planner, then the existing VM-lifetime queue, real `PdfRenderer`, cache, and page-local overlay. Horizontal requests are now accepted. |
| No next-page preload while held | There was no horizontal page-ahead plan. | Current center/visible requests are emitted first, then adjacent `PAGE_AHEAD`; the actor continues while pointer-down is true. |
| Dark mode showed a white unused block | The horizontal viewport, Pager, and unused page container used `Color.White`. | Unused surround uses `MarkLeafTheme.extra.pdfCanvas`; only the actual PDF paper stays white. Controls remain overlays. |
| Left blank could jump during pinch | Display overscroll could be confused with legal coordinates in incomplete evidence. | Scale rebase is based only on legal offset and the content focal anchor; visual overscroll is never folded into legal offset. Human fixed-motion retest remains required. |

## Actual production call chain

`MotionEvent` → Compose `pointerInput/awaitEachGesture` → stable pointer-id epoch reducer →
horizontal interaction/handoff policy → shared zoom-pan kernel → displayed graphics transform →
latest viewport snapshot → conflated 16ms horizontal tile planner → ViewModel tile request →
`TileWorkQueue` → real page renderer/cache → page-local tile overlay swap.

## Telemetry and geometry evidence

QA-only JSONL now records pointer ids/positions/count, pointer epoch, rebase frame, gesture phase and
owner, centroid/delta, scale factor, scale, legal/visual offsets, bounds, raw/consumed/residual pan,
handoff state/accumulation, page index, planner tick, request/ready/swap counts, visible coverage,
page-ahead count, and background token.

Final-build real MainActivity evidence on display 0 used the real three-page PDF and production
renderer. During pointer-down the log reported `gesturePhase=DRAGGING`, `tileRequestCount=24`,
`tileReadyCount=16`, `tileSwapCount=8`, `pageAheadRequestCount=24`, and
`visibleCoverage=1.0`. This proves horizontal request/ready/swap and page-ahead work are no longer
gated on ACTION_UP. It is not a substitute for the requested three-second human pinch.

- legal drag gain X/Y: 1.00 / 1.00 in the shared-kernel tests (required 0.95–1.05)
- rubber curve for a 24px limit: raw 4/8/12/24px → visual 4.84/8.70/11.78/17.78px
- spring: about 250ms from X=24px/Y=20px at 120Hz, without changing scale/legal offset
- pointer transition frame: transform delta 0; pure-test anchor drift 0px
- real runner comparison: pre-fix final horizontal renderer path was absent; post-fix real path
  publishes real renderer counters above, but the multi-display instrumentation scenario remains blocked

Light/dark QA screenshots are retained outside Git at
`dist/performance/h13-light.png` and `dist/performance/h13-dark.png`. The dark capture shows themed
surround around a distinct paper surface; no unused hard-coded white viewport remains.

## Fixture and device safety

- Fixture: QA-generated three-page PDF with page edges, text, fine lines and table geometry
- Fixture bytes: 43,277
- Fixture SHA-256: `33D2C04400096299480F72E7FD3FDBE3E88F7D1DAC614AD72DE67E79A4DFDAEE`
- Both `com.markleaf.app` and `com.markleaf.app.qa` remained installed.
- Installation used only `adb -s db04f865 install -r` for the QA package.
- No main-package install, clear, uninstall, instrumentation, database, or file operation was run.

## Validation

- JVM: 620/620 PASS
- H1 minimum gate (59): PASS; H1.3 recovery suite 5/5 PASS
- P0C1 series minimum gate (65): PASS
- P0C1.4 minimum gate (23): PASS
- `lintDebug`: PASS
- `assembleDebug`: PASS
- `assembleQa`: PASS
- `compileQaAndroidTestKotlin`: PASS
- `assembleQaAndroidTest`: PASS
- real UI instrumentation: BLOCKED, not PASS (HyperOS multi-display runner did not deliver the
  complete gesture to the foreground production viewport)

## Deliverable

- APK: `dist/markleaf-qa-r7-h1-3-real-gesture-recovery.apk`
- applicationId: `com.markleaf.app.qa`
- label: `MarkLeaf QA`
- size: 73,584,607 bytes
- modified: 2026-07-14 21:31:30 +08:00
- SHA-256: `D0EA73D4F0774C0299A2DFD7CE27B1D789C82C6BEE49186A1D346E1BAB564CE4`

Human acceptance: **PENDING**.
