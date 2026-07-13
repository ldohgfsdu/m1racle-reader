package com.markleaf.app.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuousDocumentGeometryTest {
    private val geometry = ContinuousDocumentGeometry.create(
        pageCount = 4,
        basePageWidth = 1000f,
        aspects = mapOf(0 to 1f, 1 to 1.5f, 2 to 1f, 3 to 1f),
        pageGap = 20f,
    )

    @Test fun `scale is continuous and clamped to one through four`() {
        assertEquals(1f, geometry.clamp(ContinuousDocumentState(scale = .2f), 1000f, 800f).scale, 0f)
        assertEquals(2.37f, geometry.clamp(ContinuousDocumentState(scale = 2.37f), 1000f, 800f).scale, 0f)
        assertEquals(4f, geometry.clamp(ContinuousDocumentState(scale = 8f), 1000f, 800f).scale, 0f)
    }

    @Test fun `zoom anchor preserves document point at all centroid corners`() {
        listOf(0f to 0f, 500f to 400f, 1000f to 800f).forEach { (x, y) ->
            val before = ContinuousDocumentState(scale = 1.6f, scrollX = 250f, scrollY = 900f)
            val docX = (before.scrollX + x) / before.scale
            val docY = (before.scrollY + y) / before.scale
            val after = geometry.zoomAt(before, 2.7f, x, y, 1000f, 800f)
            assertEquals(docX, (after.scrollX + x) / after.scale, 0.001f)
            assertEquals(docY, (after.scrollY + y) / after.scale, 0.001f)
        }
    }

    @Test fun `fit width always centers horizontally`() {
        val state = geometry.clamp(ContinuousDocumentState(scale = 1f, scrollX = 400f), 1000f, 800f)
        assertEquals(0f, state.scrollX, 0f)
    }

    @Test fun `horizontal and vertical boundaries clamp`() {
        val state = geometry.clamp(ContinuousDocumentState(scale = 2f, scrollX = 9999f, scrollY = 99999f), 1000f, 800f)
        assertEquals(1000f, state.scrollX, 0f)
        assertEquals(geometry.maxScrollY(2f, 800f), state.scrollY, 0f)
    }

    @Test fun `pages compose only visible range with small prefetch`() {
        val longDocument = ContinuousDocumentGeometry.create(
            pageCount = 982,
            basePageWidth = 1000f,
            aspects = emptyMap(),
            pageGap = 20f,
        )
        val pages = longDocument.composedPages(ContinuousDocumentState(scale = 1f, scrollY = 1050f), 700f)
        assertTrue(pages.count() <= 5)
        assertTrue(pages.count() < longDocument.pageCount)
    }

    @Test fun `center page crosses boundaries without resetting document scroll`() {
        assertEquals(1, geometry.pageAtViewportCenter(ContinuousDocumentState(scrollY = 900f), 800f))
        assertEquals(2, geometry.pageAtViewportCenter(ContinuousDocumentState(scrollY = 2400f), 800f))
    }

    @Test fun `pan is unified document state not page state`() {
        val moved = geometry.panBy(ContinuousDocumentState(scale = 2f, scrollX = 500f, scrollY = 900f), -100f, 200f, 1000f, 800f)
        assertEquals(600f, moved.scrollX, 0f)
        assertEquals(700f, moved.scrollY, 0f)
        assertFalse(moved.gestureActive)
    }
}
