package com.markleaf.app.pdf

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class R7H14UnifiedGestureArchitectureTest {
    @Test
    fun `both production adapters use one await gesture lifecycle`() {
        val lifecycle = uiSource("PdfGesturePointerLifecycle.kt")
        val continuous = uiSource("ContinuousDocumentViewport.kt")
        val screen = uiSource("PdfViewerScreen.kt")
        assertTrue(lifecycle.contains("awaitEachGesture"))
        assertTrue(lifecycle.contains("awaitFirstDown"))
        assertTrue(lifecycle.contains("controller.endGesture()"))
        assertTrue(continuous.contains("awaitPdfGestureLifecycle("))
        assertTrue(screen.contains("awaitPdfGestureLifecycle(sharedGestureController)"))
    }

    @Test
    fun `pointer math is shared and release motion uses one platform spline runner`() {
        val controller = pdfSource("PdfGestureTransformController.kt")
        val continuous = uiSource("ContinuousDocumentViewport.kt")
        val horizontal = uiSource("PdfViewerScreen.kt")
        val platform = uiSource("PlatformPdfFling.kt")
        assertTrue(controller.contains("ZoomPanPointerEpochReducer.frame("))
        assertTrue(controller.contains("fun applyPlatformFlingDelta("))
        assertTrue(controller.contains("ZoomPanPhysics.springStep("))
        assertTrue(platform.contains("OverScroller("))
        assertTrue(continuous.contains("platformFlingRunner.run("))
        assertTrue(horizontal.contains("platformFlingRunner.run("))
        assertFalse(continuous.contains("ZoomPanPointerEpochReducer.frame("))
        assertFalse(continuous.contains("ZoomPanPhysics.flingStep("))
        assertFalse(horizontal.contains("ZoomPanPointerEpochReducer.frame("))
        assertFalse(horizontal.contains("ZoomPanPhysics.flingStep("))
        assertFalse(horizontal.contains("var pointerEpoch"))
    }

    @Test
    fun `mode and page adapters preserve session transform instead of resetting`() {
        val horizontal = pdfSource("HorizontalZoomPanPhysics.kt")
        val viewModel = uiSource("PdfViewerViewModel.kt")
        assertTrue(horizontal.contains("fun restoreViewportSession("))
        assertFalse(horizontal.contains("fun pageCommitTransform()"))
        assertTrue(viewModel.contains("stableSession.toCanonicalAnchor().toViewportSession("))
        assertTrue(pdfSource("PdfViewportSessionState.kt").contains("data class CanonicalPdfAnchor("))
        assertFalse(viewModel.contains("always enters at 1×"))
    }

    @Test
    fun `one three page strip owns scale one and zoomed transitions`() {
        val screen = uiSource("PdfViewerScreen.kt")
        assertTrue(screen.contains("ThreePageStripPhysics.drag("))
        assertTrue(screen.contains("ThreePageStripPhysics.settleTargetPx("))
        assertTrue(screen.contains("pageStrip = pageStrip.committed("))
        assertFalse(screen.contains("horizontalNestedScrollDispatcher"))
        assertFalse(screen.contains("dispatchPostScroll("))
        assertFalse(screen.contains("HorizontalPager("))
        assertFalse(screen.contains("pagerState.animateScrollToPage("))
    }

    @Test
    fun `mode switch reads live transform preview without persisting every pointer frame`() {
        val continuous = uiSource("ContinuousDocumentViewport.kt")
        val screen = uiSource("PdfViewerScreen.kt")
        val viewModel = uiSource("PdfViewerViewModel.kt")
        val session = pdfSource("PdfViewportSessionState.kt")
        assertTrue(continuous.contains("currentOnViewportSessionPreview("))
        assertTrue(screen.contains("currentOnViewportSessionPreview(horizontalSession(page))"))
        assertTrue(viewModel.contains("viewportSessionTracker.latestForMode(ready.readingMode)"))
        assertTrue(session.contains("fun preview(value: PdfViewportSessionState)"))
        assertTrue(viewModel.contains("never persisted by itself"))
    }

    private fun uiSource(name: String) = File("src/main/java/com/markleaf/app/ui/pdf/$name").readText()
    private fun pdfSource(name: String) = File("src/main/java/com/markleaf/app/pdf/$name").readText()
}
