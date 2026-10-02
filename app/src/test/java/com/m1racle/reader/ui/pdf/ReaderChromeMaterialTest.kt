package com.m1racle.reader.ui.pdf

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.m1racle.reader.testing.ProductionSource
import com.m1racle.reader.ui.material.GlassMaterialEngine
import com.m1racle.reader.ui.material.GlassMaterialRole
import com.m1racle.reader.ui.material.supportsGlassLens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Unified material, geometry and glass-on-glass contracts for Reader chrome. */
class ReaderChromeMaterialTest {

    @Test
    fun `nested cards share parent menu optics and preserve layered sampling`() {
        for (dark in listOf(false, true)) {
            val menu = GlassMaterialEngine.resolve(GlassMaterialRole.Menu, dark)
            val nested = GlassMaterialEngine.resolve(GlassMaterialRole.SettingsCard, dark)
            assertEquals(menu.surfaceColor, nested.surfaceColor)
            assertEquals(menu.blur, nested.blur)
            assertTrue(nested.lensEnabled)
            assertEquals(menu.lensHeight, nested.lensHeight)
            assertEquals(menu.lensAmount, nested.lensAmount)
            assertEquals(menu.highlightWidth, nested.highlightWidth)
            assertEquals(menu.highlightColor, nested.highlightColor)
            assertEquals(menu.shadowColor, nested.shadowColor)
            assertEquals("RootAndMenu", nested.backdropLayer.name)
        }
    }

    @Test
    fun `parent geometry stays compact and independent from material`() {
        assertEquals(220f, MoreMenuWidth.value, 0.01f)
        assertTrue(MoreMenuWidth.value in 212f..224f)
        assertEquals(52f, MenuRowHeight.value, 0.01f)
    }

    @Test
    fun `semantic roles preserve regular frost hierarchy`() {
        val control = GlassMaterialEngine.resolve(GlassMaterialRole.Control, dark = false)
        val capsule = GlassMaterialEngine.resolve(GlassMaterialRole.Capsule, dark = false)
        val menu = GlassMaterialEngine.resolve(GlassMaterialRole.ExpandedMenu, dark = false)
        val settings = GlassMaterialEngine.resolve(GlassMaterialRole.SettingsCard, dark = false)
        val dialog = GlassMaterialEngine.resolve(GlassMaterialRole.Dialog, dark = false)

        assertEquals(0.60f, control.surfaceColor.alpha, 0.01f)
        assertEquals(0.60f, capsule.surfaceColor.alpha, 0.01f)
        assertEquals(0.90f, menu.surfaceColor.alpha, 0.01f)
        assertEquals(0.60f, settings.surfaceColor.alpha, 0.01f)
        assertEquals(0.97f, dialog.surfaceColor.alpha, 0.01f)
        assertEquals(0.12f, control.shadowColor.alpha, 0.01f)
        assertEquals(4.dp, control.blur)
        assertEquals(24.dp, menu.blur)
        assertEquals(menu.shadow, settings.shadow)
    }

    @Test
    fun `night frost remains translucent and uses one palette`() {
        val expected = mapOf(
            GlassMaterialRole.Control to (0.64f to 0.60f),
            GlassMaterialRole.Capsule to (0.64f to 0.60f),
            GlassMaterialRole.ExpandedMenu to (0.90f to 0.60f),
            GlassMaterialRole.SettingsCard to (0.64f to 0.60f),
            GlassMaterialRole.Dialog to (0.97f to 0.38f),
        )
        expected.forEach { (role, values) ->
            val spec = GlassMaterialEngine.resolve(role, dark = true)
            assertEquals(values.first, spec.surfaceColor.alpha, 0.01f)
            assertEquals(0f, spec.brightness, 0.001f)
            assertEquals(1f, spec.saturation, 0.001f)
            assertEquals(values.second, spec.highlightColor.alpha, 0.01f)
            assertEquals(when (role) {
                GlassMaterialRole.Dialog -> Color(0xFF121212)
                GlassMaterialRole.Control, GlassMaterialRole.Capsule, GlassMaterialRole.SettingsCard -> Color(0xFF242424)
                else -> Color(0xFF202020)
            }, spec.surfaceColor.copy(alpha = 1f))
        }
    }

    @Test
    fun `settings card samples exported menu material`() {
        val menu = GlassMaterialEngine.resolve(GlassMaterialRole.ExpandedMenu, dark = false)
        val settings = GlassMaterialEngine.resolve(GlassMaterialRole.SettingsCard, dark = false)
        assertTrue(menu.exportsForSettingsCard)
        assertEquals("RootAndMenu", settings.backdropLayer.name)

        val engine = materialSource()
        assertTrue(engine.contains("rememberCombinedBackdrop(host.root, host.menuExport)"))
        assertTrue(engine.contains("exportedBackdrop = exportedBackdrop"))
    }

    @Test
    fun `reader shells consume semantic engine roles directly`() {
        val host = source("ReaderMoreHost.kt")
        val popover = source("AnchoredNestedPopover.kt")
        val screen = source("PdfViewerScreen.kt")
        assertTrue(host.contains("glassMaterial("))
        assertTrue(host.contains("GlassMaterialRole.Menu"))
        assertTrue(host.contains("GlassMaterialRole.Control"))
        assertTrue(popover.contains("GlassMaterialRole.SettingsCard"))
        assertTrue(screen.contains("GlassMaterialRole.Control"))
        assertTrue(screen.contains("GlassMaterialRole.Capsule"))
        assertFalse(host.contains("drawBackdrop("))
        assertFalse(popover.contains("layerBackdrop("))
    }

    @Test
    fun `glass-on-glass keeps stable layer backdrop instances`() {
        val screen = source("PdfViewerScreen.kt")
        assertTrue(screen.contains("rememberLayerBackdrop()"))
        assertFalse(Regex("""rememberLayerBackdrop\s*\{""").containsMatchIn(screen))
        assertTrue(screen.contains("GlassBackdropSource("))
        assertTrue(screen.contains("backdrop = rootBackdrop"))
        assertTrue(screen.contains("GlassBackdropHost("))
        assertTrue(screen.contains("LocalGlassBackdropHost provides backdropHost"))
    }

    @Test
    fun `two-phase preparing states still map to children`() {
        assertEquals(
            ReaderMoreState.CHILD_PAGE_MAPPING,
            ReaderMoreState.PREPARING_CHILD_PAGE_MAPPING.preparingTarget(),
        )
        assertTrue(ReaderMoreState.PREPARING_CHILD_TOC.isPreparing())
        assertFalse(ReaderMoreState.PREPARING_CHILD_TOC.isChild())
        assertFalse(ReaderMoreState.PREPARING_CHILD_TOC.isInline())
        val host = source("ReaderMoreHost.kt")
        assertTrue(host.contains("canonicalMainBounds"))
        assertTrue(host.contains("onPreparingLayoutReady"))
        assertTrue(host.contains("isPreparing()"))
    }

    @Test
    fun `lens remains gated for custom capsule popover shape`() {
        val capsule = CapsulePopoverShape(160.dp, 280.dp, 22.dp, 16.dp)
        assertFalse(supportsGlassLens(capsule))
        assertTrue(supportsGlassLens(CircleShape))
        assertTrue(supportsGlassLens(RoundedCornerShape(28.dp)))
    }

    @Test
    fun `capsule press indication stays on pill header`() {
        val screen = source("PdfViewerScreen.kt")
        val start = screen.indexOf("private fun PdfInfoCapsule")
        val end = screen.indexOf("private fun JumpPageBody")
        assertTrue(start >= 0 && end > start)
        val capsule = screen.substring(start, end)
        val headerIdx = capsule.indexOf(".height(44.dp)")
        val clickableIdx = capsule.indexOf("clickable(")
        assertTrue(headerIdx >= 0)
        assertTrue(clickableIdx > headerIdx)
        assertFalse(capsule.substring(0, headerIdx).contains("clickable("))
        assertTrue(capsule.contains("indication = null"))
        assertTrue(capsule.contains("capsuleLocalPressHighlight"))
    }

    @Test
    fun `home shares engine while content cards stay solid`() {
        val home = appSource("com/m1racle/reader/ui/home/HomeScreen.kt").readText()
        val row = appSource("com/m1racle/reader/ui/home/DocumentListRow.kt").readText()
        val hero = appSource("com/m1racle/reader/ui/home/ContinueReadingCard.kt").readText()
        assertTrue(home.contains("controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())"))
        assertFalse(home.contains("glassMaterial"))
        assertFalse(home.contains("GlassMaterialRole.Control"))
        assertTrue(row.contains("GlassMaterialRole.Dock"))
        assertTrue(row.contains("GlassCapability.SOLID_FALLBACK"))
        assertFalse(row.contains(".glassMaterial("))
        assertFalse(hero.contains("glassMaterial"))
        assertFalse(home.contains("GlassBackdropSource("))
    }

    private fun source(name: String): String = appSource(
        "com/m1racle/reader/ui/pdf/$name",
    ).readText()

    private fun materialSource(): String = appSource(
        "com/m1racle/reader/ui/material/GlassMaterial.kt",
    ).readText()

    private fun appSource(relative: String): File = ProductionSource.file(relative)
}
