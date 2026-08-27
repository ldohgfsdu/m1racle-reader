package com.m1racle.reader.ui.reader

import com.m1racle.reader.data.anchor.DocumentAnchor
import com.m1racle.reader.data.local.BookmarkDao
import com.m1racle.reader.data.local.BookmarkEntity
import com.m1racle.reader.data.local.BookmarkWithDocument
import com.m1racle.reader.data.local.FavoriteDocumentDao
import com.m1racle.reader.data.local.FavoriteDocumentEntity
import com.m1racle.reader.data.local.HighlightDao
import com.m1racle.reader.data.local.HighlightEntity
import com.m1racle.reader.data.local.NoteDao
import com.m1racle.reader.data.local.NoteEntity
import com.m1racle.reader.data.repository.ReadingRepository
import com.m1racle.reader.ui.annotation.RepositoryAnnotationOverlaySource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R26.2 标注管理层契约：
 * 1. 列表查询排序（location ASC, createdAt ASC —— 与 DAO SQL 同序）；
 * 2. 跳转依据：行 location 即目标页（0-based），锚点可恢复；
 * 3. 删除后列表与 overlay 双双消失；
 * 4. 跨页面恢复：不同页各自覆盖项互不影响，且新 Source 实例（模拟重开）仍可读。
 */
class AnnotationManagementTest {

    private class FakeHighlightDao : HighlightDao {
        val rows = LinkedHashMap<Long, HighlightEntity>()
        private var nextId = 1L

        override suspend fun insert(highlight: HighlightEntity): Long {
            val id = nextId++
            rows[id] = highlight.copy(id = id)
            return id
        }

        override suspend fun delete(documentId: String, id: Long) {
            if (rows[id]?.documentId == documentId) rows.remove(id)
        }

        override suspend fun getById(documentId: String, id: Long): HighlightEntity? =
            rows[id]?.takeIf { it.documentId == documentId }

        override suspend fun listAtLocation(documentId: String, locationKind: String, location: Int) =
            rows.values.filter {
                it.documentId == documentId && it.locationKind == locationKind && it.location == location
            }.sortedWith(compareBy({ it.createdAt }, { it.id }))

        /** 与生产 SQL 同序：location ASC, createdAt ASC, id ASC。 */
        override suspend fun listForDocument(documentId: String): List<HighlightEntity> =
            rows.values.filter { it.documentId == documentId }
                .sortedWith(compareBy({ it.location }, { it.createdAt }, { it.id }))
    }

    private class FakeNoteDao : NoteDao {
        val rows = LinkedHashMap<Long, NoteEntity>()
        override suspend fun insert(note: NoteEntity): Long = unused()
        override suspend fun updateContent(documentId: String, id: Long, content: String, updatedAt: Long) = unused()
        override suspend fun delete(documentId: String, id: Long) = unused()
        override suspend fun getById(documentId: String, id: Long): NoteEntity? = null
        override suspend fun listAtLocation(documentId: String, locationKind: String, location: Int) =
            rows.values.filter {
                it.documentId == documentId && it.locationKind == locationKind && it.location == location
            }.sortedWith(compareBy({ it.createdAt }, { it.id }))
        override suspend fun listForDocument(documentId: String): List<NoteEntity> =
            rows.values.filter { it.documentId == documentId }

        private fun unused(): Nothing = throw UnsupportedOperationException()
    }

    private class NoBookmarks : BookmarkDao {
        override suspend fun insert(bookmark: BookmarkEntity): Long = unused()
        override suspend fun delete(id: Long) = unused()
        override suspend fun listForDocument(documentId: String): List<BookmarkEntity> = unused()
        override fun observeAll(): Flow<List<BookmarkWithDocument>> = unused()
        override suspend fun getAt(documentId: String, locationKind: String, location: Int) = unused()
        override suspend fun deleteAt(documentId: String, locationKind: String, location: Int): Int = unused()
        override suspend fun listForDocumentAllKinds(documentId: String): List<BookmarkEntity> = unused()

        private fun unused(): Nothing = throw UnsupportedOperationException()
    }

    private class NoFavorites : FavoriteDocumentDao {
        override suspend fun insert(favorite: FavoriteDocumentEntity): Long = unused()
        override suspend fun delete(documentId: String) = unused()
        override suspend fun getAll(): List<FavoriteDocumentEntity> = unused()
        override suspend fun get(documentId: String): FavoriteDocumentEntity? = unused()

        private fun unused(): Nothing = throw UnsupportedOperationException()
    }

    @Test
    fun `list query sorts by page then creation time`() = runTest {
        val repo = ReadingRepository(NoBookmarks(), NoFavorites(), FakeHighlightDao(), FakeNoteDao(), com.m1racle.reader.testing.testSearchRepository())
        val controller = ReaderHighlightController(repo, "doc-1")
        controller.commitHighlight(captureRect(9), nowMs = 100L)
        controller.commitHighlight(captureRect(3), nowMs = 200L)
        controller.commitHighlight(captureRect(9), nowMs = 150L)

        val rows = controller.listForDocument()
        assertEquals(listOf(3, 9, 9), rows.map { it.location })
        assertEquals(listOf(200L, 100L, 150L), rows.map { it.createdAt })
    }

    @Test
    fun `row location is the jump target and anchor restores to same page`() = runTest {
        val repo = ReadingRepository(NoBookmarks(), NoFavorites(), FakeHighlightDao(), FakeNoteDao(), com.m1racle.reader.testing.testSearchRepository())
        val controller = ReaderHighlightController(repo, "pdf-7")
        controller.commitHighlight(captureRect(41), nowMs = 10L)

        val row = controller.listForDocument().single()
        // 跳转语义：goToPage(row.location)；0-based 页索引即锚点所在页。
        assertEquals(41, row.location)
        val anchor = repo.resolveHighlightAnchor("pdf-7", row.id) as DocumentAnchor.PdfPage
        assertEquals(row.location, anchor.page)
        assertTrue(anchor.rect != null) // 矩形随锚点完整恢复
    }

    @Test
    fun `delete removes from list and overlay`() = runTest {
        val repo = ReadingRepository(NoBookmarks(), NoFavorites(), FakeHighlightDao(), FakeNoteDao(), com.m1racle.reader.testing.testSearchRepository())
        val controller = ReaderHighlightController(repo, "doc-1")
        val committed = controller.commitHighlight(captureRect(4)) as ReaderHighlightController.CommitResult.Success

        assertEquals(1, controller.listForDocument().size)
        assertEquals(1, controller.itemsForPage(4).size)

        controller.deleteHighlight(committed.id)

        assertTrue(controller.listForDocument().isEmpty())
        assertTrue(controller.itemsForPage(4).isEmpty())
    }

    @Test
    fun `cross page highlights restore independently and survive new source instances`() = runTest {
        val highlightDao = FakeHighlightDao()
        val repo = ReadingRepository(NoBookmarks(), NoFavorites(), highlightDao, FakeNoteDao(), com.m1racle.reader.testing.testSearchRepository())
        val controller = ReaderHighlightController(repo, "doc")
        controller.commitHighlight(captureRect(2))
        controller.commitHighlight(captureRect(7))

        // 各页覆盖互不可见。
        assertEquals(setOf(2), controller.itemsForPage(2).map { it.anchor.let { a -> (a as DocumentAnchor.PdfPage).page } }.toSet())
        assertEquals(1, controller.itemsForPage(7).size)

        // 模拟文档重开：全新 OverlaySource 实例读取同一持久化数据。
        val freshSource = RepositoryAnnotationOverlaySource(repo)
        assertEquals(1, freshSource.itemsFor("doc", "PAGE_INDEX", 2).size)
        assertEquals(1, freshSource.itemsFor("doc", "PAGE_INDEX", 7).size)
        assertTrue(freshSource.itemsFor("doc", "PAGE_INDEX", 5).isEmpty())
    }

    private fun captureRect(
        page: Int,
        l: Float = 30f,
        t: Float = 40f,
        r: Float = 260f,
        b: Float = 140f,
    ) =
        com.m1racle.reader.ui.annotation.TextSelection.PdfSelection(
            page = page,
            leftPx = l,
            topPx = t,
            rightPx = r,
            bottomPx = b,
            pageWidthPx = 800f,
            pageHeightPx = 1131f,
        )
}
