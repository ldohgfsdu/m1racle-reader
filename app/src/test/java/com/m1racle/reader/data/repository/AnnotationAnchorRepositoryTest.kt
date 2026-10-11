package com.m1racle.reader.data.repository

import com.m1racle.reader.data.anchor.DocumentAnchor
import com.m1racle.reader.data.local.BookmarkDao
import com.m1racle.reader.data.local.BookmarkEntity
import com.m1racle.reader.data.local.BookmarkLocationKind
import com.m1racle.reader.data.local.BookmarkWithDocument
import com.m1racle.reader.data.local.FavoriteDocumentDao
import com.m1racle.reader.data.local.FavoriteDocumentEntity
import com.m1racle.reader.data.local.HighlightDao
import com.m1racle.reader.data.local.HighlightEntity
import com.m1racle.reader.data.local.NoteDao
import com.m1racle.reader.data.local.NoteEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * R24 仓库级锚点往返：addHighlight/addNote 以 DocumentAnchor 为入口，
 * 存储投影（kind/location/anchor 列）由解析器单点派生，恢复端还原等价锚点。
 */
class AnnotationAnchorRepositoryTest {

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

        override suspend fun listForDocument(documentId: String): List<HighlightEntity> =
            rows.values.filter { it.documentId == documentId }
                .sortedWith(compareBy({ it.location }, { it.createdAt }, { it.id }))
    }

    private class FakeNoteDao : NoteDao {
        val rows = LinkedHashMap<Long, NoteEntity>()
        private var nextId = 1L

        override suspend fun insert(note: NoteEntity): Long {
            val id = nextId++
            rows[id] = note.copy(id = id)
            return id
        }

        override suspend fun updateContent(documentId: String, id: Long, content: String, updatedAt: Long) {
            rows[id] = rows[id]?.copy(content = content, updatedAt = updatedAt)
                ?: throw IllegalStateException("missing note $id")
        }

        override suspend fun delete(documentId: String, id: Long) {
            rows.remove(id)
        }

        override suspend fun getById(documentId: String, id: Long): NoteEntity? =
            rows[id]?.takeIf { it.documentId == documentId }

        override suspend fun listAtLocation(documentId: String, locationKind: String, location: Int) =
            rows.values.filter {
                it.documentId == documentId && it.locationKind == locationKind && it.location == location
            }.sortedWith(compareBy({ it.createdAt }, { it.id }))

        override suspend fun listForDocument(documentId: String): List<NoteEntity> =
            rows.values.filter { it.documentId == documentId }
                .sortedWith(compareBy({ it.location }, { it.createdAt }, { it.id }))
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
    fun `pdf highlight stores projected columns and restores equivalent anchor`() = runTest {
        val repo = ReadingRepository(NoBookmarks(), NoFavorites(), FakeHighlightDao(), FakeNoteDao(), com.m1racle.reader.testing.testSearchRepository(), transactionRunner = com.m1racle.reader.testing.ImmediateRepositoryTransactionRunner())
        val anchor = DocumentAnchor.PdfPage(
            page = 12,
            rects = listOf(com.m1racle.reader.data.anchor.NormalizedRect(0.2f, 0.3f, 0.4f, 0.1f)),
        )
        val id = repo.addHighlight("pdf-1", anchor, createdAt = 10L, excerpt = "原文")
        assertEquals(12, repo.highlightsForDocument("pdf-1").single().location)
        assertEquals(anchor, repo.resolveHighlightAnchor("pdf-1", id))
    }

    @Test
    fun `markdown note stores block projection and restores anchor with char range`() = runTest {
        val repo = ReadingRepository(NoBookmarks(), NoFavorites(), FakeHighlightDao(), FakeNoteDao(), com.m1racle.reader.testing.testSearchRepository(), transactionRunner = com.m1racle.reader.testing.ImmediateRepositoryTransactionRunner())
        val anchor = DocumentAnchor.MarkdownBlock(blockIndex = 96, charStart = 2, charEnd = 18)
        val id = repo.addNote(
            documentId = "md-1",
            anchor = anchor,
            content = "批注",
            createdAt = 7L,
        )
        val stored = repo.notesForDocument("md-1").single()
        assertEquals(96, stored.location)
        assertEquals(7L, stored.updatedAt) // 默认等于 createdAt
        assertEquals(anchor, repo.resolveNoteAnchor("md-1", id))
    }

    @Test
    fun `unmigrated null-anchor rows are refused without runtime synthesis`() = runTest {
        val highlightDao = FakeHighlightDao()
        val noteDao = FakeNoteDao()
        val repo = ReadingRepository(NoBookmarks(), NoFavorites(), highlightDao, noteDao, com.m1racle.reader.testing.testSearchRepository(), transactionRunner = com.m1racle.reader.testing.ImmediateRepositoryTransactionRunner())
        // 模拟 v10 历史行：无 anchor 列内容。
        highlightDao.rows[1] = HighlightEntity(
            id = 1,
            documentId = "d",
            locationKind = BookmarkLocationKind.PAGE_INDEX.name,
            location = 33,
            createdAt = 1L,
        )
        noteDao.rows[1] = NoteEntity(
            id = 1,
            documentId = "d",
            locationKind = BookmarkLocationKind.SCROLL_POSITION.name,
            location = 77,
            anchorStart = 9,
            anchorEnd = 30,
            content = "legacy",
            createdAt = 1L,
            updatedAt = 1L,
        )
        assertEquals(null, repo.resolveHighlightAnchor("d", 1))
        assertEquals(null, repo.resolveNoteAnchor("d", 1))
        // Real historical rows are converted once by MIGRATION_16_17, covered
        // by MigrationV16ToV17Test. The repository accepts only canonical anchors.
    }
}
