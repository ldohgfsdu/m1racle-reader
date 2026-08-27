package com.m1racle.reader.ui.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.asImageBitmap
import com.m1racle.reader.BuildConfig
import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.data.local.DocumentType
import com.m1racle.reader.pdf.ContinuousDocumentState
import com.m1racle.reader.pdf.ContinuousEntryPhase
import com.m1racle.reader.pdf.ContinuousEntryState
import com.m1racle.reader.pdf.ContinuousGestureCoordinator
import com.m1racle.reader.pdf.PdfTileEntry
import com.m1racle.reader.pdf.PdfTilePlan
import com.m1racle.reader.pdf.TileOverlaySnapshot
import com.m1racle.reader.pdf.TilePixelRect
import com.m1racle.reader.pdf.TileRenderPipelinePolicy
import com.m1racle.reader.ui.theme.M1racleReaderTheme
import kotlinx.coroutines.flow.MutableStateFlow

/** QA-build-only production continuous viewport used only for the P0C1.4 regression gate. */
class H1QaContinuousHarnessActivity : ComponentActivity() {
    private val coordinator = ContinuousGestureCoordinator { }
    private val overlays = MutableStateFlow(TileOverlaySnapshot())
    private val lock = Any()
    private var metrics = H1QaTileMetrics()
    @Volatile private var stableTransform = initialTransform()
    private val pageBitmap by lazy {
        Bitmap.createBitmap(720, 996, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }.asImageBitmap()
    }
    private val tileBitmap by lazy {
        Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }.asImageBitmap()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentInstance = this
        check(BuildConfig.APPLICATION_ID == "com.m1racle.reader.qa")
        setContent {
            M1racleReaderTheme {
                ContinuousDocumentViewport(
                    state = readyState(),
                    pageJump = null,
                    onPageJumpConsumed = {},
                    onViewportWidthPx = {},
                    onContinuousGeometryReady = {},
                    onContinuousEntryRestoreApplied = { _, _ -> },
                    onDocumentStateSettled = { next, _ -> stableTransform = next },
                    onZoomGestureActive = {},
                    onPageSettled = {},
                    onViewportChanged = {},
                    onTilePlan = ::onTilePlan,
                    tileOverlaySnapshots = overlays,
                    onEnsureAround = {},
                    onRetryPage = {},
                    gestureCoordinatorOverride = coordinator,
                )
            }
        }
    }

    override fun onDestroy() {
        if (currentInstance === this) currentInstance = null
        super.onDestroy()
    }

    internal fun tileMetrics(): H1QaTileMetrics = synchronized(lock) { metrics }
    internal fun transformSnapshot(): ContinuousDocumentState = stableTransform

    internal fun contentBoundsOnScreen(): Rect {
        val content = findViewById<android.view.View>(android.R.id.content)
        val location = IntArray(2)
        content.getLocationOnScreen(location)
        return Rect(location[0], location[1], location[0] + content.width, location[1] + content.height)
    }

    private fun onTilePlan(plan: PdfTilePlan) {
        if (!plan.snapshot.pointerDown || plan.requests.isEmpty()) return
        val beforeTransform = coordinator.snapshots.capture()?.payload?.documentState
        val readyBefore = overlays.value.entries.keys
        val entries = plan.requests
            .filter { it.key in plan.currentVisibleKeys }
            .associate { request ->
                request.key to PdfTileEntry(
                    key = request.key,
                    bitmap = tileBitmap,
                    pageRect = request.pageRect,
                    contentRectPx = TilePixelRect(0, 0, 16, 16),
                    renderedAtNanos = System.nanoTime(),
                    requestGeneration = request.requestGeneration,
                    byteSize = 1_024,
                    snapshotSequence = plan.snapshot.sequence,
                    interactionPhaseAtRequest = plan.snapshot.interactionPhase,
                    priorityAtRequest = request.priority,
                )
            }
        if (entries.isNotEmpty()) {
            overlays.value = TileOverlaySnapshot(
                sequence = overlays.value.sequence + 1,
                entries = overlays.value.entries + entries,
                publishedAtNanos = System.nanoTime(),
            )
        }
        val afterTransform = coordinator.snapshots.capture()?.payload?.documentState
        val coverageBefore = TileRenderPipelinePolicy.coverage(plan.currentVisibleKeys, readyBefore)
        val coverageAfter = TileRenderPipelinePolicy.coverage(
            plan.currentVisibleKeys,
            overlays.value.entries.keys,
        )
        synchronized(lock) {
            metrics = metrics.copy(
                pointerDownRequests = metrics.pointerDownRequests + plan.requests.size,
                pointerDownSwaps = metrics.pointerDownSwaps + entries.size,
                coverageBeforeRelease = maxOf(metrics.coverageBeforeRelease, coverageAfter),
                coverageIncreasedBeforeRelease = metrics.coverageIncreasedBeforeRelease ||
                    coverageAfter > coverageBefore,
                transformUnchangedBySwap = metrics.transformUnchangedBySwap &&
                    beforeTransform == afterTransform,
            )
        }
    }

    private fun readyState(): PdfViewerUiState.Ready = PdfViewerUiState.Ready(
        document = DocumentEntity(
            id = "h1-qa-continuous",
            displayName = "P0C1.4 QA PDF",
            uri = "content://h1-qa-continuous",
            mimeType = "application/pdf",
            fileSize = 1L,
            lastOpenedAt = 0L,
            createdAt = 0L,
            lastScrollIndex = 0,
            lastScrollOffset = 0,
            readingProgress = 0f,
            title = null,
            headingCount = 0,
            documentType = DocumentType.PDF.name,
            pageCount = PAGE_COUNT,
        ),
        pageIndex = 0,
        pageCount = PAGE_COUNT,
        pageImage = pageBitmap,
        isRendering = false,
        pageBitmaps = (0 until PAGE_COUNT).associateWith { pageBitmap },
        pageAspects = (0 until PAGE_COUNT).associateWith { 1.3833f },
        continuousDocumentState = initialTransform(),
        continuousEntryState = ContinuousEntryState(phase = ContinuousEntryPhase.READY),
    )

    companion object {
        const val PAGE_COUNT = 8
        @Volatile
        internal var currentInstance: H1QaContinuousHarnessActivity? = null

        private fun initialTransform() =
            ContinuousDocumentState(scale = 2.5f, scrollX = 240f, scrollY = 720f)
    }
}

internal data class H1QaTileMetrics(
    val pointerDownRequests: Int = 0,
    val pointerDownSwaps: Int = 0,
    val coverageBeforeRelease: Float = 0f,
    val coverageIncreasedBeforeRelease: Boolean = false,
    val transformUnchangedBySwap: Boolean = true,
)
