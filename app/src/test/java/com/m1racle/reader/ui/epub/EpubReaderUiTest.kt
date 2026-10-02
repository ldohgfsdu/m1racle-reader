package com.m1racle.reader.ui.epub

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.m1racle.reader.data.settings.ReaderSettings
import com.m1racle.reader.epub.*
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
class EpubReaderUiTest {
    @get:Rule val rule = createComposeRule()
    private val book = EpubBook(
        EpubMetadata("测试书", emptyList(), "zh", null, "3.0", false, false),
        listOf(EpubSpineItem("one", "one.xhtml", true, 0), EpubSpineItem("two", "two.xhtml", true, 1)),
        listOf(EpubTocNode("第一章", "one.xhtml", 0, emptyList()), EpubTocNode("第二章", "two.xhtml", 1, emptyList())),
        listOf(
            EpubChapter(0, "one.xhtml", "第一章", List(40) { EpubBlock.Paragraph(listOf(EpubInline("第一章段落 $it"))) }, emptyMap()),
            EpubChapter(1, "two.xhtml", "第二章", List(10) { EpubBlock.Paragraph(listOf(EpubInline("第二章段落 $it"))) }, emptyMap()),
        ),
    )

    private fun pageLabel(): String = rule.onNodeWithTag("epub-page-number")
        .fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }

    private fun waitForPages() {
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("epub-pager").fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
    }

    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Test fun `legacy host dispatches pointer input with a rectangular drawer outline`() {
        var clicked = false
        rule.setContent {
            val drawer = androidx.compose.material3.rememberDrawerState(androidx.compose.material3.DrawerValue.Open)
            androidx.compose.material3.ModalNavigationDrawer(drawerState = drawer, gesturesEnabled = drawer.isOpen,
                drawerContent = { androidx.compose.material3.ModalDrawerSheet(drawerShape = androidx.compose.ui.graphics.RectangleShape) {
                    androidx.compose.material3.TextButton(onClick = { clicked = true }) { androidx.compose.material3.Text("目录项") }
                } }) { androidx.compose.material3.Text("正文") }
        }
        rule.onNodeWithText("目录项").assertIsDisplayed().performClick()
        rule.runOnIdle { assertTrue(clicked) }
    }

    @Test fun `left and right swipes change real pages without opening drawer and saved position restores`() {
        var savedRow = 0
        var savedOffset = 0
        val shown = mutableStateOf(true)
        rule.setContent {
            LegacyHostReaderTheme {
                if (shown.value) EpubReaderContent(book, ReaderSettings(), "测试书", savedRow, savedOffset,
                    onPosition = { row, offset, _ -> savedRow = row; savedOffset = offset },
                    onFlush = {}, onBack = {}, onSettings = {}, modifier = Modifier.size(360.dp, 560.dp),
                    insets = WindowInsets(0, 0, 0, 0))
            }
        }
        waitForPages()
        assertTrue(pageLabel().startsWith("第 1 /"))
        rule.onNodeWithContentDescription("目录").assertDoesNotExist()
        rule.onNodeWithTag("epub-pager").performTouchInput { swipeRight() }
        rule.waitForIdle()
        assertTrue(pageLabel().startsWith("第 1 /"))
        rule.onNodeWithTag("epub-pager").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        val secondPage = pageLabel()
        assertTrue(secondPage, secondPage.startsWith("第 2 /"))
        rule.onNodeWithTag("epub-pager").performTouchInput { swipeRight() }
        rule.waitForIdle()
        assertTrue(pageLabel().startsWith("第 1 /"))
        rule.onNodeWithText("关闭").assertIsNotDisplayed()
        rule.onNodeWithTag("epub-pager").performTouchInput { click(percentOffset(0.88f, 0.5f)) }
        rule.waitForIdle()
        assertEquals(secondPage, pageLabel())
        rule.runOnIdle { shown.value = false }
        rule.runOnIdle { shown.value = true }
        waitForPages()
        assertEquals(secondPage, pageLabel())
        rule.onNodeWithTag("epub-pager").performTouchInput { click(percentOffset(0.5f, 0.5f)) }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("目录").assertIsDisplayed().performClick()
        rule.mainClock.advanceTimeBy(1_000) // Wait for the drawer to finish sliding before tapping a row.
        rule.waitForIdle()
        rule.onNodeWithText("关闭").assertIsDisplayed()
        rule.onNode(hasText("第二章") and hasClickAction()).assertIsDisplayed().performClick()
        rule.waitForIdle()
        assertFalse(pageLabel().startsWith("第 1 /"))
        rule.onNodeWithTag("epub-pager").performTouchInput { click(percentOffset(0.5f, 0.5f)) }
        rule.waitForIdle()
        rule.onNodeWithText("第二章 ·", substring = true).assertIsDisplayed()
    }

    @Test fun `directory header and list stay within status and navigation safe regions`() {
        rule.setContent {
            M1racleReaderTheme {
                EpubTocDrawer("测试书", book.toc, mapOf(tocKey(book.toc[0]) to 0, tocKey(book.toc[1]) to 5), {},
                    {},
                    insets = WindowInsets(top = 32.dp, bottom = 24.dp))
            }
        }
        val content = rule.onNodeWithTag("epub-toc-safe-content").fetchSemanticsNode().boundsInRoot
        val list = rule.onNodeWithTag("epub-toc-list").fetchSemanticsNode().boundsInRoot
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val density = rule.density.density
        assertTrue("content=$content", content.top >= 32 * density)
        assertTrue("list=$list root=$root", list.bottom <= root.bottom - 24 * density)
        rule.onNodeWithText("1 页").assertIsDisplayed()
        rule.onNodeWithText("6 页").assertIsDisplayed()
    }

    @Test fun `settings and viewport reflow keep the current text in the visible page`() {
        val settings = mutableStateOf(ReaderSettings())
        val width = mutableIntStateOf(360)
        var savedRow = 0
        rule.setContent {
            M1racleReaderTheme {
                EpubReaderContent(book, settings.value, "测试书", 0, 0,
                    onPosition = { row, _, _ -> savedRow = row }, onFlush = {}, onBack = {}, onSettings = {},
                    modifier = Modifier.size(width.intValue.dp, 560.dp), insets = WindowInsets(0, 0, 0, 0))
            }
        }
        waitForPages()
        repeat(2) {
            rule.onNodeWithTag("epub-pager").performTouchInput { click(percentOffset(0.88f, 0.5f)) }
            rule.waitForIdle()
        }
        val anchor = savedRow
        assertTrue(anchor > 1)
        rule.runOnIdle {
            settings.value = ReaderSettings(fontScale = 1.2f, lineHeightScale = 2f)
            width.intValue = 300
        }
        waitForPages()
        rule.onNodeWithText("第一章段落 $anchor", substring = true).assertIsDisplayed()
    }

    @Test fun `immersive reading hides app chrome until a center tap`() {
        rule.setContent {
            LegacyHostReaderTheme {
                EpubReaderContent(book, ReaderSettings(), "测试书", 0, 0,
                    onPosition = { _, _, _ -> }, onFlush = {}, onBack = {}, onSettings = {},
                    modifier = Modifier.size(360.dp, 560.dp), insets = WindowInsets(0, 0, 0, 0))
            }
        }
        waitForPages()
        rule.onNodeWithContentDescription("返回").assertDoesNotExist()
        rule.onNodeWithContentDescription("目录").assertDoesNotExist()
        rule.onNodeWithContentDescription("设置").assertDoesNotExist()
        rule.onNodeWithTag("epub-page-number").assertIsDisplayed()
        rule.onNodeWithTag("epub-pager").performTouchInput { click(percentOffset(0.5f, 0.5f)) }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("返回").assertIsDisplayed()
        rule.onNodeWithContentDescription("目录").assertIsDisplayed()
        rule.onNodeWithContentDescription("设置").assertIsDisplayed()
    }
}

/** Legacy Linux/aarch64 graphics cannot hit-test a Generic Path outline (R22.5).
 * Only the test theme's drawer corners change; all central taps, swipes and assertions stay real.
 */
@Composable
private fun LegacyHostReaderTheme(content: @Composable () -> Unit) {
    M1racleReaderTheme {
        MaterialTheme(shapes = MaterialTheme.shapes.copy(large = RoundedCornerShape(0.dp)), content = content)
    }
}
