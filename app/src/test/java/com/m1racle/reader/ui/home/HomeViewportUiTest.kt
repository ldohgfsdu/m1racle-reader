package com.m1racle.reader.ui.home

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.m1racle.reader.ui.hub.GlassDock
import com.m1racle.reader.ui.hub.HubDockMotionProvider
import com.m1racle.reader.ui.hub.HubDestination
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import com.m1racle.reader.data.local.DocumentEntity
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
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class HomeViewportUiTest {
    @get:Rule val rule = createComposeRule()
    private val docs = (0..30).map {
        DocumentEntity(id = "$it", displayName = "document-$it.pdf", uri = "content://test/$it.pdf",
            mimeType = "application/pdf", fileSize = 1L, lastOpenedAt = 1L, createdAt = 1L,
            lastScrollIndex = 0, lastScrollOffset = 0, readingProgress = 0f, title = null,
            headingCount = 0, documentType = "PDF")
    }

    @Composable
    private fun HomeWithDock(uiState: HomeUiState, busy: Boolean, onImport: () -> Unit,
        onSearch: () -> Unit, onOpen: (DocumentEntity) -> Unit, onDelete: (String) -> Unit,
        modifier: Modifier = Modifier, statusTop: Dp = 0.dp, navBottom: Dp = 0.dp) {
        Box(modifier.fillMaxSize()) {
            HomeContent(uiState, busy, onImport, onSearch, onOpen, onDelete,
                statusTop = statusTop, navBottom = navBottom)
            HubDockMotionProvider(HubDestination.HOME) {
                GlassDock(onSelect = {}, onImportDocument = onImport, importing = busy)
            }
        }
    }

    @Test fun `header stays fixed while the list reaches behind glass and clears the last item`() {
        rule.setContent {
            M1racleReaderTheme {
                HomeWithDock(HomeUiState(documents = docs, ready = true), false, {}, {}, {}, {},
                    modifier = Modifier.size(380.dp, 700.dp), statusTop = 32.dp, navBottom = 24.dp)
            }
        }
        val header = rule.onNodeWithTag("home-header").fetchSemanticsNode().boundsInRoot
        val list = rule.onNodeWithTag("home-list").fetchSemanticsNode().boundsInRoot
        val scale = rule.density.density
        assertTrue(header.top >= 32f * scale)
        assertTrue(list.top >= header.bottom)
        assertEquals("list must draw through the gesture area", 700f * scale, list.bottom, 1f)
        val dock = rule.onNodeWithTag("dock-selection", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("list=$list dock=$dock", list.bottom > dock.top)
        rule.onNodeWithTag("home-list").performScrollToIndex(20)
        assertEquals(header, rule.onNodeWithTag("home-header").fetchSemanticsNode().boundsInRoot)
        assertEquals(list, rule.onNodeWithTag("home-list").fetchSemanticsNode().boundsInRoot)
        rule.onNodeWithContentDescription("搜索文档与标注").assertIsDisplayed()
        rule.onNodeWithContentDescription("导入文件").assertIsDisplayed()
        rule.onNodeWithTag("home-list").performScrollToIndex(30)
        val last = rule.onNodeWithText("document-30").fetchSemanticsNode().boundsInRoot
        assertTrue("last=$last dock=$dock", last.bottom < dock.top)
    }

    @Test fun `fixed import action is disabled while busy and available again afterwards`() {
        val busy = mutableStateOf(true)
        var imports = 0
        rule.setContent {
            M1racleReaderTheme {
                HomeWithDock(HomeUiState(ready = true, isEmpty = true), busy.value,
                    { imports++ }, {}, {}, {})
            }
        }
        rule.onNodeWithContentDescription("导入文件").assertIsNotEnabled()
        rule.onNodeWithText("导入文件").assertIsNotEnabled()
        rule.onNodeWithText("正在导入，请稍候…").assertIsDisplayed()
        rule.runOnIdle { busy.value = false }
        rule.onNodeWithContentDescription("导入文件").assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals(1, imports) }
    }

    @Test fun `empty state offers import and shares the busy state with the toolbar`() {
        val busy = mutableStateOf(false)
        var imports = 0
        rule.setContent {
            M1racleReaderTheme {
                HomeWithDock(HomeUiState(ready = true, isEmpty = true), busy.value,
                    { imports++; busy.value = true }, {}, {}, {})
            }
        }
        rule.onNodeWithText("导入文件").assertIsDisplayed().performClick()
        rule.onNodeWithText("导入文件").assertIsNotEnabled()
        rule.onNodeWithContentDescription("导入文件").assertIsNotEnabled()
        rule.runOnIdle { assertEquals(1, imports); busy.value = false }
        rule.onNodeWithText("导入文件").assertIsEnabled()
    }

    @Test fun `empty state import stays reachable with large text in a short viewport`() {
        var imports = 0
        rule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                M1racleReaderTheme {
                    HomeWithDock(HomeUiState(ready = true, isEmpty = true), false,
                        { imports++ }, {}, {}, {}, modifier = Modifier.size(380.dp, 320.dp),
                        statusTop = 24.dp, navBottom = 24.dp)
                }
            }
        }
        rule.onNodeWithText("导入文件").performScrollTo().assertIsDisplayed().performClick()
        val viewport = rule.onNodeWithTag("home-empty").fetchSemanticsNode().boundsInRoot
        val action = rule.onNodeWithText("导入文件").fetchSemanticsNode().boundsInRoot
        assertTrue(action.top >= viewport.top)
        assertTrue(action.bottom <= viewport.bottom)
        rule.runOnIdle { assertEquals(1, imports) }
    }
}
