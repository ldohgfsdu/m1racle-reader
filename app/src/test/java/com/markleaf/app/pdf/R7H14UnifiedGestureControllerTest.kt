package com.markleaf.app.pdf

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class R7H14UnifiedGestureControllerTest {
    private val bounds = ZoomPanBounds(-800f, 200f, -2_000f, 0f)

    @Test
    fun `accepted continuous pan trace is unchanged through the shared controller`() {
        val initial = ZoomPanTransform(2f, ZoomPanPoint(-500f, -20_000f))
        val controller = PdfGestureTransformController(initial)
        controller.beginGesture(
            initial,
            listOf(pointer(7, 400f, 700f)),
            boundsForScale = { ZoomPanBounds(-1_000f, 0f, -100_000f, 0f) },
            overscrollPolicy = ZoomPanPhysicsProfile.ContinuousOverscrollPolicy,
        )
        val frame = controller.pointerFrame(
            listOf(pointer(7, 520f, 620f)),
            ZoomPanPoint.Zero,
            boundsForScale = { ZoomPanBounds(-1_000f, 0f, -100_000f, 0f) },
            overscrollPolicy = ZoomPanPhysicsProfile.ContinuousOverscrollPolicy,
        )

        assertEquals(120f, frame.rawCentroidDeltaPx.x, 0f)
        assertEquals(-80f, frame.rawCentroidDeltaPx.y, 0f)
        assertEquals(120f, frame.legalConsumedPx.x, 0f)
        assertEquals(-80f, frame.legalConsumedPx.y, 0f)
        assertEquals(ZoomPanPoint.Zero, frame.residualPx)
    }

    @Test
    fun `continuous and horizontal adapters get identical legal drag gain from one controller`() {
        fun run(policy: AxisOverscrollPolicy): PdfGestureControllerFrame {
            val controller = PdfGestureTransformController(ZoomPanTransform(2.5f, ZoomPanPoint(-300f, -700f)))
            controller.beginGesture(
                controller.snapshot().transform,
                listOf(pointer(1, 300f, 500f)),
                boundsForScale = { bounds },
                overscrollPolicy = policy,
            )
            return controller.pointerFrame(
                listOf(pointer(1, 345f, 465f)),
                ZoomPanPoint.Zero,
                boundsForScale = { bounds },
                overscrollPolicy = policy,
            )
        }

        val continuous = run(ZoomPanPhysicsProfile.ContinuousOverscrollPolicy)
        val horizontal = run(AxisOverscrollPolicy(true, true, 24f, 24f, 20f))
        assertEquals(continuous.legalConsumedPx, horizontal.legalConsumedPx)
        assertEquals(1f, horizontal.legalConsumedPx.x / horizontal.rawCentroidDeltaPx.x, 0.001f)
        assertEquals(1f, horizontal.legalConsumedPx.y / horizontal.rawCentroidDeltaPx.y, 0.001f)
    }

    @Test
    fun `pager owned single pointer cannot poison later pinch transform`() {
        val initial = ZoomPanTransform(1f)
        val controller = PdfGestureTransformController(initial)
        val policy = AxisOverscrollPolicy(true, true, 24f, 20f, 20f)
        controller.beginGesture(
            initial,
            listOf(pointer(4, 200f, 600f)),
            boundsForScale = { ZoomPanBounds(0f, 0f, 0f, 0f) },
            overscrollPolicy = policy,
        )
        val pagerFrame = controller.trackPointersWithoutTransform(
            listOf(pointer(4, 360f, 600f)),
            ZoomPanPoint.Zero,
            boundsForScale = { ZoomPanBounds(0f, 0f, 0f, 0f) },
            overscrollPolicy = policy,
        )
        assertEquals(initial, pagerFrame.snapshot.transform)

        val oneToTwo = controller.pointerFrame(
            listOf(pointer(4, 360f, 600f), pointer(9, 640f, 600f)),
            ZoomPanPoint.Zero,
            boundsForScale = { ZoomPanBounds(-800f, 0f, -1_000f, 0f) },
            overscrollPolicy = policy,
        )
        assertTrue(oneToTwo.rebased)
        assertEquals(initial, oneToTwo.snapshot.transform)
    }

    @Test
    fun `shared controller accepts platform fling deltas and owns cancellation generation`() {
        val policy = AxisOverscrollPolicy(true, true, 24f, 20f, 20f)
        val initial = ZoomPanTransform(
            scale = 2f,
            legalOffsetPx = ZoomPanPoint(-300f, -400f),
            visualOverscrollPx = ZoomPanPoint(12f, -8f),
        )
        val controller = PdfGestureTransformController(initial)
        val flingGeneration = controller.beginPlatformFling(initial)
        val fling = checkNotNull(
            controller.applyPlatformFlingDelta(
                flingGeneration,
                ZoomPanPoint(12f, -8f),
                bounds,
                policy,
            ),
        )
        assertEquals(PdfGesturePhase.FLINGING, fling.snapshot.phase)
        assertTrue(fling.snapshot.transform.legalOffsetPx != initial.legalOffsetPx)
        assertEquals(ZoomPanPoint(12f, -8f), fling.appliedDeltaPx)

        val springGeneration = controller.beginSpring(fling.snapshot.transform)
        assertTrue(springGeneration > flingGeneration)
        val spring = checkNotNull(
            controller.stepSpring(
                springGeneration,
                16_000_000L,
                ZoomPanPhysicsProfile.HorizontalSpring,
                policy,
            ),
        )
        assertEquals(PdfGesturePhase.SPRINGING, spring.snapshot.phase)
        val cancelledGeneration = controller.cancelMotion(spring.snapshot.transform)
        assertTrue(cancelledGeneration > springGeneration)
        assertEquals(PdfGesturePhase.IDLE, controller.snapshot().phase)
        assertEquals(null, controller.stepSpring(
            springGeneration,
            16_000_000L,
            ZoomPanPhysicsProfile.HorizontalSpring,
            policy,
        ))
    }

    @Test
    fun `pinch and two one two transitions preserve the focal content anchor`() {
        val origin = ZoomPanPoint(500f, 20f)
        val initial = ZoomPanTransform(2f, ZoomPanPoint(-180f, -420f))
        val controller = PdfGestureTransformController(initial)
        controller.beginGesture(
            initial,
            listOf(pointer(3, 350f, 700f), pointer(8, 650f, 700f)),
            origin,
            boundsForScale = { bounds },
            overscrollPolicy = AxisOverscrollPolicy(true, true, 24f, 24f, 20f),
        )
        val anchor = ZoomPanPhysics.captureFocalAnchor(initial, ZoomPanPoint(500f, 700f), origin)
        val pinched = controller.pointerFrame(
            listOf(pointer(3, 280f, 720f), pointer(8, 760f, 720f)),
            origin,
            boundsForScale = { bounds },
            overscrollPolicy = AxisOverscrollPolicy(true, true, 24f, 24f, 20f),
        )
        val focalAfter = ZoomPanPhysics.captureFocalAnchor(
            pinched.snapshot.transform,
            ZoomPanPoint(520f, 720f),
            origin,
        )
        assertEquals(anchor.contentX, focalAfter.contentX, 0.001f)
        assertEquals(anchor.contentY, focalAfter.contentY, 0.001f)
        assertTrue("focal drift=${pinched.focalAnchorDriftPx}", pinched.focalAnchorDriftPx <= 0.001f)

        val twoToOne = controller.pointerFrame(
            listOf(pointer(3, 280f, 720f)),
            origin,
            boundsForScale = { bounds },
            overscrollPolicy = AxisOverscrollPolicy(true, true, 24f, 24f, 20f),
        )
        assertTrue(twoToOne.rebased)
        assertEquals(pinched.snapshot.transform, twoToOne.snapshot.transform)

        val oneToTwo = controller.pointerFrame(
            listOf(pointer(3, 280f, 720f), pointer(11, 620f, 720f)),
            origin,
            boundsForScale = { bounds },
            overscrollPolicy = AxisOverscrollPolicy(true, true, 24f, 24f, 20f),
        )
        assertTrue(oneToTwo.rebased)
        assertEquals(twoToOne.snapshot.transform, oneToTwo.snapshot.transform)
    }

    @Test
    fun `real fitted page preserves off-center focal point from the first zoom frame`() {
        val layout = HorizontalZoomPanPhysics.fitPage(
            viewportWidthPx = 1_080f,
            viewportHeightPx = 1_900f,
            pageAspect = 1.414f,
            contentMarginPx = 24f,
        )
        val origin = ZoomPanPoint(layout.viewportWidthPx / 2f, layout.contentTopPx)
        val focal = ZoomPanPoint(120f, 620f)
        val initial = ZoomPanTransform(scale = 1f)
        val anchor = ZoomPanPhysics.captureFocalAnchor(initial, focal, origin)
        listOf(1.005f, 1.02f, 1.08f).forEach { scale ->
            val firstZoomFrame = ZoomPanPhysics.applyScaleFromAnchor(
                transform = initial,
                newScale = scale,
                anchor = anchor,
                focalViewportPx = focal,
                transformOriginViewportPx = origin,
                newBounds = HorizontalZoomPanPhysics.bounds(layout, scale).asZoomPanBounds(),
                scaleRange = ZoomPanPhysicsProfile.ScaleRange,
            )
            val projected = ZoomPanPhysics.captureFocalAnchor(firstZoomFrame, focal, origin)

            assertEquals(anchor.contentX, projected.contentX, 0.001f)
            assertEquals(anchor.contentY, projected.contentY, 0.001f)
        }
    }

    @Test
    fun `zoomed page commit preserves scale vertical anchor and enters from direction edge`() {
        val outgoingLayout = HorizontalZoomPanPhysics.fitPage(1_000f, 1_800f, 1.4f, 8f)
        val incomingLayout = HorizontalZoomPanPhysics.fitPage(1_000f, 1_800f, 0.9f, 8f)
        val outgoing = HorizontalPanState(legalOffsetX = -120f, legalOffsetY = -640f)
        val session = HorizontalZoomPanPhysics.viewportSession(4, outgoingLayout, 2.5f, outgoing, 10L)
        val next = HorizontalZoomPanPhysics.restoreViewportSession(
            session.copy(pageIndex = 5),
            incomingLayout,
            HorizontalPageTransitionDirection.NEXT,
        )
        val nextSession = HorizontalZoomPanPhysics.viewportSession(5, incomingLayout, next.scale, next.pan, 11L)

        assertEquals(2.5f, next.scale, 0f)
        assertEquals(session.pageAnchorYFraction, nextSession.pageAnchorYFraction, 0.001f)
        assertEquals(HorizontalZoomPanPhysics.bounds(incomingLayout, 2.5f).maxX, next.pan.legalOffsetX, 0f)
        assertFalse(next.scale == 1f)
    }

    @Test
    fun `continuous horizontal continuous mode round trip keeps page scale and anchor`() {
        val geometry = ContinuousDocumentGeometry.create(8, 1_000f, emptyMap(), 10f)
        // Keep the viewport anchor away from a page edge so both layouts can represent the same
        // page-local point without a legal-bound clamp changing the requested viewport anchor.
        val continuous = ContinuousDocumentState(scale = 2.5f, scrollX = 620f, scrollY = 7_987.5f)
        val source = geometry.viewportSessionAtAnchor(continuous, 1_000f, 1_800f, 20L)
        val layout = HorizontalZoomPanPhysics.fitPage(1_000f, 1_800f, 1.414f, 8f)
        val horizontalTarget = source.toCanonicalAnchor().toViewportSession(
            PdfReadingMode.HORIZONTAL_PAGED,
            21L,
        )
        val horizontal = HorizontalZoomPanPhysics.restoreViewportSession(
            horizontalTarget,
            layout,
        )
        val horizontalSession = HorizontalZoomPanPhysics.viewportSession(
            source.pageIndex,
            layout,
            horizontal.scale,
            horizontal.pan,
            22L,
        )
        val continuousTarget = horizontalSession.toCanonicalAnchor().toViewportSession(
            PdfReadingMode.VERTICAL_CONTINUOUS,
            23L,
        )
        val restored = geometry.restoreViewportSession(
            continuousTarget,
            ContinuousDocumentState(),
            1_000f,
            1_800f,
        )
        val result = geometry.viewportSessionAtAnchor(restored, 1_000f, 1_800f, 24L)

        assertEquals(source.pageIndex, result.pageIndex)
        assertEquals(source.scale, result.scale, 0f)
        assertTrue(abs(source.pageAnchorYFraction - result.pageAnchorYFraction) <= 0.001f)
        val anchorDriftPx = abs(source.pageAnchorYFraction - result.pageAnchorYFraction) *
            geometry.pageHeight(source.pageIndex) * source.scale
        assertTrue("mode anchor drift=$anchorDriftPx px", anchorDriftPx <= 4f)
    }

    @Test
    fun `portrait horizontal fit shares continuous physical page width while retaining top gap`() {
        val viewportWidth = 1_000f
        val topGap = 8f
        val layout = HorizontalZoomPanPhysics.fitPage(
            viewportWidthPx = viewportWidth,
            viewportHeightPx = 1_800f,
            pageAspect = 1.414f,
            contentMarginPx = topGap,
            horizontalMarginPx = 0f,
        )

        assertEquals(viewportWidth, layout.fittedWidthPx, 0f)
        assertEquals(topGap, layout.contentTopPx, 0f)
        assertEquals(viewportWidth * 2.5f, layout.fittedWidthPx * 2.5f, 0f)
    }

    @Test
    fun `continuous gap switch keeps a real anchor on the dominant visible page`() {
        val geometry = ContinuousDocumentGeometry.create(
            pageCount = 3,
            basePageWidth = 1_000f,
            aspects = mapOf(0 to 1f, 1 to 1f, 2 to 1f),
            pageGap = 100f,
        )
        // Viewport center is exactly in the 100px gap between pages 0 and 1. Both pages have the
        // same visible height, so the stable tie-break keeps the preceding page rather than
        // coercing a negative page-1 fraction to its top edge.
        val session = geometry.viewportSessionAtAnchor(
            state = ContinuousDocumentState(scale = 1f, scrollY = 550f),
            viewportWidth = 1_000f,
            viewportHeight = 1_000f,
            generation = 30L,
        )

        assertEquals(0, session.pageIndex)
        assertEquals(1f, session.pageAnchorYFraction, 0.001f)
        assertEquals(0.45f, session.viewportAnchorYFraction, 0.001f)
    }

    @Test
    fun `horizontal short page session anchors to visible page instead of blank viewport center`() {
        val layout = HorizontalZoomPanPhysics.fitPage(
            viewportWidthPx = 1_000f,
            viewportHeightPx = 1_800f,
            pageAspect = 0.5f,
            contentMarginPx = 8f,
        )
        val session = HorizontalZoomPanPhysics.viewportSession(
            pageIndex = 2,
            layout = layout,
            scale = 1f,
            pan = HorizontalPanState(),
            generation = 40L,
        )

        assertEquals(1f, session.pageAnchorYFraction, 0.001f)
        assertEquals(
            (layout.contentTopPx + layout.fittedHeightPx) / layout.viewportHeightPx,
            session.viewportAnchorYFraction,
            0.001f,
        )
    }

    @Test
    fun `tile bucket changes never mutate display transform`() {
        val transform = ZoomPanTransform(2.49f, ZoomPanPoint(-220f, -480f))
        val oldBucket = PdfZoomBuckets.index(2.49f, 0)
        val newBucket = PdfZoomBuckets.index(2.51f, oldBucket)
        assertTrue(newBucket >= oldBucket)
        assertEquals(transform, transform.copy())
    }

    @Test
    fun `horizontal sessions are valid persisted schema version without Room changes`() {
        val session = PdfViewportSessionState(
            readingMode = PdfReadingMode.HORIZONTAL_PAGED,
            pageIndex = 2,
            pageAnchorXFraction = 0.3f,
            pageAnchorYFraction = 0.7f,
            scale = 3f,
            generation = 3L,
        )
        assertEquals(session, session.normalized(5, 0))
    }

    private fun pointer(id: Long, x: Float, y: Float) =
        ZoomPanPointer(id, ZoomPanPoint(x, y))
}
