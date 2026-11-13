package com.m1racle.reader.ui.epub

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.percentOffset
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
class EpubPagerOffsetReadHostTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var view: View

    @Test fun `offset read during composition tracks controlled drag frames`() {
        val compositions = mutableListOf<Int>()
        lateinit var pager: PagerState
        rule.setContent {
            view = LocalView.current
            pager = rememberPagerState(initialPage = 1) { 3 }
            HorizontalPager(state = pager, beyondViewportPageCount = 1,
                modifier = Modifier.size(360.dp, 560.dp).testTag("composition-pager")) { page ->
                @Suppress("UNUSED_VARIABLE")
                val offset = pager.getOffsetDistanceInPages(page) + pager.currentPageOffsetFraction
                SideEffect { compositions += page }
            }
        }
        rule.waitForIdle()
        val initial = compositions.size
        dragFrames("composition-pager")
        println("B17_COMPOSITION_READ initial=$initial after=$compositions")
        assertTrue("composition read must be visible to the controlled host", compositions.size > initial)
    }

    @Test fun `offset reads during graphics and draw do not recompose controlled page content`() {
        val compositions = mutableListOf<Int>()
        var graphicsReads = 0
        var drawReads = 0
        lateinit var pager: PagerState
        rule.setContent {
            view = LocalView.current
            pager = rememberPagerState(initialPage = 1) { 3 }
            HorizontalPager(state = pager, beyondViewportPageCount = 1,
                modifier = Modifier.size(360.dp, 560.dp).testTag("deferred-pager")) { page ->
                SideEffect { compositions += page }
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()
                    .graphicsLayer {
                        @Suppress("UNUSED_VARIABLE")
                        val offset = pager.getOffsetDistanceInPages(page) + pager.currentPageOffsetFraction
                        graphicsReads++
                    }
                    .drawBehind {
                        @Suppress("UNUSED_VARIABLE")
                        val offset = pager.getOffsetDistanceInPages(page) + pager.currentPageOffsetFraction
                        drawReads++
                    })
            }
        }
        rule.waitForIdle()
        val initial = compositions.size
        dragFrames("deferred-pager")
        println("B17_DEFERRED_READ initial=$initial after=$compositions graphics=$graphicsReads draw=$drawReads")
        assertEquals("graphics/draw reads must not recompose page content", initial, compositions.size)
        assertTrue("controlled host must exercise the graphics phase", graphicsReads > 0)
        assertTrue("controlled host must exercise the draw phase", drawReads > 0)
    }

    private fun dragFrames(tag: String) {
        val pager = rule.onNodeWithTag(tag)
        rule.mainClock.autoAdvance = false
        pager.performTouchInput { down(percentOffset(0.88f, 0.5f)) }
        repeat(8) { step ->
            pager.performTouchInput {
                moveTo(percentOffset(0.88f - (step + 1) * 0.08f, 0.5f), delayMillis = 16)
            }
            rule.mainClock.advanceTimeByFrame()
            rule.runOnIdle {
                val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(image))
                image.recycle()
            }
        }
        rule.mainClock.autoAdvance = true
        pager.performTouchInput { up() }
        rule.waitForIdle()
    }
}
