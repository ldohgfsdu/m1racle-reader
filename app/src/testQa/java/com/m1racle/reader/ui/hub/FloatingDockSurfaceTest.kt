package com.m1racle.reader.ui.hub

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import com.m1racle.reader.ui.theme.M1racleReaderTheme
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
class FloatingDockSurfaceTest {
    @get:Rule val rule = createComposeRule()

    @Test fun areaBelowFloatingDockRemainsAnUntouchedPageAndReceivesTouches() {
        var clicks = 0
        rule.setContent {
            M1racleReaderTheme {
                val page = rememberLayerBackdrop()
                CompositionLocalProvider(LocalGlassBackdropHost provides GlassBackdropHost(page, page)) {
                    Box(Modifier.fillMaxSize().testTag("page")) {
                        GlassBackdropSource(page, Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxSize().clickable { clicks++ })
                        }
                        HubDockMotionProvider(HubDestination.HOME) {
                            GlassDock(onImportDocument = {}, onSelect = {})
                        }
                    }
                }
            }
        }
        rule.onNodeWithTag("page").performTouchInput { click(Offset(center.x, bottom - 2f)) }
        assertEquals(1, clicks)
    }
}
