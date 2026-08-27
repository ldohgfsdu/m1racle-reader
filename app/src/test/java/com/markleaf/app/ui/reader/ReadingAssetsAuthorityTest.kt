package com.markleaf.app.ui.reader

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R23 仓库权威钉：阅读器书签写入的唯一路径是
 * ReaderBookmarkController → ReadingRepository；两阅读器 UI 禁止直访 DAO。
 */
class ReadingAssetsAuthorityTest {

    @Test
    fun `reader screens never touch dao - only the controller`() {
        for (name in listOf("PdfViewerScreen.kt", "ViewerScreen.kt")) {
            val src = norm(readerSource(name))
            assertFalse("$name must not import DAOs", src.contains("data.local.BookmarkDao"))
            assertFalse("$name must not use ReadingRepository directly", src.contains("ReadingRepository"))
            assertTrue("$name must receive the controller", src.contains("ReaderBookmarkController"))
            assertTrue("$name must write via controller.toggle", src.contains(".toggle("))
        }
    }

    @Test
    fun `controller is constructed in nav graph with per reader coordinate systems`() {
        val nav = norm(source("app/src/main/java/com/markleaf/app/ui/navigation/NavGraph.kt").readText())
        val pdfCount = Regex("BookmarkLocationKind.PAGE_INDEX").findAll(nav).count()
        val mdCount = Regex("BookmarkLocationKind.SCROLL_POSITION").findAll(nav).count()
        assertTrue("PDF route must wire PAGE_INDEX controller", pdfCount >= 1)
        assertTrue("Markdown route must wire SCROLL_POSITION controller", mdCount >= 1)
        assertTrue(nav.contains("ReaderBookmarkController("))
    }

    @Test
    fun `repository stays the single bookmark writer facade`() {
        val repo = norm(source("app/src/main/java/com/markleaf/app/data/repository/ReadingRepository.kt").readText())
        assertTrue(repo.contains("findBookmarkAt"))
        assertTrue(repo.contains("removeBookmarkAt"))
        assertTrue(repo.contains("bookmarkedLocations"))
        val controller = norm(
            source("app/src/main/java/com/markleaf/app/ui/reader/ReaderBookmarkController.kt").readText(),
        )
        // 控制器只经由仓库，不绕过到 DAO。
        assertFalse(controller.contains("Dao"))
    }

    private fun source(repoRelative: String): File =
        listOf(File(repoRelative), File("/root/code/MarkLeaf/$repoRelative")).first { it.isFile }

    private fun readerSource(name: String): String = listOf(
        File("src/main/java/com/markleaf/app/ui/pdf/$name"),
        File("app/src/main/java/com/markleaf/app/ui/pdf/$name"),
        File("/root/code/MarkLeaf/app/src/main/java/com/markleaf/app/ui/pdf/$name"),
        File("src/main/java/com/markleaf/app/ui/viewer/$name"),
        File("app/src/main/java/com/markleaf/app/ui/viewer/$name"),
        File("/root/code/MarkLeaf/app/src/main/java/com/markleaf/app/ui/viewer/$name"),
    ).firstOrNull { it.isFile }?.readText() ?: error("missing $name")

    private fun norm(s: String): String = s.replace(Regex("\\s+"), " ")
}
