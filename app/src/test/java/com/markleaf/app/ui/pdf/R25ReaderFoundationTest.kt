package com.markleaf.app.ui.pdf

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structural gates: shared glass token + four advanced source rows + no
 * BottomSheet/Popup path for Reader More nested children.
 */
class R25ReaderFoundationTest {

    @Test
    fun `shared glass base alpha is in 0_88-0_94 light coverage band`() {
        val glass = glassSource()
        // Light glass must be dense enough that PDF faces are not readable through text.
        assertTrue(
            glass.contains("LightBaseAlpha = 0.91f") ||
                glass.contains("LightBaseAlpha = 0.90f") ||
                glass.contains("LightBaseAlpha = 0.92f") ||
                glass.contains("ReaderGlassBaseAlpha = 0.91f") ||
                glass.contains("ReaderGlassBaseAlpha = 0.90f") ||
                glass.contains("ReaderGlassBaseAlpha = 0.92f"),
        )
        assertTrue(glass.contains("enum class ReaderGlassElevation"))
        assertTrue(glass.contains("Main"))
        assertTrue(glass.contains("Child"))
        assertTrue(glass.contains("Chrome"))
        // Hairline border only — no high-alpha hard Black outline.
        assertTrue(
            glass.contains("LightBorderAlpha = 0.045f") ||
                glass.contains("LightBorderAlpha = 0.04f") ||
                glass.contains("LightBorderAlpha = 0.05f") ||
                glass.contains("LightBorderAlpha = 0.06f"),
        )
        assertTrue(glass.contains("ReaderChromeTokens"))
        assertTrue(glass.contains("ParentElevationDp = 10f") || glass.contains("ParentElevationDp = 9f"))
        assertTrue(glass.contains("ChildElevationDp = 14f") || glass.contains("ChildElevationDp = 12f") ||
            glass.contains("ChildElevationDp = 16f"))
    }

    @Test
    fun `Main child and circles use unified readerChromeSurface entry`() {
        val host = hostSource()
        // Shells go through readerChromeSurface (material renderer), not ad-hoc glass.
        assertTrue(host.contains("readerChromeSurface("))
        assertTrue(host.contains("ReaderGlassElevation.Main") || host.contains("ReaderChromeSurfaceRole.Parent"))
        assertTrue(host.contains("ReaderGlassElevation.Chrome") || host.contains("ReaderChromeSurfaceRole.Chrome"))
        val pop = popoverSource()
        assertTrue(pop.contains("readerChromeSurface("))
        assertTrue(pop.contains("ReaderGlassElevation.Child") || pop.contains("ReaderChromeSurfaceRole.Child"))
        val screen = screenSource()
        assertTrue(screen.contains("readerChromeSurface("))
        // No opaque white plate override.
        assertFalse(host.contains("containerAlpha = 1f"))
        assertFalse(pop.contains("containerAlpha = 1f"))
    }

    @Test
    fun `four advanced states map to distinct source rows`() {
        assertEquals(2, childRowIndex(ReaderMoreState.CHILD_PAGE_MAPPING))
        assertEquals(3, childRowIndex(ReaderMoreState.CHILD_TOC))
        assertEquals(4, childRowIndex(ReaderMoreState.CHILD_THUMBNAILS))
        assertEquals(5, childRowIndex(ReaderMoreState.CHILD_EXPORT))
        val rows = listOf(2, 3, 4, 5)
        assertEquals(4, rows.toSet().size)
    }

    @Test
    fun `host freezes boundsInRoot source and parent for reverse morph`() {
        val host = hostSource()
        assertTrue(host.contains("boundsInRoot()"))
        assertTrue(host.contains("frozenSourceRoot"))
        assertTrue(host.contains("frozenParentRoot"))
        assertTrue(host.contains("frozenChildState"))
        assertTrue(host.contains("AnchoredNestedPopover"))
        assertFalse(host.contains("ParentContentContextAlpha"))
        assertFalse(host.contains("parentContentAlpha"))
    }

    @Test
    fun `anchored nested hosts all four real bodies`() {
        val pop = popoverSource()
        assertTrue(pop.contains("TextbookPageAdvancedContent"))
        assertTrue(pop.contains("OutlineAdvancedContent"))
        assertTrue(pop.contains("ThumbnailsAdvancedContent"))
        assertTrue(pop.contains("ExportAdvancedContent"))
        assertTrue(pop.contains("CHILD_PAGE_MAPPING"))
        assertTrue(pop.contains("CHILD_TOC"))
        assertTrue(pop.contains("CHILD_THUMBNAILS"))
        assertTrue(pop.contains("CHILD_EXPORT"))
        // Phase-1 fake demo removed.
        assertFalse(pop.contains("第一章 绪论"))
        assertFalse(pop.contains("TocDemoFakeChapters"))
    }

    @Test
    fun `open and close morph durations in target bands`() {
        val pop = popoverSource()
        assertTrue(pop.contains("AnchoredRevealOpenMs = 200") ||
            pop.contains("AnchoredRevealOpenMs = 190") ||
            pop.contains("AnchoredRevealOpenMs = 210"))
        assertTrue(pop.contains("AnchoredRevealCloseMs = 180") ||
            pop.contains("AnchoredRevealCloseMs = 170") ||
            pop.contains("AnchoredRevealCloseMs = 190"))
    }

    @Test
    fun `popover uses full source-to-target LTRB morph not bottom-only`() {
        val pop = popoverSource()
        val geo = listOf(
            File("app/src/main/java/com/markleaf/app/pdf/AnchoredNestedGeometry.kt"),
            File("../app/src/main/java/com/markleaf/app/pdf/AnchoredNestedGeometry.kt"),
            File("src/main/java/com/markleaf/app/pdf/AnchoredNestedGeometry.kt"),
        ).first { it.isFile }.readText()
        // Shipped morph interpolates all four edges.
        assertTrue(geo.contains("sourceLeft + (targetLeft - sourceLeft) * t"))
        assertTrue(geo.contains("sourceTop + (targetTop - sourceTop) * t"))
        assertTrue(geo.contains("sourceRight + (targetRight - sourceRight) * t"))
        assertTrue(geo.contains("sourceBottom + (targetBottom - sourceBottom) * t"))
        // Popover passes frozen source bounds into morph (not target-only).
        assertTrue(pop.contains("sourceLeft = sourceRectRoot.left"))
        assertTrue(pop.contains("sourceRight = sourceRectRoot.right"))
        assertTrue(pop.contains("sourceBottom = sourceRectRoot.bottom"))
        // Bottom-only legacy signature gone.
        assertFalse(geo.contains("Final left/right/top from frame 0; only [bottom]"))
    }

    @Test
    fun `screen opens all four advanced entries - no deferred no-ops`() {
        val screen = screenSource()
        assertTrue(screen.contains("CHILD_PAGE_MAPPING"))
        assertTrue(screen.contains("CHILD_TOC"))
        assertTrue(screen.contains("CHILD_THUMBNAILS"))
        assertTrue(screen.contains("CHILD_EXPORT"))
        assertFalse(screen.contains("phase-1 deferred"))
        assertTrue(screen.contains("nestedData = nestedData"))
    }

    @Test
    fun `no Popup Dialog BottomSheet for reader more nested path`() {
        val host = hostSource()
        val pop = popoverSource()
        listOf(host, pop).forEach { src ->
            assertFalse(src.contains("androidx.compose.ui.window.Popup"))
            assertFalse(src.contains("ModalBottomSheet"))
            assertFalse(src.contains("DropdownMenu("))
        }
    }

    private fun hostSource(): String = source("ReaderMoreHost.kt")
    private fun popoverSource(): String = source("AnchoredNestedPopover.kt")
    private fun screenSource(): String = source("PdfViewerScreen.kt")
    private fun glassSource(): String = source("ReaderChromeGlass.kt")

    private fun source(name: String): String = listOf(
        File("app/src/main/java/com/markleaf/app/ui/pdf/$name"),
        File("src/main/java/com/markleaf/app/ui/pdf/$name"),
    ).first { it.isFile }.readText()
}
