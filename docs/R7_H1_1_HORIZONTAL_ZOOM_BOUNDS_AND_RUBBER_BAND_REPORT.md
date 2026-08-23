# R7 H1.1 Horizontal Zoom Bounds and Rubber Band Report

**Date:** 2026-07-13
**Baseline:** `4241b3b4a0a9af6d99e6f83a96092d19b6e2065e`
**Status:** `implementation_complete_device_acceptance_pending`
**P0C1.4:** remains `device_accepted`
**H1.2 zoomed-edge Pager handoff:** deferred

## Result

H1.1 now uses one coordinate system, `VIEWPORT_PIXEL_COORDINATES`, from pointer input through
legal pan, rubber-band display translation and spring target. A zoomed page can pan 1:1 inside its
real centered fitted rect, edge pull is visually capped at 28dp independently of scale, and release
springs only visual overscroll to zero. Above 1.01x the page owns the full gesture and cannot hand it
to Pager in this object.

The implementation is not marked device accepted. Automated 2x device evidence and directed
instrumentation pass, but the required real 3x/4x multi-touch sequence, 2-to-1-to-2 continuity and
subjective drag/spring feel still require the user's confirmation.

## Workspace gate

- Absolute path and Git root: `C:\Users\21234\Desktop\MarkLeaf`.
- HEAD and required checkpoint: `4241b3b4a0a9af6d99e6f83a96092d19b6e2065e`.
- Pre-change H1 SHA-256:
  `21AF364A4C0689EAC88BA8D6758F5A7C491E8E06838226C4F515511C058B1859`.
- Baseline JVM result: 508/508 PASS.
- `TileWorkQueue.kt`, `FinalPdfProgressFlusher.kt`, Room `6.json`, and the P0C1.4 report were all
  present. The worktree was not reset, cleaned, checked out or otherwise normalized.
- Existing permission-bit changes, checksums, raw `dist/performance` logs and unrelated untracked
  files were left untouched.

## Pre-change draw-chain audit

| # | Question | Actual chain and finding |
|---|---|---|
| 1 | Viewport width/height source | `PdfChrome` applies status-bar padding, lays out the fixed top bar, then gives its clipped `weight(1f)` viewport Box to `PdfHorizontalPagedArea`. That function's outer full-size clipped Box records the exact remaining pixel size in `onSizeChanged`; this is `viewportSize`. |
| 2 | Fitted page size | Before H1.1 it was implicit: centered `Surface.padding(8.dp)` plus `Image.fillMaxWidth(ContentScale.Fit)`. H1.1 makes it explicit using stable `state.pageAspects[page]` (height/width), the measured viewport and one 8dp margin on each side. |
| 3 | Scale layer | Horizontal zoom is applied once on the page `Surface` via `graphicsLayer`. No Canvas or layout zoom is applied, and `Image` only fits inside the already sized Surface. |
| 4 | Offset unit | Pointer positions/deltas and `graphicsLayer.translationX/Y` are viewport pixels. The old names did not make that contract explicit. |
| 5 | Repeated scale | Compose graphics-layer translation is already in parent/viewport pixels and is not multiplied by scale. H1.1 keeps it that way. |
| 6 | Old bounds input | Old `clampOffset` used `viewportSize.width/height * (scale - 1) / 2`, which assumes the fitted page fills both viewport axes. It ignored the real Surface rect and page aspect. |
| 7 | Content origin | The page Box uses `Alignment.Center`; the Surface uses `TransformOrigin.Center`. At zero translation, its origin is `((viewportWidth-fittedWidth)/2, (viewportHeight-fittedHeight)/2)`. |
| 8 | Pager/insets/padding | `HorizontalPager` has no content padding or page spacing. System/top-bar insets are already reflected in the measured viewport. The page's 8dp visual margin affects fitting exactly once and is not separately added to pan bounds. |
| 9 | Old 56dp resistance | It was converted to pixels inside the pointer path and used as the saturation limit. Graphics translation did not multiply it again, but the result was mixed with legal offset and the limit was twice the H1.1 target. |
| 10 | Handoff accumulation | `edgeAccumulation` was a separate field, but the next raw position was calculated from legal content plus raw overscroll. Crossing 32dp directly called `onPrevious/onNext`, mixing inner physics and ownership policy. |
| 11 | Old spring target | Release interpolated one mixed display `offset` back to a `clampOffset` target computed from the incorrect viewport-sized bounds. A new down could re-clamp an in-progress visual value into legal position. |
| 12 | Duplicate consumption | Pager was disabled above 1.01x and the page consumed moves, so there was no `dispatchRawDelta`; however the page detector could also consume the first one-pointer change at 1x. H1.1 leaves one-pointer changes unconsumed when Pager owns them. |
| 13 | Bitmap rect vs container | The Pager page container is the whole viewport; the actual page/bitmap Surface is smaller and centered. Treating those as equal was the primary bounds error. |
| 14 | Page proportions | Portrait, landscape and other page aspects were all forced through the same viewport-only bound formula. H1.1 recomputes the fitted rect and bounds per page aspect and on viewport resize. |

The production call chain is:

```text
PdfViewerScreen Ready/HORIZONTAL_PAGED
  -> PdfHorizontalPagedArea
     -> PdfChrome weighted clipped viewport
     -> outer Box.onSizeChanged (viewport px)
     -> HorizontalPager (no padding/spacing)
     -> full-viewport page Box, centered
     -> explicit fitted Surface
     -> graphicsLayer(center scale + viewport-px translation)
     -> Image.fillMaxSize(ContentScale.Fit)
```

## Coordinate contract and implementation

`HorizontalZoomPanPhysics.fitPage` computes the unscaled fitted Surface:

```text
availableWidth  = viewportWidth  - 2 * 8dpPx
availableHeight = viewportHeight - 2 * 8dpPx
fittedWidth/fittedHeight = aspect-preserving fit inside available rect
```

`HorizontalZoomPanPhysics.bounds` then computes, independently per axis:

```text
scaledContent = fittedContent * scale
maxPan = max(0, (scaledContent - viewport) / 2)
legalOffset in [-maxPan, +maxPan]
```

The transform state explicitly separates:

- `legalOffsetX/Y`: always clamped to real bounds;
- `rawOverscrollX/Y`: residual edge pull used to make reversal continuous;
- `visualOverscrollX`: bounded horizontal rubber-band output; Y has no visual/raw overscroll state;
- `displayOffset = legalOffset + visualOverscroll`.

Within the legal range, pointer delta is consumed 1:1. At an edge, only the unconsumed residual is
added to raw overscroll. Reverse motion removes raw edge pull before legal movement resumes. The
visual mapping is symmetric and monotonic:

```text
visual = sign(raw) * maxOverscrollPx * (1 - exp(-abs(raw) / maxOverscrollPx))
maxOverscrollPx = 28.dp.toPx() // converted once, independent of scale
```

Release runs Compose `spring(DampingRatioNoBouncy, StiffnessMedium)` over X visual overscroll only.
Y always hard-clamps to its legal top/bottom bound and never starts a spring.
Legal position stays fixed. A new down cancels the spring at its currently displayed value. Every
pointer-set transition is a rebase-only frame using the current display offset, so visual pull is
not folded into legal position.

## Pager ownership

- `scale <= 1.01`: `owner=PAGER`; inner legal offset is zero and a one-pointer page detector does
  not consume Pager's movement.
- `scale > 1.01`: `owner=INNER_PAN`; Pager is disabled and the page consumes the gesture.
- There is no H1.1 reference to the old 56dp limit, 32dp threshold, `dispatchRawDelta`, residual
  velocity, zoomed-edge `onPrevious/onNext`, or `animateScrollToPage`.
- Edge pull only rubber-bands and returns. H1.2 is not implemented.

## Physical-device evidence

Device: `25098PN5AC / db04f865`, physical size `1220x2656`, density `520dpi` (3.25 px/dp).

On PDF page 201, the actual horizontal viewport and stable page aspect produced:

```text
viewport: 1220 x 2191 px
fitted page: 1168 x 1610.8245 px
```

The real-geometry bounds are:

| Scale | Scaled content px | Legal X px | Legal Y px | Evidence class |
|---:|---:|---:|---:|---|
| 2x | 2336 x 3221.6490 | +/-558 | +/-515.3245 | Production `H1_BOUNDS` log + pan sample |
| 3x | 3504 x 4832.4735 | +/-1142 | +/-1320.7368 | Deterministic calculation from measured device rect; multi-touch not run |
| 4x | 4672 x 6443.2980 | +/-1726 | +/-2126.1490 | Deterministic calculation from measured device rect; multi-touch not run |

At 2x:

- `H1_OWNER` logged `INNER_PAN`, `pagerEnabled=false`.
- Small horizontal and vertical drags logged `rawDelta == legalConsumed`, `residual=0`, and
  `visualOverscroll=0`, confirming viewport-pixel 1:1 inner pan.
- A large positive pull reached `26.965872dp`; a large negative pull reached `27.948116dp`.
  Neither can reach a half-screen blank because the configured asymptote is 28dp (91px here).
- Page label remained `201 / 982` through both zoomed edge pulls.
- Official spring returned X visual overscroll to `0` in 179ms and 180ms, without changing the
  logged legal boundary.
- Returning to 1x logged `owner=PAGER`, `pagerEnabled=true`; one Pager swipe changed page 201 to
  202. The device was finally left on page 201, horizontal mode, 1x.

These automated observations do not establish subjective feel or multi-pointer continuity. The
user must still run the requested 2x/3x/4x and 2-to-1-to-2 sequence before acceptance.

The earlier device sample predates the product correction that removed vertical rubber-band. Its
horizontal 28dp measurements remain useful, but it is not evidence for the corrected Y contract.
Corrected physical-device acceptance must verify: legal X/Y pan works; only left/right edges have
light elasticity; top/bottom and diagonal edge drags never reveal vertical blank; release springs
only X; no accidental page turn or transform jump occurs. This remains pending.

## Verification

| Gate | Result |
|---|---|
| `:app:testDebugUnitTest` | PASS, 528/528; 0 failed/errors/skipped |
| H1 pure logic | PASS, 33/33, including Y hard-clamp at 2x/3x/4x and diagonal drag |
| P0C1.4 JVM regression | PASS, 23/23 |
| `:app:lintDebug` | PASS |
| `:app:assembleDebug` | PASS |
| `:app:compileQaAndroidTestKotlin` | PASS |
| Directed H1 device instrumentation | PASS, 11/11 on `25098PN5AC` |
| P0C1.4 Compose lifecycle rerun | NOT COMPLETED: runner did not return; no assertion failure was reported |

P0C1.4 production sources, tile policy, renderer actors, persistence, Room, Pointer Epoch, tile
geometry and cache parameters were not modified. Its accepted baseline is preserved. The H1
instrumentation suite also retains a page-ahead contract and a tile-swap/transform-independence
contract, but the stalled lifecycle runner is not misreported as a fresh runtime pass.

## APK

```text
Path: dist/markleaf-debug-r7-h1-1-bounds-rubber-band.apk
Size: 74,798,462 bytes
SHA-256: E90C3ABA5A42BBF76D2A0F66B373B2C46CB5BAA1AFA4AE03C72733440940DE6D
```

This is the stable pre-correction H1.1 baseline artifact. The X-only/Y-hard-clamp correction was
locally assembled and tested but was not copied to `dist/`; it still requires corrected device
acceptance.

## Modified files

- `app/src/main/java/com/markleaf/app/pdf/HorizontalZoomPanPhysics.kt`
- `app/src/main/java/com/markleaf/app/ui/pdf/PdfViewerScreen.kt` (horizontal function only)
- `app/src/test/java/com/markleaf/app/pdf/R7H1HorizontalZoomPanPhysicsTest.kt`
- `app/src/androidTest/java/com/markleaf/app/ui/pdf/R7P0c11H1InstrumentedTest.kt`
- `.memory/active_object.yaml`
- `.memory/CURRENT_STATE.md`
- `docs/R7_H1_HORIZONTAL_ZOOM_PAN_PHYSICS_HANDOFF.md`
- `docs/R7_H1_1_HORIZONTAL_ZOOM_BOUNDS_AND_RUBBER_BAND_REPORT.md`

The APK is a generated, ignored delivery artifact and is not intended for Git.

## Device-test side effect

The first directed H1 connected run passed. During a second connected-test attempt, the Android
test harness removed the installed main package before its replacement install was rejected by the
device with `INSTALL_FAILED_USER_RESTRICTED`. This cleared the device-local app database. The same
PDF was re-imported, 335 outline nodes were re-imported, page 201 and horizontal 1x were restored.
No backup existed for other device-only state such as old print-page anchors, so that state may
remain reset. No persistence or Room source code was changed, and P0A database device tests remain
`pending_not_executed`.

## Stop condition

Stop at H1.1. Do not start H1.2, and do not mark H1.1 `device_accepted` until the user confirms
3x/4x behavior, 2-to-1-to-2 continuity, drag weight, spring feel and absence of visible jumps.
