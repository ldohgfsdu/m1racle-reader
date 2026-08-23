package com.markleaf.app.ui.pdf

import android.app.Application
import android.util.Log
import android.content.pm.ApplicationInfo
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.markleaf.app.data.local.DocumentEntity
import com.markleaf.app.data.local.DocumentType
import com.markleaf.app.data.local.PdfPrintPageAnchorEntity
import com.markleaf.app.data.local.TextbookOutlineNodeEntity
import com.markleaf.app.data.local.TextbookOutlineNodeType
import com.markleaf.app.data.repository.DocumentRepository
import android.net.Uri
import com.markleaf.app.pdf.EmbeddedOutlineBuilder
import com.markleaf.app.pdf.EmbeddedOutlineNodeDraft
import com.markleaf.app.pdf.OutlineImportMode
import com.markleaf.app.pdf.PdfEmbeddedOutlineParser
import com.markleaf.app.pdf.BitmapMetadata
import com.markleaf.app.pdf.PageBitmapEntry
import com.markleaf.app.pdf.PageRenderRole
import com.markleaf.app.pdf.PdfPageBitmapCache
import com.markleaf.app.pdf.PdfChromePrefs
import com.markleaf.app.pdf.PdfPageRenderer
import com.markleaf.app.pdf.PdfReadingMode
import com.markleaf.app.pdf.PdfReadingNavigation
import com.markleaf.app.pdf.PdfReadingPrefs
import com.markleaf.app.pdf.PdfTopBarMode
import com.markleaf.app.pdf.PdfReadingPositionCodec
import com.markleaf.app.pdf.PdfRenderQuality
import com.markleaf.app.pdf.PdfViewportSessionState
import com.markleaf.app.pdf.toCanonicalAnchor
import com.markleaf.app.pdf.PdfViewportSessionTracker
import com.markleaf.app.pdf.PdfTileEntry
import com.markleaf.app.pdf.PdfTileKey
import com.markleaf.app.pdf.PdfTileRequest
import com.markleaf.app.pdf.PdfTilePlan
import com.markleaf.app.pdf.PdfTileLru
import com.markleaf.app.pdf.PageLayoutSize
import com.markleaf.app.pdf.TileOverlaySnapshot
import com.markleaf.app.pdf.TileRenderHealthTracker
import com.markleaf.app.pdf.TileRenderPipelinePolicy
import com.markleaf.app.pdf.TileWorkQueue
import com.markleaf.app.pdf.FinalPdfProgressFlusher
import com.markleaf.app.pdf.FinalPdfProgressSnapshot
import com.markleaf.app.pdf.ReadingPosition
import com.markleaf.app.pdf.ReadingPositionSource
import com.markleaf.app.pdf.ContinuousViewportPolicy
import com.markleaf.app.pdf.ContinuousQualityHysteresis
import com.markleaf.app.pdf.ContinuousDocumentSession
import com.markleaf.app.pdf.ContinuousDocumentState
import com.markleaf.app.pdf.ContinuousEntryRestoreState
import com.markleaf.app.pdf.ContinuousEntryState
import com.markleaf.app.pdf.ContinuousModeZoomContract
import com.markleaf.app.pdf.ContinuousScrollDirection
import com.markleaf.app.pdf.ContinuousScrollPhase
import com.markleaf.app.pdf.ContinuousViewportSnapshot
import com.markleaf.app.pdf.ContinuousRenderKey
import com.markleaf.app.pdf.PdfThumbnailLoader
import com.markleaf.app.pdf.PrintPageSection
import com.markleaf.app.pdf.PrintPageSectionCatalog
import com.markleaf.app.pdf.PrintPageResolver
import com.markleaf.app.pdf.QualityTier
import com.markleaf.app.pdf.TextbookOutlineResolver
import com.markleaf.app.pdf.ThreePageStripState
import com.markleaf.app.pdf.ZoomPanPhysics
import com.markleaf.app.pdf.ZoomPanPhysicsProfile
import com.markleaf.app.pdf.export.ChapterExportFormat
import com.markleaf.app.pdf.export.ChapterExportState
import com.markleaf.app.pdf.export.ChapterPackageExporter
import com.markleaf.app.pdf.export.ExportCancellationToken
import com.markleaf.app.pdf.export.ExportRangePlan
import com.markleaf.app.pdf.export.ExportRangeResolver
import com.markleaf.app.pdf.export.PdfAnnotatedExporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed class PdfViewerUiState {
    data object Loading : PdfViewerUiState()

    data class Ready(
        val document: DocumentEntity,
        val pageIndex: Int,
        val pageCount: Int,
        val pageImage: ImageBitmap?,
        val isRendering: Boolean,
        val renderError: String? = null,
        /** Anchors for this document (sorted optional). */
        val printAnchors: List<PdfPrintPageAnchorEntity> = emptyList(),
        /** Textbook outline nodes (P0.3A). */
        val outlineNodes: List<TextbookOutlineNodeEntity> = emptyList(),
        val readingMode: PdfReadingMode = PdfReadingMode.default(),
        val showPageButtons: Boolean = true,
        /** Continuous-mode page bitmaps (current ±1 only). Never cleared on upgrade start. */
        val pageBitmaps: Map<Int, ImageBitmap> = emptyMap(),
        /** Page aspect (h/w) when known; layout must use this, not bitmap pixels. */
        val pageAspects: Map<Int, Float> = emptyMap(),
        /** P0B1: Tier + widthBucket metadata per page for display merge policy. */
        val pageBitmapMetadata: Map<Int, BitmapMetadata> = emptyMap(),
        /** Print numbering sections for this document (may be empty). */
        val printSections: List<PrintPageSection> = emptyList(),
        /** Last stable zoom for the active reading mode; R7 continuous supports 1×..4×. */
        val zoomScale: Float = 1f,
        /** Stable R7 transform restored after configuration or reading-mode recreation. */
        val continuousDocumentState: ContinuousDocumentState = ContinuousDocumentState(),
        val viewportSessionState: PdfViewportSessionState? = null,
        val tileEntries: Map<PdfTileKey, PdfTileEntry> = emptyMap(),
        val tileDocumentGeneration: Long = 0L,
        /** Entry restore phase gates continuous viewport feedback and progress writes. */
        val continuousEntryState: ContinuousEntryState = ContinuousEntryState(),
        /** Settled quality tier for primary page (hysteresis). */
        val qualityTier: QualityTier = QualityTier.PREVIEW,
        /** Width bucket (px) of the latest primary render. */
        val renderWidthBucket: Int = 0,
    ) : PdfViewerUiState() {
        val pageLabel: String
            get() = if (pageCount <= 0) "—" else "${pageIndex + 1} / $pageCount"

        /** 0-based PDF index used for print resolution (never UI-only hacks). */
        val printResolvePageIndex: Int
            get() = pageIndex

        private val printResolve: PrintPageResolver.Result
            get() = PrintPageResolver.resolveDetailed(
                documentId = document.id,
                pdfPageIndex = printResolvePageIndex,
                anchors = printAnchors,
                sections = printSections,
            )

        val printPageNumber: Int?
            get() = printResolve.printPageNumber

        val isPrintExactAnchor: Boolean
            get() = printResolve.isExactAnchor

        val isPrintInferred: Boolean
            get() = printResolve.isInferred

        val currentOutlineNode: TextbookOutlineNodeEntity?
            get() = TextbookOutlineResolver.deepestContaining(pageIndex, outlineNodes)

        val outlinePath: List<TextbookOutlineNodeEntity>
            get() = TextbookOutlineResolver.pathToRoot(currentOutlineNode, outlineNodes)

        val outlinePathLabel: String
            get() = TextbookOutlineResolver.pathLabel(outlinePath)

        val canGoPrevious: Boolean get() = pageIndex > 0
        val canGoNext: Boolean get() = pageIndex < pageCount - 1
    }

    data class Error(
        val displayName: String?,
        val message: String,
    ) : PdfViewerUiState()
}

/**
 * Offer to import PDF embedded bookmarks after open.
 */
data class EmbeddedOutlineOffer(
    val drafts: List<EmbeddedOutlineNodeDraft>,
    val hasLocalNodes: Boolean,
    val alreadyImported: Boolean,
)

class PdfViewerViewModel(
    application: Application,
    private val documentId: String,
    private val repository: DocumentRepository,
    private val applicationScope: CoroutineScope,
) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val renderer = PdfPageRenderer(application.applicationContext)
    /** Separate renderer + LRU for sheet thumbnails (does not block main page). */
    private val thumbnailLoader = PdfThumbnailLoader(application.applicationContext)
    private val readingPrefs = PdfReadingPrefs(application.applicationContext)
    private val chromePrefs = PdfChromePrefs(application.applicationContext)

    private val _topBarMode = MutableStateFlow(chromePrefs.getTopBarMode())
    val topBarMode: StateFlow<PdfTopBarMode> = _topBarMode.asStateFlow()

    fun setTopBarMode(mode: PdfTopBarMode) {
        if (_topBarMode.value == mode) return
        chromePrefs.setTopBarMode(mode)
        _topBarMode.value = mode
    }

    // Material style prefs removed (r26.1): Liquid Glass is the only chrome surface.

    private val _uiState = MutableStateFlow<PdfViewerUiState>(PdfViewerUiState.Loading)
    val uiState: StateFlow<PdfViewerUiState> = _uiState.asStateFlow()

    private val _embeddedOffer = MutableStateFlow<EmbeddedOutlineOffer?>(null)
    val embeddedOutlineOffer: StateFlow<EmbeddedOutlineOffer?> = _embeddedOffer.asStateFlow()

    private val _importMessage = MutableStateFlow<String?>(null)
    val importMessage: StateFlow<String?> = _importMessage.asStateFlow()

    private val _exportPlan = MutableStateFlow<ExportRangePlan?>(null)
    val exportPlan: StateFlow<ExportRangePlan?> = _exportPlan.asStateFlow()

    private val _exportState = MutableStateFlow<ChapterExportState?>(null)
    val exportState: StateFlow<ChapterExportState?> = _exportState.asStateFlow()

    /** Generation gate: late callbacks of cancelled exports never touch newer state. */
    private val exportStateGate = ExportStateGate()

    /** Cancellation token of the CURRENT export run (job-local, replaced per start). */
    private var activeExportCancellation: ExportCancellationToken? = null

    private var exportJob: Job? = null
    private var pendingExportPlan: ExportRangePlan? = null
    private var lastExportSuggestedName: String? = null
    private var pendingExportFormat: ChapterExportFormat = ChapterExportFormat.ZIP_PACKAGE
    private val chapterExporter = ChapterPackageExporter(application.applicationContext)
    private val pdfAnnotatedExporter = PdfAnnotatedExporter(application.applicationContext)

    val lastExportFormat: ChapterExportFormat
        get() = pendingExportFormat

    /** Exposed for the thumbnails Advanced Card; call [prepareThumbnails] first. */
    fun thumbnailLoader(): PdfThumbnailLoader = thumbnailLoader

    private var renderJob: Job? = null
    /** Per-page continuous render jobs; key = pageIndex. */
    private var cacheJobs = mutableMapOf<Int, Job>()
    private val cacheJobKeys = mutableMapOf<Int, ContinuousRenderKey>()
    /** Monotonic generation per page; stale jobs must not apply. */
    private var pageGenerations = mutableMapOf<Int, Long>()
    private val cacheMutex = Mutex()
    private val pageCache = PdfPageBitmapCache()
    private var persistJob: Job? = null
    private var zoomSettleJob: Job? = null
    /** Debounced primary-only hi-res after scroll idle / pinch end. */
    private var primaryUpgradeJob: Job? = null
    private var lastPersistedPage: Int = -1
    /** Last page that received a continuous hi-res upgrade request. */
    private var lastHiResPrimary: Int = -1
    /** Measured Compose viewport width in **px** (never dp). */
    private var viewportWidthPx: Int = 1080
    private var openedDocument: DocumentEntity? = null
    private var anchors: List<PdfPrintPageAnchorEntity> = emptyList()
    private var printSections: List<PrintPageSection> = emptyList()
    private var outlineNodes: List<TextbookOutlineNodeEntity> = emptyList()
    private var documentUri: String? = null
    private var cachedDrafts: List<EmbeddedOutlineNodeDraft> = emptyList()
    private var readingMode: PdfReadingMode = PdfReadingMode.default()
    private var showPageButtons: Boolean = true
    private var zoomScale: Float = 1f
    private var qualityTier: QualityTier = QualityTier.PREVIEW
    private val continuousDocumentSession = ContinuousDocumentSession()
    private var continuousEntryRestore = ContinuousEntryRestoreState()
    private var continuousGeometryReady = false
    private var lastPersistedReadingGeneration = -1L
    /** While true, continuous scroll must not update pageIndex chrome (zoom gesture). */
    private var suspendPageSettled: Boolean = false
    /** UI supplies plain page indexes; VM stays independent of Compose layout types. */
    private var visibleContinuousPages: Set<Int> = emptySet()
    private var continuousDirection = ContinuousScrollDirection.IDLE
    private var continuousPhase = ContinuousScrollPhase.IDLE
    private var continuousProtectedPages: Set<Int> = emptySet()
    private var continuousPrimaryPage: Int = 0
    /** R7.1: completed vertical renders stay cache-pending until transaction settle. */
    private var pendingContinuousCachePublish: Boolean = false
    private val pendingContinuousPages = linkedSetOf<Int>()
    private var continuousInteractionId: Long = 0L
    private val viewportSessionTracker = PdfViewportSessionTracker()
    private val tileCache = PdfTileLru()
    private val tileWorkQueue = TileWorkQueue()
    private val _tileOverlaySnapshot = MutableStateFlow(TileOverlaySnapshot())
    val tileOverlaySnapshot: StateFlow<TileOverlaySnapshot> = _tileOverlaySnapshot.asStateFlow()
    private val tileWakeSignal = Channel<Unit>(Channel.CONFLATED)
    private var tileWorkerJob: Job? = null
    private var tilePublishJob: Job? = null
    private val pendingTileSwaps = linkedMapOf<PdfTileKey, PdfTileEntry>()
    private val tileHealthTracker = TileRenderHealthTracker()
    private var latestVisibleTileKeys: Set<PdfTileKey> = emptySet()
    private var latestTilePlan: PdfTilePlan? = null
    private var tileOverlaySequence: Long = 0L
    private var tileWorkerStartCount: Long = 0L
    private var lastTileHealthLogNanos: Long = 0L
    private val tileDocumentGeneration: Long get() = tileWorkQueue.currentGeneration()
    /**
     * P0A lifecycle hardening: set to true in onCleared so that any subsequent
     * render/tile/thumbnail request is a no-op. Without this, a race between
     * onCleared and a pending Compose callback could queue new work after
     * applicationScope has issued the close.
     */
    @Volatile private var closed: Boolean = false
    /**
     * P0A lifecycle hardening: idempotent close guard. close() / closeAsync()
     * may be invoked from onCleared AND from openDocument; only the first
     * close on applicationScope actually runs.
     */
    @Volatile private var closeIssued: Boolean = false
    /**
     * P0A2/P0A3 lifecycle hardening: single atomic final progress flush.
     * Replaces two separate un-ordered writes (position + session).
     *
     * P0A2: writeFn now uses [DocumentRepository.updatePdfProgressSnapshotAtomic]
     * (Room withTransaction) so readingPosition + viewportSession land in a
     * single DB transaction. The coroutine Mutex in FinalPdfProgressFlusher
     * serializes writeFn calls but does NOT provide DB atomicity; this
     * transaction does. A crash between the two DAO updates is no longer
     * possible: either both land or neither does.
     *
     * P0A3: ALL PDF progress writes now go through this single writeFn —
     * debounced page-settle, zoom-settle, and final flush. The previous
     * `persistProgress` / `persistViewportSession` bypass paths that called
     * `updatePdfReadingPosition` / `updatePdfViewportSession` directly with
     * `viewModelScope.launch(NonCancellable + IO)` have been removed. This
     * closes the ACTIVE_BYPASS_PATH identified in the P0A3 audit: those bypass
     * writes did not participate in the flusher's generation ordering, so a
     * late bypass write could overwrite a newer final flush. position may be
     * null (session-only update) or session may be null (position-only update);
     * both are handled atomically by `updatePdfProgressSnapshotAtomic`.
     *
     * See [com.markleaf.app.pdf.FinalPdfProgressFlusher].
     */
    private val progressFlusher = FinalPdfProgressFlusher(applicationScope) { snapshot ->
        val pos = snapshot.readingPosition
        val session = snapshot.viewportSession
        repository.updatePdfProgressSnapshotAtomic(
            id = snapshot.documentId,
            pageIndex = pos?.pageIndex,
            intraPageFractionEncoded = pos?.let { PdfReadingPositionCodec.encodeFraction(it.intraPageFraction) },
            pageCount = if (pos != null) snapshot.pageCount else null,
            readingProgress = pos?.let { DocumentRepository.pdfReadingProgress(it.pageIndex, snapshot.pageCount) },
            session = session,
        )
    }
    /** Monotonic counter for final-flush snapshots; prevents out-of-order overwrites. */
    private var finalFlushGeneration: Long = 0L

    fun onContinuousViewportChanged(snapshot: ContinuousViewportSnapshot) {
        if (suspendPageSettled) return
        val ready = _uiState.value as? PdfViewerUiState.Ready ?: return
        val entryPhase = ready.continuousEntryState.phase

        // ── User-scroll escape hatch ────────────────────────────────
        // When the user explicitly drags while the state machine is still in
        // APPLYING_TARGET (restore not completed or validation stuck), cancel
        // the pending restore atomically and enter READY from the current
        // viewport position.  The old lastStable survives so an immediate
        // onStop/flush writes the prior real position, not page 0.
        if (entryPhase != com.markleaf.app.pdf.ContinuousEntryPhase.READY &&
            snapshot.phase == ContinuousScrollPhase.DRAGGING
        ) {
            if (continuousEntryRestore.cancelRestore()) {
                if (isDebuggable) {
                    Log.d(ZOOM_LOG_TAG,
                        "RESTORE_CANCELLED_BY_USER_DRAG oldPhase=$entryPhase " +
                        "consumedGeneration=${continuousEntryRestore.getConsumedGeneration()} " +
                        "viewportPrimary=${snapshot.primaryPage}")
                }
                _uiState.value = ReadyBase(
                    ready,
                    pageIndex = snapshot.primaryPage,
                    continuousEntryState = continuousEntryRestore.snapshot(),
                )
            }
            return
        }

        if (entryPhase != com.markleaf.app.pdf.ContinuousEntryPhase.READY) return
        visibleContinuousPages = snapshot.visiblePages
        continuousDirection = snapshot.direction
        continuousPhase = snapshot.phase
        continuousPrimaryPage = snapshot.primaryPage
        continuousProtectedPages = ContinuousViewportPolicy.boundedPreviewPages(
            snapshot,
            ready.pageCount,
        )
        primaryUpgradeJob?.cancel()
        if (snapshot.phase != ContinuousScrollPhase.IDLE) cancelNonScrollPreviewJobs()
        ensurePreviewAround(snapshot.primaryPage)
        if (snapshot.phase == ContinuousScrollPhase.IDLE) {
            ensureReadablePreviews(snapshot)
            schedulePrimaryHiRes(snapshot.primaryPage, reason = "viewportIdle")
        }
        if (isDebuggable) {
            Log.d(ZOOM_LOG_TAG,
                "VIEWPORT_PAGE_CANDIDATE primary=${snapshot.primaryPage} " +
                "visible=${snapshot.visiblePages} phase=${snapshot.phase} " +
                "direction=${snapshot.direction}")
        }
    }

    /**
     * One-shot programmatic scroll request for continuous mode.
     * [currentPageIndex] must never by itself trigger list scrolling.
     */
    data class PageJumpRequest(
        val id: Long,
        val targetPageIndex: Int,
        val source: String,
    )

    private val _pageJump = MutableStateFlow<PageJumpRequest?>(null)
    val pageJump: StateFlow<PageJumpRequest?> = _pageJump.asStateFlow()

    private fun emitPageJump(targetPageIndex: Int, source: String) {
        _pageJump.value = PageJumpRequest(
            id = System.nanoTime(),
            targetPageIndex = targetPageIndex,
            source = source,
        )
    }

    /** Continuous viewport acknowledges a one-shot jump so recreation cannot replay it. */
    fun onContinuousPageJumpConsumed(requestId: Long) {
        if (_pageJump.value?.id == requestId) _pageJump.value = null
    }

    /** Viewport has valid size/geometry; only then may a pending entry target apply. */
    fun onContinuousGeometryReady() {
        continuousGeometryReady = true
        val request = continuousEntryRestore.beginApplyIfReady(geometryReady = true) ?: return
        val ready = _uiState.value as? PdfViewerUiState.Ready ?: return
        if (ready.readingMode != PdfReadingMode.VERTICAL_CONTINUOUS) return
        _uiState.value = ReadyBase(
            ready,
            continuousEntryState = continuousEntryRestore.snapshot(),
        )
        if (isDebuggable) {
            Log.d(ZOOM_LOG_TAG, "ENTRY_PHASE old=WAITING_GEOMETRY new=APPLYING_TARGET " +
                "source=${request.source} page=${request.pageIndex} fraction=${request.intraPageFraction} " +
                "generation=${request.generation}")
        }
    }

    /** The viewport applies a center-anchor restore once geometry can validate it. */
    fun onContinuousEntryRestoreApplied(
        position: ReadingPosition,
        documentState: ContinuousDocumentState,
    ) {
        val ready = _uiState.value as? PdfViewerUiState.Ready ?: return
        if (!continuousEntryRestore.completeRestore(position.generation, position, ready.pageCount)) {
            if (isDebuggable) {
                val current = continuousEntryRestore.snapshot()
                Log.d(ZOOM_LOG_TAG,
                    "RESTORE_APPLY_REJECT generation=${position.generation} " +
                    "phase=${current.phase} " +
                    "pendingGen=${current.pendingRestorePosition?.generation} " +
                    "consumedGen=${continuousEntryRestore.getConsumedGeneration()}")
            }
            return
        }
        val stable = continuousDocumentSession.commit(documentState, position.pageIndex)
        zoomScale = PdfRenderQuality.clampContinuousZoom(stable.scale)
        qualityTier = ContinuousQualityHysteresis.resolve(zoomScale, qualityTier)
        val completed = continuousEntryRestore.snapshot()
        _uiState.value = ReadyBase(
            ready,
            pageIndex = position.pageIndex,
            isRendering = false,
            zoomScale = zoomScale,
            qualityTier = qualityTier,
            continuousDocumentState = stable,
            continuousEntryState = completed,
        )
        completed.lastStableReadingPosition?.let {
            if (isDebuggable) {
                Log.d(ZOOM_LOG_TAG,
                    "LAST_STABLE_UPDATE page=${it.pageIndex} fraction=${it.intraPageFraction} " +
                    "source=${it.source} generation=${it.generation} reason=restoreComplete")
            }
            schedulePersist(it, ready.pageCount, force = true)
        }
        if (isDebuggable) {
            Log.d(ZOOM_LOG_TAG,
                "RESTORE_COMPLETE source=${position.source} page=${position.pageIndex} " +
                "fraction=${position.intraPageFraction} generation=${position.generation}")
        }
    }

    private fun queueContinuousRestore(
        ready: PdfViewerUiState.Ready,
        pageIndex: Int,
        fraction: Float,
        source: ReadingPositionSource,
    ): ReadingPosition? {
        val request = continuousEntryRestore.requestRestore(
            pageIndex = pageIndex,
            intraPageFraction = fraction,
            source = source,
            pageCount = ready.pageCount,
        ) ?: return null
        continuousGeometryReady = false
        _pageJump.value = null
        _uiState.value = ReadyBase(
            ready,
            pageIndex = request.pageIndex,
            continuousEntryState = continuousEntryRestore.snapshot(),
        )
        if (isDebuggable) {
            Log.d(ZOOM_LOG_TAG, "TARGET_SELECTED source=${request.source} page=${request.pageIndex} " +
                "fraction=${request.intraPageFraction} generation=${request.generation}")
        }
        return request
    }

    private fun recordStableReadingPosition(
        ready: PdfViewerUiState.Ready,
        pageIndex: Int,
        fraction: Float,
        source: ReadingPositionSource,
    ): ReadingPosition? {
        val position = continuousEntryRestore.recordUserStable(
            pageIndex = pageIndex,
            intraPageFraction = fraction,
            source = source,
            pageCount = ready.pageCount,
        ) ?: return null
        if (isDebuggable) {
            Log.d(ZOOM_LOG_TAG,
                "LAST_STABLE_UPDATE page=${position.pageIndex} fraction=${position.intraPageFraction} " +
                "source=${position.source} generation=${position.generation} reason=userStable")
        }
        _uiState.value = ReadyBase(
            ready,
            pageIndex = position.pageIndex,
            continuousEntryState = continuousEntryRestore.snapshot(),
        )
        schedulePersist(position, ready.pageCount)
        return position
    }

    private fun publishCache(
        ready: PdfViewerUiState.Ready,
        pageIndex: Int = ready.pageIndex,
        pageImage: ImageBitmap? = ready.pageImage,
        isRendering: Boolean = ready.isRendering,
        renderError: String? = ready.renderError,
        readingMode: PdfReadingMode = ready.readingMode,
        showPageButtons: Boolean = ready.showPageButtons,
        zoomScale: Float = this.zoomScale,
        qualityTier: QualityTier = this.qualityTier,
    ): PdfViewerUiState.Ready {
        val primaryBucket = pageCache.get(pageIndex)?.widthBucket
            ?: PdfRenderQuality.targetWidthPx(viewportWidthPx, QualityTier.PREVIEW)
        return ready.copy(
            pageIndex = pageIndex,
            pageImage = pageImage ?: pageCache.image(pageIndex),
            isRendering = isRendering,
            renderError = renderError,
            readingMode = readingMode,
            showPageButtons = showPageButtons,
            pageBitmaps = pageCache.snapshotImages(),
            pageAspects = pageCache.snapshotAspects(),
            pageBitmapMetadata = snapshotPageMetadata(),
            printAnchors = anchors,
            printSections = printSections,
            outlineNodes = outlineNodes,
            zoomScale = zoomScale,
            qualityTier = qualityTier,
            renderWidthBucket = primaryBucket,
        )
    }

    /** P0B1: Build per-page BitmapMetadata from cache entries for display merge policy. */
    private fun snapshotPageMetadata(): Map<Int, BitmapMetadata> =
        pageCache.keys().associateWith { page ->
            val entry = pageCache.get(page)!!
            BitmapMetadata(entry.tier, entry.widthBucket)
        }

    private fun ReadyBase(
        ready: PdfViewerUiState.Ready,
        pageIndex: Int = ready.pageIndex,
        pageImage: ImageBitmap? = ready.pageImage,
        isRendering: Boolean = ready.isRendering,
        renderError: String? = ready.renderError,
        readingMode: PdfReadingMode = ready.readingMode,
        showPageButtons: Boolean = ready.showPageButtons,
        pageBitmaps: Map<Int, ImageBitmap> = ready.pageBitmaps,
        pageAspects: Map<Int, Float> = ready.pageAspects,
        pageBitmapMetadata: Map<Int, BitmapMetadata> = ready.pageBitmapMetadata,
        zoomScale: Float = ready.zoomScale,
        renderWidthBucket: Int = ready.renderWidthBucket,
        qualityTier: QualityTier = ready.qualityTier,
        continuousDocumentState: ContinuousDocumentState = ready.continuousDocumentState,
        viewportSessionState: PdfViewportSessionState? = ready.viewportSessionState,
        tileEntries: Map<PdfTileKey, PdfTileEntry> = ready.tileEntries,
        tileDocumentGeneration: Long = ready.tileDocumentGeneration,
        continuousEntryState: ContinuousEntryState = ready.continuousEntryState,
    ): PdfViewerUiState.Ready = ready.copy(
        pageIndex = pageIndex,
        pageImage = pageImage,
        isRendering = isRendering,
        renderError = renderError,
        readingMode = readingMode,
        showPageButtons = showPageButtons,
        pageBitmaps = pageBitmaps,
        pageAspects = pageAspects,
        pageBitmapMetadata = pageBitmapMetadata,
        printAnchors = anchors,
        printSections = printSections,
        outlineNodes = outlineNodes,
        zoomScale = zoomScale,
        qualityTier = qualityTier,
        continuousDocumentState = continuousDocumentState,
        viewportSessionState = viewportSessionState,
        tileEntries = tileEntries,
        tileDocumentGeneration = tileDocumentGeneration,
        continuousEntryState = continuousEntryState,
        renderWidthBucket = renderWidthBucket,
    )

    init {
        startTileRenderActor()
        openDocument()
    }

    fun retry() {
        openDocument()
    }

    /**
     * @param widthPx viewport width in **device pixels** from Compose onSizeChanged
     * (never density-independent dp).
     */
    fun onViewportWidth(widthPx: Int) {
        if (widthPx <= 0) return
        val clamped = widthPx.coerceIn(
            PdfRenderQuality.MIN_RENDER_WIDTH_PX,
            PdfRenderQuality.MAX_CONTINUOUS_WIDTH_PX,
        )
        if (clamped == viewportWidthPx) return
        viewportWidthPx = clamped
        val ready = _uiState.value as? PdfViewerUiState.Ready ?: return
        // Viewport size change: re-request previews only; keep old bitmaps until replace.
        when (ready.readingMode) {
            PdfReadingMode.HORIZONTAL_PAGED -> requestRender(ready.pageIndex)
            PdfReadingMode.VERTICAL_CONTINUOUS -> {
                ensurePreviewAround(ready.pageIndex)
                if (ready.continuousEntryState.phase == com.markleaf.app.pdf.ContinuousEntryPhase.READY) {
                    schedulePrimaryHiRes(ready.pageIndex, reason = "viewport")
                }
            }
        }
    }

    /**
     * Pinch/double-tap ended. GPU already applied scale in UI.
     * Debounce then upgrade **only** the primary page (never neighbors, never mid-scroll path).
     */
    /** Single committed zoom owner for the document session. */
    fun commitZoom(scale: Float, zoomTargetPage: Int? = null) {
        commitStableZoom(scale, zoomTargetPage, continuousState = null)
    }

    /** R7 stable-point commit: transform and quality state advance together exactly once. */
    fun commitContinuousDocumentState(
        state: ContinuousDocumentState,
        zoomTargetPage: Int? = null,
    ) {
        val ready = _uiState.value as? PdfViewerUiState.Ready ?: return
        if (ready.readingMode != PdfReadingMode.VERTICAL_CONTINUOUS) return
        val stable = continuousDocumentSession.commit(
            state,
            zoomTargetPage ?: ready.pageIndex,
        )
        commitStableZoom(stable.scale, zoomTargetPage, continuousState = stable)
    }

    fun commitViewportSession(session: PdfViewportSessionState) {
        val ready = _uiState.value as? PdfViewerUiState.Ready ?: return
        if (session.readingMode != ready.readingMode) return
        if (ready.readingMode == PdfReadingMode.VERTICAL_CONTINUOUS &&
            ready.continuousEntryState.phase != com.markleaf.app.pdf.ContinuousEntryPhase.READY
        ) return
        val normalized = session.normalized(ready.pageCount, ready.pageIndex) ?: return
        viewportSessionTracker.commit(normalized)
        _uiState.value = ReadyBase(ready, viewportSessionState = normalized)
        viewportSessionTracker.takeForFlush()?.let(::persistViewportSession)
    }

    /** Latest exact UI transform for an immediate mode switch; never persisted by itself. */
    fun previewViewportSession(session: PdfViewportSessionState) {
        val ready = _uiState.value as? PdfViewerUiState.Ready ?: return
        if (session.readingMode != ready.readingMode) return
        if (ready.readingMode == PdfReadingMode.VERTICAL_CONTINUOUS &&
            ready.continuousEntryState.phase != com.markleaf.app.pdf.ContinuousEntryPhase.READY
        ) return
        val normalized = session.normalized(ready.pageCount, ready.pageIndex) ?: return
        viewportSessionTracker.preview(normalized)
    }

    /** Non-suspending render-consumer ingress. Pointer input never waits for PdfRenderer. */
    fun requestPredictiveTiles(plan: PdfTilePlan) {
        if (closed) return
        val ready = _uiState.value as? PdfViewerUiState.Ready ?: return
        if (ready.readingMode != PdfReadingMode.VERTICAL_CONTINUOUS &&
            ready.readingMode != PdfReadingMode.HORIZONTAL_PAGED
        ) return
        val requests = plan.requests
        latestTilePlan = plan
        latestVisibleTileKeys = plan.currentVisibleKeys
        // Compute cache-hit keys for the queue to skip re-queueing.
        val cacheHitKeys: Set<PdfTileKey> = requests.mapTo(mutableSetOf()) { it.key }
            .filter { tileCache.get(it) != null }.toSet()
        val now = System.nanoTime()
        val coverage = TileRenderPipelinePolicy.coverage(latestVisibleTileKeys, tileCache.snapshot().keys)
        tileHealthTracker.onViewport(plan.snapshot, coverage)
        if (isDebuggable) {
            requests.filter { it.priority == com.markleaf.app.pdf.TilePriority.PAGE_AHEAD && it.key in cacheHitKeys }
                .forEach { Log.d(ZOOM_LOG_TAG, "PAGE_AHEAD_TILE key=${it.key} priority=${it.priority} cacheHit=true requestedBeforeVisible=true leadTimeMs=0") }
        }
        val submit = tileWorkQueue.submit(
            requests,
            plan.pageSizes,
            plan.snapshot.interactionPhase.name,
            cacheHitKeys,
        )
        tileHealthTracker.onRequests(submit.enqueuedKeys.size, now)
        if (isDebuggable) {
            Log.d(
                ZOOM_LOG_TAG,
                "RENDER_PLANNER_TICK snapshotSequence=${plan.snapshot.sequence} " +
                    "currentVisibleCount=${plan.currentVisibleKeys.size} queueDepth=${tileWorkQueue.pendingSize()} " +
                    "interactionPhase=${plan.snapshot.interactionPhase}",
            )
            requests.filter { it.key in submit.enqueuedKeys }.forEach { request ->
                Log.d(
                    ZOOM_LOG_TAG,
                    "TILE_REQUEST tileKey=${request.key} snapshotSequence=${request.snapshotSequence} " +
                        "priority=${request.priority} phase=${plan.snapshot.interactionPhase} " +
                        "requestTime=${request.requestedAtNanos} queueDepth=${tileWorkQueue.pendingSize()}",
                )
            }
            logGestureRenderHealth(now)
        }
        tileWakeSignal.trySend(Unit)
    }

    /** One actor for the ViewModel lifetime. Gesture down/up never restarts this worker. */
    private fun startTileRenderActor() {
        if (tileWorkerJob?.isActive == true) return
        tileWorkerStartCount += 1L
        tileWorkerJob = viewModelScope.launch {
            for (ignored in tileWakeSignal) {
                if (closed) break
                try {
                    drainTileQueue()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Worker survival: a single unexpected drain failure must
                    // never kill the tile pipeline permanently. OOM is already
                    // contained at the bitmap boundary (BitmapAllocationGuard);
                    // this catches only ordinary exceptions and keeps the loop.
                    Log.e(ZOOM_LOG_TAG, "tile drain failed; continuing", e)
                }
            }
        }
    }

    private suspend fun drainTileQueue() {
        tileWorkQueue.runWorker(
            renderFn = { request, pageSize ->
                when (val result = renderer.renderTile(request, pageSize)) {
                    is PdfPageRenderer.TileRenderResult.Success ->
                        TileWorkQueue.RenderOutcome.Success(result.image, result.byteSize)
                    PdfPageRenderer.TileRenderResult.Closed ->
                        TileWorkQueue.RenderOutcome.Closed
                    PdfPageRenderer.TileRenderResult.InvalidPage ->
                        TileWorkQueue.RenderOutcome.InvalidPage
                    PdfPageRenderer.TileRenderResult.OutOfMemory -> {
                        // Bounded OOM recovery: free half the tile cache so the
                        // next request has headroom; this tile fails explicitly.
                        Log.w(ZOOM_LOG_TAG, "TILE_OOM: bitmap allocation failed, trimming cache")
                        tileCache.trimTo(tileCache.bytes / 2)
                        TileWorkQueue.RenderOutcome.Failed("渲染内存不足")
                    }
                    is PdfPageRenderer.TileRenderResult.Failed ->
                        TileWorkQueue.RenderOutcome.Failed(result.message)
                }
            },
            onTileReady = { entry ->
                val current = _uiState.value as? PdfViewerUiState.Ready
                if (current != null && TileRenderPipelinePolicy.swapAllowed(entry.interactionPhaseAtRequest)) {
                    if (current.readingMode == PdfReadingMode.HORIZONTAL_PAGED) {
                        HorizontalRealPathTelemetry.onTileReady()
                    }
                    val evicted = tileCache.put(
                        entry,
                        tileWorkQueue.activeKeys() + _tileOverlaySnapshot.value.entries.keys,
                    )
                    pendingTileSwaps[entry.key] = entry
                    scheduleTileOverlayPublish()
                    if (isDebuggable) {
                        Log.d(
                            ZOOM_LOG_TAG,
                            "TILE_READY tileKey=${entry.key} snapshotSequence=${entry.snapshotSequence} " +
                                "targetTime=${entry.targetedAtNanos} requestTime=${entry.requestTimeNanos} " +
                                "renderStartTime=${entry.renderStartNanos} readyTime=${entry.renderedAtNanos} " +
                                "phase=${entry.interactionPhaseAtRequest} byteSize=${entry.byteSize}",
                        )
                        Log.d(ZOOM_LOG_TAG, "TILE_CACHE hit=false miss=true eviction=${evicted.size} bytes=${tileCache.bytes}")
                    }
                }
            },
            onTileDropped = { key, reason ->
                if (isDebuggable) {
                    Log.d(
                        ZOOM_LOG_TAG,
                        "CURRENT_TILE_LIFECYCLE key=$key dropReason=${reason.name} " +
                            "phaseAtSwap=NONE activeGeometry=${tileWorkQueue.activeKeys().map { it.geometryGeneration }.distinct()}",
                    )
                }
            },
            isReady = { _uiState.value is PdfViewerUiState.Ready },
        )
    }

    /** Publish all tiles completed in one display interval as one overlay-only snapshot. */
    private fun scheduleTileOverlayPublish() {
        if (tilePublishJob?.isActive == true) return
        tilePublishJob = viewModelScope.launch {
            delay(TileRenderPipelinePolicy.TILE_PUBLISH_BATCH_MS)
            val swapNanos = System.nanoTime()
            val batch = pendingTileSwaps.values.toList()
            pendingTileSwaps.clear()
            tileOverlaySequence += 1L
            _tileOverlaySnapshot.value = TileOverlaySnapshot(
                sequence = tileOverlaySequence,
                entries = tileCache.snapshot(),
                publishedAtNanos = swapNanos,
            )
            if ((_uiState.value as? PdfViewerUiState.Ready)?.readingMode == PdfReadingMode.HORIZONTAL_PAGED) {
                HorizontalRealPathTelemetry.onTileSwap(batch.size)
            }
            val readyKeys = _tileOverlaySnapshot.value.entries.keys
            val coverage = TileRenderPipelinePolicy.coverage(latestVisibleTileKeys, readyKeys)
            latestTilePlan?.let { tileHealthTracker.onViewport(it.snapshot, coverage) }
            batch.forEach { entry ->
                val latencyMs = ((swapNanos - entry.requestTimeNanos).coerceAtLeast(0L) / 1_000_000L)
                val centerLatency = latencyMs.takeIf {
                    entry.priorityAtRequest == com.markleaf.app.pdf.TilePriority.FOCUS ||
                        entry.priorityAtRequest == com.markleaf.app.pdf.TilePriority.CENTER
                }
                tileHealthTracker.onSwap(swapNanos, centerLatency)
                if (isDebuggable) {
                    Log.d(
                        ZOOM_LOG_TAG,
                        "TILE_SWAP tileKey=${entry.key} snapshotSequence=${entry.snapshotSequence} " +
                            "swapTime=$swapNanos requestToSwapMs=$latencyMs " +
                            "phaseAtSwap=${entry.interactionPhaseAtRequest}",
                    )
                    Log.d(
                        ZOOM_LOG_TAG,
                        "CURRENT_TILE_LIFECYCLE key=${entry.key} targetTime=${entry.targetedAtNanos} " +
                            "requestTime=${entry.requestTimeNanos} renderStartTime=${entry.renderStartNanos} " +
                            "readyTime=${entry.renderedAtNanos} swapTime=$swapNanos dropReason=NONE " +
                            "phaseAtSwap=${entry.interactionPhaseAtRequest}",
                    )
                }
            }
            if (isDebuggable) logGestureRenderHealth(swapNanos)
        }
    }

    private fun logGestureRenderHealth(nowNanos: Long) {
        if (nowNanos - lastTileHealthLogNanos < TileRenderPipelinePolicy.HEALTH_WINDOW_NANOS) return
        lastTileHealthLogNanos = nowNanos
        val health = tileHealthTracker.snapshot(
            nowNanos = nowNanos,
            plannerActive = latestTilePlan != null,
            rendererActive = tileWorkQueue.isRunning(),
        )
        Log.d(
            ZOOM_LOG_TAG,
            "GESTURE_RENDER_HEALTH pointerDown=${health.pointerDown} plannerActive=${health.plannerActive} " +
                "rendererActive=${health.rendererActive} requestsLast500ms=${health.requestsLast500ms} " +
                "swapsLast500ms=${health.swapsLast500ms} currentCoverage=${health.currentCoverage} " +
                "centerP50Ms=${health.centerLatencyP50Ms} centerP95Ms=${health.centerLatencyP95Ms} " +
                "workerStarts=$tileWorkerStartCount",
        )
    }

    private fun commitStableZoom(
        scale: Float,
        zoomTargetPage: Int?,
        continuousState: ContinuousDocumentState?,
    ) {
        val clamped = ZoomPanPhysics.clampScale(scale, ZoomPanPhysicsProfile.ScaleRange)
        if (isDebuggable) Log.d(ZOOM_LOG_TAG, "commitZoom old=$zoomScale new=$clamped reason=userCommit page=${(_uiState.value as? PdfViewerUiState.Ready)?.pageIndex} zoomTarget=$zoomTargetPage mode=$readingMode t=${System.currentTimeMillis()}")
        zoomScale = clamped
        val nextTier = if (readingMode == PdfReadingMode.VERTICAL_CONTINUOUS) {
            ContinuousQualityHysteresis.resolve(clamped, qualityTier)
        } else {
            PdfRenderQuality.resolveTier(clamped, qualityTier)
        }
        qualityTier = nextTier
        val ready = _uiState.value as? PdfViewerUiState.Ready ?: return
        _uiState.value = if (
            ready.readingMode == PdfReadingMode.VERTICAL_CONTINUOUS &&
            pendingContinuousCachePublish
        ) {
            pendingContinuousCachePublish = false
            if (isDebuggable) pendingContinuousPages.forEach { page ->
                val entry = pageCache.get(page)
                Log.d(
                    ZOOM_LOG_TAG,
                    "BITMAP_SWAP interactionId=$continuousInteractionId pageIndex=$page " +
                        "bitmapGeneration=${entry?.generation ?: -1L} tier=${entry?.tier ?: nextTier} " +
                        "timestamp=${System.currentTimeMillis()}",
                )
            }
            pendingContinuousPages.clear()
            publishCache(ready, zoomScale = clamped, qualityTier = nextTier).copy(
                continuousDocumentState = continuousState ?: ready.continuousDocumentState,
            )
        } else {
            ReadyBase(
                ready,
                zoomScale = clamped,
                qualityTier = nextTier,
                continuousDocumentState = continuousState ?: ready.continuousDocumentState,
            )
        }
        when (ready.readingMode) {
            PdfReadingMode.HORIZONTAL_PAGED -> {
                zoomSettleJob?.cancel()
                zoomSettleJob = viewModelScope.launch {
                    delay(PdfRenderQuality.ZOOM_SETTLE_DEBOUNCE_MS)
                    if (!suspendPageSettled) requestRender(ready.pageIndex)
                }
            }
            PdfReadingMode.VERTICAL_CONTINUOUS -> {
                // R7.1: the viewport emits one final IDLE snapshot after transaction
                // settle; that single path owns preview/HIGH scheduling.
            }
        }
    }

    fun onZoomScaleSettled(scale: Float, zoomTargetPage: Int? = null) = commitZoom(scale, zoomTargetPage)

    /** @deprecated Prefer [onZoomScaleSettled]. */
    fun onZoomScaleChange(scale: Float) = onZoomScaleSettled(scale)

    fun setZoomGestureActive(active: Boolean) {
        val wasActive = suspendPageSettled
        suspendPageSettled = active
        if (active) {
            if (!wasActive) continuousInteractionId += 1L
            // Pinch active: no PdfRenderer, cancel pending upgrades, keep all bitmaps.
            zoomSettleJob?.cancel()
            primaryUpgradeJob?.cancel()
            // Cancel in-flight HIGH jobs only (not preview fills).
            cancelNonPreviewJobs(keepPreview = true)
        } else if (wasActive && pendingContinuousCachePublish) {
            // Cancellation/dispose must release last-good results without starting work.
            val ready = _uiState.value as? PdfViewerUiState.Ready
            pendingContinuousCachePublish = false
            if (ready != null) {
                _uiState.value = publishCache(ready)
            }
            pendingContinuousPages.clear()
        }
    }

    /**
     * goToPreviousPage / goToNextPage: manual paging in any mode.
     * (The legacy fast-scroll drag funnel was deleted in the R25 closure —
     * vertical continuous scrolling owns the viewport directly.)
     */
    fun goToPreviousPage() {
        val ready = _uiState.value as? PdfViewerUiState.Ready ?: return
        if (!ready.canGoPrevious) return
        goToPage(ready.pageIndex - 1)
    }

    fun goToNextPage() {
        val ready = _uiState.value as? PdfViewerUiState.Ready ?: return
        if (!ready.canGoNext) return
        goToPage(ready.pageIndex + 1)
    }

    /**
     * Jump to 0-based [pageIndex] and render.
     * User-facing jump dialog converts 1-based → 0-based before calling.
     */
    fun goToPage(pageIndex: Int) {
        val ready = _uiState.value as? PdfViewerUiState.Ready ?: return
        val target = PdfReadingNavigation.clampPageIndex(pageIndex, ready.pageCount)
        if (target == ready.pageIndex &&
            (
                (ready.readingMode == PdfReadingMode.HORIZONTAL_PAGED &&
                    ready.pageImage != null && !ready.isRendering) ||
                    (ready.readingMode == PdfReadingMode.VERTICAL_CONTINUOUS &&
                        ready.pageBitmaps.containsKey(target))
                )
        ) {
            return
        }
        when (ready.readingMode) {
            PdfReadingMode.HORIZONTAL_PAGED -> {
                _uiState.value = ReadyBase(
                    ready,
                    pageIndex = target,
                    isRendering = true,
                    renderError = null,
                    pageImage = ready.pageBitmaps[target],
                )
                recordStableReadingPosition(
                    _uiState.value as PdfViewerUiState.Ready,
                    target,
                    0f,
                    ReadingPositionSource.EXTERNAL_NAVIGATION,
                )
                requestRender(target)
                // Explicit navigation (tap/input/outline/thumb) is a one-shot Pager request.
                emitPageJump(target, "horizontalExternal")
            }
            PdfReadingMode.VERTICAL_CONTINUOUS -> {
                queueContinuousRestore(
                    ready = ready,
                    pageIndex = target,
                    fraction = 0f,
                    source = ReadingPositionSource.EXTERNAL_NAVIGATION,
                )
            }
        }
    }

    fun setReadingMode(mode: PdfReadingMode) {
        val ready = _uiState.value as? PdfViewerUiState.Ready ?: return
        if (ready.readingMode == mode) return
        val lastStable = continuousEntryRestore.snapshot().lastStableReadingPosition
            ?: ReadingPosition(
                pageIndex = ready.pageIndex,
                intraPageFraction = 0f,
                source = ReadingPositionSource.USER_HORIZONTAL_SETTLED,
                generation = 0L,
            )
        val fallbackSession = PdfViewportSessionState(
            readingMode = ready.readingMode,
            pageIndex = lastStable.pageIndex,
            pageAnchorXFraction = 0.5f,
            pageAnchorYFraction = lastStable.intraPageFraction,
            scale = ready.viewportSessionState?.scale ?: ready.zoomScale,
            generation = System.nanoTime(),
        )
        val stableSession = (
            viewportSessionTracker.latestForMode(ready.readingMode) ?:
                viewportSessionTracker.stable() ?:
                ready.viewportSessionState ?:
                fallbackSession
            )
            .normalized(ready.pageCount, lastStable.pageIndex) ?: fallbackSession
        val modeSession = stableSession.toCanonicalAnchor().toViewportSession(
            mode = mode,
            nextGeneration = System.nanoTime(),
        )
        // Product decision (R13): horizontal paging defaults to FitPage/Contain.
        // The FIRST entry into horizontal mode (no previously committed
        // horizontal session) starts at scale 1f = contain-fit for the current
        // page, regardless of any zoom borrowed from the continuous session.
        // Returning to horizontal after the user already zoomed there restores
        // that user zoom (the saved horizontal session) untouched.
        val effectiveModeSession = if (
            mode == PdfReadingMode.HORIZONTAL_PAGED &&
            viewportSessionTracker.latestForMode(PdfReadingMode.HORIZONTAL_PAGED) == null
        ) {
            modeSession.copy(scale = 1f)
        } else {
            modeSession
        }
        val page = PdfReadingNavigation.pageAfterModeSwitch(effectiveModeSession.pageIndex, ready.pageCount)
        if (isDebuggable) {
            Log.d(
                "MarkLeafH14",
                "H14_MODE_SWITCH_REQUEST from=${ready.readingMode} to=$mode " +
                    "pageBefore=${stableSession.pageIndex} pageAfter=$page " +
                    "scaleBefore=${stableSession.scale} scaleAfter=${effectiveModeSession.scale} " +
                    "anchorX=${effectiveModeSession.pageAnchorXFraction} " +
                    "anchorY=${effectiveModeSession.pageAnchorYFraction} generation=${effectiveModeSession.generation}",
            )
        }
        val modeTarget = if (mode == PdfReadingMode.VERTICAL_CONTINUOUS) {
            continuousEntryRestore.requestRestore(
                pageIndex = page,
                intraPageFraction = effectiveModeSession.pageAnchorYFraction,
                source = ReadingPositionSource.MODE_SWITCH_RESTORE,
                pageCount = ready.pageCount,
            )
        } else {
            null
        }
        readingMode = mode
        readingPrefs.setMode(documentId, mode)
        viewportSessionTracker.commit(modeSession)
        when (mode) {
            PdfReadingMode.HORIZONTAL_PAGED -> {
                // The target adapter receives the same document-session scale and page anchor.
                zoomScale = modeSession.scale
                qualityTier = ContinuousQualityHysteresis.resolve(zoomScale, qualityTier)
                _uiState.value = ReadyBase(
                    ready,
                    pageIndex = page,
                    readingMode = mode,
                    isRendering = true,
                    pageImage = ready.pageBitmaps[page] ?: ready.pageImage,
                    zoomScale = zoomScale,
                    qualityTier = qualityTier,
                    viewportSessionState = modeSession,
                )
                requestRender(page)
            }
            PdfReadingMode.VERTICAL_CONTINUOUS -> {
                val restored = continuousDocumentSession.commit(
                    continuousDocumentSession.stableState.copy(scale = modeSession.scale),
                    page,
                )
                val restoredPage = modeTarget?.pageIndex ?: page
                zoomScale = modeSession.scale
                qualityTier = ContinuousQualityHysteresis.resolve(zoomScale, qualityTier)
                continuousGeometryReady = false
                _pageJump.value = null
                _uiState.value = ReadyBase(
                    ready,
                    pageIndex = restoredPage,
                    readingMode = mode,
                    isRendering = false,
                    pageBitmaps = ready.pageBitmaps,
                    pageAspects = ready.pageAspects,
                    zoomScale = zoomScale,
                    qualityTier = qualityTier,
                    continuousDocumentState = restored,
                    viewportSessionState = modeSession,
                    continuousEntryState = continuousEntryRestore.snapshot(),
                )
                ensurePreviewAround(restoredPage)
            }
        }
    }

    fun setShowPageButtons(show: Boolean) {
        val ready = _uiState.value as? PdfViewerUiState.Ready ?: return
        if (ready.showPageButtons == show) return
        showPageButtons = show
        readingPrefs.setShowPageButtons(documentId, show)
        _uiState.value = ReadyBase(ready, showPageButtons = show)
    }

    /**
     * Vertical list settled on [pageIndex] (visible center).
     * Scroll path: **preview only**. Hi-res is scheduled after idle debounce.
     * Never emits [PageJumpRequest]. Never clears bitmaps.
     */
    fun onVerticalPageSettled(position: ReadingPosition) {
        if (suspendPageSettled) return
        val ready = _uiState.value as? PdfViewerUiState.Ready ?: return
        if (ready.readingMode != PdfReadingMode.VERTICAL_CONTINUOUS) return

        // ── Belated escape hatch ────────────────────────────────────
        // If the viewport reports a user settle while we are still in a
        // pre-READY phase (the onViewportChanged drag path did not catch it,
        // or geometry change re-triggered a stale target), cancel the pending
        // restore now.  Do NOT persist an INITIAL_PLACEHOLDER — only a real
        // user-driven or viewport-center position may trigger the escape.
        if (ready.continuousEntryState.phase != com.markleaf.app.pdf.ContinuousEntryPhase.READY) {
            if (position.source == ReadingPositionSource.INITIAL_PLACEHOLDER) {
                if (isDebuggable) {
                    Log.d(ZOOM_LOG_TAG,
                        "VIEWPORT_PAGE_REJECT reason=placeholder phase=${ready.continuousEntryState.phase} " +
                        "page=${position.pageIndex}")
                }
                return
            }
            if (continuousEntryRestore.cancelRestore()) {
                if (isDebuggable) {
                    Log.d(ZOOM_LOG_TAG,
                        "RESTORE_CANCELLED_ON_SETTLE oldPhase=${ready.continuousEntryState.phase} " +
                        "settledPage=${position.pageIndex} " +
                        "consumedGeneration=${continuousEntryRestore.getConsumedGeneration()}")
                }
                val escaped = continuousEntryRestore.snapshot()
                _uiState.value = ReadyBase(ready, pageIndex = position.pageIndex,
                    continuousEntryState = escaped)
            }
            // Fall through: the state machine is now READY; track normally.
        }

        // Re-read after potential escape-hatch state change.
        val current = _uiState.value as? PdfViewerUiState.Ready ?: return
        if (current.readingMode != PdfReadingMode.VERTICAL_CONTINUOUS) return
        if (current.continuousEntryState.phase != com.markleaf.app.pdf.ContinuousEntryPhase.READY) return

        val target = PdfReadingNavigation.clampPageIndex(position.pageIndex, current.pageCount)
        if (target != current.pageIndex) {
            if (isDebuggable) {
                Log.d(ZOOM_LOG_TAG,
                    "VIEWPORT_PAGE_ACCEPT old=${current.pageIndex} new=$target " +
                    "fraction=${position.intraPageFraction} source=${position.source}")
            }
            _uiState.value = ReadyBase(
                current,
                pageIndex = target,
                pageImage = current.pageImage,
                isRendering = false,
                renderError = null,
                pageBitmaps = current.pageBitmaps,
                pageAspects = current.pageAspects,
            )
        }
        recordStableReadingPosition(
            _uiState.value as PdfViewerUiState.Ready,
            target,
            position.intraPageFraction,
            ReadingPositionSource.USER_CONTINUOUS_SCROLL,
        )
        // R7 V2 render scheduling is owned by onContinuousViewportChanged. This
        // callback updates metadata/persistence only and never drives viewport.
    }

    /** Pager settle feedback: update VM/cache once, never emit a reverse Pager request. */
    fun onHorizontalPageSettled(pageIndex: Int) {
        val ready = _uiState.value as? PdfViewerUiState.Ready ?: return
        if (ready.readingMode != PdfReadingMode.HORIZONTAL_PAGED) return
        val target = PdfReadingNavigation.clampPageIndex(pageIndex, ready.pageCount)
        if (target == ready.pageIndex) return
        _uiState.value = ReadyBase(
            ready,
            pageIndex = target,
            pageImage = ready.pageBitmaps[target],
            isRendering = ready.pageBitmaps[target] == null,
            renderError = null,
        )
        recordStableReadingPosition(
            _uiState.value as PdfViewerUiState.Ready,
            target,
            0f,
            ReadingPositionSource.USER_HORIZONTAL_SETTLED,
        )
        requestRender(target)
    }

    /**
     * Public entry used by UI placeholders. Continuous mode = **preview only**.
     * Does not upgrade to high (avoids sequential hi-res while flinging).
     */
    fun ensurePagesAround(center: Int) {
        val ready = _uiState.value as? PdfViewerUiState.Ready ?: return
        if (ready.readingMode == PdfReadingMode.HORIZONTAL_PAGED) {
            ensureHorizontalStripAround(center, ready)
        } else {
            ensurePreviewAround(center)
        }
    }

    /**
     * Keep exactly the stable PREVIOUS/CURRENT/NEXT base window for horizontal mode.
     * A page that just left CURRENT remains protected as PREVIOUS until the completed commit rotates
     * the strip; only then may the old page outside the three-slot window be recycled.
     */
    private fun ensureHorizontalStripAround(center: Int, ready: PdfViewerUiState.Ready) {
        val current = PdfReadingNavigation.clampPageIndex(center, ready.pageCount)
        val keep = ThreePageStripState(current, ready.pageCount.coerceAtLeast(1))
            .slots()
            .mapNotNull { it.pageIndex }
            .toSet()
        continuousProtectedPages = keep

        cacheJobs.keys.toList().forEach { page ->
            if (page !in keep) {
                cacheJobs.remove(page)?.cancel()
                cacheJobKeys.remove(page)
            }
        }
        val beforeKeys = pageCache.keys()
        pageCache.retainOnly(keep)
        if (beforeKeys.any { it !in keep }) {
            val latest = _uiState.value as? PdfViewerUiState.Ready ?: ready
            _uiState.value = publishCache(latest, pageIndex = latest.pageIndex)
        }

        val order = buildList {
            add(current)
            if (current + 1 in keep) add(current + 1)
            if (current - 1 in keep) add(current - 1)
        }
        order.forEach { page ->
            requestPageRender(
                pageIndex = page,
                forceTier = QualityTier.SCROLL_PREVIEW,
                allowUpgrade = false,
                allowDowngrade = false,
                primaryIndex = current,
            )
        }
    }

    /**
     * Lazy continuous path: current ±1 **PREVIEW** only.
     * - Never starts HIGH/MEDIUM here
     * - Never clears a page that still has a bitmap until it leaves the window
     * - Cancels off-window jobs (including stale HIGH)
     * - Demotes previous primary HIGH via preview replace (old image stays until done)
     */
    private fun ensurePreviewAround(center: Int) {
        if (suspendPageSettled) return
        val ready = _uiState.value as? PdfViewerUiState.Ready ?: return
        if (ready.readingMode != PdfReadingMode.VERTICAL_CONTINUOUS &&
            readingMode != PdfReadingMode.VERTICAL_CONTINUOUS
        ) {
            // Still allow when state lagging during mode switch.
        }
        val c = PdfReadingNavigation.clampPageIndex(center, ready.pageCount)
        val snapshot = ContinuousViewportSnapshot(
            visiblePages = visibleContinuousPages,
            primaryPage = c,
            direction = continuousDirection,
            phase = continuousPhase,
        )
        val keep = ContinuousViewportPolicy.boundedPreviewPages(snapshot, ready.pageCount)
        continuousProtectedPages = keep

        // Cancel jobs outside the lazy window.
        cacheJobs.keys.toList().forEach { page ->
            if (page !in keep) {
                cacheJobs.remove(page)?.cancel()
                cacheJobKeys.remove(page)
            }
        }
        // Drop off-window bitmaps only (never wipe in-window while re-rendering).
        val beforeKeys = pageCache.keys()
        pageCache.retainOnly(keep)
        if (beforeKeys.any { it !in keep }) {
            val cur = _uiState.value as? PdfViewerUiState.Ready ?: ready
            _uiState.value = publishCache(cur, pageIndex = cur.pageIndex)
        }

        // Demote non-primary HIGH: request preview replace (keep showing old until done).
        for (highPage in pageCache.highPages()) {
            if (highPage != c && highPage in keep) {
                requestPageRender(
                    pageIndex = highPage,
                    forceTier = QualityTier.PREVIEW,
                    allowUpgrade = false,
                    allowDowngrade = true,
                    primaryIndex = c,
                )
            } else if (highPage != c && highPage !in keep) {
                // Already removed by retainOnly; cancel any job.
                cacheJobs.remove(highPage)?.cancel()
                cacheJobKeys.remove(highPage)
            }
        }

        // Visible first, then nearest pages in the current direction.
        for (i in ContinuousViewportPolicy.previewRequestOrder(snapshot, ready.pageCount)) {
            requestPageRender(
                pageIndex = i,
                forceTier = QualityTier.SCROLL_PREVIEW,
                allowUpgrade = false,
                allowDowngrade = false,
                primaryIndex = c,
            )
        }
    }

    private fun ensureReadablePreviews(snapshot: ContinuousViewportSnapshot) {
        if (snapshot.phase != ContinuousScrollPhase.IDLE) return
        snapshot.visiblePages.forEach { page ->
            val scrollJob = cacheJobs[page]
            val scrollKey = cacheJobKeys[page]
            if (scrollJob?.isActive == true && scrollKey?.tier == QualityTier.SCROLL_PREVIEW) {
                viewModelScope.launch {
                    scrollJob.join()
                    if (continuousPhase == ContinuousScrollPhase.IDLE &&
                        page in visibleContinuousPages
                    ) {
                        requestPageRender(
                            page, QualityTier.PREVIEW, allowUpgrade = true,
                            primaryIndex = continuousPrimaryPage,
                        )
                    }
                }
            } else {
                requestPageRender(
                    pageIndex = page,
                    forceTier = QualityTier.PREVIEW,
                    allowUpgrade = true,
                    primaryIndex = snapshot.primaryPage,
                )
            }
        }
    }

    private fun cancelNonScrollPreviewJobs() {
        cacheJobKeys.toMap().forEach { (page, key) ->
            if (key.tier != QualityTier.SCROLL_PREVIEW) {
                cacheJobs.remove(page)?.cancel()
                cacheJobKeys.remove(page)
            }
        }
    }

    /**
     * After [PdfRenderQuality.SCROLL_IDLE_UPGRADE_MS], upgrade **only** [primary] if zoomed.
     * Scroll again / pinch / fast-scroll cancels this — so pages passed while flinging
     * are never sequentially upgraded to high.
     */
    private fun schedulePrimaryHiRes(primary: Int, reason: String) {
        primaryUpgradeJob?.cancel()
        if (suspendPageSettled || continuousPhase != ContinuousScrollPhase.IDLE) return
        val ready = _uiState.value as? PdfViewerUiState.Ready ?: return
        if (ready.readingMode != PdfReadingMode.VERTICAL_CONTINUOUS) return
        if (ready.continuousEntryState.phase != com.markleaf.app.pdf.ContinuousEntryPhase.READY) return
        val p = PdfReadingNavigation.clampPageIndex(primary, ready.pageCount)
        if (isDebuggable) {
            val desiredTier = ContinuousQualityHysteresis.resolve(ready.zoomScale, qualityTier)
            val shown = pageCache.get(p)
            Log.d(ZOOM_LOG_TAG, "ZOOM_QUALITY_TARGET page=$p scale=${ready.zoomScale} viewportWidthPx=$viewportWidthPx " +
                "desiredWidthBucket=${PdfRenderQuality.targetWidthPx(viewportWidthPx, desiredTier)} " +
                "currentDisplayedTier=${shown?.tier} currentWidthBucket=${shown?.widthBucket} reason=$reason " +
                "timestamp=${System.currentTimeMillis()}")
        }
        primaryUpgradeJob = viewModelScope.launch {
            delay(PdfRenderQuality.SCROLL_IDLE_UPGRADE_MS)
            // Preserve the readable/scroll fallback: never cancel a lower-tier render
            // for the primary just to start HIGH. Wait for it to complete atomically.
            val lowerTierJob = cacheJobs[p]
            val lowerTierKey = cacheJobKeys[p]
            if (lowerTierJob?.isActive == true && lowerTierKey?.tier != QualityTier.HIGH) {
                lowerTierJob.join()
            }
            if (suspendPageSettled) return@launch
            val cur = _uiState.value as? PdfViewerUiState.Ready ?: return@launch
            if (cur.readingMode != PdfReadingMode.VERTICAL_CONTINUOUS) return@launch
            // Primary must still match settled page (user did not move on).
            if (cur.pageIndex != p) return@launch
            if (continuousPrimaryPage != p || continuousPhase != ContinuousScrollPhase.IDLE) return@launch
            val tier = ContinuousQualityHysteresis.resolve(cur.zoomScale, qualityTier)
            qualityTier = tier
            lastHiResPrimary = p
            if (tier != QualityTier.HIGH) {
                if (pageCache.get(p)?.tier == QualityTier.HIGH) {
                    requestPageRender(
                        pageIndex = p,
                        forceTier = QualityTier.PREVIEW,
                        allowUpgrade = false,
                        allowDowngrade = true,
                        primaryIndex = p,
                    )
                }
                return@launch
            }
            // Cancel HIGH jobs for any other page first.
            cancelHighJobsExcept(p)
            if (isDebuggable) Log.d(ZOOM_LOG_TAG, "ZOOM_QUALITY_REQUEST page=$p scale=${cur.zoomScale} " +
                "desiredWidthBucket=${PdfRenderQuality.targetWidthPx(viewportWidthPx, tier)} timestamp=${System.currentTimeMillis()}")
            requestPageRender(
                pageIndex = p,
                forceTier = tier,
                allowUpgrade = true,
                allowDowngrade = false,
                primaryIndex = p,
            )
        }
    }

    private fun cancelNonPreviewJobs(keepPreview: Boolean) {
        // R7.1: keep light preview fills that may complete into pending; cancel costly
        // MEDIUM/HIGH work. Cached/displayed bitmaps are never cleared here.
        cacheJobKeys.toMap().forEach { (page, key) ->
            val shouldCancel = !keepPreview ||
                key.tier == QualityTier.MEDIUM || key.tier == QualityTier.HIGH
            if (shouldCancel) {
                cacheJobs.remove(page)?.cancel()
                cacheJobKeys.remove(page)
            }
        }
        primaryUpgradeJob?.cancel()
        zoomSettleJob?.cancel()
    }

    private fun cancelHighJobsExcept(primary: Int) {
        // Demote path handles non-primary HIGH; just cancel their jobs if any.
        cacheJobs.keys.toList().forEach { page ->
            if (page != primary) {
                val tier = pageCache.get(page)?.tier
                if (tier != null && tier.ordinal > QualityTier.PREVIEW.ordinal) {
                    cacheJobs.remove(page)?.cancel()
                    cacheJobKeys.remove(page)
                }
            }
        }
    }

    /** Jump using 1-based page number from UI. Returns false if out of range / invalid. */
    fun jumpToHumanPage(humanPage: Int): Boolean {
        val ready = _uiState.value as? PdfViewerUiState.Ready ?: return false
        if (ready.pageCount <= 0) return false
        if (humanPage < 1 || humanPage > ready.pageCount) return false
        goToPage(humanPage - 1)
        return true
    }

    /**
     * Bind current PDF page to textbook print page [printPageNumber] (1-based).
     */
    fun setPrintPageForCurrent(printPageNumber: Int) {
        val ready = _uiState.value as? PdfViewerUiState.Ready ?: return
        if (printPageNumber < 1) return
        val pdfIndex = ready.pageIndex
        viewModelScope.launch {
            try {
                repository.upsertPrintPageAnchor(
                    documentId = documentId,
                    pdfPageIndex = pdfIndex,
                    printPageNumber = printPageNumber,
                )
                reloadAnchorsIntoState()
            } catch (_: Exception) {
                // ignore persist errors
            }
        }
    }

    fun clearPrintPageForCurrent() {
        val ready = _uiState.value as? PdfViewerUiState.Ready ?: return
        viewModelScope.launch {
            try {
                repository.clearPrintPageAnchor(documentId, ready.pageIndex)
                reloadAnchorsIntoState()
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    /**
     * Open the secondary thumbnail renderer for this document (idempotent).
     * Safe to call when the sheet is shown.
     */
    fun prepareThumbnails() {
        val uri = documentUri ?: return
        viewModelScope.launch {
            if (!thumbnailLoader.isOpen) {
                thumbnailLoader.open(uri)
            }
        }
    }

    fun saveOutlineNode(
        id: String?,
        parentId: String?,
        type: TextbookOutlineNodeType,
        title: String,
        startPageIndex: Int,
        endPageIndex: Int,
        sortOrder: Int = 0,
    ) {
        val ready = _uiState.value as? PdfViewerUiState.Ready ?: return
        viewModelScope.launch {
            try {
                repository.upsertOutlineNode(
                    documentId = documentId,
                    id = id,
                    parentId = parentId,
                    type = type,
                    title = title,
                    startPageIndex = startPageIndex,
                    endPageIndex = endPageIndex,
                    sortOrder = sortOrder,
                    pageCount = ready.pageCount,
                )
                reloadOutlineIntoState()
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    fun deleteOutlineNode(nodeId: String) {
        viewModelScope.launch {
            try {
                repository.deleteOutlineNode(documentId, nodeId)
                reloadOutlineIntoState()
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    fun dismissEmbeddedOutlineOffer() {
        _embeddedOffer.value = null
    }

    fun consumeImportMessage() {
        _importMessage.value = null
    }

    /** Open export sheet for a catalog node (from export picker, not per-row outline icons). */
    fun beginExportNode(node: TextbookOutlineNodeEntity) {
        val ready = _uiState.value as? PdfViewerUiState.Ready ?: return
        if (isExportRunning()) {
            // Never stack a new export flow over an in-flight progress dialog.
            _importMessage.value = "正在导出，请等待完成或先取消"
            return
        }
        clearTerminalExportDialogIfAny()
        val plan = ExportRangeResolver.plan(
            node = node,
            nodes = outlineNodes,
            documentPageCount = ready.pageCount,
            printAnchors = anchors,
        )
        _exportPlan.value = plan
        pendingExportPlan = plan
    }

    private fun isExportRunning(): Boolean {
        val st = _exportState.value
        return st is ChapterExportState.Preparing ||
            st is ChapterExportState.CopyingPages ||
            st is ChapterExportState.BuildingMetadata ||
            st is ChapterExportState.WritingArchive
    }

    /**
     * A terminal export dialog (Completed/Failed/Cancelled) must never stay
     * on screen over a NEW export sheet: both dialogs would render at once
     * and the stale one hides the fresh flow, making a second export look
     * like it "just went back to reading".
     */
    private fun clearTerminalExportDialogIfAny() {
        val st = _exportState.value
        if (st is ChapterExportState.Completed ||
            st is ChapterExportState.Failed ||
            st is ChapterExportState.Cancelled
        ) {
            _exportState.value = null
        }
    }

    /**
     * Single export entry: default to deepest outline node covering the current page,
     * otherwise the first root node. No outline nodes → snackbar only.
     */
    fun beginExportCurrent() {
        val ready = _uiState.value as? PdfViewerUiState.Ready ?: return
        if (outlineNodes.isEmpty()) {
            _importMessage.value = "请先添加或导入目录，再导出章节"
            return
        }
        val node = TextbookOutlineResolver.deepestContaining(ready.pageIndex, outlineNodes)
            ?: outlineNodes.firstOrNull { it.parentId == null }
            ?: outlineNodes.first()
        beginExportNode(node)
    }

    fun dismissExportPlan() {
        _exportPlan.value = null
    }

    fun dismissExportState() {
        _exportState.value = null
        pendingExportPlan = null
    }

    fun cancelExport() {
        // Bump the generation FIRST: the cancelled job's late onState
        // callbacks (it may still run inside its IO block) must not touch
        // the UI state of any subsequent export. The job-local cancellation
        // token stops THIS run only — a newer export owns its own token.
        exportStateGate.cancel()
        activeExportCancellation?.cancel()
        exportJob?.cancel()
        _exportState.value = ChapterExportState.Cancelled
    }

    /**
     * After user confirms range + format, prepare SAF suggested name.
     * Call [runExportToUri] when CreateDocument returns.
     */
    fun confirmExportRange(
        startIndex0: Int,
        endIndex0: Int,
        format: ChapterExportFormat = ChapterExportFormat.ZIP_PACKAGE,
    ): String? {
        val ready = _uiState.value as? PdfViewerUiState.Ready ?: return null
        val base = pendingExportPlan ?: _exportPlan.value ?: return null
        val err = ExportRangeResolver.validateRange(startIndex0, endIndex0, ready.pageCount)
        if (err != null) {
            _importMessage.value = err
            return null
        }
        val plan = ExportRangeResolver.plan(
            node = base.node,
            nodes = outlineNodes,
            documentPageCount = ready.pageCount,
            printAnchors = anchors,
            overrideStartIndex = startIndex0,
            overrideEndIndex = endIndex0,
        )
        pendingExportPlan = plan
        pendingExportFormat = format
        _exportPlan.value = null
        val name = ExportRangeResolver.defaultExportName(
            format = format,
            documentDisplayName = ready.document.displayName,
            pathTitles = plan.pathTitles,
        )
        lastExportSuggestedName = name
        return name
    }

    fun suggestedExportFileName(): String =
        lastExportSuggestedName
            ?: ExportRangeResolver.defaultExportName(
                pendingExportFormat,
                "chapter",
                emptyList(),
            )

    fun runExportToUri(outputUri: Uri) {
        val ready = _uiState.value as? PdfViewerUiState.Ready ?: return
        val plan = pendingExportPlan ?: return
        val uriStr = documentUri ?: return
        val format = pendingExportFormat
        exportJob?.cancel()
        // New generation: late callbacks of the previous job (still draining
        // its IO block after cancel) become stale and are dropped by the gate.
        // Each run owns its own cancellation token — the previous run's token
        // is left cancelled, so it can never be re-enabled by this start.
        val generation = exportStateGate.begin()
        val cancellation = ExportCancellationToken()
        activeExportCancellation = cancellation
        exportJob = viewModelScope.launch {
            val onState: (ChapterExportState) -> Unit = { state ->
                if (exportStateGate.isCurrent(generation)) _exportState.value = state
            }
            onState(ChapterExportState.Preparing)
            val result = when (format) {
                ChapterExportFormat.ZIP_PACKAGE -> chapterExporter.export(
                    sourceUri = Uri.parse(uriStr),
                    documentId = documentId,
                    documentDisplayName = ready.document.displayName,
                    plan = plan,
                    allNodes = outlineNodes,
                    printAnchors = anchors,
                    outputUri = outputUri,
                    cancellation = cancellation,
                    onState = onState,
                )
                ChapterExportFormat.PDF_ANNOTATED -> pdfAnnotatedExporter.export(
                    sourceUri = Uri.parse(uriStr),
                    documentDisplayName = ready.document.displayName,
                    plan = plan,
                    outputUri = outputUri,
                    cancellation = cancellation,
                    onState = onState,
                )
            }
            if (exportStateGate.isCurrent(generation)) _exportState.value = result
        }
    }

    fun hasCachedEmbeddedOutline(): Boolean = cachedDrafts.isNotEmpty()

    /** Re-show import dialog from outline sheet if drafts were cached. */
    fun requestEmbeddedImportUi() {
        if (cachedDrafts.isEmpty()) return
        _embeddedOffer.value = EmbeddedOutlineOffer(
            drafts = cachedDrafts,
            hasLocalNodes = outlineNodes.isNotEmpty(),
            alreadyImported = com.markleaf.app.pdf.PdfEmbeddedOutlineImporter
                .isAlreadyImported(cachedDrafts, outlineNodes),
        )
    }

    /**
     * Force re-scan PDF bookmarks with PDFBox (user-triggered).
     * Auto-open path skips when embedded outline already imported.
     */
    fun rescanEmbeddedOutline() {
        val ready = _uiState.value as? PdfViewerUiState.Ready ?: return
        val uri = documentUri ?: ready.document.uri
        viewModelScope.launch {
            try {
                detectEmbeddedOutline(uri = uri, pageCount = ready.pageCount, force = true)
                if (cachedDrafts.isNotEmpty()) {
                    requestEmbeddedImportUi()
                }
            } catch (_: Throwable) {
                _importMessage.value = "重新扫描目录失败"
            }
        }
    }

    fun importEmbeddedOutline(mode: OutlineImportMode) {
        val drafts = cachedDrafts.ifEmpty { _embeddedOffer.value?.drafts.orEmpty() }
        if (drafts.isEmpty()) {
            _embeddedOffer.value = null
            return
        }
        viewModelScope.launch {
            try {
                val written = repository.importEmbeddedOutline(
                    documentId = documentId,
                    drafts = drafts,
                    mode = mode,
                )
                reloadOutlineIntoState()
                _embeddedOffer.value = null
                _importMessage.value = when {
                    written == 0 -> "目录已是最新，未重复导入"
                    mode == OutlineImportMode.REPLACE -> "已替换导入 $written 个目录节点"
                    else -> "已合并导入 $written 个目录节点"
                }
            } catch (_: Exception) {
                _importMessage.value = "导入失败"
            }
        }
    }

    private suspend fun reloadAnchorsIntoState() {
        anchors = repository.listPrintPageAnchors(documentId)
        val ready = _uiState.value as? PdfViewerUiState.Ready ?: return
        _uiState.value = ReadyBase(ready)
    }

    private suspend fun reloadOutlineIntoState() {
        outlineNodes = repository.listOutlineNodes(documentId)
        val ready = _uiState.value as? PdfViewerUiState.Ready ?: return
        _uiState.value = ReadyBase(ready)
    }

    private fun openDocument() {
        viewModelScope.launch {
            _uiState.value = PdfViewerUiState.Loading
            renderer.closeAsync()
            thumbnailLoader.closeAsync()
            anchors = emptyList()
            printSections = emptyList()
            outlineNodes = emptyList()
            documentUri = null
            pageCache.clear()
            tilePublishJob?.cancel(); tilePublishJob = null
            pendingTileSwaps.clear()
            tileWorkQueue.clear()
            tileWorkQueue.updateDocumentGeneration(tileWorkQueue.currentGeneration() + 1L)
            tileCache.clear()
            tileOverlaySequence += 1L
            _tileOverlaySnapshot.value = TileOverlaySnapshot(sequence = tileOverlaySequence)
            latestVisibleTileKeys = emptySet()
            latestTilePlan = null
            tileHealthTracker.reset()
            continuousDocumentSession.reset()
            viewportSessionTracker.seed(null)
            continuousEntryRestore = ContinuousEntryRestoreState()
            continuousGeometryReady = false
            lastPersistedReadingGeneration = -1L
            pendingContinuousCachePublish = false
            pendingContinuousPages.clear()
            continuousInteractionId = 0L
            suspendPageSettled = false
            pageGenerations.clear()
            cacheJobs.values.forEach { it.cancel() }
            cacheJobs.clear()
            cacheJobKeys.clear()
            renderJob?.cancel()
            renderJob = null
            zoomSettleJob?.cancel()
            primaryUpgradeJob?.cancel()
            if (isDebuggable) Log.d(ZOOM_LOG_TAG, "zoomReset old=$zoomScale new=1.0 reason=openDocument page=-1 mode=$readingMode t=${System.currentTimeMillis()}")
            zoomScale = 1f
            qualityTier = QualityTier.PREVIEW
            suspendPageSettled = false
            lastHiResPrimary = -1

            val entity = try {
                repository.getDocumentById(documentId)
            } catch (_: Exception) {
                null
            }

            if (entity == null) {
                _uiState.value = PdfViewerUiState.Error(
                    displayName = null,
                    message = "文件可能已被移动或删除",
                )
                return@launch
            }

            if (entity.type != DocumentType.PDF) {
                _uiState.value = PdfViewerUiState.Error(
                    displayName = entity.displayName,
                    message = "该文档不是 PDF",
                )
                return@launch
            }

            try {
                repository.touchLastOpenedAt(entity.id)
            } catch (_: Exception) {
                // non-fatal
            }

            anchors = try {
                repository.listPrintPageAnchors(documentId)
            } catch (_: Exception) {
                emptyList()
            }
            printSections = PrintPageSectionCatalog.sectionsFor(documentId, entity.displayName)
            outlineNodes = try {
                repository.listOutlineNodes(documentId)
            } catch (_: Exception) {
                emptyList()
            }

            when (val open = renderer.open(entity.uri)) {
                is PdfPageRenderer.OpenResult.Success -> {
                    val pageCount = open.pageCount
                    val restored = PdfReadingNavigation.clampPageIndex(
                        entity.lastScrollIndex,
                        pageCount,
                    )
                    openedDocument = entity.copy(pageCount = pageCount)
                    documentUri = entity.uri
                    readingMode = readingPrefs.getMode(documentId)
                    showPageButtons = readingPrefs.getShowPageButtons(documentId)
                    lastPersistedPage = restored
                    val persistedPosition = ReadingPosition(
                        pageIndex = restored,
                        intraPageFraction = PdfReadingPositionCodec.decodeFraction(entity.lastScrollOffset),
                        // This is known persisted history, not a transient first frame.
                        source = ReadingPositionSource.USER_CONTINUOUS_SCROLL,
                        generation = 0L,
                    )
                    continuousEntryRestore.seedLastStable(persistedPosition, pageCount)
                    val persistedViewportSession = if (
                        entity.pdfViewportSessionVersion != null &&
                        entity.pdfViewportReadingMode != null &&
                        entity.pdfViewportPageIndex != null &&
                        entity.pdfPageAnchorXFraction != null &&
                        entity.pdfPageAnchorYFraction != null &&
                        entity.pdfViewportAnchorXFraction != null &&
                        entity.pdfViewportAnchorYFraction != null &&
                        entity.pdfViewportScale != null &&
                        entity.pdfViewportGeneration != null
                    ) {
                        PdfViewportSessionState(
                            version = entity.pdfViewportSessionVersion,
                            readingMode = PdfReadingMode.fromStorage(entity.pdfViewportReadingMode),
                            pageIndex = entity.pdfViewportPageIndex,
                            pageAnchorXFraction = entity.pdfPageAnchorXFraction,
                            pageAnchorYFraction = entity.pdfPageAnchorYFraction,
                            viewportAnchorXFraction = entity.pdfViewportAnchorXFraction,
                            viewportAnchorYFraction = entity.pdfViewportAnchorYFraction,
                            scale = entity.pdfViewportScale,
                            generation = entity.pdfViewportGeneration,
                        ).normalized(pageCount, restored)
                    } else null
                    viewportSessionTracker.seed(persistedViewportSession)
                    if (readingMode == PdfReadingMode.VERTICAL_CONTINUOUS) {
                        continuousEntryRestore.requestRestore(
                            pageIndex = persistedViewportSession?.pageIndex ?: persistedPosition.pageIndex,
                            intraPageFraction = persistedViewportSession?.pageAnchorYFraction ?: persistedPosition.intraPageFraction,
                            source = if (persistedViewportSession != null) ReadingPositionSource.ZOOM_SESSION_RESTORE else ReadingPositionSource.STARTUP_RESTORE,
                            pageCount = pageCount,
                        )
                    } else {
                        continuousEntryRestore.markReadyWithoutRestore()
                    }
                    _uiState.value = PdfViewerUiState.Ready(
                        document = openedDocument!!,
                        pageIndex = restored,
                        pageCount = pageCount,
                        pageImage = null,
                        isRendering = readingMode == PdfReadingMode.HORIZONTAL_PAGED,
                        printAnchors = anchors,
                        printSections = printSections,
                        outlineNodes = outlineNodes,
                        readingMode = readingMode,
                        showPageButtons = showPageButtons,
                        pageBitmaps = emptyMap(),
                        pageAspects = emptyMap(),
                        zoomScale = zoomScale,
                        continuousDocumentState = continuousDocumentSession.stableState,
                        viewportSessionState = persistedViewportSession,
                        tileDocumentGeneration = tileDocumentGeneration,
                        continuousEntryState = continuousEntryRestore.snapshot(),
                        qualityTier = qualityTier,
                        renderWidthBucket = PdfRenderQuality.targetWidthPx(
                            viewportWidthPx,
                            QualityTier.PREVIEW,
                        ),
                    )
                    when (readingMode) {
                        PdfReadingMode.HORIZONTAL_PAGED -> requestRender(restored)
                        PdfReadingMode.VERTICAL_CONTINUOUS -> {
                            ensurePreviewAround(restored)
                        }
                    }
                    // Warm thumbnail renderer in background (does not pre-render pages).
                    launch {
                        try {
                            thumbnailLoader.open(entity.uri)
                        } catch (_: Throwable) {
                            // non-fatal
                        }
                    }
                    // Detect embedded PDF outline after first paint; never crash the reader.
                    // Skip heavy PDFBox when this document already has imported embedded nodes.
                    launch {
                        try {
                            kotlinx.coroutines.delay(400)
                            detectEmbeddedOutline(
                                uri = entity.uri,
                                pageCount = pageCount,
                                force = false,
                            )
                        } catch (_: Throwable) {
                            cachedDrafts = emptyList()
                            _embeddedOffer.value = null
                        }
                    }
                }
                PdfPageRenderer.OpenResult.NotFound -> {
                    _uiState.value = PdfViewerUiState.Error(
                        displayName = entity.displayName,
                        message = "文件可能已被移动或删除",
                    )
                }
                PdfPageRenderer.OpenResult.PermissionDenied -> {
                    _uiState.value = PdfViewerUiState.Error(
                        displayName = entity.displayName,
                        message = "文件访问权限已失效，请重新导入",
                    )
                }
                is PdfPageRenderer.OpenResult.Failed -> {
                    _uiState.value = PdfViewerUiState.Error(
                        displayName = entity.displayName,
                        message = open.message,
                    )
                }
            }
        }
    }

    private suspend fun detectEmbeddedOutline(
        uri: String,
        pageCount: Int,
        force: Boolean,
    ) {
        try {
            // Lightweight path: already imported embedded bookmarks → skip PDFBox re-load.
            // User can still force via [rescanEmbeddedOutline].
            if (!force) {
                val embeddedCount = outlineNodes.count {
                    it.source == com.markleaf.app.data.local.OutlineNodeSource.PDF_EMBEDDED.name
                }
                if (embeddedCount > 0) {
                    cachedDrafts = emptyList()
                    _embeddedOffer.value = null
                    return
                }
            }

            when (val parsed = PdfEmbeddedOutlineParser.parse(appContext, uri)) {
                is PdfEmbeddedOutlineParser.ParseResult.Success -> {
                    val drafts = try {
                        EmbeddedOutlineBuilder.inferEndPages(parsed.entries, pageCount)
                    } catch (_: Throwable) {
                        emptyList()
                    }
                    cachedDrafts = drafts
                    if (drafts.isEmpty()) return
                    val already = try {
                        com.markleaf.app.pdf.PdfEmbeddedOutlineImporter
                            .isAlreadyImported(drafts, outlineNodes)
                    } catch (_: Throwable) {
                        false
                    }
                    // Soft warning: some destinations unresolved but tree still usable.
                    if (parsed.unresolvedDestinations > 0 && force) {
                        _importMessage.value =
                            "已解析目录，其中 ${parsed.unresolvedDestinations} 项页码为近似值"
                    }
                    // Prompt when not fully imported yet, or when local tree empty (fresh).
                    if (!already || outlineNodes.isEmpty() || force) {
                        _embeddedOffer.value = EmbeddedOutlineOffer(
                            drafts = drafts,
                            hasLocalNodes = outlineNodes.isNotEmpty(),
                            alreadyImported = already,
                        )
                    }
                }
                PdfEmbeddedOutlineParser.ParseResult.None -> {
                    cachedDrafts = emptyList()
                    if (force) {
                        _importMessage.value = "此 PDF 没有可用的内置目录"
                    }
                }
                is PdfEmbeddedOutlineParser.ParseResult.Failed -> {
                    cachedDrafts = emptyList()
                    _embeddedOffer.value = null
                    // Always surface failure (auto or force) so users know why directory is empty.
                    _importMessage.value = parsed.message
                }
            }
        } catch (_: Throwable) {
            cachedDrafts = emptyList()
            _embeddedOffer.value = null
            _importMessage.value = "目录解析异常"
        }
    }

    /**
     * Horizontal-mode primary render. Keeps [pageImage] until success (no white flash).
     */
    private fun requestRender(pageIndex: Int) {
        val ready = _uiState.value as? PdfViewerUiState.Ready ?: return
        val tier = if (readingMode == PdfReadingMode.HORIZONTAL_PAGED) {
            PdfRenderQuality.resolveTier(zoomScale, qualityTier)
        } else {
            qualityTier
        }
        // Keep existing image while re-rendering.
        if (ready.pageImage == null && pageCache.image(pageIndex) == null) {
            _uiState.value = ReadyBase(ready, isRendering = true, renderError = null)
        } else {
            _uiState.value = ReadyBase(
                ready,
                pageImage = ready.pageImage ?: pageCache.image(pageIndex),
                isRendering = false,
                renderError = null,
            )
        }
        requestPageRender(pageIndex, forceTier = tier, allowUpgrade = true, horizontalPrimary = true)
    }

    /**
     * Unified page render. **Never nulls existing bitmap on start.**
     * Job identity: documentId + pageIndex + widthBucket; new request cancels older job for page.
     *
     * @param allowUpgrade request higher tier than cached
     * @param allowDowngrade replace HIGH with PREVIEW (neighbor demote); old image stays until success
     */
    private fun requestPageRender(
        pageIndex: Int,
        forceTier: QualityTier,
        allowUpgrade: Boolean,
        allowDowngrade: Boolean = false,
        horizontalPrimary: Boolean = false,
        primaryIndex: Int? = null,
    ) {
        val ready = _uiState.value as? PdfViewerUiState.Ready ?: return
        if (pageIndex !in 0 until ready.pageCount) return
        val widthForJob = PdfRenderQuality.targetWidthPx(viewportWidthPx, forceTier)
        val requestKey = ContinuousRenderKey(documentId, pageIndex, forceTier, widthForJob)
        if (cacheJobs[pageIndex]?.isActive == true && cacheJobKeys[pageIndex] == requestKey) return
        val existing = pageCache.get(pageIndex)
        if (existing != null) {
            if (existing.widthBucket == widthForJob && existing.tier == forceTier) {
                if (horizontalPrimary) {
                    _uiState.value = ReadyBase(
                        ready,
                        pageImage = existing.image,
                        isRendering = false,
                        renderError = null,
                        pageBitmaps = pageCache.snapshotImages(),
                        pageAspects = pageCache.snapshotAspects(),
                        pageBitmapMetadata = snapshotPageMetadata(),
                        renderWidthBucket = existing.widthBucket,
                    )
                }
                return
            }
            // Skip if we already have equal/better and not forced to downgrade.
            if (!allowDowngrade) {
                if (!allowUpgrade && existing.widthBucket >= widthForJob) return
                if (allowUpgrade &&
                    existing.widthBucket >= widthForJob &&
                    existing.tier.ordinal >= forceTier.ordinal
                ) {
                    return
                }
            } else {
                // Downgrade only when existing is actually higher tier/width.
                if (existing.tier.ordinal <= forceTier.ordinal &&
                    existing.widthBucket <= widthForJob
                ) {
                    return
                }
            }
        }

        // Same request already in flight for this width? skip duplicate.
        val active = cacheJobs[pageIndex]
        if (active?.isActive == true && existing != null && !allowDowngrade && !allowUpgrade) {
            // Preview fill already running.
            return
        }

        // Bump generation; cancel previous job for this page only.
        // Existing bitmap stays in pageCache until putIfCurrent succeeds.
        val gen = (pageGenerations[pageIndex] ?: 0L) + 1L
        pageGenerations[pageIndex] = gen
        cacheJobs.remove(pageIndex)?.cancel()
        cacheJobKeys.remove(pageIndex)
        if (horizontalPrimary) {
            renderJob?.cancel()
        }
        if (isDebuggable && ready.readingMode == PdfReadingMode.VERTICAL_CONTINUOUS) {
            Log.d(
                ZOOM_LOG_TAG,
                "RENDER_REQUEST interactionId=$continuousInteractionId pageIndex=$pageIndex " +
                    "bitmapGeneration=$gen tier=$forceTier timestamp=${System.currentTimeMillis()}",
            )
        }

        val job = viewModelScope.launch {
            val renderStartedAt = System.currentTimeMillis()
            val size = renderer.pageSizePts(pageIndex)
            val result = renderer.renderPage(
                pageIndex = pageIndex,
                targetWidthPx = widthForJob,
                maxEdgePx = PdfPageRenderer.MAX_EDGE_PX,
            )
            // Stale generation must not overwrite.
            if (pageGenerations[pageIndex] != gen) return@launch
            val current = _uiState.value as? PdfViewerUiState.Ready ?: return@launch
            when (result) {
                is PdfPageRenderer.RenderResult.Success -> {
                    val readyAt = System.currentTimeMillis()
                    if (isDebuggable && current.readingMode == PdfReadingMode.VERTICAL_CONTINUOUS) {
                        Log.d(ZOOM_LOG_TAG, "ZOOM_QUALITY_READY page=$pageIndex tier=$forceTier widthBucket=$widthForJob " +
                            "renderDurationMs=${readyAt - renderStartedAt} timestamp=$readyAt")
                    }
                    val aspect = pageCache.aspect(pageIndex) ?: if (size != null && size.first > 0) {
                        size.second.toFloat() / size.first.toFloat()
                    } else if (result.widthPx > 0) {
                        result.heightPx.toFloat() / result.widthPx.toFloat()
                    } else {
                        DEFAULT_PAGE_ASPECT
                    }
                    val entry = PageBitmapEntry(
                        image = result.image,
                        widthBucket = widthForJob,
                        tier = forceTier,
                        generation = gen,
                        aspectHw = aspect,
                    )
                    val primary = primaryIndex ?: current.pageIndex
                    val installed = pageCache.putIfCurrent(
                        pageIndex = pageIndex,
                        entry = entry,
                        currentGeneration = gen,
                        allowDowngrade = allowDowngrade,
                        primaryIndex = primary,
                        protectedPages = continuousProtectedPages,
                    )
                    if (!installed) return@launch
                    if (
                        current.readingMode == PdfReadingMode.VERTICAL_CONTINUOUS &&
                        suspendPageSettled
                    ) {
                        pendingContinuousCachePublish = true
                        pendingContinuousPages += pageIndex
                        if (isDebuggable) {
                            Log.d(
                                ZOOM_LOG_TAG,
                                "BITMAP_PENDING interactionId=$continuousInteractionId pageIndex=$pageIndex bitmapGeneration=$gen " +
                                    "tier=$forceTier timestamp=${System.currentTimeMillis()}",
                            )
                        }
                        return@launch
                    }
                    if (current.readingMode == PdfReadingMode.VERTICAL_CONTINUOUS) {
                        pageCache.retainOnly(continuousProtectedPages)
                    }
                    // Atomic UI replace — old bitmap remained visible until this point.
                    _uiState.value = publishCache(
                        current,
                        pageImage = if (
                            current.readingMode == PdfReadingMode.HORIZONTAL_PAGED &&
                            current.pageIndex == pageIndex
                        ) {
                            result.image
                        } else {
                            current.pageImage
                        },
                        isRendering = false,
                        renderError = null,
                    )
                    if (isDebuggable && current.readingMode == PdfReadingMode.VERTICAL_CONTINUOUS) {
                        Log.d(ZOOM_LOG_TAG, "ZOOM_QUALITY_SWAP page=$pageIndex tier=$forceTier widthBucket=$widthForJob " +
                            "readyToSwapMs=${System.currentTimeMillis() - readyAt} timestamp=${System.currentTimeMillis()}")
                    }
                }
                is PdfPageRenderer.RenderResult.Failed -> {
                    // Keep old bitmap; never flash white/loading if we have one.
                    if (current.pageIndex == pageIndex &&
                        pageCache.image(pageIndex) == null &&
                        current.pageImage == null
                    ) {
                        _uiState.value = ReadyBase(
                            current,
                            isRendering = false,
                            renderError = result.message,
                        )
                    }
                }
                PdfPageRenderer.RenderResult.OutOfMemory -> {
                    // Bounded OOM recovery: trim page cache headroom, surface an
                    // explicit error only when nothing is on screen, never loop.
                    Log.w(ZOOM_LOG_TAG, "PAGE_OOM: full-page bitmap allocation failed, trimming cache")
                    pageCache.trimMemory()
                    if (current.pageIndex == pageIndex &&
                        pageCache.image(pageIndex) == null &&
                        current.pageImage == null
                    ) {
                        _uiState.value = ReadyBase(
                            current,
                            isRendering = false,
                            renderError = "内存不足，无法渲染该页",
                        )
                    }
                }
                PdfPageRenderer.RenderResult.Closed,
                PdfPageRenderer.RenderResult.InvalidPage,
                -> {
                    if (current.pageIndex == pageIndex &&
                        pageCache.image(pageIndex) == null &&
                        current.pageImage == null
                    ) {
                        _uiState.value = ReadyBase(
                            current,
                            isRendering = false,
                            renderError = if (result is PdfPageRenderer.RenderResult.Closed) {
                                "PDF 已关闭"
                            } else {
                                "页码无效"
                            },
                        )
                    }
                }
            }
        }
        cacheJobs[pageIndex] = job
        cacheJobKeys[pageIndex] = requestKey
        job.invokeOnCompletion {
            if (cacheJobs[pageIndex] === job) {
                cacheJobs.remove(pageIndex)
                cacheJobKeys.remove(pageIndex)
            }
        }
        if (horizontalPrimary) {
            renderJob = job
        }
    }

    private fun schedulePersist(
        position: ReadingPosition,
        pageCount: Int,
        force: Boolean = false,
    ) {
        val geometryReadyForMode = readingMode != PdfReadingMode.VERTICAL_CONTINUOUS || continuousGeometryReady
        if (!force && !continuousEntryRestore.canPersist(geometryReadyForMode)) {
            if (isDebuggable) {
                val s = continuousEntryRestore.snapshot()
                Log.d(ZOOM_LOG_TAG,
                    "PROGRESS_WRITE_SUPPRESSED reason=${if (s.phase != com.markleaf.app.pdf.ContinuousEntryPhase.READY) "phase=${s.phase}" else if (s.pendingRestorePosition != null) "pendingRestore" else if (!geometryReadyForMode) "geometryNotReady" else "positionNull"}")
            }
            return
        }
        if (!force && position.generation <= lastPersistedReadingGeneration) {
            if (isDebuggable) {
                Log.d(ZOOM_LOG_TAG,
                    "PROGRESS_WRITE_SUPPRESSED reason=staleGeneration positionGen=${position.generation} " +
                    "lastPersisted=$lastPersistedReadingGeneration")
            }
            return
        }
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(PERSIST_DEBOUNCE_MS)
            if (!force && position.generation <= lastPersistedReadingGeneration) return@launch
            persistProgress(position, pageCount, force)
        }
    }

    /**
     * P0A3: debounced position persistence now goes through the unified
     * [progressFlusher] instead of directly calling the DAO. This closes the
     * ACTIVE_BYPASS_PATH: the previous `viewModelScope.launch(NonCancellable + IO)`
     * path bypassed the flusher's generation ordering and could overwrite a
     * newer final flush with a stale generation.
     *
     * Generation: the position's own [ReadingPosition.generation] is used as
     * the snapshot generation, so the flusher rejects stale writes. The
     * companion `lastStableViewportSession` is carried along so position and
     * session land atomically in a single Room transaction.
     */
    private fun persistProgress(
        position: ReadingPosition,
        pageCount: Int,
        force: Boolean = false,
    ) {
        if (closed) return
        if (!force && position.generation <= lastPersistedReadingGeneration) return
        lastPersistedReadingGeneration = position.generation
        finalFlushGeneration++
        progressFlusher.flush(
            FinalPdfProgressSnapshot(
                documentId = documentId,
                readingPosition = position,
                // Carry the last stable session so position + session write
                // atomically. zoom-settle will refresh this on its own path.
                viewportSession = viewportSessionTracker.takeForFlush(),
                pageCount = pageCount,
                generation = finalFlushGeneration,
            )
        )
        if (isDebuggable) {
            Log.d(ZOOM_LOG_TAG,
                "PROGRESS_WRITE_SUBMIT page=${position.pageIndex} fraction=${position.intraPageFraction} " +
                "generation=${finalFlushGeneration} force=$force")
        }
    }

    /**
     * P0A3: viewport-session persistence now goes through the unified
     * [progressFlusher]. The previous `viewModelScope.launch(NonCancellable + IO)`
     * bypass is removed. The companion `lastStableReadingPosition` is carried
     * along so session and position land atomically in a single Room transaction.
     */
    private fun persistViewportSession(session: PdfViewportSessionState) {
        if (closed) return
        finalFlushGeneration++
        progressFlusher.flush(
            FinalPdfProgressSnapshot(
                documentId = documentId,
                readingPosition = continuousEntryRestore.snapshot().lastStableReadingPosition,
                viewportSession = session,
                pageCount = (_uiState.value as? PdfViewerUiState.Ready)?.pageCount ?: 0,
                generation = finalFlushGeneration,
            )
        )
        if (isDebuggable) {
            Log.d(ZOOM_LOG_TAG,
                "VIEWPORT_SESSION_WRITE_SUBMIT page=${session.pageIndex} scale=${session.scale} " +
                "generation=${finalFlushGeneration}")
        }
    }

    fun flushProgress() {
        if (closed) return
        val ready = _uiState.value as? PdfViewerUiState.Ready ?: return
        // P0A: final flush goes through FinalPdfProgressFlusher (single atomic
        // write with generation guard). The debounced persistProgress path
        // remains for in-flight writes during scroll; this is the final path
        // for onStop / dispose / back-navigation.
        val stablePosition = continuousEntryRestore.snapshot().lastStableReadingPosition
        val stableSession = viewportSessionTracker.takeForFlush()
        if (stablePosition == null && stableSession == null) return
        finalFlushGeneration++
        progressFlusher.flush(
            FinalPdfProgressSnapshot(
                documentId = documentId,
                readingPosition = stablePosition,
                viewportSession = stableSession,
                pageCount = ready.pageCount,
                generation = finalFlushGeneration,
            )
        )
    }

    override fun onCleared() {
        // P0A lifecycle hardening: AndroidX ViewModel.clear$lifecycle_viewmodel_release
        // bytecode (verified against lifecycle-viewmodel 2.8.7) shows the order is:
        //   1. ViewModelImpl.clear() — sets isCleared, closes all closeables
        //      including viewModelScope's CloseableCoroutineScope
        //   2. ViewModel.onCleared() — called AFTER viewModelScope is cancelled
        // So viewModelScope is NOT active here; viewModelScope.launch would not
        // run reliably. Use applicationScope (process lifetime, SupervisorJob,
        // IO) for the final close.
        if (closed) {
            super.onCleared()
            return
        }
        closed = true

        // 1. Capture immutable stable snapshots before cancelling anything.
        val ready = _uiState.value as? PdfViewerUiState.Ready
        val stablePosition = continuousEntryRestore.snapshot().lastStableReadingPosition
        val stableSession = ready?.viewportSessionState
        val pageCount = ready?.pageCount ?: 0

        // 2. Cancel all production jobs (renderJob, cacheJobs, tileWorkerJob,
        //    primaryUpgradeJob, zoomSettleJob, persistJob). viewModelScope is
        //    already cancelled by the framework, but explicit cancel is safe
        //    and documents intent.
        renderJob?.cancel(); renderJob = null
        cacheJobs.values.forEach { it.cancel() }; cacheJobs.clear()
        cacheJobKeys.clear()
        tileWorkerJob?.cancel(); tileWorkerJob = null
        tilePublishJob?.cancel(); tilePublishJob = null
        tileWakeSignal.close()
        primaryUpgradeJob?.cancel(); primaryUpgradeJob = null
        zoomSettleJob?.cancel(); zoomSettleJob = null
        persistJob?.cancel(); persistJob = null

        // 3. Issue idempotent close on applicationScope — never runBlocking on
        //    main thread. closeAsync acquires the renderer/thumbnail mutex on
        //    IO; the main thread returns immediately.
        issueCloseAsync()

        // 4. Final flush of stable progress via FinalPdfProgressFlusher.
        //    onStop / dispose / back-navigation already flushed through the
        //    same flusher; this is the last-resort fallback for process death
        //    or skipped flush paths. The flusher's generation guard ensures
        //    this fallback cannot clobber a newer write that already landed
        //    from a prior flushProgress() call.
        //
        //    P0A1: use sealAndFlushFinal — a single atomic seal-and-submit.
        //    The previous flush() + close() pair was racy: close() could set
        //    closed=true before the async flush launch acquired the mutex,
        //    causing the final snapshot to be silently dropped. sealAndFlushFinal
        //    guarantees the accepted final snapshot reaches the mutex and
        //    transitions the flusher to CLOSED only after the write resolves.
        finalFlushGeneration++
        progressFlusher.sealAndFlushFinal(
            FinalPdfProgressSnapshot(
                documentId = documentId,
                readingPosition = stablePosition,
                viewportSession = stableSession,
                pageCount = pageCount,
                generation = finalFlushGeneration,
            )
        )

        super.onCleared()
    }

    /**
     * Idempotent: issues renderer.closeAsync() + thumbnailLoader.closeAsync()
     * on applicationScope exactly once. Subsequent calls are no-ops. Close
     * exceptions are logged but do NOT cancel applicationScope (SupervisorJob
     * + per-launch try/catch).
     *
     * P0A1: NonCancellable removed. applicationScope uses SupervisorJob, so a
     * child launch failure (caught here) does not propagate to siblings. The
     * launch is a structured child of applicationScope and is cancelled when
     * the scope is cancelled (e.g. in tests).
     */
    private fun issueCloseAsync() {
        if (closeIssued) return
        closeIssued = true
        applicationScope.launch(Dispatchers.IO) {
            try {
                renderer.closeAsync()
            } catch (e: Exception) {
                Log.w(ZOOM_LOG_TAG, "issueCloseAsync: renderer.closeAsync failed", e)
            }
            try {
                thumbnailLoader.closeAsync()
            } catch (e: Exception) {
                Log.w(ZOOM_LOG_TAG, "issueCloseAsync: thumbnailLoader.closeAsync failed", e)
            }
        }
    }

    class Factory(
        private val application: Application,
        private val documentId: String,
        private val repository: DocumentRepository,
        private val applicationScope: CoroutineScope,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PdfViewerViewModel::class.java)) {
                return PdfViewerViewModel(
                    application = application,
                    documentId = documentId,
                    repository = repository,
                    applicationScope = applicationScope,
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }

    companion object {
        private const val ZOOM_LOG_TAG = "MarkLeafPdfZoom"
        private const val PERSIST_DEBOUNCE_MS = 350L
        private const val DEFAULT_PAGE_ASPECT = 1.414f
    }

    private val isDebuggable: Boolean
        get() = (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
