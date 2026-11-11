package com.m1racle.reader.ui.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.m1racle.reader.BuildConfig
import com.m1racle.reader.core.model.PdfViewportSessionState
import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.data.local.DocumentType
import com.m1racle.reader.pdf.ContinuousDocumentState
import com.m1racle.reader.pdf.ContinuousEntryPhase
import com.m1racle.reader.pdf.ContinuousEntryState
import com.m1racle.reader.pdf.ContinuousGestureCoordinator
import com.m1racle.reader.pdf.ContinuousScrollPhase
import com.m1racle.reader.pdf.ContinuousViewportSnapshot
import com.m1racle.reader.pdf.ReadingPosition
import com.m1racle.reader.pdf.ReadingPositionSource
import com.m1racle.reader.ui.theme.M1racleReaderTheme

/**
 * QA-only host for injecting a native page-size arrival during vertical motion.
 *
 * The late aspect is applied only after production has emitted measurable drag/
 * fling frames. Tests can therefore verify the shipped viewport transform
 * instead of substituting a second gesture or geometry implementation.
 */
class PdfMotionAspectHarnessActivity : ComponentActivity() {

    enum class LateAspectTrigger {
        DRAGGING,
        FLINGING,
    }

    data class TimedSnapshot(
        val observedAtNanos: Long,
        val snapshot: ContinuousViewportSnapshot,
    )

    private data class ArmedInjection(
        val trigger: LateAspectTrigger,
        val pageIndex: Int,
        val aspect: Float,
        val minimumMotionFrames: Int,
    )

    private val frameLock = Any()
    private val frames = mutableListOf<TimedSnapshot>()
    private var armedInjection: ArmedInjection? = null
    private var armedMotionFrames = 0
    private var injectionFrameOrdinal = -1
    private var injectedAtNanos = -1L
    private var injectedPhase: ContinuousScrollPhase? = null

    private val placeholderBitmap: ImageBitmap by lazy { bitmap(Color.LTGRAY) }
    private val lateAspectBitmap: ImageBitmap by lazy { bitmap(Color.DKGRAY) }
    private val coordinator = ContinuousGestureCoordinator { }

    private var state by mutableStateOf(readyState())
    private var pageJump by mutableStateOf<PdfViewerViewModel.PageJumpRequest?>(null)
    private var geometryResetGeneration by mutableStateOf(0)

    @Volatile
    private var settledPageIndex: Int? = null

    @Volatile
    private var restoredShortPageApplied = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(BuildConfig.APPLICATION_ID == "com.m1racle.reader.qa") {
            "PDF motion harness must never run in the production package"
        }
        setContent {
            M1racleReaderTheme {
                key(geometryResetGeneration) {
                    ContinuousDocumentViewport(
                        state = state,
                        pageJump = pageJump,
                        onPageJumpConsumed = { requestId ->
                            if (pageJump?.id == requestId) pageJump = null
                        },
                        onViewportWidthPx = {},
                        onContinuousGeometryReady = {},
                        onContinuousEntryRestoreApplied = { position, applied ->
                            state = state.copy(
                                pageIndex = position.pageIndex,
                                continuousDocumentState = applied,
                                continuousEntryState = ContinuousEntryState(
                                    phase = ContinuousEntryPhase.READY,
                                    lastStableReadingPosition = position,
                                ),
                            )
                            restoredShortPageApplied = true
                        },
                        onDocumentStateSettled = { _, page ->
                            page?.let { settledPageIndex = it }
                        },
                        onZoomGestureActive = {},
                        onPageSettled = ::recordSettledPage,
                        onViewportChanged = ::recordSnapshot,
                        onEnsureAround = {},
                        onRetryPage = {},
                        gestureCoordinatorOverride = coordinator,
                    )
                }
            }
        }
    }

    internal fun requestPageJump(pageIndex: Int) {
        runOnUiThread {
            pageJump = PdfViewerViewModel.PageJumpRequest(
                id = System.nanoTime(),
                targetPageIndex = pageIndex,
                source = "pdf-motion-aspect-harness",
            )
        }
    }

    internal fun prepareRestoredShortPage() {
        runOnUiThread {
            restoredShortPageApplied = false
            state = state.copy(
                pageIndex = 1,
                pageAspects = (1 until PAGE_COUNT).associateWith {
                    if (it == 1) 431f / 997f else PAGE_ASPECT
                },
                continuousEntryState = ContinuousEntryState(
                    phase = ContinuousEntryPhase.APPLYING_TARGET,
                    pendingRestorePosition = ReadingPosition(
                        pageIndex = 1,
                        intraPageFraction = 0.5f,
                        source = ReadingPositionSource.STARTUP_RESTORE,
                        generation = System.nanoTime(),
                    ),
                ),
            )
        }
    }

    internal fun isRestoredShortPageApplied(): Boolean = restoredShortPageApplied

    internal fun publishPageAspect(pageIndex: Int, aspect: Float) {
        require(pageIndex in 0 until PAGE_COUNT && aspect in 0.4f..3f)
        runOnUiThread {
            state = state.copy(
                pageAspects = state.pageAspects + (pageIndex to aspect),
                pageBitmaps = state.pageBitmaps + (pageIndex to lateAspectBitmap),
            )
        }
    }

    internal fun armLateAspectInjection(
        trigger: LateAspectTrigger,
        pageIndex: Int,
        aspect: Float,
        minimumMotionFrames: Int = 3,
    ) {
        require(pageIndex in 0 until PAGE_COUNT)
        require(aspect in 0.4f..3f)
        synchronized(frameLock) {
            frames.clear()
            armedInjection = ArmedInjection(
                trigger = trigger,
                pageIndex = pageIndex,
                aspect = aspect,
                minimumMotionFrames = minimumMotionFrames.coerceAtLeast(1),
            )
            armedMotionFrames = 0
            injectionFrameOrdinal = -1
            injectedAtNanos = -1L
            injectedPhase = null
        }
        settledPageIndex = null
    }

    internal fun snapshots(): List<TimedSnapshot> =
        synchronized(frameLock) { frames.toList() }

    internal fun injectionFrameOrdinal(): Int =
        synchronized(frameLock) { injectionFrameOrdinal }

    internal fun injectedAtNanos(): Long =
        synchronized(frameLock) { injectedAtNanos }

    internal fun injectedPhase(): ContinuousScrollPhase? =
        synchronized(frameLock) { injectedPhase }

    internal fun latestPhase(): ContinuousScrollPhase? =
        synchronized(frameLock) { frames.lastOrNull()?.snapshot?.phase }

    internal fun settledPage(): Int? = settledPageIndex

    internal fun enableFitWidthShortPageWindow() {
        runOnUiThread {
            geometryResetGeneration += 1
            state = state.copy(
                pageIndex = 0,
                pageAspects = (0 until PAGE_COUNT).associateWith { SHORT_PAGE_ASPECT },
                continuousDocumentState = ContinuousDocumentState(),
            )
        }
    }

    internal fun capturePayload(): com.m1racle.reader.pdf.ContinuousGestureSnapshotPayload? =
        coordinator.snapshots.capture()?.payload

    internal fun captureTopAnchor(): PdfViewportSessionState? {
        val payload = capturePayload() ?: return null
        return payload.geometry.viewportSessionAtAnchor(
            state = payload.documentState,
            viewportWidth = payload.viewportWidth.toFloat(),
            viewportHeight = payload.viewportHeight.toFloat(),
            generation = System.nanoTime(),
            viewportAnchorYFraction = 0f,
        )
    }

    internal fun capturedPageAspect(pageIndex: Int): Float? {
        val payload = capturePayload() ?: return null
        if (pageIndex !in 0 until payload.geometry.pageCount) return null
        return payload.geometry.pageHeight(pageIndex) / payload.geometry.basePageWidth
    }

    private fun recordSnapshot(snapshot: ContinuousViewportSnapshot) {
        val observedAt = System.nanoTime()
        val injection = synchronized(frameLock) {
            frames += TimedSnapshot(observedAt, snapshot)
            val armed = armedInjection ?: return@synchronized null
            val matchingPhase = when (armed.trigger) {
                LateAspectTrigger.DRAGGING -> snapshot.phase == ContinuousScrollPhase.DRAGGING
                LateAspectTrigger.FLINGING -> snapshot.phase == ContinuousScrollPhase.FLINGING
            }
            if (!matchingPhase || snapshot.contentAnchorDeltaY == 0f) return@synchronized null

            armedMotionFrames += 1
            if (armedMotionFrames < armed.minimumMotionFrames) return@synchronized null

            armedInjection = null
            injectionFrameOrdinal = frames.lastIndex
            injectedAtNanos = observedAt
            injectedPhase = snapshot.phase
            armed
        } ?: return

        // Publish both identity and size together, matching the production merge
        // path where a completed page preview supplies a bitmap and its aspect.
        state = state.copy(
            pageBitmaps = state.pageBitmaps + (injection.pageIndex to lateAspectBitmap),
            pageAspects = state.pageAspects + (injection.pageIndex to injection.aspect),
        )
    }

    private fun recordSettledPage(position: ReadingPosition) {
        settledPageIndex = position.pageIndex
    }

    private fun readyState(): PdfViewerUiState.Ready = PdfViewerUiState.Ready(
        document = DocumentEntity(
            id = "pdf-motion-aspect-qa",
            displayName = "PDF late aspect motion fixture.pdf",
            uri = "content://pdf-motion-aspect-qa",
            mimeType = "application/pdf",
            fileSize = 1L,
            lastOpenedAt = 0L,
            createdAt = 0L,
            lastScrollIndex = 1,
            lastScrollOffset = 0,
            readingProgress = 1f / (PAGE_COUNT - 1),
            title = null,
            headingCount = 0,
            documentType = DocumentType.PDF.name,
            pageCount = PAGE_COUNT,
        ),
        pageIndex = 1,
        pageCount = PAGE_COUNT,
        pageImage = placeholderBitmap,
        isRendering = false,
        // Page 0 intentionally remains on the geometry placeholder until motion
        // starts. Changing its real aspect moves page 1's top unless frozen.
        pageBitmaps = (0 until PAGE_COUNT).associateWith { placeholderBitmap },
        pageAspects = (1 until PAGE_COUNT).associateWith { PAGE_ASPECT },
        continuousDocumentState = ContinuousDocumentState(),
        continuousEntryState = ContinuousEntryState(phase = ContinuousEntryPhase.READY),
    )

    private fun bitmap(color: Int): ImageBitmap =
        Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
            eraseColor(color)
        }.asImageBitmap()

    companion object {
        private const val PAGE_COUNT = 8
        // Tall pages leave room for several native fling frames without a page
        // switch resetting the same-page content-anchor measurement.
        private const val PAGE_ASPECT = 3f
        private const val SHORT_PAGE_ASPECT = 0.4f
    }
}
