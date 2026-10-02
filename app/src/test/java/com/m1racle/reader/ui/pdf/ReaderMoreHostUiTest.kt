package com.m1racle.reader.ui.pdf

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.unit.dp
import com.m1racle.reader.ui.theme.M1racleReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

/**
 * UI gates: INLINE accordion + four advanced nested children with real bodies.
 * Main stays present (no parent alpha ghost) when a child is open.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(
    sdk = [34],
    qualifiers = "w411dp-h891dp-normal-notlong-notround-any-420dpi-keyshidden-nonav",
)
class ReaderMoreHostUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val submenuEntries = listOf(
        PdfChromeSubmenuEntry(ReaderMoreState.INLINE_READING_MODE, Icons.Outlined.SwapVert, "阅读方式"),
        PdfChromeSubmenuEntry(ReaderMoreState.INLINE_READING_UI, Icons.Outlined.AutoAwesome, "阅读界面"),
    )
    private val readingModeOptions = listOf(
        PdfChromeMenuOption("上下连续", selected = true, onClick = {}),
        PdfChromeMenuOption("左右翻页", selected = false, onClick = {}),
    )
    private val topBarModeOptions = listOf(
        PdfChromeMenuOption("普通", selected = false, onClick = {}),
        PdfChromeMenuOption("全屏沉浸", selected = true, onClick = {}),
    )

    private var lastState: ReaderMoreState? = null

    private fun advancedData() = ReaderAdvancedData(
        textbookPage = TextbookPageAdvancedData(
            currentPdfHumanPage = 23,
            currentPrint = null,
            isExact = false,
            onConfirm = {},
            onClear = {},
        ),
        outline = OutlineAdvancedData(
            nodes = emptyList(),
            currentPageIndex = 0,
            hasEmbeddedDrafts = false,
            onJumpToNode = {},
            onRequestEdit = {},
            onDelete = {},
            onImportEmbedded = {},
        ),
        thumbnails = null,
        export = ExportAdvancedData(
            plan = null,
            documentPageCount = 24,
            allNodes = emptyList(),
            initialFormat = com.m1racle.reader.pdf.export.ChapterExportFormat.ZIP_PACKAGE,
            onSelectNode = {},
            onConfirm = { _, _, _ -> },
        ),
    )

    private fun launchContent(
        initial: ReaderMoreState = ReaderMoreState.MAIN,
        fontScale: Float = 1f,
        height: Dp? = null,
        includeEditBody: Boolean = false,
        reverseTasks: Boolean = false,
        viewportBottomRoot: Float = 1800f,
    ) {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
            M1racleReaderTheme {
                var state by remember { mutableStateOf(initial) }
                var childClosing by remember { mutableStateOf(false) }
                lastState = state
                val taskItems = listOf(
                    PdfChromeMenuItem(
                        Icons.Outlined.Tag,
                        "设置教材页码",
                        childState = ReaderMoreState.CHILD_PAGE_MAPPING,
                    ),
                    PdfChromeMenuItem(
                        Icons.AutoMirrored.Outlined.MenuBook,
                        "教材目录",
                        childState = ReaderMoreState.CHILD_TOC,
                    ),
                    PdfChromeMenuItem(
                        Icons.Outlined.GridView,
                        "页面缩略图",
                        childState = ReaderMoreState.CHILD_THUMBNAILS,
                    ),
                    PdfChromeMenuItem(
                        Icons.Outlined.IosShare,
                        "导出章节",
                        childState = ReaderMoreState.CHILD_EXPORT,
                    ),
                )
                val items = (if (includeEditBody) listOf(PdfChromeMenuItem(Icons.Outlined.Tag, "编辑正文")) else emptyList()) +
                    (if (reverseTasks) taskItems.reversed() else taskItems)
                ReaderMoreHost(
                    state = state,
                    closing = false,
                    childClosing = childClosing,
                    contentDescription = "打开菜单",
                    onToggleOpen = { state = ReaderMoreStateMachine.onToggleOpen(state) },
                    onCloseRequest = { childClosing = true },
                    onChildCollapsed = {
                        childClosing = false
                        state = ReaderMoreState.MAIN
                    },
                    onOpenChild = { target -> state = ReaderMoreStateMachine.onOpenChild(state, target) },
                    onSectionToggle = { section ->
                        state = ReaderMoreStateMachine.onSectionToggle(state, section)
                    },
                    submenuEntries = submenuEntries,
                    plainMenuItems = items,
                    readingModeOptions = readingModeOptions,
                    topBarModeOptions = topBarModeOptions,
                    nestedData = advancedData(),
                    nestedMaxHeightDp = 520.dp,
                    viewportBottomRoot = viewportBottomRoot,
                    onPreparingLayoutReady = { state = it },
                    modifier = height?.let { Modifier.height(it) } ?: Modifier,
                )
            }
            }
        }
    }

    private fun assertAllLevel1Present() {
        listOf("阅读方式", "阅读界面", "设置教材页码", "教材目录", "页面缩略图", "导出章节")
            .forEach { composeRule.onAllNodesWithText(it).onFirst().assertExists() }
    }

    @Test
    fun `long press follows the finger inside a bound without activating a row then nested entry still opens`() {
        launchContent(ReaderMoreState.MAIN)
        val row = composeRule.onNodeWithText("阅读方式")
        val initial = row.getUnclippedBoundsInRoot()
        row.performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(700)
        row.performTouchInput {
            advanceEventTime(700)
            moveBy(Offset(-65f, 40f))
        }
        composeRule.waitForIdle()
        val moved = row.getUnclippedBoundsInRoot()
        val followedLeft = initial.left - moved.left
        val followedDown = moved.top - initial.top
        assertTrue("row should follow the finger left, dx=$followedLeft", followedLeft > 2.dp)
        assertTrue("row should follow the finger down, dy=$followedDown", followedDown > 2.dp)
        assertTrue("left follow stays inside the finger travel, dx=$followedLeft", followedLeft < 40.dp)
        assertTrue("down follow stays inside the finger travel, dy=$followedDown", followedDown < 28.dp)
        assertEquals(ReaderMoreState.MAIN, lastState)
        row.performTouchInput { up() }
        composeRule.waitForIdle()
        assertEquals(initial, row.getUnclippedBoundsInRoot())
        assertEquals(ReaderMoreState.MAIN, lastState)
        composeRule.onNodeWithText("教材目录").performClick()
        composeRule.waitForIdle()
        assertEquals(ReaderMoreState.CHILD_TOC, lastState)
    }

    @Test
    fun `main menu contains all level-1 entries`() {
        launchContent(ReaderMoreState.MAIN)
        assertAllLevel1Present()
        composeRule.onNodeWithText("上下连续").assertDoesNotExist()
    }

    @Test
    fun `inline reading mode expands inside main`() {
        launchContent(ReaderMoreState.MAIN)
        composeRule.onNodeWithText("阅读方式").performClick()
        composeRule.onNodeWithText("上下连续").assertExists()
        composeRule.onAllNodesWithText("阅读方式").assertCountEquals(1)
        assertAllLevel1Present()
    }

    @Test
    fun `TOC nested keeps Main upper rows present`() {
        launchContent(ReaderMoreState.MAIN)
        composeRule.onNodeWithText("教材目录").performClick()
        composeRule.waitForIdle()
        // Main parent still present (upper context).
        composeRule.onNodeWithText("阅读方式").assertExists()
        composeRule.onNodeWithText("阅读界面").assertExists()
        // Nested body: empty outline shows the dedicated empty-state guidance
        // (count line only renders when the outline is non-empty).
        composeRule.onAllNodesWithText("教材目录").onFirst().assertExists()
        composeRule.onNodeWithText("暂无目录项 · 可扫描 PDF 或从当前页新建").assertExists()
    }

    @Test
    fun `page mapping nested shows print field`() {
        launchContent(ReaderMoreState.MAIN)
        composeRule.onNodeWithText("设置教材页码").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("阅读方式").assertExists()
        composeRule.onAllNodesWithText("印刷页码").onFirst().assertExists()
        composeRule.onNodeWithText("保存").assertExists()
    }

    @Test
    fun `export nested shows format options`() {
        launchContent(ReaderMoreState.MAIN)
        composeRule.onNodeWithText("导出章节").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("阅读方式").assertExists()
        composeRule.onNodeWithText("请先建立或导入目录，再导出章节").assertExists()
    }

    @Test
    fun `TOC nested close returns to MAIN`() {
        launchContent(ReaderMoreState.MAIN)
        composeRule.onNodeWithText("教材目录").performClick()
        composeRule.waitForIdle()
        val nodes = composeRule.onAllNodesWithText("教材目录").fetchSemanticsNodes()
        var closed = false
        for (node in nodes.asReversed()) {
            try {
                val action = node.config[androidx.compose.ui.semantics.SemanticsActions.OnClick]
                action.action!!.invoke()
                closed = true
                break
            } catch (_: Exception) {
            }
        }
        org.junit.Assert.assertTrue(closed)
        composeRule.waitForIdle()
        org.junit.Assert.assertEquals(ReaderMoreState.MAIN, lastState)
        assertAllLevel1Present()
    }

    private fun assertChildOrigin(label: String, reverseTasks: Boolean = false, includeEditBody: Boolean = true) {
        launchContent(includeEditBody = includeEditBody, reverseTasks = reverseTasks, viewportBottomRoot = 2300f)
        val rowTop = composeRule.onNodeWithText(label).fetchSemanticsNode().boundsInRoot.top
        composeRule.onNodeWithText(label).performClick()
        composeRule.waitForIdle()
        val header = composeRule.onAllNodesWithText(label).fetchSemanticsNodes().last()
        org.junit.Assert.assertEquals("$label must expand at its clicked row", rowTop, header.boundsInRoot.top, 1f)
    }

    @Test fun `TOC originates from its own row with edit body preceding`() = assertChildOrigin("教材目录")
    @Test fun `page mapping originates from its own row with edit body preceding`() = assertChildOrigin("设置教材页码")
    @Test fun `thumbnails originate from their own row with edit body preceding`() = assertChildOrigin("页面缩略图")
    @Test fun `export originates from its own row with edit body preceding`() = assertChildOrigin("导出章节")

    @Test fun `TOC origin follows reordered entries`() = assertChildOrigin("教材目录", reverseTasks = true)
    @Test fun `page mapping origin follows reordered entries`() = assertChildOrigin("设置教材页码", reverseTasks = true)
    @Test fun `thumbnails origin follows reordered entries`() = assertChildOrigin("页面缩略图", reverseTasks = true)
    @Test fun `export origin follows reordered entries`() = assertChildOrigin("导出章节", reverseTasks = true)
    @Test fun `TOC origin remains correct without edit action`() = assertChildOrigin("教材目录", includeEditBody = false)

    @Test
    fun `opening reveals stationary full size menu text`() {
        composeRule.mainClock.autoAdvance = false
        launchContent()
        composeRule.mainClock.advanceTimeBy(160)
        val first = composeRule.onNodeWithText("阅读方式", useUnmergedTree = true).fetchSemanticsNode()
        val position = first.positionInRoot
        val size = first.size
        composeRule.mainClock.advanceTimeBy(96)
        val middle = composeRule.onNodeWithText("阅读方式", useUnmergedTree = true).fetchSemanticsNode()
        org.junit.Assert.assertEquals(position.x, middle.positionInRoot.x, 1f)
        org.junit.Assert.assertEquals(position.y, middle.positionInRoot.y, 1f)
        org.junit.Assert.assertEquals(size, middle.size)
        composeRule.mainClock.advanceTimeBy(1000)
        val end = composeRule.onNodeWithText("阅读方式", useUnmergedTree = true).fetchSemanticsNode()
        org.junit.Assert.assertEquals(position.x, end.positionInRoot.x, 1f)
        org.junit.Assert.assertEquals(position.y, end.positionInRoot.y, 1f)
        org.junit.Assert.assertEquals(size, end.size)
    }

    @Test
    fun `collapsed shows one more circle`() {
        launchContent(ReaderMoreState.CLOSED)
        composeRule.onAllNodesWithContentDescription("打开菜单").assertCountEquals(1)
    }

    @Test
    fun `short viewport can enter a child after collapsing inline options`() {
        launchContent(fontScale = 2f, height = 260.dp)
        composeRule.onNodeWithText("阅读方式").performClick()
        composeRule.onNodeWithText("设置教材页码").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        org.junit.Assert.assertEquals(ReaderMoreState.CHILD_PAGE_MAPPING, lastState)
        composeRule.onAllNodesWithText("印刷页码").onFirst().assertExists()
    }
}
