package com.markleaf.app.ui.pdf

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Static contract: Back / Parent / Child shells share [ReaderChromeTokens]
 * border/alpha/elevation — no hard black outline, light glass 88–94%.
 *
 * Also drives the SHIPPED applied-border path ([resolveGlassBorder] +
 * [appliedGlassBorderColor]) so opaque Black 1dp outlines cannot regress green.
 */
class ReaderChromeTokensContractTest {

    @Test
    fun `glass tokens in product bands`() {
        assertTrue(ReaderChromeTokens.LightBaseAlpha in 0.88f..0.94f)
        assertTrue(
            ReaderChromeTokens.LightBaseAlpha + ReaderChromeTokens.ChildAlphaDelta in 0.90f..0.97f,
        )
        assertTrue(ReaderChromeTokens.LightBorderAlpha in 0.03f..0.07f)
        assertTrue(ReaderChromeTokens.DarkBorderAlpha in 0.08f..0.12f)
        assertTrue(ReaderChromeTokens.ParentElevationDp in 8f..10f)
        assertTrue(ReaderChromeTokens.ChildElevationDp in 12f..16f)
        assertTrue(ReaderChromeTokens.DividerAlpha in 0.05f..0.08f)
        assertTrue(ReaderChromeTokens.LightMutedAlpha in 0.55f..0.62f)
    }

    @Test
    fun `applied glass border alpha is hairline not opaque black`() {
        // Light: base Black + LightBorderAlpha → drawn alpha stays 0.03–0.07.
        val (lightBase, lightA) = resolveGlassBorder(dark = false)
        assertEquals(0f, lightBase.red, 1e-4f)
        assertEquals(0f, lightBase.green, 1e-4f)
        assertEquals(0f, lightBase.blue, 1e-4f)
        // Base must be full-opaque RGB; alpha lives only in borderAlpha.
        assertEquals(1f, lightBase.alpha, 1e-4f)
        assertEquals(ReaderChromeTokens.LightBorderAlpha, lightA, 1e-4f)
        assertTrue(lightA in 0.03f..0.07f)

        val lightSpec = sampleSpec(borderColor = lightBase, borderAlpha = lightA)
        val lightStroke = appliedGlassBorderColor(lightSpec)
        // Color packs alpha to 8-bit (~1/255); assert hairline band, not exact float.
        assertTrue(
            "applied alpha=${lightStroke.alpha} must stay in light hairline 0.03–0.07 (not opaque 1f)",
            lightStroke.alpha in 0.03f..0.07f,
        )
        assertEquals(
            "applied path must use borderAlpha token (8-bit quantization ok)",
            lightA,
            lightStroke.alpha,
            0.01f,
        )
        assertFalse("must not be opaque black", lightStroke.alpha >= 0.99f)

        // Dark: base White + DarkBorderAlpha → 0.08–0.12.
        val (darkBase, darkA) = resolveGlassBorder(dark = true)
        assertEquals(1f, darkBase.red, 1e-4f)
        assertEquals(1f, darkBase.alpha, 1e-4f)
        assertEquals(ReaderChromeTokens.DarkBorderAlpha, darkA, 1e-4f)
        assertTrue(darkA in 0.08f..0.12f)
        val darkStroke = appliedGlassBorderColor(
            sampleSpec(borderColor = darkBase, borderAlpha = darkA),
        )
        assertTrue(darkStroke.alpha in 0.08f..0.12f)
        assertEquals(darkA, darkStroke.alpha, 0.01f)
        assertFalse(darkStroke.alpha >= 0.99f)
    }

    @Test
    fun `readerGlassSurface uses appliedGlassBorderColor not borderAlpha equals one overwrite`() {
        val glass = source("ReaderChromeGlass.kt")
        // Factory stores opaque base + token alpha (not pre-baked color + borderAlpha=1f).
        assertTrue(glass.contains("resolveGlassBorder"))
        assertTrue(glass.contains("appliedGlassBorderColor"))
        assertTrue(glass.contains("borderAlpha = borderA"))
        // Surface must call appliedGlassBorderColor for the stroke.
        assertTrue(glass.contains("val stroke = appliedGlassBorderColor(spec)"))
        assertTrue(glass.contains(".border(spec.borderWidth, stroke, shape)"))
        // Regression: never set borderAlpha = 1f while color already embeds hairline.
        assertFalse(glass.contains("borderAlpha = 1f"))
    }

    private fun sampleSpec(
        borderColor: Color,
        borderAlpha: Float,
    ): ReaderGlassSurfaceSpec = ReaderGlassSurfaceSpec(
        containerColor = Color.White,
        containerAlpha = ReaderChromeTokens.LightBaseAlpha,
        borderColor = borderColor,
        borderAlpha = borderAlpha,
        borderWidth = 1.dp,
        elevation = ReaderChromeTokens.ParentElevationDp.dp,
        contentColor = Color.Black,
        contentMutedAlpha = ReaderChromeTokens.LightMutedAlpha,
    )

    @Test
    fun `Back Parent Child all call unified readerChromeSurface`() {
        val host = source("ReaderMoreHost.kt")
        val pop = source("AnchoredNestedPopover.kt")
        val screen = source("PdfViewerScreen.kt")
        // Material dual-style entry (Frosted/Liquid) — not ad-hoc glass/Backdrop.
        assertTrue(host.contains("readerChromeSurface("))
        assertTrue(pop.contains("readerChromeSurface("))
        assertTrue(screen.contains("readerChromeSurface("))
        assertTrue(host.contains("ReaderChromeSurfaceRole.Parent") || host.contains("ReaderGlassElevation.Main"))
        assertTrue(pop.contains("ReaderChromeSurfaceRole.Child") || pop.contains("ReaderGlassElevation.Child"))
    }

    @Test
    fun `shells do not hardcode high-alpha black border on glass path`() {
        val glass = source("ReaderChromeGlass.kt")
        assertFalse(glass.contains("Color.Black.copy(alpha = 0.2"))
        assertFalse(glass.contains("Color.Black.copy(alpha = 0.3"))
        assertFalse(glass.contains("borderColor = Color.Black)"))
        assertTrue(glass.contains("readerChromeBorderColor()"))
        assertTrue(glass.contains("LightBorderAlpha"))
    }

    @Test
    fun `host snaps inline progress when child opens - no dual expansion freeze`() {
        val host = source("ReaderMoreHost.kt")
        assertTrue(host.contains("isPreparing()") || host.contains("isChild()"))
        assertTrue(host.contains("readingModeProgress.snapTo(0f)"))
        assertTrue(host.contains("topBarProgress.snapTo(0f)"))
        assertTrue(host.contains("baseHeightPx"))
        assertTrue(host.contains("canonicalMainBounds") || host.contains("nestedActive"))
    }

    @Test
    fun `page mapping has no sticky clipped footer - save is in-body`() {
        val card = source("ReaderAdvancedCard.kt")
        assertTrue(
            "expected in-body 44dp save; len=${card.length} cwd=${File(".").absolutePath}",
            card.contains("height(44.dp)"),
        )
        assertTrue(
            "expected IME Done submit",
            card.contains("ImeAction.Done"),
        )
        assertFalse("must not auto-requestFocus", card.contains("requestFocus()"))
        assertFalse("must not showSoftInput on open", card.contains("showSoftInput"))
        assertTrue(card.contains("TextbookPageAdvancedContent"))
        assertTrue(card.contains("verticalScroll(rememberScrollState())"))
    }

    @Test
    fun `screen wires section-while-child close-first path`() {
        val screen = source("PdfViewerScreen.kt")
        assertTrue(screen.contains("ReaderMoreTransitions.onSectionToggle"))
        assertTrue(screen.contains("pendingInlineAfterChild"))
        assertTrue(screen.contains("ReaderMoreTransitions.onBack"))
        assertTrue(screen.contains("ReaderMoreTransitions.afterChildCollapsed"))
        assertTrue(screen.contains("AdvancedChildLayoutSpec.maxHeightDp"))
    }

    private fun source(name: String): String {
        val candidates = listOf(
            File("app/src/main/java/com/markleaf/app/ui/pdf/$name"),
            File("src/main/java/com/markleaf/app/ui/pdf/$name"),
            File("../app/src/main/java/com/markleaf/app/ui/pdf/$name"),
            // Absolute-ish from common MarkLeaf roots.
            File("/root/code/MarkLeaf/app/src/main/java/com/markleaf/app/ui/pdf/$name"),
        )
        val hit = candidates.firstOrNull { it.isFile }
            ?: error("source not found for $name; cwd=${File(".").absolutePath}; tried=$candidates")
        return hit.readText()
    }
}
