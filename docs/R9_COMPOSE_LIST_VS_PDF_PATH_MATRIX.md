# R9 — Home / Markdown / PDF continuous path matrix

Companion to `docs/R9_COMPOSE_LIST_VS_PDF_SCROLL_ISOLATION_REPORT.md`.

| Capability / path | Home | Markdown | PDF continuous |
|---|---|---|---|
| Compose LazyColumn | Yes | Yes | Yes |
| Variable height item | Yes | Yes | **No** (aspectRatio) |
| Compose Text body | Yes | Yes | No (chrome only) |
| AnnotatedString | No | Yes | No |
| Top-bar scroll linkage | **No** (fixed TopAppBar) | **Yes** (nestedScroll + overlay Animatable) | **No** (stable bar) |
| Per-frame scroll state → ViewModel | No | No during fling (settled save) | Viewport ensure via snapshotFlow |
| Page / item size changes mid-scroll | Text reflow | Text reflow | Rare |
| Bitmap page body | No | Optional images | **Yes** |
| stable item size | No | No | **Yes** |
| contentType | Missing | Yes | Missing |
| item key | `document.id` | `docId:blockId` | `docId\|page` |
| WindowInsets | Scaffold default | statusBars + nav | Scaffold / viewport |
| List re-emit on scroll | No | Blocks fixed after load | Bitmap map as pages ready |
| Root composable | `HomeScreen` | `ViewerSuccessScreen` / body | `PdfVerticalZoomLockArea` |
| Scaffold / top bar | Scaffold + TopAppBar | Overlay ReaderTopBar | Stable PDF chrome |
| Scroll container | LazyColumn | LazyColumn + nestedScroll | LazyColumn (+ zoom lock) |
| Animations on scroll | None for bar | Top bar hide/show Animatable | Gesture/zoom (not bar hide) |

## Priority suspects (home∩MD − PDF)

1. Variable-height Compose text measure for list bodies  
2. Lack of fixed-aspect body geometry  

## Proven split (isolation)

| Screen | Primary residual driver |
|---|---|
| Markdown | Scroll-hide top bar (**chrome A/B gain**); residual text blocks after fix |
| Home | Multi-line **card** item UI (ladder E jump); no scroll chrome |
| PDF continuous | Architecture: bitmap + fixed aspect (formal FT this session **invalid** — dumps were home) |
