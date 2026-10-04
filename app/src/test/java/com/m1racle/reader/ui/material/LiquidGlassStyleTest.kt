package com.m1racle.reader.ui.material

import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test

class LiquidGlassStyleTest {
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
            assertEquals(0.10f, idle.surfaceColor.alpha, 0.004f)
            assertEquals(0.dp, idle.blur)
            assertEquals(0.dp, idle.lensHeight)
            assertEquals(0.dp, idle.lensAmount)
            assertEquals(0.dp, idle.shadow)
            assertEquals(0f, idle.highlightColor.alpha, 0f)
            for (state in listOf(GlassInteractionState.PRESSED, GlassInteractionState.DRAGGING)) {
                val pressed = GlassMaterialEngine.resolve(GlassMaterialRole.SelectionLens, env,
                    interaction = state)
                assertEquals(0f, pressed.surfaceColor.alpha, 0f)
                assertEquals(10.dp, pressed.lensHeight)
                assertEquals(14.dp, pressed.lensAmount)
                assertEquals(1f, pressed.highlightColor.alpha, 0f)
                assertEquals(8.dp, pressed.innerShadow)
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
