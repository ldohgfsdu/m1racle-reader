package com.m1racle.reader.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.m1racle.reader.ui.material.GlassCapability
import com.m1racle.reader.ui.material.GlassEnvironment
import com.m1racle.reader.ui.material.LocalGlassCapability
import com.m1racle.reader.ui.material.LocalGlassEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ReaderScrollEdgeRenderTest {
    @get:Rule val rule = createComposeRule()

    @Test fun scrollEdgeDoesNotDarkenBlankPageInEitherReaderEnvironment() {
        lateinit var view: View
        val pageColor = mutableStateOf(Color.White)
        rule.setContent {
            MaterialTheme(colorScheme = lightColorScheme(background = pageColor.value)) {
                CompositionLocalProvider(
                    LocalGlassEnvironment provides if (pageColor.value == Color.White) GlassEnvironment.READER_LIGHT else GlassEnvironment.READER_DARK,
                    LocalGlassCapability provides GlassCapability.SOLID_FALLBACK,
                ) {
                    view = LocalView.current
                    val backdrop = rememberLayerBackdrop()
                    Box(Modifier.size(200.dp).background(pageColor.value).testTag("page")) {
                        Box(Modifier.size(200.dp).readerGradient(backdrop))
                    }
                }
            }
        }
        for (expected in listOf(Color.White, Color(0xFF242424))) {
            rule.runOnIdle { pageColor.value = expected }
            val bounds = rule.onNodeWithTag("page").fetchSemanticsNode().boundsInRoot
            lateinit var image: Bitmap
            rule.runOnIdle {
                image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(image))
            }
            for (y in listOf(bounds.top.toInt() + 1, bounds.center.y.toInt(), bounds.bottom.toInt() - 1)) {
                val color = Color(image.getPixel(bounds.center.x.toInt(), y))
                assertEquals("blank page red at $y", expected.red, color.red, 0.005f)
                assertEquals("blank page green at $y", expected.green, color.green, 0.005f)
                assertEquals("blank page blue at $y", expected.blue, color.blue, 0.005f)
            }
        }
    }
    @Test fun edgeFadesOnlyWhenScrollingContentOverlapsToolbar() {
        lateinit var view: View
        val overlaps = mutableStateOf(false)
        rule.setContent {
            MaterialTheme(colorScheme = lightColorScheme(background = Color.White)) {
                CompositionLocalProvider(LocalGlassCapability provides GlassCapability.SOLID_FALLBACK) {
                    view = LocalView.current
                    val backdrop = rememberLayerBackdrop()
                    Box(Modifier.size(320.dp, 160.dp).background(Color.Red).testTag("page")) {
                        ReaderTopBarOverlay(
                            backdrop = backdrop, hasContentBehindBar = { overlaps.value },
                            title = "X", topBarScrollState = null, searchState = null,
                            onBack = {}, searchEnabled = true, onOpenSearch = {},
                            settingsEnabled = true, onOpenSettings = {}, tocEnabled = true, onOpenToc = {},
                            onSearchQueryChange = {}, onCloseSearch = {},
                            onSearchPrevious = {}, onSearchNext = {},
                        )
                    }
                }
            }
        }
        fun sampleBlankTitleArea(): Color {
            val bounds = rule.onNodeWithTag("page").fetchSemanticsNode().boundsInRoot
            lateinit var image: Bitmap
            rule.runOnIdle {
                image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(image))
            }
            return Color(image.getPixel(
                (bounds.left + bounds.width * 100f / 320f).toInt(),
                (bounds.top + bounds.height * 30f / 160f).toInt(),
            ))
        }
        assertEquals("no overlap leaves content clear", 0f, sampleBlankTitleArea().green, 0.005f)
        rule.runOnIdle { overlaps.value = true }
        rule.waitForIdle()
        assertTrue("overlap protects toolbar contrast", sampleBlankTitleArea().green > 0.80f)
        rule.runOnIdle { overlaps.value = false }
        rule.waitForIdle()
        assertEquals("returning to top removes edge", 0f, sampleBlankTitleArea().green, 0.005f)
    }

}
