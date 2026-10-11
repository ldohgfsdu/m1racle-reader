package com.m1racle.reader.ui.material

import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class GlassOpticalPolicyTest {
    @Test
    fun `production uses promoted liquid authority`() {
        // User requested promotion of the settings liquid material on 2026-09-25.
        val expectedLight = mapOf(
            GlassMaterialRole.Control to 0.32f,
            GlassMaterialRole.SelectionLens to 0.10f,
            GlassMaterialRole.Menu to 0.60f,
            GlassMaterialRole.ExpandedMenu to 0.90f,
            GlassMaterialRole.SettingsCard to 0.60f,
            GlassMaterialRole.Dialog to 0.97f,
            GlassMaterialRole.ProgressiveChrome to 0.90f,
        )
        for ((role, alpha) in expectedLight) {
            val spec = GlassMaterialEngine.resolve(role, GlassEnvironment.READER_LIGHT)
            assertEquals("production light $role", alpha, spec.surfaceColor.alpha, 0.004f)
        }
        // Shared liquid role colors also apply in dark mode.
        val expectedDark = mapOf(
            GlassMaterialRole.Control to 0.22f,
            GlassMaterialRole.SelectionLens to 0.10f,
            GlassMaterialRole.Menu to 0.64f,
            GlassMaterialRole.ExpandedMenu to 0.90f,
            GlassMaterialRole.SettingsCard to 0.64f,
            GlassMaterialRole.Dialog to 0.97f,
            GlassMaterialRole.ProgressiveChrome to 0.90f,
        )
        for ((role, alpha) in expectedDark) {
            val spec = GlassMaterialEngine.resolve(role, GlassEnvironment.READER_DARK)
            assertEquals("production dark $role", alpha, spec.surfaceColor.alpha, 0.004f)
        }
        // Controls and readable panels retain distinct liquid blur radii.
        for (role in listOf(
            GlassMaterialRole.Control,
            GlassMaterialRole.SelectionLens,
            GlassMaterialRole.Menu,
            GlassMaterialRole.ExpandedMenu,
            GlassMaterialRole.SettingsCard,
        )) {
            assertEquals("production blur $role", when (role) {
                GlassMaterialRole.ExpandedMenu -> 24.dp
                GlassMaterialRole.SelectionLens -> 0.dp
                else -> 4.dp
            },
                GlassMaterialEngine.resolve(role, GlassEnvironment.READER_LIGHT).blur)
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
            GlassMaterialRole.SelectionLens to GlassOpticalFamily.CONTENT,
            GlassMaterialRole.Menu to GlassOpticalFamily.CONTENT,
            GlassMaterialRole.ExpandedMenu to GlassOpticalFamily.CONTENT,
            GlassMaterialRole.SettingsCard to GlassOpticalFamily.CONTENT,
            GlassMaterialRole.Dialog to GlassOpticalFamily.PANEL,
            GlassMaterialRole.Dock to GlassOpticalFamily.CONTENT,
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
            assertEquals(role == GlassMaterialRole.SelectionLens, full.chromaticAberration)
            assertEquals(1f, full.foreground.contentAlpha, 0f)
            if (family == GlassOpticalFamily.PANEL) assertFalse(full.lensEnabled)
        }
    }
}
