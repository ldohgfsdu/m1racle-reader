# MARKLEAF_MARKDOWN_TOC_ANCHOR_AND_SCROLL_PERFORMANCE

Updated: 2026-07-14

## Status

- implementation: complete
- host_performance_validation: pass on optimized main-package build
- toc_anchor_validation: pass
- main-package 120Hz touch display mode: pass
- qa_device_instrumentation: blocked
- human_scroll_acceptance: accepted
- markdown_toc_anchor: device_accepted
- markdown_scroll_performance: device_accepted
- markdown_reader_acceptance: device_accepted
- R4 table visual parameters: unchanged

## Production-path audit

The production path was already block-lazy and was retained:

`ViewerScreen` -> `ViewerSuccessScreen` -> `ReaderDocumentBody` -> `LazyColumn` ->
`itemsIndexed(state.blocks)` -> `MarkdownBlockView`.

- Markdown is read and parsed once by `ViewerViewModel.load()` on `Dispatchers.Default`. It is not
  parsed during recomposition or when a block enters the viewport.
- Before this change, Lazy items had `contentType`, but the key was only `type-index`. It is now
  `documentId:blockId`, using immutable parse-result block IDs.
- The document body does not nest another vertical lazy/scroll container. Tables retain only their
  existing horizontal scroll.
- Reading position was already isolated through `snapshotFlow`, `distinctUntilChanged`, integer
  percentage changes, and settle/background flushes. Room is not written on every pixel/frame and
  its schema/semantics were not changed.
- TOC active-state observation remains inside `TocDrawerContent`; body items do not subscribe to raw
  scroll offset.
- The remaining ordinary-block intrinsic pass was the quote rail's `IntrinsicSize.Min`. It was
  replaced by an equivalent non-measuring `drawBehind` rail. The accepted R4 table renderer had no
  intrinsic sizing and its visual tokens were not changed.

## Anchor implementation

`MarkdownParser` now constructs an immutable document-level `AnchorRegistry` together with the AST:

`anchorId -> MarkdownAnchorTarget(blockIndex, headingIndex)`.

- Chinese headings are retained in slugs.
- Duplicate suffixes are stable: `title`, `title-2`, `title-3`.
- `InlineMarkdownParser` preserves each link destination as an `AnnotatedString` annotation.
- `InlineMarkdownText` activates the annotation instead of rendering an inert underline.
- `#anchor` resolves through the O(1) registry; it never scans heading text or reparses the document.
- Missing anchors log `MARKDOWN_TOC anchor_miss` and do not crash.
- External URLs continue through the platform URI handler.
- Near targets (at most four lazy items) use a short `animateScrollToItem`; distant targets use
  direct `scrollToItem`. Both first force the auto-hiding TopBar visible, then use the heading's own
  top padding as the positive scroll offset. This cancels the heading-internal padding exactly and
  leaves the heading text 8dp below the 56dp TopBar instead of double-counting the list inset.
- Fragment lookup normalizes punctuation in both generated and authored anchors, so links such as
  `#一、心电图纸与正常波形` resolve to the generated `一心电图纸与正常波形` slug.
- Drawer rows remain clickable and now expose an explicit directory-link accessibility description.

Host ADB activation against the real main-package document passed. The target heading bounds were
`y=358..472` on the 520dpi device; the TopBar bottom is approximately `y=332`, leaving the intended
8dp (about 26px) reading gap. Evidence: `dist/performance/markdown-anchor-top-fixed.png`.

| Case | Anchor | Result |
|---|---|---|
| First Chinese heading | `长文档章节-1` | PASS |
| Middle Chinese heading | `长文档章节-8` | PASS |
| Last duplicate heading | `重复章节-3` | PASS |
| Missing target | `不存在` | PASS, no crash |

Evidence: `dist/performance/markdown-scroll-20260714-174129/toc-anchor-results.json` and the
base64-safe structured activation log in `anchor-events.jsonl`.

## Scroll and table root cause

The visible hitch was not Markdown reparsing. `TableBlockView` remembered the solved widths only
inside an individual lazy item composition. Lazy disposal removed that state, so an unchanged table
re-entering the viewport rebuilt every unconstrained cell measurement and, for two-column tables,
all 12 finite width candidates.

The new document-scoped `TableLayoutCache` key contains:

- document ID and stable table ID;
- table content hash;
- viewport width, density, and system font scale;
- reader text size, line-height, and page-margin settings;
- resolved typography identity and layout direction.

Vertical and horizontal raw offsets are excluded. The value retains the immutable full column
solution. Each table's horizontal offset is also kept by stable table ID so lazy disposal does not
reset it. Cell measurements within one solve are reused by text, resolved style identity, width and
wrap mode. Traces cover `MarkdownParse`, `MarkdownBlockCompose`, `MarkdownTableSolve`,
`MarkdownTableMeasure`, and `AnchorJump`.

On the fixed QA fixture, first layout/warm-up performed 1 parse, 7 table solves and 84 actual cell
measurements. After clearing counters, five down/up round trips produced:

- parse count delta: 0
- table solve count delta: 0
- cell measure count delta: 0
- cache hits: 5
- cache misses: 0

Before instrumentation existed, every lazy-disposed table re-entry necessarily executed one full
solver call; an exact pre-change solver count cannot truthfully be reconstructed. The pre-change
frame baseline was captured before implementation.

## Device performance

Device: `25098PN5AC / db04f865`.

The previous report mixed isolated QA Choreographer telemetry with the real reader and therefore
overstated `host_performance_validation`. That conclusion is withdrawn. Main-package captures are:

| Metric | Original main package | Main package after 120Hz policy |
|---|---:|---:|
| Refresh rate | 60.000 Hz | 120.000 Hz |
| Vertical p50 | 9.511 ms | 12.454 ms |
| Vertical p95 | 14.380 ms | 19.146 ms |
| Table horizontal p50 | 5.355 ms | 5.878 ms |
| Table horizontal p95 | 11.962 ms | 17.801 ms |

`MainActivity` now selects the highest same-resolution display mode and, on Android 15+, enables
touch frame-rate boost while disabling balanced frame-rate power saving for this foreground reader
window. HyperOS changed the real main-package display from mode 4 / 60Hz to mode 1 / 120Hz during
touch, confirmed by `dumpsys display`. Reading-position persistence no longer enqueues Room writes
while a gesture is active; the exact position is flushed after settling/background/leave.

The user's retest correctly rejected the debug build. A focused Perfetto capture showed GPU draw at
about 0.5ms while the debug runtime performed JIT work and LazyColumn/Compose semantics updates.
Three enabled accessibility services (Flowmix, GKD and Scene) made each lazy semantics structure
update cost about 4ms. Debug `gfxinfo` measured p50 8ms / p95 21ms, so merely requesting the 120Hz
panel could not make that build smooth.

The installed acceptance build now uses the same `com.markleaf.app` ID and signing key but the
non-debuggable optimized release path with the packaged Compose startup profile. Device dexopt is
`speed-profile`. It also contains the settled-only reading-position observer, which no longer reads
or allocates index/offset snapshots for each pixel-level scroll change.

| Optimized real-reader flow | Frames | p50 | p90 | p95 | p99 | Missed vsync |
|---|---:|---:|---:|---:|---:|---:|
| Long-document vertical, up/down | 2030 | 5ms | 6ms | 7ms | 8ms | 0 |
| Three-column table horizontal, both directions | 2082 | 5ms | 5ms | 5ms | 7ms | 0 |

Both p95 values are below the 8.33ms 120Hz budget. Evidence is under
`dist/performance/markdown-live-user-report-20260714-1826/`. The subsequent real-finger review
accepted long-document and table scrolling, completing the human tactile gate.

## Validation and isolation

- JVM: 615/615 PASS (minimum 606)
- existing table suites: 37/37 PASS; cache tests: 2/2 PASS
- display configuration matrix: 27/27 PASS
- lintDebug: PASS
- assembleDebug: PASS
- assembleQa: PASS
- compileQaAndroidTestKotlin: PASS
- assembleQaAndroidTest: PASS
- QA performance target printed as `com.markleaf.app.qa`
- QA was installed, measured, and uninstalled; `com.markleaf.app` remained installed throughout
- no main-package instrumentation, uninstall, or clear-data command ran
- isolated HyperOS QA instrumentation status remains BLOCKED; it is not reported as PASS

R4 regression screenshot:
`dist/performance/markdown-toc-scroll-r4-regression.png`. The accepted two/three-column solver,
header baseline, typography, padding, dividers, horizontal gesture behavior and 27-setting matrix
are unchanged.

## Artifact

- Path: `dist/markleaf-markdown-toc-scroll-performance-r4-optimized.apk`
- Bytes: 56,943,132
- SHA-256: `591415E435AF286DC29AEDD5654D9736974EBD1B551416128922BE4D8CA3EC56`
- Build: non-debuggable optimized release path, same applicationId and signing key, device
  `speed-profile` compiled.
- Installation: `adb install -r` PASS on `db04f865`; no uninstall, clear-data, or main-package
  instrumentation was used.

Human acceptance confirms that underlined TOC entries are clickable, Chinese headings resolve to
the correct title, the title lands below the TopBar, long-document vertical scrolling is smooth,
and tables/code blocks enter the viewport without a visible stall. No further fling, damping,
animation, or LazyColumn tuning is required. Overall status: `device_accepted`.
