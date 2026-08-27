package com.m1racle.reader.ui.pdf

import android.graphics.Paint
import android.content.pm.ApplicationInfo
import android.util.Log
import com.m1racle.reader.BuildConfig
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m1racle.reader.pdf.BitmapMetadata
import com.m1racle.reader.pdf.ContinuousDocumentGeometry
import com.m1racle.reader.pdf.ContinuousDocumentState
import com.m1racle.reader.pdf.ContinuousLayoutMath
import com.m1racle.reader.pdf.ContinuousEntryPhase
import com.m1racle.reader.pdf.ContinuousGestureCapture
import com.m1racle.reader.pdf.ContinuousGestureCoordinator
import com.m1racle.reader.pdf.ContinuousGestureSnapshotPayload
import com.m1racle.reader.pdf.ContinuousInteractionPhase
import com.m1racle.reader.pdf.ContinuousInteractionReducer
import com.m1racle.reader.pdf.ContinuousInteractionTransaction
import com.m1racle.reader.pdf.PdfTileKey
import com.m1racle.reader.pdf.ContinuousScrollDirection
import com.m1racle.reader.pdf.ContinuousScrollPhase
import com.m1racle.reader.pdf.ContinuousViewportSnapshot
import com.m1racle.reader.pdf.DisplayMergePolicy
import com.m1racle.reader.pdf.PdfGestureTransformController
import com.m1racle.reader.pdf.PdfTileEntry
import com.m1racle.reader.pdf.PdfTileDisplay
import com.m1racle.reader.pdf.PdfTilePlanner
import com.m1racle.reader.pdf.PdfTileRequest
import com.m1racle.reader.pdf.PdfTilePlan
import com.m1racle.reader.pdf.PdfViewportSnapshot
import com.m1racle.reader.pdf.TileOverlaySnapshot
import com.m1racle.reader.pdf.TileInteractionPhase
import com.m1racle.reader.pdf.TileRenderPipelinePolicy
import com.m1racle.reader.pdf.LatestViewportConflator
import com.m1racle.reader.pdf.PageLayoutRect
import com.m1racle.reader.pdf.PageLayoutSize
import com.m1racle.reader.pdf.PdfZoomBuckets
import com.m1racle.reader.pdf.PdfZoomPredictor
import com.m1racle.reader.pdf.ZoomPrediction
import com.m1racle.reader.pdf.ViewportMotionEstimate
import com.m1racle.reader.pdf.PdfViewportMotion
import com.m1racle.reader.pdf.TilePriority
import com.m1racle.reader.pdf.DisplayMergeResult
import com.m1racle.reader.pdf.DisplayedBitmap
import com.m1racle.reader.pdf.QualityTier
import com.m1racle.reader.pdf.PdfViewportSessionState
import com.m1racle.reader.pdf.ReadingPosition
import com.m1racle.reader.pdf.ReadingPositionSource
import com.m1racle.reader.pdf.PdfReadingMode
import com.m1racle.reader.pdf.ZoomPanPhysics
import com.m1racle.reader.pdf.ZoomPanPhysicsProfile
import com.m1racle.reader.pdf.ZoomPanPoint
import com.m1racle.reader.pdf.ZoomPanPointer
import com.m1racle.reader.pdf.ZoomPanTransform
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.roundToInt

/** Scroll-Y delta (document px) above which a direction is reported. */
private const val SCROLL_DIRECTION_EPSILON = 0.5f

internal val R7ContinuousScaleKey = SemanticsPropertyKey<Float>("R7ContinuousScale")
internal var SemanticsPropertyReceiver.r7ContinuousScale by R7ContinuousScaleKey
internal val R7TileCountKey = SemanticsPropertyKey<Int>("R7TileCount")
internal var SemanticsPropertyReceiver.r7TileCount by R7TileCountKey

/**
 * R7 production continuous reader: a virtualized, document-wide 2D viewport.
 * The legacy R6 lock/overlay reader remains in source only for rollback comparison.
 */
@Composable
internal fun ContinuousDocumentViewport(
    state: PdfViewerUiState.Ready,
    pageJump: PdfViewerViewModel.PageJumpRequest?,
    onPageJumpConsumed: (Long) -> Unit,
    onViewportWidthPx: (Int) -> Unit,
    onContinuousGeometryReady: () -> Unit,
    onContinuousEntryRestoreApplied: (ReadingPosition, ContinuousDocumentState) -> Unit,
    onDocumentStateSettled: (ContinuousDocumentState, Int?) -> Unit,
    onZoomGestureActive: (Boolean) -> Unit,
    onPageSettled: (ReadingPosition) -> Unit,
    onViewportChanged: (ContinuousViewportSnapshot) -> Unit,
    onEnsureAround: (Int) -> Unit,
    onRetryPage: (Int) -> Unit,
    onViewportSessionSettled: (PdfViewportSessionState) -> Unit = {},
    onViewportSessionPreview: (PdfViewportSessionState) -> Unit = {},
    onTilePlan: (PdfTilePlan) -> Unit = {},
    tileOverlaySnapshots: StateFlow<TileOverlaySnapshot>? = null,
    gestureCoordinatorOverride: ContinuousGestureCoordinator? = null,
    pageAnnotation: @Composable androidx.compose.foundation.layout.BoxScope.(Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val documentId = state.document.id
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val context = LocalContext.current
    val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val detailTileOverlayEnabled = BuildConfig.DETAIL_TILE_OVERLAY_ENABLED
    val showBaseLayer = !isDebuggable || BuildConfig.DETAIL_TILE_BASE_LAYER_ENABLED
    val detailTileOverlayAlpha = if (isDebuggable) BuildConfig.DETAIL_TILE_OVERLAY_ALPHA else 1f
    val showTileDiagnostics = isDebuggable && BuildConfig.DETAIL_TILE_DIAGNOSTICS_ENABLED
    // P1-7: consume the published overlay as a whole snapshot and index it once
    // per sequence — consumers read pre-grouped per-page lists instead of
    // filtering the entire cache in every composition / measure pass.
    val externalOverlaySnapshot: TileOverlaySnapshot? = if (tileOverlaySnapshots != null) {
        tileOverlaySnapshots.collectAsStateWithLifecycle().value
    } else {
        null
    }
    val tilesSource: Map<PdfTileKey, PdfTileEntry> =
        externalOverlaySnapshot?.entries ?: state.tileEntries
    val tag = "M1racleReaderR71"
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var documentState by remember(documentId) { mutableStateOf(state.continuousDocumentState) }

    /** Last reported scroll-Y (document px) for real scroll-direction detection. */
    var lastReportedScrollYPx by remember(documentId) { mutableFloatStateOf(documentState.scrollY) }

    // Ground-truth anchor tracking: the PRIMARY page's top edge on screen,
    // screenY = pageTop*scale - scrollY (from the page placement transform).
    // Cross-frame delta of the SAME page = the PDF content's real on-screen
    // motion; this is the only direction source the chrome adapter may use.
    // Epoch reset: any NON-scroll coordinate discontinuity (zoom scale change,
    // viewport resize, page identity switch) invalidates the baseline — the
    // frame only re-baselines and produces NO motion sample (anchorDeltaY = 0),
    // so zoom/layout/programmatic movement can never drive the chrome machine.
    var lastAnchorPage by remember(documentId) { mutableIntStateOf(-1) }
    var lastAnchorScreenY by remember(documentId) { mutableFloatStateOf(0f) }
    var lastAnchorScale by remember(documentId) { mutableFloatStateOf(documentState.scale) }
    var lastAnchorViewport by remember(documentId) { mutableStateOf(IntSize.Zero) }

    /** Latest instant scroll velocity (dp/s) — fling fast-path input for chrome. */
    var lastVelocityYDpPerSec by remember(documentId) { mutableFloatStateOf(0f) }
    var appliedEntryGeneration by remember(documentId) { mutableStateOf<Long?>(null) }
    var flingActive by remember(documentId) { mutableStateOf(false) }
    var consumedJumpId by remember(documentId) { mutableStateOf<Long?>(null) }
    var interaction by remember(documentId) { mutableStateOf(ContinuousInteractionTransaction()) }
    var displayedBitmaps by remember(documentId) { mutableStateOf(state.pageBitmaps) }
    var pendingBitmaps by remember(documentId) { mutableStateOf(emptyMap<Int, ImageBitmap>()) }
    var displayedAspects by remember(documentId) { mutableStateOf(state.pageAspects) }
    var pendingAspects by remember(documentId) { mutableStateOf(emptyMap<Int, Float>()) }
    // P0B1: per-page quality metadata for display merge policy
    var displayedMetadata by remember(documentId) { mutableStateOf(state.pageBitmapMetadata) }
    var pendingMetadata by remember(documentId) { mutableStateOf(emptyMap<Int, BitmapMetadata>()) }
    var tileFocusViewport by remember(documentId) { mutableStateOf<Offset?>(null) }
    var zoomPrediction by remember(documentId) { mutableStateOf(ZoomPrediction()) }
    var tileBucket by remember(documentId) { mutableStateOf(0) }
    var tileTargetBucket by remember(documentId) { mutableStateOf(0) }
    var viewportMotion by remember(documentId) { mutableStateOf(ViewportMotionEstimate()) }
    var lastMotionScale by remember(documentId) { mutableStateOf(documentState.scale) }
    val viewportConflator = remember(documentId) { LatestViewportConflator<PdfViewportSnapshot>() }
    val viewportSignal = remember(documentId) { Channel<Unit>(Channel.CONFLATED) }
    val viewportSequence = remember(documentId) { AtomicLong(0L) }
    val gestures = remember(documentId) { ContinuousDocumentGestureState() }
    val platformFlingRunner = remember(context) { PlatformPdfFlingRunner(context) }
    val sharedGestureController = remember(documentId) {
        PdfGestureTransformController(
            ZoomPanTransform(
                scale = documentState.scale,
                legalOffsetPx = ZoomPanPoint(-documentState.scrollX, -documentState.scrollY),
            ),
        )
    }
    val currentOnZoomGestureActive by rememberUpdatedState(onZoomGestureActive)
    val currentOnDocumentStateSettled by rememberUpdatedState(onDocumentStateSettled)
    val currentOnViewportSessionSettled by rememberUpdatedState(onViewportSessionSettled)
    val currentOnViewportSessionPreview by rememberUpdatedState(onViewportSessionPreview)
    val currentOnTilePlan by rememberUpdatedState(onTilePlan)
    val currentOnContinuousGeometryReady by rememberUpdatedState(onContinuousGeometryReady)
    val currentOnContinuousEntryRestoreApplied by rememberUpdatedState(onContinuousEntryRestoreApplied)
    val currentOnPageJumpConsumed by rememberUpdatedState(onPageJumpConsumed)
    val currentQualityTier by rememberUpdatedState(state.qualityTier)
    val currentEntryState by rememberUpdatedState(state.continuousEntryState)
    val ownedGestureCoordinator = remember(documentId) {
        ContinuousGestureCoordinator(
            ownerDocumentId = documentId,
            onGestureActiveChanged = { active -> currentOnZoomGestureActive(active) },
        )
    }
    val gestureCoordinator = gestureCoordinatorOverride ?: ownedGestureCoordinator
    val geometry = remember(state.pageCount, viewportSize.width, displayedAspects) {
        ContinuousDocumentGeometry.create(
            pageCount = state.pageCount,
            basePageWidth = viewportSize.width.toFloat().coerceAtLeast(1f),
            aspects = displayedAspects,
            pageGap = with(density) { 1.dp.toPx() },
        )
    }
    var geometryEpoch by remember(documentId) { mutableStateOf(geometry) }
    val alignedDocumentState = if (
        geometryEpoch !== geometry &&
        geometryEpoch.pageCount == geometry.pageCount &&
        viewportSize.width > 0 &&
        viewportSize.height > 0
    ) {
        geometry.remapStatePreservingViewportTop(
            geometryEpoch,
            documentState,
            viewportSize.width.toFloat(),
            viewportSize.height.toFloat(),
        )
    } else {
        documentState
    }
    SideEffect {
        if (geometryEpoch !== geometry) {
            // Mid-gesture writes fight the pointer epoch (baseTransform + absolute
            // delta) and jump the page; keep remapping for render only until lift.
            if (documentState.gestureActive) return@SideEffect
            geometryEpoch = geometry
            if (alignedDocumentState != documentState) {
                documentState = alignedDocumentState
                sharedGestureController.replaceTransform(
                    ZoomPanTransform(
                        scale = alignedDocumentState.scale,
                        legalOffsetPx = ZoomPanPoint(
                            -alignedDocumentState.scrollX,
                            -alignedDocumentState.scrollY,
                        ),
                    ),
                )
            }
        }
    }
    val tileIndex = remember(
        externalOverlaySnapshot?.sequence, tilesSource, state.tileDocumentGeneration,
        detailTileOverlayEnabled, geometry,
    ) {
        buildContinuousTileIndex(
            source = tilesSource.values,
            documentGeneration = state.tileDocumentGeneration,
            geometry = geometry,
            overlayEnabled = detailTileOverlayEnabled,
        )
    }

    val interactionActive = interaction.phase == ContinuousInteractionPhase.PINCHING ||
        interaction.phase == ContinuousInteractionPhase.SETTLING

    LaunchedEffect(
        state.pageBitmaps,
        state.pageAspects,
        state.pageBitmapMetadata,
        interaction.phase,
        lastAnchorPage,
    ) {
        val phase = interaction.phase
        val newBitmaps = state.pageBitmaps
        val newMetadata = state.pageBitmapMetadata
        val newAspects = state.pageAspects

        val accepted = mutableMapOf<Int, ImageBitmap>()
        val acceptedMeta = mutableMapOf<Int, BitmapMetadata>()
        val acceptedAspects = mutableMapOf<Int, Float>()
        val newPending = mutableMapOf<Int, ImageBitmap>()
        val newPendingMeta = mutableMapOf<Int, BitmapMetadata>()
        val newPendingAspects = mutableMapOf<Int, Float>()

        for ((page, bitmap) in newBitmaps) {
            val meta = newMetadata[page] ?: BitmapMetadata(QualityTier.SCROLL_PREVIEW, 0)
            val displayed = displayedBitmaps[page]
            val displayedMeta = displayedMetadata[page]
            val displayedEntry = if (displayed != null && displayedMeta != null) {
                DisplayedBitmap(displayed, displayedMeta)
            } else null

            val result = DisplayMergePolicy.merge(phase, displayedEntry, bitmap, meta)

            when (result) {
                DisplayMergeResult.ACCEPT -> {
                    accepted[page] = bitmap
                    acceptedMeta[page] = meta
                    if (page in newAspects && page !in displayedAspects) {
                        acceptedAspects[page] = newAspects[page]!!
                    }
                    if (isDebuggable) {
                        Log.d(tag, "DISPLAY_BITMAP_ACCEPT pageIndex=$page " +
                            "oldTier=${displayedMeta?.tier} newTier=${meta.tier} " +
                            "oldBucket=${displayedMeta?.widthBucket} newBucket=${meta.widthBucket} " +
                            "bitmapId=${System.identityHashCode(bitmap)} " +
                            "reason=${if (displayed == null) "NEW_PAGE" else "UPGRADE"} " +
                            "timestamp=${System.currentTimeMillis()}")
                    }
                }
                DisplayMergeResult.REJECT -> {
                    if (isDebuggable) {
                        Log.d(tag, "DISPLAY_BITMAP_REJECT pageIndex=$page " +
                            "oldTier=${displayedMeta?.tier} newTier=${meta.tier} " +
                            "oldBucket=${displayedMeta?.widthBucket} newBucket=${meta.widthBucket} " +
                            "reason=DOWNGRADE_BLOCKED timestamp=${System.currentTimeMillis()}")
                    }
                }
                DisplayMergeResult.PENDING -> {
                    val existingBitmap = newPending[page] ?: pendingBitmaps[page]
                    val existingMeta = newPendingMeta[page] ?: pendingMetadata[page]
                    val existingEntry = if (existingBitmap != null && existingMeta != null) {
                        DisplayedBitmap(existingBitmap, existingMeta)
                    } else null
                    val winner = DisplayMergePolicy.bestPending(
                        existingEntry,
                        DisplayedBitmap(bitmap, meta),
                    )
                    @Suppress("UNCHECKED_CAST")
                    newPending[page] = winner.bitmap as ImageBitmap
                    newPendingMeta[page] = winner.metadata
                    if (winner.bitmap === bitmap && page in newAspects && page !in displayedAspects) {
                        newPendingAspects[page] = newAspects[page]!!
                    }
                    if (isDebuggable) {
                        Log.d(tag, "DISPLAY_BITMAP_PENDING pageIndex=$page " +
                            "oldTier=${displayedMeta?.tier} newTier=${meta.tier} " +
                            "reason=INTERACTION_FREEZE timestamp=${System.currentTimeMillis()}")
                    }
                }
                DisplayMergeResult.SKIP -> { /* same bitmap identity, no change */ }
            }
        }

        if (accepted.isNotEmpty()) {
            displayedBitmaps = displayedBitmaps + accepted
            displayedMetadata = displayedMetadata + acceptedMeta
            displayedAspects = displayedAspects + acceptedAspects
        }
        if (newPending.isNotEmpty()) {
            pendingBitmaps = pendingBitmaps + newPending
            pendingMetadata = pendingMetadata + newPendingMeta
            pendingAspects = pendingAspects + newPendingAspects
        }
    }

    val currentGeometry by rememberUpdatedState(geometry)
    val geometryReady = viewportSize.width > 0 && viewportSize.height > 0 && state.pageCount > 0 &&
        geometry.pageCount == state.pageCount && geometry.baseDocumentHeight > 0f
    val currentGeometryReady by rememberUpdatedState(geometryReady)
    val currentViewportSize by rememberUpdatedState(viewportSize)
    val currentTileDocumentGeneration by rememberUpdatedState(state.tileDocumentGeneration)

    // Batch B-1: display-mirror retention — shrink displayedBitmaps/Metadata/
    // Aspects to the anchor±1 window (+ visibly composed pages during pinch)
    // once gestures settle. The cache side already windows itself; without this
    // the mirror grew one bitmap per visited page for the whole composition.
    LaunchedEffect(
        displayedBitmaps,
        pendingBitmaps,
        interaction.phase,
        lastAnchorPage,
        documentState.scale,
    ) {
        val geometry = currentGeometry
        val phaseAllowsPrune =
            interaction.phase == ContinuousInteractionPhase.IDLE ||
                interaction.phase == ContinuousInteractionPhase.SETTLING
        if (!phaseAllowsPrune || !currentGeometryReady || geometry.pageCount <= 0) return@LaunchedEffect
        val anchor = lastAnchorPage.coerceIn(0, geometry.pageCount - 1)
        val zoomComposed = if (documentState.scale > 1f) {
            geometry.composedPages(
                documentState.copy(gestureActive = false),
                currentViewportSize.height.toFloat(),
                prefetchPages = 0,
            ).toSet()
        } else {
            emptySet()
        }
        val keep = DisplayedBitmapRetentionPolicy.retentionSet(
            anchorPage = anchor,
            pageCount = geometry.pageCount,
            zoomComposed = zoomComposed,
        )
        val prunedDisplayed = DisplayedBitmapRetentionPolicy.prune(displayedBitmaps, keep)
        if (prunedDisplayed !== displayedBitmaps) {
            displayedBitmaps = prunedDisplayed
            displayedMetadata = DisplayedBitmapRetentionPolicy.prune(displayedMetadata, keep)
            displayedAspects = DisplayedBitmapRetentionPolicy.prune(displayedAspects, keep)
            pendingBitmaps = DisplayedBitmapRetentionPolicy.prune(pendingBitmaps, keep)
            pendingMetadata = DisplayedBitmapRetentionPolicy.prune(pendingMetadata, keep)
            pendingAspects = DisplayedBitmapRetentionPolicy.prune(pendingAspects, keep)
        }
    }

    fun tileInteractionPhase(pointerDown: Boolean): TileInteractionPhase = when {
        !detailTileOverlayEnabled -> TileInteractionPhase.DISABLED
        flingActive -> TileInteractionPhase.FLINGING
        interaction.phase == ContinuousInteractionPhase.PINCHING -> TileInteractionPhase.PINCHING
        interaction.phase == ContinuousInteractionPhase.SETTLING -> TileInteractionPhase.SETTLING
        interaction.phase == ContinuousInteractionPhase.DRAGGING || pointerDown -> TileInteractionPhase.DRAGGING
        else -> TileInteractionPhase.IDLE
    }

    /** Pointer-side producer: pure math + atomic latest-state offer; never suspends or renders. */
    fun publishViewportSnapshot(next: ContinuousDocumentState, rebased: Boolean = false) {
        val size = currentViewportSize
        val activeGeometry = currentGeometry
        if (!currentGeometryReady || size.width <= 0 || size.height <= 0) return
        val now = System.nanoTime()
        currentOnViewportSessionPreview(
            activeGeometry.viewportSessionAtAnchor(
                next,
                size.width.toFloat(),
                size.height.toFloat(),
                now,
            ),
        )
        val rect = PdfTilePlanner.documentViewport(next, size.width.toFloat(), size.height.toFloat())
        viewportMotion = PdfViewportMotion.update(
            viewportMotion,
            rect,
            next.scale,
            lastMotionScale,
            now,
            rebased,
        )
        lastVelocityYDpPerSec = viewportMotion.velocityYDocumentPxPerSec / density.density
        lastMotionScale = next.scale
        zoomPrediction = PdfZoomPredictor.update(zoomPrediction, next.scale, now, rebased)
        val currentPage = activeGeometry.pageAtViewportCenter(next, size.height.toFloat())
        val sequence = viewportSequence.incrementAndGet()
        val snapshot = PdfViewportSnapshot(
            documentGeneration = currentTileDocumentGeneration,
            geometryGeneration = activeGeometry.pageGeometryGeneration(currentPage),
            viewportRectDocument = rect,
            scale = next.scale,
            focalPointViewport = tileFocusViewport,
            velocityX = viewportMotion.velocityXDocumentPxPerSec,
            velocityY = viewportMotion.velocityYDocumentPxPerSec,
            zoomVelocity = viewportMotion.zoomVelocityPerSec,
            motionConfidence = viewportMotion.confidence,
            interactionPhase = tileInteractionPhase(next.gestureActive),
            timestampNanos = now,
            sequence = sequence,
            pointerDown = next.gestureActive,
            currentPage = currentPage,
            rebaseFrame = rebased,
        )
        viewportConflator.offer(snapshot)
        viewportSignal.trySend(Unit)
        if (isDebuggable && (sequence == 1L || sequence % 8L == 0L || rebased)) {
            Log.d(
                tag,
                "VIEWPORT_SNAPSHOT phase=${snapshot.interactionPhase} scale=${snapshot.scale} " +
                    "rect=${snapshot.viewportRectDocument} velocity=${snapshot.velocityX},${snapshot.velocityY} " +
                    "zoomVelocity=${snapshot.zoomVelocity} sequence=$sequence pointerDown=${snapshot.pointerDown}",
            )
        }
    }

    /** Render-side planner actor: one coroutine, conflated input, never collectLatest. */
    LaunchedEffect(documentId, detailTileOverlayEnabled) {
        var lastPlannerTickNanos = 0L
        for (ignored in viewportSignal) {
            val elapsed = System.nanoTime() - lastPlannerTickNanos
            val throttleNanos = TileRenderPipelinePolicy.PLANNER_THROTTLE_MS * 1_000_000L
            if (elapsed in 1 until throttleNanos) {
                delay((throttleNanos - elapsed + 999_999L) / 1_000_000L)
            }
            val snapshot = viewportConflator.pollLatest() ?: continue
            val activeGeometry = currentGeometry
            val size = currentViewportSize
            if (!currentGeometryReady || snapshot.currentPage !in 0 until activeGeometry.pageCount) continue
            if (snapshot.geometryGeneration != activeGeometry.pageGeometryGeneration(snapshot.currentPage)) continue
            if (!TileRenderPipelinePolicy.plannerAllowed(snapshot.interactionPhase)) {
                currentOnTilePlan(PdfTilePlan(snapshot, emptyList(), emptyMap(), emptySet()))
                lastPlannerTickNanos = System.nanoTime()
                continue
            }
            val plannerState = ContinuousDocumentState(
                scale = snapshot.scale,
                scrollX = snapshot.viewportRectDocument.left * snapshot.scale,
                scrollY = snapshot.viewportRectDocument.top * snapshot.scale,
                gestureActive = snapshot.pointerDown,
            )
            val motion = ViewportMotionEstimate(
                velocityXDocumentPxPerSec = snapshot.velocityX,
                velocityYDocumentPxPerSec = snapshot.velocityY,
                zoomVelocityPerSec = snapshot.zoomVelocity,
                direction = when {
                    kotlin.math.abs(snapshot.velocityY) < 40f -> com.m1racle.reader.pdf.TileNavigationDirection.IDLE
                    snapshot.velocityY > 0f -> com.m1racle.reader.pdf.TileNavigationDirection.FORWARD
                    else -> com.m1racle.reader.pdf.TileNavigationDirection.BACKWARD
                },
                confidence = snapshot.motionConfidence,
                timestampNanos = snapshot.timestampNanos,
                lastRect = snapshot.viewportRectDocument,
            )
            val corridor = PdfViewportMotion.corridor(snapshot.viewportRectDocument, motion)
            val currentBucket = PdfZoomBuckets.index(snapshot.scale, tileBucket)
            tileBucket = currentBucket
            val predictedScale = (snapshot.scale + snapshot.zoomVelocity * .12f).coerceIn(1f, 4f)
            val targetBucket = PdfZoomBuckets.index(predictedScale, currentBucket)
            tileTargetBucket = targetBucket
            val views = PdfTilePlanner.visiblePages(
                activeGeometry,
                plannerState,
                size.width.toFloat(),
                size.height.toFloat(),
            )
            val plannerNow = System.nanoTime()
            val all = mutableListOf<PdfTileRequest>()
            val sizes = mutableMapOf<Int, PageLayoutSize>()
            val requestBuckets = PdfTileDisplay.bucketsToRequest(
                snapshot.scale,
                currentBucket,
                targetBucket,
                pinching = snapshot.interactionPhase == TileInteractionPhase.PINCHING,
            )
            if (requestBuckets.isNotEmpty()) views.forEach { view ->
                sizes[view.pageIndex] = view.pageSize
                val focus = snapshot.focalPointViewport?.let { focal ->
                    val documentX = snapshot.viewportRectDocument.left + focal.x / snapshot.scale
                    val pageY = snapshot.viewportRectDocument.top + focal.y / snapshot.scale - activeGeometry.pageTop(view.pageIndex)
                    if (documentX in 0f..view.pageWidth && pageY in 0f..view.pageHeight) documentX to pageY else null
                }
                fun planned(bucket: Int): List<PdfTileRequest> = PdfTilePlanner.requests(
                    documentId,
                    snapshot.documentGeneration,
                    view,
                    bucket,
                    snapshot.timestampNanos,
                    focus?.first,
                    focus?.second,
                ).map { request ->
                    val leading = request.priority == TilePriority.MARGIN && when (motion.direction) {
                        com.m1racle.reader.pdf.TileNavigationDirection.FORWARD -> request.pageRect.top >= view.visibleRect.bottom
                        com.m1racle.reader.pdf.TileNavigationDirection.BACKWARD -> request.pageRect.bottom <= view.visibleRect.top
                        com.m1racle.reader.pdf.TileNavigationDirection.IDLE -> false
                    }
                    request.copy(
                        priority = if (leading) TilePriority.LEADING_EDGE else request.priority,
                        requestedAtNanos = plannerNow,
                        snapshotSequence = snapshot.sequence,
                        targetedAtNanos = snapshot.timestampNanos,
                    )
                }
                requestBuckets.forEach { bucket -> all += planned(bucket) }
                if (isDebuggable) {
                    Log.d(tag, "TILE_TARGET pageIndex=${view.pageIndex} currentScale=${snapshot.scale} " +
                        "predictedScale=$predictedScale currentBucket=$currentBucket targetBucket=$targetBucket " +
                        "visibleRect=${view.visibleRect} tileCount=${all.size} phase=${snapshot.interactionPhase}")
                }
            }
            PdfTilePlanner.pageAhead(activeGeometry, corridor, snapshot.currentPage)?.let { ahead ->
                sizes[ahead.pageIndex] = ahead.pageSize
                all += PdfTilePlanner.requests(
                    documentId,
                    snapshot.documentGeneration,
                    ahead,
                    currentBucket,
                    snapshot.timestampNanos,
                    null,
                    null,
                ).filter { it.priority != TilePriority.MARGIN }.take(8).map {
                    it.copy(
                        priority = TilePriority.PAGE_AHEAD,
                        requestedAtNanos = plannerNow,
                        snapshotSequence = snapshot.sequence,
                        targetedAtNanos = snapshot.timestampNanos,
                    )
                }
            }
            val ordered = TileRenderPipelinePolicy.ordered(all.distinctBy { it.key })
            val visibleKeys = ordered.filter { TileRenderPipelinePolicy.isCurrentVisible(it.priority) }
                .mapTo(linkedSetOf()) { it.key }
            currentOnTilePlan(PdfTilePlan(snapshot, ordered, sizes, visibleKeys))
            lastPlannerTickNanos = System.nanoTime()
        }
    }

    LaunchedEffect(detailTileOverlayEnabled, geometry, viewportSize) {
        publishViewportSnapshot(documentState)
    }

    DisposableEffect(viewportSignal) {
        onDispose { viewportSignal.close() }
    }

    LaunchedEffect(viewportSize, geometry, interactionActive) {
        if (interactionActive ||
            currentEntryState.phase != ContinuousEntryPhase.READY ||
            viewportSize.width <= 0 || viewportSize.height <= 0
        ) {
            if (isDebuggable && !interactionActive && currentEntryState.phase != ContinuousEntryPhase.READY) {
                Log.d(tag, "FLATTEN_GATE_REJECT reason=notReady phase=${currentEntryState.phase}")
            }
            return@LaunchedEffect
        }
        val clamped = geometry.clamp(
            documentState.copy(gestureActive = false),
            viewportSize.width.toFloat(),
            viewportSize.height.toFloat(),
        )
        if (clamped != documentState) {
            documentState = clamped
            publishViewportSnapshot(clamped)
            val page = geometry.pageAtViewportCenter(clamped, viewportSize.height.toFloat())
            currentOnDocumentStateSettled(clamped, page)
        }
    }

    fun update(next: ContinuousDocumentState): ContinuousDocumentState {
        val clamped = currentGeometry.clamp(
            next,
            viewportSize.width.toFloat(),
            viewportSize.height.toFloat(),
        )
        documentState = clamped
        sharedGestureController.replaceTransform(
            ZoomPanTransform(
                scale = clamped.scale,
                legalOffsetPx = ZoomPanPoint(-clamped.scrollX, -clamped.scrollY),
            ),
        )
        publishViewportSnapshot(clamped)
        return clamped
    }

    LaunchedEffect(
        currentEntryState.phase,
        currentEntryState.pendingRestorePosition?.generation,
        viewportSize,
        geometry,
    ) {
        val entry = currentEntryState
        if (isDebuggable) {
            Log.d(tag, "ENTRY_PHASE phase=${entry.phase} pendingGen=${entry.pendingRestorePosition?.generation} " +
                "pendingPage=${entry.pendingRestorePosition?.pageIndex} appliedGeneration=$appliedEntryGeneration " +
                "geometryReady=$geometryReady viewportSize=$viewportSize")
        }
        if (!geometryReady) return@LaunchedEffect
        if (entry.phase == ContinuousEntryPhase.WAITING_GEOMETRY) {
            currentOnContinuousGeometryReady()
            return@LaunchedEffect
        }
        val target = entry.pendingRestorePosition
        if (entry.phase != ContinuousEntryPhase.APPLYING_TARGET || target == null ||
            appliedEntryGeneration == target.generation
        ) return@LaunchedEffect
        if (documentState.gestureActive) return@LaunchedEffect

        sharedGestureController.cancelMotion(
            ZoomPanTransform(
                scale = documentState.scale,
                legalOffsetPx = ZoomPanPoint(-documentState.scrollX, -documentState.scrollY),
            ),
        )
        gestures.flingJob?.cancel()
        gestures.flingJob = null
        flingActive = false
        val session = state.viewportSessionState?.takeIf {
            target.source in setOf(
                ReadingPositionSource.ZOOM_SESSION_RESTORE,
                ReadingPositionSource.MODE_SWITCH_RESTORE,
            ) && it.pageIndex == target.pageIndex
        }
        val applied = if (session != null) {
            update(geometry.restoreViewportSession(session, documentState, viewportSize.width.toFloat(), viewportSize.height.toFloat()))
        } else {
            val anchorY = if (target.intraPageFraction == 0f &&
                target.source == ReadingPositionSource.EXTERNAL_NAVIGATION
            ) {
                // Outline/page jump: align the page TOP with the viewport top
                // (a center anchor would leave the previous page's bottom half
                // on screen, looking like "stuck between two pages").
                0f
            } else {
                viewportSize.height / 2f
            }
            val contentAnchorY = geometry.pageTop(target.pageIndex) + target.intraPageFraction * geometry.pageHeight(target.pageIndex)
            update(documentState.copy(scrollY = contentAnchorY * documentState.scale - anchorY, gestureActive = false))
        }
        // ── Robust restore validation ──────────────────────────────
        // The old abs(actual - target) <= 1 check was too strict: page
        // gaps, varying page heights, and anchor positions can push the
        // viewport-center page 1–2 pages away even when the target is
        // already intersecting.  Instead, require BOTH:
        //   1. The target page intersects the viewport (any part visible).
        //   2. The anchor document-Y is inside the visible region.
        val targetVisible = geometry.pageIntersectsViewport(applied, viewportSize.height.toFloat(), target.pageIndex)
        val anchorVisible = geometry.anchorIsInViewport(
            applied, viewportSize.height.toFloat(), target.pageIndex, target.intraPageFraction,
        )
        val actual = geometry.readingPositionAtViewportCenter(
            state = applied,
            viewportHeight = viewportSize.height.toFloat(),
            source = target.source,
            generation = target.generation,
        )
        if (targetVisible && anchorVisible) {
            appliedEntryGeneration = target.generation
            if (isDebuggable && target.source == ReadingPositionSource.MODE_SWITCH_RESTORE) {
                Log.d(
                    "M1racleReaderH14",
                    "H14_MODE_APPLY mode=${PdfReadingMode.VERTICAL_CONTINUOUS} " +
                        "page=${actual.pageIndex} scale=${applied.scale} " +
                        "anchorY=${session?.pageAnchorYFraction ?: actual.intraPageFraction} " +
                        "generation=${target.generation}",
                )
            }
            currentOnContinuousEntryRestoreApplied(actual, applied)
        } else if (isDebuggable) {
            Log.d(tag, "RESTORE_REJECT generation=${target.generation} targetPage=${target.pageIndex} " +
                "actualPage=${actual.pageIndex} targetVisible=$targetVisible anchorVisible=$anchorVisible " +
                "reason=${when { !targetVisible && !anchorVisible -> "pageAndAnchorNotVisible" !targetVisible -> "pageNotVisible" else -> "anchorNotVisible" }}")
        }
    }

    fun cancelFling() {
        sharedGestureController.cancelMotion(
            ZoomPanTransform(
                scale = documentState.scale,
                legalOffsetPx = ZoomPanPoint(-documentState.scrollX, -documentState.scrollY),
            ),
        )
        gestures.flingJob?.cancel()
        gestures.flingJob = null
        flingActive = false
    }

    fun commitStableState(stable: ContinuousDocumentState = documentState.copy(gestureActive = false)) {
        val page = currentGeometry.pageAtViewportCenter(
            stable,
            viewportSize.height.toFloat(),
        )
        currentOnDocumentStateSettled(stable, page)
        currentOnViewportSessionSettled(
            currentGeometry.viewportSessionAtAnchor(
                stable, viewportSize.width.toFloat(), viewportSize.height.toFloat(), System.nanoTime(),
            ),
        )
    }

    fun startFling(fingerVelocity: Offset) {
        cancelFling()
        if (fingerVelocity.getDistance() < platformFlingRunner.minimumFlingVelocityPxPerSecond) {
            commitStableState()
            return
        }
        val motionGeneration = sharedGestureController.beginPlatformFling(
            currentTransform = ZoomPanTransform(
                scale = documentState.scale,
                legalOffsetPx = ZoomPanPoint(-documentState.scrollX, -documentState.scrollY),
            ),
        )
        flingActive = true
        publishViewportSnapshot(documentState.copy(gestureActive = false))
        gestures.flingJob = scope.launch {
            var flingResult: PlatformPdfFlingResult? = null
            try {
                gestures.mutatorMutex.mutate(MutatePriority.Default) {
                    flingResult = platformFlingRunner.run(
                        releaseVelocityPxPerSecond = fingerVelocity,
                        isCurrent = {
                            motionGeneration == sharedGestureController.snapshot().motionGeneration
                        },
                        applyDelta = { requested ->
                            val frame = sharedGestureController.applyPlatformFlingDelta(
                                    generation = motionGeneration,
                                    deltaPx = ZoomPanPoint(requested.x, requested.y),
                                    bounds = currentGeometry.zoomPanBounds(
                                        documentState.scale,
                                        viewportSize.width.toFloat(),
                                        viewportSize.height.toFloat(),
                                    ),
                                    overscrollPolicy = ZoomPanPhysicsProfile.ContinuousOverscrollPolicy,
                                )
                            if (frame == null) {
                                Offset.Zero
                            } else {
                                update(
                                    documentState.copy(
                                        scale = frame.snapshot.transform.scale,
                                        scrollX = -frame.snapshot.transform.legalOffsetPx.x,
                                        scrollY = -frame.snapshot.transform.legalOffsetPx.y,
                                        gestureActive = false,
                                    ),
                                )
                                Offset(frame.appliedDeltaPx.x, frame.appliedDeltaPx.y)
                            }
                        },
                    )
                }
            } finally {
                flingActive = false
                publishViewportSnapshot(documentState.copy(gestureActive = false))
                if (sharedGestureController.snapshot().motionGeneration == motionGeneration) {
                    sharedGestureController.finishMotion(motionGeneration, settleOverscroll = false)
                    if (isDebuggable) {
                        flingResult?.let { result ->
                            Log.d(
                                tag,
                                "PLATFORM_FLING release=(${result.releaseVelocityPxPerSecond.x}," +
                                    "${result.releaseVelocityPxPerSecond.y}) splinePosition=" +
                                    "(${result.splinePositionPx.x},${result.splinePositionPx.y}) " +
                                    "plannedFinal=(${result.plannedFinalPositionPx.x}," +
                                    "${result.plannedFinalPositionPx.y}) " +
                                    "displayedPosition=(${result.displayedPositionPx.x}," +
                                    "${result.displayedPositionPx.y}) " +
                                    "durationMs=${result.durationMs} interrupted=${result.interrupted} " +
                                    "terminatedByBounds=${result.terminatedByBounds}",
                            )
                        }
                    }
                    commitStableState()
                }
            }
        }
    }

    val pendingModeRestoreSession = state.viewportSessionState?.takeIf { session ->
        val pending = currentEntryState.pendingRestorePosition
        currentEntryState.phase != ContinuousEntryPhase.READY &&
            pending?.source == ReadingPositionSource.MODE_SWITCH_RESTORE &&
            session.readingMode == PdfReadingMode.VERTICAL_CONTINUOUS &&
            session.pageIndex == pending.pageIndex &&
            viewportSize.width > 0 && viewportSize.height > 0
    }
    // Render the target session synchronously while the entry state machine commits it. Without
    // this derived frame, continuous mode could briefly draw the previous/default transform before
    // LaunchedEffect applied the exact page-local anchor.
    val renderDocumentState = pendingModeRestoreSession?.let { session ->
        geometry.restoreViewportSession(
            session,
            alignedDocumentState,
            viewportSize.width.toFloat(),
            viewportSize.height.toFloat(),
        )
    } ?: alignedDocumentState
    val requestedComposedRange = geometry.composedPages(renderDocumentState, viewportSize.height.toFloat())
    val actualVisibleRange = geometry.composedPages(
        renderDocumentState,
        viewportSize.height.toFloat(),
        prefetchPages = 0,
    )
    val visiblePages = when {
        interactionActive -> interaction.stableVisiblePages
        actualVisibleRange.isEmpty() -> emptySet()
        else -> actualVisibleRange.toSet()
    }
    val composedPages = when {
        interactionActive -> interaction.stableVisiblePages
        requestedComposedRange.isEmpty() -> emptySet()
        else -> requestedComposedRange.toSet()
    }
    val primaryPage = geometry.pageAtViewportCenter(renderDocumentState, viewportSize.height.toFloat())
    // P1-1: the drive collector below outlives single compositions — route every
    // per-composition value it needs through state holders so it never reads a
    // stale captured local.
    val currentVisiblePages by rememberUpdatedState(visiblePages)
    val currentPrimaryAnchorPage by rememberUpdatedState(primaryPage)
    val currentInteractionActive by rememberUpdatedState(interactionActive)
    val currentOnViewportChanged by rememberUpdatedState(onViewportChanged)
    val snapshotPayload = ContinuousGestureSnapshotPayload(
        documentId = documentId,
        composedPages = composedPages,
        visiblePages = visiblePages,
        viewportPage = primaryPage,
        geometry = geometry,
        documentState = renderDocumentState,
        viewportWidth = viewportSize.width,
        viewportHeight = viewportSize.height,
        displayedBitmapIds = composedPages.associateWith { page ->
            displayedBitmaps[page]?.let(System::identityHashCode) ?: 0
        },
        pageBounds = composedPages.associateWith { page ->
            geometry.pageBoundsInViewport(renderDocumentState, page)
        },
    )
    SideEffect {
        gestureCoordinator.snapshots.publish(snapshotPayload)
    }

    ContinuousGestureLifecycleEffect(gestureCoordinator) {
        gestures.settleJob?.cancel()
        gestures.settleJob = null
        cancelFling()
    }

    LaunchedEffect(
        requestedComposedRange.first,
        requestedComposedRange.last,
        interaction.phase,
        interaction.interactionId,
    ) {
        if (!interactionActive || requestedComposedRange.isEmpty()) return@LaunchedEffect
        val required = requestedComposedRange.filterTo(linkedSetOf()) { it !in interaction.stableVisiblePages }
        if (required.isNotEmpty()) {
            interaction = ContinuousInteractionReducer.appendPages(interaction, required)
            if (isDebuggable) required.forEach { page ->
                Log.d(tag, "PAGE_COMPOSE interactionId=${interaction.interactionId} pageIndex=$page " +
                    "bitmapGeneration=-1 tier=${state.qualityTier} timestamp=${System.currentTimeMillis()}")
            }
        }
    }

    // P1-1: one long-lived collector. The trigger tuple is read as snapshot
    // state, so per-frame float changes EMIT without cancelling/relaunching the
    // coroutine (the old LaunchedEffect keys restarted it every frame), and
    // unchanged tuples (e.g. identical visible-page sets) no longer re-emit.
    LaunchedEffect(documentId) {
        snapshotFlow {
            ContinuousViewportDriveInputs(
                scrollX = documentState.scrollX,
                scrollY = documentState.scrollY,
                scale = documentState.scale,
                gestureActive = documentState.gestureActive,
                interactionPhase = interaction.phase,
                flingActive = flingActive,
                visiblePages = currentVisiblePages,
            )
        }.collect { inputs ->
            inputs // emission gate only; the body below reads live state.
            // During a transformed document gesture only last-good bitmaps are moved by GPU.
            // Re-entering IDLE below is what resumes existing R3 preview/HIGH scheduling.
            if (currentEntryState.phase != ContinuousEntryPhase.READY ||
                currentInteractionActive || (documentState.gestureActive && documentState.scale > 1.001f)
            ) {
                return@collect
            }
        // Real scroll direction from the scroll-Y delta (works while dragging,
        // flinging, and on settles) — the previous fling-only FORWARD signal
        // never produced BACKWARD, so chrome could hide but never return.
        val deltaY = documentState.scrollY - lastReportedScrollYPx
        lastReportedScrollYPx = documentState.scrollY
        val direction = when {
            deltaY > SCROLL_DIRECTION_EPSILON -> ContinuousScrollDirection.FORWARD
            deltaY < -SCROLL_DIRECTION_EPSILON -> ContinuousScrollDirection.BACKWARD
            else -> ContinuousScrollDirection.IDLE
        }
        val phase = when {
            flingActive -> ContinuousScrollPhase.FLINGING
            documentState.gestureActive -> ContinuousScrollPhase.DRAGGING
            else -> ContinuousScrollPhase.IDLE
        }
        // Ground-truth anchor measurement: the primary page's top edge on
        // screen = pageTop*scale - scrollY. Only cross-frame deltas of the
        // SAME page are meaningful. Epoch changes (zoom scale, viewport size,
        // page identity) re-baseline WITHOUT producing a motion sample, so
        // non-scroll movement can never drive the chrome state machine.
        val anchorPage = currentPrimaryAnchorPage
        val anchorScreenY = if (anchorPage >= 0) {
            currentGeometry.pageTop(anchorPage) * documentState.scale - documentState.scrollY
        } else {
            0f
        }
        val scaleChanged = kotlin.math.abs(documentState.scale - lastAnchorScale) > 0.001f
        val viewportChanged = viewportSize != lastAnchorViewport
        lastAnchorScale = documentState.scale
        lastAnchorViewport = viewportSize
        val anchorDeltaY = if (
            anchorPage == lastAnchorPage && !scaleChanged && !viewportChanged
        ) {
            anchorScreenY - lastAnchorScreenY
        } else {
            // Baseline/reset frame: no sample for the chrome machine.
            0f
        }
        lastAnchorPage = anchorPage
        lastAnchorScreenY = anchorScreenY
        currentOnViewportChanged(
            ContinuousViewportSnapshot(
                currentVisiblePages, currentPrimaryAnchorPage, direction, phase, deltaY,
                lastVelocityYDpPerSec, anchorScreenY, anchorDeltaY,
            ),
        )
        }
    }

    LaunchedEffect(primaryPage, documentState.gestureActive, flingActive, interaction.phase) {
        if (isDebuggable) {
            Log.d(tag, "VIEWPORT_PAGE_CANDIDATE primaryPage=$primaryPage gestureActive=${documentState.gestureActive} " +
                "flingActive=$flingActive interactionPhase=${interaction.phase} entryPhase=${currentEntryState.phase}")
        }
        if (currentEntryState.phase == ContinuousEntryPhase.READY &&
            !documentState.gestureActive && !flingActive && !interactionActive
        ) {
            onPageSettled(
                currentGeometry.readingPositionAtViewportCenter(
                    state = documentState,
                    viewportHeight = viewportSize.height.toFloat(),
                    source = ReadingPositionSource.USER_CONTINUOUS_SCROLL,
                    generation = 0L,
                ),
            )
        } else if (isDebuggable && currentEntryState.phase != ContinuousEntryPhase.READY) {
            Log.d(tag, "VIEWPORT_PAGE_REJECT reason=notReady phase=${currentEntryState.phase} " +
                "primaryPage=$primaryPage")
        }
    }

    LaunchedEffect(pageJump?.id, viewportSize.width) {
        if (currentEntryState.phase != ContinuousEntryPhase.READY) return@LaunchedEffect
        val request = pageJump ?: return@LaunchedEffect
        if (request.id == consumedJumpId || viewportSize.width <= 0) return@LaunchedEffect
        consumedJumpId = request.id
        cancelFling()
        val target = request.targetPageIndex.coerceIn(0, (state.pageCount - 1).coerceAtLeast(0))
        val jumped = update(
            documentState.copy(scrollY = geometry.pageTop(target) * documentState.scale),
        )
        if (!gestureCoordinator.isActive) {
            currentOnDocumentStateSettled(jumped, target)
        }
        currentOnPageJumpConsumed(request.id)
    }

    SubcomposeLayout(
        modifier = modifier
            .clipToBounds()
            .testTag("r7-continuous-viewport")
            .semantics { r7ContinuousScale = renderDocumentState.scale; r7TileCount = tileIndex.total }
            .onSizeChanged {
                viewportSize = it
                onViewportWidthPx(it.width)
            }
            // Stable V2 root detector: only document identity is a key.
            .pointerInput(documentId) {
                if (isDebuggable) {
                    Log.d(
                        tag,
                        "POINTER_DETECTOR_START documentId=$documentId " +
                            "timestamp=${System.currentTimeMillis()}",
                    )
                }
                try {
                    awaitPdfGestureLifecycle(
                        controller = sharedGestureController,
                        pass = PointerEventPass.Initial,
                    ) { down ->
                    val velocityTracker = VelocityTracker()
                    cancelFling()
                    sharedGestureController.beginGesture(
                        currentTransform = ZoomPanTransform(
                            scale = documentState.scale,
                            legalOffsetPx = ZoomPanPoint(-documentState.scrollX, -documentState.scrollY),
                        ),
                        initialPointers = listOf(
                            ZoomPanPointer(down.id.value, ZoomPanPoint(down.position.x, down.position.y)),
                        ),
                        boundsForScale = { targetScale ->
                            currentGeometry.zoomPanBounds(
                                targetScale,
                                viewportSize.width.toFloat(),
                                viewportSize.height.toFloat(),
                            )
                        },
                        overscrollPolicy = ZoomPanPhysicsProfile.ContinuousOverscrollPolicy,
                    )
                    velocityTracker.addPosition(down.uptimeMillis, down.position)
                    publishViewportSnapshot(documentState.copy(gestureActive = true))
                    var twoFinger = false
                    var pinchUsed = false
                    var gestureCapture: ContinuousGestureCapture? = null
                    var dragStarted = false
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2) {
                            tileFocusViewport = if(pressed.size>=2) Offset((pressed[0].position.x+pressed[1].position.x)/2f,(pressed[0].position.y+pressed[1].position.y)/2f) else null
                            if (!twoFinger) {
                                twoFinger = true
                                cancelFling()
                                val capture = gestureCoordinator.beginCapture()
                                if (capture == null) {
                                    if (isDebuggable) {
                                        Log.d(
                                            tag,
                                            "STALE_SNAPSHOT_REJECTED documentId=$documentId " +
                                                "currentGeneration=${gestureCoordinator.currentGeneration} snapshotVersion=" +
                                                "${gestureCoordinator.snapshots.capture()?.version ?: -1L} " +
                                                "timestamp=${System.currentTimeMillis()}",
                                        )
                                    }
                                } else {
                                    gestureCapture = capture
                                    pinchUsed = true
                                    val snapshot = capture.snapshot.payload
                                    val wasSettling = interaction.phase == ContinuousInteractionPhase.SETTLING
                                    if (wasSettling) {
                                        gestures.settleJob?.cancel()
                                        if (isDebuggable) {
                                            Log.d(tag, "SETTLE_CANCEL interactionId=${interaction.interactionId} pageIndex=${snapshot.viewportPage} " +
                                                "snapshotVersion=${capture.snapshot.version} generation=${capture.generation} " +
                                                "tier=$currentQualityTier timestamp=${System.currentTimeMillis()}")
                                            Log.d(tag, "INTERACTION_REUSE interactionId=${interaction.interactionId} pageIndex=${snapshot.viewportPage} " +
                                                "snapshotVersion=${capture.snapshot.version} generation=${capture.generation} " +
                                                "tier=$currentQualityTier timestamp=${System.currentTimeMillis()}")
                                            if (pendingBitmaps.isNotEmpty()) {
                                                Log.d(tag, "BITMAP_SWAP_DEFERRED interactionId=${interaction.interactionId} pageIndex=${snapshot.viewportPage} " +
                                                    "snapshotVersion=${capture.snapshot.version} generation=${capture.generation} " +
                                                    "tier=$currentQualityTier timestamp=${System.currentTimeMillis()}")
                                            }
                                        }
                                    }
                                    val frozenPages = if (wasSettling) {
                                        interaction.stableVisiblePages
                                    } else {
                                        snapshot.composedPages
                                    }
                                    val frozenBitmapIds = if (wasSettling) {
                                        interaction.stableDisplayedBitmapIds
                                    } else {
                                        snapshot.displayedBitmapIds.filterKeys { it in frozenPages }
                                    }
                                    interaction = ContinuousInteractionReducer.pinchStart(
                                        interaction,
                                        frozenPages,
                                        frozenBitmapIds,
                                        snapshot.documentState,
                                    )
                                    if (isDebuggable) {
                                        Log.d(
                                            tag,
                                            "GESTURE_SNAPSHOT generation=${capture.generation} " +
                                                "snapshotVersion=${capture.snapshot.version} " +
                                                "composedPages=${snapshot.composedPages} " +
                                                "visiblePages=${snapshot.visiblePages} " +
                                                "viewportPage=${snapshot.viewportPage} " +
                                                "bitmapIds=${snapshot.displayedBitmapIds} " +
                                                "pageBounds=${snapshot.pageBounds} " +
                                                "timestamp=${System.currentTimeMillis()}",
                                        )
                                        if (!wasSettling) {
                                            Log.d(tag, "INTERACTION_START interactionId=${interaction.interactionId} pageIndex=${snapshot.viewportPage} " +
                                                "snapshotVersion=${capture.snapshot.version} generation=${capture.generation} " +
                                                "tier=$currentQualityTier timestamp=${System.currentTimeMillis()}")
                                            Log.d(tag, "VISIBLE_SET_FREEZE interactionId=${interaction.interactionId} pageIndex=${snapshot.viewportPage} " +
                                                "pages=${interaction.stableVisiblePages} snapshotVersion=${capture.snapshot.version} " +
                                                "generation=${capture.generation} tier=$currentQualityTier " +
                                                "timestamp=${System.currentTimeMillis()}")
                                        }
                                    }
                                }
                            }
                            pressed.forEach { it.consume() }
                            if (gestureCapture != null) {
                                val frame = sharedGestureController.pointerFrame(
                                    pointers = pressed.map {
                                        ZoomPanPointer(it.id.value, ZoomPanPoint(it.position.x, it.position.y))
                                    },
                                    transformOriginViewportPx = ZoomPanPoint.Zero,
                                    boundsForScale = { targetScale ->
                                        currentGeometry.zoomPanBounds(
                                            targetScale,
                                            viewportSize.width.toFloat(),
                                            viewportSize.height.toFloat(),
                                        )
                                    },
                                    overscrollPolicy = ZoomPanPhysicsProfile.ContinuousOverscrollPolicy,
                                )
                                if (frame.rebased) {
                                    publishViewportSnapshot(documentState.copy(gestureActive = true), rebased = true)
                                    if (isDebuggable) {
                                        Log.d(tag, "POINTER_EPOCH_REBASE interactionId=${interaction.interactionId} " +
                                            "epochId=${frame.snapshot.pointerEpoch?.epochId} mode=${frame.snapshot.pointerEpoch?.mode} " +
                                            "pointerIds=${frame.snapshot.pointerEpoch?.activePointerIds} pair=" +
                                            "${frame.snapshot.pointerEpoch?.primaryPointerId},${frame.snapshot.pointerEpoch?.secondaryPointerId} " +
                                            "scale=${documentState.scale} scrollX=${documentState.scrollX} scrollY=${documentState.scrollY}")
                                        Log.d(tag, "POINTER_EPOCH_FRAME_SKIPPED interactionId=${interaction.interactionId} " +
                                            "epochId=${frame.snapshot.pointerEpoch?.epochId} reason=POINTER_SET_CHANGED")
                                    }
                                } else {
                                    val transformed = frame.snapshot.transform
                                    update(
                                        documentState.copy(
                                            scale = transformed.scale,
                                            scrollX = -transformed.legalOffsetPx.x,
                                            scrollY = -transformed.legalOffsetPx.y,
                                            gestureActive = true,
                                        ),
                                    )
                                }
                            }
                        } else if (pressed.size == 1) {
                            val change = pressed.first()
                            if (!twoFinger || gestureCapture != null) {
                                if (!twoFinger) {
                                    cancelFling()
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    // P0B1: track drag phase for display merge policy
                                    if (!dragStarted) {
                                        interaction = ContinuousInteractionReducer.dragStart(interaction)
                                        dragStarted = true
                                    }
                                }
                                if (gestureCapture != null || !twoFinger) {
                                    val frame = sharedGestureController.pointerFrame(
                                        pointers = listOf(
                                            ZoomPanPointer(
                                                change.id.value,
                                                ZoomPanPoint(change.position.x, change.position.y),
                                            ),
                                        ),
                                        transformOriginViewportPx = ZoomPanPoint.Zero,
                                        boundsForScale = { targetScale ->
                                            currentGeometry.zoomPanBounds(
                                                targetScale,
                                                viewportSize.width.toFloat(),
                                                viewportSize.height.toFloat(),
                                            )
                                        },
                                        overscrollPolicy = ZoomPanPhysicsProfile.ContinuousOverscrollPolicy,
                                    )
                                    if (frame.rebased) {
                                        publishViewportSnapshot(documentState.copy(gestureActive = true), rebased = true)
                                        if (isDebuggable) {
                                            Log.d(tag, "POINTER_EPOCH_REBASE interactionId=${interaction.interactionId} " +
                                                "epochId=${frame.snapshot.pointerEpoch?.epochId} mode=SINGLE_PAN pointerIds=${frame.snapshot.pointerEpoch?.activePointerIds}")
                                            Log.d(tag, "POINTER_EPOCH_FRAME_SKIPPED interactionId=${interaction.interactionId} " +
                                                "epochId=${frame.snapshot.pointerEpoch?.epochId} reason=2_TO_1")
                                        }
                                    } else {
                                        val transformed = frame.snapshot.transform
                                        update(
                                            documentState.copy(
                                                scale = transformed.scale,
                                                scrollX = -transformed.legalOffsetPx.x,
                                                scrollY = -transformed.legalOffsetPx.y,
                                                gestureActive = true,
                                            ),
                                        )
                                    }
                                } else {
                                    update(
                                        documentState.copy(
                                            gestureActive = true,
                                        ),
                                    )
                                }
                            }
                            change.consume()
                        }
                    } while (event.changes.any { it.pressed })
                    tileFocusViewport = null

                    val ended = documentState.copy(gestureActive = false)
                    update(ended)
                    val capture = gestureCapture
                    if (pinchUsed && capture != null) {
                        val now = System.currentTimeMillis()
                        interaction = ContinuousInteractionReducer.pinchEnd(interaction, ended, now)
                        publishViewportSnapshot(ended)
                        val settlingId = interaction.interactionId
                        val settlingGeneration = capture.generation
                        if (isDebuggable) {
                            Log.d(tag, "PINCH_END interactionId=$settlingId pageIndex=${capture.snapshot.payload.viewportPage} " +
                                "snapshotVersion=${capture.snapshot.version} generation=$settlingGeneration " +
                                "tier=$currentQualityTier timestamp=$now")
                        }
                        gestures.settleJob?.cancel()
                        gestures.settleJob = scope.launch {
                            try {
                                delay(ContinuousInteractionReducer.SETTLE_DEBOUNCE_MS)
                                val settleNow = System.currentTimeMillis()
                                if (!gestureCoordinator.isCurrent(settlingGeneration) ||
                                    interaction.interactionId != settlingId ||
                                    !ContinuousInteractionReducer.canSettle(interaction, settleNow)
                                ) return@launch

                                // P0B1: settle commit — best-of(displayed, pending) per page.
                                // Never downgrades displayed; only upgrades or keeps. Atomic single
                                // commit per page, no intermediate downgrade-then-upgrade.
                                val committed = mutableMapOf<Int, ImageBitmap>()
                                val committedMeta = mutableMapOf<Int, BitmapMetadata>()
                                val committedAspects = mutableMapOf<Int, Float>()
                                val requiredAtCommit = currentGeometry.composedPages(
                                    documentState.copy(gestureActive = false),
                                    viewportSize.height.toFloat(),
                                ).toSet()
                                for ((page, pendingBmp) in pendingBitmaps) {
                                    if (page !in requiredAtCommit) continue
                                    val pendingMeta = pendingMetadata[page] ?: continue
                                    val displayedBmp = displayedBitmaps[page]
                                    val displayedMeta = displayedMetadata[page]
                                    val displayedEntry = if (displayedBmp != null && displayedMeta != null) {
                                        DisplayedBitmap(displayedBmp, displayedMeta)
                                    } else null
                                    val pendingEntry = DisplayedBitmap(pendingBmp, pendingMeta)
                                    val winner = DisplayMergePolicy.settle(displayedEntry, pendingEntry)
                                    if (winner != null && winner.bitmap !== displayedBmp) {
                                        @Suppress("UNCHECKED_CAST")
                                        committed[page] = winner.bitmap as ImageBitmap
                                        committedMeta[page] = winner.metadata
                                        if (page in pendingAspects) {
                                            committedAspects[page] = pendingAspects[page]!!
                                        }
                                        if (isDebuggable) {
                                            Log.d(tag, "BITMAP_SWAP interactionId=$settlingId pageIndex=$page " +
                                                "snapshotVersion=${capture.snapshot.version} generation=$settlingGeneration " +
                                                "oldTier=${displayedMeta?.tier} newTier=${winner.metadata.tier} " +
                                                "oldBucket=${displayedMeta?.widthBucket} newBucket=${winner.metadata.widthBucket} " +
                                                "bitmapId=${System.identityHashCode(winner.bitmap)} " +
                                                "reason=SETTLE_BEST_OF timestamp=$settleNow")
                                        }
                                    }
                                }
                                if (committed.isNotEmpty()) {
                                    displayedBitmaps = displayedBitmaps + committed
                                    displayedMetadata = displayedMetadata + committedMeta
                                    displayedAspects = displayedAspects + committedAspects
                                }
                                pendingBitmaps = emptyMap()
                                pendingMetadata = emptyMap()
                                pendingAspects = emptyMap()
                                interaction = ContinuousInteractionReducer.settleCommit(interaction, settleNow)
                                val stable = documentState.copy(gestureActive = false)
                                val settledPrimary = currentGeometry.pageAtViewportCenter(
                                    stable,
                                    viewportSize.height.toFloat(),
                                )
                                if (isDebuggable) {
                                    Log.d(tag, "VISIBLE_SET_RECALCULATE interactionId=$settlingId pageIndex=$settledPrimary " +
                                        "snapshotVersion=${capture.snapshot.version} generation=$settlingGeneration " +
                                        "tier=$currentQualityTier timestamp=$settleNow")
                                    Log.d(tag, "SETTLE_COMMIT interactionId=$settlingId pageIndex=$settledPrimary " +
                                        "snapshotVersion=${capture.snapshot.version} generation=$settlingGeneration " +
                                        "tier=$currentQualityTier timestamp=$settleNow")
                                }
                                gestureCoordinator.finishAndCommit(settlingGeneration, stable) {
                                    currentOnDocumentStateSettled(it, settledPrimary)
                                    currentOnViewportSessionSettled(
                                        currentGeometry.viewportSessionAtAnchor(
                                            it, viewportSize.width.toFloat(), viewportSize.height.toFloat(), System.nanoTime(),
                                        ),
                                    )
                                }
                            } finally {
                                if (gestureCoordinator.isCurrent(settlingGeneration)) {
                                    interaction = ContinuousInteractionReducer.cancel(interaction)
                                }
                                gestureCoordinator.finish(settlingGeneration)
                            }
                        }
                    } else if (!twoFinger) {
                        // P0B1: transition DRAGGING → IDLE so display merge policy
                        // resumes upgrade-only behavior during fling/settle.
                        if (dragStarted) {
                            interaction = ContinuousInteractionReducer.dragEnd(interaction)
                            publishViewportSnapshot(ended)
                            dragStarted = false
                        }
                        val velocity = velocityTracker.calculateVelocity()
                        val fling = ContinuousLayoutMath.dominantAxisFlingVelocity(
                            velocity.x,
                            velocity.y,
                        )
                        startFling(Offset(fling.first, fling.second))
                    }
                }
                } finally {
                    gestures.settleJob?.cancel()
                    gestures.settleJob = null
                    cancelFling()
                    interaction = ContinuousInteractionReducer.cancel(interaction)
                    gestureCoordinator.cleanup()
                }
            },
    ) { constraints ->
        val width = constraints.maxWidth.coerceAtLeast(1)
        val height = constraints.maxHeight.coerceAtLeast(1)
        val pages = composedPages
        val placeables = pages.associateWith { page ->
            subcompose(page) {
                val bitmap = displayedBitmaps[page]
                DocumentPage(
                    pageAnnotation = pageAnnotation,
                    documentId = documentId,
                    page = page,
                    pageCount = state.pageCount,
                    bitmap = bitmap,
                    interactionId = interaction.interactionId,
                    bitmapTier = state.qualityTier,
                    isDebuggable = isDebuggable,
                    onEnsure = { onEnsureAround(page) },
                    onRetry = { onRetryPage(page) },
                    hasError = page == state.pageIndex && state.renderError != null,
                    tiles = tileIndex.forPage(page),
                    showBaseLayer = showBaseLayer,
                    tileOverlayAlpha = detailTileOverlayAlpha,
                    showTileDiagnostics = showTileDiagnostics,
                )
            }.first().measure(
                Constraints.fixed(width, geometry.pageHeight(page).roundToInt().coerceAtLeast(1)),
            )
        }
        layout(width, height) {
            pages.forEach { page ->
                val top = geometry.pageTop(page)
                placeables.getValue(page).placeWithLayer(0, top.roundToInt()) {
                    scaleX = renderDocumentState.scale
                    scaleY = renderDocumentState.scale
                    translationX = -renderDocumentState.scrollX
                    translationY = top * (renderDocumentState.scale - 1f) - renderDocumentState.scrollY
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                }
            }
        }
    }
}

/** Trigger tuple for the continuous-viewport drive collector (P1-1). */
private data class ContinuousViewportDriveInputs(
    val scrollX: Float,
    val scrollY: Float,
    val scale: Float,
    val gestureActive: Boolean,
    val interactionPhase: ContinuousInteractionPhase,
    val flingActive: Boolean,
    val visiblePages: Set<Int>,
)

/** Pre-grouped drawable tiles for one overlay publication (P1-7 hot-path index). */
internal class ContinuousTileIndex(private val byPage: Map<Int, List<PdfTileEntry>>) {
    val total: Int = byPage.values.sumOf { it.size }

    fun forPage(page: Int): List<PdfTileEntry> = byPage[page].orEmpty()
}

internal fun buildContinuousTileIndex(
    source: Collection<PdfTileEntry>,
    documentGeneration: Long,
    geometry: ContinuousDocumentGeometry,
    overlayEnabled: Boolean,
): ContinuousTileIndex {
    if (!overlayEnabled) return ContinuousTileIndex(emptyMap())
    val filtered = ArrayList<PdfTileEntry>(source.size)
    for (tile in source) {
        val page = tile.key.pageIndex
        if (tile.key.documentGeneration != documentGeneration) continue
        if (page !in 0 until geometry.pageCount) continue
        if (tile.key.geometryGeneration != geometry.pageGeometryGeneration(page)) continue
        if (tile.key.fittedPageWidthVersion != geometry.fittedPageWidthVersion) continue
        filtered += tile
    }
    val byPage = HashMap<Int, List<PdfTileEntry>>(filtered.size)
    for ((page, list) in filtered.groupBy { it.key.pageIndex }) {
        byPage[page] = PdfTileDisplay.forPage(list, page)
    }
    return ContinuousTileIndex(byPage)
}

private class ContinuousDocumentGestureState {
    val mutatorMutex = MutatorMutex()
    var flingJob: Job? = null
    var settleJob: Job? = null
}

/** Real Compose lifecycle boundary used by production and instrumentation tests. */
@Composable
internal fun ContinuousGestureLifecycleEffect(
    coordinator: ContinuousGestureCoordinator,
    onCleanup: () -> Unit = {},
) {
    val currentCleanup by rememberUpdatedState(onCleanup)
    DisposableEffect(coordinator) {
        onDispose {
            currentCleanup()
            coordinator.cleanup()
        }
    }
}

@Composable
private fun DocumentPage(
    documentId: Any,
    page: Int,
    pageCount: Int,
    bitmap: ImageBitmap?,
    interactionId: Long,
    bitmapTier: QualityTier,
    isDebuggable: Boolean,
    onEnsure: () -> Unit,
    onRetry: () -> Unit,
    hasError: Boolean,
    tiles: List<PdfTileEntry>,
    showBaseLayer: Boolean,
    tileOverlayAlpha: Float,
    showTileDiagnostics: Boolean,
    pageAnnotation: @Composable androidx.compose.foundation.layout.BoxScope.(Int) -> Unit = {},
) {
    val drawnTileKeys = remember(documentId, page) { mutableSetOf<com.m1racle.reader.pdf.PdfTileKey>() }
    val diagnosticPaint = remember(showTileDiagnostics) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.MAGENTA
            textSize = 10f
        }
    }
    DisposableEffect(documentId, page) {
        if (isDebuggable) {
            Log.d("M1racleReaderR71", "PAGE_COMPOSE interactionId=$interactionId pageIndex=$page " +
                "bitmapGeneration=-1 tier=$bitmapTier timestamp=${System.currentTimeMillis()}")
        }
        onDispose {
            if (isDebuggable) {
                Log.d("M1racleReaderR71", "PAGE_DISPOSE interactionId=$interactionId pageIndex=$page " +
                    "bitmapGeneration=-1 tier=$bitmapTier timestamp=${System.currentTimeMillis()}")
            }
        }
    }
    if (bitmap == null) {
        LaunchedEffect(page) { onEnsure() }
    }
    androidx.compose.material3.Surface(
        color = Color.White,
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = "PDF page ${page + 1} of $pageCount" },
    ) {
      Box(Modifier.fillMaxSize()) {
        if (showBaseLayer && bitmap == null) {
            when {
                hasError -> androidx.compose.material3.TextButton(onClick = onRetry) {
                    androidx.compose.material3.Text("Retry")
                }
                else -> {
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(Color.White))
                }
            }
        }
        if ((showBaseLayer && bitmap != null) || tiles.isNotEmpty()) Canvas(Modifier.fillMaxSize()) {
            if (showBaseLayer && bitmap != null) {
                drawImage(
                    image = bitmap,
                    dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)),
                    filterQuality = FilterQuality.High,
                )
            }
            tiles.forEach { tile ->
                val source = tile.contentRectPx
                val destination = tile.pageRect.roundedPixelRect()
                drawImage(
                    image=tile.bitmap,
                    srcOffset=IntOffset(source.left,source.top),
                    srcSize=IntSize(source.width.coerceAtLeast(1),source.height.coerceAtLeast(1)),
                    dstOffset=IntOffset(destination.left,destination.top),
                    dstSize=IntSize(destination.width.coerceAtLeast(1),destination.height.coerceAtLeast(1)),
                    alpha=tileOverlayAlpha,
                    filterQuality = FilterQuality.High,
                )
                if (showTileDiagnostics) {
                    drawRect(
                        color = Color.Magenta.copy(alpha = .7f),
                        topLeft = Offset(destination.left.toFloat(), destination.top.toFloat()),
                        size = Size(destination.width.toFloat(), destination.height.toFloat()),
                        style = Stroke(width = 1f),
                    )
                    val bleedRight = tile.bitmap.width - source.right
                    val bleedBottom = tile.bitmap.height - source.bottom
                    val label = "p=$page b=${tile.key.zoomBucket} ${tile.key.tileX}/${tile.key.tileY} " +
                        "rect=${tile.pageRect} content=${source.width}x${source.height} " +
                        "bleed=${source.left},${source.top},$bleedRight,$bleedBottom"
                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        destination.left + 2f,
                        destination.top + diagnosticPaint.textSize + 2f,
                        diagnosticPaint,
                    )
                }
                if(isDebuggable && drawnTileKeys.add(tile.key)) {
                    Log.d("M1racleReaderPdfZoom","TILE_FRAME_METRIC tileKey=${tile.key} firstCanvasDrawNanos=${System.nanoTime()} visibleCoveragePercent=-1 pageIndex=$page")
                }
            }
        }
        // R26.1 标注覆盖/选择层：渲染在页面内容之上（同页局部坐标系）。
        pageAnnotation(page)
      }
    }
}
