package com.m1racle.reader.ui.home

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.m1racle.reader.data.local.DocumentEntity
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
class HomeDocumentActionsUiTest {
    @get:Rule val rule = createComposeRule()
    private val filename = "相同前缀的长文件名-这是需要辨认的完整版本名称.pdf"
    private val document = DocumentEntity(
        id = "only-book", displayName = filename, uri = "content://test/book.pdf",
        mimeType = "application/pdf", fileSize = 12L, lastOpenedAt = 1L,
        createdAt = 1L, lastScrollIndex = 0, lastScrollOffset = 0,
        readingProgress = 0.2f, title = null, headingCount = 0,
        documentType = "PDF", pageCount = 5,
    )

    @Test fun `compact row menu omits repeated filename and confirmation never removes on cancel`() {
        var removed = 0
        var opened = 0
        rule.setContent {
            M1racleReaderTheme {
                DocumentListRow(document, onOpen = { opened++ }, onDelete = { removed++ })
            }
        }
        rule.onNodeWithContentDescription("文档操作").performClick()
        rule.onNode(hasText(filename) and hasAnyAncestor(isPopup())).assertDoesNotExist()
        rule.onNodeWithText("从文库移除").performClick()
        rule.onNode(hasText(filename) and hasAnyAncestor(isDialog())).assertIsDisplayed()
        rule.runOnIdle { assertEquals(0, removed); assertEquals(0, opened) }
        rule.onNodeWithText("取消").performClick()
        rule.runOnIdle { assertEquals(0, removed); assertEquals(0, opened) }
        rule.onNodeWithContentDescription("文档操作").performClick()
        rule.onNodeWithText("从文库移除").performClick()
        rule.onNodeWithText("移除").performClick()
        rule.runOnIdle { assertEquals(1, removed); assertEquals(0, opened) }
    }

    @Test fun `only continue reading document has the same confirmed removal`() {
        var removed = 0
        var opened = 0
        rule.setContent {
            M1racleReaderTheme {
                ContinueReadingCard(document, "1 / 5", onOpen = { opened++ }, onDelete = { removed++ })
            }
        }
        rule.onNodeWithContentDescription("文档操作").performClick()
        rule.onNodeWithText("从文库移除").performClick()
        rule.onNodeWithText("将清除这份文档的阅读进度、书签、收藏、批注和应用内副本，无法撤销。不会删除原始文件。")
            .assertIsDisplayed()
        rule.runOnIdle { assertEquals(0, removed); assertEquals(0, opened) }
        rule.onNodeWithText("移除").performClick()
        rule.runOnIdle { assertEquals(1, removed); assertEquals(0, opened) }
    }
    @Test fun `full name is readable without entering removal or opening document`() {
        var removed = 0
        var opened = 0
        rule.setContent {
            M1racleReaderTheme {
                DocumentListRow(document, onOpen = { opened++ }, onDelete = { removed++ })
            }
        }
        rule.onNodeWithContentDescription("文档操作").performClick()
        rule.onNodeWithText("文件信息").performClick()
        rule.onNode(hasText(filename) and hasAnyAncestor(isDialog())).assertIsDisplayed()
        rule.onNodeWithText("移除").assertDoesNotExist()
        rule.onNodeWithText("关闭").performClick()
        rule.runOnIdle { assertEquals(0, removed); assertEquals(0, opened) }
    }

}
