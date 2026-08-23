package com.markleaf.app.ui.pdf

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
import androidx.compose.ui.unit.dp
import com.markleaf.app.ui.theme.MarkLeafTheme
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
            initialFormat = com.markleaf.app.pdf.export.ChapterExportFormat.ZIP_PACKAGE,
            onSelectNode = {},
            onConfirm = { _, _, _ -> },
        ),
    )

    private fun launchContent(initial: ReaderMoreState = ReaderMoreState.MAIN) {
        composeRule.setContent {
            MarkLeafTheme {
                var state by remember { mutableStateOf(initial) }
                var childClosing by remember { mutableStateOf(false) }
                lastState = state
                val items = listOf(
                    PdfChromeMenuItem(
                        Icons.Outlined.Tag,
                        "设置教材页码",
                        onClick = {
                            state = ReaderMoreStateMachine.onOpenChild(state, ReaderMoreState.CHILD_PAGE_MAPPING)
                        },
                        closeOnClick = false,
                    ),
                    PdfChromeMenuItem(
                        Icons.AutoMirrored.Outlined.MenuBook,
                        "教材目录",
                        onClick = {
                            state = ReaderMoreStateMachine.onOpenChild(state, ReaderMoreState.CHILD_TOC)
                        },
                        closeOnClick = false,
                    ),
                    PdfChromeMenuItem(
                        Icons.Outlined.GridView,
                        "页面缩略图",
                        onClick = {
                            state = ReaderMoreStateMachine.onOpenChild(state, ReaderMoreState.CHILD_THUMBNAILS)
                        },
                        closeOnClick = false,
                    ),
                    PdfChromeMenuItem(
                        Icons.Outlined.IosShare,
                        "导出章节",
                        onClick = {
                            state = ReaderMoreStateMachine.onOpenChild(state, ReaderMoreState.CHILD_EXPORT)
                        },
                        closeOnClick = false,
                    ),
                )
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
                    onSectionToggle = { section ->
                        state = ReaderMoreStateMachine.onSectionToggle(state, section)
                    },
                    submenuEntries = submenuEntries,
                    plainMenuItems = items,
                    readingModeOptions = readingModeOptions,
                    topBarModeOptions = topBarModeOptions,
                    nestedData = advancedData(),
                    nestedMaxHeightDp = 520.dp,
                    viewportBottomRoot = 1800f,
                )
            }
        }
    }

    private fun assertAllLevel1Present() {
        listOf("阅读方式", "阅读界面", "设置教材页码", "教材目录", "页面缩略图", "导出章节")
            .forEach { composeRule.onAllNodesWithText(it).onFirst().assertExists() }
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

    @Test
    fun `collapsed shows one more circle`() {
        launchContent(ReaderMoreState.CLOSED)
        composeRule.onAllNodesWithContentDescription("打开菜单").assertCountEquals(1)
    }
}
