# R7 P0C1.2 Tile Geometry Corruption Fix

**Date:** 2026-07-13  
**Implementation:** complete  
**Physical-device acceptance:** PENDING  
**Default detail tile overlay:** OFF

## 1. Root cause

The device video proves the whole-page PREVIEW/HIGH base bitmap is geometrically correct and the
detail overlay corrupts it when tiles arrive.

The concrete code failure was stale page geometry combined with under-specified tile identity:

1. `ContinuousDocumentViewport` can initially create page geometry with the fallback `1.414`
   aspect while the whole-page bitmap is still rendering.
2. It could enqueue a tile with that provisional `pageBaseHeight`.
3. The whole-page renderer then publishes the PDF's real aspect and Compose creates the correct
   base geometry.
4. The old tile key contained document/page/bucket/grid and a bucket-scaled width, but no geometry
   generation or fitted-width version. The old tile therefore remained a cache hit or could finish
   in flight.
5. `PdfPageRenderer.renderTile` converted page-local Y to PDF Y using the old page height. The
   resulting content was then drawn at the new page-local destination, producing the observed
   split/reordered QR, NOTES, labels and decorations.

Two secondary correctness risks were fixed at the same boundary:

- Canvas inferred source crop as fixed `[2,2,bitmapWidth-2,bitmapHeight-2]` instead of carrying the
  renderer's actual content rectangle.
- Canvas rounded destination origin and size separately. For non-integer tile boundaries,
  `round(left)+round(width)` can differ from `round(right)`, opening or overlapping a 1 px seam.

## 2. Old source / matrix / destination math

Old renderer math:

```text
pointL = pageRect.left / oldPageBaseWidth * pdfWidth
pointT = pageRect.top  / oldPageBaseHeight * pdfHeight
pointW = pageRect.width  / oldPageBaseWidth * pdfWidth
pointH = pageRect.height / oldPageBaseHeight * pdfHeight

sx = (outputWidth  - 2*fixedBleed) / pointW
sy = (outputHeight - 2*fixedBleed) / pointH
tx = fixedBleed - pointL*sx
ty = fixedBleed - pointT*sy
```

Old Canvas math:

```text
src = [2, 2, bitmapWidth-2, bitmapHeight-2]
dstOrigin = [round(pageRect.left), round(pageRect.top)]
dstSize = [round(pageRect.width), round(pageRect.height)]
```

The matrix itself can map one snapshot consistently, but `oldPageBaseHeight` was not part of the
cache identity and could disagree with the displayed base. The Canvas path also discarded the
request's actual content dimensions.

## 3. Corrected coordinate contract

`PdfTileEntry.pageRect` and `PdfTileRequest.pageRect` are now `PageLayoutRect` and always mean:

```text
UNSCALED_PAGE_LOCAL_COORDINATES
```

- origin: fitted current PDF page top-left;
- units: scale-1 page layout units;
- excludes viewport offset, document scroll, Compose scale, zoom bucket pixels, bitmap pixels and
  bleed.

Separate types prevent cross-space mixing:

- `PageLayoutRect`
- `DocumentViewportRect`
- `PdfPageRect`
- `TilePixelRect`
- `PageLayoutSize`
- `PdfPageSize`

Correct render math:

```text
renderScale = targetTilePixelsPerPageLocalUnit
contentWidthPx  = round(pageRect.width  * renderScale)
contentHeightPx = round(pageRect.height * renderScale)

bitmapWidth  = bleedLeft + contentWidthPx  + bleedRight
bitmapHeight = bleedTop  + contentHeightPx + bleedBottom

scaleX = renderScale * fittedPageWidth  / pdfPageWidth
scaleY = renderScale * fittedPageHeight / pdfPageHeight
translateX = bleedLeft - pageRect.left * renderScale
translateY = bleedTop  - pageRect.top  * renderScale
```

The Android `Matrix` is written directly as nine values, avoiding pre/post-order ambiguity:

```text
[ scaleX, 0, translateX,
  0, scaleY, translateY,
  0, 0, 1 ]
```

Therefore:

```text
bitmapX = (pageLocalX - pageRect.left) * renderScale + bleedLeft
bitmapY = (pageLocalY - pageRect.top)  * renderScale + bleedTop
```

## 4. Bleed crop and Canvas destination

Every request carries explicit `TilePixelInsets`, logical content size and derived
`contentRectPx`:

```text
src = [bleedLeft,
       bleedTop,
       bleedLeft + contentWidthPx,
       bleedTop + contentHeightPx]
```

The complete bitmap is never stretched into `pageRect`. Edge tiles use the clamped logical
`pageRect`, so content size and destination are both based on the same actual edge extent.

Canvas destination edges are rounded independently:

```text
dst = [round(pageRect.left), round(pageRect.top),
       round(pageRect.right), round(pageRect.bottom)]
```

Adjacent tiles therefore share an identical rounded boundary.

## 5. Geometry generation filtering

`PdfTileKey` now contains:

- `documentId`
- `documentGeneration`
- `geometryGeneration`
- `pageIndex`
- `rotation`
- `fittedPageWidthVersion`
- `zoomBucket`
- `tileX`, `tileY`

`geometryGeneration` is a deterministic fingerprint of fitted page width, fitted page height and
rotation. A changed real aspect cannot share the provisional key.

The queue rejects an old in-flight completion before publish and logs:

```text
TILE_DROP(STALE_GEOMETRY)
```

`ContinuousDocumentViewport` independently filters document generation, geometry generation and
fitted-width version before Canvas. Old cached geometry cannot display even if it remains in the
byte LRU.

## 6. Feature flag and debug capture controls

Default builds use:

```text
DETAIL_TILE_OVERLAY_ENABLED=false
```

With the flag off, the viewport submits an empty target set and draws zero detail tiles. The
whole-page PREVIEW/HIGH path is unchanged.

Debug-only capture builds can use Gradle properties:

```text
-Pmarkleaf.detailTileOverlayEnabled=true
-Pmarkleaf.detailTileBaseLayerEnabled=false   # tile only
-Pmarkleaf.detailTileOverlayAlpha=0.5         # 50% combined overlay
-Pmarkleaf.detailTileDiagnosticsEnabled=true  # border + key/rect/content/bleed label
```

Release builds never draw diagnostic borders or labels because diagnostics are additionally gated
by the app debuggable flag.

## 7. Pixel composition evidence

JVM pure-logic composition test:

- portrait, landscape and rotated identity;
- all 1.0/1.4/2.0/2.8/4.0 buckets;
- 1 px and 2 px bleed;
- right/bottom edges, non-zero tile X/Y and non-integer fitted scaling;
- checkerboard, coordinate rules, QR-like blocks, cross-boundary text-like bars and colors.

Result: **30/30 combinations, 0 differing pixels and no uncovered sentinel pixels**.

Android instrumented test `R7P0c12TilePixelCompositionInstrumentedTest` creates a real two-page
portrait/landscape PDF at runtime, renders a whole-page reference through the existing renderer,
renders every tile through `PdfPageRenderer.renderTile`, crops/stitches with the production rules,
and applies bounded antialias-difference assertions.

Status: compiled and packaged, **runtime NOT RUN** because ADB has no attached device and this
workstation has no installed emulator executable.

## 8. Verification

```text
:app:testDebugUnitTest
PASS — 485 tests, 0 failures, 0 errors, 0 skipped

:app:lintDebug
PASS

:app:assembleDebug
PASS

:app:assembleDebugAndroidTest
PASS

:app:connectedDebugAndroidTest
NOT RUN — no ADB device; task remained in device wait and was terminated
```

## 9. APK

```text
Path: dist/markleaf-debug-r7-p0c1-2-tile-geometry-safe.apk
Size: 74,761,374 bytes
SHA-256: BA785800507407D5968F0B8267C231BC0865BED428FB82320210A9C17A86B3BC
Default detail tile overlay: OFF
```

## 10. Device acceptance

The required medical textbook page-30 QR comparison could not be captured without a connected
device and source document. No before/after screenshot is claimed.

Required next gate:

1. build a debug APK with tiles explicitly enabled;
2. capture base-only, tile-only and combined/50% views;
3. compare QR finder blocks, “本章思维导图” baseline, NOTES, pencil, page 30 and blue dashed rule;
4. repeat 1x→4x, home re-entry, task-card kill re-entry, next page and return;
5. run the real PdfRenderer pixel-composition instrumented test.

Current status:

```text
P0C1 = DEVICE PENDING
P0C1.1 = BLOCKED_BY_P0C1
P0B2 persistence = DEVICE ACCEPTED
```

The default overlay must remain OFF until both instrumented pixel diff and page-30 physical-device
visual acceptance pass.
