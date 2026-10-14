package com.m1racle.reader.ui.epub

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.RoundedCorner
import android.view.WindowInsets
import androidx.compose.foundation.layout.WindowInsets as ComposeInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.LayoutDirection

import androidx.compose.ui.unit.dp
import com.m1racle.reader.data.settings.ReaderSettings
import com.m1racle.reader.epub.*
import com.m1racle.reader.ui.material.GlassCapability
import com.m1racle.reader.ui.material.LocalGlassCapability
import com.m1racle.reader.ui.theme.M1racleReaderTheme
import com.m1racle.reader.ui.viewer.components.ReaderDisplaySettings
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class EpubReferenceFidelityHostTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var view: View
    private val book = EpubBook(
        EpubMetadata("唯一书名", emptyList(), "zh", null, "3.0", false, false),
        listOf(EpubSpineItem("one", "one.xhtml", true, 0)), emptyList(),
        listOf(EpubChapter(0, "one.xhtml", "第一章", List(50) {
            EpubBlock.Paragraph(listOf(EpubInline("正文段落 $it · " + "测试文字 ".repeat(12))))
        }, emptyMap())),
    )
    private val settings = mutableStateOf(ReaderSettings())
    private val menu = mutableStateOf(false)
    private val corners = mutableStateOf(EpubDeviceCorners())
    private fun start() {
        rule.setContent {
            view = LocalView.current
            M1racleReaderTheme(darkTheme = true) {
                CompositionLocalProvider(LocalGlassCapability provides GlassCapability.SOLID_FALLBACK) {
                    EpubReaderContent(book, settings.value, "唯一书名", EpubSavedPosition.Start,
                        onPosition = {}, onFlush = {}, onBack = {}, onSettings = { menu.value = true },
                        modifier = Modifier.size(360.dp, 560.dp), insets = ComposeInsets(0, 0, 0, 0),
                        deviceCorners = corners.value,
                        settingsVisible = menu.value, onDismissSettings = { menu.value = false },
                        settingsContent = {
                            EpubReadingSettings(settings.value,
                                onFontScale = { settings.value = settings.value.copy(fontScale = it) },
                                onLineHeightScale = { settings.value = settings.value.copy(lineHeightScale = it) },
                                onPaddingLevel = { settings.value = settings.value.copy(contentPaddingLevel = it) },
                                onReset = { settings.value = ReaderSettings() })
                        })
                }
            }
        }
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("epub-pager").fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
    }
    private fun image(): Bitmap {
        lateinit var bitmap: Bitmap
        rule.runOnIdle {
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        return bitmap
    }

    @Test fun `resting paper fills the window without a nested rim and cancellation restores it exactly`() {
        start()
        rule.runOnIdle { corners.value = EpubDeviceCorners(24f, 24f, 24f, 24f) }
        val pager = rule.onNodeWithTag("epub-pager")
        val bounds = pager.fetchSemanticsNode().boundsInRoot
        val y = (bounds.top + bounds.height / 2).toInt()
        val baseline = image()
        for (x in listOf(bounds.left.toInt(), bounds.right.toInt()-1)) {
            assertEquals("resting side must be paper, not an inset card outline", 15, Color.red(baseline.getPixel(x,y)))
        }
        assertEquals("inner paper is not a raised side gap", 15, Color.red(baseline.getPixel(bounds.left.toInt()+2,y)))
        for ((x, row) in listOf(bounds.left.toInt() to bounds.top.toInt(),
            (bounds.right.toInt()-1) to (bounds.bottom.toInt()-1))) {
            assertEquals("window corner must be filled by paper background", 15, Color.red(baseline.getPixel(x,row)))
        }
        rule.mainClock.autoAdvance = false
        pager.performTouchInput {
            down(percentOffset(.88f,.5f)); moveTo(percentOffset(.58f,.5f),600)
            moveTo(percentOffset(.88f,.5f),800); advanceEventTime(800); up()
        }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        val restored = image()
        for (row in (bounds.top.toInt()+30) until (bounds.bottom.toInt()-30)) {
            for (x in listOf(bounds.left.toInt(),bounds.left.toInt()+1,bounds.right.toInt()-2,bounds.right.toInt()-1)) {
                assertEquals("side changed after cancellation at $x,$row", baseline.getPixel(x,row), restored.getPixel(x,row))
            }
        }
        baseline.recycle(); restored.recycle()
    }

    @Test fun `under sheet follows and original brightness grows throughout the actual held drag`() {
        start()
        val pager = rule.onNodeWithTag("epub-pager")
        val bounds = pager.fetchSemanticsNode().boundsInRoot
        rule.mainClock.autoAdvance = false
        pager.performTouchInput { down(percentOffset(0.88f, 0.5f)) }
        var previousRed = 15
        var previousUnderLeft = bounds.right
        for (distance in listOf(0.12f, 0.3f, 0.5f, 0.65f)) {
            pager.performTouchInput { moveTo(percentOffset(0.88f - distance, 0.5f), delayMillis = 400) }
            rule.mainClock.advanceTimeByFrame()
            val front = rule.onNodeWithTag("epub-sheet-0").fetchSemanticsNode().boundsInRoot
            val under = rule.onNodeWithTag("epub-sheet-1").fetchSemanticsNode().boundsInRoot
            assertEquals((front.right - bounds.left) * 0.25f, under.left - bounds.left, 1f)
            assertTrue("target content must keep moving as the finger advances", under.left < previousUnderLeft)
            previousUnderLeft = under.left
            val actualDistance = 1f - (front.right - bounds.left) / bounds.width
            val expected = Color.red(androidx.compose.ui.graphics.lerp(com.m1racle.reader.ui.theme.InkBackground,
                androidx.compose.ui.graphics.Color(0xFF202022), 1 - (1 - actualDistance) * (1 - actualDistance)).toArgb())
            val bitmap = image()
            val red = Color.red(bitmap.getPixel((bounds.left + 24 * rule.density.density).toInt(),
                (bounds.top + 40 * rule.density.density).toInt()))
            bitmap.recycle()
            assertEquals(expected, red)
            assertTrue("brightness must not plateau after the first 6 percent", red > previousRed)
            previousRed = red
        }
        pager.performTouchInput {
            moveTo(percentOffset(0.88f, 0.5f), delayMillis = 800); advanceEventTime(800); up()
        }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        val bitmap = image()
        assertEquals(15, Color.red(bitmap.getPixel(24, 40)))
        bitmap.recycle()
    }

    @Test fun `platform radius is physical per corner including null and rotated asymmetric corners`() {
        val platform = WindowInsets.Builder()
            .setRoundedCorner(RoundedCorner.POSITION_TOP_LEFT, RoundedCorner(0, 24, 24, 24))
            .setRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT, RoundedCorner(1, 32, 328, 32))
            .setRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT, RoundedCorner(2, 44, 316, 516))
            .setRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT, RoundedCorner(3, 18, 18, 542)).build()
        val radius = epubDeviceCorners(platform)
        assertEquals(EpubDeviceCorners(24f, 32f, 44f, 18f), radius)
        assertEquals(EpubDeviceCorners(), epubDeviceCorners(null))
        for (direction in LayoutDirection.entries) {
            val outline = radius.shape().createOutline(androidx.compose.ui.geometry.Size(360f, 560f),
                direction, androidx.compose.ui.unit.Density(3f)) as Outline.Rounded
            assertEquals(24f, outline.roundRect.topLeftCornerRadius.x, 0f)
            assertEquals(32f, outline.roundRect.topRightCornerRadius.x, 0f)
            assertEquals(44f, outline.roundRect.bottomRightCornerRadius.x, 0f)
            assertEquals(18f, outline.roundRect.bottomLeftCornerRadius.x, 0f)
        }
    }

    @Test fun `paper exposes its full fixed radius from the first visible movement through settling`() {
        start()
        val density = rule.density.density
        rule.runOnIdle { corners.value = EpubDeviceCorners(44*density, 44*density, 44*density, 44*density) }
        val pager = rule.onNodeWithTag("epub-pager")
        rule.mainClock.autoAdvance = false
        pager.performTouchInput { down(percentOffset(0.88f, 0.5f)) }
        for (distance in listOf(0.03f, 0.05f, 0.12f, 0.5f, 0.9f)) {
            pager.performTouchInput { moveTo(percentOffset(0.88f - distance, 0.5f), 600) }
            rule.mainClock.advanceTimeByFrame()
            val front = rule.onNodeWithTag("epub-sheet-0").fetchSemanticsNode().boundsInRoot
            val bitmap = image()
            val x = (front.right - 15*density).toInt()
            assertTrue("top corner must already have full radius at distance $distance",
                Color.red(bitmap.getPixel(x, (front.top + 3*density).toInt())) <= 15)
            assertTrue("bottom corner must keep the same radius at distance $distance",
                Color.red(bitmap.getPixel(x, (front.bottom - 3*density).toInt())) <= 15)
            bitmap.recycle()
        }
        pager.performTouchInput { moveTo(percentOffset(0.88f, 0.5f), 800); advanceEventTime(800); up() }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
    }

    @Test fun `changing device radius changes the actual moving paper cutout without moving text`() {
        start()
        val density = rule.density.density
        rule.runOnIdle { corners.value = EpubDeviceCorners(24*density, 24*density, 24*density, 24*density) }
        val pager = rule.onNodeWithTag("epub-pager")
        rule.mainClock.autoAdvance = false
        pager.performTouchInput { down(percentOffset(0.88f, 0.5f)); moveTo(percentOffset(0.43f, 0.5f), 600) }
        rule.mainClock.advanceTimeByFrame()
        val front = rule.onNodeWithTag("epub-sheet-0").fetchSemanticsNode().boundsInRoot
        val x = (front.right - 15*density).toInt()
        val y = (front.top + 3*density).toInt()
        val first = image()
        assertTrue(Color.red(first.getPixel(x, y)) > 20)
        first.recycle()
        val body = rule.onNodeWithTag("epub-reading-area").fetchSemanticsNode().boundsInRoot
        rule.mainClock.autoAdvance = true
        rule.runOnIdle { corners.value = EpubDeviceCorners(44*density, 44*density, 44*density, 44*density) }
        rule.mainClock.advanceTimeBy(32)
        rule.waitForIdle()
        val second = image()
        assertEquals(15, Color.red(second.getPixel(x, y)))
        second.recycle()
        assertEquals(body, rule.onNodeWithTag("epub-reading-area").fetchSemanticsNode().boundsInRoot)
        pager.performTouchInput { moveTo(percentOffset(0.88f, 0.5f), 600); up() }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
    }

    @Test fun `chrome reveal keeps one title while sliding controls and never repaginates`() {
        start()
        val pager = rule.onNodeWithTag("epub-pager")
        val body = rule.onNodeWithTag("epub-reading-area").fetchSemanticsNode().boundsInRoot
        val pageBounds = pager.fetchSemanticsNode().boundsInRoot
        rule.onAllNodesWithText("唯一书名").filter(SemanticsMatcher("visible paper title") {
            it.boundsInRoot.overlaps(pageBounds) }).assertCountEquals(1)
        rule.mainClock.autoAdvance = false
        pager.performTouchInput { click(center) }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(80)
        rule.waitForIdle()
        val middle = rule.onNodeWithTag("epub-top-controls").fetchSemanticsNode().boundsInRoot
        rule.onAllNodesWithText("唯一书名").filter(SemanticsMatcher("visible paper title") {
            it.boundsInRoot.overlaps(pageBounds) }).assertCountEquals(1)
        rule.mainClock.advanceTimeBy(300)
        val final = rule.onNodeWithTag("epub-top-controls").fetchSemanticsNode().boundsInRoot
        assertTrue("controls slide into position", middle.top < final.top)
        rule.onAllNodesWithText("唯一书名").filter(SemanticsMatcher("visible paper title") {
            it.boundsInRoot.overlaps(pageBounds) }).assertCountEquals(1)
        assertEquals(body, rule.onNodeWithTag("epub-reading-area").fetchSemanticsNode().boundsInRoot)
        rule.mainClock.autoAdvance = true
    }

    @Test fun `settings expands next to its button and applies choices without dismissing the menu`() {
        start()
        rule.onNodeWithTag("epub-pager").performTouchInput { click(center) }
        rule.waitForIdle()
        val button = rule.onNodeWithContentDescription("设置").fetchSemanticsNode()
        rule.onNodeWithContentDescription("设置").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("epub-settings-menu").assertExists()
        val panel = rule.onNodeWithTag("epub-settings-menu").fetchSemanticsNode().boundsInRoot
        assertEquals(button.boundsInRoot.top, panel.top, 0f)
        assertEquals(button.boundsInRoot.right, panel.right, 0f)
        assertTrue("compact panel must leave paper visible", panel.width <= 288f * rule.density.density)
        assertTrue("panel must fit the reader", panel.right <= rule.onNodeWithTag("epub-pager").fetchSemanticsNode().boundsInRoot.right)
        rule.runOnIdle {
            assertFalse("reader menu must not create an independent popup window",
                android.view.inspector.WindowInspector.getGlobalWindowViews().any { it.javaClass.simpleName == "PopupLayout" })
        }
        rule.onNodeWithText("大").performClick()
        rule.waitForIdle()
        assertEquals(ReaderSettings.FONT_LARGE, settings.value.fontScale, 0f)
        rule.onNodeWithTag("epub-settings-menu").assertExists()
        rule.onNodeWithTag("epub-line-slider").performTouchInput { click(percentOffset(.8f, .5f)) }
        rule.waitForIdle()
        assertTrue(settings.value.lineHeightScale > ReaderSettings.LINE_DEFAULT)
        rule.onNodeWithTag("epub-margin-2").performClick()
        rule.waitForIdle()
        assertEquals(com.m1racle.reader.data.settings.ContentPaddingLevel.Relaxed, settings.value.contentPaddingLevel)
        rule.onNodeWithText("重置默认").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(ReaderSettings(), settings.value)
        rule.runOnIdle { menu.value = false }
        rule.waitForIdle()
        rule.onNodeWithTag("epub-settings-menu").assertDoesNotExist()
    }

    @Test fun `original icons remain unique and stationary throughout expansion and collapse`() {
        start()
        rule.onNodeWithTag("epub-pager").performTouchInput { click(center) }
        rule.waitForIdle()
        val toc = rule.onNodeWithTag("epub-menu-tab-toc").fetchSemanticsNode().boundsInRoot
        val settings = rule.onNodeWithTag("epub-menu-tab-settings").fetchSemanticsNode().boundsInRoot
        val capsule = rule.onNodeWithTag("epub-menu-capsule").fetchSemanticsNode().boundsInRoot
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("epub-menu-tab-settings").performClick()
        var lastWidth = capsule.width
        var lastHeight = capsule.height
        repeat(8) {
            rule.mainClock.advanceTimeBy(40)
            rule.onAllNodesWithContentDescription("目录").assertCountEquals(1)
            rule.onAllNodesWithContentDescription("设置").assertCountEquals(1)
            assertEquals(toc, rule.onNodeWithTag("epub-menu-tab-toc").fetchSemanticsNode().boundsInRoot)
            assertEquals(settings, rule.onNodeWithTag("epub-menu-tab-settings").fetchSemanticsNode().boundsInRoot)
            val panel = rule.onNodeWithTag("epub-settings-menu").fetchSemanticsNode().boundsInRoot
            assertEquals(capsule.top, panel.top, 0f)
            assertEquals(capsule.right, panel.right, 0f)
            assertTrue(panel.width >= lastWidth)
            assertTrue(panel.height >= lastHeight)
            lastWidth = panel.width
            lastHeight = panel.height
        }
        assertTrue(lastWidth > capsule.width)
        assertTrue(lastHeight > capsule.height)
        rule.onNodeWithText("关闭").performClick()
        repeat(8) {
            rule.mainClock.advanceTimeBy(40)
            assertEquals(toc, rule.onNodeWithTag("epub-menu-tab-toc").fetchSemanticsNode().boundsInRoot)
            assertEquals(settings, rule.onNodeWithTag("epub-menu-tab-settings").fetchSemanticsNode().boundsInRoot)
            rule.onAllNodesWithContentDescription("目录").assertCountEquals(1)
            rule.onAllNodesWithContentDescription("设置").assertCountEquals(1)
        }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertEquals(capsule, rule.onNodeWithTag("epub-menu-capsule").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun `menu scrim spans the full window while panel respects system safe areas`() {
        rule.setContent {
            M1racleReaderTheme(darkTheme = false) {
                EpubReaderMenu(true, false, {}, {}, {}, {}, {}, Modifier.size(360.dp, 560.dp),
                    insets = ComposeInsets(top = 32.dp, bottom = 24.dp))
            }
        }
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val scrim = rule.onNodeWithTag("epub-menu-dismiss-area").fetchSemanticsNode().boundsInRoot
        val panel = rule.onNodeWithTag("epub-settings-menu").fetchSemanticsNode().boundsInRoot
        assertEquals(root, scrim)
        assertEquals((32 + 4) * rule.density.density, panel.top, 0f)
        assertTrue(panel.bottom <= root.bottom - 24 * rule.density.density)
    }

    @Test fun `outside dismiss consumes the tap without turning the page`() {
        start()
        val page = rule.onNodeWithTag("epub-page-number").fetchSemanticsNode().config[
            androidx.compose.ui.semantics.SemanticsProperties.Text]
        rule.runOnIdle { menu.value = true }
        rule.waitForIdle()
        rule.onNodeWithTag("epub-menu-dismiss-area").performTouchInput { click(percentOffset(.05f, .99f)) }
        rule.waitForIdle()
        rule.onNodeWithTag("epub-settings-menu").assertDoesNotExist()
        assertEquals(page, rule.onNodeWithTag("epub-page-number").fetchSemanticsNode().config[
            androidx.compose.ui.semantics.SemanticsProperties.Text])
    }

    @Test fun `one right menu switches between contents and settings and closes from either tab`() {
        start()
        rule.onNodeWithTag("epub-pager").performTouchInput { click(center) }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("设置").performClick()
        rule.waitForIdle()
        val menuBounds = rule.onNodeWithTag("epub-settings-menu").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("epub-menu-tab-settings").assertIsSelected()
        rule.onNodeWithTag("epub-menu-tab-toc").performClick()
        rule.waitForIdle()
        assertEquals("tab switching must not resize or reposition the panel", menuBounds,
            rule.onNodeWithTag("epub-settings-menu").fetchSemanticsNode().boundsInRoot)
        rule.onNodeWithTag("epub-menu-tab-toc").assertIsSelected()
        rule.onNodeWithTag("epub-toc-list").assertIsDisplayed()
        rule.onNodeWithTag("epub-font-2").assertDoesNotExist()
        rule.onNodeWithTag("epub-menu-tab-settings").performClick()
        rule.waitForIdle()
        assertEquals(menuBounds, rule.onNodeWithTag("epub-settings-menu").fetchSemanticsNode().boundsInRoot)
        rule.onNodeWithTag("epub-font-2").assertIsDisplayed()
        rule.onAllNodesWithTag("epub-settings-menu").assertCountEquals(1)
        rule.onNodeWithTag("epub-menu-tab-settings").performClick()
        rule.onNodeWithTag("epub-settings-menu").assertIsDisplayed()
        val selectedLens = rule.onNodeWithTag("epub-segment-selection").fetchSemanticsNode().boundsInRoot
        val settingsTab = rule.onNodeWithTag("epub-menu-tab-settings").fetchSemanticsNode().boundsInRoot
        assertEquals(settingsTab.center.x, selectedLens.center.x, .5f)
        rule.onNodeWithText("关闭").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("epub-settings-menu").assertDoesNotExist()
    }
}

