package com.m1racle.reader.ui.hub

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.m1racle.reader.ui.theme.M1racleReaderTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

/** Real composed pixels, including the capsule overlay; semantics alone miss blank icons. */
@RunWith(AndroidJUnit4::class)
class GlassDockPixelsInstrumentedTest {
    @get:Rule val rule = createComposeRule()

    @Test fun lightDockKeepsAllFourIconsSharp() = assertIconPixels(dark = false)

    @Test fun darkDockKeepsAllFourIconsSharp() = assertIconPixels(dark = true)

    @Test
    fun selectedAndInactiveTabsKeepBlurredGlassDuringHomePress() {
        assertEquals("com.m1racle.reader.qa", InstrumentationRegistry.getInstrumentation().targetContext.packageName)
        rule.setContent {
            M1racleReaderTheme {
                Box(Modifier.fillMaxSize().testTag("dock-pixel-scene")) {
                    HubDockMotionProvider(HubDestination.HOME) {
                        HubGlassShell {
                            Canvas(Modifier.fillMaxSize()) {
                                // Dense stripes expose a raw-page sampling leak. The shell
                                // should blur these while keeping the icon source sharp.
                                drawRect(Color.White)
                                val stripe = density
                                var x = 0f
                                while (x < size.width) {
                                    drawRect(Color.Black, Offset(x, 0f), Size(stripe, size.height))
                                    x += stripe * 2
                                }
                            }
                        }
                        GlassDock(onSelect = {})
                    }
                }
            }
        }
        rule.waitForIdle()
        assertNoRawStripes("idle")
        val home = rule.onAllNodes(isSelectable())[0]
        home.performTouchInput { down(center) }
        try {
            rule.mainClock.advanceTimeBy(800)
            rule.waitForIdle()
            assertNoRawStripes("holding Home")
        } finally {
            home.performTouchInput { cancel() }
        }
        rule.waitForIdle()
        assertNoRawStripes("released")
    }

    private fun assertNoRawStripes(phase: String) {
        val scene = rule.onNodeWithTag("dock-pixel-scene")
        val origin = scene.fetchSemanticsNode().boundsInRoot.topLeft
        val pixels = scene.captureToImage().toPixelMap()
        rule.onAllNodes(isSelectable()).fetchSemanticsNodes().forEachIndexed { index, tab ->
            val bounds = tab.boundsInRoot.translate(-origin)
            // Left of the centered icon, at the bar's vertical center: this
            // region contains glass only, even while the icons expand.
            val left = (bounds.left + bounds.width * 0.10f).roundToInt()
            val right = (bounds.left + bounds.width * 0.20f).roundToInt()
            val top = (bounds.center.y - bounds.height * 0.03f).roundToInt()
            val bottom = (bounds.center.y + bounds.height * 0.03f).roundToInt()
            var minLight = 1f
            var maxLight = 0f
            for (y in top until bottom) for (x in left until right) {
                val light = pixels[x, y].luminance()
                minLight = minOf(minLight, light)
                maxLight = maxOf(maxLight, light)
            }
            assertTrue("tab $index leaks raw background at $phase: ${maxLight - minLight}", maxLight - minLight < 0.20f)
        }
    }

    private fun assertIconPixels(dark: Boolean) {
        assertEquals(
            "com.m1racle.reader.qa",
            InstrumentationRegistry.getInstrumentation().targetContext.packageName,
        )
        rule.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                M1racleReaderTheme(darkTheme = dark) {
                    Box(
                        Modifier.fillMaxSize().background(if (dark) Color.Black else Color.White)
                            .testTag("dock-pixel-scene"),
                    ) {
                        HubDockMotionProvider(HubDestination.HOME) {
                            GlassDock(onSelect = {})
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        val scene = rule.onNodeWithTag("dock-pixel-scene")
        val origin = scene.fetchSemanticsNode().boundsInRoot.topLeft
        // Capture the scene, not individual Icon nodes: a later glass layer can cover them.
        val pixels = scene.captureToImage().toPixelMap()
        HubDestination.entries.forEach { destination ->
            val bounds = rule.onNodeWithTag("dock-icon-${destination.name}", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot.translate(-origin)
            val left = bounds.left.roundToInt()
            val top = bounds.top.roundToInt()
            val right = bounds.right.roundToInt()
            val bottom = bounds.bottom.roundToInt()
            var ink = 0
            var background = 0
            for (y in top until bottom) for (x in left until right) {
                val light = pixels[x, y].luminance()
                // The selected dark icon is viewed through a 20% dark surface;
                // require contrasting strokes, not the raw uncomposited tint.
                if (if (dark) light > 0.35f else light < 0.25f) ink++
                if (if (dark) light < 0.25f else light > 0.6f) background++
            }
            val area = (right - left) * (bottom - top)
            assertTrue("$destination must contain visible icon strokes, dark=$dark", ink > area * 0.05f)
            assertTrue("$destination must retain sharp contrast, dark=$dark", background > area * 0.15f)
        }
    }
}
