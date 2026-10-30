package com.m1racle.reader.ui.home

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.m1racle.reader.MainActivity
import com.m1racle.reader.M1racleReaderApplication
import com.m1racle.reader.data.local.DocumentEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeLibraryNavigationInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun viewAllResetsPreviousFormatFilterAndRetainsEveryDocument() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as M1racleReaderApplication
        assertEquals("com.m1racle.reader.qa", app.packageName)
        val dao = app.database.documentDao()
        val prefix = "home-preview-${System.nanoTime()}"
        val docs = (1..9).map {
            DocumentEntity(id = "$prefix-$it", displayName = "首页测试 $it.md", uri = "content://fixture/$prefix/$it",
                mimeType = "text/markdown", fileSize = 1L, lastOpenedAt = System.currentTimeMillis() + it,
                createdAt = 1L, lastScrollIndex = 0, lastScrollOffset = 0, readingProgress = .25f,
                title = null, headingCount = 0, documentType = "MARKDOWN")
        }
        try {
            docs.forEach { dao.insertDocument(it) }
            rule.waitUntil(5000) { rule.onAllNodesWithTag("home-view-all").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("dock-icon-LIBRARY", useUnmergedTree = true).performTouchInput { click() }
            rule.onNode(hasText("PDF") and isSelectable()).performClick()
            rule.onNodeWithTag("dock-icon-HOME", useUnmergedTree = true).performTouchInput { click() }
            rule.onNodeWithTag("home-view-all").performScrollTo().performClick()
            rule.onNodeWithTag("hub-page-library").assertExists()
            rule.onNode(hasText("全部") and isSelectable()).assertIsSelected()
            rule.onNodeWithText("首页测试 9").assertIsDisplayed()
            rule.onNodeWithText("首页测试 1").performScrollTo().assertIsDisplayed()
            docs.forEach { assertEquals(it.id, dao.getById(it.id)?.id) }
        } finally {
            docs.forEach { dao.deleteById(it.id) }
        }
    }
}
