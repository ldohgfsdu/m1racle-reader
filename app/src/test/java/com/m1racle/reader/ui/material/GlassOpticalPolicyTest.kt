package com.m1racle.reader.ui.material

import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class GlassOpticalPolicyTest {
    @Test
    fun `comparison candidates stay local and respect unavailable sampling`() {
        val role = GlassMaterialRole.Capsule
        val original = GlassMaterialEngine.resolve(role, GlassEnvironment.READER_LIGHT)
        for (preset in GlassPreviewPreset.entries) {
            for (env in GlassEnvironment.entries) {
                val plate = GlassMaterialEngine.resolve(role, env,
                    GlassCapability.SOLID_FALLBACK, previewPreset = preset)
                assertEquals(0.dp, plate.blur)
                assertTrue(plate.surfaceColor.alpha >= .96f)
                assertFalse(plate.lensEnabled)
            }
        }
        val stronger = GlassMaterialEngine.resolve(role, GlassEnvironment.READER_LIGHT,
            previewPreset = GlassPreviewPreset.Blur40)
        assertEquals(40.dp, stronger.blur)
        assertEquals(.45f, stronger.surfaceColor.alpha, .005f)
        assertEquals(1f, stronger.foreground.contentAlpha, 0f)
        val after = GlassMaterialEngine.resolve(role, GlassEnvironment.READER_LIGHT)
        assertEquals(original.surfaceColor, after.surfaceColor)
        assertEquals(original.blur, after.blur)
        assertEquals(original.foreground, after.foreground)
        assertEquals(original.lensEnabled, after.lensEnabled)
    }

    @Test
    fun `light tint candidates vary only tint at frozen blur`() {
        // R36.2 LIGHT TINT batch: comparison may move light tint alpha and nothing else.
        val role = GlassMaterialRole.Capsule
        val production = GlassMaterialEngine.resolve(role, GlassEnvironment.READER_LIGHT)
        val candidates = mapOf(
            GlassPreviewPreset.Tint86 to .86f,
            GlassPreviewPreset.Tint80 to .80f,
            GlassPreviewPreset.Tint75 to .75f,
            GlassPreviewPreset.Tint70 to .70f,
            GlassPreviewPreset.Tint65 to .65f,
            GlassPreviewPreset.Tint60 to .60f,
            GlassPreviewPreset.Tint55 to .55f,
            GlassPreviewPreset.Tint50 to .50f,
            GlassPreviewPreset.Tint45 to .45f,
            GlassPreviewPreset.Tint35 to .35f,
        )
        for ((preset, tint) in candidates) {
            val spec = GlassMaterialEngine.resolve(
                role, GlassEnvironment.READER_LIGHT, previewPreset = preset)
            assertEquals("blur frozen for $preset", 26.dp, spec.blur)
            assertEquals("tint for $preset", tint, spec.surfaceColor.alpha, .005f)
            assertEquals("tint rgb for $preset",
                production.surfaceColor.copy(alpha = 1f), spec.surfaceColor.copy(alpha = 1f))
            assertEquals(production.shadow, spec.shadow)
            assertEquals(production.shadowColor, spec.shadowColor)
            assertEquals(production.highlightWidth, spec.highlightWidth)
            assertEquals(production.highlightColor, spec.highlightColor)
            assertEquals(production.vibrancy, spec.vibrancy)
            assertEquals(production.lensEnabled, spec.lensEnabled)
            assertEquals(production.lensHeight, spec.lensHeight)
            assertEquals(production.lensAmount, spec.lensAmount)
            assertEquals(production.family, spec.family)
            assertEquals(production.foreground, spec.foreground)
            assertEquals(production.chromaticAberration, spec.chromaticAberration)
            assertEquals(production.brightness, spec.brightness, 0f)
            assertEquals(production.saturation, spec.saturation, 0f)
        }
        // Production path (no preset) is unaffected by candidate evaluation.
        val productionAgain =
            GlassMaterialEngine.resolve(role, GlassEnvironment.READER_LIGHT)
        assertEquals(production.surfaceColor, productionAgain.surfaceColor)
        assertEquals(production.blur, productionAgain.blur)
    }

    @Test
    fun `production light recipe uses frozen authority`() {
        // LIGHT_TINT_PRODUCTION_AUTHORITY = FROZEN_086. Change these numbers
        // only as an explicit authority promotion after device/human approval
        // of a candidate (.45/.55/.65/.75 stay preview-only until then).
        val expectedLight = mapOf(
            GlassMaterialRole.Control to 0.86f,
            GlassMaterialRole.Capsule to 0.86f,
            GlassMaterialRole.Menu to 0.86f,
            GlassMaterialRole.ExpandedMenu to 0.90f,
            GlassMaterialRole.SettingsCard to 0.90f,
            GlassMaterialRole.Dialog to 0.97f,
            GlassMaterialRole.ProgressiveChrome to 0.62f,
        )
        for ((role, alpha) in expectedLight) {
            val spec = GlassMaterialEngine.resolve(role, GlassEnvironment.READER_LIGHT)
            assertEquals("production light $role", alpha, spec.surfaceColor.alpha, 0.004f)
        }
        // Current frozen dark authority (DARK batch audits from here, changes nothing yet).
        val expectedDark = mapOf(
            GlassMaterialRole.Control to 0.80f,
            GlassMaterialRole.Capsule to 0.80f,
            GlassMaterialRole.Menu to 0.80f,
            GlassMaterialRole.ExpandedMenu to 0.86f,
            GlassMaterialRole.SettingsCard to 0.86f,
            GlassMaterialRole.Dialog to 0.97f,
            GlassMaterialRole.ProgressiveChrome to 0.62f,
        )
        for ((role, alpha) in expectedDark) {
            val spec = GlassMaterialEngine.resolve(role, GlassEnvironment.READER_DARK)
            assertEquals("production dark $role", alpha, spec.surfaceColor.alpha, 0.004f)
        }
        // Frozen production blur (18dp) for the reader overlay family.
        for (role in listOf(
            GlassMaterialRole.Control,
            GlassMaterialRole.Capsule,
            GlassMaterialRole.Menu,
            GlassMaterialRole.ExpandedMenu,
            GlassMaterialRole.SettingsCard,
        )) {
            assertEquals("production blur $role", 18.dp,
                GlassMaterialEngine.resolve(role, GlassEnvironment.READER_LIGHT).blur)
        }
    }

    @Test
    fun `dark candidates keep foreground policy and frozen channels`() {
        // R36.2 dark families: uniform delta on dark alpha only. Role delta
        // (+.06 panel over control) must survive; frozen blur untouched.
        val deltas = mapOf(
            GlassPreviewPreset.DarkD1 to -0.06f,
            GlassPreviewPreset.DarkD2 to -0.12f,
            GlassPreviewPreset.DarkD3 to 0.06f,
        )
        val roles = listOf(
            GlassMaterialRole.Control,
            GlassMaterialRole.Capsule,
            GlassMaterialRole.Menu,
            GlassMaterialRole.ExpandedMenu,
            GlassMaterialRole.SettingsCard,
            GlassMaterialRole.Dialog,
            GlassMaterialRole.Dock,
            GlassMaterialRole.ProgressiveChrome,
            GlassMaterialRole.PreviewCapsule,
        )
        for ((preset, delta) in deltas) {
            for (role in roles) {
                val production = GlassMaterialEngine.resolve(role, GlassEnvironment.READER_DARK)
                val spec = GlassMaterialEngine.resolve(
                    role, GlassEnvironment.READER_DARK, previewPreset = preset)
                val expectedDark =
                    (production.surfaceColor.alpha + delta).coerceIn(0f, 0.98f)
                assertEquals("dark tint $preset/$role", expectedDark, spec.surfaceColor.alpha, .005f)
                assertEquals("dark tint rgb $preset/$role",
                    production.surfaceColor.copy(alpha = 1f), spec.surfaceColor.copy(alpha = 1f))
                assertEquals(production.blur, spec.blur)
                assertEquals(production.shadow, spec.shadow)
                assertEquals(production.shadowColor, spec.shadowColor)
                assertEquals(production.highlightWidth, spec.highlightWidth)
                assertEquals(production.highlightColor, spec.highlightColor)
                assertEquals(production.vibrancy, spec.vibrancy)
                assertEquals(production.lensEnabled, spec.lensEnabled)
                assertEquals(production.lensHeight, spec.lensHeight)
                assertEquals(production.lensAmount, spec.lensAmount)
                assertEquals(production.family, spec.family)
                assertEquals(production.foreground, spec.foreground)
                assertEquals(production.chromaticAberration, spec.chromaticAberration)
                assertEquals(production.brightness, spec.brightness, 0f)
                assertEquals(production.saturation, spec.saturation, 0f)
                // Light side completely untouched by dark candidates.
                val lightProd = GlassMaterialEngine.resolve(role, GlassEnvironment.READER_LIGHT)
                val lightSpec = GlassMaterialEngine.resolve(
                    role, GlassEnvironment.READER_LIGHT, previewPreset = preset)
                assertEquals("light untouched $preset/$role", lightProd.surfaceColor, lightSpec.surfaceColor)
                assertEquals(lightProd.blur, lightSpec.blur)
            }
            // Role hierarchy delta survives every family.
            val control = GlassMaterialEngine.resolve(
                GlassMaterialRole.Control, GlassEnvironment.READER_DARK, previewPreset = preset)
            val panel = GlassMaterialEngine.resolve(
                GlassMaterialRole.SettingsCard, GlassEnvironment.READER_DARK, previewPreset = preset)
            assertEquals("role delta $preset", 0.06f,
                panel.surfaceColor.alpha - control.surfaceColor.alpha, 0.006f)
        }
    }

    @Test
    fun `renderers honor effective capability and share progressive endpoints`() {
        val root = listOf(File("src/main/java/com/m1racle/reader/ui"),
            File("app/src/main/java/com/m1racle/reader/ui")).first { it.isDirectory }
        val material = File(root, "material/GlassMaterial.kt").readText()
        val modifier = material.substring(material.indexOf("fun Modifier.glassMaterial("))
        assertTrue(modifier.indexOf("GlassCapabilityProbe.forSurface(") <
            modifier.indexOf("GlassMaterialEngine.resolve("))
        assertTrue(modifier.contains("sampleBackdrop = sampleBackdrop"))
        assertTrue(modifier.contains("supportsLens = supportsGlassLens(shape)"))
        val gradient = File(root, "viewer/ReaderGradient.kt").readText()
        assertTrue(gradient.contains("GlassCapability.SOLID_FALLBACK) Modifier"))
        assertTrue(gradient.contains("if (spec.vibrancy) vibrancy()"))
        val preview = File(root, "material/BlurComparisonPreview.kt").readText()
        assertTrue(preview.contains("return readerGradient(backdrop)"))
        assertFalse(preview.contains("runtimeShaderEffect"))
    }

    @Test
    fun `window and unsupported shape select capability before resolving recipe`() {
        for (device in GlassCapability.entries) {
            assertEquals(GlassCapability.SOLID_FALLBACK,
                GlassCapabilityProbe.forSurface(device, false, true, true))
            assertEquals(GlassCapability.SOLID_FALLBACK,
                GlassCapabilityProbe.forSurface(device, true, false, true))
        }
        assertEquals(GlassCapability.BLUR_ONLY, GlassCapabilityProbe.forSurface(
            GlassCapability.FULL_OPTICAL, true, true, false))
        assertEquals(GlassCapability.FULL_OPTICAL, GlassCapabilityProbe.forSurface(
            GlassCapability.FULL_OPTICAL, true, true, true))
        assertEquals(GlassCapability.BLUR_ONLY, GlassCapabilityProbe.forSurface(
            GlassCapability.BLUR_ONLY, true, true, true))
    }

    @Test
    fun `unavailable sampling isolates text instead of reusing optical tint`() {
        for (role in GlassMaterialRole.entries) for (env in GlassEnvironment.entries) {
            val optical = GlassMaterialEngine.resolve(role, env)
            val plate = GlassMaterialEngine.resolve(role, env, GlassCapability.SOLID_FALLBACK)
            assertEquals(GlassOpticalFamily.FALLBACK, plate.family)
            assertTrue("$role/$env must isolate text without blur", plate.surfaceColor.alpha >= .96f)
            assertTrue(plate.surfaceColor.alpha >= optical.surfaceColor.alpha)
            assertEquals(0.dp, plate.blur)
            assertFalse(plate.lensEnabled)
            assertEquals(0.dp, plate.lensHeight)
            assertEquals(0.dp, plate.lensAmount)
            assertFalse(plate.vibrancy)
            assertFalse(plate.depthEffect)
            assertFalse(plate.chromaticAberration)
            assertEquals(1f, plate.foreground.contentAlpha, 0f)
        }
    }

    @Test
    fun `families preserve role semantics across both environments and themes`() {
        val expected = mapOf(
            GlassMaterialRole.Control to GlassOpticalFamily.INTERACTIVE,
            GlassMaterialRole.Capsule to GlassOpticalFamily.CONTENT,
            GlassMaterialRole.Menu to GlassOpticalFamily.CONTENT,
            GlassMaterialRole.ExpandedMenu to GlassOpticalFamily.CONTENT,
            GlassMaterialRole.SettingsCard to GlassOpticalFamily.PANEL,
            GlassMaterialRole.Dialog to GlassOpticalFamily.PANEL,
            GlassMaterialRole.Dock to GlassOpticalFamily.FALLBACK,
            GlassMaterialRole.ProgressiveChrome to GlassOpticalFamily.CONTENT,
        )
        for ((role, family) in expected) for (env in GlassEnvironment.entries) {
            val full = GlassMaterialEngine.resolve(role, env)
            val blur = GlassMaterialEngine.resolve(role, env, GlassCapability.BLUR_ONLY)
            assertEquals(family, full.family)
            assertEquals(family, blur.family)
            assertEquals(full.blur, blur.blur)
            assertEquals(full.surfaceColor, blur.surfaceColor)
            assertFalse(blur.lensEnabled)
            assertEquals(0.dp, blur.lensAmount)
            assertFalse(full.chromaticAberration)
            assertEquals(1f, full.foreground.contentAlpha, 0f)
            if (family == GlassOpticalFamily.PANEL) assertFalse(full.lensEnabled)
        }
    }
}
