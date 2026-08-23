# R7 H1.1B shared zoom-pan physics report

**Object:** `R7_H1_1B_UNIFY_ZOOM_PAN_PHYSICS_WITH_CONTINUOUS_MODE`  
**Date:** 2026-07-13  
**Status:** implementation complete; objective device checks pass; real two-finger/subjective A/B acceptance pending  
**Baseline:** `4241b3b4a0a9af6d99e6f83a96092d19b6e2065e`

## 1. Continuous production audit

The production entry remains:

```text
PdfViewerScreen.kt / PdfViewerScreen
  -> ContinuousDocumentViewport.kt / ContinuousDocumentViewport
     -> ContinuousInteractionTransaction.kt / ContinuousPointerEpochReducer.frame
        -> ZoomPanPhysics.kt / ZoomPanPointerEpochReducer.frame
           -> ZoomPanPhysics.applyPan or applyScaleFromAnchor
     -> ContinuousDocumentGeometry.kt / zoomPanBounds, panBy, clamp
        -> ZoomPanPhysics.applyPan or clampToBounds
```

The actual production ownership/call chain is:

1. Scale owner: `ContinuousDocumentViewport.documentState`, whose value is a
   `ContinuousDocumentState.scale`. Stable values are handed to
   `onDocumentStateSettled`/`onViewportSessionSettled`; no page owns scale.
2. Offset owner: the same document-wide `ContinuousDocumentState.scrollX/scrollY`. The continuous
   adapter maps them to shared content translation as `legalOffset=(-scrollX,-scrollY)`.
3. Single-finger pan: `ContinuousDocumentViewport` reads pointer delta, then
   `ContinuousDocumentGeometry.panBy` calls `ZoomPanPhysics.applyPan`.
4. Legal bounds: `ContinuousDocumentGeometry.zoomPanBounds` derives them from
   `maxScrollX/maxScrollY` and viewport size. At 1x X remains fit-width locked at zero.
5. Boundary residual: shared `applyPan` computes raw -> legal consumed -> residual. The continuous
   production policy disables display overscroll, so residual is discarded after a hard clamp.
6. Rubber band: continuous production has none. The shared core supports a monotonic saturating
   mapper for explicit future/product policies, but `ContinuousOverscrollPolicy` disables both axes.
7. Release: one-finger release calls `ContinuousDocumentViewport.startFling`; it uses the shared
   `ZoomPanPhysics.flingStep`. The `450ms` `ContinuousInteractionReducer` settle is a bitmap/
   interaction commit, not transform spring-back. There is no continuous transform spring.
8. Pointer-set change: `ContinuousPointerEpochReducer.frame` is now a compatibility adapter over
   `ZoomPanPointerEpochReducer.frame`. 1->2, 2->1, third-pointer and pair replacement frames rebase
   only and do not transform.
9. New pointer down: continuous calls `cancelFling`; a new pinch also cancels the pending settle job.
   Horizontal calls `cancelHorizontalMotion` before creating a new shared pointer epoch.
10. Tile swap: tile/display maps remain separate from `documentState`; tile arrival cannot write
    scale or legal offset. No tile planner, queue, cache, geometry key, or swap policy was changed.
11. Device evidence already authoritative before H1.1B: P0C1.4 proved rendering continues while
    the pointer is down and remains `device_accepted`. Pointer Epoch itself remains device-pending;
    H1.1B does not upgrade that status. Its JVM characterization remains green.
12. Continuous-only and not shared: `ContinuousDocumentGeometry` page stack, composed/visible page
    sets, document page tracking, entry/restore state machine, viewport-session persistence,
    vertical viewport ownership, page-ahead rendering and tile planning.

## 2. Extracted shared pure Kotlin core

New file: `app/src/main/java/com/markleaf/app/pdf/ZoomPanPhysics.kt`.

Shared symbols:

- `ZoomPanBounds`, `ZoomPanTransform`, `ZoomPanPoint`
- `AxisOverscrollPolicy`, `ZoomPanRange`, `ZoomPanFocalAnchor`
- `ZoomPanPhysics.applyPan`, `captureFocalAnchor`, `applyScaleFromAnchor`,
  `applyScaleAtFocal`, `clampToBounds`, `springTarget`, `rubberBand`
- `ZoomPanPhysics.flingStep`, `shouldStartFling`, `shouldContinueFling`
- `ZoomPanPointerEpochReducer.frame`

The core is pure Kotlin. It has no Compose state, Pager, viewport composable, coroutine, render,
navigation, Room or persistence dependency. Every coordinate is a viewport pixel.

The extracted continuous production parameters are shared rather than copied:

| Parameter | Shared value |
|---|---:|
| Scale range | 1x..4x |
| Legal pan | 1:1 |
| Fling start threshold | 30 px/s |
| Fling stop threshold | 8 px/s |
| Exponential decay | 5.2/s |
| Max integrated frame | 48 ms |
| Velocity clamp | none |
| Continuous transform resistance | none |
| Continuous transform overscroll limit | 0 (disabled policy) |
| Continuous transform spring | none |

## 3. Mode adapter boundary

Continuous adapter:

- `ContinuousDocumentGeometry` alone provides document dimensions/bounds and maps scroll sign.
- `ContinuousDocumentViewport` keeps vertical document/page/render ownership.
- Existing P0C1.4 planner, tile worker and bitmap swap lifecycle remain independent.

Horizontal adapter:

- `HorizontalZoomPanPhysics.fitPage/bounds` alone provide real fitted page layout and top-aligned
  page bounds.
- `PdfHorizontalPagedArea` supplies viewport, fitted rect, transform origin and pointer samples.
- Shared `ZoomPanPointerEpochReducer` owns stable pairs/rebase/pinch; shared `ZoomPanPhysics` owns
  legal pan and fling integration.
- `HorizontalPager` remains outside the core: Pager owns at `scale <= 1.01`; the page owns and
  consumes at `scale > 1.01`. A second pointer stops an in-progress Pager scroll.
- Drawing remains `translation=displayOffset` with no second scale multiplication.

## 4. Removed duplicate H1 production physics

Removed from horizontal production behavior:

- horizontal-private `panAxis` and inverse-rubber-band implementations;
- the manual active-id/pair/base-span pointer epoch implementation in `PdfHorizontalPagedArea`;
- the H1-only `28dp` edge-pull constant;
- the H1-only `Animatable + Spring.StiffnessMedium + DampingRatioNoBouncy` rebound;
- separate per-frame pinch focal math and the horizontal-only absence of fling.

`HorizontalZoomPanPhysics` now contains layout/Pager adaptation and compatibility state mapping;
all transform math delegates to the shared core. Both production modes use the same scale/pan/
pointer/fling parameters. Both currently use the continuous hard-clamp axis policy, so horizontal
no longer exposes a separate subjective H1 resistance/rebound tuning set.

## 5. Top alignment and transform invariants

The H1.1A layout fix is unchanged:

- page container: `Alignment.TopCenter`;
- layout gap: `8dp`;
- graphics transform origin: `TransformOrigin(0.5f, 0f)`;
- translation uses viewport pixels and is not multiplied by scale;
- focal capture uses legal offset only and never folds visual overscroll into legal offset.

On device at 1x, viewport top was `y=523` and page top was `y=549`: `26px`, exactly `8dp` on this
device. The former large centered gray block did not return.

## 6. Continuous zero-behavior-change evidence

Characterization was added and passed before extraction, then rerun after extraction:

- `R7H11bContinuousPhysicsCharacterizationTest`: 5/5
- `ContinuousDocumentGeometryTest`: pass
- `R7P0b11PointerEpochTest`: 6/6
- `R7P0c14GestureRenderDecouplingTest`: 23/23

The full JVM suite is 557/557 with zero failure/skip. The H1.1B shared tests add:

- `ZoomPanPhysicsTest`: 15/15
- `R7H11bHorizontalSharedPhysicsTest`: 5/5
- existing `R7H1HorizontalZoomPanPhysicsTest`: 37/37

No P0C1.4 planner/renderer/tile policy, page tracking, restore, Room or persistence code was changed.

## 7. Build and test gates

```text
:app:testDebugUnitTest              PASS (557/557, 0 failed, 0 skipped)
:app:lintDebug                      PASS
:app:assembleDebug                  PASS
:app:assembleQa                     PASS
:app:compileQaAndroidTestKotlin     PASS
:app:assembleQaAndroidTest          PASS
```

QA runtime details must not be flattened into a false all-pass result:

- The guarded `connectedQaAndroidTest` resolved `applicationId=com.markleaf.app.qa` and ran the
  first 12 tests successfully.
- Existing `R7P0c12TilePixelCompositionInstrumentedTest` failed at test 13 with
  `meanDelta=2.047792...`; this is outside H1.1B and was not modified.
- The next configuration-recreation test hung as on previous HyperOS/UTP runs; the run was stopped.
- Direct H1 execution against the already installed QA test APK ran 14 tests: 13 passed. Its only
  failure used `+32px` from the top-aligned Y max and incorrectly expected it to be legal. Source
  was corrected to `-32px`; `assembleQaAndroidTest` passed. Device installation of the corrected
  test APK was then rejected with `INSTALL_FAILED_USER_RESTRICTED`, so no corrected 14/14 runtime
  claim is made.

## 8. Device objective evidence

Device: `25098PN5AC / db04f865`, viewport `1220x2133px`, page `1168x1610.8245px`.

At 2x horizontal:

- bounds: X `[-558, +558]`, Y `[-1114.6489, 0]`;
- legal in-range X/Y log frames had `rawDelta == legalConsumed`, residual zero;
- shared pointer epoch logged rebase-only first frames;
- Pager was disabled and page owner was `INNER_PAN`;
- edge frames reached legal X `558`, then all outward delta became residual while visual
  overscroll remained `(0,0)`;
- shared fling ended at about 7.7-7.9 px/s after about 0.49-0.91s;
- displayed page remained 299 (zero-based pageIndex 298) through zoomed pan/edge pulls;
- after double-tap back to 1x, Pager moved 299 -> 300 and back to 299 normally.

Main-package safety:

- final-artifact pre-install backup: 5 files, 812,032 bytes,
  SHA-256 `16F78635DBF5948B788BD432B24B21B4726983153557501E25012B5E3A0BB416`;
- `adb install -r` succeeded;
- immediate post-install backup had the identical file count, byte count and archive SHA-256.

## 9. Artifact

```text
Path: dist/markleaf-debug-r7-h1-1b-shared-zoom-pan.apk
Bytes: 74,799,810
SHA-256: B5073A99024BB916BF1517ED2BD329D1FE2FADF1DB3FFF39F62EBBC94B5A0EB7
```

## 10. Acceptance and stop point

Objective implementation/device checks pass. Real 2.5x two-finger pinch, 1->2->1 pointer feel,
continuous-vs-horizontal same-page subjective drag/fling comparison and left-blank visual
continuity still require the user. Device acceptance therefore remains pending.

H1.2 was not started. Room/persistence, P0C1.4 rendering policy, PDFium, page-ahead/tile tuning and
other deferred objects were not expanded. No commit and no push were performed.
