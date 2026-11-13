package com.m1racle.reader.ui.annotation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.m1racle.reader.core.model.NormalizedRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfSelectionLoupeGeometryTest {

    @Test fun `source rect is centred on the focus and doubled in size`() {
        val source = PdfSelectionLoupeGeometry.sourceRect(
            focus = Offset(200f, 300f),
            loupeSize = Size(100f, 40f),
            pageWidthPx = 400f,
            pageHeightPx = 600f,
        )
        assertEquals(175f, source.left, 0.001f)
        assertEquals(50f, source.width, 0.001f)
        assertEquals(290f, source.top, 0.001f)
        assertEquals(20f, source.height, 0.001f)
    }

    @Test fun `source rect never leaves the page`() {
        val source = PdfSelectionLoupeGeometry.sourceRect(
            focus = Offset(5f, 595f),
            loupeSize = Size(100f, 40f),
            pageWidthPx = 400f,
            pageHeightPx = 600f,
        )
        assertEquals(0f, source.left, 0.001f)
        assertEquals(580f, source.top, 0.001f)
        assertTrue(source.right <= 400f)
        assertTrue(source.bottom <= 600f)
    }

    @Test fun `a page smaller than the loupe is not scaled past its own size`() {
        val source = PdfSelectionLoupeGeometry.sourceRect(
            focus = Offset(20f, 20f),
            loupeSize = Size(400f, 400f),
            pageWidthPx = 100f,
            pageHeightPx = 80f,
        )
        assertEquals(0f, source.left, 0.001f)
        assertEquals(100f, source.width, 0.001f)
        assertEquals(80f, source.height, 0.001f)
    }

    @Test fun `selected glyph maps to its magnified position in the loupe`() {
        val source = PdfSelectionLoupeGeometry.sourceRect(
            focus = Offset(200f, 300f),
            loupeSize = Size(100f, 40f),
            pageWidthPx = 400f,
            pageHeightPx = 600f,
        )
        val rect = NormalizedRect(0.5f, 0.5f, 0.05f, 0.02f)
        val bounds = PdfSelectionLoupeGeometry.toLoupe(rect, source, 400f, 600f)
        assertEquals((200f - source.left) * 2f, bounds.left, 0.001f)
        assertEquals((300f - source.top) * 2f, bounds.top, 0.001f)
        assertEquals(0.05f * 400f * 2f, bounds.width, 0.001f)
        assertEquals(0.02f * 600f * 2f, bounds.height, 0.001f)
    }

    @Test fun `bitmap crop matches the page crop at any bitmap resolution`() {
        val source = PdfSelectionLoupeGeometry.sourceRect(
            focus = Offset(200f, 300f),
            loupeSize = Size(100f, 40f),
            pageWidthPx = 400f,
            pageHeightPx = 600f,
        )
        val low = PdfSelectionLoupeGeometry.bitmapSource(source, 400f, 600f, 400, 600)
        val high = PdfSelectionLoupeGeometry.bitmapSource(source, 400f, 600f, 800, 1200)
        assertEquals(175, low.left)
        assertEquals(50, low.width)
        assertEquals(350, high.left)
        assertEquals(100, high.width)
        assertEquals(290, low.top)
        assertEquals(580, high.top)
    }

    @Test fun `bitmap crop stays inside the bitmap at the page edge`() {
        val source = PdfSelectionLoupeGeometry.sourceRect(
            focus = Offset(399f, 599f),
            loupeSize = Size(100f, 40f),
            pageWidthPx = 400f,
            pageHeightPx = 600f,
        )
        val crop = PdfSelectionLoupeGeometry.bitmapSource(source, 400f, 600f, 400, 600)
        assertTrue(crop.left + crop.width <= 400)
        assertTrue(crop.top + crop.height <= 600)
        assertTrue(crop.width > 0 && crop.height > 0)
    }
}
