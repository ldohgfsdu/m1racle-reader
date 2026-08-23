# R7 H1.1A top-align and pinch X-bound rebase report

Date: 2026-07-13  
Baseline HEAD: `4241b3b4a0a9af6d99e6f83a96092d19b6e2065e`  
Status: `implementation_complete_device_acceptance_pending`

## Scope lock

Only the horizontal-paged layout geometry, its live-pinch legal-coordinate rebase, and directed
H1 tests changed. H1.2 edge Pager handoff was not started. P0C1.4 production code, Room,
persistence, Pointer Epoch, tile geometry, cache/quality policy, vertical fast scroller, compact
window and horizontal scrubber were not changed. The 28dp X rubber-band, Compose spring,
`1.01f` Pager threshold and zoom limits were not tuned.

## 1. Top gray block root cause and viewport layout

The production call chain is:

```text
PdfViewerScreen Ready
  -> PdfChrome Column(statusBarsPadding)
    -> TopBar Surface (56dp row plus optional print/outline text)
    -> Box(weight=1, clipToBounds, pdfCanvas background)       [reading viewport]
      -> PdfHorizontalPagedArea(fillMaxSize)
        -> HorizontalPager(fillMaxSize)
          -> page Box(fillMaxSize)
            -> fitted page Surface
        -> bottom page/arrow controls aligned as an overlay
```

The reading viewport height is therefore the app height after the status-bar inset and the actual
TopBar intrinsic height. Horizontal mode passes `showPageButtons=false` and `scrubber=null` to
`PdfChrome`, so no fixed bottom chrome consumes viewport height. The horizontal page number and
left/right buttons are drawn by `PdfHorizontalPagedArea` as a bottom overlay; they do not compress
the Pager. The page label inside the TopBar shares the fixed 56dp row and does not create a second
row. Optional print/outline labels can increase TopBar height, but they were not the gray block.

The large gray block came from the page-level Box using `contentAlignment=Alignment.Center`.
Portrait content was width-fitted to a height much smaller than the viewport, so the exposed
`MarkLeafTheme.extra.pdfCanvas` background was split above and below the centered page. There was
no extra Spacer, verticalArrangement, safe-drawing inset, scrubber reservation or placeholder
above the page.

The fix uses `Alignment.TopCenter`, an explicit 8dp content gap, and a page graphics transform
origin of top-center (`TransformOrigin(0.5f, 0f)`). The fitted layout now carries `contentTopPx`;
Y bounds are top-aligned (`maxY=0`, `minY=availableHeight-scaledHeight` when negative), so zoomed
vertical pan still reaches the real top and bottom without exposing blank.

Physical-device evidence on `25098PN5AC / db04f865`:

- viewport: `1220 x 2133px`, screen viewport top `y=523`;
- fitted page: `1168 x 1610.8245px`;
- page top: `y=549`, a `26px / 3.25 = 8dp` gap below the viewport top;
- the former centered top gap would be `(2133 - 1610.8245) / 2 = 261.0878px`, about `80.3dp`;
- bottom controls remained clickable as an overlay and did not change the logged viewport height.

Screenshot evidence (excluded from Git):
`dist/performance/r7-h1-1a-topalign-device-20260713.png`.

## 2. Live-pinch X jump root cause and corrected call chain

Before H1.1A, a pointer-set frame captured:

```text
baseOffset = displayOffset(horizontalPan)
displayOffsetX = legalOffsetX + visualOverscrollX
focal = (centroid - viewportCenter - baseOffset) / baseScale
desiredDisplay = centroid - viewportCenter - focal * newScale
fromDisplayOffset(desiredDisplay, newBounds)
```

Thus display-only X edge feedback participated in the next scale frame. `fromDisplayOffset` then
clamped/decomposed that display value against new bounds, allowing old visual blank to influence
the new legal/visual state. This is the code-backed cause of the abrupt left-blank expansion.

The fitted rect remains an unscaled, stable viewport/aspect fit during live pinch. It is evaluated
against current viewport/aspect, while `panBounds(page, newScale)` recomputes scaled width/height
and legal bounds on every pinch frame. H1.1A now uses this chain:

```text
pointer-set rebase
  -> baseScale + settled base legalOffsetX/Y (visual/raw excluded)
  -> stable page-content anchor relative to top-center transform origin
each pinch frame
  -> newScale
  -> new X/Y bounds
  -> desiredLegal = pointer - origin - contentAnchor * newScale
  -> clamp desiredLegal to new bounds
  -> HorizontalPanState(legalOffsetX, legalOffsetY, raw=0, visual=0)
```

`fromDisplayOffset` was removed from this path. `rebaseLegalForPinch` never reads
`displayOffsetX`, `rawOverscrollX` or `visualOverscrollX`, and its return state contains no edge
feedback. The same content point therefore remains under the fingers unless a real content bound
makes that geometrically impossible.

## 3. Legal-range 1:1 drag

The pan path was audited rather than retuned:

```text
change.position - change.previousPosition
  -> HorizontalZoomPanPhysics.pan
    -> legal candidate and clamp
    -> legalConsumed
    -> residual only
    -> rubberBand(residual)
```

With no edge pull, the legal component is not damped. The second pointerInput observes taps but
does not integrate pan, so the delta is not applied twice. Above 1.01x `owner=INNER_PAN` and Pager
is disabled. The perceived sticky state was reproducibly attributable to display overscroll being
created/carried by the old pinch rebase; removing that contamination restores the existing 1:1
legal path without changing physical parameters.

At 2x on device, sampled frames showed exact equality, for example:

```text
rawDelta=(10.799805,0) legalConsumed=(10.799805,0) residual=(0,0)
rawDelta=(8.081055,0)  legalConsumed=(8.081055,0)  residual=(0,0)
rawDelta=(0,7.4003906) legalConsumed=(0,7.4003906) residual=(0,0)
```

At the right edge, legal X remained `558px`; only residual entered the rubber band. Maximum
sampled `visualOverscrollX=27.94859dp`; the X-only spring returned it to zero in `179ms` without
changing pageIndex 291.

Raw log (excluded from Git):
`dist/performance/r7-h1-1a-device-evidence-20260713.log`.

## 4. Tests and builds

| Gate | Result |
|---|---|
| Full JVM | PASS, 532/532; 0 failed/skipped |
| H1 JVM | PASS, 37/37 |
| P0C1.4 JVM regression | PASS, 23/23 |
| lintDebug | PASS |
| assembleDebug | PASS |
| assembleQa | PASS |
| compileQaAndroidTestKotlin | PASS |
| QA H1 instrumentation runtime | NOT RUN: device rejected QA APK install with `INSTALL_FAILED_USER_RESTRICTED`; main package was never targeted |

New coverage includes top alignment/no-large-top-gap, visual-X exclusion from pinch legal state,
left-blank bound protection, legal 1:1/residual-only resistance, and content-anchor continuity
through deterministic 2x/3x/4x bounds. Existing P0C1.4 tests stayed green.

## 5. Artifact and installation

```text
Path: dist/markleaf-debug-r7-h1-1a-topalign-xbound.apk
Bytes: 74,798,278
Modified: 2026-07-13 14:19:37 +08:00
SHA-256: FA4D06255DFEB71A3C08C1D555D2624236DFB5EE847F49F9B16ADCB1CC5C93D9
```

The main package was backed up and verified before installation. It was updated only with
`adb install -r`. Before/after backups each contained five files in an 812,032-byte archive;
all payload hashes and the sanitized reading-state tuple were identical.

## 6. Device acceptance state

- Top gray block: **objectively removed**; only the intended 8dp gap remains.
- Page position: **objectively moved to the top** of the reading viewport.
- Legal-range drag: **objective 2x logs show 1:1 consumption**; subjective feel pending.
- Left-blank jump during real two-finger live pinch: **PENDING user confirmation**.
- 2x/3x pinch continuity and subjective `跟手 / 太黏 / 太滑`: **PENDING user confirmation**.
- P0C1.4: production code untouched and 23/23 JVM regression PASS; new manual hold-drag visual
  confirmation is **PENDING**. Its existing `device_accepted` status is not revoked.

Overall H1.1A device status remains **PENDING** until the user performs real two-finger 2x/3x
pinch and hold-drag checks. H1.2 has not started.
