package com.m1racle.reader.ui.annotation

import com.m1racle.reader.core.model.NormalizedRect
import com.m1racle.reader.pdf.PdfSelectableGlyph
import com.m1racle.reader.pdf.PdfSelectableLine
import com.m1racle.reader.pdf.PdfSelectablePage
import com.m1racle.reader.pdf.PdfTextSelectionRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

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
    fun `forward range puts corner tips on matching glyph lower outer corners`() {
        val range = PdfTextSelectionRange(
            anchor = PdfSelectablePage.Hit(0, 0),
            focus = PdfSelectablePage.Hit(0, 1),
        )
        val handles = PdfTextSelectionGeometry.handles(page, range, 400f, 600f, 10f)

        val anchor = handles.single { it.handle == PdfSelectionHandle.ANCHOR }
        val focus = handles.single { it.handle == PdfSelectionHandle.FOCUS }
        assertEquals(40f, anchor.tipX, 0.001f)
        assertEquals(160f, focus.tipX, 0.001f)
        assertEquals(150f, anchor.tipY, 0.001f)
        assertEquals(150f, focus.tipY, 0.001f)
        assertTrue("start body extends away from selected text", anchor.isLeft)
        assertFalse("end body extends away from selected text", focus.isLeft)
        assertFalse(anchor.above)
        assertFalse(focus.above)
        assertEquals(10f, anchor.radiusPx, 0.001f)
        assertEquals(10f, focus.radiusPx, 0.001f)
        assertEquals(30f, anchor.x, 0.001f)
        assertEquals(170f, focus.x, 0.001f)
        assertEquals(160f, anchor.y, 0.001f)
        assertEquals(160f, focus.y, 0.001f)
    }

    @Test
    fun `body centres attach corner to the glyph lower boundary`() {
        val range = PdfTextSelectionRange(
            anchor = PdfSelectablePage.Hit(0, 0),
            focus = PdfSelectablePage.Hit(0, 1),
        )
        val handles = PdfTextSelectionGeometry.handles(page, range, 400f, 600f, 10f)

        // Glyph band is y=.2..25 of a 600px page => 120..150.
        handles.forEach { handle ->
            assertTrue(handle.y > handle.tipY)
            assertEquals(handle.radiusPx, handle.y - handle.tipY, 0.001f)
            assertEquals(handle.radiusPx, kotlin.math.abs(handle.x - handle.tipX), 0.001f)
        }
    }

    @Test
    fun `bottom line flips both bodies above without moving their horizontal tips`() {
        val bottomPage = PdfSelectablePage(
            page = 0,
            width = 400f,
            height = 600f,
            lines = listOf(
                PdfSelectableLine(
                    glyphs = listOf(
                        PdfSelectableGlyph("中", NormalizedRect(.1f, .96f, .1f, .03f)),
                        PdfSelectableGlyph("文", NormalizedRect(.25f, .96f, .1f, .03f)),
                    ),
                    flowLeft = .1f,
                    flowRight = .35f,
                ),
            ),
        )
        val range = PdfTextSelectionRange(
            anchor = PdfSelectablePage.Hit(0, 0),
            focus = PdfSelectablePage.Hit(0, 1),
        )
        val handles = PdfTextSelectionGeometry.handles(bottomPage, range, 400f, 600f, 10f)

        assertTrue(handles.all { it.above })
        assertEquals(listOf(40f, 140f), handles.map { it.tipX })
        handles.forEach { handle ->
            assertEquals(576f, handle.tipY, 0.001f)
            assertEquals(566f, handle.y, 0.001f)
            assertTrue("flipped body must stay on the page", handle.y - handle.radiusPx >= 0f)
            assertTrue(handle.y + handle.radiusPx <= 600f)
            assertTrue(handle.y < handle.tipY)
        }
    }

    @Test
    fun `page edge flips only the body while preserving the text anchor tip`() {
        val edgePage = PdfSelectablePage(
            page = 0,
            width = 400f,
            height = 600f,
            lines = listOf(
                PdfSelectableLine(
                    glyphs = listOf(
                        PdfSelectableGlyph("边", NormalizedRect(.0f, .2f, .04f, .05f)),
                        PdfSelectableGlyph("缘", NormalizedRect(.96f, .2f, .04f, .05f)),
                    ),
                    flowLeft = .0f,
                    flowRight = 1f,
                ),
            ),
        )
        val range = PdfTextSelectionRange(
            anchor = PdfSelectablePage.Hit(0, 0),
            focus = PdfSelectablePage.Hit(0, 1),
        )
        val handles = PdfTextSelectionGeometry.handles(edgePage, range, 400f, 600f, 12.5f)

        val start = handles.single { it.handle == PdfSelectionHandle.ANCHOR }
        val end = handles.single { it.handle == PdfSelectionHandle.FOCUS }
        assertEquals(0f, start.tipX, 0.001f)
        assertEquals(400f, end.tipX, 0.001f)
        assertFalse("left-edge start body flips inward", start.isLeft)
        assertTrue("right-edge end body flips inward", end.isLeft)
        assertTrue(start.x > start.tipX)
        assertTrue(end.x < end.tipX)
    }

    @Test
    fun `single glyph keeps two non-overlapping bodies with fixed endpoint identities`() {
        val range = PdfTextSelectionRange(
            anchor = PdfSelectablePage.Hit(0, 0),
            focus = PdfSelectablePage.Hit(0, 0),
        )
        val handles = PdfTextSelectionGeometry.handles(page, range, 400f, 600f, 12.5f)
        val anchor = handles.single { it.handle == PdfSelectionHandle.ANCHOR }
        val focus = handles.single { it.handle == PdfSelectionHandle.FOCUS }

        assertEquals(2, handles.size)
        assertTrue(anchor.tipX < focus.tipX)
        assertTrue(anchor.isLeft)
        assertFalse(focus.isLeft)
        assertTrue(
            "single-glyph handle bodies must not overlap",
            hypot(anchor.x - focus.x, anchor.y - focus.y) >= anchor.radiusPx + focus.radiusPx,
        )
    }

    @Test
    fun `start handle drag uses the target START edge at a shared A B boundary`() {
        val adjacent = PdfSelectablePage(
            page = 0,
            width = 400f,
            height = 600f,
            lines = listOf(
                PdfSelectableLine(
                    glyphs = listOf(
                        PdfSelectableGlyph("A", NormalizedRect(.1f, .2f, .1f, .05f)),
                        PdfSelectableGlyph("B", NormalizedRect(.2f, .2f, .1f, .05f)),
                    ),
                    flowLeft = .1f,
                    flowRight = .3f,
                ),
            ),
        )
        val range = PdfTextSelectionRange(
            anchor = PdfSelectablePage.Hit(0, 1),
            focus = PdfSelectablePage.Hit(0, 1),
        )
        val handle = PdfTextSelectionGeometry.handles(
            adjacent, range, 400f, 600f, visualRadiusPx = 12.5f,
        ).single { it.handle == PdfSelectionHandle.ANCHOR }

        val hit = PdfTextSelectionGeometry.hitGlyphForHandleDrag(
            page = adjacent,
            range = range,
            handle = PdfSelectionHandle.ANCHOR,
            downX = handle.x,
            downY = handle.y,
            currentX = handle.x,
            currentY = handle.y,
            widthPx = 400f,
            heightPx = 600f,
        )

        assertEquals("target B START boundary must win the shared A B tie", PdfSelectablePage.Hit(0, 1), hit)
    }

    @Test
    fun `start handle drag uses boundary delta when zoomed`() {
        val adjacent = PdfSelectablePage(
            page = 0,
            width = 400f,
            height = 600f,
            lines = listOf(
                PdfSelectableLine(
                    glyphs = listOf(
                        PdfSelectableGlyph("A", NormalizedRect(.1f, .2f, .1f, .05f)),
                        PdfSelectableGlyph("B", NormalizedRect(.2f, .2f, .1f, .05f)),
                    ),
                    flowLeft = .1f,
                    flowRight = .3f,
                ),
            ),
        )
        val range = PdfTextSelectionRange(
            anchor = PdfSelectablePage.Hit(0, 1),
            focus = PdfSelectablePage.Hit(0, 1),
        )
        val handle = PdfTextSelectionGeometry.handles(
            adjacent, range, 400f, 600f, visualRadiusPx = 12.5f / 2.5f,
        ).single { it.handle == PdfSelectionHandle.ANCHOR }

        val hit = PdfTextSelectionGeometry.hitGlyphForHandleDrag(
            page = adjacent,
            range = range,
            handle = PdfSelectionHandle.ANCHOR,
            downX = handle.x,
            downY = handle.y,
            currentX = handle.x,
            currentY = handle.y,
            widthPx = 400f,
            heightPx = 600f,
        )

        assertEquals("zoom must not change boundary affinity", PdfSelectablePage.Hit(0, 1), hit)
    }

    @Test
    fun `handle drag from narrow glyph to wide glyph uses START boundary without centre bias`() {
        val mixed = PdfSelectablePage(
            page = 0,
            width = 400f,
            height = 600f,
            lines = listOf(
                PdfSelectableLine(
                    glyphs = listOf(
                        PdfSelectableGlyph("i", NormalizedRect(.1f, .2f, .03f, .05f)),
                        PdfSelectableGlyph("中", NormalizedRect(.2f, .2f, .2f, .05f)),
                    ),
                    flowLeft = .1f,
                    flowRight = .4f,
                ),
            ),
        )
        val range = PdfTextSelectionRange(
            anchor = PdfSelectablePage.Hit(0, 0),
            focus = PdfSelectablePage.Hit(0, 0),
        )
        val anchor = PdfTextSelectionGeometry.handles(
            mixed, range, 400f, 600f, 12.5f,
        ).single { it.handle == PdfSelectionHandle.ANCHOR }
        val wideTargetTipX = mixed.lines[0].glyphs[1].rect.x * mixed.width

        val hit = PdfTextSelectionGeometry.hitGlyphForHandleDrag(
            page = mixed,
            range = range,
            handle = PdfSelectionHandle.ANCHOR,
            downX = anchor.x,
            downY = anchor.y,
            currentX = anchor.x + wideTargetTipX - anchor.tipX,
            currentY = anchor.y,
            widthPx = 400f,
            heightPx = 600f,
        )

        assertEquals("wide target boundary must choose its right character at START tie", PdfSelectablePage.Hit(0, 1), hit)
    }

    @Test
    fun `handle drag from wide glyph to narrow glyph keeps END boundary identity`() {
        val mixed = PdfSelectablePage(
            page = 0,
            width = 400f,
            height = 600f,
            lines = listOf(
                PdfSelectableLine(
                    glyphs = listOf(
                        PdfSelectableGlyph("中", NormalizedRect(.1f, .2f, .2f, .05f)),
                        PdfSelectableGlyph("i", NormalizedRect(.3f, .2f, .03f, .05f)),
                    ),
                    flowLeft = .1f,
                    flowRight = .33f,
                ),
            ),
        )
        val range = PdfTextSelectionRange(
            anchor = PdfSelectablePage.Hit(0, 0),
            focus = PdfSelectablePage.Hit(0, 0),
        )
        val focus = PdfTextSelectionGeometry.handles(
            mixed, range, 400f, 600f, 12.5f,
        ).single { it.handle == PdfSelectionHandle.FOCUS }
        val narrowRect = mixed.lines[0].glyphs[1].rect
        val narrowTargetTipX = (narrowRect.x + narrowRect.width) * mixed.width

        val hit = PdfTextSelectionGeometry.hitGlyphForHandleDrag(
            page = mixed,
            range = range,
            handle = PdfSelectionHandle.FOCUS,
            downX = focus.x,
            downY = focus.y,
            currentX = focus.x + narrowTargetTipX - focus.tipX,
            currentY = focus.y,
            widthPx = 400f,
            heightPx = 600f,
        )

        assertEquals(PdfSelectablePage.Hit(0, 1), hit)
    }

    @Test
    fun `crossing drag fixes focus and flips affinity in the same pointer event`() {
        val mixed = PdfSelectablePage(
            page = 0,
            width = 400f,
            height = 600f,
            lines = listOf(
                PdfSelectableLine(
                    glyphs = listOf(
                        PdfSelectableGlyph("A", NormalizedRect(.1f, .2f, .1f, .05f)),
                        PdfSelectableGlyph("B", NormalizedRect(.2f, .2f, .1f, .05f)),
                        PdfSelectableGlyph("C", NormalizedRect(.3f, .2f, .1f, .05f)),
                    ),
                    flowLeft = .1f,
                    flowRight = .4f,
                ),
            ),
        )
        val range = PdfTextSelectionRange(
            anchor = PdfSelectablePage.Hit(0, 0),
            focus = PdfSelectablePage.Hit(0, 1),
        )
        val anchor = PdfTextSelectionGeometry.handles(
            mixed, range, 400f, 600f, 12.5f,
        ).single { it.handle == PdfSelectionHandle.ANCHOR }
        val crossedTarget = mixed.lines[0].glyphs[2]
        val crossedTargetTipX =
            (crossedTarget.rect.x + crossedTarget.rect.width) * mixed.width

        val hit = PdfTextSelectionGeometry.hitGlyphForHandleDrag(
            page = mixed,
            range = range,
            handle = PdfSelectionHandle.ANCHOR,
            downX = anchor.x,
            downY = anchor.y,
            currentX = anchor.x + crossedTargetTipX - anchor.tipX,
            currentY = anchor.y,
            widthPx = 400f,
            heightPx = 600f,
        )

        val target = requireNotNull(hit)
        assertEquals(PdfSelectablePage.Hit(0, 2), hit)
        assertEquals(PdfSelectablePage.Hit(0, 1), range.focus)
        assertEquals(range.focus, range.copy(anchor = target).ordered().first)
        assertEquals(target, range.copy(anchor = target).ordered().second)
        val crossed = range.copy(anchor = target)
        val crossedHandles = PdfTextSelectionGeometry.handles(mixed, crossed, 400f, 600f, 12.5f)
        assertFalse(crossedHandles.single { it.handle == PdfSelectionHandle.ANCHOR }.isLeft)
        assertTrue(crossedHandles.single { it.handle == PdfSelectionHandle.FOCUS }.isLeft)
        assertEquals(
            "UP must not re-snap after affinity has crossed in the same pointer stream",
            target,
            PdfTextSelectionGeometry.hitGlyphForHandleDrag(
                page = mixed,
                range = range,
                handle = PdfSelectionHandle.ANCHOR,
                preferred = target,
                downX = anchor.x,
                downY = anchor.y,
                currentX = anchor.x + crossedTargetTipX - anchor.tipX,
                currentY = anchor.y,
                widthPx = 400f,
                heightPx = 600f,
            ),
        )
    }

    @Test
    fun `reverse range keeps endpoint identity while directions follow visual side`() {
        val range = PdfTextSelectionRange(
            anchor = PdfSelectablePage.Hit(0, 1),
            focus = PdfSelectablePage.Hit(0, 0),
        )
        val handles = PdfTextSelectionGeometry.handles(page, range, 400f, 600f, 10f)

        val left = handles.single { it.tipX == handles.minOf(PdfSelectionHandlePoint::tipX) }
        val right = handles.single { it.tipX == handles.maxOf(PdfSelectionHandlePoint::tipX) }
        assertEquals(PdfSelectionHandle.FOCUS, left.handle)
        assertEquals(PdfSelectionHandle.ANCHOR, right.handle)
        assertTrue(left.isLeft)
        assertFalse(right.isLeft)
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
    fun `thin glyph hit testing uses the visually drawn body centres`() {
        val range = PdfTextSelectionRange(
            anchor = PdfSelectablePage.Hit(0, 0),
            focus = PdfSelectablePage.Hit(0, 0),
        )
        val handles = PdfTextSelectionGeometry.handles(
            thinPage, range, 400f, 600f, visualRadiusPx = 12.5f,
        )
        val anchor = handles.single { it.handle == PdfSelectionHandle.ANCHOR }
        val focus = handles.single { it.handle == PdfSelectionHandle.FOCUS }

        assertEquals(
            PdfSelectionHandle.ANCHOR,
            PdfTextSelectionGeometry.hitHandle(
                thinPage, range, anchor.x, anchor.y, 400f, 600f,
                visualRadiusPx = 12.5f, hitRadiusPx = 22f,
            ),
        )
        assertEquals(
            PdfSelectionHandle.FOCUS,
            PdfTextSelectionGeometry.hitHandle(
                thinPage, range, focus.x, focus.y, 400f, 600f,
                visualRadiusPx = 12.5f, hitRadiusPx = 22f,
            ),
        )
        assertNull(
            PdfTextSelectionGeometry.hitHandle(
                thinPage, range, focus.x + 23f, focus.y, 400f, 600f,
                visualRadiusPx = 12.5f, hitRadiusPx = 22f,
            ),
        )
    }

    @Test
    fun `crossed range keeps endpoint identities and swaps outward directions`() {
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
        assertTrue(anchorBefore.tipX < focusBefore.tipX)
        assertTrue(anchorAfter.tipX > focusAfter.tipX)
        assertFalse(anchorAfter.isLeft)
        assertTrue(focusAfter.isLeft)
        assertEquals(
            PdfSelectionHandle.FOCUS,
            PdfTextSelectionGeometry.hitHandle(
                page, crossed, after.minBy(PdfSelectionHandlePoint::tipX).x,
                focusAfter.y, 400f, 600f, visualRadiusPx = 10f, hitRadiusPx = 24f,
            ),
        )
        assertEquals(
            PdfSelectionHandle.ANCHOR,
            PdfTextSelectionGeometry.hitHandle(
                page, crossed, after.maxBy(PdfSelectionHandlePoint::tipX).x,
                anchorAfter.y, 400f, 600f, visualRadiusPx = 10f, hitRadiusPx = 24f,
            ),
        )
    }

    @Test
    fun `two point five x page scale keeps visual and touch diameters fixed`() {
        val metrics = PdfTextSelectionGeometry.fixedScreenActionBarMetrics(
            barWidthPx = 100f,
            barHeightPx = 40f,
            gapPx = 8f,
            marginPx = 8f,
            pageScale = 2.5f,
        )
        val pageLocalVisualRadius = 12.5f * metrics.inverseScale
        val pageLocalTouchRadius = 22f * metrics.inverseScale

        assertEquals(25f, pageLocalVisualRadius * 2f * 2.5f, 0.001f)
        assertEquals(44f, pageLocalTouchRadius * 2f * 2.5f, 0.001f)
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
                visualRadiusPx = 12.5f,
            ).isEmpty(),
        )
    }
}
