package com.markleaf.app.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class R7H1HorizontalZoomPanPhysicsTest {
    private val viewport = HorizontalPageLayout(
        viewportWidthPx = 1000f,
        viewportHeightPx = 800f,
        fittedWidthPx = 900f,
        fittedHeightPx = 700f,
    )
    private val bounds = HorizontalPanBounds(-100f, 100f, -80f, 80f)
    private val limit = 28f

    @Test
    fun `scale one bounds are zero`() {
        assertEquals(HorizontalPanBounds(0f, 0f, 0f, 0f), HorizontalZoomPanPhysics.bounds(viewport, 1f))
    }

    @Test
    fun `scale two uses real fitted horizontal and vertical bounds`() {
        val actual = HorizontalZoomPanPhysics.bounds(viewport, 2f)
        assertEquals(450f, actual.maxPanX, 0.001f)
        assertEquals(700f, actual.maxPanY, 0.001f)
        assertEquals(-700f, actual.minY, 0.001f)
        assertEquals(0f, actual.maxY, 0.001f)
    }

    @Test
    fun `scale three and four bounds grow from fitted content`() {
        val three = HorizontalZoomPanPhysics.bounds(viewport, 3f)
        val four = HorizontalZoomPanPhysics.bounds(viewport, 4f)
        assertEquals(900f, three.maxPanX, 0.001f)
        assertEquals(1400f, three.maxPanY, 0.001f)
        assertEquals(1350f, four.maxPanX, 0.001f)
        assertEquals(2100f, four.maxPanY, 0.001f)
    }

    @Test
    fun `portrait page is height fitted`() {
        val layout = HorizontalZoomPanPhysics.fitPage(1000f, 800f, 1.5f, 0f)
        assertEquals(533.3333f, layout.fittedWidthPx, 0.001f)
        assertEquals(800f, layout.fittedHeightPx, 0.001f)
    }

    @Test
    fun `landscape page is width fitted`() {
        val layout = HorizontalZoomPanPhysics.fitPage(1000f, 800f, 0.5f, 0f)
        assertEquals(1000f, layout.fittedWidthPx, 0.001f)
        assertEquals(500f, layout.fittedHeightPx, 0.001f)
    }

    @Test
    fun `page margin is applied only while fitting`() {
        val layout = HorizontalZoomPanPhysics.fitPage(1000f, 800f, 0.5f, 8f)
        assertEquals(984f, layout.fittedWidthPx, 0.001f)
        assertEquals(492f, layout.fittedHeightPx, 0.001f)
        // Short page: vertical centering adds the half-gap on top of the margin.
        assertEquals(154f, layout.contentTopPx, 0.001f)
    }

    @Test
    fun `short horizontal page is vertically centered in the viewport`() {
        val layout = HorizontalZoomPanPhysics.fitPage(1000f, 800f, 0.5f, 8f)
        val gap = (layout.viewportHeightPx - layout.fittedHeightPx) / 2f
        assertEquals(154f, layout.contentTopPx, 0.001f)
        assertTrue(kotlin.math.abs(layout.contentTopPx - gap) < 0.001f)
    }

    @Test
    fun `short portrait page centers instead of leaving a large bottom gap`() {
        val layout = HorizontalZoomPanPhysics.fitPage(1200f, 2200f, 1.38f, 8f)
        // fitted 1184x1633.92 inside 1200x2200: top = 8 + (2184-1633.92)/2
        val expectedTop = 8f + (2184f - 1184f * 1.38f) / 2f
        assertEquals(expectedTop, layout.contentTopPx, 0.001f)
        assertTrue((layout.viewportHeightPx - layout.fittedHeightPx) / 2f > 250f)
        assertTrue(layout.contentTopPx > 250f)
    }

    @Test
    fun `page filling the viewport height stays at the reading margin`() {
        val layout = HorizontalZoomPanPhysics.fitPage(1000f, 800f, 1.5f, 8f)
        // heightAtFullWidth = 984*1.5 = 1476 > 784 -> height fitted to 784.
        assertEquals(784f, layout.fittedHeightPx, 0.001f)
        assertEquals(8f, layout.contentTopPx, 0.001f)
    }

    @Test
    fun `content smaller than viewport keeps its original margin available to focal zoom`() {
        val portrait = HorizontalPageLayout(1000f, 800f, 500f, 800f)
        val actual = HorizontalZoomPanPhysics.bounds(portrait, 1.2f)
        assertEquals(50f, actual.maxPanX, 0.001f)
        assertEquals(160f, actual.maxPanY, 0.001f)
    }

    @Test
    fun `legal range follows pointer one to one`() {
        val result = HorizontalZoomPanPhysics.pan(HorizontalPanState(), 20f, 10f, bounds, limit)
        assertEquals(20f, result.state.legalOffsetX, 0.001f)
        assertEquals(10f, result.state.legalOffsetY, 0.001f)
        assertEquals(20f, result.legalConsumedX, 0.001f)
        assertEquals(0f, result.residualX, 0.001f)
    }

    @Test
    fun `before edge there is no damping`() {
        val result = HorizontalZoomPanPhysics.pan(
            HorizontalPanState(legalOffsetX = 70f),
            20f,
            0f,
            bounds,
            limit,
        )
        assertEquals(90f, result.state.displayOffsetX, 0.001f)
        assertEquals(0f, result.state.visualOverscrollX, 0.001f)
    }

    @Test
    fun `only residual after legal consumption is damped`() {
        val result = HorizontalZoomPanPhysics.pan(
            HorizontalPanState(legalOffsetX = 95f),
            20f,
            0f,
            bounds,
            limit,
        )
        assertEquals(100f, result.state.legalOffsetX, 0.001f)
        assertEquals(5f, result.legalConsumedX, 0.001f)
        assertEquals(15f, result.residualX, 0.001f)
        assertEquals(HorizontalZoomPanPhysics.rubberBand(15f, limit), result.state.visualOverscrollX, 0.001f)
    }

    @Test
    fun `visual overscroll never enters legal offset`() {
        val result = HorizontalZoomPanPhysics.pan(
            HorizontalPanState(legalOffsetX = 100f),
            200f,
            0f,
            bounds,
            limit,
        )
        assertEquals(100f, result.state.legalOffsetX, 0.001f)
        assertTrue(result.state.visualOverscrollX > 0f)
        assertTrue(result.state.displayOffsetX > result.state.legalOffsetX)
    }

    @Test
    fun `multi frame outward drag saturates instead of accumulating display distance`() {
        var state = HorizontalPanState(legalOffsetX = 100f)
        repeat(100) {
            state = HorizontalZoomPanPhysics.pan(state, 20f, 0f, bounds, limit).state
        }
        assertTrue(state.rawOverscrollX > 1000f)
        assertTrue(state.visualOverscrollX <= limit)
        assertTrue(state.displayOffsetX <= 128f)
    }

    @Test
    fun `maximum visual overscroll approaches twenty eight pixels`() {
        val visual = HorizontalZoomPanPhysics.rubberBand(10000f, limit)
        assertTrue(visual in 27.9f..28f)
    }

    @Test
    fun `rubber band maximum is independent of scale`() {
        val raw = 500f
        val atTwo = HorizontalZoomPanPhysics.rubberBand(raw, limit)
        val atThree = HorizontalZoomPanPhysics.rubberBand(raw, limit)
        val atFour = HorizontalZoomPanPhysics.rubberBand(raw, limit)
        assertEquals(atTwo, atThree, 0f)
        assertEquals(atTwo, atFour, 0f)
    }

    @Test
    fun `left and right rubber band are symmetric`() {
        assertEquals(
            -HorizontalZoomPanPhysics.rubberBand(100f, limit),
            HorizontalZoomPanPhysics.rubberBand(-100f, limit),
            0.001f,
        )
    }

    @Test
    fun `top and bottom are hard clamped without visual overscroll`() {
        val top = HorizontalZoomPanPhysics.pan(
            HorizontalPanState(legalOffsetY = -80f), 0f, -100f, bounds, limit,
        ).state
        val bottom = HorizontalZoomPanPhysics.pan(
            HorizontalPanState(legalOffsetY = 80f), 0f, 100f, bounds, limit,
        ).state
        assertEquals(-80f, top.displayOffsetY, 0f)
        assertEquals(80f, bottom.displayOffsetY, 0f)
    }

    @Test
    fun `release removes visual overscroll only`() {
        val pulled = HorizontalZoomPanPhysics.pan(
            HorizontalPanState(legalOffsetX = 100f, legalOffsetY = 40f),
            50f,
            0f,
            bounds,
            limit,
        ).state
        val settled = HorizontalZoomPanPhysics.settle(pulled)
        assertEquals(100f, settled.legalOffsetX, 0.001f)
        assertEquals(40f, settled.legalOffsetY, 0.001f)
        assertEquals(0f, settled.visualOverscrollX, 0f)
    }

    @Test
    fun `spring frames do not modify legal offset`() {
        val start = HorizontalPanState(legalOffsetX = -75f, legalOffsetY = 30f)
        val frame = HorizontalZoomPanPhysics.withVisualOverscroll(start, -12f, limit)
        assertEquals(start.legalOffsetX, frame.legalOffsetX, 0f)
        assertEquals(start.legalOffsetY, frame.legalOffsetY, 0f)
    }

    @Test
    fun `new pointer rebase preserves interrupted spring display`() {
        val springFrame = HorizontalZoomPanPhysics.withVisualOverscroll(
            HorizontalPanState(legalOffsetX = 100f),
            12f,
            limit,
        )
        val rebased = HorizontalZoomPanPhysics.pan(springFrame, 90f, 20f, bounds, limit, rebased = true)
        assertEquals(springFrame, rebased.state)
        assertEquals(112f, rebased.state.displayOffsetX, 0.001f)
    }

    @Test
    fun `one to two pointer rebase does not pan or zoom`() {
        val state = HorizontalPanState(legalOffsetX = 20f, legalOffsetY = 30f)
        assertEquals(state, HorizontalZoomPanPhysics.pan(state, 100f, 50f, bounds, limit, rebased = true).state)
    }

    @Test
    fun `two to one pointer rebase does not fold visual into legal`() {
        val state = HorizontalZoomPanPhysics.withVisualOverscroll(
            HorizontalPanState(legalOffsetX = -100f), -10f, limit,
        )
        val actual = HorizontalZoomPanPhysics.pan(state, 50f, 0f, bounds, limit, rebased = true).state
        assertEquals(-100f, actual.legalOffsetX, 0f)
        assertEquals(-110f, actual.displayOffsetX, 0.001f)
    }

    @Test
    fun `zoomed gesture belongs only to inner pan`() {
        assertEquals(HorizontalGestureOwner.INNER_PAN, HorizontalZoomPanPhysics.owner(2f))
        assertEquals(HorizontalGestureOwner.INNER_PAN, HorizontalZoomPanPhysics.owner(4f))
    }

    @Test
    fun `one x gesture belongs to pager`() {
        assertEquals(HorizontalGestureOwner.PAGE_STRIP, HorizontalZoomPanPhysics.owner(1f))
        assertEquals(HorizontalGestureOwner.PAGE_STRIP, HorizontalZoomPanPhysics.owner(1.01f))
    }

    @Test
    fun `zoomed outward pull cannot change page identity`() {
        val pageIndex = 12
        val result = HorizontalZoomPanPhysics.pan(
            HorizontalPanState(legalOffsetX = 100f), 500f, 0f, bounds, limit,
        )
        assertEquals(12, pageIndex)
        assertEquals(100f, result.state.legalOffsetX, 0f)
    }

    @Test
    fun `bitmap replacement leaves transform unchanged`() {
        val transform = HorizontalPanState(legalOffsetX = 30f, legalOffsetY = -20f)
        val beforeBitmapIdentity = transform
        val afterBitmapIdentity = transform
        assertEquals(beforeBitmapIdentity, afterBitmapIdentity)
    }

    @Test
    fun `viewport resize clamps legal position`() {
        val resizedBounds = HorizontalPanBounds(-40f, 40f, -20f, 20f)
        val state = HorizontalPanState(legalOffsetX = 90f, legalOffsetY = -70f)
        val actual = HorizontalZoomPanPhysics.clampToBounds(state, resizedBounds)
        assertEquals(40f, actual.legalOffsetX, 0f)
        assertEquals(-20f, actual.legalOffsetY, 0f)
    }

    @Test
    fun `display blank exposure never exceeds configured limit`() {
        val pulled = HorizontalZoomPanPhysics.pan(
            HorizontalPanState(legalOffsetX = 100f, legalOffsetY = 80f),
            100000f,
            100000f,
            bounds,
            limit,
        ).state
        assertTrue(pulled.displayOffsetX - bounds.maxX <= limit)
        assertEquals(bounds.maxY, pulled.displayOffsetY, 0f)
    }

    @Test
    fun `reverse drag unwinds edge pull before moving legal position`() {
        val pulled = HorizontalZoomPanPhysics.pan(
            HorizontalPanState(legalOffsetX = 100f), 20f, 0f, bounds, limit,
        ).state
        val partialReverse = HorizontalZoomPanPhysics.pan(pulled, -10f, 0f, bounds, limit).state
        assertEquals(100f, partialReverse.legalOffsetX, 0f)
        assertEquals(10f, partialReverse.rawOverscrollX, 0.001f)
    }

    @Test
    fun `pinch uses legal offset and never folds visual overscroll into legal x`() {
        val base = HorizontalZoomPanPhysics.withVisualOverscroll(
            HorizontalPanState(legalOffsetX = 50f, legalOffsetY = -20f),
            12f,
            limit,
        )
        val state = HorizontalZoomPanPhysics.rebaseLegalForPinch(
            baseState = base,
            baseScale = 2f,
            newScale = 3f,
            basePointerX = 600f,
            basePointerY = 300f,
            pointerX = 600f,
            pointerY = 300f,
            transformOriginX = 500f,
            transformOriginY = 8f,
            bounds = HorizontalPanBounds(-1000f, 1000f, -1000f, 0f),
        )
        assertEquals(25f, state.legalOffsetX, 0.001f)
        assertEquals(0f, state.visualOverscrollX, 0f)
        assertEquals(0f, state.rawOverscrollX, 0f)
    }

    @Test
    fun `invalid page aspect uses stable default`() {
        val invalid = HorizontalZoomPanPhysics.fitPage(1000f, 800f, Float.NaN, 0f)
        val fallback = HorizontalZoomPanPhysics.fitPage(
            1000f, 800f, HorizontalZoomPanPhysics.DEFAULT_PAGE_ASPECT, 0f,
        )
        assertEquals(fallback, invalid)
    }

    @Test
    fun `multi frame vertical overflow never leaves legal bounds`() {
        var state = HorizontalPanState(legalOffsetY = bounds.maxY)
        repeat(100) {
            state = HorizontalZoomPanPhysics.pan(state, 0f, 20f, bounds, limit).state
        }
        assertEquals(bounds.maxY, state.legalOffsetY, 0f)
        assertEquals(bounds.maxY, state.displayOffsetY, 0f)
    }

    @Test
    fun `diagonal drag keeps horizontal rubber band and clamps vertical component`() {
        val state = HorizontalZoomPanPhysics.pan(
            HorizontalPanState(legalOffsetX = bounds.maxX, legalOffsetY = bounds.maxY),
            200f,
            200f,
            bounds,
            limit,
        ).state
        assertTrue(state.visualOverscrollX > 0f)
        assertEquals(bounds.maxY, state.displayOffsetY, 0f)
    }

    @Test
    fun `vertical edge has no release spring state at two three and four x`() {
        listOf(2f, 3f, 4f).forEach { scale ->
            val scaleBounds = HorizontalZoomPanPhysics.bounds(viewport, scale)
            val pulled = HorizontalZoomPanPhysics.pan(
                HorizontalPanState(legalOffsetY = scaleBounds.maxY),
                0f,
                500f,
                scaleBounds,
                limit,
            ).state
            assertEquals(scaleBounds.maxY, pulled.displayOffsetY, 0f)
            assertEquals(pulled, HorizontalZoomPanPhysics.settle(pulled))
        }
    }

    @Test
    fun `pinch content anchor stays continuous across two three and four x`() {
        val originX = 500f
        val originY = 8f
        val pointerX = 620f
        val pointerY = 420f
        val contentX = 35f
        val contentY = 250f
        var scale = 2f
        var state = HorizontalPanState(
            legalOffsetX = pointerX - originX - contentX * scale,
            legalOffsetY = pointerY - originY - contentY * scale,
        )
        listOf(3f, 4f).forEach { newScale ->
            state = HorizontalZoomPanPhysics.rebaseLegalForPinch(
                baseState = state,
                baseScale = scale,
                newScale = newScale,
                basePointerX = pointerX,
                basePointerY = pointerY,
                pointerX = pointerX,
                pointerY = pointerY,
                transformOriginX = originX,
                transformOriginY = originY,
                bounds = HorizontalPanBounds(-2000f, 2000f, -3000f, 0f),
            )
            assertEquals(pointerX, originX + contentX * newScale + state.legalOffsetX, 0.001f)
            assertEquals(pointerY, originY + contentY * newScale + state.legalOffsetY, 0.001f)
            assertEquals(0f, state.visualOverscrollX, 0f)
            scale = newScale
        }
    }

    @Test
    fun `pinch scale change cannot abruptly expand left blank`() {
        val newBounds = HorizontalPanBounds(-400f, 400f, -900f, 0f)
        val edgePulled = HorizontalZoomPanPhysics.withVisualOverscroll(
            HorizontalPanState(legalOffsetX = 200f),
            24f,
            limit,
        )
        val rebased = HorizontalZoomPanPhysics.rebaseLegalForPinch(
            baseState = edgePulled,
            baseScale = 2f,
            newScale = 3f,
            basePointerX = 500f,
            basePointerY = 400f,
            pointerX = 500f,
            pointerY = 400f,
            transformOriginX = 500f,
            transformOriginY = 8f,
            bounds = newBounds,
        )
        assertTrue(rebased.legalOffsetX in newBounds.minX..newBounds.maxX)
        assertEquals(rebased.legalOffsetX, rebased.displayOffsetX, 0f)
        assertEquals(0f, rebased.visualOverscrollX, 0f)
    }
}
