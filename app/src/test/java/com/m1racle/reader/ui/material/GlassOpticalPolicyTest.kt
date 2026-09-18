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
