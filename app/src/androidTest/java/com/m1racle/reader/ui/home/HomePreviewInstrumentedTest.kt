package com.m1racle.reader.ui.home

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.design.RadixColors
import com.m1racle.reader.ui.hub.GlassDock
import com.m1racle.reader.ui.hub.HubDestination
import com.m1racle.reader.ui.hub.HubDockMotionProvider
import com.m1racle.reader.ui.material.GlassBackdropHost
import com.m1racle.reader.ui.material.GlassBackdropSource
import com.m1racle.reader.ui.material.LocalGlassBackdropHost
import com.m1racle.reader.ui.theme.M1racleReaderTheme
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** Synthetic data only, in the isolated QA application. Uses the production state selector and UI. */
class HomePreviewInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun lightHomeHasFiveRecentRowsAndSaturatedImportAction() = verifyHome(false, false)
    @Test fun darkHomeHasFiveRecentRowsAndSaturatedImportAction() = verifyHome(true, false)
    @Test fun narrowHomeKeepsBrandSearchAndLastRowReachableWithLargeText() = verifyHome(true, true)

    private fun verifyHome(dark: Boolean, narrow: Boolean) {
        val source = (12 downTo 1).map {
            DocumentEntity(id = "$it", displayName = "测试文档 $it.md", uri = "content://fixture/$it",
                mimeType = "text/markdown", fileSize = 1024L, lastOpenedAt = it.toLong(), createdAt = 1L,
                lastScrollIndex = 0, lastScrollOffset = 0, readingProgress = .37f,
                title = null, headingCount = 0, documentType = "MARKDOWN")
        }
        var libraryOpens = 0
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (narrow) 1.5f else 1f)) {
                M1racleReaderTheme(darkTheme = dark) {
                    val page = rememberLayerBackdrop()
                    CompositionLocalProvider(LocalGlassBackdropHost provides GlassBackdropHost(page, page)) {
                        Box((if (narrow) Modifier.width(320.dp) else Modifier).fillMaxSize().testTag("home-scene")) {
                            GlassBackdropSource(page, Modifier.fillMaxSize()) {
                                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                                    HomeContent(homeUiState(source, emptySet()), false, {}, {}, {}, {},
                                        onOpenLibrary = { libraryOpens++ })
                                }
                            }
                            HubDockMotionProvider(HubDestination.HOME) {
                                GlassDock(onSelect = {}, onImportDocument = {})
                            }
                        }
                    }
                }
            }
        }
        rule.onNodeWithText("Reader").assertIsDisplayed()
        rule.onNodeWithTag("home-brand-mark").assertIsDisplayed()
        rule.onNodeWithContentDescription("搜索文档与标注").assertIsDisplayed()
        val header = rule.onNodeWithTag("home-header").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("home-view-all").performScrollTo()
        capture("home-${if (dark) "dark" else "light"}${if (narrow) "-large-text" else ""}").recycle()
        rule.onNodeWithTag("home-view-all").performClick()
        rule.runOnIdle { assertEquals(1, libraryOpens) }
        rule.onNodeWithTag("home-list").performScrollToIndex(6)
        rule.onNodeWithText("测试文档 7").assertIsDisplayed()
        rule.onNodeWithText("测试文档 6").assertDoesNotExist()
        assertEquals(header, rule.onNodeWithTag("home-header").fetchSemanticsNode().boundsInRoot)
        val last = rule.onNodeWithText("测试文档 7").fetchSemanticsNode().boundsInRoot
        val dock = rule.onNodeWithTag("dock-capsule").fetchSemanticsNode().boundsInRoot
        assertTrue("the final row must clear the dock", last.bottom < dock.top)
        val bitmap = capture("home-${if (dark) "dark" else "light"}${if (narrow) "-large-text" else ""}-tail")
        val action = rule.onNodeWithTag("dock-import-surface", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInWindow
        val color = bitmap.getPixel((action.left + action.width * .28f).roundToInt(), action.center.y.roundToInt())
        assertEquals("action fill must retain the full Radix blue", RadixColors.Blue9.toArgb(), color)
        val icon = rule.onNodeWithTag("dock-icon-HOME", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInWindow
        val bluePixels = mutableMapOf<Int, Int>()
        for (y in icon.top.roundToInt() until icon.bottom.roundToInt()) {
            for (x in icon.left.roundToInt() until icon.right.roundToInt()) {
                val pixel = bitmap.getPixel(x, y)
                if (android.graphics.Color.blue(pixel) > android.graphics.Color.red(pixel) + 50) {
                    bluePixels[pixel] = (bluePixels[pixel] ?: 0) + 1
                }
            }
        }
        assertTrue("selected glyph must contain solid blue ink", bluePixels.isNotEmpty())
        val foreground = bluePixels.maxBy { it.value }.key
        val background = bitmap.getPixel((icon.left - with(rule.density) { 8.dp.toPx() }).roundToInt(),
            icon.center.y.roundToInt())
        val contrast = androidx.core.graphics.ColorUtils.calculateContrast(foreground, background)
        assertTrue("rendered selected foreground contrast=$contrast, dark=$dark", contrast >= 4.5)
        println("DOCK_CONTRAST dark=$dark narrow=$narrow ratio=$contrast foreground=${foreground.toUInt().toString(16)} background=${background.toUInt().toString(16)}")
        bitmap.recycle()
    }

    private fun capture(name: String): Bitmap {
        rule.waitForIdle()
        val committed = CountDownLatch(1)
        rule.runOnUiThread {
            val view = rule.activity.window.decorView
            view.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
            view.invalidate()
        }
        assertTrue(committed.await(5, TimeUnit.SECONDS))
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val copied = CountDownLatch(1)
        var result = PixelCopy.ERROR_UNKNOWN
        rule.runOnUiThread {
            PixelCopy.request(rule.activity.window, Rect(0, 0, view.width, view.height), bitmap,
                { result = it; copied.countDown() }, Handler(Looper.getMainLooper()))
        }
        assertTrue(copied.await(5, TimeUnit.SECONDS))
        assertEquals(PixelCopy.SUCCESS, result)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        return bitmap
    }
}
