# R7 H1.5 Three-page strip and continuous page transition

Date: 2026-07-15  
Baseline HEAD: `f94ce56919f3794e74626f32a1b2b6179cb40cf5`  
Device: `25098PN5AC / db04f865`  
Status: implementation complete; host/build/isolated-QA device automation PASS; human acceptance PENDING.

## 1. Replaced call chain

The previous horizontal path had two visual owners. At 1x, `HorizontalPager(rememberPagerState)`
owned the page position and `snapshotFlow(pagerState.settledPage)` called `onPageChanged`. At zoom,
the page-local transform accumulated an edge transition and later changed `pageIndex`; the state
change then caused `PdfViewerViewModel.onHorizontalPageSettled` to render/publish a different page.
Consequently the current surface could move before the neighboring surface was part of the same
visual coordinate space.

The production path is now:

`awaitPdfGestureLifecycle` -> `PdfGestureTransformController` -> page-local legal pan -> residual X
-> `ThreePageStripPhysics.drag` -> `pageStripOffsetPx` graphics-layer translation ->
`ThreePageStripPhysics.release` -> exact settle -> stable slot rotation -> atomic rebase to zero ->
`onHorizontalPageSettled`.

`HorizontalPager`, `rememberPagerState`, `animateScrollToPage`, and the separate zoomed page
transition implementation are absent from the horizontal production path. There is one pointer
owner and one page-transition implementation.

## 2. Three-page state and coordinate model

`ThreePageStripState` always describes stable physical slots with stable ids:

- `PREVIOUS`: page `current - 1`, base center `-viewportWidth`
- `CURRENT`: page `current`, base center `0`
- `NEXT`: page `current + 1`, base center `+viewportWidth`

Every slot uses:

`slotTranslationX = role.baseMultiplier * viewportWidth + pageStripOffsetPx`

and is keyed by its stable slot id. Slot translation is applied before slot clipping/background, so
the background, base bitmap, and tiles move together; the outer viewport performs the final clip.
During a NEXT commit, the old NEXT physical slot becomes CURRENT, old CURRENT becomes PREVIOUS,
and old PREVIOUS is recycled as NEXT. PREVIOUS is symmetric. The visual page therefore does not
depend on replacing the content of one stationary page container.

## 3. Gesture consumption

- `scale == 1`: page-local X/Y offsets are zero. The same controller receives the complete pointer
  stream and horizontal delta directly drives `pageStripOffsetPx` at 1:1.
- `scale > 1.01`: `PdfGestureTransformController` first consumes legal page X/Y pan. Only outward
  residual X at the real content edge starts/drives the strip. Once a strip transition is active,
  raw X continues the same strip without a second dead zone. Residual is never written back into
  `legalOffsetX`, and no Pager receives the same delta.
- Pointer-down interrupts pan fling, spring, or strip settle at the currently displayed offset; it
  does not jump to an animation target first.

## 4. Page-index commit timing

Drag and settle keep the outgoing `pageIndex`. Release produces either CANCEL or COMMIT. CANCEL
settles to zero and never changes the page. COMMIT first settles to exactly `+/-viewportWidth`,
restores the target page's continued scale/normalized anchor, rotates stable slots, rebases
`pageStripOffsetPx` to zero, and only then calls `onPageChanged(targetPage)`. Reading progress and
ViewModel window movement therefore happen after visual settle, not from transition progress.

## 5. Neighbor base/detail preheat

`PdfViewerViewModel.ensureHorizontalStripAround` protects exactly the current three-page window
and requests base surfaces in the order CURRENT, interaction-direction neighbor, other neighbor.
Pages outside the new three-page window are eligible for eviction only after commit. Strip motion
is gated on the target base bitmap being ready; a missing base records
`H15_STRIP_BLOCKED_MISSING_BASE` and does not expose a gray substitute.

`HorizontalTilePlanning` gives the entering edge of NEXT/PREVIOUS an explicit detail preheat band
at the current zoom bucket while preserving the normalized vertical anchor. Until detail tiles
arrive, the real neighbor base remains visible. Tile swaps do not change slot identity or gesture
ownership.

## 6. Frame-by-frame no-gray evidence

`R7H15ThreePageStripRealUiTest` ran against the real QA `MainActivity`, repository and
`PdfRenderer`. At held-pointer frames 8, 16 and 24 it asserted:

- page 2 remained the committed page;
- target base readiness was true and neighbor bitmap identity was not `-1`;
- the visible neighbor band contained real fixture detail rather than a uniform canvas;
- no page commit log existed before ACTION_UP.

Evidence:

- [frame 8](../dist/performance/h1-5-three-page-strip/h15-three-page-preview-frame-8.png)
- [frame 16](../dist/performance/h1-5-three-page-strip/h15-three-page-preview-frame-16.png)
- [frame 24](../dist/performance/h1-5-three-page-strip/h15-three-page-preview-frame-24.png)

All three frames show CURRENT and NEXT page surfaces in the same clipped viewport. The modifier
order regression test also prevents a stationary full-screen slot background from covering the
translated CURRENT slot.

## 7. Cancel/commit final offsets

On the 1220 px device viewport:

- CANCEL: settle target `0`, settle final `0`; real-device log final `0`.
- COMMIT NEXT: settle target `-1220`, settle final `-1220`, rebase `0`.
- COMMIT PREVIOUS: settle target `+1220`, settle final `+1220`, rebase `0`.
- Zoomed preview: scale `2.5375118` remained unchanged while NEXT base was ready.

Page index changed exactly once after a committed settle and did not change during held preview.

## 8. No between-pages state

The transition state can animate only toward zero or the signed viewport width. `settleTargetPx`
has no other stable result. Device tests cover cancel, NEXT and PREVIOUS, then assert a zero strip
rebase. Pure-state tests cover exact settle, interruption, boundaries and zero visual displacement
across stable slot rotation. There is no persistent fractional-page state.

## 9. Renderer/surface lifetime

The outgoing CURRENT slot and target neighbor coexist before motion. Their base/tile surfaces are
keyed by physical slot id and retained through settle. The ViewModel protects current +/-1, and
does not move the cache window until the post-settle page commit. The real UI test observed a real
neighbor bitmap before release; only after slot rotation may the page leaving the new three-page
window be recycled.

## 10. Input-to-display and frame data

Fresh real-device `adb input` traces (not the serialized UiAutomation synthetic clock) recorded:

- long transition: event-to-main observer p50/p95/max `9/15/17 ms`, direct drag gain `1.0/1.0`;
- 1x short preview/cancel: p50/p95/max `7/14/20 ms`, direct drag gain `1.0/1.0`;
- controlled 1x strip window: 794 rendered frames, p50/p90/p95/p99 `6/11/14/24 ms`,
  frame-deadline missed `1` (`0.13%`).

The display supports 120 Hz but HyperOS had negotiated an active 60 Hz mode during this capture.
No refresh setting was changed and this report does not claim locked 120 fps. The QA probe did not
capture authoritative numeric Compose recomposition/layout counts, so none are invented; stable
keys, graphics-layer-only strip motion, and source/JVM architecture assertions are the available
evidence for avoiding per-frame viewport reconstruction.

## 11. Mode-switch anchor continuity

The H1.4 canonical-anchor regression remains green: 18/18 host tests, zero scale delta, and the
round-trip gate limits page-local anchor drift to <=4 px. The three-page strip consumes the same
`PdfViewportSessionState`/`CanonicalPdfAnchor` and does not add a separate mode-switch record.
This task did not obtain a new real-finger numeric anchor-drift measurement; visual mode-switch
continuity remains part of the pending human acceptance and is not called device-accepted.

## 12. Regression and build gates

- JVM: `666/666` PASS, 0 failure/error/skip.
- H1.5: `24/24` PASS.
- H1.4: `18/18` PASS.
- P0C1 series: `65/65` PASS; P0C1.4 remains `23/23`.
- Markdown matched tests: `56`; table tests: `39`; configuration matrix: `27/27`.
- `:app:lintDebug`: PASS.
- `:app:assembleDebug`: PASS.
- `:app:assembleQa`: PASS.
- `:app:compileQaAndroidTestKotlin`: PASS.
- `:app:assembleQaAndroidTest`: PASS.
- Isolated QA instrumentation: component path `5/5` PASS; real reader path `2/2` PASS.

## 13. QA artifact and data safety

- APK: `dist/markleaf-qa-r7-h1-5-three-page-strip.apk`
- Size: `74,219,483` bytes
- SHA-256: `898D6162D05C5B536355A3756B25499FA4140A9732E7BCBE2C37CC04749E41E8`
- Binary applicationId: `com.markleaf.app.qa`
- Binary label: `MarkLeaf QA`
- Fixture: `files/h1-real-gesture-fixture.pdf`, 43,277 bytes, 3 pages,
  SHA-256 `33D2C04400096299480F72E7FD3FDBE3E88F7D1DAC614AD72DE67E79A4DFDAEE`.

Only the isolated QA app/test packages were installed or instrumented. `com.markleaf.app` was
queried read-only and its package path remained unchanged. No main-package install, clear,
uninstall, instrumentation, database access, or file write occurred.

## 14. Human acceptance

The QA reader is installed and staged in horizontal mode at the fixed fixture page 2/3 and 1x.
Human acceptance is **PENDING**. Automation has not been converted into `device_accepted`.

No commit, push, reset, clean, or stash was performed. Markdown and Room were not modified.
