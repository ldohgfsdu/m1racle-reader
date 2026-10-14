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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.test.onNodeWithText
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

    @Test fun lightDockDoesNotWashOutPageOutsideCapsule() = assertNoOutsideScrim(false)
    @Test fun darkDockDoesNotWashOutPageOutsideCapsule() = assertNoOutsideScrim(true)

    @Test fun darkBlackBackdropRetainsCharcoalRailAndCompactSelection() {
        rule.setContent { M1racleReaderTheme(darkTheme = true) {
            val page = rememberLayerBackdrop()
            CompositionLocalProvider(LocalGlassBackdropHost provides GlassBackdropHost(page, page)) {
                Box(Modifier.fillMaxSize().testTag("dock-pixel-scene")) {
                    GlassBackdropSource(page, Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().background(Color.Black))
                    }
                    HubDockMotionProvider(HubDestination.HOME) {
                        GlassDock(onSelect = {}, onImportDocument = {})
                    }
                }
            }
        } }
        val origin = rule.onNodeWithTag("dock-pixel-scene").renderedBounds().topLeft
        val tabs = rule.onAllNodes(isSelectable())
        val home = tabs[0].renderedBounds().translate(-origin)
        val library = tabs[1].renderedBounds().translate(-origin)
        val blank = Offset(library.left + library.width * .85f, library.center.y)
        val selectedBlank = home.center + Offset(20f * rule.density.density, 0f)
        fun PixelMap.at(point: Offset) = this[point.x.roundToInt(), point.y.roundToInt()]
        fun neutral(color: Color): Float {
            assertTrue("black backdrop must keep a neutral rail", kotlin.math.abs(color.red - color.blue) < .01f)
            return (color.red + color.green + color.blue) / 3f
        }
        val idle = captureDockPixels("ios-night-black-rest")
        val rail = idle.at(blank)
        // The supplied black-page reference has ~24/255 rail and ~51/255
        // compact selection. Bounds allow video quantization, not a black rail.
        assertTrue("night rail must remain charcoal over black: $rail", neutral(rail) in .07f.. .14f)
        val compact = idle.at(selectedBlank)
        assertTrue("compact selection must remain visibly separate: $compact", neutral(compact) in .16f.. .25f)
        tabs[0].performTouchInput { down(center); advanceEventTime(800) }
        try {
            rule.mainClock.advanceTimeBy(800)
            val held = captureDockPixels("ios-night-black-held")
            assertTrue("holding must not lift the whole rail", colorDifference(rail, held.at(blank)) < .02f)
            assertTrue("held lens must transmit the real charcoal rail, without a gray fill",
                colorDifference(rail, held.at(selectedBlank)) < .02f)
        } finally { tabs[0].performTouchInput { cancel() } }
        rule.mainClock.advanceTimeBy(1000)
        val returned = captureDockPixels("ios-night-black-cancelled")
        assertTrue("cancel restores compact night selection", colorDifference(compact, returned.at(selectedBlank)) < .02f)
    }

    @Test fun lightPickupShowsCapturedRailOutlineInsideLens() = assertInnerRailOutline(false)
    @Test fun darkPickupShowsCapturedRailOutlineInsideLens() = assertInnerRailOutline(true)

    @Test fun lightLensTintsOnlyTheCoveredPartOfAnIcon() = assertPartialIconTint(false)
    @Test fun darkLensTintsOnlyTheCoveredPartOfAnIcon() = assertPartialIconTint(true)

    @Test fun lightHeldLensMagnifiesItsForeground() = assertForegroundMagnification(false)
    @Test fun darkHeldLensMagnifiesItsForeground() = assertForegroundMagnification(true)

    private fun assertForegroundMagnification(dark: Boolean) {
        rule.setContent { M1racleReaderTheme(darkTheme = dark) {
            val page = rememberLayerBackdrop()
            CompositionLocalProvider(LocalGlassBackdropHost provides GlassBackdropHost(page, page)) {
                Box(Modifier.fillMaxSize().testTag("dock-pixel-scene")) {
                    GlassBackdropSource(page, Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().background(if (dark) Color.Black else Color.White))
                    }
                    HubDockMotionProvider(HubDestination.HOME) {
                        GlassDock(onSelect = {}, onImportDocument = {})
                    }
                }
            }
        } }
        val origin = rule.onNodeWithTag("dock-pixel-scene").renderedBounds().topLeft
        val icon = rule.onNodeWithTag("dock-icon-HOME", useUnmergedTree = true)
            .renderedBounds().translate(-origin)
        val untouched = rule.onNodeWithTag("dock-icon-SETTINGS", useUnmergedTree = true).renderedBounds()
        val dp = rule.density.density
        // Measure the glyph's body, excluding the refracted top-rim pixels.
        // Its original viewbox contains the widest horizontal section.
        val region = icon.inflate(6f * dp).copy(top = icon.top, bottom = icon.bottom + 2f * dp)
        fun blueWidth(pixels: PixelMap): Int {
            val columns = (region.left.roundToInt() until region.right.roundToInt()).filter { x ->
                (region.top.roundToInt() until region.bottom.roundToInt()).any { y ->
                    val c = pixels[x, y]
                    c.blue > c.red + .15f && c.blue > c.green + .03f
                }
            }
            assertTrue("home icon must contain accent pixels", columns.isNotEmpty())
            return columns.last() - columns.first() + 1
        }
        val idle = blueWidth(captureDockPixels("foreground-$dark-idle"))
        val home = rule.onAllNodes(isSelectable())[0]
        home.performTouchInput { down(center); advanceEventTime(800) }
        try {
            rule.mainClock.advanceTimeBy(800)
            val heldPixels = captureDockPixels("foreground-$dark-held")
            val held = blueWidth(heldPixels)
            val ratio = held.toFloat() / idle
            assertTrue("covered foreground must magnify like the reference: $idle -> $held ($ratio), dark=$dark",
                ratio in 1.14f..1.28f)
            assertEquals("the original row and the uncovered icon keep their bounds", untouched,
                rule.onNodeWithTag("dock-icon-SETTINGS", useUnmergedTree = true).renderedBounds())
            if (dark) {
                val lens = rule.onNodeWithTag("dock-selection").renderedBounds().translate(-origin)
                val clear = lens.center + Offset(20f * dp, 0f)
                val color = heldPixels[clear.x.roundToInt(), clear.y.roundToInt()]
                val distant = rule.onAllNodes(isSelectable())[3].renderedBounds().translate(-origin)
                val reference = heldPixels[(distant.left + distant.width * .85f).roundToInt(),
                    distant.center.y.roundToInt()]
                assertTrue("the clear lens must preserve the actual PDF material without an extra gray pill: $color vs $reference",
                    colorDifference(color, reference) < .01f)
            }
        } finally { home.performTouchInput { cancel() } }
    }

    @Test fun lightLeftCapHasClearSpaceInsideHeldLens() = assertEndCapInset(false, false)
    @Test fun darkLeftCapHasClearSpaceInsideHeldLens() = assertEndCapInset(true, false)
    @Test fun lightRightCapHasClearSpaceInsideHeldLens() = assertEndCapInset(false, true)
    @Test fun darkRightCapHasClearSpaceInsideHeldLens() = assertEndCapInset(true, true)

    private fun assertEndCapInset(dark: Boolean, right: Boolean) {
        rule.setContent { M1racleReaderTheme(darkTheme = dark) {
            val page = rememberLayerBackdrop()
            CompositionLocalProvider(LocalGlassBackdropHost provides GlassBackdropHost(page, page)) {
                Box(Modifier.fillMaxSize().testTag("dock-pixel-scene")) {
                    GlassBackdropSource(page, Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().background(Color.Black))
                    }
                    HubDockMotionProvider(HubDestination.HOME) {
                        GlassDock(onSelect = {}, onImportDocument = {})
                    }
                }
            }
        } }
        val origin = rule.onNodeWithTag("dock-pixel-scene").renderedBounds().topLeft
        val tab = rule.onAllNodes(isSelectable())[if (right) 3 else 0]
        tab.performTouchInput { down(center); advanceEventTime(800) }
        try {
            rule.mainClock.advanceTimeBy(800)
            val lens = rule.onNodeWithTag("dock-selection").renderedBounds().translate(-origin)
            val dp = rule.density.density
            val pixels = captureDockPixels("cap-inset-$dark-$right")
            val yStart = (lens.center.y - 8f * dp).roundToInt()
            val yEnd = (lens.center.y + 8f * dp).roundToInt()
            val rows = (yStart until yEnd).count { y ->
                // The reference has about 10dp from the lens end to the
                // captured rail cap. Exclude the first 6dp entirely: a peak
                // on the outer lens rim cannot satisfy this regression.
                ((6f * dp).roundToInt()..(16f * dp).roundToInt()).any { inset ->
                    val x = (if (right) lens.right - inset else lens.left + inset).roundToInt()
                    val side = (1f * dp).roundToInt()
                    fun brightness(column: Int): Float {
                        val c = pixels[column, y]
                        return (c.red + c.green + c.blue) / 3f
                    }
                    brightness(x) > brightness(x - side) + .08f &&
                        brightness(x) > brightness(x + side) + .08f
                }
            }
            assertTrue("captured end cap must sit clearly inside the lens: dark=$dark right=$right $rows/${yEnd-yStart}",
                rows > (yEnd - yStart) * .6f)
            val xStart = (lens.center.x - 8f * dp).roundToInt()
            val xEnd = (lens.center.x + 8f * dp).roundToInt()
            for (top in listOf(true, false)) {
                val columns = (xStart until xEnd).count { x ->
                    ((6f * dp).roundToInt()..(12f * dp).roundToInt()).any { inset ->
                        val y = (if (top) lens.top + inset else lens.bottom - inset).roundToInt()
                        val side = (1f * dp).roundToInt()
                        fun brightness(row: Int): Float {
                            val c = pixels[x, row]
                            return (c.red + c.green + c.blue) / 3f
                        }
                        brightness(y) > brightness(y - side) + .08f &&
                            brightness(y) > brightness(y + side) + .08f
                    }
                }
                assertTrue("sampled rail height must leave a separate inner edge: top=$top dark=$dark right=$right $columns/${xEnd-xStart}",
                    columns > (xEnd - xStart) * .6f)
            }
        } finally { tab.performTouchInput { cancel() } }
    }

    private fun assertPartialIconTint(dark: Boolean) {
        rule.setContent { M1racleReaderTheme(darkTheme = dark) {
            val page = rememberLayerBackdrop()
            CompositionLocalProvider(LocalGlassBackdropHost provides GlassBackdropHost(page, page)) {
                Box(Modifier.fillMaxSize().testTag("dock-pixel-scene")) {
                    GlassBackdropSource(page, Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().background(if (dark) Color.Black else Color.White))
                    }
                    HubDockMotionProvider(HubDestination.HOME) {
                        GlassDock(onSelect = {}, onImportDocument = {})
                    }
                }
            }
        } }
        val home = rule.onAllNodes(isSelectable())[0]
        val library = rule.onNodeWithTag("dock-icon-LIBRARY", useUnmergedTree = true)
        val lens = rule.onNodeWithTag("dock-selection")
        val dp = rule.density.density
        home.performTouchInput { down(center); advanceEventTime(800) }
        try {
            rule.mainClock.advanceTimeBy(800)
            val shift = library.renderedBounds().center.x + 2f * dp - lens.renderedBounds().right
            home.performTouchInput { moveBy(Offset(shift, 0f), 160); advanceEventTime(800) }
            rule.mainClock.advanceTimeBy(800)
            val origin = rule.onNodeWithTag("dock-pixel-scene").renderedBounds().topLeft
            val icon = library.renderedBounds().translate(-origin)
            val glass = lens.renderedBounds().translate(-origin)
            assertTrue("lens must cross the icon before reaching its center",
                glass.center.x < icon.left && glass.right > icon.center.x && glass.right < icon.right)
            val pixels = captureDockPixels("partial-icon-tint-$dark")
            var coveredBlue = 0
            var uncoveredNeutral = 0
            var uncoveredBlue = 0
            for (y in icon.top.roundToInt() until icon.bottom.roundToInt()) {
                for (x in icon.left.roundToInt() until icon.right.roundToInt()) {
                    val c = pixels[x, y]
                    val blue = c.blue > .55f && c.blue > c.red + .2f
                    if (x < icon.center.x - 6f * dp && blue) coveredBlue++
                    if (x > glass.right + 2f * dp) {
                        if (blue) uncoveredBlue++
                        val gray = kotlin.math.max(c.red, kotlin.math.max(c.green, c.blue)) -
                            kotlin.math.min(c.red, kotlin.math.min(c.green, c.blue)) < .08f
                        val ink = if (dark) c.red > .65f else c.red < .3f
                        if (gray && ink) uncoveredNeutral++
                    }
                }
            }
            assertTrue("covered part must already be blue before the tab center: $coveredBlue", coveredBlue > 20)
            assertTrue("uncovered part must retain neutral icon pixels: $uncoveredNeutral", uncoveredNeutral > 20)
            assertEquals("the entire icon must not switch color on crossing a slot", 0, uncoveredBlue)
            rule.onAllNodes(isSelectable())[0].assertIsSelected()
        } finally { home.performTouchInput { cancel() } }
    }

    @Test fun lensRimSeparatesColorsFromAMonochromeBackdrop() {
        val capability = mutableStateOf(GlassCapability.FULL_OPTICAL)
        rule.setContent { M1racleReaderTheme(darkTheme = true) {
            val page = rememberLayerBackdrop()
            CompositionLocalProvider(LocalGlassBackdropHost provides GlassBackdropHost(page, page),
                LocalGlassCapability provides capability.value) {
                Box(Modifier.fillMaxSize().testTag("dock-pixel-scene")) {
                    GlassBackdropSource(page, Modifier.fillMaxSize()) {
                        Canvas(Modifier.fillMaxSize()) {
                            val cell = 16.dp.toPx()
                            for (y in 0..(size.height / cell).toInt())
                                for (x in 0..(size.width / cell).toInt()) {
                                    drawRect(if ((x + y) % 2 == 0) Color.White else Color.Black,
                                        Offset(x * cell, y * cell), Size(cell, cell))
                                }
                        }
                    }
                    HubDockMotionProvider(HubDestination.HOME) {
                        GlassDock(onSelect = {}, onImportDocument = {})
                    }
                }
            }
        } }
        val home = rule.onAllNodes(isSelectable())[0]
        val sceneOrigin = rule.onNodeWithTag("dock-pixel-scene").renderedBounds().topLeft
        val idleLens = rule.onNodeWithTag("dock-selection").renderedBounds().translate(-sceneOrigin)
        val idlePixels = captureDockPixels("spectral-rim-idle")
        home.performTouchInput { down(center); advanceEventTime(800) }
        try {
            rule.mainClock.advanceTimeBy(800)
            val origin = rule.onNodeWithTag("dock-pixel-scene").renderedBounds().topLeft
            val lens = rule.onNodeWithTag("dock-selection").renderedBounds().translate(-origin)
            val icon = rule.onNodeWithTag("dock-icon-HOME", useUnmergedTree = true)
                .renderedBounds().translate(-origin)
            val label = rule.onNodeWithText("首页", useUnmergedTree = true)
                .renderedBounds().translate(-origin)
            val dp = rule.density.density
            var rimArea = 0
            fun spectralPixels(pixels: PixelMap, bounds: androidx.compose.ui.geometry.Rect = lens): Int {
                var count = 0
                rimArea = 0
                for (y in bounds.top.roundToInt() until bounds.bottom.roundToInt())
                    for (x in bounds.left.roundToInt().coerceAtLeast(0) until bounds.right.roundToInt()) {
                        val r = bounds.height / 2f
                        val dx = (kotlin.math.abs(x - bounds.center.x) - (bounds.width / 2f - r)).coerceAtLeast(0f)
                        val edge = r - kotlin.math.hypot(dx, y - bounds.center.y)
                        if (edge !in .3f * dp..4f * dp) continue
                        val point = Offset(x.toFloat(), y.toFloat())
                        if (icon.inflate(4f * dp).contains(point) || label.inflate(4f * dp).contains(point)) continue
                        rimArea++
                        val c = pixels[x, y]
                        if (kotlin.math.max(c.red, kotlin.math.max(c.green, c.blue)) -
                            kotlin.math.min(c.red, kotlin.math.min(c.green, c.blue)) > .15f) count++
                    }
                return count
            }
            assertEquals("unpressed monochrome glass must not create rainbow bands", 0,
                spectralPixels(idlePixels, idleLens))
            val heldPixels = captureDockPixels("spectral-rim-optical")
            val spectral = spectralPixels(heldPixels)
            assertTrue("colored fringe must stay thin, not fill the rim: $spectral/$rimArea",
                spectral < rimArea * .12f)
            rule.mainClock.advanceTimeBy(1600)
            val stillPixels = captureDockPixels("spectral-rim-stationary")
            var maxChange = 0f
            for (y in lens.top.roundToInt() until lens.bottom.roundToInt())
                for (x in lens.left.roundToInt().coerceAtLeast(0) until lens.right.roundToInt()) {
                    maxChange = kotlin.math.max(maxChange, colorDifference(heldPixels[x, y], stillPixels[x, y]))
                }
            assertTrue("stationary glass must not animate rainbow colors: $maxChange", maxChange < .01f)
            rule.runOnIdle { capability.value = GlassCapability.BLUR_ONLY }
            val plain = spectralPixels(captureDockPixels("spectral-rim-blur-only"))
            assertTrue("a gray backdrop must produce real colored fringes at the lens rim: $spectral vs $plain",
                spectral > plain + 25)
        } finally { home.performTouchInput { cancel() } }
    }

    @Test fun lightDispersionRemainsVisibleAcrossBothHorizontalAxisCrossings() = assertAxisDispersion(false)
    @Test fun darkDispersionRemainsVisibleAcrossBothHorizontalAxisCrossings() = assertAxisDispersion(true)

    private fun assertAxisDispersion(dark: Boolean) {
        val capability = mutableStateOf(GlassCapability.FULL_OPTICAL)
        rule.setContent { M1racleReaderTheme(darkTheme = dark) {
            // All sampled content is neutral: any fringe must come from optics.
            // Hide source foreground, so a blue navigation glyph cannot pass.
            MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(onSurface = Color.Transparent)) {
                val page = rememberLayerBackdrop()
                CompositionLocalProvider(LocalGlassBackdropHost provides GlassBackdropHost(page, page),
                    LocalGlassCapability provides capability.value) {
                    Box(Modifier.fillMaxSize().testTag("dock-pixel-scene")) {
                        GlassBackdropSource(page, Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxSize().background(Color.Black))
                        }
                        HubDockMotionProvider(HubDestination.HOME) {
                            GlassDock(onSelect = {}, onImportDocument = {})
                        }
                    }
                }
            }
        } }
        val home = rule.onAllNodes(isSelectable())[0]
        home.performTouchInput { down(center); advanceEventTime(800) }
        try {
            rule.mainClock.advanceTimeBy(800)
            val origin = rule.onNodeWithTag("dock-pixel-scene").renderedBounds().topLeft
            val lens = rule.onNodeWithTag("dock-selection").renderedBounds().translate(-origin)
            val dp = rule.density.density
            val optical = captureDockPixels("axis-dispersion-$dark-optical")
            rule.runOnIdle { capability.value = GlassCapability.BLUR_ONLY }
            val plain = captureDockPixels("axis-dispersion-$dark-plain")
            for (top in listOf(true, false)) {
                var colored = 0
                var energy = 0f
                for (x in (lens.center.x - 6f * dp).roundToInt()..(lens.center.x + 6f * dp).roundToInt()) {
                    // Include the sub-dp outer lip, as in the existing spectral
                    // rim regression. Starting at .8dp misses a fine fringe.
                    for (inset in (0.3f * dp).roundToInt()..(4f * dp).roundToInt()) {
                        val y = (if (top) lens.top + inset else lens.bottom - inset).roundToInt()
                        fun chroma(c: Color): Float = maxOf(c.red, c.green, c.blue) - minOf(c.red, c.green, c.blue)
                        val extra = chroma(optical[x, y]) - chroma(plain[x, y])
                        energy += extra
                        if (extra > .04f) colored++
                    }
                }
                assertTrue("the real rail must produce fine dispersion across its horizontal axis, dark=$dark top=$top: $colored / $energy",
                    colored > 8 && energy > .8f)
            }
        } finally { home.performTouchInput { cancel() } }
    }

    private fun assertInnerRailOutline(dark: Boolean) {
        // Isolate the captured rail highlight from the page. A bright exterior
        // can obscure a narrow peak even when the dark inner contour is visible.
        val pageColor = Color.Black
        rule.setContent { M1racleReaderTheme(darkTheme = dark) {
            val page = rememberLayerBackdrop()
            CompositionLocalProvider(LocalGlassBackdropHost provides GlassBackdropHost(page, page)) {
                Box(Modifier.fillMaxSize().testTag("dock-pixel-scene")) {
                    GlassBackdropSource(page, Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().background(pageColor))
                    }
                    HubDockMotionProvider(HubDestination.HOME) {
                        GlassDock(onSelect = {}, onImportDocument = {})
                    }
                }
            }
        } }
        val origin = rule.onNodeWithTag("dock-pixel-scene").renderedBounds().topLeft
        val home = rule.onAllNodes(isSelectable())[0]
        home.performTouchInput { down(center); advanceEventTime(800) }
        try {
            rule.mainClock.advanceTimeBy(800)
            val lens = rule.onNodeWithTag("dock-selection").renderedBounds().translate(-origin)
            val pixels = captureDockPixels("inner-rail-outline-$dark")
            val dp = rule.density.density
            val xStart = (lens.center.x - 8f * dp).roundToInt()
            val xEnd = (lens.center.x + 8f * dp).roundToInt()
            // Exclude the outer lens highlight and the icon/label. The only
            // bright contour in these bands must come from the captured rail.
            fun columnsWithInnerEdge(top: Boolean): Int = (xStart until xEnd).count { x ->
                // The separate fit regression requires the inner contour
                // beyond the outermost 6dp. Here retain the broad presence check.
                ((2f * dp).roundToInt()..(12f * dp).roundToInt()).any { inset ->
                    val y = if (top) lens.top + inset else lens.bottom - inset
                    fun brightness(row: Int): Float {
                        val c = pixels[x, row]
                        return (c.red + c.green + c.blue) / 3f
                    }
                    val row = y.roundToInt()
                    val side = (1f * dp).roundToInt()
                    // Local contrast measures a real narrow contour in either
                    // theme; dark captured highlights need not become white.
                    val peak = brightness(row)
                    peak > brightness(row - side) + .08f &&
                        peak > brightness(row + side) + .08f
                }
            }
            assertTrue("missing top rail contour inside lens, dark=$dark",
                columnsWithInnerEdge(true) > (xEnd - xStart) * .6f)
            assertTrue("missing bottom rail contour inside lens, dark=$dark",
                columnsWithInnerEdge(false) > (xEnd - xStart) * .6f)
            val yStart = (lens.center.y - 8f * dp).roundToInt()
            val yEnd = (lens.center.y + 8f * dp).roundToInt()
            val capRows = (yStart until yEnd).count { y ->
                ((6f * dp).roundToInt()..(16f * dp).roundToInt()).any { inset ->
                    val x = (lens.left + inset).roundToInt().coerceAtLeast(1)
                    val side = (1f * dp).roundToInt()
                    fun brightness(column: Int): Float {
                        val c = pixels[column, y]
                        return (c.red + c.green + c.blue) / 3f
                    }
                    val peak = brightness(x)
                    peak > brightness((x - side).coerceAtLeast(0)) + .08f &&
                        peak > brightness(x + side) + .08f
                }
            }
            assertTrue("missing curved rail cap inside lens, dark=$dark: $capRows/${yEnd-yStart}",
                capRows > (yEnd - yStart) * .6f)
        } finally { home.performTouchInput { cancel() } }
    }

    private fun assertNoOutsideScrim(dark: Boolean) {
        val pageColor = Color(0xFF365F81)
        rule.setContent { M1racleReaderTheme(darkTheme = dark) {
            val page = rememberLayerBackdrop()
            CompositionLocalProvider(LocalGlassBackdropHost provides GlassBackdropHost(page, page)) {
                Box(Modifier.fillMaxSize().testTag("dock-pixel-scene")) {
                    GlassBackdropSource(page, Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().background(pageColor))
                    }
                    HubDockMotionProvider(HubDestination.HOME) {
                        GlassDock(onSelect = {}, onImportDocument = {})
                    }
                }
            }
        } }
        val scene = rule.onNodeWithTag("dock-pixel-scene").renderedBounds()
        val rail = rule.onNodeWithTag("dock-material", useUnmergedTree = true)
            .renderedBounds().translate(-scene.topLeft)
        val pixels = captureDockPixels("no-outside-scrim-$dark")
        val edge = (2f * rule.density.density).roundToInt()
        for (x in listOf(edge, scene.width.roundToInt() - edge - 1)) {
            assertTrue("floating dock must leave surrounding page colors intact, dark=$dark",
                colorDifference(pageColor, pixels[x, rail.center.y.roundToInt()]) < .02f)
        }
    }

    @Test fun pressedLensDoesNotFoldBlueLabelIntoItsBottomRim() {
        val hideForeground = mutableStateOf(false)
        rule.setContent { M1racleReaderTheme(darkTheme = true) {
            val scheme = MaterialTheme.colorScheme
            MaterialTheme(colorScheme = if (hideForeground.value) scheme.copy(onSurface = Color.Transparent) else scheme) {
            val page = rememberLayerBackdrop()
            CompositionLocalProvider(LocalGlassBackdropHost provides GlassBackdropHost(page, page)) {
                Box(Modifier.fillMaxSize().testTag("dock-pixel-scene")) {
                    GlassBackdropSource(page, Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().background(Color.Black))
                    }
                    HubDockMotionProvider(HubDestination.HOME) {
                        GlassDock(onSelect = {}, onImportDocument = {})
                    }
                }
            }
            }
        } }
        val origin = rule.onNodeWithTag("dock-pixel-scene").renderedBounds().topLeft
        val home = rule.onAllNodes(isSelectable())[0]
        val label = rule.onNodeWithText("首页", useUnmergedTree = true).renderedBounds().translate(-origin)
        // Keep the existing forbidden-rim bounds and 2% limit. Compare the
        // same held optical scene with transparent foreground, so real blue
        // dispersion from a white rail border cannot count as blue label ink.
        val inkBottom = label.bottom
        home.performTouchInput { down(center); advanceEventTime(800) }
        try {
            rule.mainClock.advanceTimeBy(800)
            val lens = rule.onNodeWithTag("dock-selection").renderedBounds().translate(-origin)
            val pixels = captureDockPixels("label-fold-held")
            val rim = androidx.compose.ui.geometry.Rect(
                lens.left.coerceAtLeast(0f), inkBottom + 2f * rule.density.density,
                lens.right, lens.bottom,
            )
            rule.runOnIdle { hideForeground.value = true }
            val withoutForeground = captureDockPixels("label-fold-no-foreground")
            fun foregroundInk(bounds: androidx.compose.ui.geometry.Rect): Int {
                var ink = 0
                for (y in bounds.top.roundToInt() until bounds.bottom.roundToInt()) {
                    for (x in bounds.left.roundToInt() until bounds.right.roundToInt()) {
                        val color = pixels[x, y]
                        if (color.blue > .6f && color.blue > color.red + .25f &&
                            colorDifference(color, withoutForeground[x, y]) > .1f) ink++
                    }
                }
                return ink
            }
            val totalInk = foregroundInk(lens.copy(left = lens.left.coerceAtLeast(0f)))
            val foldedInk = foregroundInk(rim)
            assertTrue("label must remain visible", totalInk > 0)
            assertTrue("blue label folds into the bottom rim: $foldedInk / $totalInk",
                foldedInk < totalInk * .02f)
        } finally { home.performTouchInput { cancel() } }
    }

    @Test fun darkPressPreservesDistantRailColor() = assertDistantRailColor(true)
    @Test fun lightPressPreservesDistantRailColor() = assertDistantRailColor(false)

    @Test fun lightPanelMaterialDoesNotWhitenOnPress() = assertPanelMaterialStable(false)
    @Test fun darkPanelMaterialDoesNotWhitenOnPress() = assertPanelMaterialStable(true)

    private fun assertPanelMaterialStable(dark: Boolean) {
        val pressed = mutableStateOf(false)
        rule.setContent { M1racleReaderTheme(darkTheme = dark) {
            val page = rememberLayerBackdrop()
            CompositionLocalProvider(LocalGlassBackdropHost provides GlassBackdropHost(page, page)) {
                Box(Modifier.fillMaxSize().testTag("dock-pixel-scene")) {
                    GlassBackdropSource(page, Modifier.fillMaxSize()) {
                        Canvas(Modifier.fillMaxSize()) {
                            val colors = listOf(Color(0xFF183A58), Color(0xFFBAC8D5), Color(0xFF637B92))
                            val cell = 24f * density
                            for (row in 0..(size.height / cell).toInt()) {
                                for (column in 0..(size.width / cell).toInt()) {
                                    drawRect(colors[(row + column) % colors.size],
                                        Offset(column * cell, row * cell), Size(cell, cell))
                                }
                            }
                        }
                    }
                    // Isolate the actual panel material from the unchanged
                    // pickup geometry, foreground and separate optical lens.
                    Box(Modifier.align(Alignment.Center).size(300.dp, 56.dp)
                        .glassMaterial(GlassMaterialRole.Dock, RoundedCornerShape(50), pressed = pressed.value)
                        .testTag("stable-panel"))
                }
            }
        } }
        val origin = rule.onNodeWithTag("dock-pixel-scene").renderedBounds().topLeft
        val bounds = rule.onNodeWithTag("stable-panel").renderedBounds().translate(-origin)
        val idle = captureDockPixels("panel-stable-$dark-idle")
        rule.runOnIdle { pressed.value = true }
        rule.mainClock.advanceTimeBy(800)
        val held = captureDockPixels("panel-stable-$dark-held")
        var maximum = 0f
        for (y in bounds.top.roundToInt() until bounds.bottom.roundToInt()) {
            for (x in bounds.left.roundToInt() until bounds.right.roundToInt()) {
                maximum = maxOf(maximum, colorDifference(idle[x, y], held[x, y]))
            }
        }
        assertTrue("press must not change the whole panel material, dark=$dark max RGB difference=$maximum",
            maximum < .01f)
    }

    private fun assertDistantRailColor(dark: Boolean) {
        rule.setContent { M1racleReaderTheme(darkTheme = dark) {
            val page = rememberLayerBackdrop()
            CompositionLocalProvider(LocalGlassBackdropHost provides GlassBackdropHost(page, page)) {
                Box(Modifier.fillMaxSize().testTag("dock-pixel-scene")) {
                    GlassBackdropSource(page, Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().background(Color(0xFF183A58)))
                    }
                    HubDockMotionProvider(HubDestination.HOME) {
                        GlassDock(onSelect = {}, onImportDocument = {})
                    }
                }
            }
        } }
        val origin = rule.onNodeWithTag("dock-pixel-scene").renderedBounds().topLeft
        val distantTab = rule.onAllNodes(isSelectable())[3].renderedBounds().translate(-origin)
        val x = (distantTab.left + distantTab.width * .85f).roundToInt()
        val y = distantTab.center.y.roundToInt()
        val idle = captureDockPixels("rail-color-${dark}-idle")[x, y]
        val home = rule.onAllNodes(isSelectable())[0]
        home.performTouchInput { down(center); advanceEventTime(800) }
        try {
            rule.mainClock.advanceTimeBy(800)
            val held = captureDockPixels("rail-color-${dark}-held")[x, y]
            assertTrue("press must not whiten the distant rail, dark=$dark: $idle -> $held",
                colorDifference(idle, held) < .03f)
        } finally { home.performTouchInput { cancel() } }
    }

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
        val restingLens = lens.renderedBounds()
        val inset = 6f * rule.density.density
        val verticalInset = (rest.height - restingLens.height) / 2f
        assertEquals(verticalInset, restingLens.top - rest.top, .5f)
        assertEquals(verticalInset, rest.bottom - restingLens.bottom, .5f)
        assertEquals(inset, restingLens.left - rest.left, .5f)
        val importSurface = rule.onNodeWithTag("dock-import-surface", useUnmergedTree = true).renderedBounds()
        assertEquals(50f * rule.density.density, importSurface.width, .5f)
        assertEquals(36f * rule.density.density, importSurface.height, .5f)
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
            assertEquals(68f * rule.density.density, liftedLens.height, .5f)
            // Paired reference screenshots: about 220px lens over a 190px rail.
            // Reject the previous oversized 78dp pickup even if it shows a rim.
            assertTrue("pickup height must match Coolapk's measured rail proportion",
                liftedLens.height / held.height in 1.13f..1.19f)
            assertTrue("pickup width must match the full reference rail proportion",
                liftedLens.width / held.width in .27f.. .29f)
            assertEquals(held.top - liftedLens.top, liftedLens.bottom - held.bottom, .5f)
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
                    Box(Modifier.fillMaxSize().testTag("dock-pixel-scene")) {
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
                        val squash = stretched.height / heldLens.height
                        if (stretched.width < heldLens.width * 1.249f) {
                            assertEquals("unclamped X and Y must share the same velocity deformation",
                                1f - .25f * strain, squash, .003f)
                        } else {
                            // X reaches its .2 clamp at strain .2667, before Y
                            // reaches its .2 clamp at strain .8. A capped X can
                            // no longer be inverted to recover the strain.
                            assertTrue("saturated horizontal stretch must preserve bounded vertical squash",
                                squash in .8f..(1f - .25f * (.2f / .75f) + .003f))
                        }
                        assertTrue("rightward drag must squash height", stretched.height < heldLens.height)
                    }
                }
            }
            assertTrue(requests.isEmpty())
            // Faster travel must be able to squash the lens inside the rail,
            // rather than keeping a rigid enlarged capsule throughout a drag.
            repeat(5) {
                home.performTouchInput { moveBy(Offset(slot * .6f, 0f), 16) }
                rule.mainClock.advanceTimeByFrame()
                awaitWindowFrame()
            }
            assertTrue("fast drag must squash the lens inside the real rail",
                lens.renderedBounds().height < rail.renderedBounds().height)
            captureDockPixels("fast-drag-inside-rail")
            home.performTouchInput { advanceEventTime(800) }
            rule.mainClock.advanceTimeBy(800)
            assertTrue("held lens must lift outside the rail again after motion settles",
                lens.renderedBounds().height > rail.renderedBounds().height)
            captureDockPixels("stopped-drag-outside-rail")
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

    @Test fun lightHeldDockRetainsPageColorTransmission() = assertPageColorTransmission(dark = false, held = true)
    @Test fun darkHeldDockRetainsPageColorTransmission() = assertPageColorTransmission(dark = true, held = true)

    @Test fun shortTapMovesTheLensContinuouslyInsteadOfTeleporting() {
        val selected = mutableStateOf(HubDestination.HOME)
        val requests = mutableListOf<HubDestination>()
        rule.setContent { M1racleReaderTheme {
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
        } }
        val lens = rule.onNodeWithTag("dock-selection")
        rule.waitForIdle()
        for ((index, destination) in listOf(3 to HubDestination.SETTINGS, 0 to HubDestination.HOME)) {
            val start = lens.renderedBounds().center.x
            val targetTab = rule.onAllNodes(isSelectable())[index]
            val target = targetTab.renderedBounds().center.x
            rule.mainClock.autoAdvance = false
            try {
                targetTab.performTouchInput { down(center); advanceEventTime(16); up() }
                rule.mainClock.advanceTimeBy(64)
                val between = lens.renderedBounds().center.x
                val fraction = (between - start) / (target - start)
                assertTrue("short tap must show an intermediate lens position: $fraction", fraction > .02f && fraction < .95f)
                captureDockPixels("tap-intermediate-${destination.route}")
                assertEquals(destination, selected.value)
                rule.mainClock.advanceTimeBy(1200)
                assertEquals(target, lens.renderedBounds().center.x, 1f)
                captureDockPixels("tap-settled-${destination.route}")
            } finally { rule.mainClock.autoAdvance = true }
        }
        assertEquals(listOf(HubDestination.SETTINGS, HubDestination.HOME), requests)
    }

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

    @Test fun dockRimRefractsPatternBeyondBlurAlone() {
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
        val bounds = rule.onNodeWithTag("dock-material", useUnmergedTree = true).renderedBounds()
            .translate(-scene.renderedBounds().topLeft)
        val idle = captureDockPixels("optical-rim-idle")
        rule.runOnIdle { capability.value = GlassCapability.BLUR_ONLY }
        rule.waitForIdle()
        val idleBlurred = captureDockPixels("blur-only-rim-idle")
        rule.runOnIdle { capability.value = GlassCapability.FULL_OPTICAL }
        val home = rule.onAllNodes(isSelectable())[0]
        home.performTouchInput { down(center); advanceEventTime(800) }
        rule.mainClock.advanceTimeBy(800)
        val refracted = captureDockPixels("optical-rim-held")
        rule.runOnIdle { capability.value = GlassCapability.BLUR_ONLY }
        rule.waitForIdle()
        val blurred = captureDockPixels("blur-only-rim-held")
        home.performTouchInput { cancel() }
        fun assertRefracted(a: PixelMap, b: PixelMap, phase: String) {
            var changed = 0
            var area = 0
            for (y in bounds.top.roundToInt() until bounds.bottom.roundToInt()) {
                for (x in bounds.left.roundToInt() until bounds.right.roundToInt()) {
                    // Follow the complete capsule boundary, including its curved ends.
                    // An inactive-tab circle or only straight rims misses those normals.
                    val radius = bounds.height / 2f
                    val dx = (kotlin.math.abs(x - bounds.center.x) - (bounds.width / 2f - radius))
                        .coerceAtLeast(0f)
                    val edgeDistance = radius - kotlin.math.hypot(dx, y - bounds.center.y)
                    if (edgeDistance < rule.density.density || edgeDistance > 8f * rule.density.density) continue
                    if (colorDifference(a[x, y], b[x, y]) > 0.10f) changed++
                    area++
                }
            }
            assertTrue("PDF dock rim must refract background at $phase, not just blur it: $changed/$area",
                changed > area * 0.10f)
        }
        assertRefracted(idle, idleBlurred, "rest")
        assertRefracted(refracted, blurred, "hold")
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
                held.width in resting.width * 1.495f..resting.width * 1.505f)
            // Coolapk pickup size; PDF and RiseDiary control sizes are independent.
            assertEquals(68f / 48f, held.height / resting.height, 0.003f)
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

    private fun assertPageColorTransmission(dark: Boolean, held: Boolean = false) {
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
                        Box(Modifier.align(Alignment.Center).size(300.dp, 56.dp)
                            .glassMaterial(GlassMaterialRole.Control, RoundedCornerShape(50))
                            .testTag("pdf-material-reference"))
                        HubDockMotionProvider(HubDestination.HOME) {
                            GlassDock(onSelect = {}, onImportDocument = {})
                        }
                    }
                }
            }
        }
        val scene = rule.onNodeWithTag("dock-pixel-scene")
        val origin = scene.renderedBounds().topLeft
        val home = rule.onAllNodes(isSelectable())[0]
        if (held) {
            home.performTouchInput { down(center); advanceEventTime(800) }
            rule.mainClock.advanceTimeBy(800)
        }
        try {
        val warmPixels = captureDockPixels("ios-transmission-$dark-$held-warm")
        rule.runOnIdle { background.value = cool }
        val coolPixels = captureDockPixels("ios-transmission-$dark-$held-cool")
        val sourceDifference = colorDifference(warm, cool)
        val pdfCenter = rule.onNodeWithTag("pdf-material-reference").renderedBounds().center - origin
        val pdfTransmission = colorDifference(
            warmPixels[pdfCenter.x.roundToInt(), pdfCenter.y.roundToInt()],
            coolPixels[pdfCenter.x.roundToInt(), pdfCenter.y.roundToInt()]) / sourceDifference
        assertTrue("the real PDF control must transmit the page", pdfTransmission > .60f)
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
            assertEquals("tab ${index + 1} must match actual PDF control transmission, dark=$dark",
                pdfTransmission, transmission, .02f)
        }
        } finally { if (held) home.performTouchInput { cancel() } }
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

