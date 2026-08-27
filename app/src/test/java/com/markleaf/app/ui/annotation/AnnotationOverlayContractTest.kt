package com.markleaf.app.ui.annotation

import com.markleaf.app.data.anchor.DocumentAnchor
import com.markleaf.app.data.local.BookmarkDao
import com.markleaf.app.data.local.BookmarkEntity
import com.markleaf.app.data.local.BookmarkLocationKind
import com.markleaf.app.data.local.BookmarkWithDocument
import com.markleaf.app.data.local.FavoriteDocumentDao
import com.markleaf.app.data.local.FavoriteDocumentEntity
import com.markleaf.app.data.local.HighlightDao
import com.markleaf.app.data.local.HighlightEntity
import com.markleaf.app.data.local.NoteDao
import com.markleaf.app.data.local.NoteEntity
import com.markleaf.app.data.repository.ReadingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R25 覆盖层数据契约：按 (kind, location) 过滤、锚点恢复、PDF 归一化矩形 /
 * MD 字符区间的只读映射。
 */
class AnnotationOverlayContractTest {

    private class FakeHighlightDao : HighlightDao {
        val rows = LinkedHashMap<Long, HighlightEntity>()
        override suspend fun insert(highlight: HighlightEntity): Long {
            val id = rows.size + 1L
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
        override suspend fun listForDocument(documentId: String): List<HighlightEntity> =
            rows.values.filter { it.documentId == documentId }
    }

    private class FakeNoteDao : NoteDao {
        val rows = LinkedHashMap<Long, NoteEntity>()
        override suspend fun insert(note: NoteEntity): Long {
            val id = rows.size + 1L
            rows[id] = note.copy(id = id)
            return id
        }
        override suspend fun updateContent(documentId: String, id: Long, content: String, updatedAt: Long) = error()
        override suspend fun delete(documentId: String, id: Long) {
            if (rows[id]?.documentId == documentId) rows.remove(id)
        }
        override suspend fun getById(documentId: String, id: Long): NoteEntity? =
            rows[id]?.takeIf { it.documentId == documentId }
        override suspend fun listAtLocation(documentId: String, locationKind: String, location: Int) =
            rows.values.filter {
                it.documentId == documentId && it.locationKind == locationKind && it.location == location
            }.sortedWith(compareBy({ it.createdAt }, { it.id }))
        override suspend fun listForDocument(documentId: String): List<NoteEntity> =
            rows.values.filter { it.documentId == documentId }

        private fun error(): Nothing = throw UnsupportedOperationException()
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
    fun `overlay source filters by kind and location and restores anchors`() = runTest {
        val highlights = FakeHighlightDao()
        val notes = FakeNoteDao()
        val repo = ReadingRepository(NoBookmarks(), NoFavorites(), highlights, notes, com.markleaf.app.testing.testSearchRepository())
        // 页 9：一个高亮；页 10 与 MD 块：不应出现在页 9 查询里。
        repo.addHighlight(
            "pdf-1",
            DocumentAnchor.PdfPage(page = 9, rect = com.markleaf.app.data.anchor.NormalizedRect(0.1f, 0.2f, 0.3f, 0.1f)),
            createdAt = 1L,
            excerpt = "sel",
        )
        repo.addHighlight("pdf-1", DocumentAnchor.PdfPage(page = 10), createdAt = 2L)
        repo.addHighlight(
            "md-1",
            DocumentAnchor.MarkdownBlock(blockIndex = 9, charStart = 0, charEnd = 5),
            createdAt = 3L,
        )
        repo.addNote(
            "pdf-1",
            DocumentAnchor.PdfPage(page = 9),
            content = "页九批注",
            createdAt = 4L,
        )

        val source = RepositoryAnnotationOverlaySource(repo)
        val page9 = source.itemsFor("pdf-1", "PAGE_INDEX", 9)
        assertEquals(2, page9.size)
        assertTrue(page9.all { it.pdfRectNormalized() != null || it.type == AnnotationType.NOTE })
        val rectItem = page9.first { it.type == AnnotationType.HIGHLIGHT }
        assertEquals(0.1f, rectItem.pdfRectNormalized()!!.x, 1e-6f)

        val block9 = source.itemsFor("md-1", "SCROLL_POSITION", 9)
        assertEquals(1, block9.size)
        assertEquals(0 to 5, block9.single().markdownRange())

        // 无覆盖位置 → 空快照。
        assertTrue(source.itemsFor("pdf-1", "PAGE_INDEX", 999).isEmpty())
        assertTrue(source.itemsFor("pdf-1", "FUTURE_KIND", 9).isEmpty())
    }

    @Test
    fun `note overlay items expose null pdf rect safely`() = runTest {
        val repo = ReadingRepository(NoBookmarks(), NoFavorites(), FakeHighlightDao(), FakeNoteDao(), com.markleaf.app.testing.testSearchRepository())
        repo.addNote("d", DocumentAnchor.PdfPage(page = 3), content = "n", createdAt = 1L)
        val item = RepositoryAnnotationOverlaySource(repo)
            .itemsFor("d", "PAGE_INDEX", 3)
            .single { it.type == AnnotationType.NOTE }
        assertNull(item.pdfRectNormalized())
        assertNull(item.markdownRange())
    }
}
