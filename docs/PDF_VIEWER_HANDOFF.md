# PDF_VIEWER_HANDOFF

**Task id:** PDF_VIEWER_HANDOFF_V1  
**Active object:** `PDF_VIEWER_INTERACTION_STABILITY` (see `.memory/active_object.yaml`)  
**Date:** 2026-07-11  
**Constraint for previous session:** stop feature work; this file is the executable handoff for the next Agent.

**Do not** modify Markdown, ZIP package pipeline, tables, OCR, knowledge points, or migrate to PDFium unless a later task explicitly expands scope.

## P0-A implementation verification (2026-07-11)

R4 continuous zoom/pan implementation and build complete. Device acceptance remains **PENDING**.

- R4 APK: `/storage/emulated/0/outbox/MarkLeaf/markleaf-debug-r4.apk`
- R4 SHA-256: `3a677a0fb8031939191c2e1b2f6efd96e3d54845fe3963dc361557fc980a9196`

R3 implementation/build complete. Device acceptance remains **PENDING**.

- R3 APK: `/storage/emulated/0/outbox/MarkLeaf/markleaf-debug-r3.apk`
- R3 SHA-256: `4f598a5f3bad6ff60430f67571f94661a37f0196e2abadcbe706b9b8a40f1da1`

R2 implementation/build complete. Device acceptance remains **PENDING**.

- R2 APK: `/storage/emulated/0/outbox/MarkLeaf/markleaf-debug-r2.apk`
- R2 SHA-256: `45d4d4dcb4b9423be2bd5084260088656b1c6f083c45ba01ae6bf1625efd0d82`

Continuous-mode cache protection and committed zoom ownership were implemented. Verified:

- PDF test group and `:app:testDebugUnitTest`: PASS
- `:app:lintDebug`: PASS
- `:app:assembleDebug`: PASS
- APK: `/storage/emulated/0/outbox/MarkLeaf/markleaf-debug.apk`
- SHA-256: `2c2a1a123a4123bd3fb373c25dba32d204172428a587a25e7b4ac782039cc10e`

Device acceptance remains **PENDING**. Do not treat these build results as proof of flicker, pinch, or scroll behavior.

---

## A. Current architecture (real paths / symbols)

### A.1 UI entry

| Symbol | Path | Role |
|---|---|---|
| `PdfViewerScreen` | `app/src/main/java/com/markleaf/app/ui/pdf/PdfViewerScreen.kt` | Top-level PDF reader composable: mode switch, dialogs, outline/export/thumbs, wires continuous vs horizontal. |
| `PdfChrome` | same file, `private fun PdfChrome` | Scaffold: optional TopBar (`topBarVisible`), clipped content weight box, optional bottom scrubber/buttons. |
| `PdfVerticalContinuousArea` | same file | Continuous LazyColumn + pinch/pan + Fast Scroller. |
| `PdfHorizontalPagedArea` | same file | HorizontalPager + per-page zoom/pan + chrome overlay. |
| `PdfFastScroller` | same file | Right-edge scrubber (preview-only during drag). |
| `PdfReadingModeSheet` | same file | Mode + show-page-buttons settings. |
| `PdfPageScrubber` | same file | Horizontal compact Slider (not used as continuous primary jump). |

Nav: `app/src/main/java/com/markleaf/app/ui/navigation/NavGraph.kt` → PDF route + `PdfViewerViewModel.Factory`.

### A.2 ViewModel / state

| Symbol | Path | Role |
|---|---|---|
| `PdfViewerUiState` | `app/src/main/java/com/markleaf/app/ui/pdf/PdfViewerViewModel.kt` | `Loading` / `Ready` / `Error`. |
| `PdfViewerUiState.Ready` | same | `pageIndex`, `pageBitmaps`, `pageAspects`, `zoomScale`, `qualityTier`, `printAnchors`, `printSections`, `readingMode`, `pageJump` consumers, print resolve helpers. |
| `PdfViewerViewModel` | same | Open document, mode, jump, outline/export, continuous render policy, zoom settle, fast scroll. |
| `PageJumpRequest` | same (`PdfViewerViewModel.PageJumpRequest`) | One-shot programmatic continuous scroll (`emitPageJump`); must not be driven by settled page alone. |
| `onViewportWidth` | same | Stores `viewportWidthPx`; continuous → `ensurePreviewAround` + `schedulePrimaryHiRes`. |
| `onZoomScaleSettled` | same | After pinch/double-tap; continuous preview + debounced primary hi-res. |
| `setZoomGestureActive` | same | Pinch active: cancel upgrades / in-flight jobs; keep cache. |
| `onVerticalPageSettled` | same | Update pageIndex; **preview only** + `schedulePrimaryHiRes`. |
| `ensurePagesAround` | same | Public alias → `ensurePreviewAround` only. |
| `ensurePreviewAround` | same, private | Keep primary±1 PREVIEW; demote non-primary HIGH. |
| `schedulePrimaryHiRes` | same, private | 200ms idle; only if `continuousNeedsHiRes(zoomScale)`. |
| `requestPageRender` | same, private | Generation-guarded render; atomic cache put; never null bitmap on start. |
| `onFastScrollPreview` / `onFastScrollFinished` | same | Throttled jump; preview only while dragging. |
| `pageCache: PdfPageBitmapCache` | same field | In-memory continuous bitmaps. |

**Note:** There is **no** type named `PageRenderModeSwitch`. Mode is `PdfReadingMode` + `PdfViewerViewModel.setReadingMode` + UI `PdfReadingModeSheet`.

### A.3 Renderer / quality / cache

| Symbol | Path | Role |
|---|---|---|
| `PdfPageRenderer` | `app/src/main/java/com/markleaf/app/pdf/PdfPageRenderer.kt` | Android `PdfRenderer`; `open`, `renderPage(targetWidthPx)`, `pageSizePts`. Mutex serializes page open. |
| `QualityTier` | `app/src/main/java/com/markleaf/app/pdf/PdfRenderQuality.kt` | `PREVIEW` / `MEDIUM` / `HIGH`. |
| `PageRenderRole` | same | `PRIMARY` / `VISIBLE_OTHER` / `PRELOAD`. |
| `PdfRenderQuality` | same | Width caps, hysteresis, `keepRange`, `continuousNeedsHiRes`, `continuousPrimaryUpgradeTier`, debounce constants. |
| `PageBitmapEntry` | `app/src/main/java/com/markleaf/app/pdf/PdfPageBitmapCache.kt` | image + widthBucket + tier + generation + aspectHw. |
| `PdfPageBitmapCache` | same | put/putIfCurrent/retainOnly/highPages; max 1 HIGH. |
| `PdfThumbnailLoader` | `app/src/main/java/com/markleaf/app/pdf/PdfThumbnailLoader.kt` | Separate small-renderer LRU for thumb sheet. |

**Width policy (continuous, current code):**

| Tier | Formula | Cap |
|---|---|---|
| PREVIEW | `viewportWidthPx * 1.25` | 1600 |
| MEDIUM | `viewportWidthPx * 2.0` | 2304 |
| HIGH | `viewportWidthPx * 2.8` | 3072 |

`PdfRenderQuality.MAX_ZOOM = 2.5f` (product later asked for 3× — code still 2.5).  
`DOUBLE_TAP_ZOOM = 2.0f`.

### A.4 Print / outline / mode prefs

| Symbol | Path | Role |
|---|---|---|
| `PrintPageSection` / `PrintPageSectionCatalog` | `app/src/main/java/com/markleaf/app/pdf/PrintPageSection.kt` | Segment model; 内科学 front 0..30 + body 31+. |
| `PrintPageResolver` | `app/src/main/java/com/markleaf/app/pdf/PrintPageResolver.kt` | Section-scoped resolve; `resolveDetailed`. |
| `PdfPrintPageAnchorEntity` / Dao | `app/src/main/java/com/markleaf/app/data/local/` | Sparse anchors. |
| `TextbookOutlineResolver` | `app/src/main/java/com/markleaf/app/pdf/TextbookOutlineResolver.kt` | Deepest node / path label. |
| `PdfEmbeddedOutlineParser` / `Importer` / `EmbeddedOutlineBuilder` | `app/src/main/java/com/markleaf/app/pdf/` | PDF bookmarks → outline nodes. |
| `PdfReadingMode` | `app/src/main/java/com/markleaf/app/pdf/PdfReadingMode.kt` | `VERTICAL_CONTINUOUS`, `HORIZONTAL_PAGED`. |
| `PdfReadingNavigation` | same | clamp / mode-switch page keep. |
| `PdfReadingPrefs` | `app/src/main/java/com/markleaf/app/pdf/PdfReadingPrefs.kt` | Per-document mode + showPageButtons SharedPreferences. |

### A.5 Continuous gesture state (UI-local)

In `PdfVerticalContinuousArea`:

- `committedZoom`, `gestureZoom`, `panX`, `zoomGestureActive` — `remember { }` (not keyed by page)
- `displayZoom = clamp(committedZoom * gestureZoom)`
- Pinch: `awaitEachGesture` + `calculateZoom` / `calculateCentroid`; GPU `graphicsLayer { scaleX/Y = gestureZoom }` while active
- Layout width uses `layoutZoom` (committed during gesture)
- Double-tap toggles 1× / 2× then `onZoomScaleSettled`
- End pinch: commit zoom → `restoreZoomAnchor()` → `onZoomScaleSettled`
- Item key: `"$docId|$page"`

### A.6 Horizontal gesture state (UI-local)

In `PdfHorizontalPagedArea`:

- `HorizontalPager` with `userScrollEnabled = scale <= 1.01f`
- `var scale by remember(state.pageIndex)` — **resets to 1f when pageIndex changes**
- `var offset by remember(state.pageIndex)`
- `detectTransformGestures` on page for pinch/pan
- `detectTapGestures`: double-tap zoom; single tap edges/center for page/chrome
- `LaunchedEffect(scale) { delay(200); onZoomScaleSettled(scale) }` — fires on every settled scale including init
- Bottom semi-transparent overlay when `chromeVisible`
- Parent: `chromeVisible` auto-false after `CHROME_AUTO_HIDE_MS` (2000) when horizontal

---

## B. Verified functionality

### B.1 Compile / unit (last known PASS)

- `./gradlew :app:testDebugUnitTest` — PASS  
- `./gradlew :app:lintDebug` — PASS  
- `./gradlew :app:assembleDebug` — PASS  

Notable unit tests under `app/src/test/java/com/markleaf/app/pdf/`:

- `PrintPageResolverTest` — sections 33→2, 330→299, 633→602; no front leak  
- `PdfRenderQualityTest` — buckets / hysteresis / caps  
- `ContinuousLazyPolicyTest` — scroll at 1× no hi-res need  
- `PdfReadingModeTest`, outline/export/thumbnail policy tests  

### B.2 Feature checklist

| Feature | Unit / build | Device |
|---|---|---|
| PDF open | PASS | Previously worked |
| Embedded outline import | PASS | Previously worked |
| Textbook outline jump | PASS | Previously worked |
| Print sections mapping | PASS (locked tests) | Needs device chrome check |
| Mode switch keeps page | PASS (navigation helper) | Partial |
| Markdown ZIP + relative images | Prior work | Prior work |
| Table horizontal scroll | Prior work | Prior work |
| External VIEW/SEND | Prior work | Prior work |
| Continuous smooth scroll + zoom | Policy coded | **FAIL (BUG-1, BUG-2)** |
| Horizontal natural swipe | Pager present | **FAIL / poor (BUG-3)** |
| Horizontal chrome UX | Auto-hide coded | **FAIL (BUG-4)** |

### B.3 APK

`/storage/emulated/0/outbox/MarkLeaf/markleaf-debug.apk`  
Built from uncommitted tree; not a clean git tag.

---

## C. Device defects & repro

### BUG-1 Continuous small-scroll flicker / white / reload

**Repro**

1. Open 内科学 PDF  
2. Switch to vertical continuous  
3. Slow-scroll less than half a page  
4. Observe white flash, reload, flicker, or bitmap thrash  

**Expected**

- Always keep preview while scrolling  
- No white / Loading over existing bitmap  
- No repeated hi-res for every tiny pageIndex change  

### BUG-2 Pinch weak / reset on release

**Repro**

1. Continuous mode on any page  
2. Two-finger pinch  
3. Zoom may be subtle; double-tap may enlarge  
4. On release, returns to 1× or layout reflows oddly  

**Expected**

- Continuous pinch 1×→3× (code max currently **2.5**)  
- Keep final scale + pan after release  
- Reset only on explicit double-tap policy or intentional page reset  
- Pinch active: **no** PdfRenderer  
- Pinch end 200ms: primary hi-res only; old preview until replace  

### BUG-3 Horizontal cannot swipe naturally

**Repro**

1. Switch to horizontal paged  
2. Drag horizontally  
3. Often cannot page; edge taps work  

**Expected**

- Real horizontal Pager with fling / snap  
- Edge taps remain  
- scale > 1: pan only; page only at 1× and horizontal boundary  

### BUG-4 Horizontal chrome / layout

**Current**

- Auto-hides top bar after 2s  
- Users unsure how to restore  
- Controls still feel heavy; reading area compressed  

**Expected**

- Default chrome **shown**  
- Center tap toggles top/bottom chrome  
- **No** auto lock into undiscoverable immersion  
- Controls as overlay; no permanent large bottom slider eating viewport  

---

## D. High-probability root causes (code-backed)

Each item: path → symbol → behavior → defect link → how next agent verifies.

### D1. Continuous zoom is split: local UI state vs VM `zoomScale`

- **Path:** `PdfViewerScreen.kt` → `PdfVerticalContinuousArea`  
- **Symbols:** `committedZoom`, `gestureZoom`, `LaunchedEffect(state.zoomScale)`  
- **Behavior:** Pinch mutates local `committedZoom`/`gestureZoom`; VM updated only via `onZoomScaleSettled`. `LaunchedEffect(state.zoomScale)` can overwrite local zoom when VM emits 1f or lags.  
- **Why:** Scale appears to “snap back” if VM default/`Ready.zoomScale` fights local state, or after recompose/mode rebuild of parent.  
- **Verify:** Log `committedZoom`, `gestureZoom`, `state.zoomScale` across pinch start/end/recompose; ensure single source of truth (prefer VM or rememberSaveable document-level).

### D2. Continuous pinch uses GPU scale while layout stays on `committedZoom`

- **Path:** same; `graphicsLayer { scaleX/Y = gestureZoom }` vs `layoutZoom` page width  
- **Behavior:** During pinch, visual scale ≠ layout size; on end, gestureZoom→1 and layout jumps to final width + `restoreZoomAnchor` may `scrollToItem`.  
- **Why:** Perceived reset / page jump / “reflow flash”.  
- **Verify:** Record firstVisibleItemIndex + scrollOffset before/after pinch end; disable `restoreZoomAnchor` temporarily to isolate.

### D3. Scroll settle → `schedulePrimaryHiRes` + window `retainOnly` + UI republish

- **Path:** `PdfViewerViewModel.kt` → `onVerticalPageSettled`, `ensurePreviewAround`, `schedulePrimaryHiRes`, `publishCache`  
- **Behavior:** Debounced 150ms settle updates `pageIndex`, retains only ±1, may demote HIGH, republishes `pageBitmaps` map. Empty page shows `CircularProgressIndicator` + `LaunchedEffect(page){ onEnsureAround(page) }`.  
- **Why:** Small scroll changing “primary” triggers cache window move; missing neighbor shows white spinner; map identity change recomposes Images.  
- **Verify:** Log `pageIndex`, cache keys, job starts while slow-scrolling 1 page height; assert no PREVIEW clear for pages still on screen; assert no HIGH request at zoom=1 (`continuousNeedsHiRes`).

### D4. Generation bump cancels in-flight job but empty page still Loading

- **Path:** `requestPageRender` — bumps `pageGenerations`, cancels `cacheJobs[page]`  
- **Behavior:** Old bitmap should remain in `pageCache` until `putIfCurrent` success.  
- **Why residual flicker:** If `retainOnly` removed the page, or UI reads `state.pageBitmaps` that was published without key after demote fail, Loading branch runs.  
- **Verify:** Breakpoint/log when UI branch `bmp == null` while page is visible; ensure `publishCache` never drops in-window keys without replacement.

### D5. `onViewportWidth` re-triggers preview + hi-res schedule

- **Path:** `onViewportWidth` → `ensurePreviewAround` + `schedulePrimaryHiRes`  
- **UI:** `onSizeChanged` on continuous Box  
- **Behavior:** Any width change (chrome show/hide, system bars, first layout) restarts fill/upgrade.  
- **Why:** Flicker when top bar toggles or IME/insets change measured width.  
- **Verify:** Ignore width changes below N px threshold; log calls during scroll/chrome toggle.

### D6. Horizontal `scale`/`offset` keyed by `state.pageIndex`

- **Path:** `PdfHorizontalPagedArea`  
- **Symbols:** `var scale by remember(state.pageIndex) { 1f }`, `offset` same  
- **Behavior:** Any pageIndex change resets zoom to 1×.  
- **Why:** Expected for page change; also resets if `pageIndex` thrash from pager↔VM feedback loop.  
- **Verify:** Log pageIndex during attempted swipe; if thrashing, fix settle/goToPage loop first.

### D7. Horizontal pointer handlers may steal Pager drag

- **Path:** `PdfHorizontalPagedArea` page `pointerInput` → `detectTransformGestures` always installed  
- **Behavior:** Transform gestures can consume pans even near 1×; `userScrollEnabled = scale <= 1.01f` only helps if events reach Pager.  
- **Why:** BUG-3 “cannot swipe, only edge tap”.  
- **Verify:** Temporarily remove transform pointerInput at scale==1; confirm Pager swipe works; then reintroduce pan only when scale>1.

### D8. Horizontal chrome auto-hide + TopBar out of composition

- **Path:** `PdfViewerScreen` Ready branch: `LaunchedEffect(chromeVisible, …) { delay(2000); chromeVisible=false }`; `PdfChrome(topBarVisible = isVertical || chromeVisible)`  
- **Behavior:** After 2s TopBar removed; center tap should toggle but competes with edge tap zones / transform.  
- **Why:** BUG-4 undiscoverable immersion; also `onSizeChanged` width changes when TopBar appears/disappears (links D5).  
- **Verify:** Set auto-hide off; confirm default chrome stays; only toggle on center tap.

### D9. Horizontal `LaunchedEffect(scale)` always calls `onZoomScaleSettled`

- **Path:** end of `PdfHorizontalPagedArea`  
- **Behavior:** On composition and every scale change after 200ms, pushes zoom into VM (quality upgrades).  
- **Why:** Extra render churn; possible interaction with continuous if mode switch shares VM zoom field.  
- **Verify:** Only call settle on gesture end / double-tap, not on first composition.

### D10. Item keys / bitmap map identity

- **Path:** continuous `items(key = { "$docId|$page" })` — stable. Good.  
- **Risk:** `pageBitmaps` new Map every `publishCache` forces Image recomposition; if combined with aspect default 1.414 then real aspect, layout height jumps once (not key destroy).  
- **Verify:** Freeze aspects after first known value; ensure aspect never comes from bitmap pixel size mid-upgrade.

### D11. Scroll feedback must not re-emit PageJump

- **Path:** `onVerticalPageSettled` must not call `emitPageJump`; only `PageJumpRequest` drives `scrollToItem`.  
- **Current:** settled does **not** emit jump (correct). Fast scroller **does** emit jump (intended).  
- **Verify:** Ensure no new code path ties pageIndex LaunchedEffect to scrollToItem in continuous.

---

## E. Fixed product decisions (next phase)

### Continuous

- Scroll: **preview only**  
- Cache: primary ±1 preview max  
- Pinch active: **GPU transform only**, no PdfRenderer, no quality bucket change  
- Pinch end: debounce **200ms**, upgrade **primary only**  
- Hi-res: atomic replace; **never** clear old bitmap first  
- Cancel off-screen / stale high jobs  
- No full-document pre-render  
- Target pinch range **1×–3×** (update `MAX_ZOOM` if still 2.5)  
- Persist scale/pan across release; no accidental reset  

### Horizontal

- **HorizontalPager** is primary navigation  
- Edge taps secondary  
- Center tap toggles chrome; **default chrome visible**  
- **No** auto-enter undiscoverable immersion (remove or gate 2s auto-hide)  
- Double-tap 1×/2×  
- Pinch keeps final scale  
- scale > 1×: pan page; do not page until 1× + edge  
- Chrome as overlay; do not permanently crush viewport with large bottom slider  

### Print / non-goals

- Keep PrintPageSection locked tests  
- Out of scope: Markdown, ZIP package, table extraction, OCR, knowledge, PDFium  

---

## F. Acceptance criteria

1. Continuous scroll 30 pages: no white full page, no obvious flicker.  
2. Slow small scroll: no repeated hi-res thrash at 1×.  
3. Pinch to 2×, release: stays 2×.  
4. Hi-res arrival: only sharper, never blank.  
5. Horizontal: fling/drag pages.  
6. Horizontal: edge tap still pages.  
7. scale > 1×: horizontal drag pans only.  
8. Center tap toggles chrome; no auto lock immersion.  
9. Print map 33→2, 330→299, 633→602 unit tests pass.  
10. No regression: Markdown, ZIP, relative images, outline, print anchors.  
11. `testDebugUnitTest`, `lintDebug`, `assembleDebug` pass when work resumes.  

---

## G. Workspace state

### Branch / commits

- Branch: `master`  
- Recent commits:  
  - `4ce2822` chore: prepare v0.1.0-alpha release notes  
  - `fa33b97` docs: finalize MVP release notes  
  - `d7f1e8e` feat: add local markdown image support  
  - `b776e63` feat: add markdown table support  
  - `b6cd155` feat: add lightweight code syntax highlighting  

### Dirty tree (summary)

- **Modified tracked:** app gradle/manifest, MainActivity, DB/repo, Markdown viewer, theme, strings, settings, home/nav, etc. (`git diff --stat` ~37 files, +3029/-605 at handoff snapshot).  
- **Untracked (critical for PDF):** entire  
  - `app/src/main/java/com/markleaf/app/pdf/`  
  - `app/src/main/java/com/markleaf/app/ui/pdf/`  
  - plus packageimport, share, many tests, docs PDF_P0_*, etc.  
- **Uncommitted but previously build-verified:** yes — last APK assembled from this dirty tree.  
- **Do not commit:** inbox/outbox PDFs/ZIPs, `.kotlin/`, image caches, large fixtures, APK binaries unless release process requires.

### APK

`/storage/emulated/0/outbox/MarkLeaf/markleaf-debug.apk`

### Suggested first tasks for next Agent

1. Read this file + `.memory/active_object.yaml` + `.memory/CURRENT_STATE.md`.  
2. **Do not** start with architecture rewrite.  
3. Fix in order:  
   - **BUG-3** pointerInput vs Pager at scale=1 (quick win)  
   - **BUG-4** remove auto-hide; default chrome on; center toggle only  
   - **BUG-2** single source of truth for continuous zoom; eliminate layout/GPU fight + VM overwrite  
   - **BUG-1** prove at zoom=1 no hi-res; stabilize retainOnly/publish so in-window preview never null  
4. Re-run unit tests for print + quality; then device accept F1–F10.  

---

## Related docs

- `docs/PDF_P0_1.md` … `PDF_P0_4A.md` — earlier PDF workbench stages  
- `.memory/DECISIONS.md` — historical decisions (if present)  
- `AGENTS.md` — project scope + handoff entry pointer  
