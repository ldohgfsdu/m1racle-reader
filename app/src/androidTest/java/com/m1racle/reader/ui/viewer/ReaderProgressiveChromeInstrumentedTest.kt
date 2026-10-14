package com.m1racle.reader.ui.viewer

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.m1racle.reader.ui.epub.EpubQaHostActivity
import com.m1racle.reader.ui.material.GlassBackdropSource
import com.m1racle.reader.ui.material.GlassCapability
import com.m1racle.reader.ui.material.GlassEnvironment
import com.m1racle.reader.ui.material.LocalGlassCapability
import com.m1racle.reader.ui.material.LocalGlassEnvironment
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Hardware pixels: depth, transmission, fixed edge with hidden controls and blank pages. */
@RunWith(AndroidJUnit4::class)
class ReaderProgressiveChromeInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<EpubQaHostActivity>()

    @Test fun lightEdgeBlursMoreTowardTopWithoutBands() = assertDepth(false)
    @Test fun darkEdgeBlursMoreTowardTopWithoutBands() = assertDepth(true)

    private fun assertDepth(dark: Boolean) {
        setScene(dark)
        val pixels = capture("depth-$dark")
        val top = detail(pixels, 16f)
        val middle = detail(pixels, 88f)
        val lower = detail(pixels, 104f)
        val clear = detail(pixels, 152f)
        assertTrue("top must diffuse detail: $top / $clear", top < clear * .20f)
        assertTrue("viewport edge must not expose sharp content through transparent samples", detail(pixels, 1f) < clear * .20f)
        assertTrue("blur must vary with depth: $top / $middle / $lower", middle > top * 1.5f && lower > middle * 1.5f)
        assertTrue("lower edge returns to clear content: $lower / $clear", lower > clear * .70f)
        for (y in 70..114) {
            assertTrue("no visible band at $y dp", abs(detail(pixels, y.toFloat()) - detail(pixels, y + 1f)) < clear * .15f)
        }
    }

    @Test fun lightAndDarkEdgesTransmitTheScrollingContentColor() {
        val dark = mutableStateOf(false)
        setScene(dark = dark, colored = true)
        for (night in listOf(false, true)) {
            rule.runOnIdle { dark.value = night }
            val pixels = capture("transmission-$night")
            val red = pixels[px(60f), px(18f)]
            val blue = pixels[px(260f), px(18f)]
            assertTrue("red content must remain visible: $red", red.red - red.blue > .40f)
            assertTrue("blue content must remain visible: $blue", blue.blue - blue.red > .40f)
        }
    }

    @Test fun collapsingControlsKeepsTheSameProgressiveEdge() {
        val scroll = ReaderTopBarScrollState(100f, 8f)
        val enabled = mutableStateOf(true)
        val dark = mutableStateOf(false)
        setScene(dark, scrollState = scroll, enabled = enabled)
        for (night in listOf(false, true)) {
            rule.runOnIdle { dark.value = night; enabled.value = true; scroll.forceShow() }
            rule.onNodeWithContentDescription("更多").assertExists()
            val expanded = capture("expanded-fixed-$night")
            rule.runOnIdle { scroll.snapTo(100f) }
            rule.onNodeWithContentDescription("更多").assertDoesNotExist()
            val collapsed = capture("collapsed-fixed-$night")
            rule.runOnIdle { enabled.value = false }
            val source = capture("source-fixed-$night")
            var expandedDifference = 0f
            var collapsedDifference = 0f
            var visibilityDifference = 0f
            var count = 0
            // Empty title region between the back button and the navigation group:
            // foreground controls must disappear, but these actual background pixels stay put.
            for (y in px(70f) until px(104f)) for (x in px(100f) until px(200f)) {
                expandedDifference += abs(expanded[x, y].red - source[x, y].red)
                collapsedDifference += abs(collapsed[x, y].red - source[x, y].red)
                visibilityDifference += abs(expanded[x, y].red - collapsed[x, y].red)
                count++
            }
            assertTrue("expanded chrome must actually blur content", expandedDifference / count > .15f)
            assertTrue("hiding controls must preserve the same blur", collapsedDifference / count > .15f)
            assertTrue("control visibility must not change the depth profile", visibilityDifference / count < .005f)
        }
    }

    @Test fun blankPagesKeepTheirOriginalColorInBothThemes() {
        val dark = mutableStateOf(false)
        setScene(dark = dark, blank = true)
        for (night in listOf(false, true)) {
            rule.runOnIdle { dark.value = night }
            val pixels = capture("blank-$night")
            val expected = if (night) Color(0xFF242424) else Color.White
            for (y in listOf(4f, 40f, 80f, 120f, 160f)) {
                val actual = pixels[px(160f), px(y)]
                assertEquals("blank page red at $y", expected.red, actual.red, .008f)
                assertEquals("blank page green at $y", expected.green, actual.green, .008f)
                assertEquals("blank page blue at $y", expected.blue, actual.blue, .008f)
            }
        }
    }

    @Test fun fadingBlurDoesNotRevealASharpHorizontalStrokeUnderneath() {
        val dark = mutableStateOf(false)
        setScene(dark = dark, horizontalStroke = true)
        for (night in listOf(false, true)) {
            rule.runOnIdle { dark.value = night }
            val pixels = capture("stroke-$night")
            val page = if (night) Color(0xFF242424).red else 1f
            val source = if (night) 1f else 0f
            val contrast = abs(pixels[px(160f), px(96f)].red - page) / abs(source - page)
            assertTrue("a fading blur must not composite the sharp stroke back in: $contrast", contrast < .48f)
        }
    }

    @Test fun aFineGlyphStrokeDiffusesWithoutRepeatingSideImages() {
        setScene(dark = mutableStateOf(false), verticalStroke = true)
        val pixels = capture("fine-stroke")
        val center = px(160f)
        val y = px(16f)
        for (direction in listOf(-1, 1)) {
            var previous = 1f - pixels[center, y].red
            for (distance in 1..px(30f)) {
                val contrast = 1f - pixels[center + direction * distance, y].red
                assertTrue("blur must diffuse a glyph, without repeated edges at $direction/$distance: $previous -> $contrast",
                    contrast <= previous + .009f)
                previous = contrast
            }
        }
    }

    @Test fun aStrokeLeavingTheViewportDoesNotFlashAcrossTheTopEdge() {
        val position = mutableStateOf(0f)
        setScene(dark = mutableStateOf(false), topStrokePosition = position)
        val entering = capture("top-stroke-entering")
        rule.runOnIdle { position.value = -1.5f }
        val leaving = capture("top-stroke-leaving")
        var difference = 0f
        var count = 0
        for (y in px(1f)..px(12f)) for (x in px(80f)..px(240f)) {
            difference += abs(entering[x,y].red - leaving[x,y].red)
            count++
        }
        assertTrue("a 1.5dp scroll must not turn a single stroke into a top-wide flash: ${difference/count}", difference/count < .08f)
    }

    @Test fun aLightMovingStrokeRegainsClarityContinuouslyThroughTheTransition() = assertMovingStroke(false)
    @Test fun aDarkMovingStrokeRegainsClarityContinuouslyThroughTheTransition() = assertMovingStroke(true)

    private fun assertMovingStroke(dark: Boolean) {
        val position = mutableStateOf(52f)
        setScene(dark = mutableStateOf(dark), topStrokePosition = position)
        val page = if (dark) Color(0xFF242424).red else 1f
        val ink = if (dark) 1f else 0f
        var previous = 0f
        val measurements = mutableListOf<Pair<Int, Float>>()
        for (y in 52..114 step 2) {
            rule.runOnIdle { position.value = y.toFloat() }
            val pixels = capture("moving-stroke-$dark-$y")
            // Integrate a 2dp aperture that travels with the glyph. Fractional
            // coverage removes pixel-grid aliasing from the motion measurement.
            val density = rule.density.density
            val start = (y - .5f) * density
            val end = (y + 1.5f) * density
            var contrast = 0f
            for (row in floor(start).toInt() until ceil(end).toInt()) {
                val coverage = (minOf(row + 1f, end) - maxOf(row.toFloat(), start)).coerceAtLeast(0f)
                contrast += abs(page - pixels[px(160f), row].red) / abs(ink - page) * coverage
            }
            contrast /= density
            if (measurements.isNotEmpty()) {
                assertTrue("a scrolling glyph must not abruptly sharpen at $y dp: $previous -> $contrast",
                    contrast - previous < .14f)
                assertTrue("a scrolling glyph must not become blurred again at $y dp: $previous -> $contrast",
                    contrast >= previous - .035f)
            }
            measurements += y to contrast
            previous = contrast
        }
        assertTrue("a glyph must gradually return to its original clarity", previous > .90f)
        File(rule.activity.getExternalFilesDir(null), "chrome-moving-stroke-$dark.csv").writeText(
            "position_dp,contrast\n" + measurements.joinToString("\n") { "${it.first},${it.second}" })
    }

    private fun setScene(
        dark: Boolean,
        scrollState: ReaderTopBarScrollState? = null,
        enabled: MutableState<Boolean> = mutableStateOf(true),
    ) = setScene(mutableStateOf(dark), scrollState = scrollState, enabled = enabled)

    private fun setScene(
        dark: MutableState<Boolean>,
        colored: Boolean = false,
        blank: Boolean = false,
        horizontalStroke: Boolean = false,
        verticalStroke: Boolean = false,
        topStrokePosition: MutableState<Float>? = null,
        scrollState: ReaderTopBarScrollState? = null,
        enabled: MutableState<Boolean> = mutableStateOf(true),
    ) {
        rule.setContent {
            val page = if (dark.value) Color(0xFF242424) else Color.White
            MaterialTheme(colorScheme = if (dark.value) darkColorScheme(background = page) else lightColorScheme(background = page)) {
                CompositionLocalProvider(
                    LocalGlassEnvironment provides if (dark.value) GlassEnvironment.READER_DARK else GlassEnvironment.READER_LIGHT,
                    LocalGlassCapability provides GlassCapability.FULL_OPTICAL,
                ) {
                    val backdrop = rememberLayerBackdrop()
                    Box(Modifier.size(if (scrollState == null) 320.dp else 400.dp, 180.dp).background(page).testTag("chrome-scene")) {
                        GlassBackdropSource(backdrop, Modifier.fillMaxSize()) {
                            Canvas(Modifier.fillMaxSize()) {
                                if (blank) drawRect(page)
                                else if (topStrokePosition != null) {
                                    drawRect(page)
                                    drawRect(if (dark.value) Color.White else Color.Black,
                                        topLeft = Offset(0f, topStrokePosition.value.dp.toPx()),
                                        size = Size(size.width, 1.dp.toPx()))
                                } else if (verticalStroke) {
                                    drawRect(page)
                                    drawRect(Color.Black,
                                        topLeft = Offset(159.5.dp.toPx(), 0f),
                                        size = Size(1.dp.toPx(), size.height))
                                } else if (horizontalStroke) {
                                    drawRect(page)
                                    drawRect(if (dark.value) Color.White else Color.Black,
                                        topLeft = Offset(0f, 95.5.dp.toPx()),
                                        size = Size(size.width, 1.dp.toPx()))
                                } else if (colored) {
                                    drawRect(Color.Red, size = Size(size.width / 2, size.height))
                                    drawRect(Color.Blue, topLeft = Offset(size.width / 2, 0f), size = Size(size.width / 2, size.height))
                                } else {
                                    // A range of spatial frequencies exposes repeated silhouettes and flat-radius masks.
                                    for (x in 0 until size.width.toInt()) {
                                        val u = x / density
                                        val gray = (.5 + .45 * sin(u * .7 + u * u * .001)).toFloat()
                                        drawRect(Color(gray, gray, gray), Offset(x.toFloat(), 0f), Size(1f, size.height))
                                    }
                                }
                            }
                        }
                        if (enabled.value) {
                            if (scrollState == null) Box(Modifier.fillMaxWidth().height(112.dp)
                                .readerGradient(backdrop, statusBarHeight = 24.dp))
                            else ReaderTopBarOverlay(
                                backdrop = backdrop, title = "", topBarScrollState = scrollState,
                                searchState = null, onBack = {}, searchEnabled = false,
                                onOpenSearch = {}, settingsEnabled = false, onOpenSettings = {},
                                tocEnabled = false, onOpenToc = {}, onSearchQueryChange = {},
                                onCloseSearch = {}, onSearchPrevious = {}, onSearchNext = {},
                            )
                        }
                    }
                }
            }
        }
    }

    private fun px(dp: Float): Int = (dp * rule.density.density).roundToInt()

    private fun detail(pixels: PixelMap, y: Float): Float {
        var total = 0f
        val start = px(40f)
        val end = px(280f)
        for (x in start until end) total += abs(pixels[x, px(y)].red - pixels[x + 1, px(y)].red)
        return total / (end - start)
    }

    private fun capture(name: String): PixelMap {
        rule.waitForIdle()
        val node = rule.onNodeWithTag("chrome-scene").fetchSemanticsNode()
        val bounds = rule.runOnIdle { node.boundsInWindow }
        val committed = CountDownLatch(1)
        rule.runOnUiThread {
            val view = rule.activity.window.decorView
            view.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
            view.invalidate()
        }
        assertTrue("window frame must commit", committed.await(10, TimeUnit.SECONDS))
        val rect = Rect(bounds.left.roundToInt(), bounds.top.roundToInt(), bounds.right.roundToInt(), bounds.bottom.roundToInt())
        val bitmap = Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888)
        val copied = CountDownLatch(1)
        var result = PixelCopy.ERROR_UNKNOWN
        rule.runOnUiThread {
            PixelCopy.request(rule.activity.window, rect, bitmap, { result = it; copied.countDown() }, Handler(Looper.getMainLooper()))
        }
        assertTrue("pixels must be copied", copied.await(10, TimeUnit.SECONDS))
        assertEquals(PixelCopy.SUCCESS, result)
        File(rule.activity.getExternalFilesDir(null), "chrome-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        return bitmap.asImageBitmap().toPixelMap()
    }
}
