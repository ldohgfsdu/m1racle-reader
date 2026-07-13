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
        assertTrue(source.contains("ContinuousPointerEpochReducer.frame("))
        assertFalse(source.contains("calculateZoom()"))
        assertTrue(source.contains("currentGeometry.panBy("))
        assertTrue(source.contains("onContinuousViewportChanged").not())
        assertTrue(source.contains("onViewportChanged(ContinuousViewportSnapshot"))
    }

    private fun screenSource(): String = source("PdfViewerScreen.kt")
    private fun viewportSource(): String = source("ContinuousDocumentViewport.kt")

    private fun source(name: String): String = listOf(
        File("app/src/main/java/com/markleaf/app/ui/pdf/$name"),
        File("src/main/java/com/markleaf/app/ui/pdf/$name"),
        File("../src/main/java/com/markleaf/app/ui/pdf/$name"),
    ).first { it.isFile }.readText()
}
