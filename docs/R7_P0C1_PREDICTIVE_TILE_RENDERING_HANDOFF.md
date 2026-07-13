# R7 P0C1 Predictive Visible-Region Tile Rendering

## Status

- Active object: `R7_P0C1_PREDICTIVE_VISIBLE_REGION_TILE_RENDERING`
- P0B2 zoom-session restore: DEVICE ACCEPTED (`home_return_restore_pass`, `task_card_kill_restore_pass`)
- P0B1.1 pointer epoch: DEVICE PENDING
- P0C1 physical-device timing and visual acceptance: PENDING

## Production audit

`PdfPageRenderer` owns the single `Mutex` protecting `PdfRenderer`, page opening, full-page
rendering and the new tile rendering entry. Full-page job dedup remains in
`PdfViewerViewModel.cacheJobs/cacheJobKeys`; whole-page lifetime remains in
`PdfPageBitmapCache`. Tiles use a separate byte-budgeted LRU and job map.

`ContinuousDocumentViewport` measures each page in scale-1 document coordinates, then applies
the complete document transform in `placeWithLayer` with origin `(0,0)`. `DocumentPage` is
therefore the correct overlay boundary: one Canvas draws all ready tiles in page-local
coordinates and inherits exactly the base bitmap's transform without affecting measurement,
geometry, page tracking or zoom-session state. Rotation is currently always zero because the
existing renderer exposes unrotated pages; rotation remains part of `PdfTileKey` for invalidation.

## Architecture

The existing PREVIEW/HIGH bitmap remains the stable base and is never cleared by tile work.
`PdfTilePlanner` inverse-transforms the viewport, intersects only actually visible pages, and
creates a stable 512-output-pixel grid plus one margin ring. `PdfZoomBuckets` uses
1.0/1.4/2.0/2.8/4.0 buckets with hysteresis. `PdfZoomPredictor` uses an EMA velocity and 120 ms
look-ahead; pointer rebase produces no document-state change and therefore no predictor sample.

Requests are sorted focus, center, visible, margin, adjacent; deduplicated by stable key and
bounded to 32 active keys. The ViewModel launches ordered jobs while `PdfPageRenderer.mutex`
keeps all PdfRenderer access serial. Document generation and active-region checks reject stale
results. `PdfTileLru` defaults to 56 MB and pins current active keys.

`renderTile` maps the page-local rect to PDF page points and uses a `Matrix` directly with
`PdfRenderer.Page.render`. It creates only a roughly 512x512 output bitmap, includes a two-pixel
bleed, and never renders/crops a 3x/4x whole page. Tile ready publishes an immutable overlay
snapshot immediately, including during PINCHING; settle does not clear the cache.

## Logs and acceptance

Logs: `TILE_TARGET`, `TILE_REQUEST`, `TILE_READY`, `TILE_SWAP`, `TILE_DROP`, `TILE_CACHE`.
Real center latency, PINCHING coverage and 3x/4x sharpness require physical-device evidence and
must not be inferred from JVM/build results.

Deferred: compact window, vertical scrollbar/fast scroller, horizontal scrubber, UI polish,
horizontal Pager changes and R6 legacy changes.
