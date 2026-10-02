package com.m1racle.reader.ui.material

import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test

class LiquidGlassStyleTest {
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
