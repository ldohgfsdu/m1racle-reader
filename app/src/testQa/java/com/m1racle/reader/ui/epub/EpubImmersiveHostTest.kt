package com.m1racle.reader.ui.epub

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.compose.foundation.layout.WindowInsets as ComposeInsets
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.m1racle.reader.data.settings.ReaderSettings
import com.m1racle.reader.epub.*
import com.m1racle.reader.ui.theme.M1racleReaderTheme
import com.m1racle.reader.ui.material.GlassCapability
import com.m1racle.reader.ui.material.LocalGlassCapability
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class EpubImmersiveHostTest {
    @get:Rule val rule = createComposeRule()
    private val book = EpubBook(
        EpubMetadata("测试书", emptyList(), "zh", null, "3.0", false, false),
        listOf(EpubSpineItem("one", "one.xhtml", true, 0)),
        listOf(EpubTocNode("第一章", "one.xhtml", 0, emptyList())),
        listOf(EpubChapter(0, "one.xhtml", "第一章", List(50) {
            EpubBlock.Paragraph(listOf(EpubInline("正文段落 $it · " + "测试文字 ".repeat(12))))
        }, emptyMap())),
    )

    private fun waitForPages() {
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("epub-pager").fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
    }

    @Test fun `moving paper clips both window corners instead of drawing a rectangular edge`() {
        lateinit var view: View
        rule.setContent {
            view = LocalView.current
            HostReaderTheme(darkTheme = true) {
                EpubReaderContent(book, ReaderSettings(), "测试书", EpubSavedPosition.Start,
                    onPosition = {}, onFlush = {}, onBack = {}, onSettings = {},
                    modifier = Modifier.size(360.dp, 560.dp),
                    insets = ComposeInsets(top = 32.dp, bottom = 24.dp),
                    deviceCorners = EpubDeviceCorners(24f * rule.density.density, 24f * rule.density.density, 24f * rule.density.density, 24f * rule.density.density))
            }
        }
        waitForPages()
        val pager = rule.onNodeWithTag("epub-pager")
        rule.mainClock.autoAdvance = false
        pager.performTouchInput {
            down(percentOffset(0.85f, 0.5f))
            moveTo(percentOffset(0.4f, 0.5f), delayMillis = 600)
        }
        rule.mainClock.advanceTimeByFrame()
        val front = rule.onNodeWithTag("epub-sheet-0").fetchSemanticsNode().boundsInRoot
        val density = rule.density.density
        lateinit var image: Bitmap
        rule.runOnIdle {
            image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
        }
        val x = (front.right - 8 * density).toInt()
        val interior = image.getPixel(x, (front.top + 40 * density).toInt())
        assertTrue("the moving paper must actually be drawn", Color.red(interior) >= 26)
        for (y in listOf((front.top + 2 * density).toInt(), (front.bottom - 2 * density).toInt())) {
            val corner = image.getPixel(x, y)
            assertTrue("rounded cutout at ($x,$y) must reveal the darker sheet", Color.red(corner) < 22)
            assertEquals(255, Color.alpha(corner))
        }
        pager.performTouchInput { moveTo(percentOffset(0.8f, 0.5f), delayMillis = 600); up() }
        rule.mainClock.advanceTimeBy(2_000)
        rule.mainClock.autoAdvance = true
    }

    @Test fun `paper spans the window while text and controls keep stable cutout safe bounds`() {
        val settingsVisible = mutableStateOf(false)
        val emitted = mutableListOf<EpubPosition>()
        rule.setContent {
            HostReaderTheme {
                EpubReaderContent(book, ReaderSettings(), "测试书", EpubSavedPosition.Start,
                    onPosition = { emitted += it }, onFlush = {}, onBack = {}, onSettings = {},
                    modifier = Modifier.size(360.dp, 560.dp),
                    insets = ComposeInsets(left = 24.dp, top = 32.dp, right = 16.dp, bottom = 24.dp),
                    settingsVisible = settingsVisible.value)
            }
        }
        waitForPages()
        val pager = rule.onNodeWithTag("epub-pager")
        val paper = pager.fetchSemanticsNode().boundsInRoot
        val body = rule.onNodeWithTag("epub-reading-area").fetchSemanticsNode().boundsInRoot
        val footer = rule.onNodeWithTag("epub-page-number").fetchSemanticsNode().boundsInRoot
        val label = rule.onNodeWithTag("epub-page-number").fetchSemanticsNode().config[SemanticsProperties.Text]
        val anchor = emitted.last().anchor
        val density = rule.density.density
        assertEquals(0f, paper.left, 0f)
        assertEquals(0f, paper.top, 0f)
        assertEquals(360 * density, paper.width, 1f)
        assertEquals(560 * density, paper.height, 1f)
        assertTrue(body.left >= 24 * density)
        assertTrue(body.right <= paper.right - 16 * density)
        assertTrue(body.top >= 32 * density)
        assertTrue(body.bottom <= footer.top)
        assertTrue(footer.bottom <= paper.bottom - 24 * density)
        pager.performTouchInput { click(center) }
        rule.waitForIdle()
        for (description in listOf("返回", "目录", "设置")) {
            val control = rule.onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot
            assertTrue(control.top >= 32 * density)
            assertTrue(control.bottom <= body.top)
            assertTrue(control.left >= 24 * density)
            assertTrue(control.right <= paper.right - 16 * density)
        }
        pager.performTouchInput { click(center) }
        rule.runOnIdle { settingsVisible.value = true }
        rule.waitForIdle()
        rule.runOnIdle { settingsVisible.value = false }
        rule.waitForIdle()
        assertEquals(paper, pager.fetchSemanticsNode().boundsInRoot)
        assertEquals(body, rule.onNodeWithTag("epub-reading-area").fetchSemanticsNode().boundsInRoot)
        assertEquals(label, rule.onNodeWithTag("epub-page-number").fetchSemanticsNode().config[SemanticsProperties.Text])
        assertEquals(anchor, emitted.last().anchor)
        // The moving sheet must extend into both native bar zones too.
        rule.mainClock.autoAdvance = false
        pager.performTouchInput {
            down(percentOffset(0.85f, 0.5f))
            moveTo(percentOffset(0.4f, 0.5f), delayMillis = 600)
        }
        rule.mainClock.advanceTimeByFrame()
        val front = rule.onNodeWithTag("epub-sheet-0").fetchSemanticsNode().boundsInRoot
        assertTrue(front.right < paper.right - paper.width * 0.2f)
        assertEquals(paper.top, front.top, 0f)
        assertEquals(paper.bottom, front.bottom, 0f)
        pager.performTouchInput { moveTo(percentOffset(0.8f, 0.5f), delayMillis = 600); up() }
        rule.mainClock.advanceTimeBy(2_000)
        rule.mainClock.autoAdvance = true
    }

    @Test fun `native status and navigation bars follow chrome overlays focus and exit`() {
        val shown = mutableStateOf(true)
        val overlay = mutableStateOf(false)
        lateinit var view: View
        var originalCutoutMode = -1
        rule.setContent {
            view = LocalView.current
            if (originalCutoutMode == -1) originalCutoutMode = (view.context as Activity).window.attributes.layoutInDisplayCutoutMode
            HostReaderTheme {
                if (shown.value) EpubReaderContent(book, ReaderSettings(), "测试书", EpubSavedPosition.Start,
                    onPosition = {}, onFlush = {}, onBack = {}, onSettings = {},
                    modifier = Modifier.size(360.dp, 560.dp), insets = ComposeInsets(0, 0, 0, 0),
                    settingsVisible = overlay.value)
            }
        }
        waitForPages()
        val controller = requireNotNull(view.windowInsetsController)
        val bars = WindowInsetsControllerCompat((view.context as Activity).window, view)
        fun requestedBars(): Int = ReflectionHelpers.callInstanceMethod<Int>(controller, "getRequestedVisibleTypes") and
            (WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        fun assertImmersive() = rule.runOnIdle {
            assertEquals(WindowInsets.Type.navigationBars(), requestedBars())
            // AndroidX stores this in Window attributes; Robolectric does not
            // propagate those attributes to the ViewRoot controller's getter.
            assertEquals(WindowInsetsController.BEHAVIOR_DEFAULT, bars.systemBarsBehavior)
        }
        val both = WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
        assertImmersive()
        rule.runOnIdle {
            assertEquals(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS,
                (view.context as Activity).window.attributes.layoutInDisplayCutoutMode)
        }
        val pager = rule.onNodeWithTag("epub-pager")
        pager.performTouchInput { click(center) }
        rule.waitForIdle()
        assertImmersive()
        rule.onNodeWithContentDescription("目录").performClick()
        rule.mainClock.advanceTimeBy(1_000)
        assertImmersive()
        rule.onNodeWithText("关闭").performClick()
        pager.performTouchInput { click(center) }
        rule.waitForIdle()
        assertImmersive()
        rule.runOnIdle { overlay.value = true }
        rule.waitForIdle()
        assertImmersive()
        rule.runOnIdle { overlay.value = false }
        rule.waitForIdle()
        assertImmersive()
        // Returning from another window must reapply the current reader policy.
        rule.runOnIdle {
            WindowInsetsControllerCompat((view.context as Activity).window, view).show(WindowInsetsCompat.Type.systemBars())
            assertEquals(both, requestedBars())
            ReflectionHelpers.callInstanceMethod<Void>(view.viewTreeObserver, "dispatchOnWindowFocusChange",
                ReflectionHelpers.ClassParameter.from(Boolean::class.javaPrimitiveType, true))
        }
        assertImmersive()
        rule.runOnIdle { shown.value = false }
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals(both, requestedBars())
            assertEquals(WindowInsetsController.BEHAVIOR_DEFAULT, bars.systemBarsBehavior)
            assertEquals(originalCutoutMode, (view.context as Activity).window.attributes.layoutInDisplayCutoutMode)
        }
    }
}

/** The host SkSL runtime cannot compile Kyant lens shaders. Keep actual layout,
 * clipping and gestures; exercise the installed APK's glass on Android instead. */
@Composable
private fun HostReaderTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    M1racleReaderTheme(darkTheme = darkTheme) {
        CompositionLocalProvider(LocalGlassCapability provides GlassCapability.SOLID_FALLBACK, content = content)
    }
}
