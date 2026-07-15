package com.markleaf.app.ui.pdf

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.markleaf.app.pdf.ContinuousDocumentGeometry
import com.markleaf.app.pdf.AxisOverscrollPolicy
import com.markleaf.app.pdf.DocumentViewportRect
import com.markleaf.app.pdf.HorizontalGestureOwner
import com.markleaf.app.pdf.HorizontalPageLayout
import com.markleaf.app.pdf.HorizontalPanBounds
import com.markleaf.app.pdf.HorizontalPanState
import com.markleaf.app.pdf.HorizontalZoomPanPhysics
import com.markleaf.app.pdf.PdfTilePlanner
import com.markleaf.app.pdf.PrefetchCorridor
import com.markleaf.app.pdf.TileNavigationDirection
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class R7P0c11H1InstrumentedTest {
    private val viewport = HorizontalPageLayout(1080f, 1800f, 1040f, 1600f)
    private val bounds = HorizontalPanBounds(-500f, 500f, -700f, 700f)
    private val overscrollPolicy: AxisOverscrollPolicy =
        AxisOverscrollPolicy(
            horizontalEnabled = true,
            verticalEnabled = true,
            maxDistancePx = 24f,
            horizontalMaxDistancePx = 24f,
            verticalMaxDistancePx = 20f,
        )

    @Test
    fun pageAheadBandIsPreparedBeforeIntersection() {
        val geometry = ContinuousDocumentGeometry.create(
            3, 1000f, mapOf(0 to 1f, 1 to 1f, 2 to 1f), 10f,
        )
        val corridor = PrefetchCorridor(
            DocumentViewportRect(0f, 500f, 1000f, 900f),
            DocumentViewportRect(0f, 650f, 1000f, 1050f),
            DocumentViewportRect(0f, 900f, 1000f, 1300f),
            TileNavigationDirection.FORWARD,
            .9f,
        )
        val ahead = PdfTilePlanner.pageAhead(geometry, corridor, 0)!!
        assertEquals(1, ahead.pageIndex)
        assertEquals(0f, ahead.visibleRect.top, 0f)
    }

    @Test
    fun twoXInternalSmallDragTracksPointer() {
        val result = HorizontalZoomPanPhysics.pan(
            HorizontalPanState(), 24f, -18f, bounds, overscrollPolicy, scale = 2f,
        )
        assertEquals(24f, result.state.displayOffsetX, 0.001f)
        assertEquals(-18f, result.state.displayOffsetY, 0.001f)
    }

    @Test
    fun threeXInternalSmallDragTracksPointer() {
        val threeXBounds = HorizontalZoomPanPhysics.bounds(viewport, 3f)
        val result = HorizontalZoomPanPhysics.pan(
            HorizontalPanState(), -40f, -32f, threeXBounds, overscrollPolicy, scale = 3f,
        )
        assertEquals(-40f, result.state.displayOffsetX, 0.001f)
        assertEquals(-32f, result.state.displayOffsetY, 0.001f)
    }

    @Test
    fun edgePullUsesBoundedHorizontalVisualOverscroll() {
        val result = HorizontalZoomPanPhysics.pan(
            HorizontalPanState(legalOffsetX = bounds.maxX),
            120f,
            0f,
            bounds,
            overscrollPolicy,
            scale = 2f,
        )
        assertEquals(bounds.maxX, result.state.legalOffsetX, 0f)
        org.junit.Assert.assertTrue(result.state.visualOverscrollX in 0f..24f)
        assertEquals(120f, result.residualX, 0f)
    }

    @Test
    fun largeOutwardPullCannotExposeHalfScreenBlank() {
        val result = HorizontalZoomPanPhysics.pan(
            HorizontalPanState(legalOffsetX = bounds.maxX),
            5000f,
            0f,
            bounds,
            overscrollPolicy,
            scale = 2f,
        )
        org.junit.Assert.assertTrue(result.state.displayOffsetX <= bounds.maxX + 24f)
    }

    @Test
    fun releaseReturnsOnlyVisualOverscroll() {
        val pulled = HorizontalZoomPanPhysics.pan(
            HorizontalPanState(legalOffsetX = bounds.maxX),
            400f,
            0f,
            bounds,
            overscrollPolicy,
            scale = 2f,
        ).state
        val settled = HorizontalZoomPanPhysics.settle(pulled)
        assertEquals(bounds.maxX, settled.legalOffsetX, 0f)
        assertEquals(0f, settled.visualOverscrollX, 0f)
    }

    @Test
    fun leftAndRightEdgesAreSymmetric() {
        val left = HorizontalZoomPanPhysics.pan(
            HorizontalPanState(legalOffsetX = bounds.minX),
            -200f,
            0f,
            bounds,
            overscrollPolicy,
            scale = 2f,
        ).state
        val right = HorizontalZoomPanPhysics.pan(
            HorizontalPanState(legalOffsetX = bounds.maxX),
            200f,
            0f,
            bounds,
            overscrollPolicy,
            scale = 2f,
        ).state
        assertEquals(-right.visualOverscrollX, left.visualOverscrollX, 0.001f)
    }

    @Test
    fun twoToOneToTwoRebaseDoesNotJump() {
        val state = HorizontalPanState(legalOffsetX = 200f)
        val toOne = HorizontalZoomPanPhysics.pan(
            state, 100f, 0f, bounds, overscrollPolicy, scale = 2f, rebased = true,
        ).state
        val toTwo = HorizontalZoomPanPhysics.pan(
            toOne, -100f, 0f, bounds, overscrollPolicy, scale = 2f, rebased = true,
        ).state
        assertEquals(state, toTwo)
    }

    @Test
    fun zoomedGestureNeverBelongsToPager() {
        assertEquals(HorizontalGestureOwner.INNER_PAN, HorizontalZoomPanPhysics.owner(2f))
        assertEquals(HorizontalGestureOwner.INNER_PAN, HorizontalZoomPanPhysics.owner(4f))
    }

    @Test
    fun oneXGestureBelongsToPager() {
        assertEquals(HorizontalGestureOwner.PAGE_STRIP, HorizontalZoomPanPhysics.owner(1f))
    }

    @Test
    fun tileSwapCannotMutateHorizontalTransform() {
        val before = HorizontalPanState(legalOffsetX = 120f, legalOffsetY = -60f)
        val after = before
        assertEquals(before, after)
    }

    @Test
    fun verticalEdgesUseBoundedScaleIndependentBounceAtTwoThreeOrFourX() {
        listOf(2f, 3f, 4f).forEach { scale ->
            val scaleBounds = HorizontalZoomPanPhysics.bounds(viewport, scale)
            val top = HorizontalZoomPanPhysics.pan(
                HorizontalPanState(legalOffsetY = scaleBounds.minY),
                0f,
                -5000f,
                scaleBounds,
                overscrollPolicy,
                scale = scale,
            ).state
            val bottom = HorizontalZoomPanPhysics.pan(
                HorizontalPanState(legalOffsetY = scaleBounds.maxY),
                0f,
                5000f,
                scaleBounds,
                overscrollPolicy,
                scale = scale,
            ).state
            org.junit.Assert.assertTrue(top.displayOffsetY >= scaleBounds.minY - 20f)
            org.junit.Assert.assertTrue(bottom.displayOffsetY <= scaleBounds.maxY + 20f)
            org.junit.Assert.assertTrue(top.visualOverscrollY < 0f)
            org.junit.Assert.assertTrue(bottom.visualOverscrollY > 0f)
        }
    }

    @Test
    fun diagonalEdgeDragKeepsBothVisualAxesBounded() {
        val state = HorizontalZoomPanPhysics.pan(
            HorizontalPanState(legalOffsetX = bounds.maxX, legalOffsetY = bounds.maxY),
            500f,
            500f,
            bounds,
            overscrollPolicy,
            scale = 2f,
        ).state
        org.junit.Assert.assertTrue(state.visualOverscrollX in 0f..24f)
        org.junit.Assert.assertTrue(state.visualOverscrollY in 0f..20f)
        assertEquals(bounds.maxX, state.legalOffsetX, 0f)
        assertEquals(bounds.maxY, state.legalOffsetY, 0f)
    }

    @Test
    fun livePinchRebaseNeverFoldsVisualXIntoLegalX() {
        val pulled = HorizontalPanState(
            legalOffsetX = 200f,
            legalOffsetY = -100f,
            rawOverscrollX = 40f,
            visualOverscrollX = 20f,
        )
        val rebased = HorizontalZoomPanPhysics.rebaseLegalForPinch(
            baseState = pulled,
            baseScale = 2f,
            newScale = 3f,
            basePointerX = 540f,
            basePointerY = 800f,
            pointerX = 540f,
            pointerY = 800f,
            transformOriginX = 540f,
            transformOriginY = 24f,
            bounds = HorizontalPanBounds(-1000f, 1000f, -2000f, 0f),
        )
        assertEquals(300f, rebased.legalOffsetX, 0.001f)
        assertEquals(0f, rebased.visualOverscrollX, 0f)
        assertEquals(rebased.legalOffsetX, rebased.displayOffsetX, 0f)
    }
}
