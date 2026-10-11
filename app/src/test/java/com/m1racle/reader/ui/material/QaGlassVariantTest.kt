package com.m1racle.reader.ui.material

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Value mapping for the QA-only glass variants (QA_GLASS_VARIANTS.md, 2026-10-10). */
class QaGlassVariantTest {

    private val floating = listOf(GlassMaterialRole.Control, GlassMaterialRole.Capsule, GlassMaterialRole.Dock)
    private fun sel(v: QaGlassVariant, lens: QaDockNightLens = QaDockNightLens.WHITE_10) = QaGlassSelection(v, lens)
    private fun spec(role: GlassMaterialRole, dark: Boolean, qa: QaGlassSelection, pressed: Boolean = false) =
        GlassMaterialEngine.resolve(role, dark, pressed = pressed, qa = qa)

    private fun assertRgb(expected: Color, actual: Color) {
        assertEquals(expected.red, actual.red, 0.004f)
        assertEquals(expected.green, actual.green, 0.004f)
        assertEquals(expected.blue, actual.blue, 0.004f)
    }

    @Test
    fun baselineAndFollowPageLeaveEveryMaterialUnchanged() {
        GlassMaterialRole.entries.forEach { role ->
            assertNull(qaGlassOverride(role, sel(QaGlassVariant.BASELINE)))
            assertNull(qaGlassOverride(role, sel(QaGlassVariant.FOLLOW_PAGE)))
            listOf(false, true).forEach { dark ->
                val base = spec(role, dark, QaGlassSelection())
                val follow = spec(role, dark, sel(QaGlassVariant.FOLLOW_PAGE))
                assertEquals(base.surfaceColor, follow.surfaceColor)
                assertEquals(base.blur, follow.blur)
                assertEquals(base.shadow, follow.shadow)
                assertEquals(base.highlightColor, follow.highlightColor)
            }
        }
        assertNull(qaGlassPrimaryText(QaGlassVariant.BASELINE, glassDark = true))
        assertNull(qaPdfPageNumberText(QaGlassVariant.BASELINE, glassDark = false))
    }

    @Test
    fun solidNightOnlyDensifiesNightFloatingGlass() {
        floating.forEach { role ->
            val base = spec(role, dark = false, QaGlassSelection())
            val day = spec(role, dark = false, sel(QaGlassVariant.SOLID_NIGHT))
            assertEquals(base.surfaceColor, day.surfaceColor)
            val night = spec(role, dark = true, sel(QaGlassVariant.SOLID_NIGHT))
            assertRgb(Color(0xFF242424), night.surfaceColor)
            assertEquals("$role night alpha", 0.75f, night.surfaceColor.alpha, 0.004f)
            // A denser plate must not turn grey / lighter on press.
            val pressed = spec(role, dark = true, sel(QaGlassVariant.SOLID_NIGHT), pressed = true)
            assertRgb(Color(0xFF242424), pressed.surfaceColor)
            assertTrue(pressed.surfaceColor.alpha >= night.surfaceColor.alpha)
        }
        val menu = spec(GlassMaterialRole.Menu, dark = true, sel(QaGlassVariant.SOLID_NIGHT))
        assertEquals(spec(GlassMaterialRole.Menu, dark = true, QaGlassSelection()).surfaceColor, menu.surfaceColor)
    }

    @Test
    fun coolapkPlateMatchesSpec() {
        floating.forEach { role ->
            val day = spec(role, dark = false, sel(QaGlassVariant.COOLAPK))
            val night = spec(role, dark = true, sel(QaGlassVariant.COOLAPK))
            assertRgb(Color.White, day.surfaceColor)
            assertEquals(0.70f, day.surfaceColor.alpha, 0.004f)
            assertRgb(Color(0xFF1C1C1E), night.surfaceColor)
            assertEquals(0.70f, night.surfaceColor.alpha, 0.004f)
            listOf(day, night).forEach { assertEquals("$role blur", 24f, it.blur.value, 0f) }
            listOf(day, night).forEach { assertEquals("$role rim", 1f, it.highlightWidth.value, 0f) }
            assertEquals(0.50f, day.highlightColor.alpha, 0.004f)
            assertEquals(0.25f, night.highlightColor.alpha, 0.004f)
            assertEquals(12f, day.shadow.value, 0f)
            assertEquals(0.12f, day.shadowColor.alpha, 0.004f)
            assertEquals(0.30f, night.shadowColor.alpha, 0.004f)
        }
        // Whole-plate press: tint +0.08, rim +0.15 (no grey pressed tint), scale 1.03.
        floating.forEach { role ->
            listOf(false, true).forEach { dark ->
                val idle = spec(role, dark, sel(QaGlassVariant.COOLAPK))
                val down = spec(role, dark, sel(QaGlassVariant.COOLAPK), pressed = true)
                assertRgb(idle.surfaceColor, down.surfaceColor)
                assertEquals("$role tint", idle.surfaceColor.alpha + 0.08f, down.surfaceColor.alpha, 0.004f)
                assertEquals("$role rim", idle.highlightColor.alpha + 0.15f, down.highlightColor.alpha, 0.004f)
            }
        }
        listOf(GlassMaterialRole.Control, GlassMaterialRole.Capsule).forEach { role ->
            assertEquals(1.03f, spec(role, false, sel(QaGlassVariant.COOLAPK), pressed = true).scale, 0f)
        }
        // The Dock owns its own press transform; engine scale stays production.
        assertEquals(
            spec(GlassMaterialRole.Dock, false, QaGlassSelection(), pressed = true).scale,
            spec(GlassMaterialRole.Dock, false, sel(QaGlassVariant.COOLAPK), pressed = true).scale,
            0f,
        )
        // Baseline press is untouched.
        assertEquals(1.075f, spec(GlassMaterialRole.Capsule, false, QaGlassSelection(), pressed = true).scale, 0f)
        assertEquals(0.40f, spec(GlassMaterialRole.Capsule, false, QaGlassSelection(), pressed = true).surfaceColor.alpha, 0.004f)
        assertTrue(sel(QaGlassVariant.COOLAPK).coolapk)
        assertTrue(!sel(QaGlassVariant.SOLID_NIGHT).coolapk)
    }

    // WCAG relative luminance / contrast, composited over the worst plain backdrop.
    private fun lin(c: Float) = if (c <= 0.03928f) c / 12.92f else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    private fun lum(c: Color) = 0.2126f * lin(c.red) + 0.7152f * lin(c.green) + 0.0722f * lin(c.blue)
    private fun over(top: Color, under: Color) = Color(
        top.red * top.alpha + under.red * (1 - top.alpha),
        top.green * top.alpha + under.green * (1 - top.alpha),
        top.blue * top.alpha + under.blue * (1 - top.alpha),
    )
    private fun contrast(a: Color, b: Color): Float {
        val (hi, lo) = listOf(lum(a), lum(b)).sortedDescending()
        return (hi + 0.05f) / (lo + 0.05f)
    }

    @Test
    fun coolapkImportIsDenseTintedGlassReadableOnWhiteAndBlack() {
        assertRgb(Color(0xFF0055B3), qaCoolapkImportTint(dark = false))
        assertRgb(Color(0xFF6AAEFF), qaCoolapkImportTint(dark = true))
        assertEquals(0.80f, qaCoolapkImportTint(false).alpha, 0.004f)
        assertEquals(0.80f, qaCoolapkImportTint(true).alpha, 0.004f)
        assertEquals(Color.White, qaCoolapkImportGlyph(dark = false))
        assertEquals(Color(0xFF1C1C1E), qaCoolapkImportGlyph(dark = true))
        assertEquals(1f, QaCoolapkImport.RimWidth.value, 0f)
        listOf(false, true).forEach { dark ->
            val worst = listOf(Color.White, Color.Black).minOf { bg ->
                contrast(qaCoolapkImportGlyph(dark), over(qaCoolapkImportTint(dark), bg))
            }
            assertTrue("import glyph dark=$dark worst contrast $worst", worst >= 4.5f)
        }
    }

    @Test
    fun coolapkKeepsProductionGeometry() {
        // Material only: variant 2 must not resize the Dock, the "+" or PDF controls.
        val src = java.io.File("src/main/java/com/m1racle/reader/ui")
        val dock = java.io.File(src, "hub/GlassDock.kt").readText()
        val pdf = java.io.File(src, "pdf/PdfViewerScreen.kt").readText()
        val tokens = java.io.File(src, "home/HomeDesignTokens.kt").readText()
        listOf("DockHeight", "DockBottomGap", "DockHorizontalMargin", "DockLensExtraWidth",
            "DockImportSize", "DockLabelSizeSp", "PdfControlSize").forEach { name ->
            listOf(dock, pdf, tokens).forEach { assertTrue("QaCoolapk.$name still used", !it.contains("QaCoolapk.$name")) }
        }
        assertTrue(pdf.contains("internal fun pdfChromeControlSize(production: Dp): Dp = production"))
        assertTrue(dock.contains("Box(Modifier.size(width = 49.dp, height = 48.dp)"))
    }
    @Test
    fun textColorsAndPdfChromeDarkness() {
        listOf(QaGlassVariant.FOLLOW_PAGE, QaGlassVariant.SOLID_NIGHT, QaGlassVariant.COOLAPK).forEach { v ->
            assertEquals(Color(0xFF1C1C1E), qaGlassPrimaryText(v, glassDark = false))
            assertEquals(Color(0xFFF2F2F2), qaGlassPrimaryText(v, glassDark = true))
            assertEquals(qaGlassPrimaryText(v, true), qaPdfPageNumberText(v, true))
        }
        // 1a follows the rendered page; the others follow the theme.
        assertEquals(false, qaPdfChromeDark(QaGlassVariant.FOLLOW_PAGE, themeDark = true, pagesDark = false))
        assertEquals(true, qaPdfChromeDark(QaGlassVariant.FOLLOW_PAGE, themeDark = false, pagesDark = true))
        assertEquals(true, qaPdfChromeDark(QaGlassVariant.SOLID_NIGHT, themeDark = true, pagesDark = false))
        assertEquals(false, qaPdfChromeDark(QaGlassVariant.BASELINE, themeDark = false, pagesDark = true))
    }

    @Test
    fun dockNightLensB() {
        val b = sel(QaGlassVariant.BASELINE, QaDockNightLens.BLACK_12_STROKE)
        val night = spec(GlassMaterialRole.SelectionLens, dark = true, b)
        assertRgb(Color.Black, night.surfaceColor)
        assertEquals(0.12f, night.surfaceColor.alpha, 0.004f)
        assertEquals(
            spec(GlassMaterialRole.SelectionLens, dark = false, QaGlassSelection()).surfaceColor,
            spec(GlassMaterialRole.SelectionLens, dark = false, b).surfaceColor,
        )
        assertNotEquals(night.surfaceColor, spec(GlassMaterialRole.SelectionLens, dark = true, QaGlassSelection()).surfaceColor)
        assertEquals(0.35f, qaDockNightLensStrokeAlpha(QaDockNightLens.BLACK_12_STROKE, dark = true), 0f)
        assertEquals(0f, qaDockNightLensStrokeAlpha(QaDockNightLens.BLACK_12_STROKE, dark = false), 0f)
        assertEquals(0f, qaDockNightLensStrokeAlpha(QaDockNightLens.WHITE_10, dark = true), 0f)
        assertEquals(Color(0xFFF2F2F2), qaDockSelectedText(QaDockNightLens.BLACK_12_STROKE, dark = true))
        assertNull(qaDockSelectedText(QaDockNightLens.BLACK_12_STROKE, dark = false))
        assertNull(qaDockSelectedText(QaDockNightLens.WHITE_10, dark = true))
    }

    @Test
    fun pdfNightInvertToggle() {
        assertTrue(QaGlassSelection().pdfNightInvert)
        assertTrue(qaPdfInvertPages(themeDark = true, selection = QaGlassSelection()))
        assertTrue(!qaPdfInvertPages(themeDark = false, selection = QaGlassSelection()))
        val off = QaGlassSelection(variant = QaGlassVariant.FOLLOW_PAGE, pdfNightInvert = false)
        assertTrue(!qaPdfInvertPages(themeDark = true, selection = off))
        // 1a then follows the white pages: light glass + dark text in night theme.
        val pagesDark = qaPdfInvertPages(themeDark = true, selection = off)
        assertEquals(false, qaPdfChromeDark(QaGlassVariant.FOLLOW_PAGE, themeDark = true, pagesDark = pagesDark))
        assertEquals(Color(0xFF1C1C1E), qaGlassPrimaryText(QaGlassVariant.FOLLOW_PAGE, glassDark = false))
    }

    // ---- 档 3 全部统一 ----
    private val unifiedRoles = listOf(GlassMaterialRole.Control, GlassMaterialRole.Capsule, GlassMaterialRole.Dock,
        GlassMaterialRole.Menu, GlassMaterialRole.SettingsCard)
    private fun uni(blur: QaUnifiedBlur = QaUnifiedBlur.B4, order: QaCompressOrder = QaCompressOrder.VIBRANCY_FIRST) =
        QaGlassSelection(variant = QaGlassVariant.UNIFIED, unifiedBlur = blur, compressOrder = order)

    @Test
    fun unifiedUsesCapsulePlateWithCompressionOnEveryGlassSurface() {
        unifiedRoles.forEach { role ->
            QaUnifiedBlur.entries.forEach { b ->
                val day = spec(role, dark = false, uni(b))
                val night = spec(role, dark = true, uni(b))
                assertRgb(Color.White, day.surfaceColor)
                assertEquals("$role day alpha", 0.32f, day.surfaceColor.alpha, 0.004f)
                assertRgb(Color(0xFF242424), night.surfaceColor)
                assertEquals("$role night alpha", 0.22f, night.surfaceColor.alpha, 0.004f)
                assertEquals("$role blur", b.radius.value, day.blur.value, 0f)
                assertEquals(0.45f, day.contrast, 0f); assertEquals(0.275f, day.brightness, 0f)
                assertEquals(0.35f, night.contrast, 0f); assertEquals(-0.325f, night.brightness, 0f)
                assertEquals("$role muted", 1f, day.contentMutedAlpha, 0f)
                assertEquals("$role muted", 1f, night.contentMutedAlpha, 0f)
                // Press: no grey #AAAAAA plate, only a small denser tint.
                val down = spec(role, dark = true, uni(b), pressed = true)
                assertRgb(Color(0xFF242424), down.surfaceColor)
                assertTrue(down.surfaceColor.alpha >= night.surfaceColor.alpha)
            }
        }
        assertEquals(listOf(4f, 12f), QaUnifiedBlur.entries.map { it.radius.value })
        // Selection lens and progressive chrome are not part of 档 3.
        listOf(GlassMaterialRole.SelectionLens, GlassMaterialRole.ProgressiveChrome, GlassMaterialRole.ExpandedMenu, GlassMaterialRole.Dialog).forEach { role ->
            assertNull(qaGlassOverride(role, uni()))
        }
        assertEquals(Color.Black, qaGlassPrimaryText(QaGlassVariant.UNIFIED, glassDark = false))
        assertEquals(Color.White, qaGlassPrimaryText(QaGlassVariant.UNIFIED, glassDark = true))
    }

    @Test
    fun unifiedCompressionRunsAndOrderFollowsTheSwitch() {
        unifiedRoles.forEach { role ->
            listOf(false, true).forEach { dark ->
                val first = spec(role, dark, uni(order = QaCompressOrder.VIBRANCY_FIRST))
                val order = glassColorEffectOrder(first)
                assertTrue("$role compression must run", glassColorStep(first))
                assertEquals("$role compression last", "colorControls", order.last())
                val other = glassColorEffectOrder(spec(role, dark, uni(order = QaCompressOrder.COMPRESS_FIRST)))
                assertEquals("$role compression first", "colorControls", other.first())
            }
        }
        // Production order is untouched.
        assertTrue(!spec(GlassMaterialRole.Capsule, false, QaGlassSelection()).compressAfterVibrancy)
        assertTrue(QaGlassSelection().compressOrder == QaCompressOrder.VIBRANCY_FIRST)
    }

    @Test
    fun unifiedWorstCaseContrastOnPureBlackAndWhite() {
        fun compressed(v: Float, c: Float, b: Float) = ((v - 0.5f) * c + 0.5f + b).coerceIn(0f, 1f)
        listOf(false, true).forEach { dark ->
            listOf(false, true).forEach { pressed ->
                val s = spec(GlassMaterialRole.Menu, dark, uni(), pressed = pressed)
                val text = qaGlassPrimaryText(QaGlassVariant.UNIFIED, glassDark = dark)!!
                val worst = listOf(0f, 1f).minOf { bg ->
                    val g = compressed(bg, s.contrast, s.brightness)
                    contrast(text, over(s.surfaceColor, Color(g, g, g)))
                }
                assertTrue("dark=$dark pressed=$pressed worst $worst", worst >= 4.5f)
            }
        }
    }
}
