package com.m1racle.reader.ui.epub

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.m1racle.reader.data.settings.ReaderSettings
import com.m1racle.reader.epub.*
import com.m1racle.reader.ui.material.GlassCapability
import com.m1racle.reader.ui.material.LocalGlassCapability
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
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class EpubReleaseHandoffHostTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var view: View
    private var savedRow = 0
    private val book = EpubBook(
        EpubMetadata("测试书", emptyList(), "zh", null, "3.0", false, false),
        listOf(EpubSpineItem("one", "one.xhtml", true, 0)), emptyList(),
        listOf(EpubChapter(0, "one.xhtml", "第一章", List(50) {
            EpubBlock.Paragraph(listOf(EpubInline("正文段落 $it · " + "测试文字 ".repeat(12))))
        }, emptyMap())),
    )

    private fun start() {
        rule.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalGlassCapability provides GlassCapability.SOLID_FALLBACK) {
                M1racleReaderTheme(darkTheme = true) {
                    EpubReaderContent(book, ReaderSettings(), "测试书", EpubSavedPosition.Start,
                        onPosition = { savedRow = it.sourceRow }, onFlush = {}, onBack = {}, onSettings = {},
                        modifier = Modifier.size(360.dp, 560.dp), insets = WindowInsets(0, 0, 0, 0))
                }
            }
        }
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("epub-pager").fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
    }

    private fun pageLabel() = rule.onNodeWithTag("epub-page-number").fetchSemanticsNode()
        .config[SemanticsProperties.Text].joinToString { it.text }

    private fun rightPaperRed(): Int {
        val bounds = rule.onNodeWithTag("epub-pager").fetchSemanticsNode().boundsInRoot
        lateinit var image: Bitmap
        rule.runOnIdle {
            image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
        }
        val red = Color.red(image.getPixel((bounds.left + bounds.width * 0.9f).toInt(),
            (bounds.top + 40 * rule.density.density).toInt()))
        image.recycle()
        return red
    }

    private fun releasePastHalf(forward: Boolean) {
        val pager = rule.onNodeWithTag("epub-pager")
        val label = pageLabel()
        val row = savedRow
        rule.mainClock.autoAdvance = false
        pager.performTouchInput {
            down(percentOffset(if (forward) 0.88f else 0.12f, 0.5f))
            moveTo(percentOffset(if (forward) 0.73f else 0.27f, 0.5f), delayMillis = 200)
            moveTo(percentOffset(if (forward) 0.26f else 0.74f, 0.5f), delayMillis = 600)
            advanceEventTime(800)
        }
        rule.mainClock.advanceTimeByFrame()
        val before = rightPaperRed()
        if (forward) assertEquals(15, before)
        else assertTrue("original paper follows drag distance", before in 27..30)
        pager.performTouchInput { up() }
        // Sample each real render frame across drag -> fling, rather than just its final page.
        repeat(3) { frame ->
            rule.mainClock.advanceTimeByFrame()
            val bounds = pager.fetchSemanticsNode().boundsInRoot
            val under = rule.onNodeWithTag("epub-sheet-1").fetchSemanticsNode().boundsInRoot
            val distance = if (forward) 1f - (under.left - bounds.left) / (bounds.width * 0.25f)
                else (under.left - bounds.left) / (bounds.width * 0.25f)
            val expected = if (forward) 15 else Color.red(androidx.compose.ui.graphics.lerp(
                com.m1racle.reader.ui.theme.InkBackground, androidx.compose.ui.graphics.Color(0xFF202022),
                1 - (1-distance) * (1-distance)).toArgb())
            assertEquals("paper identity changed at release frame $frame", expected, rightPaperRed())
            assertEquals("footer committed before the sheet stopped", label, pageLabel())
            assertEquals("position saved before the sheet stopped", row, savedRow)
        }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
    }

    @Test fun `forward held drag release keeps the revealed paper dark and position uncommitted`() {
        start()
        releasePastHalf(forward = true)
        assertTrue(pageLabel().startsWith("第 2 /"))
        assertTrue(savedRow > 0)
    }

    @Test fun `backward held drag release keeps the original paper lifted until it is covered`() {
        start()
        rule.onNodeWithTag("epub-pager").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertTrue(pageLabel().startsWith("第 2 /"))
        releasePastHalf(forward = false)
        assertTrue(pageLabel().startsWith("第 1 /"))
        assertEquals(0, savedRow)
    }

    @Test fun `grabbing the released sheet and pulling it back cancels without saving the target page`() {
        start()
        val label = pageLabel()
        val pager = rule.onNodeWithTag("epub-pager")
        rule.mainClock.autoAdvance = false
        pager.performTouchInput {
            down(percentOffset(0.88f, 0.5f))
            moveTo(percentOffset(0.26f, 0.5f), delayMillis = 800)
            advanceEventTime(800)
            up()
        }
        rule.mainClock.advanceTimeBy(32)
        pager.performTouchInput { down(percentOffset(0.26f, 0.5f)) }
        rule.mainClock.advanceTimeByFrame()
        assertEquals("re-grab must not change the paper's identity", 15, rightPaperRed())
        assertEquals(label, pageLabel())
        assertEquals(0, savedRow)
        pager.performTouchInput {
            moveTo(percentOffset(0.88f, 0.5f), delayMillis = 800)
            advanceEventTime(800)
            up()
        }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertEquals(label, pageLabel())
        assertEquals(0, savedRow)
    }
}
