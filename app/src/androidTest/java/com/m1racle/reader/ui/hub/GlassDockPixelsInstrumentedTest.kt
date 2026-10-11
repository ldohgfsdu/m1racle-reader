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
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import com.m1racle.reader.ui.material.GlassMaterialRole
import com.m1racle.reader.ui.material.glassMaterial
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

    @Test fun referenceDockGeometryExpandsMaterialWithoutMovingHitboxes() {
        rule.setContent { M1racleReaderTheme(darkTheme = false) {
            val page = rememberLayerBackdrop()
            CompositionLocalProvider(LocalGlassBackdropHost provides GlassBackdropHost(page, page)) {
                Box(Modifier.fillMaxSize()) {
                    GlassBackdropSource(page, Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().background(Color.White))
                    }
                    HubDockMotionProvider(HubDestination.HOME) {
                        GlassDock(onSelect = {}, onImportDocument = {})
                    }
                }
            }
        } }
        val home = rule.onAllNodes(isSelectable())[0]
        val hitbox = home.renderedBounds()
        val lens = rule.onNodeWithTag("dock-selection")
        val material = rule.onNodeWithTag("dock-material", useUnmergedTree = true)
        val rest = material.renderedBounds()
        assertEquals(56f * rule.density.density, rest.height, .5f)
        assertEquals(48f * rule.density.density, lens.renderedBounds().height, .5f)
        home.performTouchInput { down(center); advanceEventTime(800) }
        try {
            rule.mainClock.advanceTimeBy(800)
            awaitWindowFrame()
            val held = material.renderedBounds()
            assertEquals(rest.width + 16f * rule.density.density, held.width, .5f)
            assertEquals(rest.height * held.width / rest.width, held.height, .5f)
            val liftedLens = lens.renderedBounds()
            assertTrue("pressed lens must exceed both rail edges vertically",
                liftedLens.top < held.top && liftedLens.bottom > held.bottom)
            assertEquals(hitbox, home.renderedBounds())
            assertEquals(48f * (78f / 56f) * rule.density.density, lens.renderedBounds().height, .5f)
        } finally { home.performTouchInput { cancel() } }
        rule.waitForIdle()
        assertEquals(rest, material.renderedBounds())
    }

    @Test fun lightControlEnlargesBrightensAndRebounds() = assertControlResponse(false)
    @Test fun darkControlEnlargesBrightensAndRebounds() = assertControlResponse(true)

    private fun assertControlResponse(dark: Boolean) {
        val pressed = mutableStateOf(false)
        rule.setContent { M1racleReaderTheme(darkTheme = dark) {
            val page = rememberLayerBackdrop()
            CompositionLocalProvider(LocalGlassBackdropHost provides GlassBackdropHost(page, page)) {
                Box(Modifier.fillMaxSize().testTag("dock-pixel-scene")) {
                    GlassBackdropSource(page, Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().background(Color(0xFF183A58)))
                    }
                    Box(Modifier.align(Alignment.Center).size(48.dp)
                        .glassMaterial(GlassMaterialRole.Control, CircleShape, pressed = pressed.value)
                        .testTag("reference-control"))
                }
            }
        } }
        val node = rule.onNodeWithTag("reference-control")
        val rest = node.renderedBounds()
        val origin = rule.onNodeWithTag("dock-pixel-scene").renderedBounds().topLeft
        val point = rest.center - origin
        val idleLight = captureDockPixels()[point.x.roundToInt(), point.y.roundToInt()].luminance()
        rule.mainClock.autoAdvance = false
        try {
            rule.runOnIdle { pressed.value = true }
            rule.mainClock.advanceTimeBy(800)
            awaitWindowFrame()
            val held = node.renderedBounds()
            assertEquals(rest.width * 1.085f, held.width, .5f)
            val heldPixels = captureDockPixels("reference-control-${if(dark) "dark" else "light"}-held")
            val heldLight = heldPixels[point.x.roundToInt(), point.y.roundToInt()].luminance()
            assertTrue("pressed control must brighten real sampled pixels: $idleLight -> $heldLight", heldLight > idleLight + .01f)
            rule.runOnIdle { pressed.value = false }
            var rebounded = false
            repeat(40) {
                rule.mainClock.advanceTimeByFrame()
                if (node.renderedBounds().width < rest.width * .998f) rebounded = true
            }
            assertTrue("release must rebound through resting size", rebounded)
        } finally { rule.mainClock.autoAdvance = true }
        rule.waitForIdle()
        assertEquals(rest, node.renderedBounds())
    }

    @Test fun opticalLensTracksEachDragFrameAndReversalOnDevice() {
        val requests = mutableListOf<HubDestination>()
        rule.setContent {
            M1racleReaderTheme {
                val page = rememberLayerBackdrop()
                CompositionLocalProvider(LocalGlassBackdropHost provides GlassBackdropHost(page, page),
                    LocalGlassCapability provides GlassCapability.FULL_OPTICAL) {
                    Box(Modifier.fillMaxSize()) {
                        GlassBackdropSource(page, Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxSize().background(Color.White))
                        }
                        HubDockMotionProvider(HubDestination.HOME) {
                            GlassDock(onSelect = requests::add, onImportDocument = {})
                        }
                    }
                }
            }
        }
        val home = rule.onAllNodes(isSelectable())[0]
        val library = rule.onAllNodes(isSelectable())[1]
        val lens = rule.onNodeWithTag("dock-selection")
        val restingLens = lens.renderedBounds()
        val rail = rule.onNodeWithTag("dock-material", useUnmergedTree = true)
        val restingRailCenter = rail.renderedBounds().center.x
        val slot = library.renderedBounds().center.x -
            home.renderedBounds().center.x
        rule.mainClock.autoAdvance = false
        var held = false
        try {
            // Keep injected event time aligned with the held animation clock.
            home.performTouchInput { down(center); advanceEventTime(800) }
            held = true
            rule.mainClock.advanceTimeBy(800)
            awaitWindowFrame()
            val heldLens = lens.renderedBounds()
            var position = 0f
            var movementCount = 0
            repeat(3) {
                for (movement in List(12) { .25f } + List(12) { -.25f }) {
                    position += movement
                    home.performTouchInput { moveBy(Offset(slot * movement, 0f), 16) }
                    rule.mainClock.advanceTimeByFrame()
                    awaitWindowFrame()
                    movementCount++
                    assertEquals("optical lens must follow event $movementCount at slot $position on the next frame",
                        home.renderedBounds().center.x + slot * position +
                            rail.renderedBounds().center.x - restingRailCenter,
                        lens.renderedBounds().center.x, 1f)
                    val follow = rail.renderedBounds().center.x - restingRailCenter
                    assertTrue("rail follow must stay within the upstream 4dp bound",
                        kotlin.math.abs(follow) <= 4f * rule.density.density + .5f)
                    if (movementCount == 12) {
                        val stretched = lens.renderedBounds()
                        assertTrue("optical drag must visibly stretch width", stretched.width > heldLens.width * 1.15f)
                        // Upstream couples X and Y through the same signed velocity.
                        // Check that formula rather than the old independently tuned 6% squash.
                        val strain = (1f - heldLens.width / stretched.width) / .75f
                        assertEquals("vertical squash must match upstream velocity deformation",
                            1f - .25f * strain, stretched.height / heldLens.height, .003f)
                        assertTrue("rightward drag must squash height", stretched.height < heldLens.height)
                    }
                }
            }
            assertTrue(requests.isEmpty())
            home.performTouchInput { cancel() }
            held = false
            var rebounded = false
            repeat(30) {
                rule.mainClock.advanceTimeByFrame()
                if (lens.renderedBounds().width < restingLens.width * .98f) rebounded = true
            }
            assertTrue("optical release must rebound past rest", rebounded)
        } finally {
            if (held) home.performTouchInput { cancel() }
            rule.mainClock.autoAdvance = true
        }
        rule.waitForIdle()
        assertEquals(home.renderedBounds().center.x,
            lens.renderedBounds().center.x, 1f)
        assertTrue(requests.isEmpty())
    }

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
        val resting = lens.renderedBounds()
        val library = rule.onAllNodes(isSelectable())[1]
        library.performTouchInput { down(center) }
        var gestureActive = true
        try {
            rule.mainClock.advanceTimeBy(800)
            awaitWindowFrame()
            rule.waitForIdle()
            assertEquals(library.renderedBounds().center.x,
                lens.renderedBounds().center.x, 1f)
            assertEquals(HubDestination.HOME, selected.value)
            assertTrue(requests.isEmpty())
            val scene = rule.onNodeWithTag("dock-pixel-scene")
            val pixels = captureDockPixels("inactive-tab-held")
            val icon = rule.onNodeWithTag("dock-icon-LIBRARY", useUnmergedTree = true)
                .renderedBounds().translate(-scene.renderedBounds().topLeft)
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
            val returningLens = lens.renderedBounds()
                .translate(-scene.renderedBounds().topLeft)
            val returningPixels = captureDockPixels()
            val homeIcon = rule.onNodeWithTag("dock-icon-HOME", useUnmergedTree = true)
                .renderedBounds().translate(-scene.renderedBounds().topLeft)
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
        awaitWindowFrame()
        assertEquals(resting, lens.renderedBounds())
        assertTrue(requests.isEmpty())
        library.performTouchInput { down(center); advanceEventTime(500); up() }
        rule.waitForIdle()
        library.assertIsSelected()
        assertEquals(listOf(HubDestination.LIBRARY), requests)
    }

    @Test fun unifiedDockRefractsPatternBeyondBlurAlone() {
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
        val bounds = rule.onAllNodes(isSelectable())[1].renderedBounds()
            .translate(-scene.renderedBounds().topLeft)
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
        val resting = lens.renderedBounds()
        val before = captureDockPixels("sampled-rim-idle")
        val home = rule.onAllNodes(isSelectable())[0]
        val homeBounds = home.renderedBounds().translate(
            -rule.onNodeWithTag("dock-pixel-scene").renderedBounds().topLeft)
        val restingInk = blueInkCount(before, homeBounds)
        home.performTouchInput { down(center) }
        try {
            rule.mainClock.advanceTimeBy(800)
            awaitWindowFrame()
            rule.waitForIdle()
            assertBlurredPage("holding Home")
            val after = captureDockPixels("sampled-rim-held")
            val heldInk = blueInkCount(after, homeBounds)
            assertTrue("sampled icon and label remain visible during reference pickup: $restingInk -> $heldInk",
                restingInk > 0 && heldInk > 0)
            val held = lens.renderedBounds()
            assertTrue("lens must use the reference pickup: $resting -> $held",
                held.width in resting.width * 1.388f..resting.width * 1.398f)
            // The reference lens lifts to 66dp independently of PDF control feedback.
            assertEquals(78f / 56f, held.height / resting.height, 0.003f)
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
        assertEquals(resting, lens.renderedBounds())
    }

    private fun assertBlurredPage(phase: String) {
        val scene = rule.onNodeWithTag("dock-pixel-scene")
        val origin = scene.renderedBounds().topLeft
        val pixels = captureDockPixels("blur-$phase")
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
            var highFrequencyContrast = 0f
            for (y in top until bottom) for (x in left until right) {
                val light = pixels[x, y].luminance()
                minLight = minOf(minLight, light)
                maxLight = maxOf(maxLight, light)
                // Remove the smooth radial highlight. Raw one-dp stripes retain
                // high-frequency contrast; the upstream light gradient does not.
                if (x > left && x < right - 1) {
                    val localAverage = (pixels[x - 1, y].luminance() + pixels[x + 1, y].luminance()) / 2f
                    highFrequencyContrast = maxOf(highFrequencyContrast, kotlin.math.abs(light - localAverage))
                }
            }
            assertTrue("tab $index leaks raw background at $phase: ${highFrequencyContrast}", highFrequencyContrast < 0.02f)
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
        val origin = scene.renderedBounds().topLeft
        // Capture the scene, not individual Icon nodes: a later glass layer can cover them.
        val pixels = captureDockPixels()
        HubDestination.entries.forEach { destination ->
            val bounds = rule.onNodeWithTag("dock-icon-${destination.name}", useUnmergedTree = true)
                .renderedBounds().translate(-origin)
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
        val origin = scene.renderedBounds().topLeft
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

    // LayoutCoordinates use mutable matrix scratch storage. Read them on the UI
    // thread, where layer updates occur, rather than racing a frame on the test thread.
    private fun androidx.compose.ui.test.SemanticsNodeInteraction.renderedBounds(): androidx.compose.ui.geometry.Rect {
        val node = fetchSemanticsNode()
        return rule.runOnIdle { node.boundsInRoot }
    }

    private fun awaitWindowFrame() {
        // Compose idleness does not mean RenderThread has published its frame.
        // Wait for a real buffer commit before PixelCopy, especially on the first
        // optical shader frame; otherwise the test can read the preceding image.
        val committed = CountDownLatch(1)
        rule.runOnUiThread {
            val view = rule.activity.window.decorView
            view.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
            view.invalidate()
        }
        assertTrue("Window did not commit the requested frame", committed.await(5, TimeUnit.SECONDS))
    }

    private fun captureDockPixels(evidenceName: String? = null): PixelMap {
        rule.waitForIdle()
        val bounds = rule.onNodeWithTag("dock-pixel-scene").fetchSemanticsNode().boundsInWindow
        val region = Rect(bounds.left.roundToInt(), bounds.top.roundToInt(),
            bounds.right.roundToInt(), bounds.bottom.roundToInt())
        awaitWindowFrame()
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

