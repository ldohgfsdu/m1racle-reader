package com.m1racle.reader.ui.epub

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
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

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun `large default large reflow keeps the same text anchor inside a long list item`() {
        val text = (0..3_000).joinToString(" ") { "word" + it.toString().padStart(4, '0') }
        val longBook = book.copy(chapters = listOf(book.chapters.first().copy(blocks = listOf(
            EpubBlock.ListBlock(false, listOf(listOf(EpubInline(text)), listOf(EpubInline("End")))),
        ))))
        val settings = mutableStateOf(ReaderSettings(fontScale = ReaderSettings.FONT_LARGE))
        rule.setContent {
            LegacyHostReaderTheme {
                EpubReaderContent(longBook, settings.value, "测试书", EpubSavedPosition.Start,
                    onPosition = { }, onFlush = {}, onBack = {}, onSettings = {},
                    modifier = Modifier.size(360.dp, 560.dp), insets = WindowInsets(0, 0, 0, 0))
            }
        }
        waitForPages()
        repeat(5) {
            rule.onNodeWithTag("epub-pager").performTouchInput { click(percentOffset(0.88f, 0.5f)) }
            rule.waitForIdle()
        }
        val before = pageLabel()
        val visible = rule.onAllNodes(hasText("word", substring = true)).fetchSemanticsNodes()
            .first { it.boundsInRoot.overlaps(rule.onNodeWithTag("epub-reading-area").fetchSemanticsNode().boundsInRoot) }
            .config[SemanticsProperties.Text].joinToString { it.text }
        val anchor = Regex("word\\d{4}").find(visible)!!.value
        assertFalse(before.startsWith("第 1 /"))
        rule.runOnIdle { settings.value = settings.value.copy(fontScale = ReaderSettings.FONT_DEFAULT) }
        rule.waitUntil(15_000) { pageLabel().substringAfter('/') != before.substringAfter('/') }
        rule.waitForIdle()
        rule.onNodeWithText(anchor, substring = true).assertIsDisplayed()
        rule.runOnIdle { settings.value = settings.value.copy(fontScale = ReaderSettings.FONT_LARGE) }
        rule.waitUntil(15_000) { pageLabel().substringAfter('/') == before.substringAfter('/') }
        rule.waitForIdle()
        assertEquals(before, pageLabel())
        rule.onNodeWithText(anchor, substring = true).assertIsDisplayed()
    }

    @Test fun `dragging past half does not commit footer or position before the page settles`() {
        var savedRow = 0
        rule.setContent {
            LegacyHostReaderTheme {
                EpubReaderContent(book, ReaderSettings(), "测试书", EpubSavedPosition.Start,
                    onPosition = { savedRow = it.sourceRow }, onFlush = {}, onBack = {}, onSettings = {},
                    modifier = Modifier.size(360.dp, 560.dp), insets = WindowInsets(0, 0, 0, 0))
            }
        }
        waitForPages()
        val initial = pageLabel()
        rule.onNodeWithTag("epub-pager").performTouchInput {
            down(percentOffset(0.9f, 0.5f))
            moveTo(percentOffset(0.75f, 0.5f), delayMillis = 120)
            moveTo(percentOffset(0.25f, 0.5f), delayMillis = 400)
        }
        rule.waitForIdle()
        assertEquals("The visible number must describe the committed reading position", initial, pageLabel())
        assertEquals(0, savedRow)
        rule.onNodeWithTag("epub-pager").performTouchInput {
            moveTo(percentOffset(0.8f, 0.5f), delayMillis = 400)
            advanceEventTime(200)
            up()
        }
        rule.waitForIdle()
        assertEquals(initial, pageLabel())
        assertEquals(0, savedRow)
        rule.onNodeWithTag("epub-pager").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertTrue(pageLabel().startsWith("第 2 /"))
        assertTrue(savedRow > 0)
    }

    @Test fun `large system text leaves footer outside reading area without reflow on chrome toggle`() {
        rule.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides
                androidx.compose.ui.unit.Density(density.density, 2f)) {
                LegacyHostReaderTheme {
                    EpubReaderContent(book, ReaderSettings(), "测试书", EpubSavedPosition.Start,
                        onPosition = { }, onFlush = {}, onBack = {}, onSettings = {},
                        modifier = Modifier.size(360.dp, 560.dp), insets = WindowInsets(0, 0, 0, 0))
                }
            }
        }
        waitForPages()
        val before = rule.onNodeWithTag("epub-reading-area").fetchSemanticsNode().boundsInRoot
        val label = pageLabel()
        val footer = rule.onNodeWithTag("epub-page-number").fetchSemanticsNode().boundsInRoot
        assertTrue("reading=$before footer=$footer", before.bottom <= footer.top)
        rule.onNodeWithTag("epub-pager").performTouchInput { click(percentOffset(0.5f, 0.5f)) }
        rule.waitForIdle()
        val chapter = rule.onNodeWithText("第一章 ·", substring = true).fetchSemanticsNode().boundsInRoot
        assertTrue("reading=$before chapter=$chapter", before.bottom <= chapter.top)
        for (description in listOf("返回", "目录", "设置")) {
            val control = rule.onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot
            assertTrue("$description=$control must be above reading=$before", control.bottom <= before.top)
        }
        assertEquals(before, rule.onNodeWithTag("epub-reading-area").fetchSemanticsNode().boundsInRoot)
        assertEquals(label, pageLabel())
    }

    @Test fun `revealing controls preserves visible text and keeps all top controls outside the page`() {
        var settingsOpened = false
        var backClicked = false
        rule.setContent {
            LegacyHostReaderTheme {
                EpubReaderContent(book, ReaderSettings(), "很长的测试书名用于检查标题省略而不会挤占目录设置", EpubSavedPosition.Start,
                    onPosition = { }, onFlush = {}, onBack = { backClicked = true },
                    onSettings = { settingsOpened = true }, modifier = Modifier.size(320.dp, 560.dp),
                    insets = WindowInsets(0, 0, 0, 0))
            }
        }
        waitForPages()
        val reading = rule.onNodeWithTag("epub-reading-area").fetchSemanticsNode().boundsInRoot
        val label = pageLabel()
        val text = rule.onNodeWithText("第一章段落 0", substring = true).fetchSemanticsNode()
            .config[SemanticsProperties.Text]
        repeat(2) {
            rule.onNodeWithTag("epub-pager").performTouchInput { click(center) }
            rule.waitForIdle()
            for (description in listOf("返回", "目录", "设置")) {
                val control = rule.onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot
                assertTrue("$description=$control reading=$reading", control.bottom <= reading.top)
            }
            assertEquals(reading, rule.onNodeWithTag("epub-reading-area").fetchSemanticsNode().boundsInRoot)
            assertEquals(label, pageLabel())
            assertEquals(text, rule.onNodeWithText("第一章段落 0", substring = true).fetchSemanticsNode()
                .config[SemanticsProperties.Text])
            rule.onNodeWithContentDescription("设置").performClick()
            rule.runOnIdle { assertTrue(settingsOpened) }
            rule.onNodeWithTag("epub-pager").performTouchInput { click(center) }
            rule.waitForIdle()
            rule.onNodeWithContentDescription("设置").assertDoesNotExist()
            assertEquals(reading, rule.onNodeWithTag("epub-reading-area").fetchSemanticsNode().boundsInRoot)
        }
        rule.onNodeWithTag("epub-pager").performTouchInput { click(center) }
        rule.onNodeWithContentDescription("返回").performClick()
        rule.runOnIdle { assertTrue(backClicked) }
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
        var saved = EpubSavedPosition.Start
        val shown = mutableStateOf(true)
        rule.setContent {
            LegacyHostReaderTheme {
                if (shown.value) EpubReaderContent(book, ReaderSettings(), "测试书", saved,
                    onPosition = { saved = it.saved() },
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
        rule.onNodeWithText("关闭").assertDoesNotExist()
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

    @Test fun `swiping and TOC jumps show each same-file chapter at the first line with its own footer`() {
        val oneFile = book.copy(spine = listOf(book.spine.first()),
            toc = listOf(EpubTocNode("同文件第一章", "one.xhtml#first", 0, emptyList()),
                EpubTocNode("同文件第二章", "one.xhtml#second", 0, emptyList())),
            chapters = listOf(EpubChapter(0, "one.xhtml", "同文件第一章", listOf(
                EpubBlock.Heading(1, "同文件第一章"), EpubBlock.Paragraph(listOf(EpubInline("上一章结尾"))),
                EpubBlock.Heading(1, "同文件第二章"), EpubBlock.Paragraph(listOf(EpubInline("下一章正文"))),
            ), mapOf("first" to 0, "second" to 2))))
        rule.setContent {
            LegacyHostReaderTheme {
                EpubReaderContent(oneFile, ReaderSettings(), "测试书", EpubSavedPosition.Start,
                    onPosition = { }, onFlush = {}, onBack = {}, onSettings = {},
                    modifier = Modifier.size(360.dp, 560.dp), insets = WindowInsets(0, 0, 0, 0))
            }
        }
        waitForPages()
        fun visibleText(): String {
            val bounds = rule.onNodeWithTag("epub-reading-area").fetchSemanticsNode().boundsInRoot
            return rule.onAllNodes(hasText("同文件", substring = true)).fetchSemanticsNodes()
                .first { it.boundsInRoot.overlaps(bounds) }.config[SemanticsProperties.Text].joinToString { it.text }
        }
        assertEquals("第 1 / 2 页", pageLabel())
        assertFalse(visibleText().contains("同文件第二章"))
        rule.onNodeWithTag("epub-pager").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals("第 2 / 2 页", pageLabel())
        assertTrue(visibleText().startsWith("同文件第二章"))
        assertFalse(visibleText().contains("上一章结尾"))
        rule.onNodeWithTag("epub-pager").performTouchInput { click(center) }
        rule.onNodeWithText("同文件第二章 · 1/1").assertIsDisplayed()
        rule.onNodeWithTag("epub-pager").performTouchInput { swipeRight() }
        rule.waitForIdle()
        rule.onNodeWithTag("epub-pager").performTouchInput { click(center) }
        rule.onNodeWithContentDescription("目录").performClick()
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNode(hasText("同文件第二章") and hasClickAction()).performClick()
        rule.waitForIdle()
        assertEquals("第 2 / 2 页", pageLabel())
        assertTrue(visibleText().startsWith("同文件第二章"))
        assertFalse(visibleText().contains("上一章结尾"))
    }

    @Test fun `long directory opens at the active chapter instead of the first entry`() {
        val toc = List(80) { EpubTocNode("目录条目 $it", "chapter-$it.xhtml", it, emptyList()) }
        rule.setContent {
            M1racleReaderTheme {
                EpubTocPanel("测试书", toc, toc.associate { tocKey(it) to it.spineIndex!! }, 75, {},
                    modifier = Modifier.size(340.dp, 400.dp))
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("目录条目 75").assertIsDisplayed()
        rule.onNodeWithText("目录条目 0").assertDoesNotExist()
    }

    @Test fun `directory header and list stay within status and navigation safe regions`() {
        rule.setContent {
            M1racleReaderTheme {
                EpubTocPanel("测试书", book.toc, mapOf(tocKey(book.toc[0]) to 0, tocKey(book.toc[1]) to 5), 0, {},
                    modifier = Modifier.windowInsetsPadding(WindowInsets(top = 32.dp, bottom = 24.dp)))
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
                EpubReaderContent(book, settings.value, "测试书", EpubSavedPosition.Start,
                    onPosition = { savedRow = it.sourceRow }, onFlush = {}, onBack = {}, onSettings = {},
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
                EpubReaderContent(book, ReaderSettings(), "测试书", EpubSavedPosition.Start,
                    onPosition = { }, onFlush = {}, onBack = {}, onSettings = {},
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
