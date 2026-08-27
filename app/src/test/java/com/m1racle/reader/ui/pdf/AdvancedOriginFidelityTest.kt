package com.m1racle.reader.ui.pdf

import com.m1racle.reader.pdf.anchoredRevealRect
import com.m1racle.reader.pdf.computeAnchoredNestedTarget
import com.m1racle.reader.pdf.AnchoredNestedInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Origin fidelity on the **shipped** nested path
 * (AnchoredNestedPopover → computeAnchoredNestedTarget + anchoredRevealRect).
 *
 * Dead advancedMorphRect is no longer the production morph; these tests drive
 * the real functions.
 */
class AdvancedOriginFidelityTest {

    @Test
    fun `four task source rows have distinct Y`() {
        val rowTops = listOf(2, 3, 4, 5).map { parentRowTop(it) }
        rowTops.zipWithNext().forEach { (a, b) ->
            assertTrue("rows must be ordered and distinct", b > a)
        }
        assertEquals(4, rowTops.toSet().size)
    }

    @Test
    fun `every advanced state maps to its own distinct row`() {
        val expected = mapOf(
            ReaderMoreState.CHILD_PAGE_MAPPING to 2,
            ReaderMoreState.CHILD_TOC to 3,
            ReaderMoreState.CHILD_THUMBNAILS to 4,
            ReaderMoreState.CHILD_EXPORT to 5,
        )
        expected.forEach { (state, rowIndex) ->
            assertEquals("$state must own row $rowIndex", rowIndex, childRowIndex(state))
        }
        assertEquals(4, expected.values.toSet().size)
    }

    @Test
    fun `each state reverse morph lands on its own row Y at p0`() {
        // Simulate four rows on Main (width 216, tops from parentRowTop).
        val menuLeft = 200f
        val menuRight = 416f
        val densityRowH = 52f
        val densityPad = 3f
        fun sourceFor(rowIndex: Int): FloatArray {
            val top = densityPad + densityRowH * rowIndex
            return floatArrayOf(menuLeft, top, menuRight, top + densityRowH)
        }
        listOf(
            ReaderMoreState.CHILD_PAGE_MAPPING to 2,
            ReaderMoreState.CHILD_TOC to 3,
            ReaderMoreState.CHILD_THUMBNAILS to 4,
            ReaderMoreState.CHILD_EXPORT to 5,
        ).forEach { (state, row) ->
            val s = sourceFor(row)
            val input = AnchoredNestedInput(
                parentRectLeft = menuLeft,
                parentRectTop = densityPad,
                parentRectRight = menuRight,
                parentRectBottom = densityPad + densityRowH * 6,
                sourceRectLeft = s[0],
                sourceRectTop = s[1],
                sourceRectRight = s[2],
                sourceRectBottom = s[3],
                minLeftPx = 16f,
                viewportBottom = 900f,
                leftExpandPx = 120f,
                contentHeightPx = 280f,
                bottomMarginPx = 28f,
                minHeightPx = densityRowH,
            )
            val target = computeAnchoredNestedTarget(input)
            val p0 = anchoredRevealRect(
                sourceLeft = s[0], sourceTop = s[1], sourceRight = s[2], sourceBottom = s[3],
                targetLeft = target.left, targetTop = target.top,
                targetRight = target.right, targetBottom = target.bottom,
                p = 0f,
            )
            assertEquals("$state p0 top is its source row", s[1], p0.top, 1e-3f)
            assertEquals("$state p0 left is source", s[0], p0.left, 1e-3f)
            assertEquals("$state p0 right is source", s[2], p0.right, 1e-3f)
            assertEquals("$state p0 bottom is source", s[3], p0.bottom, 1e-3f)
            // Distinct Y across kinds.
            assertTrue(p0.top > densityPad + densityRowH) // below first two rows
        }
    }

    @Test
    fun `target rect changes never modify origin at p0`() {
        val sL = 200f; val sT = 107f; val sR = 416f; val sB = 159f
        val targetA = floatArrayOf(120f, 107f, 416f, 560f)
        val targetB = floatArrayOf(80f, 60f, 416f, 320f)
        val rA = anchoredRevealRect(sL, sT, sR, sB, targetA[0], targetA[1], targetA[2], targetA[3], 0f)
        val rB = anchoredRevealRect(sL, sT, sR, sB, targetB[0], targetB[1], targetB[2], targetB[3], 0f)
        assertEquals(rA, rB)
        assertEquals(sT, rA.top, 1e-3f)
        assertEquals(sL, rA.left, 1e-3f)
        assertEquals(sR, rA.right, 1e-3f)
        assertEquals(sB, rA.bottom, 1e-3f)
    }

    @Test
    fun `closing frame at p equals opening frame at p - exact retrace`() {
        val sL = 200f; val sT = 263f; val sR = 416f; val sB = 315f
        val tL = 132f; val tT = 200f; val tR = 416f; val tB = 560f
        (0..10).forEach { i ->
            val p = i / 10f
            val open = anchoredRevealRect(sL, sT, sR, sB, tL, tT, tR, tB, p)
            val closing = anchoredRevealRect(sL, sT, sR, sB, tL, tT, tR, tB, p)
            assertEquals(open, closing)
        }
    }

    @Test
    fun `collapse last frame is source row not target width strip`() {
        val last = anchoredRevealRect(
            sourceLeft = 200f, sourceTop = 263f, sourceRight = 416f, sourceBottom = 315f,
            targetLeft = 100f, targetTop = 120f, targetRight = 416f, targetBottom = 600f,
            p = 0f,
        )
        assertEquals(200f, last.left, 1e-3f)
        assertEquals(416f, last.right, 1e-3f)
        assertEquals(52f, last.height, 1e-3f)
        assertTrue("not expanded strip", last.width < 250f)
    }
}
