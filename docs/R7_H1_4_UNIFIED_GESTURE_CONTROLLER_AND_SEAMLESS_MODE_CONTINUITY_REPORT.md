# R7 H1.4 unified gesture controller and seamless mode continuity

Date: 2026-07-15  
HEAD inspected: `f94ce56919f3794e74626f32a1b2b6179cb40cf5`  
QA package: `com.markleaf.app.qa` (`MarkLeaf QA`)

## Status

- implementation: **complete**
- host automated validation: **pass**
- build validation: **pass**
- QA device installation: **pass — `db04f865` online; QA and main packages coexist**
- main-package data safety: **pass — zero writes; package path unchanged**
- QA device instrumentation: **blocked — one attempt produced no test result under the HyperOS multi-display runner**
- human device acceptance round 1: **FAIL**
- human device acceptance R2: **FAIL**
- human device acceptance R3: **PENDING**
- overall: **implementation complete, human revalidation pending**

This report does not mark H1.4 `device_accepted`. R2 fixed focal pinch and pointer takeover, but
human round 2 still rejected zoomed edge handoff effort, horizontal-to-continuous precision and the
shared repeated-stroke fling feel. R3 requires another human run.

## Human round 1 and R2 correction

Round 1 passed in-range drag parity, stable 2-to-1-to-2 pointer lifecycle, zoomed page scale
continuity, gesture-time tiles, page-ahead preload, dark surround and 1x Pager. It failed because:

- the horizontal legal range was derived from the outer viewport. During early 1x pinch frames the
  fitted page had no legal X/Y room, so the focal point moved away until the scaled page crossed the
  viewport and then returned;
- a handoff activation started `animateScrollToPage` while subsequent pointer deltas also drove
  nested Pager scroll, making the page race the finger and stealing ordinary edge rebound;
- a continuous viewport center inside a page gap was assigned to the next page and its negative
  page-local fraction was coerced to zero;
- target mode state was committed in an effect after composition, leaving a possible old/default
  transform frame.

R2 derives focal bounds from fitted-page scale growth starting at 1.005x, transfers any in-flight
1x Pager displacement into the second-pointer rebase, removes the competing Pager animation,
requires meaningful outward distance even at high velocity, selects a dominant visible page and
real page point for gap/blank anchors, and draws the target session synchronously before the async
state commit catches up.

## Human round 2 and R3 correction

Round 2 passed focal pinch (`指哪放哪`), one-finger-to-two-finger takeover, 2-to-1-to-2 stability,
continuous-to-horizontal improvement, gesture-time tile rendering, page-ahead preload, dark
surround and 1x Pager. It failed because:

- navigable zoomed edges still accumulated a separate 42dp inner-page threshold before Pager
  ownership, so rebound and page navigation competed;
- release dispatched zero fling velocity to Pager, making an otherwise deliberate sustained drag
  cancel unless it had already crossed a large positional threshold;
- horizontal used an 8dp left/right fit inset while continuous used full viewport width. Equal
  numeric scale therefore produced unequal physical page width and a small mode-switch jump;
- mode switching could read the last persisted/stable session instead of the newest UI transform;
- the shared exponential fling decay (`5.2/s`) stopped short repeated strokes too quickly in both
  continuous and horizontal adapters.

R3 makes every navigable X edge Pager-owned from the first true residual. The legal part of the
crossing frame is committed to the page edge, inner X rubber is cleared, only the first residual is
dispatched, and all later frames plus the real release velocity are delivered to Pager. Pager now
owns its native commit/cancel decision; there is no second inner 42dp gate.

The shared fling decay is `3.2/s`, retaining 1:1 pointer drag while increasing a 1000px/s reference
fling from about 192px to about 320px. Both modes receive the same change. Mode switching now reads
an in-memory live viewport preview that is explicitly not flushed to persistence. Portrait
horizontal fitting retains the 8dp top gap but uses the same physical page width as continuous.

## Root cause: horizontal did not reuse the complete continuous chain

Horizontal mode previously maintained its own `awaitEachGesture` lifecycle, pointer epoch,
centroid/span history, focal calculation, single-finger integration, fling/spring ownership and
page-local transform. Sharing a few pure `ZoomPanPhysics` functions did not make those two state
machines equivalent.

The audit also found a concrete 1-to-2 discontinuity: while 1x Pager owned a single pointer,
horizontal still advanced its hidden transform controller. A later second pointer rebased from a
transform that could contain unrendered overscroll. The displayed page and the next pinch epoch no
longer had the same starting transform, producing the observed detour/jump.

Page commit separately restored both sides to 1x, and mode switching moved page/scale/anchor through
different asynchronous owners. Those paths directly contradicted the new document-session rule.

## Extracted production controller

`PdfGestureTransformController` now owns:

- stable pointer ids and pointer epochs;
- pointer-set rebase-only transitions;
- centroid/span baselines and focal content anchor;
- continuous Float display scale;
- legal pan and residual-only overscroll;
- fling velocity/integration state;
- spring integration state;
- motion-generation cancellation;
- interaction phase and transform snapshots.

`awaitPdfGestureLifecycle` is the shared exception-safe down/up lifecycle used by both production
viewports. It owns `awaitEachGesture`, first-down capture and controller teardown. The two adapters
retain only mode-specific rendering and navigation callbacks.

When Pager owns a one-finger frame, `trackPointersWithoutTransform` advances stable pointer history
but discards transform output. Thus a later 1-to-2 transition has a current pointer baseline without
hidden pan or overscroll.

## Shared controller / adapter boundary

| Shared controller | Continuous adapter | Horizontal adapter |
| --- | --- | --- |
| pointer lifecycle, epoch, centroid/span, focal math | document geometry and global scroll mapping | page geometry and fitted page bounds |
| legal pan, residual overscroll, scale | continuous composition/render callbacks | adjacent page selection and page-ahead planning |
| fling, spring, cancellation, snapshot | document-local tile viewport | Pager edge intent, COMMIT/CANCEL and visual transition |

Pager does not reinterpret pinch centroid, drag gain or pointer rebase. It only consumes the shared
controller's horizontal residual/intent after the page reaches a legal X edge.

## 2-to-1-to-2 and focal evidence

The transition frame only rebases. It applies no pan, scale or overscroll:

- 2-to-1 transform delta: `0`
- 1-to-2 transform delta: `0`
- hidden Pager transform delta before 1-to-2: `0`
- host focal-anchor drift in the interior trace: `<= 0.001 px`
- required real-device limits (`<= 4dp` visual jump, `<= 2dp` focal drift): device validation pending

The controller projects the captured content anchor back into viewport coordinates on each pinch
frame, so the QA probe can report real focal drift instead of inferring it from scale alone.

## Legal drag parity

The accepted continuous trace was characterized before horizontal adaptation. Both adapters now
receive the same controller result for the same in-range pointer delta:

- continuous legal drag gain X/Y: `1.000 / 1.000`
- horizontal legal drag gain X/Y: `1.000 / 1.000`
- host difference: `0.000 / 0.000` (limit `<= 0.05`)

Only residual past a legal bound enters rubber-band resistance. Visual overscroll never becomes the
next frame's legal input.

## Zoomed page continuity

The old `pageCommitTransform() -> 1x/zero` rule and its assertions were removed. A page commit now
serializes the outgoing transform as a normalized page-local session and restores the target using:

- the same continuous Float scale (`2.5x -> 2.5x` in the fixed test);
- the same normalized vertical anchor (`delta <= 0.001` in host geometry tests);
- NEXT at the new page's left legal edge;
- PREVIOUS at the new page's right legal edge;
- target-page rendering immediately at the current scale/bucket.

Adjacent Pager pages are precomposed with this continued transform, so they are not intentionally
rendered at 1x before the commit effect applies. A cancelled handoff preserves the current transform.

## Continuous / horizontal mode continuity

`PdfViewportSessionState` is the single document-session handoff contract. It carries mode, page,
page-local normalized anchor, viewport anchor, continuous scale and runtime legal/visual/phase/epoch
metadata. Existing persisted fields and Room schema remain unchanged; runtime-only fields have
defaults and are not added to Room.

Both directions use the same session:

- continuous -> horizontal: same page, `2.5x`, normalized page anchor;
- horizontal -> continuous: maps that page-local point back into document coordinates;
- host round trip: page unchanged, scale delta `0`, normalized Y-anchor delta `<= 0.001`;
- no product reset to 1x is executed during mode switching.

QA-only real MainActivity tests were added for the full mode-switch and zoomed-page paths. They parse
`H14_MODE_SWITCH_REQUEST`, `H14_MODE_APPLY` and `H14_PAGE_COMMIT` evidence. Their APK was installed
only against `com.markleaf.app.qa`, but the single device attempt produced neither test-start nor
test-result output while HyperOS kept display 0 off. The run was safely stopped and is recorded as
BLOCKED, not PASS; host assertions are not presented as real-device results.

## Tile bucket independence and preserved capabilities

Display scale remains a continuous Float. `PdfZoomBuckets` only selects render requests. The fixed
2.49-to-2.51 bucket-boundary test records zero transform delta, zero legal-offset delta and no
recenter.

Regression results retained on the host:

- H1.3 recovery suite: 5/5 PASS
- P0C1 series: 65/65 PASS
- P0C1.4 gesture/render decoupling: 23/23 PASS
- existing table layout/header suites: 37/37 PASS
- table cache: 2/2 PASS
- display configuration matrix: 27/27 PASS
- Markdown-matched suites: 56 PASS

Four-edge spring, zoomed handoff policy, dark themed surround, tile request/ready/swap during input,
page-ahead planning, left-blank legal/visual separation, top alignment, control overlay and 1x Pager
remain covered. Human verification of those preserved behaviors is still required on this APK.

## Validation

| Gate | Result |
| --- | --- |
| `:app:testDebugUnitTest` | PASS — 640/640, 0 failed, 0 skipped |
| H1.4 new tests | PASS — 18/18 |
| `:app:lintDebug` | PASS |
| `:app:assembleDebug` | PASS |
| `:app:assembleQa` | PASS |
| `:app:compileQaAndroidTestKotlin` | PASS |
| `:app:assembleQaAndroidTest` | PASS |
| QA APK install on `db04f865` | PASS |
| main/QA package coexistence | PASS |
| real QA device suite | BLOCKED — HyperOS multi-display runner returned no test result; not reported as PASS |

## Deliverable

- path: `dist/markleaf-qa-r7-h1-4-direct-handoff-shared-fling-r3.apk`
- applicationId read from APK: `com.markleaf.app.qa`
- label read from APK: `MarkLeaf QA`
- size: `74,202,347 bytes`
- modified: `2026-07-15 08:26:36 +08:00`
- SHA-256: `0AC4C249D8A5DD61539B0B4BC536EF1B4D9FF9FAB3875F28BBEBD4522F3A92BE`

Device `25098PN5AC / db04f865` reported `device`. `adb install -r` installed only the QA APK.
The main package path remained exactly:

`package:/data/app/~~4jkavlkAMK-ErCujCjTPBA==/com.markleaf.app-0I8Y4V1-LZz37ixR-gFPyQ==/base.apk`

The installed QA package path is:

`package:/data/app/~~VlxPfcY3VG6x79nzJvpneg==/com.markleaf.app.qa-cdc0tA32UZTro2tigKHQCw==/base.apk`

Thus `com.markleaf.app` and `com.markleaf.app.qa` coexist. No install, clear, uninstall,
instrumentation or data access targeted the main package.

The QA-only fixture is `files/h1-real-gesture-fixture.pdf`, 43,277 bytes, SHA-256
`33D2C04400096299480F72E7FD3FDBE3E88F7D1DAC614AD72DE67E79A4DFDAEE`. The real QA
`MainActivity` was resumed with its fixture intent on display 0. HyperOS left the secure keyguard
showing and display 0 off during the runner attempt, so no performance metric or real UI assertion
is claimed from that attempt. After user unlock, the scene was prepared through the existing UI in
horizontal mode at page 2/3 and 1x. R3 scene evidence is kept outside Git at
`dist/performance/markleaf-h14-r3-scene-1x.png`.

No commit, push, reset, clean or stash was performed.

## Human acceptance checklist

- internal drag: matches continuous / still heavy / too slippery
- pinch: focal / still detours then returns
- 2-to-1-to-2: stable / jumps
- zoomed page turn: target keeps scale / resets to 1x
- page entry: natural edge / abrupt
- continuous -> horizontal: seamless / jumps or resets
- horizontal -> continuous: seamless / jumps or resets
- four-edge rebound, tile-during-gesture, next-page preload, dark surround and 1x Pager: normal / regressed

Human acceptance rounds 1 and 2: **FAIL**. R3 revalidation: **PENDING**.
