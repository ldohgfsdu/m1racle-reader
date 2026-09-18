package com.m1racle.reader.ui.pdf

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.m1racle.reader.ui.material.GlassMaterialRole
import com.m1racle.reader.ui.theme.M1racleReaderTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class PdfChromeVisibilityTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `capsule fade buffer includes the shadow without moving its content`() {
        val visible = mutableStateOf(false)
        var control = Rect.Zero
        rule.mainClock.autoAdvance = false
        rule.setContent {
            M1racleReaderTheme {
                Box(Modifier.size(360.dp, 300.dp)) {
                    PdfChromeVisibility(visible.value, GlassMaterialRole.Capsule,
                        fadeIn(tween(180)), fadeOut(tween(180)),
                        modifier = Modifier.offset(50.dp, 50.dp)) {
                        Box(Modifier.size(200.dp, 44.dp).onGloballyPositioned {
                            control = it.boundsInRoot()
                        })
                    }
                }
            }
        }
        rule.runOnIdle { visible.value = true }
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(80)
        rule.waitForIdle()
        val scale = rule.density.density
        val requiredOutset = 8f * scale // 6dp blur plus 2dp downward offset.
        fun assertGeometry() {
            // Clipped boundsInRoot can match the wrapper footprint and hide the
            // very overflow the fade buffer must keep. Use unclipped layout size.
            val bufferDp = rule.onNodeWithTag("pdf-chrome-fade-buffer").getUnclippedBoundsInRoot()
            val buffer = with(rule.density) {
                Rect(bufferDp.left.toPx(), bufferDp.top.toPx(), bufferDp.right.toPx(), bufferDp.bottom.toPx())
            }
            assertEquals(50f * scale, control.left, 1f)
            assertEquals(50f * scale, control.top, 1f)
            assertEquals(200f * scale, control.width, 1f)
            assertEquals(44f * scale, control.height, 1f)
            assertTrue(
                "fade layer must retain left shadow: control=$control buffer=$buffer",
                control.left - buffer.left >= requiredOutset,
            )
            assertTrue("fade layer must retain right shadow: control=$control buffer=$buffer", buffer.right - control.right >= requiredOutset)
            assertTrue("fade layer must retain top shadow: control=$control buffer=$buffer", control.top - buffer.top >= requiredOutset)
            assertTrue("fade layer must retain bottom shadow: control=$control buffer=$buffer", buffer.bottom - control.bottom >= requiredOutset)
        }
        assertGeometry()
        rule.mainClock.advanceTimeBy(200)
        rule.waitForIdle()
        assertGeometry()
        rule.runOnIdle { visible.value = false }
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(80)
        rule.waitForIdle()
        assertGeometry()
        rule.mainClock.advanceTimeBy(200)
        rule.waitForIdle() // Fully hidden state must also measure without negative dimensions.
        rule.onNodeWithTag("pdf-chrome-fade-buffer").assertDoesNotExist()
    }

    @Test fun `full viewport menu keeps the same content constraints`() {
        var content = Rect.Zero
        rule.setContent {
            M1racleReaderTheme {
                Box(Modifier.size(320.dp, 260.dp)) {
                    PdfChromeVisibility(true, GlassMaterialRole.Menu,
                        fadeIn(tween(180)), fadeOut(tween(180))) {
                        Box(Modifier.fillMaxSize().onGloballyPositioned { content = it.boundsInRoot() })
                    }
                }
            }
        }
        rule.waitForIdle()
        val scale = rule.density.density
        assertEquals(0f, content.left, 1f)
        assertEquals(0f, content.top, 1f)
        assertEquals(320f * scale, content.width, 1f)
        assertEquals(260f * scale, content.height, 1f)
    }
}
