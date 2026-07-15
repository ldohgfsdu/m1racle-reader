package com.markleaf.app.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class R7H13HorizontalRealPathRecoveryTest {
    private val layout = HorizontalPageLayout(
        viewportWidthPx = 1_000f,
        viewportHeightPx = 1_800f,
        fittedWidthPx = 960f,
        fittedHeightPx = 1_360f,
        contentTopPx = 24f,
    )

    @Test
    fun visibleRectUsesDisplayedTransformWithoutChangingPageGeometry() {
        val view = checkNotNull(
            HorizontalTilePlanning.visiblePage(
                pageIndex = 2,
                layout = layout,
                scale = 2.5f,
                pan = HorizontalPanState(legalOffsetX = -180f, legalOffsetY = -220f),
            ),
        )
        assertEquals(2, view.pageIndex)
        assertEquals(960f, view.pageWidth, 0f)
        assertEquals(1_360f, view.pageHeight, 0f)
        assertTrue(view.visibleRect.width in 399f..401f)
        assertTrue(view.visibleRect.height in 719f..721f)
    }

    @Test
    fun pointerDownPlanPrioritizesCurrentVisibleBeforePageAhead() {
        val visible = checkNotNull(HorizontalTilePlanning.visiblePage(1, layout, 2.5f, HorizontalPanState()))
        val ahead = checkNotNull(
            HorizontalTilePlanning.pageAhead(
                pageIndex = 1,
                pageCount = 3,
                direction = HorizontalPageTransitionDirection.NEXT,
                layoutForPage = { layout },
                visible = visible,
            ),
        )
        val snapshot = PdfViewportSnapshot(
            documentGeneration = 7L,
            geometryGeneration = visible.geometryGeneration,
            viewportRectDocument = DocumentViewportRect(0f, 0f, 400f, 700f),
            scale = 2.5f,
            focalPointViewport = null,
            velocityX = 0f,
            velocityY = 0f,
            zoomVelocity = 0f,
            motionConfidence = 1f,
            interactionPhase = TileInteractionPhase.PINCHING,
            timestampNanos = 11L,
            sequence = 4L,
            pointerDown = true,
            currentPage = 1,
        )
        val plan = HorizontalTilePlanning.plan("doc", 7L, snapshot, visible, 2, 3, ahead)
        assertTrue(plan.currentVisibleKeys.isNotEmpty())
        assertTrue(plan.requests.any { it.priority == TilePriority.PAGE_AHEAD && it.key.pageIndex == 2 })
        val firstAhead = plan.requests.indexOfFirst { it.priority == TilePriority.PAGE_AHEAD }
        val lastVisible = plan.requests.indexOfLast { TileRenderPipelinePolicy.isCurrentVisible(it.priority) }
        assertTrue(firstAhead > lastVisible)
    }

    @Test
    fun nextStripNeighbourPreheatsItsLeftEnteringBandAtNormalizedVerticalAnchor() {
        val visible = PageTileViewport(
            pageIndex = 1,
            pageWidth = 1_000f,
            pageHeight = 2_000f,
            visibleRect = PageLayoutRect(200f, 800f, 700f, 1_300f),
            geometryGeneration = 1L,
            fittedPageWidthVersion = 1_000,
        )
        val next = checkNotNull(
            HorizontalTilePlanning.pageAhead(
                1, 3, HorizontalPageTransitionDirection.NEXT,
                layoutForPage = { layout.copy(fittedWidthPx = 900f, fittedHeightPx = 1_500f) },
                visible = visible,
            ),
        )
        assertEquals(0f, next.visibleRect.left, 0f)
        assertEquals(600f, next.visibleRect.top, 0.01f)
    }

    @Test
    fun previousStripNeighbourPreheatsItsRightEnteringBand() {
        val visible = PageTileViewport(
            pageIndex = 1,
            pageWidth = 1_000f,
            pageHeight = 2_000f,
            visibleRect = PageLayoutRect(200f, 400f, 700f, 900f),
            geometryGeneration = 1L,
            fittedPageWidthVersion = 1_000,
        )
        val previous = checkNotNull(
            HorizontalTilePlanning.pageAhead(
                1, 3, HorizontalPageTransitionDirection.PREVIOUS,
                layoutForPage = { layout.copy(fittedWidthPx = 900f, fittedHeightPx = 1_500f) },
                visible = visible,
            ),
        )
        assertEquals(900f, previous.visibleRect.right, 0f)
        assertEquals(400f, previous.visibleRect.left, 0f)
    }

    @Test
    fun twelveDpPullHasClearlyVisibleEarlyResistance() {
        val visual = ZoomPanPhysics.rubberBand(rawOverscrollPx = 12f, limitPx = 24f)
        assertTrue(visual >= 11f)
        assertTrue(visual < 24f)
    }

    @Test
    fun horizontalSpringSettlesInsideHumanAcceptanceWindow() {
        var spring = ZoomPanSpringState(positionPx = ZoomPanPoint(24f, 20f))
        var elapsedMs = 0
        while (elapsedMs < 1_000) {
            val step = ZoomPanPhysics.springStep(
                spring = spring,
                elapsedNanos = 8_333_333L,
                config = ZoomPanPhysicsProfile.HorizontalSpring,
            )
            spring = step.state
            elapsedMs += 8
            if (step.settled) break
        }
        assertTrue("spring elapsed=$elapsedMs ms", elapsedMs in 180..260)
    }

    @Test
    fun pointerSetTransitionFramePreservesExactTransform() {
        val bounds = ZoomPanBounds(-500f, 500f, -800f, 0f)
        val initial = ZoomPanTransform(
            scale = 2.5f,
            legalOffsetPx = ZoomPanPoint(130f, -240f),
        )
        val two = ZoomPanPointerEpochReducer.frame(
            previous = null,
            pointers = listOf(
                ZoomPanPointer(3L, ZoomPanPoint(380f, 620f)),
                ZoomPanPointer(8L, ZoomPanPoint(700f, 620f)),
            ),
            transform = initial,
            transformOriginViewportPx = ZoomPanPoint(540f, 24f),
            boundsForScale = { bounds },
            scaleRange = ZoomPanPhysicsProfile.ScaleRange,
            overscrollPolicy = AxisOverscrollPolicy(true, true, 80f),
        )
        val one = ZoomPanPointerEpochReducer.frame(
            previous = two.epoch,
            pointers = listOf(ZoomPanPointer(3L, ZoomPanPoint(380f, 620f))),
            transform = two.transform,
            transformOriginViewportPx = ZoomPanPoint(540f, 24f),
            boundsForScale = { bounds },
            scaleRange = ZoomPanPhysicsProfile.ScaleRange,
            overscrollPolicy = AxisOverscrollPolicy(true, true, 80f),
        )
        assertTrue(one.rebased)
        assertEquals(two.transform, one.transform)
    }
}
