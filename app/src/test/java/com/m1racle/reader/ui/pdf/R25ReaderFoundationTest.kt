package com.m1racle.reader.ui.pdf

import java.io.File
import com.m1racle.reader.ui.material.GlassMaterialEngine
import com.m1racle.reader.ui.material.GlassMaterialRole
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
    fun `reader roles form one translucent material hierarchy`() {
        val control = GlassMaterialEngine.resolve(GlassMaterialRole.Control, dark = false)
        val menu = GlassMaterialEngine.resolve(GlassMaterialRole.Menu, dark = false)
        val child = GlassMaterialEngine.resolve(GlassMaterialRole.SettingsCard, dark = false)
        assertEquals(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.32f).alpha, control.surfaceColor.alpha, 0f)
        assertEquals(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.60f).alpha, menu.surfaceColor.alpha, 0f)
        assertEquals(menu.surfaceColor.alpha, child.surfaceColor.alpha, 0f)
        assertEquals(control.blur, menu.blur)
        assertEquals(menu.shadow, child.shadow)
        assertEquals(0.75f, menu.highlightWidth.value, 0.001f)
    }

    @Test
    fun `Main child circles and capsule use unified engine entry`() {
        val host = hostSource()
        assertTrue(host.contains("glassMaterial("))
        assertTrue(host.contains("GlassMaterialRole.Menu"))
        assertTrue(host.contains("GlassMaterialRole.Control"))
        val pop = popoverSource()
        assertTrue(pop.contains("glassMaterial("))
        assertTrue(pop.contains("GlassMaterialRole.SettingsCard"))
        val screen = screenSource()
        assertTrue(screen.contains("GlassMaterialRole.Control"))
        assertTrue(screen.contains("GlassMaterialRole.Capsule"))
        assertFalse(host.contains("containerAlpha = 1f"))
        assertFalse(pop.contains("containerAlpha = 1f"))
    }

    @Test
    fun `nested rows report identity rather than a fixed ordinal`() {
        val host = hostSource()
        assertTrue(host.contains("onRowBoundsRoot(child, coords.boundsInRoot())"))
        assertTrue(host.contains("canonicalMainBounds[target] ?: rowRectsRoot[target]"))
        assertTrue(host.contains("state == child || state.preparingTarget() == child"))
        assertFalse(host.contains("childRowIndex"))
        assertFalse(host.contains("val rowIndex = 2 + i"))
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
        assertTrue(pop.contains("M1racleMotion.slow"))
        assertTrue(pop.contains("M1racleMotion.normalIn"))
        assertTrue(pop.contains("AnchoredRevealOpenMs = com.m1racle.reader.ui.theme.M1racleMotion.SlowMs"))
        assertTrue(pop.contains("AnchoredRevealCloseMs = com.m1racle.reader.ui.theme.M1racleMotion.NormalMs"))
    }

    @Test
    fun `popover uses full source-to-target LTRB morph not bottom-only`() {
        val pop = popoverSource()
        val geo = listOf(
            File("app/src/main/java/com/m1racle/reader/pdf/AnchoredNestedGeometry.kt"),
            File("../app/src/main/java/com/m1racle/reader/pdf/AnchoredNestedGeometry.kt"),
            File("src/main/java/com/m1racle/reader/pdf/AnchoredNestedGeometry.kt"),
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
    private fun source(name: String): String = listOf(
        File("app/src/main/java/com/m1racle/reader/ui/pdf/$name"),
        File("src/main/java/com/m1racle/reader/ui/pdf/$name"),
    ).first { it.isFile }.readText()
}
