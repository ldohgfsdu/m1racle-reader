package com.markleaf.app.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Production-path geometry for AnchoredNestedPopover → anchoredRevealRect /
 * computeAnchoredNestedTarget (the SHIPPED morph, not dead advancedMorphRect).
 */
class AnchoredNestedGeometryTest {

    private fun base(
        parentLeft: Float = 200f,
        parentTop: Float = 80f,
        parentRight: Float = 416f,
        parentBottom: Float = 400f,
        sourceLeft: Float = 200f,
        sourceTop: Float = 180f,
        sourceRight: Float = 416f,
        sourceBottom: Float = 232f,
        minLeft: Float = 16f,
        viewportBottom: Float = 900f,
        leftExpand: Float = 120f,
        contentH: Float = 220f,
    ) = AnchoredNestedInput(
        parentRectLeft = parentLeft,
        parentRectTop = parentTop,
        parentRectRight = parentRight,
        parentRectBottom = parentBottom,
        sourceRectLeft = sourceLeft,
        sourceRectTop = sourceTop,
        sourceRectRight = sourceRight,
        sourceRectBottom = sourceBottom,
        minLeftPx = minLeft,
        viewportBottom = viewportBottom,
        leftExpandPx = leftExpand,
        contentHeightPx = contentH,
        bottomMarginPx = 28f,
        minHeightPx = sourceBottom - sourceTop,
    )

    @Test
    fun `target right equals parent right`() {
        val t = computeAnchoredNestedTarget(base())
        assertEquals(416f, t.right, 1e-3f)
    }

    @Test
    fun `target top prefers source row top`() {
        val t = computeAnchoredNestedTarget(base(sourceTop = 263f, sourceBottom = 315f))
        assertEquals(263f, t.top, 1e-3f)
    }

    @Test
    fun `target left expands from parent left and respects 16dp screen margin`() {
        // leftExpand 120dp from parentLeft 200 → 80; 16dp margin never clips.
        val t = computeAnchoredNestedTarget(base(leftExpand = 120f, minLeft = 16f))
        assertEquals(80f, t.left, 1e-3f)
        assertTrue(t.left >= 16f)
    }

    @Test
    fun `target left clamps to the 16dp screen margin on narrow screens`() {
        // Screen so narrow that parentLeft - leftExpand < 16dp → clamp.
        val t = computeAnchoredNestedTarget(base(parentLeft = 80f, leftExpand = 120f, minLeft = 16f))
        assertEquals(16f, t.left, 1e-3f)
    }

    @Test
    fun `target height clamps to viewport bottom`() {
        val t = computeAnchoredNestedTarget(
            base(sourceTop = 700f, sourceBottom = 752f, contentH = 400f, viewportBottom = 900f),
        )
        assertTrue(t.bottom <= 900f - 28f + 1e-3f)
        assertTrue(t.height >= 52f - 1e-3f)
    }

    // ── Production morph path: full L/T/R/B source → target ─────────────────

    private fun morphSample(
        sL: Float = 200f,
        sT: Float = 263f,
        sR: Float = 416f,
        sB: Float = 315f,
        tL: Float = 132f,
        tT: Float = 263f,
        tR: Float = 416f,
        tB: Float = 560f,
        p: Float,
    ) = anchoredRevealRect(
        sourceLeft = sL, sourceTop = sT, sourceRight = sR, sourceBottom = sB,
        targetLeft = tL, targetTop = tT, targetRight = tR, targetBottom = tB,
        p = p,
    )

    @Test
    fun `p0 equals frozen source row bounds exactly`() {
        val r = morphSample(p = 0f)
        assertEquals(200f, r.left, 1e-3f)
        assertEquals(263f, r.top, 1e-3f)
        assertEquals(416f, r.right, 1e-3f)
        assertEquals(315f, r.bottom, 1e-3f)
        assertEquals(216f, r.width, 1e-3f)
        assertEquals(52f, r.height, 1e-3f)
    }

    @Test
    fun `p1 equals target bounds exactly`() {
        val r = morphSample(p = 1f)
        assertEquals(132f, r.left, 1e-3f)
        assertEquals(263f, r.top, 1e-3f)
        assertEquals(416f, r.right, 1e-3f)
        assertEquals(560f, r.bottom, 1e-3f)
    }

    @Test
    fun `open never snaps horizontal edges to target at early p`() {
        // At p=0.1, left must still be near source (not already at target 132).
        val r = morphSample(p = 0.1f)
        assertTrue("left still near source at p=0.1", r.left > 190f)
        assertTrue("left not yet at target", r.left < 200f)
        // Bottom has grown a little past source bottom.
        assertTrue(r.bottom > 315f)
        assertTrue(r.bottom < 560f)
    }

    @Test
    fun `every edge is continuous linear on source-target trajectory`() {
        (0..20).forEach { i ->
            val p = i / 20f
            val r = morphSample(p = p)
            val expL = 200f + (132f - 200f) * p
            val expT = 263f + (263f - 263f) * p
            val expR = 416f + (416f - 416f) * p
            val expB = 315f + (560f - 315f) * p
            assertEquals("left at p=$p", expL, r.left, 1e-3f)
            assertEquals("top at p=$p", expT, r.top, 1e-3f)
            assertEquals("right at p=$p", expR, r.right, 1e-3f)
            assertEquals("bottom at p=$p", expB, r.bottom, 1e-3f)
        }
    }

    @Test
    fun `close retraces open - same p same rect`() {
        (0..10).forEach { i ->
            val p = i / 10f
            val open = morphSample(p = p)
            val close = morphSample(p = p) // same function, reverse driven by p 1→0
            assertEquals(open, close)
        }
    }

    @Test
    fun `collapse last frame p0 is source row shape not expanded strip`() {
        val last = morphSample(p = 0f)
        assertEquals(200f, last.left, 1e-3f)
        assertEquals(416f, last.right, 1e-3f)
        assertEquals(52f, last.height, 1e-3f)
        // Must NOT be expanded-width strip (target width = 416-132 = 284).
        assertTrue(last.width < 220f)
    }

    @Test
    fun `export low source still morphs from its own Y`() {
        // Export row lower on Main — source top 400, target may stay near that.
        val input = base(
            sourceLeft = 200f, sourceTop = 400f, sourceRight = 416f, sourceBottom = 452f,
            contentH = 280f, viewportBottom = 900f,
        )
        val target = computeAnchoredNestedTarget(input)
        val p0 = anchoredRevealRect(
            sourceLeft = input.sourceRectLeft,
            sourceTop = input.sourceRectTop,
            sourceRight = input.sourceRectRight,
            sourceBottom = input.sourceRectBottom,
            targetLeft = target.left,
            targetTop = target.top,
            targetRight = target.right,
            targetBottom = target.bottom,
            p = 0f,
        )
        assertEquals(input.sourceRectTop, p0.top, 1e-3f)
        assertEquals(input.sourceRectLeft, p0.left, 1e-3f)
        assertEquals(input.sourceRectRight, p0.right, 1e-3f)
        assertEquals(input.sourceRectBottom, p0.bottom, 1e-3f)
        val p1 = anchoredRevealRect(
            sourceLeft = input.sourceRectLeft,
            sourceTop = input.sourceRectTop,
            sourceRight = input.sourceRectRight,
            sourceBottom = input.sourceRectBottom,
            targetLeft = target.left,
            targetTop = target.top,
            targetRight = target.right,
            targetBottom = target.bottom,
            p = 1f,
        )
        assertEquals(target.left, p1.left, 1e-3f)
        assertEquals(target.top, p1.top, 1e-3f)
        assertEquals(target.right, p1.right, 1e-3f)
        assertEquals(target.bottom, p1.bottom, 1e-3f)
    }

    @Test
    fun `popover source uses compute then reveal on shipped API chain`() {
        // Mirrors AnchoredNestedPopover call order.
        val input = base(
            sourceLeft = 218f, sourceTop = 190f, sourceRight = 434f, sourceBottom = 242f,
            parentLeft = 218f, parentRight = 434f,
        )
        val target = computeAnchoredNestedTarget(input)
        // Target right must inherit parent right (source right when row fills Main).
        assertEquals(input.parentRectRight, target.right, 1e-3f)
        val mid = anchoredRevealRect(
            sourceLeft = input.sourceRectLeft,
            sourceTop = input.sourceRectTop,
            sourceRight = input.sourceRectRight,
            sourceBottom = input.sourceRectBottom,
            targetLeft = target.left,
            targetTop = target.top,
            targetRight = target.right,
            targetBottom = target.bottom,
            p = 0.5f,
        )
        assertEquals((input.sourceRectLeft + target.left) / 2f, mid.left, 1e-3f)
        assertEquals((input.sourceRectBottom + target.bottom) / 2f, mid.bottom, 1e-3f)
    }
}
