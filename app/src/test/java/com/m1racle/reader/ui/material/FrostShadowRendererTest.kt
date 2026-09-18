package com.m1racle.reader.ui.material

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrostShadowRendererTest {
    @Test
    fun shadowUsesAnExplicitDrawNodeInsteadOfOnlyElevation() {
        for (shape in listOf(CircleShape, RoundedCornerShape(22.dp))) {
            val nodes = Modifier.frostShadow(
                shape, GlassMaterialEngine.resolve(GlassMaterialRole.Control, dark = false),
            ).foldIn(emptyList<Modifier.Node>()) { nodes, element ->
                nodes + (element as ModifierNodeElement<*>).create()
            }
            assertEquals("one shadow renderer, no stacked old path", 1, nodes.size)
            assertTrue("shadow must draw pixels instead of relying on elevation lighting", nodes.single() is DrawModifierNode)
        }
    }
}
