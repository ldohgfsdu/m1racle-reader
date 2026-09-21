package com.m1racle.reader.ui.material

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftGlassComparisonTest {
    @Test
    fun `candidate parameters stay within the alpha cap`() {
        for (candidate in SoftGlassCandidate.entries) {
            for (role in SoftGlassRole.entries) {
                for (dark in listOf(false, true)) {
                    for (pressed in listOf(false, true)) {
                        val spec = SoftGlassComparison.resolve(candidate, role, dark, pressed)
                        assertTrue(
                            "$candidate/$role dark=$dark pressed=$pressed tint ${spec.tintAlpha}",
                            spec.tintAlpha <= SoftGlassComparison.TintCap,
                        )
                        assertTrue(spec.tintAlpha >= 0f)
                        assertEquals(spec.tintAlpha, spec.tint.alpha, 1f / 255f)
                        assertTrue(spec.tint.alpha <= SoftGlassComparison.TintCap + 1f / 255f)
                    }
                }
            }
        }
    }

    @Test
    fun `foreground alpha is always one`() {
        for (candidate in SoftGlassCandidate.entries) {
            for (role in SoftGlassRole.entries) {
                for (dark in listOf(false, true)) {
                    val spec = SoftGlassComparison.resolve(candidate, role, dark)
                    assertEquals(1f, spec.foregroundAlpha, 0f)
                    assertEquals(SoftGlassComparison.ForegroundAlpha, spec.foregroundAlpha, 0f)
                }
            }
        }
    }

    @Test
    fun `background choice is not a recipe input`() {
        val source = source("ui/material/SoftGlassComparison.kt")
        val signature = source.substring(
            source.indexOf("fun resolve("),
            source.indexOf(")", source.indexOf("fun resolve(")),
        )
        assertFalse(signature.contains("background"))
        assertFalse(signature.contains("text"))
        val preview = source("ui/material/BlurComparisonPreview.kt")
        val plate = preview.substring(
            preview.indexOf("fun Modifier.plate("),
            preview.indexOf("fun Modifier.legacyPlate("),
        )
        assertFalse(plate.contains("textBackground"))
        assertFalse(plate.contains("ColorStress"))
        assertFalse(Regex("resolve\\([^)]*textBackground").containsMatchIn(preview))
        assertFalse(Regex("schemeLine\\([^)]*textBackground").containsMatchIn(preview))
        assertFalse(Regex("parameterSummary\\([^)]*textBackground").containsMatchIn(preview))
        val first = SoftGlassComparison.parameterSummary(SoftGlassCandidate.H0, dark = false, density = 3f)
        val second = SoftGlassComparison.parameterSummary(SoftGlassCandidate.H0, dark = false, density = 3f)
        assertEquals(first, second)
    }

    @Test
    fun `production recipes stay on the frozen authority`() {
        val light = mapOf(
            GlassMaterialRole.Control to 0.86f,
            GlassMaterialRole.Capsule to 0.86f,
            GlassMaterialRole.Menu to 0.86f,
            GlassMaterialRole.ExpandedMenu to 0.90f,
            GlassMaterialRole.SettingsCard to 0.90f,
            GlassMaterialRole.Dialog to 0.97f,
            GlassMaterialRole.ProgressiveChrome to 0.62f,
        )
        for ((role, alpha) in light) {
            val spec = GlassMaterialEngine.resolve(role, GlassEnvironment.READER_LIGHT)
            assertEquals(alpha, spec.surfaceColor.alpha, 0.004f)
        }
        assertEquals(
            0.80f,
            GlassMaterialEngine.resolve(GlassMaterialRole.Control, GlassEnvironment.READER_DARK)
                .surfaceColor.alpha,
            0.004f,
        )
        assertEquals(
            0.86f,
            GlassMaterialEngine.resolve(GlassMaterialRole.SettingsCard, GlassEnvironment.READER_DARK)
                .surfaceColor.alpha,
            0.004f,
        )
        for (role in listOf(
            GlassMaterialRole.Control,
            GlassMaterialRole.Capsule,
            GlassMaterialRole.Menu,
            GlassMaterialRole.ExpandedMenu,
            GlassMaterialRole.SettingsCard,
        )) {
            assertEquals(
                18.dp,
                GlassMaterialEngine.resolve(role, GlassEnvironment.READER_LIGHT).blur,
            )
        }
        val before = GlassMaterialEngine.resolve(
            GlassMaterialRole.Control,
            GlassEnvironment.READER_LIGHT,
        )
        SoftGlassComparison.resolve(SoftGlassCandidate.H0, SoftGlassRole.Panel, dark = true, pressed = true)
        val after = GlassMaterialEngine.resolve(
            GlassMaterialRole.Control,
            GlassEnvironment.READER_LIGHT,
        )
        assertEquals(before.surfaceColor, after.surfaceColor)
        assertEquals(before.blur, after.blur)
        assertEquals(before.foreground, after.foreground)
        val untouched = listOf(
            "ui/pdf/PdfViewerScreen.kt",
            "ui/pdf/ReaderMoreHost.kt",
            "ui/pdf/AnchoredNestedPopover.kt",
            "ui/viewer/ViewerScreen.kt",
            "ui/home/HomeScreen.kt",
            "ui/hub/GlassDock.kt",
            "ui/material/GlassAlertDialog.kt",
            "ui/material/GlassMaterial.kt",
        )
        val root = readerRoot()
        for (path in untouched) {
            val text = File(root, path).readText()
            assertFalse(path, text.contains("SoftGlass"))
            assertFalse(path, text.contains("softGlassPreview"))
        }
        val callers = File(root, "ui").walkTopDown()
            .filter { it.isFile && it.name.endsWith(".kt") && it.readText().contains("softGlassPreview(") }
            .map { it.name }
            .sorted()
            .toList()
        assertEquals(listOf("BlurComparisonPreview.kt", "SoftGlassPreview.kt"), callers)
    }

    @Test
    fun `H0 H1 H2 role hierarchy is deterministic`() {
        val panels = SoftGlassCandidate.entries.associateWith {
            SoftGlassComparison.resolve(it, SoftGlassRole.Panel, dark = false)
        }
        assertTrue(panels.getValue(SoftGlassCandidate.H1).logicalBlur < panels.getValue(SoftGlassCandidate.H0).logicalBlur)
        assertTrue(panels.getValue(SoftGlassCandidate.H0).logicalBlur < panels.getValue(SoftGlassCandidate.H2).logicalBlur)
        assertTrue(panels.getValue(SoftGlassCandidate.H1).tintAlpha < panels.getValue(SoftGlassCandidate.H0).tintAlpha)
        assertTrue(panels.getValue(SoftGlassCandidate.H0).tintAlpha < panels.getValue(SoftGlassCandidate.H2).tintAlpha)
        assertEquals(32.dp, panels.getValue(SoftGlassCandidate.H0).logicalBlur)
        assertEquals(26.dp, panels.getValue(SoftGlassCandidate.H1).logicalBlur)
        assertEquals(40.dp, panels.getValue(SoftGlassCandidate.H2).logicalBlur)
        assertEquals(0.13f, panels.getValue(SoftGlassCandidate.H0).tintAlpha, 0.0001f)
        assertEquals(0.09f, panels.getValue(SoftGlassCandidate.H1).tintAlpha, 0.0001f)
        assertEquals(0.17f, panels.getValue(SoftGlassCandidate.H2).tintAlpha, 0.0001f)
        for (candidate in SoftGlassCandidate.entries) {
            val panel = SoftGlassComparison.resolve(candidate, SoftGlassRole.Panel, dark = false)
            val control = SoftGlassComparison.resolve(candidate, SoftGlassRole.Control, dark = false)
            val again = SoftGlassComparison.resolve(candidate, SoftGlassRole.Control, dark = false)
            assertEquals(again, control)
            assertEquals(panel.logicalBlur.value - SoftGlassComparison.RoleBlurDeltaDp, control.logicalBlur.value, 0.001f)
            assertEquals(panel.tintAlpha - SoftGlassComparison.RoleTintDelta, control.tintAlpha, 0.0001f)
            assertEquals(panel.tint.copy(alpha = 1f), control.tint.copy(alpha = 1f))
            assertEquals(panel.designSaturation, control.designSaturation, 0f)
            assertEquals(panel.designBrightness, control.designBrightness, 0f)
            assertEquals(panel.designContrast, control.designContrast, 0f)
            assertEquals(panel.designSoftLight, control.designSoftLight, 0f)
            assertEquals(panel.refractionAmountPx, control.refractionAmountPx, 0f)
            assertEquals(panel.highlight, control.highlight)
            assertEquals(panel.shadowAlpha, control.shadowAlpha, 0f)
            assertEquals(Color.White, panel.tint.copy(alpha = 1f))
            assertEquals(0f, panel.centerRefractionPx, 0f)
            assertEquals(SoftGlassComparison.EdgeRefractionBandPx, panel.refractionHeightPx, 0f)
        }
        assertEquals(1.20f, panels.getValue(SoftGlassCandidate.H0).designSaturation, 0f)
        assertEquals(1.05f, panels.getValue(SoftGlassCandidate.H0).designBrightness, 0f)
        assertEquals(0.95f, panels.getValue(SoftGlassCandidate.H0).designContrast, 0f)
        assertEquals(0.13f, panels.getValue(SoftGlassCandidate.H0).designSoftLight, 0f)
        assertEquals(1f, panels.getValue(SoftGlassCandidate.H0).refractionAmountPx, 0f)
        assertEquals(0.28f, panels.getValue(SoftGlassCandidate.H0).highlight.topLeft, 0f)
        assertEquals(0.05f, panels.getValue(SoftGlassCandidate.H0).highlight.bottomRight, 0f)
        assertEquals(0.08f, panels.getValue(SoftGlassCandidate.H0).highlight.inner, 0f)
        assertEquals(0.06f, panels.getValue(SoftGlassCandidate.H0).shadowAlpha, 0f)
        assertEquals(1.25f, panels.getValue(SoftGlassCandidate.H1).designSaturation, 0f)
        assertEquals(0.8f, panels.getValue(SoftGlassCandidate.H1).refractionAmountPx, 0f)
        assertEquals(1.14f, panels.getValue(SoftGlassCandidate.H2).designSaturation, 0f)
        assertEquals(1.2f, panels.getValue(SoftGlassCandidate.H2).refractionAmountPx, 0f)
    }

    @Test
    fun `renderer mapping is explicit and linear`() {
        val panel = SoftGlassComparison.resolve(SoftGlassCandidate.H0, SoftGlassRole.Panel, dark = false)
        assertEquals(96f, SoftGlassComparison.rendererBlurPx(panel.logicalBlur, density = 3f), 0.001f)
        assertEquals(panel.logicalBlur.value * 2.75f, SoftGlassComparison.rendererBlurPx(panel.logicalBlur, 2.75f), 0.001f)
        assertEquals(panel.designSaturation, panel.rendererSaturation, 0f)
        assertEquals(panel.designContrast, panel.rendererContrast, 0f)
        assertEquals(panel.designBrightness - SoftGlassComparison.BrightnessIdentity, panel.rendererBrightness, 0.0001f)
        assertEquals(
            panel.designSoftLight * SoftGlassComparison.SoftLightToBrightness,
            panel.rendererSoftLightBrightness,
            0.0001f,
        )
        assertEquals(
            "H0 · blur 32dp/96px · tint .13 · sat 1.20 · bright 1.05 · contrast 0.95 · highlight .28 · refraction 1.0px",
            SoftGlassComparison.schemeLine(SoftGlassCandidate.H0, dark = false, density = 3f),
        )
        val summary = SoftGlassComparison.parameterSummary(SoftGlassCandidate.H0, dark = false, density = 3f)
        assertTrue(summary.contains("logicalBlur 32dp"))
        assertTrue(summary.contains("rendererBlur 96px"))
        assertTrue(summary.contains("px = dp × density 3.00"))
        assertTrue(summary.contains("control blur 29dp"))
        assertTrue(summary.contains("foreground 1.00"))
        assertTrue(summary.contains("painted"))
        assertTrue(summary.contains("center 0"))
    }

    @Test
    fun `dark candidate does not mutate the light candidate`() {
        val light = SoftGlassComparison.resolve(SoftGlassCandidate.H0, SoftGlassRole.Panel, dark = false)
        val dark = SoftGlassComparison.resolve(SoftGlassCandidate.H0, SoftGlassRole.Panel, dark = true)
        val lightAgain = SoftGlassComparison.resolve(SoftGlassCandidate.H0, SoftGlassRole.Panel, dark = false)
        assertEquals(light, lightAgain)
        assertTrue(dark.usesDarkRecipe)
        assertFalse(light.usesDarkRecipe)
        assertEquals(light.logicalBlur.value + SoftGlassComparison.DarkBlurExtraDp, dark.logicalBlur.value, 0.001f)
        assertEquals(0.18f, dark.tintAlpha, 0.0001f)
        assertEquals(1.14f, dark.designSaturation, 0f)
        assertEquals(0.91f, dark.designBrightness, 0f)
        assertEquals(0.96f, dark.designContrast, 0f)
        assertEquals(light.designSoftLight * SoftGlassComparison.DarkSoftLightScale, dark.designSoftLight, 0.0001f)
        assertEquals(Color(0xFF101418), dark.tint.copy(alpha = 1f))
        assertEquals(light.highlight, dark.highlight)
        assertEquals(light.refractionAmountPx, dark.refractionAmountPx, 0f)
        val darkControl = SoftGlassComparison.resolve(SoftGlassCandidate.H0, SoftGlassRole.Control, dark = true)
        assertEquals(dark.logicalBlur.value - SoftGlassComparison.RoleBlurDeltaDp, darkControl.logicalBlur.value, 0.001f)
        assertEquals(dark.tintAlpha - SoftGlassComparison.RoleTintDelta, darkControl.tintAlpha, 0.0001f)
        for (candidate in listOf(SoftGlassCandidate.H1, SoftGlassCandidate.H2)) {
            val day = SoftGlassComparison.resolve(candidate, SoftGlassRole.Panel, dark = false)
            val requested = SoftGlassComparison.resolve(candidate, SoftGlassRole.Panel, dark = true)
            assertFalse(requested.usesDarkRecipe)
            assertEquals(day, requested.copy(requestedDark = false))
        }
        assertEquals(
            "H0 Dark · blur 35dp/105px · tint .18 · sat 1.14 · bright 0.91 · contrast 0.96 · highlight .28 · refraction 1.0px",
            SoftGlassComparison.schemeLine(SoftGlassCandidate.H0, dark = true, density = 3f),
        )
        assertTrue(
            SoftGlassComparison.schemeLine(SoftGlassCandidate.H1, dark = true, density = 3f)
                .startsWith("H1 · 暗色未展开"),
        )
    }

    @Test
    fun `pressed response only scales and lifts highlight`() {
        val idle = SoftGlassComparison.resolve(SoftGlassCandidate.H0, SoftGlassRole.Panel, dark = false)
        val pressed = SoftGlassComparison.resolve(
            SoftGlassCandidate.H0,
            SoftGlassRole.Panel,
            dark = false,
            pressed = true,
        )
        assertEquals(1f, idle.pressedScale, 0f)
        assertTrue(pressed.pressedScale in 0.985f..0.99f)
        assertEquals(SoftGlassComparison.PressedScale, pressed.pressedScale, 0f)
        assertEquals(idle.tintAlpha, pressed.tintAlpha, 0f)
        assertEquals(idle.tint, pressed.tint)
        assertEquals(idle.logicalBlur, pressed.logicalBlur)
        assertEquals(idle.highlight.topLeft + SoftGlassComparison.PressedHighlightBoost, pressed.highlight.topLeft, 0.0001f)
        assertEquals(idle.highlight.bottomRight, pressed.highlight.bottomRight, 0f)
        assertEquals(idle.foregroundAlpha, pressed.foregroundAlpha, 0f)
    }

    @Test
    fun `preview renderer keeps blur ahead of tint and does not fake it`() {
        val renderer = source("ui/material/SoftGlassPreview.kt")
        val effects = renderer.substring(renderer.indexOf("effects = {"), renderer.indexOf("highlight = null"))
        val blurAt = effects.indexOf("blur(")
        val colorAt = effects.indexOf("colorControls(")
        val softAt = effects.indexOf("colorControls(", colorAt + 1)
        val lensAt = effects.indexOf("lens(")
        assertTrue(blurAt >= 0 && blurAt < colorAt)
        assertTrue(softAt > colorAt)
        assertTrue(lensAt > softAt)
        assertTrue(renderer.contains("highlight = null"))
        assertTrue(renderer.contains("rendererBlurPx("))
        assertFalse(renderer.contains("Highlight.Plain"))
        assertFalse(renderer.contains("vibrancy("))
        assertFalse(renderer.contains("androidx.compose.ui.draw.blur"))
        assertFalse(renderer.contains("Modifier.blur"))
        val preview = source("ui/material/BlurComparisonPreview.kt")
        assertTrue(preview.contains("return readerGradient(backdrop)"))
        assertTrue(preview.contains("GlassPreviewPreset"))
        assertTrue(preview.contains("柔光玻璃"))
        assertTrue(preview.contains("视觉检查"))
        assertTrue(preview.contains("背景文字不可辨认"))
        assertTrue(preview.contains("mutableStateOf(List(VisualChecks.size) { false })"))
        assertFalse(preview.contains("runtimeShaderEffect"))
        assertFalse(preview.contains("4.32.dp"))
        assertFalse(preview.contains("drawBackdrop(backdrop"))
        assertEquals(1, Regex("height\\(52\\.dp\\)").findAll(preview).count())
        assertEquals(1, Regex("height\\(112\\.dp\\)").findAll(preview).count())
        assertEquals(1, Regex("height\\(300\\.dp\\)").findAll(preview).count())
    }

    private fun source(relative: String): String = File(readerRoot(), relative).readText()

    private fun readerRoot(): File = listOf(
        File("src/main/java/com/m1racle/reader"),
        File("app/src/main/java/com/m1racle/reader"),
    ).first { it.isDirectory }
}
