package com.m1racle.reader.ui.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
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
    @Test
    fun groupedControlsKeepTouchTargetsAndSeparationOnNarrowLargeTextScreen() {
        var searches = 0
        var tocOpens = 0
        rule.setContent {
            M1racleReaderTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                    val backdrop = rememberLayerBackdrop()
                    Box(Modifier.size(320.dp, 400.dp)) {
                        GlassBackdropSource(backdrop, Modifier.fillMaxSize()) {}
                        ReaderTopBarOverlay(
                            backdrop = backdrop, title = "A long document title for large text",
                            subtitle = { androidx.compose.material3.Text("33%", style = androidx.compose.material3.MaterialTheme.typography.labelSmall) },
                            topBarScrollState = null, searchState = null, onBack = {},
                            searchEnabled = true, onOpenSearch = { searches++ },
                            settingsEnabled = true, onOpenSettings = {},
                            tocEnabled = true, onOpenToc = { tocOpens++ },
                            onSearchQueryChange = {}, onCloseSearch = {},
                            onSearchPrevious = {}, onSearchNext = {},
                        )
                    }
                }
            }
        }
        val titleBounds = rule.onNodeWithText("A long document title for large text", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val subtitleBounds = rule.onNodeWithText("33%", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("subtitle below title", subtitleBounds.top >= titleBounds.bottom)
        assertTrue("title and subtitle stay inside enlarged toolbar", subtitleBounds.bottom <= 75f)
        val buttons = listOf("返回", "搜索当前文档", "打开目录", "更多")
            .map { rule.onNodeWithContentDescription(it) }
        val bounds = buttons.map { it.fetchSemanticsNode().boundsInRoot }
        bounds.forEach { assertTrue(it.width >= 48f); assertTrue(it.height >= 48f) }
        bounds.zipWithNext().forEach { (left, right) -> assertTrue(left.right <= right.left) }
        assertTrue("navigation and more are separate groups", bounds[3].left - bounds[2].right >= 8f)
        buttons[1].performClick()
        buttons[2].performClick()
        rule.runOnIdle { assertEquals(1, searches); assertEquals(1, tocOpens) }
    }

}
