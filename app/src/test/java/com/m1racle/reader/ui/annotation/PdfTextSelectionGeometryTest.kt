package com.m1racle.reader.ui.annotation

import com.m1racle.reader.data.anchor.NormalizedRect
import com.m1racle.reader.pdf.PdfSelectableGlyph
import com.m1racle.reader.pdf.PdfSelectableLine
import com.m1racle.reader.pdf.PdfSelectablePage
import com.m1racle.reader.pdf.PdfTextSelectionRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfTextSelectionGeometryTest {
    private val page = PdfSelectablePage(
        page = 0,
        width = 400f,
        height = 600f,
        lines = listOf(
            PdfSelectableLine(
                glyphs = listOf(
                    PdfSelectableGlyph("A", NormalizedRect(.1f, .2f, .1f, .05f)),
                    PdfSelectableGlyph("B", NormalizedRect(.3f, .2f, .1f, .05f)),
                ),
                flowLeft = .1f,
                flowRight = .4f,
            ),
        ),
    )
    private val thinPage = PdfSelectablePage(
        page = 0,
        width = 400f,
        height = 600f,
        lines = listOf(
            PdfSelectableLine(
                glyphs = listOf(
                    PdfSelectableGlyph("I", NormalizedRect(.2f, .2f, .01f, .05f)),
                ),
                flowLeft = .2f,
                flowRight = .21f,
            ),
        ),
    )

    @Test
    fun `forward range exposes anchor at left and focus at right`() {
        val range = PdfTextSelectionRange(
            anchor = PdfSelectablePage.Hit(0, 0),
            focus = PdfSelectablePage.Hit(0, 1),
        )
        val handles = PdfTextSelectionGeometry.handles(page, range, 400f, 600f, 10f)

        val anchor = handles.single { it.handle == PdfSelectionHandle.ANCHOR }
        val focus = handles.single { it.handle == PdfSelectionHandle.FOCUS }
        assertEquals(40f, anchor.x, 0.001f)
        assertEquals(160f, focus.x, 0.001f)
        assertTrue(anchor.x < focus.x)
    }

    @Test
    fun `reverse range keeps endpoint identity while hit testing both handles`() {
        val range = PdfTextSelectionRange(
            anchor = PdfSelectablePage.Hit(0, 1),
            focus = PdfSelectablePage.Hit(0, 0),
        )
        val handles = PdfTextSelectionGeometry.handles(page, range, 400f, 600f, 10f)

        val left = handles.single { it.x == handles.minOf(PdfSelectionHandlePoint::x) }
        val right = handles.single { it.x == handles.maxOf(PdfSelectionHandlePoint::x) }
        assertEquals(PdfSelectionHandle.FOCUS, left.handle)
        assertEquals(PdfSelectionHandle.ANCHOR, right.handle)
        assertEquals(
            PdfSelectionHandle.FOCUS,
            PdfTextSelectionGeometry.hitHandle(
                page, range, left.x, left.y, 400f, 600f, visualRadiusPx = 10f, hitRadiusPx = 24f,
            ),
        )
        assertEquals(
            PdfSelectionHandle.ANCHOR,
            PdfTextSelectionGeometry.hitHandle(
                page, range, right.x, right.y, 400f, 600f, visualRadiusPx = 10f, hitRadiusPx = 24f,
            ),
        )
        assertNull(
            PdfTextSelectionGeometry.hitHandle(
                page, range, 200f, 300f, 400f, 600f, visualRadiusPx = 10f, hitRadiusPx = 24f,
            ),
        )
    }

    @Test
    fun `single glyph keeps fixed anchor and focus identities`() {
        val range = PdfTextSelectionRange(
            anchor = PdfSelectablePage.Hit(0, 0),
            focus = PdfSelectablePage.Hit(0, 0),
        )
        val handles = PdfTextSelectionGeometry.handles(page, range, 400f, 600f, 10f)

        assertEquals(2, handles.size)
        assertTrue(kotlin.math.abs(handles[0].x - handles[1].x) >= 10f)
        assertEquals(handles[0].handle, PdfSelectionHandle.ANCHOR)
        assertEquals(handles[1].handle, PdfSelectionHandle.FOCUS)
    }

    @Test
    fun `thin glyph hit testing uses the visually drawn handle centers`() {
        val range = PdfTextSelectionRange(
            anchor = PdfSelectablePage.Hit(0, 0),
            focus = PdfSelectablePage.Hit(0, 0),
        )
        val handles = PdfTextSelectionGeometry.handles(
            thinPage, range, 400f, 600f, visualRadiusPx = 9f,
        )
        val anchor = handles.single { it.handle == PdfSelectionHandle.ANCHOR }
        val focus = handles.single { it.handle == PdfSelectionHandle.FOCUS }

        assertEquals(
            PdfSelectionHandle.ANCHOR,
            PdfTextSelectionGeometry.hitHandle(
                thinPage, range, anchor.x, anchor.y, 400f, 600f,
                visualRadiusPx = 9f, hitRadiusPx = 24f,
            ),
        )
        assertEquals(
            PdfSelectionHandle.FOCUS,
            PdfTextSelectionGeometry.hitHandle(
                thinPage, range, focus.x, focus.y, 400f, 600f,
                visualRadiusPx = 9f, hitRadiusPx = 24f,
            ),
        )
        assertNull(
            PdfTextSelectionGeometry.hitHandle(
                thinPage, range, handles.maxOf(PdfSelectionHandlePoint::x) + 25f, focus.y, 400f, 600f,
                visualRadiusPx = 9f, hitRadiusPx = 24f,
            ),
        )
    }

    @Test
    fun `crossed range keeps endpoint identities and swaps their sides`() {
        val forward = PdfTextSelectionRange(
            anchor = PdfSelectablePage.Hit(0, 0),
            focus = PdfSelectablePage.Hit(0, 1),
        )
        val crossed = forward.copy(focus = PdfSelectablePage.Hit(0, 0), anchor = PdfSelectablePage.Hit(0, 1))
        val before = PdfTextSelectionGeometry.handles(page, forward, 400f, 600f, 10f)
        val after = PdfTextSelectionGeometry.handles(page, crossed, 400f, 600f, 10f)

        val anchorBefore = before.single { it.handle == PdfSelectionHandle.ANCHOR }
        val focusBefore = before.single { it.handle == PdfSelectionHandle.FOCUS }
        val anchorAfter = after.single { it.handle == PdfSelectionHandle.ANCHOR }
        val focusAfter = after.single { it.handle == PdfSelectionHandle.FOCUS }
        assertTrue(anchorBefore.x < focusBefore.x)
        assertTrue(anchorAfter.x > focusAfter.x)
        assertEquals(
            PdfSelectionHandle.FOCUS,
            PdfTextSelectionGeometry.hitHandle(
                page, crossed, after.minOf(PdfSelectionHandlePoint::x),
                focusAfter.y, 400f, 600f, visualRadiusPx = 10f, hitRadiusPx = 24f,
            ),
        )
        assertEquals(
            PdfSelectionHandle.ANCHOR,
            PdfTextSelectionGeometry.hitHandle(
                page, crossed, after.maxOf(PdfSelectionHandlePoint::x),
                anchorAfter.y, 400f, 600f, visualRadiusPx = 10f, hitRadiusPx = 24f,
            ),
        )
    }

    @Test
    fun `out of bounds hits are rejected before selection`() {
        assertNull(page.validRange(PdfSelectablePage.Hit(1, 0), PdfSelectablePage.Hit(0, 0)))
        assertNull(page.validRange(PdfSelectablePage.Hit(0, 0), PdfSelectablePage.Hit(0, 9)))
        assertTrue(
            PdfTextSelectionGeometry.handles(
                page,
                PdfTextSelectionRange(PdfSelectablePage.Hit(0, 0), PdfSelectablePage.Hit(0, 9)),
                widthPx = 400f,
                heightPx = 600f,
                visualRadiusPx = 9f,
            ).isEmpty(),
        )
    }
}
