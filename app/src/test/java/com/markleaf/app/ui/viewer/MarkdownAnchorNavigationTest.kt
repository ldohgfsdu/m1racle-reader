package com.markleaf.app.ui.viewer

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownAnchorNavigationTest {

    @Test
    fun nearbyTargetsAnimate_butFarTargetsJumpDirectly() {
        assertEquals(AnchorJumpMode.Animated, anchorJumpMode(10, 14))
        assertEquals(AnchorJumpMode.Animated, anchorJumpMode(14, 10))
        assertEquals(AnchorJumpMode.Direct, anchorJumpMode(2, 40))
        assertEquals(AnchorJumpMode.Direct, anchorJumpMode(40, 2))
    }
}
