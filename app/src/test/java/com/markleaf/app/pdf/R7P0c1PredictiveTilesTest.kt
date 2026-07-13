package com.markleaf.app.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class R7P0c1PredictiveTilesTest {
    private val geometry = ContinuousDocumentGeometry.create(
        3,
        1000f,
        mapOf(0 to 1f, 1 to 1f, 2 to 1f),
        10f,
    )

    private fun viewport(
        page: Int = 0,
        width: Float = 1000f,
        height: Float = 1000f,
        rect: PageLayoutRect = PageLayoutRect(0f, 0f, 500f, 500f),
    ) = PageTileViewport(page, width, height, rect)

    @Test fun `viewport maps only intersecting pages`() {
        val visible = PdfTilePlanner.visiblePages(
            geometry,
            ContinuousDocumentState(2f, 0f, 1900f),
            1000f,
            800f,
        )
        assertEquals(listOf(0, 1), visible.map { it.pageIndex })
    }

    @Test fun `stable grid reuses keys while viewport moves`() {
        val a = viewport(rect = PageLayoutRect(100f, 100f, 600f, 600f))
        val b = a.copy(visibleRect = PageLayoutRect(110f, 110f, 610f, 610f))
        val keysA = PdfTilePlanner.requests("d", 1, a, 2, 1, null, null).map { it.key }.toSet()
        val keysB = PdfTilePlanner.requests("d", 1, b, 2, 2, null, null).map { it.key }.toSet()
        assertTrue(keysA.intersect(keysB).isNotEmpty())
    }

    @Test fun `edge tile clamps to page`() {
        val requests = PdfTilePlanner.requests(
            "d", 1, viewport(rect = PageLayoutRect(950f, 950f, 1000f, 1000f)), 4, 1, null, null,
        )
        assertTrue(requests.all { it.pageRect.right <= 1000f && it.pageRect.bottom <= 1000f })
    }

    @Test fun `bucket selection finite`() {
        assertEquals(0, PdfZoomBuckets.index(1f))
        assertEquals(4, PdfZoomBuckets.index(4f))
        assertEquals(5, PdfZoomBuckets.scales.size)
    }

    @Test fun `bucket hysteresis holds near midpoint`() =
        assertEquals(2, PdfZoomBuckets.index(2.35f, 2))

    @Test fun `zoom predictor looks ahead upward`() {
        var prediction = PdfZoomPredictor.update(ZoomPrediction(), 2f, 1_000_000_000, false)
        prediction = PdfZoomPredictor.update(prediction, 2.2f, 1_100_000_000, false)
        assertTrue(PdfZoomPredictor.predicted(prediction) > 2.2f)
    }

    @Test fun `rebase does not alter velocity`() {
        val prediction = ZoomPrediction(2f, 2f, 10, 1)
        assertEquals(2f, PdfZoomPredictor.update(prediction, 3f, 20, true).velocity, 0f)
    }

    @Test fun `focus outranks center visible and margin`() {
        val request = PdfTilePlanner.requests(
            "d", 1, viewport(rect = PageLayoutRect(100f, 100f, 800f, 800f)), 2, 1, 150f, 150f,
        )
        assertEquals(TilePriority.FOCUS, request.first().priority)
        assertTrue(request.last().priority.ordinal >= TilePriority.VISIBLE.ordinal)
    }

    @Test fun `request keys deduplicate`() {
        val requests = PdfTilePlanner.requests("d", 1, viewport(), 2, 1, null, null)
        assertEquals(requests.size, requests.map { it.key }.distinct().size)
    }

    @Test fun `document generation changes every key`() {
        val a = PdfTilePlanner.requests("d", 1, viewport(), 2, 1, null, null)
        val b = PdfTilePlanner.requests("d", 2, viewport(), 2, 1, null, null)
        assertTrue(a.map { it.key }.toSet().intersect(b.map { it.key }.toSet()).isEmpty())
    }

    @Test fun `different zoom buckets never mix keys`() {
        assertNotEquals(
            PdfTilePlanner.requests("d", 1, viewport(), 2, 1, null, null).first().key,
            PdfTilePlanner.requests("d", 1, viewport(), 4, 1, null, null).first().key,
        )
    }

    @Test fun `bleed does not change logical rect`() {
        val request = PdfTilePlanner.requests(
            "d", 1, viewport(rect = PageLayoutRect(0f, 0f, 200f, 200f)), 2, 1, null, null,
        ).first()
        assertEquals(0f, request.pageRect.left, 0f)
        assertEquals(request.contentWidthPx + 4, request.outputWidth)
        assertEquals(TilePixelRect(2, 2, 514, 514), request.contentRectPx)
    }

    @Test fun `two visible pages request no third page`() {
        val views = PdfTilePlanner.visiblePages(
            geometry,
            ContinuousDocumentState(1f, 0f, 800f),
            1000f,
            500f,
        )
        assertEquals(setOf(0, 1), views.map { it.pageIndex }.toSet())
    }

    @Test fun `tile planning does not mutate document state`() {
        val state = ContinuousDocumentState(3f, 20f, 500f)
        PdfTilePlanner.visiblePages(geometry, state, 1000f, 800f)
        assertEquals(ContinuousDocumentState(3f, 20f, 500f), state)
    }

    @Test fun `shrinking prediction moves downward`() {
        var prediction = PdfZoomPredictor.update(ZoomPrediction(), 3f, 1_000_000_000, false)
        prediction = PdfZoomPredictor.update(prediction, 2.7f, 1_100_000_000, false)
        assertTrue(PdfZoomPredictor.predicted(prediction) < 2.7f)
    }

    @Test fun `abnormal delta is excluded`() {
        val prediction = ZoomPrediction(1f, 2f, 1_000_000_000, 1)
        assertEquals(1f, PdfZoomPredictor.update(prediction, 4f, 1_001_000_000, false).velocity, 0f)
    }

    @Test fun `center tile outranks other visible tiles`() {
        val view = viewport(rect = PageLayoutRect(0f, 0f, 900f, 900f))
        assertEquals(TilePriority.CENTER, PdfTilePlanner.requests("d", 1, view, 2, 1, null, null).first().priority)
    }

    @Test fun `margin requests surround visible area`() {
        val view = viewport(rect = PageLayoutRect(400f, 400f, 500f, 500f))
        assertTrue(PdfTilePlanner.requests("d", 1, view, 2, 1, null, null).any { it.priority == TilePriority.MARGIN })
    }

    @Test fun `fitted page width version is independent of zoom bucket`() {
        val a = PdfTilePlanner.requests("d", 1, viewport(), 1, 1, null, null).first().key
        val b = PdfTilePlanner.requests("d", 1, viewport(), 4, 1, null, null).first().key
        assertEquals(a.fittedPageWidthVersion, b.fittedPageWidthVersion)
        assertNotEquals(a.zoomBucket, b.zoomBucket)
    }

    @Test fun `request generation does not destroy stable cache key`() {
        assertEquals(
            PdfTilePlanner.requests("d", 1, viewport(), 2, 1, null, null).first().key,
            PdfTilePlanner.requests("d", 1, viewport(), 2, 2, null, null).first().key,
        )
    }

    @Test fun `document identity separates tiles`() {
        assertNotEquals(
            PdfTilePlanner.requests("a", 1, viewport(), 2, 1, null, null).first().key,
            PdfTilePlanner.requests("b", 1, viewport(), 2, 1, null, null).first().key,
        )
    }

    @Test fun `page identity separates tiles`() {
        val view = viewport()
        assertNotEquals(
            PdfTilePlanner.requests("d", 1, view, 2, 1, null, null).first().key,
            PdfTilePlanner.requests("d", 1, view.copy(pageIndex = 1), 2, 1, null, null).first().key,
        )
    }

    @Test fun `scale four uses fixed output tile size`() {
        val requests = PdfTilePlanner.requests("d", 1, viewport(), 4, 1, null, null)
        assertEquals(4, requests.first().key.zoomBucket)
        assertTrue(requests.all { it.outputWidth <= 516 })
    }

    @Test fun `pointer epoch does not participate in tile key`() {
        val key = PdfTileKey("d", 1, 2, 0, 0, 1000, 2, 0, 0)
        assertEquals(key, key.copy())
    }

    @Test fun `byte lru evicts oldest until under budget`() {
        val a = PdfTileKey("d", 1, 2, 0, 0, 1000, 2, 0, 0)
        val b = a.copy(tileX = 1)
        val c = a.copy(tileX = 2)
        assertEquals(listOf(a, b), PdfTileLruPolicy.evictOldest(listOf(a to 40L, b to 40L, c to 40L), emptySet(), 50L))
    }

    @Test fun `byte lru pins visible tile`() {
        val a = PdfTileKey("d", 1, 2, 0, 0, 1000, 2, 0, 0)
        val b = a.copy(tileX = 1)
        assertEquals(listOf(b), PdfTileLruPolicy.evictOldest(listOf(a to 40L, b to 40L), setOf(a), 40L))
    }
}
