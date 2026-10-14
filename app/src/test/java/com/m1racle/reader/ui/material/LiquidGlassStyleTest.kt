package com.m1racle.reader.ui.material

import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test

class LiquidGlassStyleTest {
    @Test
    fun `visible dock is the sole material processed before lens refraction`() {
        for (dark in listOf(false, true)) {
            val env = GlassEnvironment.app(dark)
            val dock = GlassMaterialEngine.resolve(GlassMaterialRole.Dock, env)
            val held = GlassMaterialEngine.resolve(GlassMaterialRole.Dock, env,
                interaction = GlassInteractionState.PRESSED)
            assertEquals(dock.surfaceColor, held.surfaceColor)
            assertEquals(dock.blur, held.blur)
            assertEquals(8.dp, dock.lensHeight)
            assertEquals(24.dp, dock.lensAmount)
            assertEquals(dock.lensHeight, held.lensHeight)
            assertEquals(dock.lensAmount, held.lensAmount)
            assertEquals(dock.highlightColor, held.highlightColor)
            assertTrue(dock.vibrancy)
            assertFalse(GlassMaterialEngine.resolve(GlassMaterialRole.SelectionLens, env).vibrancy)
            assertFalse(GlassMaterialRole.entries.any { it.name == "DockLensContent" })
        }
    }

    @Test
    fun `dock preserves pdf optics and transmission with reference specific tint and contour`() {
        for (dark in listOf(false, true)) {
            val env = GlassEnvironment.app(dark)
            val dock = GlassMaterialEngine.resolve(GlassMaterialRole.Dock, env)
            val selection = GlassMaterialEngine.resolve(GlassMaterialRole.SelectionLens, env)
            val control = GlassMaterialEngine.resolve(GlassMaterialRole.Control, env)
            val capsule = GlassMaterialEngine.resolve(GlassMaterialRole.Capsule, env)
            assertEquals(control.blur, dock.blur)
            assertEquals(control.surfaceColor.alpha, dock.surfaceColor.alpha, 0f)
            assertEquals(if (dark) androidx.compose.ui.graphics.Color(0xFF707070) else androidx.compose.ui.graphics.Color.White,
                dock.surfaceColor.copy(alpha = 1f))
            assertEquals(control.lensHeight, dock.lensHeight)
            assertEquals(control.lensAmount, dock.lensAmount)
            assertEquals(if (dark) 0.60f else 0.90f, dock.highlightColor.alpha, 0.004f)
            assertEquals(control.highlightWidth, dock.highlightWidth)
            assertEquals(control.shadow, dock.shadow)
            assertEquals(control.shadowColor, dock.shadowColor)
            assertEquals(control.vibrancy, dock.vibrancy)
            assertEquals(0.dp, selection.blur)
            assertEquals(4.dp, control.blur)
            assertEquals(control.blur, capsule.blur)
            assertEquals(control.surfaceColor, capsule.surfaceColor)
            assertEquals(if (dark) 0.22f else 0.32f, control.surfaceColor.alpha, 1f / 255f)
            for (state in GlassInteractionState.entries) {
                assertEquals(dock.blur, GlassMaterialEngine.resolve(
                    GlassMaterialRole.Dock, env, interaction = state).blur)
                assertEquals(0.dp, GlassMaterialEngine.resolve(
                    GlassMaterialRole.SelectionLens, env, interaction = state).blur)
            }
        }
    }

    @Test
    fun `pdf capsule shares control optics without becoming a selection lens`() {
        for (env in GlassEnvironment.entries) {
            for (state in GlassInteractionState.entries) {
                val capsule = GlassMaterialEngine.resolve(GlassMaterialRole.Capsule, env,
                    interaction = state)
                val control = GlassMaterialEngine.resolve(GlassMaterialRole.Control, env,
                    interaction = state)
                assertEquals(control.blur, capsule.blur)
                assertEquals(control.surfaceColor, capsule.surfaceColor)
                assertEquals(control.lensHeight, capsule.lensHeight)
                assertEquals(control.lensAmount, capsule.lensAmount)
                assertEquals(control.highlightColor, capsule.highlightColor)
                assertEquals(control.shadow, capsule.shadow)
                assertFalse(capsule.chromaticAberration)
                assertTrue(capsule.highlightColor.alpha > 0f)
                assertTrue(capsule.lensAmount > 0.dp)
            }
        }
    }

    @Test
    fun `selection is quiet at rest and becomes a clear optical lens during press or drag`() {
        for (dark in listOf(false, true)) {
            val env = GlassEnvironment.app(dark)
            val idle = GlassMaterialEngine.resolve(GlassMaterialRole.SelectionLens, env)
            assertEquals(0.06f, idle.surfaceColor.alpha, 0.004f)
            assertEquals(0.dp, idle.blur)
            assertEquals(0.dp, idle.lensHeight)
            assertEquals(0.dp, idle.lensAmount)
            assertEquals(0.dp, idle.shadow)
            assertEquals(0f, idle.highlightColor.alpha, 0f)
            for (state in listOf(GlassInteractionState.PRESSED, GlassInteractionState.DRAGGING)) {
                val pressed = GlassMaterialEngine.resolve(GlassMaterialRole.SelectionLens, env,
                    interaction = state)
                assertEquals(0f, pressed.surfaceColor.alpha, 0f)
                assertEquals(4.dp, pressed.lensHeight)
                assertEquals(6.dp, pressed.lensAmount)
                assertEquals(1f, pressed.highlightColor.alpha, 0f)
                assertEquals(3.dp, pressed.innerShadow)
                assertEquals(1f, pressed.scale, 0f) // pickup has one owner in GlassDock
                for (capability in listOf(GlassCapability.BLUR_ONLY, GlassCapability.SOLID_FALLBACK)) {
                    val limited = GlassMaterialEngine.resolve(GlassMaterialRole.SelectionLens, env,
                        capability, state)
                    assertFalse(limited.lensEnabled)
                    assertFalse(limited.chromaticAberration)
                    assertEquals(0.dp, limited.lensAmount)
                    assertEquals(1f, limited.foreground.contentAlpha, 0f)
                }
            }
        }
    }
    @Test
    fun `dock pickup has a broad optical rim without changing compact segmented lenses`() {
        for (dark in listOf(false, true)) {
            val env = GlassEnvironment.app(dark)
            val idle = GlassMaterialEngine.resolve(GlassMaterialRole.DockLens, env)
            assertEquals(0.dp, idle.blur)
            assertEquals(0.dp, idle.lensHeight)
            assertEquals(0f, idle.highlightColor.alpha, 0f)
            val held = GlassMaterialEngine.resolve(GlassMaterialRole.DockLens, env,
                interaction = GlassInteractionState.PRESSED)
            assertEquals(16.dp, held.lensHeight)
            assertEquals(8.dp, held.lensAmount)
            assertEquals(4.dp, held.innerShadow)
            assertEquals(0f, held.surfaceColor.alpha, 0f)
            assertFalse(held.vibrancy)
            assertTrue(held.chromaticAberration)
        }
    }

    @Test
    fun `dock dispersion is sub dp at the maximum bend in either stationary or dragging state`() {
        for (dark in listOf(false, true)) {
            val env = GlassEnvironment.app(dark)
            for (state in listOf(GlassInteractionState.PRESSED, GlassInteractionState.DRAGGING)) {
                val lens = GlassMaterialEngine.resolve(GlassMaterialRole.DockLens, env, interaction = state)
                assertTrue(lens.chromaticAberrationStrength > 0f)
                assertTrue("spectral separation must stay below one dp",
                    2f * lens.lensAmount.value * lens.chromaticAberrationStrength < 1f)
            }
            val limited = GlassMaterialEngine.resolve(GlassMaterialRole.DockLens, env,
                GlassCapability.BLUR_ONLY, GlassInteractionState.PRESSED)
            assertEquals(0f, limited.chromaticAberrationStrength, 0f)
        }
    }

    @Test
    fun `production liquid controls and panels retain distinct optical roles in both themes`() {
        for (dark in listOf(false, true)) {
            val control = liquidGlassStyle(GlassMaterialRole.Control, dark)
            val panel = liquidGlassStyle(GlassMaterialRole.ExpandedMenu, dark)
            assertEquals(4.dp, control.blur)
            assertEquals(8.dp, control.lensHeight)
            assertEquals(24.dp, control.lensAmount)
            assertEquals(24.dp, panel.blur)
            assertEquals(0.dp, panel.lensHeight)
            assertEquals(0.dp, panel.lensAmount)
            assertTrue(panel.tint.alpha > control.tint.alpha)
            assertEquals(0.90f, panel.tint.alpha, 1f / 255f)
            assertTrue(control.shadowAlpha > 0f)
            assertTrue(panel.shadowAlpha > 0f)
        }
        val light = liquidGlassStyle(GlassMaterialRole.Control, false)
        val dark = liquidGlassStyle(GlassMaterialRole.Control, true)
        assertNotEquals(light.tint, dark.tint)
        assertEquals(light.blur, dark.blur)
        assertEquals(light.lensAmount, dark.lensAmount)
        assertEquals(light, liquidGlassStyle(GlassMaterialRole.Control, false))
    }

}
