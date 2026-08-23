package com.markleaf.app.pdf

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Classification: STRUCTURAL_GUARD_ONLY. Source shape only; never runtime evidence. */
class R7ContinuousViewportTest {
    @Test fun `vertical production entry uses V2 not zoom lock`() {
        val source = screenSource()
        val vertical = source.substringAfter("PdfReadingMode.VERTICAL_CONTINUOUS ->")
            .substringBefore("PdfReadingMode.HORIZONTAL_PAGED ->")
        assertTrue(vertical.contains("ContinuousDocumentViewport("))
        assertFalse(vertical.contains("PdfVerticalZoomLockArea("))
    }

    @Test fun `V2 viewport has no page lock or overlay production path`() {
        val source = viewportSource()
        assertFalse(source.contains("lockedPage"))
        assertFalse(source.contains("ZoomPageOverlay"))
        assertFalse(source.contains("scrollToItem"))
        assertFalse(source.contains("dispatchRawDelta"))
    }

    @Test fun `V2 virtualizes with SubcomposeLayout and stable document key`() {
        val source = viewportSource()
        assertTrue(source.contains("SubcomposeLayout("))
        assertTrue(source.contains("geometry.composedPages"))
        assertTrue(source.contains("pointerInput(documentId)"))
        assertFalse(source.contains("LazyColumn("))
    }

    @Test fun `V2 gesture writes one state and retains R3 viewport callback`() {
        val source = viewportSource()
        assertTrue(source.contains("ContinuousDocumentState"))
        assertTrue(source.contains("PdfGestureTransformController("))
        assertTrue(source.contains("sharedGestureController.pointerFrame("))
        assertFalse(source.contains("ContinuousPointerEpochReducer.frame("))
        assertFalse(source.contains("calculateZoom()"))
        assertTrue(source.contains("onContinuousViewportChanged").not())
        assertTrue(source.contains("onViewportChanged("))
        assertTrue(source.contains("ContinuousViewportSnapshot("))
        assertTrue(source.contains("anchorDeltaY"))
        // Epoch reset: zoom scale / viewport size / page identity changes
        // re-baseline WITHOUT producing a motion sample (no chrome feedback loop).
        assertTrue(source.contains("lastAnchorScale"))
        assertTrue(source.contains("scaleChanged"))
        assertTrue(source.contains("viewportChanged"))
    }

    /** R25 closure (ported from R62): cold continuous pages render a solid
     *  white background, never a CircularProgressIndicator. */
    @Test fun `V2 zoom display keeps last-good tiles across buckets`() {
        val source = viewportSource()
        assertTrue(source.contains("PdfTileDisplay.forPage"))
        assertTrue(source.contains("PdfTileDisplay.bucketsToRequest"))
        assertFalse(source.contains("zoomBucket in setOf"))
    }

    @Test fun `V2 remaps scroll when page aspects rebuild geometry`() {
        val source = viewportSource()
        assertTrue(source.contains("remapStatePreservingViewportTop"))
        assertTrue(source.contains("alignedDocumentState"))
        assertTrue(source.contains("if (documentState.gestureActive) return@SideEffect"))
    }

    @Test fun `V2 contact pan is free 2D and only fling drops a near-axis leak`() {
        val source = viewportSource()
        assertTrue(source.contains("dominantAxisFlingVelocity"))
        assertFalse(source.contains("axisLockedPosition"))
        assertFalse(source.contains("resolveAxis"))
    }

    @Test fun `V2 cold pages use white background not spinner`() {
        val source = viewportSource()
        assertTrue(source.contains("Color.White"))
        assertFalse(source.contains("CircularProgressIndicator"))
    }

    private fun screenSource(): String = source("PdfViewerScreen.kt")
    private fun viewportSource(): String = source("ContinuousDocumentViewport.kt")

    private fun source(name: String): String = listOf(
        File("app/src/main/java/com/markleaf/app/ui/pdf/$name"),
        File("src/main/java/com/markleaf/app/ui/pdf/$name"),
        File("../src/main/java/com/markleaf/app/ui/pdf/$name"),
    ).first { it.isFile }.readText()
}
