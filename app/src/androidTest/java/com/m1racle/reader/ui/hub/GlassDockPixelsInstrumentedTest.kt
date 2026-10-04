package com.m1racle.reader.ui.hub

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.m1racle.reader.ui.theme.M1racleReaderTheme
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.m1racle.reader.ui.material.GlassBackdropHost
import com.m1racle.reader.ui.material.GlassBackdropSource
import com.m1racle.reader.ui.material.LocalGlassBackdropHost
import com.m1racle.reader.ui.material.GlassCapability
import com.m1racle.reader.ui.material.LocalGlassCapability
import androidx.compose.runtime.mutableStateOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real composed pixels, including the capsule overlay; semantics alone miss blank icons. */
@RunWith(AndroidJUnit4::class)
class GlassDockPixelsInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun lightDockKeepsAllFourIconsSharp() = assertIconPixels(dark = false)

    @Test fun darkDockKeepsAllFourIconsSharp() = assertIconPixels(dark = true)

    @Test fun lightDockTransmitsPageColorsThroughGlass() = assertPageColorTransmission(dark = false)

    @Test fun darkDockTransmitsPageColorsThroughGlass() = assertPageColorTransmission(dark = true)

    @Test fun inactiveTabShowsBlueLensOnPressAndCommitsOnlyOnRelease() {
        val selected = mutableStateOf(HubDestination.HOME)
        val requests = mutableListOf<HubDestination>()
        rule.setContent {
            M1racleReaderTheme {
                val page = rememberLayerBackdrop()
                CompositionLocalProvider(LocalGlassBackdropHost provides GlassBackdropHost(page, page)) {
                    Box(Modifier.fillMaxSize().testTag("dock-pixel-scene")) {
                        GlassBackdropSource(page, Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxSize().background(Color.White))
                        }
                        HubDockMotionProvider(selected.value) {
                            GlassDock(onSelect = { requests.add(it); selected.value = it }, onImportDocument = {})
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        val lens = rule.onNodeWithTag("dock-selection")
        val resting = lens.fetchSemanticsNode().boundsInRoot
        val library = rule.onAllNodes(isSelectable())[1]
        library.performTouchInput { down(center) }
        var gestureActive = true
        try {
            rule.mainClock.advanceTimeBy(800)
            rule.waitForIdle()
            assertEquals(library.fetchSemanticsNode().boundsInRoot.center.x,
                lens.fetchSemanticsNode().boundsInRoot.center.x, 1f)
            assertEquals(HubDestination.HOME, selected.value)
            assertTrue(requests.isEmpty())
            val scene = rule.onNodeWithTag("dock-pixel-scene")
            val pixels = captureDockPixels()
            val icon = rule.onNodeWithTag("dock-icon-LIBRARY", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot.translate(-scene.fetchSemanticsNode().boundsInRoot.topLeft)
            var blueInk = 0
            for (y in icon.top.roundToInt() until icon.bottom.roundToInt()) {
                for (x in icon.left.roundToInt() until icon.right.roundToInt()) {
                    val color = pixels[x, y]
                    if (color.blue > 0.6f && color.blue > color.red + 0.25f) blueInk++
                }
            }
            assertTrue("preview must show the blue sampled icon", blueInk > icon.width * icon.height * 0.05f)
            rule.mainClock.autoAdvance = false
            library.performTouchInput { cancel() }
            gestureActive = false
            rule.mainClock.advanceTimeBy(32)
            val returningLens = lens.fetchSemanticsNode().boundsInRoot
                .translate(-scene.fetchSemanticsNode().boundsInRoot.topLeft)
            val returningPixels = captureDockPixels()
            val homeIcon = rule.onNodeWithTag("dock-icon-HOME", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot.translate(-scene.fetchSemanticsNode().boundsInRoot.topLeft)
            var outsideLens = 0
            var duplicateBlue = 0
            for (y in homeIcon.top.roundToInt() until homeIcon.bottom.roundToInt()) {
                for (x in homeIcon.left.roundToInt() until homeIcon.right.roundToInt()) {
                    if (returningLens.contains(Offset(x.toFloat(), y.toFloat()))) continue
                    outsideLens++
                    val color = returningPixels[x, y]
                    if (color.blue > 0.6f && color.blue > color.red + 0.25f) duplicateBlue++
                }
            }
            assertTrue("returning lens must still be away from the old icon", outsideLens > 0)
            assertEquals("cancel must not show another blue icon outside the lens", 0, duplicateBlue)
        } finally {
            if (gestureActive) library.performTouchInput { cancel() }
            rule.mainClock.autoAdvance = true
        }
        rule.waitForIdle()
        assertEquals(resting, lens.fetchSemanticsNode().boundsInRoot)
        assertTrue(requests.isEmpty())
        library.performTouchInput { down(center); advanceEventTime(500); up() }
        rule.waitForIdle()
        library.assertIsSelected()
        assertEquals(listOf(HubDestination.LIBRARY), requests)
    }

    @Test fun importGlassRefractsPatternBeyondBlurAlone() {
        val capability = mutableStateOf(GlassCapability.FULL_OPTICAL)
        rule.setContent {
            M1racleReaderTheme {
                val page = rememberLayerBackdrop()
                CompositionLocalProvider(LocalGlassBackdropHost provides GlassBackdropHost(page, page),
                    LocalGlassCapability provides capability.value) {
                    Box(Modifier.fillMaxSize().testTag("dock-pixel-scene")) {
                        GlassBackdropSource(page, Modifier.fillMaxSize()) {
                            Canvas(Modifier.fillMaxSize()) {
                                val colors = listOf(Color.Red, Color.Blue, Color.Green)
                                val cell = 24f * density
                                for (row in 0..(size.height / cell).toInt()) {
                                    for (column in 0..(size.width / cell).toInt()) {
                                        drawRect(colors[(row + column) % colors.size],
                                            Offset(column * cell, row * cell), Size(cell, cell))
                                    }
                                }
                            }
                        }
                        HubDockMotionProvider(HubDestination.HOME) {
                            GlassDock(onSelect = {}, onImportDocument = {})
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        val scene = rule.onNodeWithTag("dock-pixel-scene")
        val bounds = rule.onNodeWithTag("dock-import").fetchSemanticsNode().boundsInRoot
            .translate(-scene.fetchSemanticsNode().boundsInRoot.topLeft)
        val refracted = captureDockPixels()
        rule.runOnIdle { capability.value = GlassCapability.BLUR_ONLY }
        rule.waitForIdle()
        val blurred = captureDockPixels()
        var changed = 0
        var area = 0
        for (y in bounds.top.roundToInt() until bounds.bottom.roundToInt()) {
            for (x in bounds.left.roundToInt() until bounds.right.roundToInt()) {
                val radius = kotlin.math.hypot(x - bounds.center.x, y - bounds.center.y) / bounds.width
                if (radius < 0.30f || radius > 0.46f) continue
                val a = refracted[x, y]
                val b = blurred[x, y]
                if (kotlin.math.abs(a.red - b.red) + kotlin.math.abs(a.green - b.green) +
                    kotlin.math.abs(a.blue - b.blue) > 0.10f) changed++
                area++
            }
        }
        assertTrue("lens must refract background, not just blur it: $changed/$area", changed > area * 0.10f)
    }

    @Test
    fun selectedAndInactiveTabsSampleBlurredPageDuringHomePress() {
        assertEquals("com.m1racle.reader.qa", InstrumentationRegistry.getInstrumentation().targetContext.packageName)
        rule.setContent {
            M1racleReaderTheme {
                val page = rememberLayerBackdrop()
                CompositionLocalProvider(LocalGlassBackdropHost provides GlassBackdropHost(page, page)) {
                Box(Modifier.fillMaxSize().testTag("dock-pixel-scene")) {
                    HubDockMotionProvider(HubDestination.HOME) {
                        GlassBackdropSource(page, Modifier.fillMaxSize()) {
                            Canvas(Modifier.fillMaxSize()) {
                                // Dense stripes expose a raw-page sampling leak. The shell
                                // must blur these while sampling their average, not cover them in white.
                                drawRect(Color.White)
                                val stripe = density
                                var x = 0f
                                while (x < size.width) {
                                    drawRect(Color.Black, Offset(x, 0f), Size(stripe, size.height))
                                    x += stripe * 2
                                }
                            }
                        }
                        GlassDock(onSelect = {}, onImportDocument = {})
                    }
                }
                }
            }
        }
        rule.waitForIdle()
        assertBlurredPage("idle")
        val lens = rule.onNodeWithTag("dock-selection")
        val resting = lens.fetchSemanticsNode().boundsInRoot
        val before = captureDockPixels("sampled-rim-idle")
        val home = rule.onAllNodes(isSelectable())[0]
        val homeBounds = home.fetchSemanticsNode().boundsInRoot.translate(
            -rule.onNodeWithTag("dock-pixel-scene").fetchSemanticsNode().boundsInRoot.topLeft)
        val restingInk = blueInkCount(before, homeBounds)
        home.performTouchInput { down(center) }
        try {
            rule.mainClock.advanceTimeBy(800)
            rule.waitForIdle()
            assertBlurredPage("holding Home")
            val after = captureDockPixels("sampled-rim-held")
            val heldInk = blueInkCount(after, homeBounds)
            assertTrue("sampled icon and label must magnify while held: $restingInk -> $heldInk",
                restingInk > 0 && heldInk > restingInk * 1.35f)
            val held = lens.fetchSemanticsNode().boundsInRoot
            assertTrue("lens must use the reference pickup: $resting -> $held",
                held.width in resting.width * 1.36f..resting.width * 1.40f)
            assertTrue(held.height in resting.height * 1.42f..resting.height * 1.46f)
            var changed = 0
            var area = 0
            for (y in resting.top.roundToInt() until resting.bottom.roundToInt()) {
                for (x in resting.left.roundToInt() until resting.right.roundToInt()) {
                    val a = before[x, y]
                    val b = after[x, y]
                    if (kotlin.math.abs(a.red - b.red) + kotlin.math.abs(a.green - b.green) +
                        kotlin.math.abs(a.blue - b.blue) > 0.08f) changed++
                    area++
                }
            }
            assertTrue("press must change sampled lens pixels: $changed/$area", changed > area * 0.05f)
        } finally {
            home.performTouchInput { cancel() }
        }
        rule.waitForIdle()
        assertBlurredPage("released")
        assertEquals(resting, lens.fetchSemanticsNode().boundsInRoot)
    }

    private fun assertBlurredPage(phase: String) {
        val scene = rule.onNodeWithTag("dock-pixel-scene")
        val origin = scene.fetchSemanticsNode().boundsInRoot.topLeft
        val pixels = captureDockPixels()
        rule.onAllNodes(isSelectable()).fetchSemanticsNodes().forEachIndexed { index, tab ->
            val bounds = tab.boundsInRoot.translate(-origin)
            // The expanded Home lens reaches the left edge of Library. Sample
            // Library's right gap to measure blur, not the moving lens highlight.
            // All gaps remain outside icon strokes, including during pickup.
            val gapStart = if (index == 1) 0.80f else 0.10f
            val left = (bounds.left + bounds.width * gapStart).roundToInt()
            val right = (bounds.left + bounds.width * (gapStart + 0.10f)).roundToInt()
            val top = (bounds.center.y - bounds.height * 0.03f).roundToInt()
            val bottom = (bounds.center.y + bounds.height * 0.03f).roundToInt()
            var minLight = 1f
            var maxLight = 0f
            for (y in top until bottom) for (x in left until right) {
                val light = pixels[x, y].luminance()
                minLight = minOf(minLight, light)
                maxLight = maxOf(maxLight, light)
            }
            assertTrue("tab $index leaks raw background at $phase: ${maxLight - minLight}", maxLight - minLight < 0.02f)
            assertTrue("tab $index must sample the gray stripe average at $phase: $minLight..$maxLight",
                minLight > 0.10f && maxLight < 0.85f)
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
                    val page = rememberLayerBackdrop()
                    CompositionLocalProvider(LocalGlassBackdropHost provides GlassBackdropHost(page, page)) {
                    Box(
                        Modifier.fillMaxSize().background(if (dark) Color.Black else Color.White)
                            .testTag("dock-pixel-scene"),
                    ) {
                        GlassBackdropSource(page, Modifier.matchParentSize()) {
                            Box(Modifier.fillMaxSize().background(if (dark) Color.Black else Color.White))
                        }
                        HubDockMotionProvider(HubDestination.HOME) {
                            GlassDock(onSelect = {}, onImportDocument = {})
                        }
                    }
                    }
                }
            }
        }
        rule.waitForIdle()
        val scene = rule.onNodeWithTag("dock-pixel-scene")
        val origin = scene.fetchSemanticsNode().boundsInRoot.topLeft
        // Capture the scene, not individual Icon nodes: a later glass layer can cover them.
        val pixels = captureDockPixels()
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

    private fun assertPageColorTransmission(dark: Boolean) {
        val warm = Color(0xFFFF6120)
        val cool = Color(0xFF209FFF)
        val background = mutableStateOf(warm)
        rule.setContent {
            M1racleReaderTheme(darkTheme = dark) {
                val page = rememberLayerBackdrop()
                CompositionLocalProvider(LocalGlassBackdropHost provides GlassBackdropHost(page, page)) {
                    Box(Modifier.fillMaxSize().testTag("dock-pixel-scene")) {
                        GlassBackdropSource(page, Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxSize().background(background.value))
                        }
                        HubDockMotionProvider(HubDestination.HOME) {
                            GlassDock(onSelect = {}, onImportDocument = {})
                        }
                    }
                }
            }
        }
        val scene = rule.onNodeWithTag("dock-pixel-scene")
        val origin = scene.fetchSemanticsNode().boundsInRoot.topLeft
        val warmPixels = captureDockPixels()
        rule.runOnIdle { background.value = cool }
        val coolPixels = captureDockPixels()
        val sourceDifference = colorDifference(warm, cool)
        rule.onAllNodes(isSelectable()).fetchSemanticsNodes().drop(1).forEachIndexed { index, tab ->
            val bounds = tab.boundsInRoot.translate(-origin)
            val left = (bounds.left + bounds.width * 0.80f).roundToInt()
            val right = (bounds.left + bounds.width * 0.90f).roundToInt()
            val top = (bounds.center.y - bounds.height * 0.03f).roundToInt()
            val bottom = (bounds.center.y + bounds.height * 0.03f).roundToInt()
            var transmittedDifference = 0f
            var area = 0
            for (y in top until bottom) for (x in left until right) {
                transmittedDifference += colorDifference(warmPixels[x, y], coolPixels[x, y])
                area++
            }
            val transmission = transmittedDifference / area / sourceDifference
            assertTrue("tab ${index + 1} loses page color through glass, dark=$dark: $transmission",
                transmission > if (dark) 0.64f else 0.70f)
        }
    }

    private fun colorDifference(a: Color, b: Color): Float =
        kotlin.math.abs(a.red - b.red) + kotlin.math.abs(a.green - b.green) +
            kotlin.math.abs(a.blue - b.blue)

    private fun blueInkCount(pixels: PixelMap, bounds: androidx.compose.ui.geometry.Rect): Int {
        var ink = 0
        for (y in bounds.top.roundToInt() until bounds.bottom.roundToInt()) {
            for (x in bounds.left.roundToInt() until bounds.right.roundToInt()) {
                val color = pixels[x, y]
                if (color.blue > 0.6f && color.blue > color.red + 0.25f) ink++
            }
        }
        return ink
    }

    private fun captureDockPixels(evidenceName: String? = null): PixelMap {
        rule.waitForIdle()
        val bounds = rule.onNodeWithTag("dock-pixel-scene").fetchSemanticsNode().boundsInWindow
        val region = Rect(bounds.left.roundToInt(), bounds.top.roundToInt(),
            bounds.right.roundToInt(), bounds.bottom.roundToInt())
        val bitmap = Bitmap.createBitmap(region.width(), region.height(), Bitmap.Config.ARGB_8888)
        val copied = CountDownLatch(1)
        var result = PixelCopy.ERROR_UNKNOWN
        rule.runOnUiThread {
            PixelCopy.request(rule.activity.window, region, bitmap, {
                result = it
                copied.countDown()
            }, Handler(Looper.getMainLooper()))
        }
        // Keep the same real window pixels and assertions, while allowing a loaded
        // emulator longer than Compose's one-second PixelCopy callback deadline.
        assertTrue("PixelCopy callback did not complete", copied.await(5, TimeUnit.SECONDS))
        assertEquals("PixelCopy must copy the drawn window", PixelCopy.SUCCESS, result)
        if (evidenceName != null) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            java.io.File(context.getExternalFilesDir(null), "$evidenceName.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        return bitmap.asImageBitmap().toPixelMap()
    }
}
