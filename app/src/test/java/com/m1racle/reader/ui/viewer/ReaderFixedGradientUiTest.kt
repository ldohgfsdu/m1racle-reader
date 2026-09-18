package com.m1racle.reader.ui.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.m1racle.reader.ui.material.GlassBackdropSource
import com.m1racle.reader.ui.theme.M1racleReaderTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34])
class ReaderFixedGradientUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun collapseKeepsBoundsAndReleasesHiddenTouchTargets() {
        val scroll = ReaderTopBarScrollState(100f, 8f)
        var bodyTaps = 0
        val searching = mutableStateOf(false)
        rule.setContent {
            M1racleReaderTheme {
                val backdrop = rememberLayerBackdrop()
                Box(Modifier.fillMaxSize()) {
                    GlassBackdropSource(backdrop, Modifier.fillMaxSize().testTag("body").clickable { bodyTaps++ }) {}
                    ReaderTopBarOverlay(
                        backdrop = backdrop, title = "测试文档", topBarScrollState = if (searching.value) null else scroll,
                        searchState = if (searching.value) DocumentSearchUiState(isActive = true) else null, onBack = {}, searchEnabled = true, onOpenSearch = {},
                        settingsEnabled = true, onOpenSettings = {}, tocEnabled = true, onOpenToc = {},
                        onToggleBookmark = {}, onOpenAnnotations = {}, onSearchQueryChange = {},
                        onCloseSearch = {}, onSearchPrevious = {}, onSearchNext = {},
                        modifier = Modifier.testTag("chrome"),
                    )
                }
            }
        }
        val expanded = rule.onNodeWithTag("chrome").fetchSemanticsNode().boundsInRoot
        val body = rule.onNodeWithTag("body").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithContentDescription("更多").assertExists()
        rule.runOnIdle { scroll.snapTo(100f) }
        rule.onNodeWithContentDescription("更多").assertDoesNotExist()
        assertEquals(expanded, rule.onNodeWithTag("chrome").fetchSemanticsNode().boundsInRoot)
        assertEquals(body, rule.onNodeWithTag("body").fetchSemanticsNode().boundsInRoot)
        rule.onNodeWithTag("chrome").performTouchInput { click(center) }
        rule.runOnIdle { assertEquals(1, bodyTaps) }
        rule.runOnIdle { scroll.forceShow() }
        rule.onNodeWithContentDescription("更多").performClick()
        rule.runOnIdle { scroll.snapTo(100f) }
        rule.onNodeWithContentDescription("更多").assertExists()
        rule.onNodeWithText("测试文档").assertExists()
        rule.runOnIdle { searching.value = true }
        rule.waitForIdle()
        assertEquals(expanded, rule.onNodeWithTag("chrome").fetchSemanticsNode().boundsInRoot)
        assertEquals(body, rule.onNodeWithTag("body").fetchSemanticsNode().boundsInRoot)
        rule.onNodeWithContentDescription("更多").assertDoesNotExist()
    }
}
