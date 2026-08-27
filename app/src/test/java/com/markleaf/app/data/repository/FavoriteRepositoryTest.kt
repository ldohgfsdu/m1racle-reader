package com.markleaf.app.data.repository

import com.markleaf.app.data.local.BookmarkDao
import com.markleaf.app.data.local.BookmarkWithDocument
import com.markleaf.app.data.local.BookmarkEntity
import com.markleaf.app.data.local.FavoriteDocumentDao
import com.markleaf.app.data.local.FavoriteDocumentEntity
import com.markleaf.app.data.local.HighlightDao
import com.markleaf.app.data.local.HighlightEntity
import com.markleaf.app.data.local.NoteDao
import com.markleaf.app.data.local.NoteEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H2.1 Favorite 契约：收藏、取消收藏、幂等与排序。
 */
class FavoriteRepositoryTest {

    /** 内存 Fake：IGNORE 冲突语义（重复 insert 返回 -1，保留原行）。 */
    private class FakeFavoriteDao : FavoriteDocumentDao {
        val rows = LinkedHashMap<String, FavoriteDocumentEntity>()

        override suspend fun insert(favorite: FavoriteDocumentEntity): Long =
            if (rows.putIfAbsent(favorite.documentId, favorite) == null) {
                favorite.documentId.hashCode().toLong()
            } else {
                -1L
            }

        override suspend fun delete(documentId: String) {
            rows.remove(documentId)
        }

        override suspend fun getAll(): List<FavoriteDocumentEntity> = rows.values.toList()

        override suspend fun get(documentId: String): FavoriteDocumentEntity? = rows[documentId]
    }


    private class NoBookmarks : BookmarkDao {
        override suspend fun insert(bookmark: BookmarkEntity): Long = throw unused()
        override suspend fun delete(id: Long) = throw unused()
        override suspend fun listForDocument(documentId: String): List<BookmarkEntity> = throw unused()
        override fun observeAll(): Flow<List<BookmarkWithDocument>> = throw unused()
        override suspend fun getAt(documentId: String, locationKind: String, location: Int) = throw unused()
        override suspend fun deleteAt(documentId: String, locationKind: String, location: Int): Int = throw unused()
        override suspend fun listForDocumentAllKinds(documentId: String): List<BookmarkEntity> = throw unused()
    }

    private class NoHighlights : HighlightDao {
        override suspend fun insert(highlight: HighlightEntity): Long = throw unused()
        override suspend fun delete(documentId: String, id: Long) = throw unused()
        override suspend fun listForDocument(documentId: String): List<HighlightEntity> = throw unused()
        override suspend fun getById(documentId: String, id: Long): HighlightEntity? = throw unused()
        override suspend fun listAtLocation(documentId: String, locationKind: String, location: Int): List<HighlightEntity> = throw unused()
    }

    private class NoNotes : NoteDao {
        override suspend fun insert(note: NoteEntity): Long = throw unused()
        override suspend fun updateContent(documentId: String, id: Long, content: String, updatedAt: Long) = throw unused()
        override suspend fun delete(documentId: String, id: Long) = throw unused()
        override suspend fun listForDocument(documentId: String): List<NoteEntity> = throw unused()
        override suspend fun getById(documentId: String, id: Long): NoteEntity? = throw unused()
        override suspend fun listAtLocation(documentId: String, locationKind: String, location: Int): List<NoteEntity> = throw unused()
    }

    private fun repo(dao: FakeFavoriteDao = FakeFavoriteDao()) =
        ReadingRepository(
            bookmarkDao = NoBookmarks(),
            favoriteDao = dao,
            highlightDao = NoHighlights(),
            noteDao = NoNotes(),
            searchRepository = com.markleaf.app.testing.testSearchRepository(),
        ) to dao

    @Test
    fun `add and query favorites`() = runTest {
        val (repo, _) = repo()
        assertFalse(repo.isFavorite("doc-1"))
        assertTrue(repo.addFavorite("doc-1", createdAt = 100L))
        assertTrue(repo.isFavorite("doc-1"))
        assertEquals(100L, repo.favorites().single { it.documentId == "doc-1" }.createdAt)
    }

    @Test
    fun `remove clears the favorite`() = runTest {
        val (repo, dao) = repo()
        repo.addFavorite("doc-1", createdAt = 1L)
        repo.removeFavorite("doc-1")
        assertFalse(repo.isFavorite("doc-1"))
        assertTrue(dao.rows.isEmpty())
        // 再收藏视为新一次收藏。
        assertTrue(repo.addFavorite("doc-1", createdAt = 5L))
        assertEquals(5L, repo.favorites().single().createdAt)
    }

    @Test
    fun `duplicate add is idempotent and keeps the first timestamp`() = runTest {
        val (repo, dao) = repo()
        assertTrue(repo.addFavorite("doc-1", createdAt = 10L))
        assertFalse(repo.addFavorite("doc-1", createdAt = 99L))
        assertEquals(10L, dao.rows["doc-1"]?.createdAt)
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun `favorites order by creation time descending`() = runTest {
        val (repo, _) = repo()
        repo.addFavorite("old", createdAt = 10L)
        repo.addFavorite("newest", createdAt = 30L)
        repo.addFavorite("mid", createdAt = 20L)
        assertEquals(listOf("newest", "mid", "old"), repo.favorites().map { it.documentId })
    }
}

private fun unused() = UnsupportedOperationException("not used in this test")
