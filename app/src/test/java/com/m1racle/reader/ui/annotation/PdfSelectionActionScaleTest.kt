package com.m1racle.reader.ui.annotation

import com.m1racle.reader.core.model.NormalizedRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression for the zoomed selection menu: the action bar used to live inside
 * the page's GPU scale transform, so its text and surface grew together with
 * the PDF page. See the Adobe Acrobat reference screenshots (fixed menu size
 * while the page zooms) in the 2026-11-05 fix report.
 *
 * Contract: the bar keeps a fixed on-screen size and a fixed gap to the
 * selection at every page scale, in both continuous and horizontal-paged modes.
 */
class PdfSelectionActionScaleTest {

    private val barWidthPx = 240f
    private val barHeightPx = 40f
    private val gapPx = 8f
    private val marginPx = 8f

    private fun metrics(scale: Float) =
        PdfTextSelectionGeometry.fixedScreenActionBarMetrics(
            barWidthPx = barWidthPx,
            barHeightPx = barHeightPx,
            gapPx = gapPx,
            marginPx = marginPx,
            pageScale = scale,
        )

    @Test
    fun inverseScaleCancelsThePageTransformAtEveryZoom() {
        listOf(0.5f, 1f, 1.5f, 2f, 3f, 5f).forEach { scale ->
            assertEquals(
                "inverse scale must undo the page scale at $scale",
                1f,
                metrics(scale).inverseScale * scale,
                1e-5f,
            )
        }
    }

    @Test
    fun actionBarKeepsFixedScreenSizeAndSpacingAtEveryZoom() {
        listOf(0.5f, 1f, 1.5f, 2f, 3f, 5f).forEach { scale ->
            val m = metrics(scale)
            // Page-local equivalent -> on-screen pixels.
            assertEquals("bar width at $scale", barWidthPx, m.barWidthPx * scale, 1e-4f)
            assertEquals("bar height at $scale", barHeightPx, m.barHeightPx * scale, 1e-4f)
            assertEquals("selection gap at $scale", gapPx, m.gapPx * scale, 1e-4f)
            assertEquals("edge margin at $scale", marginPx, m.marginPx * scale, 1e-4f)
        }
    }

    @Test
    fun menuStaysAboveSelectionWithAFixedScreenGapAtEveryZoom() {
        val selectionRects = listOf(NormalizedRect(0.2f, 0.4f, 0.5f, 0.03f))
        val pageWidthPx = 800f
        val pageHeightPx = 1200f
        listOf(0.5f, 1f, 2f, 3f, 5f).forEach { scale ->
            val m = metrics(scale)
            val placement = PdfTextSelectionGeometry.actionBarPlacement(
                selectionRects = selectionRects,
                widthPx = pageWidthPx,
                heightPx = pageHeightPx,
                barWidthPx = m.barWidthPx,
                barHeightPx = m.barHeightPx,
                gapPx = m.gapPx,
                marginPx = m.marginPx,
            )
            assertNotNull("placement must exist at $scale", placement)
            val visualBarTop = placement!!.y * scale
            val visualBarBottom = visualBarTop + m.barHeightPx * scale
            val visualSelectionTop = selectionRects[0].y * pageHeightPx * scale
            assertEquals(
                "gap between bar and selection must not grow at $scale",
                gapPx,
                visualSelectionTop - visualBarBottom,
                1e-3f,
            )
            val visualBarLeft = placement.x * scale
            val visualSelectionCenterX =
                (selectionRects[0].x + selectionRects[0].width / 2f) * pageWidthPx * scale
            assertEquals(
                "bar must stay horizontally centred on the selection at $scale",
                visualSelectionCenterX - barWidthPx / 2f,
                visualBarLeft,
                1e-3f,
            )
        }
    }

    @Test
    fun degeneratePageScaleIsClampedInsteadOfDividingByZero() {
        listOf(0f, -1f, Float.NaN).forEach { scale ->
            val m = metrics(scale)
            assertTrue("inverse scale must stay finite at $scale", m.inverseScale.isFinite())
            assertTrue("inverse scale must stay positive at $scale", m.inverseScale > 0f)
        }
        assertEquals(20f, metrics(0f).inverseScale, 1e-5f)
    }
}
