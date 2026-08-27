package com.m1racle.reader.pdf

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class R7H15PlatformScrollAndPageStripTest {
    @Test
    fun pointerTeardownDoesNotCancelPostReleasePlatformFling() {
        val controller = PdfGestureTransformController()
        val bounds = ZoomPanBounds(-500f, 500f, -500f, 500f)
        controller.beginGesture(
            currentTransform = ZoomPanTransform(scale = 2f),
            initialPointers = listOf(ZoomPanPointer(1L, ZoomPanPoint(100f, 100f))),
            boundsForScale = { bounds },
            overscrollPolicy = AxisOverscrollPolicy.Disabled,
        )

        val generation = controller.beginPlatformFling()
        controller.endGesture()
        val frame = controller.applyPlatformFlingDelta(
            generation = generation,
            deltaPx = ZoomPanPoint(24f, -12f),
            bounds = bounds,
            overscrollPolicy = AxisOverscrollPolicy.Disabled,
        )

        assertNotNull(frame)
        assertEquals(PdfGesturePhase.FLINGING, frame!!.snapshot.phase)
        assertEquals(24f, frame.appliedDeltaPx.x, 0.001f)
        assertEquals(-12f, frame.appliedDeltaPx.y, 0.001f)
    }

    @Test
    fun `REAL DIRECT CONTACT DRAG is viewport pixel one to one`() {
        val controller = PdfGestureTransformController(
            ZoomPanTransform(scale = 3f, legalOffsetPx = ZoomPanPoint(-200f, -300f)),
        )
        val bounds = ZoomPanBounds(-800f, 800f, -1_200f, 0f)
        controller.beginGesture(
            currentTransform = controller.snapshot().transform,
            initialPointers = listOf(ZoomPanPointer(7L, ZoomPanPoint(100f, 200f))),
            boundsForScale = { bounds },
            overscrollPolicy = AxisOverscrollPolicy.Disabled,
        )
        val frame = controller.pointerFrame(
            pointers = listOf(ZoomPanPointer(7L, ZoomPanPoint(137f, 171f))),
            transformOriginViewportPx = ZoomPanPoint.Zero,
            boundsForScale = { bounds },
            overscrollPolicy = AxisOverscrollPolicy.Disabled,
        )
        assertEquals(ZoomPanPoint(37f, -29f), frame.rawCentroidDeltaPx)
        assertEquals(ZoomPanPoint(37f, -29f), frame.legalConsumedPx)
        assertEquals(ZoomPanPoint.Zero, frame.residualPx)
    }

    @Test
    fun `edge preview consumes first residual then follows raw finger one to one`() {
        val start = ThreePageStripPhysics.idle(9L)
        val first = drag(start, 0f, rawDeltaX = -18f, rawDeltaY = 1f, residualX = -7f)
        assertEquals(ZoomedPageTransitionPhase.EDGE_PREVIEW, first.transition.phase)
        assertEquals(-7f, first.offsetPx, 0f)
        val second = drag(first.transition, first.offsetPx, -23f, 2f, -23f)
        assertEquals(-30f, second.offsetPx, 0f)
        assertEquals(2, second.transition.targetPage)
    }

    @Test
    fun `light edge pull cancels to exact current page zero`() {
        val preview = preview(offsetDelta = -120f)
        val released = ThreePageStripPhysics.release(
            preview.transition, preview.offsetPx, 1080f,
            releaseVelocityXPxPerSecond = -20f, minimumFlingVelocityPxPerSecond = 50f,
        )
        assertEquals(ZoomedPageTransitionPhase.CANCELLING, released.phase)
        assertEquals(0f, ThreePageStripPhysics.settleTargetPx(released, 1080f), 0f)
    }

    @Test
    fun `distance commit settles to exactly one viewport never between pages`() {
        val preview = preview(offsetDelta = -600f)
        val released = ThreePageStripPhysics.release(
            preview.transition, preview.offsetPx, 1080f,
            releaseVelocityXPxPerSecond = 0f, minimumFlingVelocityPxPerSecond = 50f,
        )
        assertEquals(ZoomedPageTransitionPhase.COMMITTING, released.phase)
        assertEquals(-1080f, ThreePageStripPhysics.settleTargetPx(released, 1080f), 0f)
    }

    @Test
    fun `platform minimum fling velocity can commit a deliberate shorter outward swipe`() {
        val preview = preview(offsetDelta = -160f)
        val released = ThreePageStripPhysics.release(
            preview.transition, preview.offsetPx, 1080f,
            releaseVelocityXPxPerSecond = -900f, minimumFlingVelocityPxPerSecond = 50f,
        )
        assertEquals(ZoomedPageTransitionPhase.COMMITTING, released.phase)
        assertEquals(2, released.targetPage)
    }

    @Test
    fun `reverse drag returns preview to exact idle zero`() {
        val preview = preview(offsetDelta = -90f)
        val reversed = drag(preview.transition, preview.offsetPx, 90f, 0f, 0f)
        assertEquals(ZoomedPageTransitionPhase.IDLE, reversed.transition.phase)
        assertEquals(0f, reversed.offsetPx, 0f)
    }

    @Test
    fun `canonical mode anchor round trips actual focal pixels without drift`() {
        val source = PdfViewportSessionState(
            readingMode = PdfReadingMode.VERTICAL_CONTINUOUS,
            pageIndex = 4,
            pageAnchorXFraction = 0.37f,
            pageAnchorYFraction = 0.62f,
            viewportAnchorXFraction = 0.4f,
            viewportAnchorYFraction = 0.31f,
            sourceViewportWidthPx = 1080f,
            sourceViewportHeightPx = 2200f,
            scale = 2.75f,
            generation = 11L,
        )
        val canonical = source.toCanonicalAnchor()
        val target = canonical.toViewportSession(PdfReadingMode.HORIZONTAL_PAGED, 12L)
        assertEquals(432f, canonical.viewportFocalX, 0.001f)
        assertEquals(682f, canonical.viewportFocalY, 0.001f)
        assertEquals(source.pageAnchorXFraction, target.pageAnchorXFraction, 0f)
        assertEquals(source.pageAnchorYFraction, target.pageAnchorYFraction, 0f)
        assertEquals(source.viewportAnchorXFraction, target.viewportAnchorXFraction, 0f)
        assertEquals(source.viewportAnchorYFraction, target.viewportAnchorYFraction, 0f)
        assertEquals(source.scale, target.scale, 0f)
    }

    @Test
    fun `telemetry ring reports direct gain and input latency without logcat per frame`() {
        val ring = PdfGestureFrameTelemetryRing(capacity = 2)
        ring.record(PdfGestureFrameSample(100L, 103L, ZoomPanPoint(12f, -8f), ZoomPanPoint(12f, -8f), ZoomPanPoint.Zero))
        ring.record(PdfGestureFrameSample(110L, 116L, ZoomPanPoint(-6f, 4f), ZoomPanPoint(-6f, 4f), ZoomPanPoint.Zero, -40f))
        val summary = ring.summary()
        assertEquals(2, summary.frameCount)
        assertEquals(3L, summary.inputLatencyP50Ms)
        assertEquals(6L, summary.inputLatencyP95Ms)
        assertEquals(6L, summary.maxInputLatencyMs)
        assertEquals(1f, summary.legalDragGainX, 0.0001f)
        assertEquals(1f, summary.legalDragGainY, 0.0001f)
        assertEquals(40f, summary.maxPageTransitionOffsetPx, 0f)
    }

    @Test
    fun `production has platform spline single owner and no exponential fling or live pager handoff`() {
        val screen = uiSource("PdfViewerScreen.kt")
        val continuous = uiSource("ContinuousDocumentViewport.kt")
        val platform = uiSource("PlatformPdfFling.kt")
        val physics = pdfSource("ZoomPanPhysics.kt")
        assertTrue(platform.contains("OverScroller("))
        assertTrue(platform.contains("scalarSplineSpeed"))
        assertTrue(screen.contains("platformFlingRunner.run("))
        assertTrue(continuous.contains("platformFlingRunner.run("))
        assertTrue(screen.contains("ThreePageStripPhysics.drag("))
        assertTrue(screen.contains("pageStrip.slots().forEach"))
        assertFalse(screen.contains("HorizontalPager("))
        assertFalse(screen.contains("horizontalNestedScrollDispatcher"))
        assertFalse(screen.contains("dispatchPostScroll("))
        assertFalse(screen.contains("\"type\":\"gestureFrame\""))
        assertFalse(physics.contains("FLING_DECAY_PER_SECOND"))
        assertFalse(physics.contains("fun flingStep("))
    }

    private fun preview(offsetDelta: Float): PageStripDragResult =
        drag(ThreePageStripPhysics.idle(1L), 0f, offsetDelta, 0f, offsetDelta)

    private fun drag(
        transition: PageStripTransition,
        offsetPx: Float,
        rawDeltaX: Float,
        rawDeltaY: Float,
        residualX: Float,
    ): PageStripDragResult = ThreePageStripPhysics.drag(
        transition = transition,
        displayedOffsetPx = offsetPx,
        rawDeltaX = rawDeltaX,
        rawDeltaY = rawDeltaY,
        edgeResidualX = residualX,
        currentPage = 1,
        pageCount = 3,
        viewportWidthPx = 1080f,
        previousBaseReady = true,
        nextBaseReady = true,
    )

    private fun uiSource(name: String) = File("src/main/java/com/m1racle/reader/ui/pdf/$name").readText()
    private fun pdfSource(name: String) = File("src/main/java/com/m1racle/reader/pdf/$name").readText()
}
