package com.markleaf.app.ui.pdf

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.markleaf.app.ui.common.LiquidGlassTokens
import com.markleaf.app.ui.common.LiquidSurfaceLevel
import com.markleaf.app.ui.common.resolvedSurfaceAlpha
import com.markleaf.app.ui.common.shapeSupportsLiquidLens
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** r26.1: Liquid-only chrome + geometry + glass-on-glass contracts. */
class ReaderChromeMaterialTest {

    @Test
    fun `parent width band is 220dp compact`() {
        val bands = ReaderChromeMaterialLogic.geometryInvariantBands()
        assertEquals(220f, bands.parentWidthDp, 0.01f)
        assertEquals(MoreMenuWidth.value, bands.parentWidthDp, 0.01f)
        assertTrue(MoreMenuWidth.value in 212f..224f)
        assertEquals(MenuRowHeight.value, bands.rowHeightDp, 0.01f)
    }

    @Test
    fun `no Frosted material style remains in production chrome`() {
        val material = source("ReaderChromeMaterial.kt")
        val prefs = prefsSource()
        val screen = source("PdfViewerScreen.kt")
        assertFalse(material.contains("FROSTED"))
        assertFalse(material.contains("ReaderChromeMaterialStyle"))
        assertFalse(prefs.contains("KEY_MATERIAL_STYLE"))
        assertFalse(prefs.contains("getMaterialStyle"))
        assertFalse(screen.contains("pdf_chrome_material_frosted"))
        assertFalse(screen.contains("setChromeMaterialStyle"))
        assertTrue(material.contains("markLeafLiquidSurface") || material.contains("LiquidSurfaceLevel"))
    }

    @Test
    fun `parent menu fill stays translucent glass not a solid plate`() {
        assertEquals(0.60f, LiquidGlassTokens.ParentSurfaceAlpha, 0.001f)
        assertEquals(0.50f, LiquidGlassTokens.ControlSurfaceAlpha, 0.001f)
        assertEquals(
            0.60f,
            resolvedSurfaceAlpha(LiquidSurfaceLevel.Parent, pressed = false),
            0.001f,
        )
        assertEquals(
            0.50f,
            resolvedSurfaceAlpha(LiquidSurfaceLevel.Control, pressed = false),
            0.001f,
        )
        assertTrue(
            "reference frost is milky but still see-through",
            resolvedSurfaceAlpha(LiquidSurfaceLevel.Parent, pressed = false) in 0.55f..0.75f,
        )
    }

    @Test
    fun `reader glass follows Kyant dialog regular frost`() {
        assertEquals(0.60f, LiquidGlassTokens.ParentSurfaceAlpha, 0.001f)
        assertEquals(0.66f, LiquidGlassTokens.ForegroundSurfaceAlpha, 0.001f)
        assertEquals(0.58f, LiquidGlassTokens.ParentDarkSurfaceAlpha, 0.001f)
        assertEquals(0.20f, LiquidGlassTokens.LightBrightness, 0.001f)
        assertEquals(-0.08f, LiquidGlassTokens.DarkBrightness, 0.001f)
        val darkParent = resolvedSurfaceAlpha(
            LiquidSurfaceLevel.Parent,
            pressed = false,
            dark = true,
        )
        assertEquals(0.58f, darkParent, 0.001f)
        assertTrue(
            "night frost stays see-through, not a solid plate",
            darkParent in 0.50f..0.70f,
        )
        val common = commonSource("MarkLeafLiquidSurface.kt")
        val liquidDraw = common.substringAfter("private fun liquidDraw")
        assertTrue(liquidDraw.contains("colorControls("))
        assertTrue(liquidDraw.contains("Highlight.Plain"))
        assertTrue(liquidDraw.contains("LightSurfaceTint"))
        assertTrue(liquidDraw.contains("DarkHighlightAlpha"))
        assertFalse(liquidDraw.contains("vibrancy()"))
        assertFalse(common.contains("DarkSurfaceAlphaBoost"))
    }

    @Test
    fun `child overlay samples parent export for stacked glass`() {
        val material = source("ReaderChromeMaterial.kt")
        val common = commonSource("MarkLeafLiquidSurface.kt")
        assertTrue(material.contains("ReaderChromeSurfaceRole.Child -> LiquidSurfaceLevel.Foreground"))
        assertTrue(material.contains("exportForChild = role == ReaderChromeSurfaceRole.Parent"))
        assertTrue(common.contains("rememberCombinedBackdrop(host.root, host.parentExport)"))
        assertFalse(material.contains("exportForChild = false"))
    }

    @Test
    fun `shells use readerChromeSurface liquid entry`() {
        val host = source("ReaderMoreHost.kt")
        val pop = source("AnchoredNestedPopover.kt")
        val screen = source("PdfViewerScreen.kt")
        assertTrue(host.contains("readerChromeSurface("))
        assertTrue(pop.contains("readerChromeSurface("))
        assertTrue(screen.contains("readerChromeSurface("))
        assertFalse(host.contains("drawBackdrop("))
        assertFalse(pop.contains("layerBackdrop("))
    }

    @Test
    fun `glass-on-glass uses exportedBackdrop and stable rememberLayerBackdrop`() {
        val common = commonSource("MarkLeafLiquidSurface.kt")
        val screen = source("PdfViewerScreen.kt")
        assertTrue(common.contains("exportForChild") || common.contains("exportedBackdrop"))
        assertTrue(screen.contains("rememberLayerBackdrop()"))
        assertFalse(Regex("""rememberLayerBackdrop\s*\{""").containsMatchIn(screen))
        assertTrue(screen.contains("layerBackdrop(rootBackdrop)"))
    }

    @Test
    fun `two-phase preparing states exist and map to children`() {
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
    fun `lens gated for CapsulePopoverShape`() {
        val capsule = CapsulePopoverShape(160.dp, 280.dp, 22.dp, 16.dp)
        assertFalse(shapeSupportsLiquidLens(capsule))
        assertTrue(shapeSupportsLiquidLens(CircleShape))
        assertTrue(shapeSupportsLiquidLens(RoundedCornerShape(28.dp)))
    }

    @Test
    fun `capsule press indication is on the pill header not the morphing card`() {
        val screen = source("PdfViewerScreen.kt")
        val start = screen.indexOf("private fun PdfInfoCapsule")
        val end = screen.indexOf("private fun JumpPageBody")
        assertTrue(start >= 0 && end > start)
        val capsule = screen.substring(start, end)
        val headerIdx = capsule.indexOf(".height(44.dp)")
        val clickableIdx = capsule.indexOf("clickable(")
        assertTrue("capsule header must be 44dp", headerIdx >= 0)
        assertTrue("capsule still has a clickable", clickableIdx >= 0)
        assertTrue(
            "clickable must sit on the 44dp pill, not the expanding card",
            clickableIdx > headerIdx,
        )
        val surfaceWindow = capsule.substring(0, headerIdx)
        assertFalse(
            "morphing Column/surface must not own clickable indication",
            surfaceWindow.contains("clickable("),
        )
        assertTrue(capsule.contains("indication = null"))
        assertTrue(capsule.contains("capsuleLocalPressHighlight"))
        assertTrue(
            "pill clip keeps the highlight inside the capsule silhouette",
            capsule.contains("clip(RoundedCornerShape(22.dp))"),
        )
    }

    @Test
    fun `home restores system bars and reuses shared liquid for floating controls`() {
        val home = listOf(
            File("app/src/main/java/com/markleaf/app/ui/home/HomeScreen.kt"),
            File("src/main/java/com/markleaf/app/ui/home/HomeScreen.kt"),
            File("/root/code/MarkLeaf/app/src/main/java/com/markleaf/app/ui/home/HomeScreen.kt"),
        ).first { it.isFile }.readText()
        val common = commonSource("MarkLeafLiquidSurface.kt")
        assertTrue(home.contains("wic.show(all)"))
        // Home floating controls (FAB / ··· menu) reuse the SAME shared surface.
        assertTrue(home.contains("markLeafLiquidSurface"))
        assertTrue(home.contains("layerBackdrop(rootBackdrop)"))
        // The shared renderer remains intact and is the Reader surface.
        assertTrue(common.contains("markLeafLiquidSurface"))
        assertTrue(common.contains("LiquidSurfaceLevel"))
    }

    private fun source(name: String): String = listOf(
        File("app/src/main/java/com/markleaf/app/ui/pdf/$name"),
        File("src/main/java/com/markleaf/app/ui/pdf/$name"),
        File("/root/code/MarkLeaf/app/src/main/java/com/markleaf/app/ui/pdf/$name"),
    ).first { it.isFile }.readText()

    private fun commonSource(name: String): String = listOf(
        File("app/src/main/java/com/markleaf/app/ui/common/$name"),
        File("/root/code/MarkLeaf/app/src/main/java/com/markleaf/app/ui/common/$name"),
    ).first { it.isFile }.readText()

    private fun prefsSource(): String = listOf(
        File("app/src/main/java/com/markleaf/app/pdf/PdfChromePrefs.kt"),
        File("/root/code/MarkLeaf/app/src/main/java/com/markleaf/app/pdf/PdfChromePrefs.kt"),
    ).first { it.isFile }.readText()
}
