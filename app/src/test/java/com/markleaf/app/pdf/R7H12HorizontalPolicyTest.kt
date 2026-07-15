package com.markleaf.app.pdf

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class R7H12HorizontalPolicyTest {
    private val bounds = HorizontalPanBounds(-100f, 100f, -80f, 0f)
    private val policy = AxisOverscrollPolicy(
        horizontalEnabled = true,
        verticalEnabled = true,
        maxDistancePx = 24f,
        horizontalMaxDistancePx = 24f,
        verticalMaxDistancePx = 20f,
    )

    @Test
    fun `legal motion remains one to one before every edge`() {
        val result = HorizontalZoomPanPhysics.pan(
            HorizontalPanState(legalOffsetX = 0f, legalOffsetY = -40f),
            deltaX = 25f,
            deltaY = -15f,
            bounds = bounds,
            policy = policy,
            scale = 2.5f,
        )
        assertEquals(25f, result.legalConsumedX, 0f)
        assertEquals(-15f, result.legalConsumedY, 0f)
        assertEquals(0f, result.state.visualOverscrollX, 0f)
        assertEquals(0f, result.state.visualOverscrollY, 0f)
    }

    @Test
    fun `all four edges produce bounded display-only overscroll`() {
        val left = pull(HorizontalPanState(legalOffsetX = bounds.minX), -10_000f, 0f)
        val right = pull(HorizontalPanState(legalOffsetX = bounds.maxX), 10_000f, 0f)
        val top = pull(HorizontalPanState(legalOffsetY = bounds.minY), 0f, -10_000f)
        val bottom = pull(HorizontalPanState(legalOffsetY = bounds.maxY), 0f, 10_000f)

        assertEquals(bounds.minX, left.legalOffsetX, 0f)
        assertEquals(bounds.maxX, right.legalOffsetX, 0f)
        assertEquals(bounds.minY, top.legalOffsetY, 0f)
        assertEquals(bounds.maxY, bottom.legalOffsetY, 0f)
        assertTrue(left.visualOverscrollX in -24f..-23.9f)
        assertTrue(right.visualOverscrollX in 23.9f..24f)
        assertTrue(top.visualOverscrollY in -20f..-19.9f)
        assertTrue(bottom.visualOverscrollY in 19.9f..20f)
    }

    @Test
    fun `overscroll caps do not grow at two three or four x`() {
        val values = listOf(2f, 3f, 4f).map { scale ->
            HorizontalZoomPanPhysics.pan(
                HorizontalPanState(legalOffsetX = bounds.maxX, legalOffsetY = bounds.maxY),
                10_000f,
                10_000f,
                bounds,
                policy,
                scale,
            ).state
        }
        assertTrue(values.all { abs(it.visualOverscrollX - values.first().visualOverscrollX) < 0.001f })
        assertTrue(values.all { abs(it.visualOverscrollY - values.first().visualOverscrollY) < 0.001f })
    }

    @Test
    fun `shared spring clears both visual axes without changing legal offsets`() {
        val legal = ZoomPanPoint(100f, -80f)
        var spring = ZoomPanSpringState(ZoomPanPoint(23f, -19f))
        var elapsedMs = 0
        while (elapsedMs < 1_000) {
            val frame = ZoomPanPhysics.springStep(
                spring,
                elapsedNanos = 16_000_000L,
                config = ZoomPanPhysicsProfile.HorizontalSpring,
            )
            spring = frame.state
            elapsedMs += 16
            if (frame.settled) break
        }
        assertEquals(ZoomPanPoint.Zero, spring.positionPx)
        assertTrue(elapsedMs in 100..600)
        assertEquals(legal, ZoomPanTransform(legalOffsetPx = legal).legalOffsetPx)
    }

    @Test
    fun `navigable horizontal edge gives first residual directly to page strip`() {
        val decision = advance(ThreePageStripPhysics.idle(7L), -18f, 2f, -18f)
        assertEquals(ZoomedPageTransitionPhase.EDGE_PREVIEW, decision.transition.phase)
        assertEquals(-18f, decision.offsetPx, 0f)
        assertEquals(2, decision.transition.targetPage)
    }

    @Test
    fun `page strip has no second distance gate after the content edge`() {
        val tiny = advance(ThreePageStripPhysics.idle(71L), -6f, 0f, -6f)
        assertEquals(ZoomedPageTransitionPhase.EDGE_PREVIEW, tiny.transition.phase)
        assertEquals(-6f, tiny.offsetPx, 0f)
    }

    @Test
    fun `active strip remains inner controller owned for the rest of one gesture`() {
        val active = advance(ThreePageStripPhysics.idle(8L), -8f, 0f, -8f)
        val repeated = advance(active.transition, -100f, 0f, -100f, active.offsetPx)
        assertEquals(ZoomedPageTransitionPhase.EDGE_PREVIEW, repeated.transition.phase)
        assertEquals(-108f, repeated.offsetPx, 0f)
    }

    @Test
    fun `vertical diagonal motion can never enter page strip`() {
        val start = ThreePageStripPhysics.idle(9L)
        val vertical = advance(start, -20f, 40f, -20f)
        assertEquals(ZoomedPageTransitionPhase.IDLE, vertical.transition.phase)
    }

    @Test
    fun `unavailable document edge cannot enter page strip`() {
        val blocked = ThreePageStripPhysics.drag(
            transition = ThreePageStripPhysics.idle(10L),
            displayedOffsetPx = 0f,
            rawDeltaX = -100f,
            rawDeltaY = 0f,
            edgeResidualX = -100f,
            currentPage = 2,
            pageCount = 3,
            viewportWidthPx = 1000f,
            previousBaseReady = true,
            nextBaseReady = true,
        )
        assertEquals(ZoomedPageTransitionPhase.IDLE, blocked.transition.phase)
    }

    @Test
    fun `commit is one page only and keeps document session scale`() {
        val active = advance(ThreePageStripPhysics.idle(11L), -600f, 0f, -600f)
        val committed = ThreePageStripPhysics.release(
            active.transition, active.offsetPx, 1000f, 0f, 50f,
        )
        assertEquals(ZoomedPageTransitionPhase.COMMITTING, committed.phase)
        assertEquals(-1000f, ThreePageStripPhysics.settleTargetPx(committed, 1000f), 0f)
        val layout = HorizontalZoomPanPhysics.fitPage(1_000f, 1_800f, 1.4f, 8f)
        val session = PdfViewportSessionState(
            readingMode = PdfReadingMode.HORIZONTAL_PAGED,
            pageIndex = 4,
            pageAnchorXFraction = 0.8f,
            pageAnchorYFraction = 0.4f,
            scale = 2.5f,
            generation = 1L,
        )
        val target = HorizontalZoomPanPhysics.restoreViewportSession(
            session.copy(pageIndex = 5),
            layout,
            HorizontalPageTransitionDirection.NEXT,
        )
        assertEquals(2.5f, target.scale, 0f)
        assertEquals(HorizontalZoomPanPhysics.bounds(layout, 2.5f).maxX, target.pan.legalOffsetX, 0f)
    }

    @Test
    fun `cancelled handoff preserves scale and legal offset but clears visual state`() {
        val current = HorizontalPanState(
            legalOffsetX = -100f,
            legalOffsetY = -30f,
            rawOverscrollX = -70f,
            visualOverscrollX = -20f,
            rawOverscrollY = 15f,
            visualOverscrollY = 8f,
        )
        val cancelled = HorizontalZoomPanPhysics.cancelledHandoffTransform(2.5f, current)
        assertEquals(2.5f, cancelled.scale, 0f)
        assertEquals(-100f, cancelled.pan.legalOffsetX, 0f)
        assertEquals(-30f, cancelled.pan.legalOffsetY, 0f)
        assertEquals(0f, cancelled.pan.visualOverscrollX, 0f)
        assertEquals(0f, cancelled.pan.visualOverscrollY, 0f)
    }

    @Test
    fun `continuous disabled policy remains hard clamped on both axes`() {
        val transform = ZoomPanPhysics.applyPan(
            ZoomPanTransform(2.5f, ZoomPanPoint(100f, 0f)),
            ZoomPanPoint(500f, 500f),
            bounds.asZoomPanBounds(),
            ZoomPanPhysicsProfile.ContinuousOverscrollPolicy,
        ).transform
        assertEquals(ZoomPanPoint.Zero, transform.visualOverscrollPx)
        assertEquals(ZoomPanPoint.Zero, transform.rawOverscrollPx)
    }

    private fun pull(state: HorizontalPanState, dx: Float, dy: Float): HorizontalPanState =
        HorizontalZoomPanPhysics.pan(state, dx, dy, bounds, policy, scale = 2.5f).state

    private fun advance(
        state: PageStripTransition,
        dx: Float,
        dy: Float,
        residualX: Float,
        offsetPx: Float = 0f,
    ): PageStripDragResult = ThreePageStripPhysics.drag(
        transition = state,
        displayedOffsetPx = offsetPx,
        rawDeltaX = dx,
        rawDeltaY = dy,
        edgeResidualX = residualX,
        currentPage = 1,
        pageCount = 3,
        viewportWidthPx = 1000f,
        previousBaseReady = true,
        nextBaseReady = true,
    )
}
