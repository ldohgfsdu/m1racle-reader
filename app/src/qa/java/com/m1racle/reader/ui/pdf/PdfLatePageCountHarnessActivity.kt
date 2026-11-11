package com.m1racle.reader.ui.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
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
import com.m1racle.reader.ui.theme.M1racleReaderTheme

/**
 * QA-only host for the VM's document-wide aspect accumulation contract.
 *
 * It starts with a deliberately narrow bitmap window. Tests first move far
 * enough that page 0 leaves that window, then return while separately changing
 * the logical page count. Production geometry, gesture capture, and bitmap
 * retention remain the shipped implementation.
 */
class PdfLatePageCountHarnessActivity : ComponentActivity() {

    data class TimedSnapshot(
        val observedAtNanos: Long,
        val pageCount: Int,
        val snapshot: ContinuousViewportSnapshot,
    )

    private val frameLock = Any()
    private val frames = mutableListOf<TimedSnapshot>()
    private var latePageCountArmed = false
    private var armedPageCount = -1
    private var latePageCountAppliedAtNanos = -1L
    private var appliedMotionPhase: ContinuousScrollPhase? = null
    private var injectedPageCount = -1

    private val pageBitmap: ImageBitmap by lazy { bitmap(Color.WHITE) }
    private val coordinator = ContinuousGestureCoordinator { }

    private var state by mutableStateOf(readyState())
    private var pageJump by mutableStateOf<PdfViewerViewModel.PageJumpRequest?>(null)

    @Volatile
    private var settledPageIndex: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(BuildConfig.APPLICATION_ID == "com.m1racle.reader.qa") {
            "PDF late page-count harness must never run in the production package"
        }
        setContent {
            M1racleReaderTheme {
                ContinuousDocumentViewport(
                    state = state,
                    pageJump = pageJump,
                    onPageJumpConsumed = { requestId ->
                        if (pageJump?.id == requestId) pageJump = null
                    },
                    onViewportWidthPx = {},
                    onContinuousGeometryReady = {},
                    onContinuousEntryRestoreApplied = { _, _ -> },
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

    internal fun requestPageJump(pageIndex: Int) {
        runOnUiThread {
            pageJump = PdfViewerViewModel.PageJumpRequest(
                id = System.nanoTime(),
                targetPageIndex = pageIndex,
                source = "pdf-late-page-count-harness",
            )
        }
    }

    /**
     * The next drag/fling frame publishes a larger logical page count while
     * keeping every existing aspect. This models the VM retaining a document-
     * wide aspect map independently of the current bitmap window.
     */
    internal fun armLatePageCountDuringNextMotion(newPageCount: Int = LATE_PAGE_COUNT) {
        require(newPageCount > PAGE_COUNT)
        synchronized(frameLock) {
            frames.clear()
            latePageCountArmed = true
            armedPageCount = newPageCount
            latePageCountAppliedAtNanos = -1L
            appliedMotionPhase = null
            injectedPageCount = -1
        }
        settledPageIndex = null
    }

    internal fun snapshots(): List<TimedSnapshot> =
        synchronized(frameLock) { frames.toList() }

    internal fun latePageCountAppliedAtNanos(): Long =
        synchronized(frameLock) { latePageCountAppliedAtNanos }

    internal fun appliedMotionPhase(): ContinuousScrollPhase? =
        synchronized(frameLock) { appliedMotionPhase }

    internal fun injectedPageCount(): Int =
        synchronized(frameLock) { injectedPageCount }

    internal fun latestPhase(): ContinuousScrollPhase? =
        synchronized(frameLock) { frames.lastOrNull()?.snapshot?.phase }

    internal fun settledPage(): Int? = settledPageIndex

    internal fun pageAspect(pageIndex: Int): Float? = state.pageAspects[pageIndex]

    internal fun currentPageCount(): Int = state.pageCount

    internal fun captureTopAnchor(): PdfViewportSessionState? {
        val payload = coordinator.snapshots.capture()?.payload ?: return null
        return payload.geometry.viewportSessionAtAnchor(
            state = payload.documentState,
            viewportWidth = payload.viewportWidth.toFloat(),
            viewportHeight = payload.viewportHeight.toFloat(),
            generation = System.nanoTime(),
            viewportAnchorYFraction = 0f,
        )
    }

    internal fun capturedPageAspect(pageIndex: Int): Float? {
        val payload = coordinator.snapshots.capture()?.payload ?: return null
        if (pageIndex !in 0 until payload.geometry.pageCount) return null
        return payload.geometry.pageHeight(pageIndex) / payload.geometry.basePageWidth
    }

    internal fun capturedDocumentState() =
        coordinator.snapshots.capture()?.payload?.documentState

    private fun recordSnapshot(snapshot: ContinuousViewportSnapshot) {
        val observedAt = System.nanoTime()
        val injection = synchronized(frameLock) {
            frames += TimedSnapshot(observedAt, state.pageCount, snapshot)
            if (!latePageCountArmed || snapshot.phase == ContinuousScrollPhase.IDLE) {
                return@synchronized null
            }
            if (snapshot.contentAnchorDeltaY == 0f) return@synchronized null

            latePageCountArmed = false
            val nextPageCount = armedPageCount
            armedPageCount = -1
            latePageCountAppliedAtNanos = observedAt
            appliedMotionPhase = snapshot.phase
            injectedPageCount = nextPageCount
            nextPageCount
        } ?: return

        state = state.copy(
            pageCount = injection,
            document = state.document.copy(pageCount = injection),
            pageAspects = state.pageAspects + (PREVIOUSLY_VISITED_PAGE to PREVIOUSLY_VISITED_ASPECT),
        )
    }

    private fun recordSettledPage(position: ReadingPosition) {
        settledPageIndex = position.pageIndex
    }

    private fun readyState(): PdfViewerUiState.Ready = PdfViewerUiState.Ready(
        document = DocumentEntity(
            id = "pdf-late-page-count-qa",
            displayName = "PDF late page count fixture.pdf",
            uri = "content://pdf-late-page-count-qa",
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
        pageImage = pageBitmap,
        isRendering = false,
        pageBitmaps = (1 until PAGE_COUNT).associateWith { pageBitmap },
        pageAspects = mapOf(1 to PAGE_ASPECT),
        continuousDocumentState = ContinuousDocumentState(),
        continuousEntryState = ContinuousEntryState(phase = ContinuousEntryPhase.READY),
    )

    private fun bitmap(color: Int): ImageBitmap =
        Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
            eraseColor(color)
        }.asImageBitmap()

    companion object {
        private const val PAGE_COUNT = 4
        private const val LATE_PAGE_COUNT = 8
        private const val PREVIOUSLY_VISITED_PAGE = 0
        private const val PAGE_ASPECT = 2f
        private const val PREVIOUSLY_VISITED_ASPECT = 0.55f
    }
}
