package com.m1racle.reader.ui.hub

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.m1racle.reader.ui.material.GlassBackdropHost
import com.m1racle.reader.ui.material.GlassBackdropSource
import com.m1racle.reader.ui.material.LocalGlassBackdropHost
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "w411dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@ConscryptMode(ConscryptMode.Mode.OFF)
class SoftScrollEdgeTest {
    @get:Rule val rule = createComposeRule()

    @Test fun fadeStartsTransparentAndBecomesProgressivelyBlurredWithoutSteps() {
        for (band in 0..2) {
            assertEquals(0f, scrollEdgeOpacity(band, 0f), 0f)
            assertEquals(1f, scrollEdgeOpacity(band, 1f), 0f)
            var previous = 0f
            for (step in 0..1000) {
                val alpha = scrollEdgeOpacity(band, step / 1000f)
                assertTrue(alpha in previous..1f)
                assertTrue(alpha - previous < 0.004f)
                previous = alpha
            }
        }
        for (step in 0..100) {
            val progress = step / 100f
            assertTrue(scrollEdgeOpacity(0, progress) >= scrollEdgeOpacity(1, progress))
            assertTrue(scrollEdgeOpacity(1, progress) >= scrollEdgeOpacity(2, progress))
        }
    }

    @Test fun blurPreservesGaussianVarianceAcrossDensitiesWithAFilteredFirstPass() {
        for (density in listOf(1f, 2f, 3.25f, 4f)) {
            for (dp in listOf(2f, 8f, 20f)) {
                val radius = dp * density
                val sigma = radius * 0.57735 + 0.5
                val passes = scrollEdgeBlurRadii(radius).map { it * 0.57735 + 0.5 }
                assertTrue(passes.first() <= 4.00001)
                passes.zipWithNext().forEach { (a, b) -> assertTrue(b <= a * 4 + 0.00001) }
                assertEquals(sigma * sigma, passes.sumOf { it * it }, 0.001)
            }
        }
    }

    @Test fun softEdgeDoesNotInterceptUnderlyingPageTouches() {
        var clicks = 0
        rule.setContent {
            val page = rememberLayerBackdrop()
            val edge = rememberLayerBackdrop()
            CompositionLocalProvider(LocalGlassBackdropHost provides GlassBackdropHost(page, page)) {
                Box(Modifier.fillMaxSize().testTag("page")) {
                    GlassBackdropSource(page, Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().clickable { clicks++ })
                    }
                    SoftScrollEdge(edge, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
        rule.onNodeWithTag("page").performTouchInput { click(Offset(center.x, bottom - 30f)) }
        assertEquals(1, clicks)
    }
}
