package com.markleaf.app.pdf

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class R7P0c14GestureRenderDecouplingTest {
    @Test fun `pointer down does not stop planner`() {
        assertTrue(TileRenderPipelinePolicy.plannerAllowed(TileInteractionPhase.DRAGGING))
    }

    @Test fun `dragging produces tile requests`() {
        assertTrue(requests(TileInteractionPhase.DRAGGING).isNotEmpty())
    }

    @Test fun `pinching produces tile requests`() {
        assertTrue(requests(TileInteractionPhase.PINCHING).isNotEmpty())
    }

    @Test fun `dragging ready tile can swap`() {
        assertTrue(TileRenderPipelinePolicy.swapAllowed(TileInteractionPhase.DRAGGING))
    }

    @Test fun `pinching ready tile can swap`() {
        assertTrue(TileRenderPipelinePolicy.swapAllowed(TileInteractionPhase.PINCHING))
    }

    @Test fun `settle is not first request trigger`() {
        assertTrue(requests(TileInteractionPhase.DRAGGING).first().requestedAtNanos > 0L)
        assertTrue(TileRenderPipelinePolicy.plannerAllowed(TileInteractionPhase.SETTLING))
    }

    @Test fun `sixty viewport frames conflate into one pending snapshot`() {
        val conflator = LatestViewportConflator<PdfViewportSnapshot>()
        repeat(60) { sequence -> conflator.offer(snapshot(sequence.toLong(), pointerDown = true)) }
        assertEquals(60L, conflator.offerCount())
        assertEquals(59L, conflator.pollLatest()!!.sequence)
        assertNull(conflator.pollLatest())
        assertEquals(1L, conflator.pollCount())
    }

    @Test fun `snapshot conflation processes latest viewport`() {
        val conflator = LatestViewportConflator<PdfViewportSnapshot>()
        conflator.offer(snapshot(1, rectTop = 100f))
        conflator.offer(snapshot(2, rectTop = 900f))
        assertEquals(900f, conflator.pollLatest()!!.viewportRectDocument.top, 0f)
    }

    @Test fun `small move retains in flight tile still requested`() {
        val a = key(x = 1)
        val b = key(x = 2)
        assertEquals(setOf(a), TileRenderPipelinePolicy.retainActive(setOf(a, b), setOf(a, key(x = 3))))
    }

    @Test fun `latest submit does not cancel still useful running tile`() = runBlocking {
        val queue = TileWorkQueue()
        queue.setDocumentGeneration(1)
        val running = request(key(x = 1), TilePriority.VISIBLE)
        val next = request(key(x = 2), TilePriority.VISIBLE)
        queue.submit(listOf(running), pageSizes(), TileInteractionPhase.DRAGGING.name)
        val ready = mutableListOf<PdfTileKey>()
        queue.runWorker(
            renderFn = { current, _ ->
                if (current.key == running.key) {
                    queue.submit(listOf(running, next), pageSizes(), TileInteractionPhase.DRAGGING.name)
                }
                TileWorkQueue.RenderOutcome.Success(bitmap, 64)
            },
            onTileReady = { ready += it.key },
        )
        assertEquals(running.key, ready.first())
        assertTrue(next.key in ready)
    }

    @Test fun `current focal and visible outrank page ahead`() {
        val ordered = TileRenderPipelinePolicy.ordered(
            listOf(
                request(key(x = 4), TilePriority.PAGE_AHEAD),
                request(key(x = 2), TilePriority.VISIBLE),
                request(key(x = 1), TilePriority.FOCUS),
            ),
        )
        assertEquals(listOf(TilePriority.FOCUS, TilePriority.VISIBLE, TilePriority.PAGE_AHEAD), ordered.map { it.priority })
    }

    @Test fun `page ahead cannot starve current page`() = runBlocking {
        val queue = TileWorkQueue()
        queue.setDocumentGeneration(1)
        val ahead = request(key(page = 1, x = 1), TilePriority.PAGE_AHEAD)
        val visible = request(key(page = 0, x = 2), TilePriority.VISIBLE)
        queue.submit(listOf(ahead, visible), pageSizes(0, 1), TileInteractionPhase.DRAGGING.name)
        val order = mutableListOf<PdfTileKey>()
        queue.runWorker(
            renderFn = { _, _ -> TileWorkQueue.RenderOutcome.Success(bitmap, 64) },
            onTileReady = { order += it.key },
        )
        assertEquals(visible.key, order.first())
    }

    @Test fun `tile swap cannot alter transform`() {
        val transform = ContinuousDocumentState(2.4f, 310f, 920f, gestureActive = true)
        val overlay = TileOverlaySnapshot(1, emptyMap(), 10)
        assertEquals(1L, overlay.sequence)
        assertEquals(ContinuousDocumentState(2.4f, 310f, 920f, true), transform)
    }

    @Test fun `tile emission does not change geometry generation`() {
        val geometry = ContinuousDocumentGeometry.create(1, 1080f, mapOf(0 to 1.414f), 10f)
        val before = geometry.pageGeometryGeneration(0)
        TileOverlaySnapshot(sequence = 2, entries = emptyMap(), publishedAtNanos = 20)
        assertEquals(before, geometry.pageGeometryGeneration(0))
    }

    @Test fun `pointer rebase frame keeps renderer allowed`() {
        val rebase = snapshot(4, pointerDown = true).copy(rebaseFrame = true, interactionPhase = TileInteractionPhase.PINCHING)
        assertTrue(rebase.rebaseFrame)
        assertTrue(TileRenderPipelinePolicy.plannerAllowed(rebase.interactionPhase))
    }

    @Test fun `fling keeps renderer working`() {
        assertTrue(TileRenderPipelinePolicy.plannerAllowed(TileInteractionPhase.FLINGING))
        assertTrue(TileRenderPipelinePolicy.swapAllowed(TileInteractionPhase.FLINGING))
    }

    @Test fun `renderer busy cannot block viewport producer`() {
        val conflator = LatestViewportConflator<PdfViewportSnapshot>()
        repeat(10_000) { conflator.offer(snapshot(it.toLong(), pointerDown = true)) }
        assertEquals(9_999L, conflator.pollLatest()!!.sequence)
        assertFalse(conflator.hasPending())
    }

    @Test fun `document generation change clears old pending requests`() {
        val queue = TileWorkQueue()
        queue.setDocumentGeneration(1)
        queue.submit(listOf(request(key(documentGeneration = 1), TilePriority.VISIBLE)), pageSizes(), TileInteractionPhase.DRAGGING.name)
        assertEquals(1, queue.pendingSize())
        queue.updateDocumentGeneration(2)
        assertEquals(0, queue.pendingSize())
    }

    @Test fun `hand down health records continuous swaps`() {
        val tracker = TileRenderHealthTracker()
        tracker.onViewport(snapshot(1, pointerDown = true), .25f)
        tracker.onRequests(3, 100_000_000)
        tracker.onSwap(200_000_000, 100)
        tracker.onSwap(300_000_000, 120)
        val health = tracker.snapshot(350_000_000, plannerActive = true, rendererActive = true)
        assertTrue(health.pointerDown)
        assertEquals(3, health.requestsLast500ms)
        assertEquals(2, health.swapsLast500ms)
    }

    @Test fun `coverage rises before finger release`() {
        val visible = setOf(key(x = 0), key(x = 1), key(x = 2), key(x = 3))
        val before = TileRenderPipelinePolicy.coverage(visible, setOf(key(x = 0)))
        val during = TileRenderPipelinePolicy.coverage(visible, setOf(key(x = 0), key(x = 1), key(x = 2)))
        assertTrue(during > before)
        assertEquals(.75f, during, 0f)
    }

    @Test fun `leading edge outranks near and page ahead`() {
        val ordered = TileRenderPipelinePolicy.ordered(
            listOf(
                request(key(x = 3), TilePriority.PAGE_AHEAD),
                request(key(x = 2), TilePriority.NEAR),
                request(key(x = 1), TilePriority.LEADING_EDGE),
            ),
        )
        assertEquals(listOf(TilePriority.LEADING_EDGE, TilePriority.NEAR, TilePriority.PAGE_AHEAD), ordered.map { it.priority })
    }

    @Test fun `only incompatible geometry forces full invalidation`() {
        val old = key()
        assertFalse(TileRenderPipelinePolicy.requiresFullInvalidation(old, old.copy(tileX = 1)))
        assertTrue(TileRenderPipelinePolicy.requiresFullInvalidation(old, old.copy(geometryGeneration = 99)))
        assertTrue(TileRenderPipelinePolicy.requiresFullInvalidation(old, old.copy(documentGeneration = 2)))
    }

    @Test fun `display keeps last-good lower bucket under a live bucket jump`() {
        fun entry(page: Int, bucket: Int, x: Int) = PdfTileEntry(
            key().copy(pageIndex = page, zoomBucket = bucket, tileX = x),
            bitmap,
            PageLayoutRect(0f, 0f, 64f, 64f),
            TilePixelRect(0, 0, 64, 64),
            1, 1, 64,
        )
        val drawn = PdfTileDisplay.forPage(
            listOf(entry(0, 2, 0), entry(0, 1, 0), entry(1, 1, 0)),
            pageIndex = 0,
        )
        assertEquals(listOf(1, 2), drawn.map { it.key.zoomBucket })
        assertTrue(drawn.none { it.key.pageIndex != 0 })
    }

    @Test fun `overlay snapshot preserves exact tile bitmap identity`() {
        val entry = PdfTileEntry(key(), bitmap, PageLayoutRect(0f, 0f, 64f, 64f), TilePixelRect(0, 0, 64, 64), 1, 1, 64)
        val overlay = TileOverlaySnapshot(1, mapOf(entry.key to entry), 2)
        assertSame(bitmap, overlay.entries.getValue(entry.key).bitmap)
    }

    private fun requests(phase: TileInteractionPhase): List<PdfTileRequest> {
        assertTrue(TileRenderPipelinePolicy.plannerAllowed(phase))
        val view = PageTileViewport(0, 1080f, 1527f, PageLayoutRect(100f, 100f, 800f, 900f))
        val now = System.nanoTime()
        return PdfTilePlanner.requests("doc", 1, view, 2, now, 400f, 400f)
            .map { it.copy(snapshotSequence = 1, requestedAtNanos = now) }
    }

    private fun snapshot(
        sequence: Long,
        pointerDown: Boolean = false,
        rectTop: Float = 0f,
    ) = PdfViewportSnapshot(
        documentGeneration = 1,
        geometryGeneration = 10,
        viewportRectDocument = DocumentViewportRect(0f, rectTop, 540f, rectTop + 800f),
        scale = 2f,
        focalPointViewport = Offset(270f, 400f),
        velocityX = 0f,
        velocityY = 200f,
        zoomVelocity = 0f,
        motionConfidence = .8f,
        interactionPhase = if (pointerDown) TileInteractionPhase.DRAGGING else TileInteractionPhase.IDLE,
        timestampNanos = sequence + 1,
        sequence = sequence,
        pointerDown = pointerDown,
        currentPage = 0,
    )

    private fun key(
        documentGeneration: Long = 1,
        page: Int = 0,
        x: Int = 0,
    ) = PdfTileKey("doc", documentGeneration, 10, page, 0, 1080, 2, x, 0)

    private fun request(key: PdfTileKey, priority: TilePriority) = PdfTileRequest(
        key = key,
        pageRect = PageLayoutRect(key.tileX * 64f, 0f, (key.tileX + 1) * 64f, 64f),
        renderScale = 1f,
        contentWidthPx = 64,
        contentHeightPx = 64,
        bleedPx = TilePixelInsets(0, 0, 0, 0),
        priority = priority,
        requestGeneration = 1,
        requestedAtNanos = key.tileX + 1L,
        snapshotSequence = 1,
    )

    private fun pageSizes(vararg pages: Int): Map<Int, PageLayoutSize> {
        val targetPages = if (pages.isEmpty()) intArrayOf(0) else pages
        return targetPages.associateWith { PageLayoutSize(1080f, 1527f) }
    }

    private val bitmap: ImageBitmap = object : ImageBitmap {
        override val width: Int = 64
        override val height: Int = 64
        override val colorSpace = ColorSpaces.Srgb
        override val hasAlpha: Boolean = false
        override val config: ImageBitmapConfig = ImageBitmapConfig.Argb8888
        override fun readPixels(
            buffer: IntArray,
            startX: Int,
            startY: Int,
            width: Int,
            height: Int,
            bufferOffset: Int,
            stride: Int,
        ) = Unit
        override fun prepareToDraw() = Unit
    }
}
