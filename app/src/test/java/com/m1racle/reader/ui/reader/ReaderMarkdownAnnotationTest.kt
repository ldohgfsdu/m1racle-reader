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
import com.m1racle.reader.testing.ProductionSource
import com.m1racle.reader.testing.testSearchRepository
import com.m1racle.reader.ui.annotation.RepositoryAnnotationOverlaySource
import com.m1racle.reader.ui.annotation.TextSelection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * P0-1 Markdown 标注契约：与 PDF 同一 Controller → Repository → Anchor 链，
 * SCROLL_POSITION 坐标系。覆盖创建/恢复/编辑/删除/定位恢复与路由接线。
 */
class ReaderMarkdownAnnotationTest {

    private class FakeHighlightDao : HighlightDao {
        val rows = LinkedHashMap<Long, HighlightEntity>()
        private var nextId = 1L

        override suspend fun insert(highlight: HighlightEntity): Long {
            val id = nextId++
            rows[id] = highlight.copy(id = id)
            return id
        }

        override suspend fun delete(documentId: String, id: Long) {
            rows.remove(id)
        }

        override suspend fun listForDocument(documentId: String): List<HighlightEntity> =
            rows.values.filter { it.documentId == documentId }

        override suspend fun getById(documentId: String, id: Long): HighlightEntity? =
            rows[id]?.takeIf { it.documentId == documentId }

        override suspend fun listAtLocation(
            documentId: String,
            locationKind: String,
            location: Int,
        ): List<HighlightEntity> = rows.values.filter {
            it.documentId == documentId && it.locationKind == locationKind && it.location == location
        }
    }

    private class FakeNoteDao : NoteDao {
        val rows = LinkedHashMap<Long, NoteEntity>()
        private var nextId = 1L

        override suspend fun insert(note: NoteEntity): Long {
            val id = nextId++
            rows[id] = note.copy(id = id)
            return id
        }

        override suspend fun updateContent(
            documentId: String,
            id: Long,
            content: String,
            updatedAt: Long,
        ) {
            val row = rows[id] ?: return
            rows[id] = row.copy(content = content, updatedAt = updatedAt)
        }

        override suspend fun delete(documentId: String, id: Long) {
            rows.remove(id)
        }

        override suspend fun listForDocument(documentId: String): List<NoteEntity> =
            rows.values.filter { it.documentId == documentId }

        override suspend fun getById(documentId: String, id: Long): NoteEntity? =
            rows[id]?.takeIf { it.documentId == documentId }

        override suspend fun listAtLocation(
            documentId: String,
            locationKind: String,
            location: Int,
        ): List<NoteEntity> = rows.values.filter {
            it.documentId == documentId && it.locationKind == locationKind && it.location == location
        }
    }

    private class NoBookmarks : BookmarkDao {
        override suspend fun insert(bookmark: BookmarkEntity): Long =
            throw UnsupportedOperationException()
        override suspend fun delete(id: Long) = throw UnsupportedOperationException()
        override suspend fun listForDocument(documentId: String): List<BookmarkEntity> =
            throw UnsupportedOperationException()
        override fun observeAll(): Flow<List<BookmarkWithDocument>> =
            throw UnsupportedOperationException()
        override suspend fun getAt(documentId: String, locationKind: String, location: Int): BookmarkEntity? =
            throw UnsupportedOperationException()
        override suspend fun deleteAt(documentId: String, locationKind: String, location: Int): Int =
            throw UnsupportedOperationException()
        override suspend fun listForDocumentAllKinds(documentId: String): List<BookmarkEntity> =
            throw UnsupportedOperationException()
    }

    private class NoFavorites : FavoriteDocumentDao {
        override suspend fun insert(favorite: FavoriteDocumentEntity): Long =
            throw UnsupportedOperationException()
        override suspend fun delete(documentId: String) = throw UnsupportedOperationException()
        override suspend fun getAll(): List<FavoriteDocumentEntity> =
            throw UnsupportedOperationException()
        override suspend fun get(documentId: String): FavoriteDocumentEntity? =
            throw UnsupportedOperationException()
    }

    private val highlightDao = FakeHighlightDao()
    private val noteDao = FakeNoteDao()
    private val repo = ReadingRepository(
        bookmarkDao = NoBookmarks(),
        favoriteDao = NoFavorites(),
        highlightDao = highlightDao,
        noteDao = noteDao,
        searchRepository = testSearchRepository(),
            transactionRunner = com.m1racle.reader.testing.ImmediateRepositoryTransactionRunner(),
        )
    private val highlights = ReaderHighlightController(repo, "md-1")
    private val notes = ReaderNoteController(repo, "md-1")

    private fun selection(block: Int = 7) = TextSelection.MarkdownSelection(
        blockIndex = block,
        charStart = 0,
        charEnd = 12,
        excerpt = "plain block",
    )

    @Test
    fun `markdown commit stores SCROLL_POSITION row with md anchor`() = runTest {
        val result = highlights.commitHighlight(selection())
        assertTrue(result is ReaderHighlightController.CommitResult.Success)
        val stored = highlightDao.rows.values.single()
        assertEquals("md-1", stored.documentId)
        assertEquals("SCROLL_POSITION", stored.locationKind)
        assertEquals(7, stored.location)
        assertTrue((stored.anchor ?: "").startsWith("md:"))
        assertEquals("plain block", stored.excerpt)
    }

    @Test
    fun `empty markdown selection fails without write`() = runTest {
        val result = highlights.commitHighlight(
            TextSelection.MarkdownSelection(blockIndex = 7, charStart = 5, charEnd = 5),
        )
        assertTrue(result is ReaderHighlightController.CommitResult.Failure)
        assertTrue(highlightDao.rows.isEmpty())
    }

    @Test
    fun `overlay restores committed highlight with range`() = runTest {
        highlights.commitHighlight(selection(block = 9))
        val source = RepositoryAnnotationOverlaySource(repo)
        val items = source.itemsFor("md-1", "SCROLL_POSITION", 9)
        assertEquals(1, items.size)
        assertEquals(Pair(0, 12), items.single().markdownRange())
        assertTrue(source.itemsFor("md-1", "SCROLL_POSITION", 10).isEmpty())
    }

    @Test
    fun `deleteHighlight removes markdown row`() = runTest {
        val id = (highlights.commitHighlight(selection())
            as ReaderHighlightController.CommitResult.Success).id
        highlights.deleteHighlight(id)
        assertTrue(highlightDao.rows.isEmpty())
    }

    @Test
    fun `note create update delete link on markdown anchor`() = runTest {
        val anchor = DocumentAnchor.MarkdownBlock(blockIndex = 4, charStart = 0, charEnd = 9)
        val id = notes.create(anchor, "first")
        assertEquals("first", noteDao.rows.getValue(id).content)
        notes.update(id, "second")
        assertEquals("second", noteDao.rows.getValue(id).content)
        assertNotNull(notes.linkedAt(anchor))
        notes.delete(id)
        assertNull(notes.linkedAt(anchor))
    }

    @Test
    fun `viewer route injects markdown highlight and note controllers`() {
        val nav = ProductionSource.text("com/m1racle/reader/ui/navigation/NavGraph.kt")
        val viewer = nav.substringAfter("route = Routes.VIEWER").substringBefore("route = Routes.PDF_VIEWER")
        assertTrue(viewer.contains("ReaderHighlightController("))
        assertTrue(viewer.contains("ReaderNoteController("))
        assertTrue(viewer.contains("highlightController = highlightController"))
        assertTrue(viewer.contains("noteController = noteController"))
    }
}
