package com.m1racle.reader.data.repository

import com.m1racle.reader.data.local.BookmarkDao
import com.m1racle.reader.data.local.BookmarkEntity
import com.m1racle.reader.data.local.BookmarkLocationKind
import com.m1racle.reader.data.local.FavoriteDocumentDao
import com.m1racle.reader.data.local.FavoriteDocumentEntity
import kotlinx.coroutines.flow.Flow
import com.m1racle.reader.data.local.BookmarkWithDocument
import com.m1racle.reader.data.local.HighlightDao
import com.m1racle.reader.data.local.HighlightEntity
import com.m1racle.reader.data.local.NoteDao
import com.m1racle.reader.data.local.NoteEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H2.1 Bookmark 契约：创建（文档中性定位）、删除、按文档查询（时间升序）。
 */
class BookmarkRepositoryTest {

    /** 内存 Fake：自增主键模拟 Room autoGenerate。 */
    private class FakeBookmarkDao : BookmarkDao {
        val rows = LinkedHashMap<Long, BookmarkEntity>()
        private var nextId = 1L

        override suspend fun insert(bookmark: BookmarkEntity): Long {
            val id = nextId++
            rows[id] = bookmark.copy(id = id)
            return id
        }

        override suspend fun delete(id: Long) {
            rows.remove(id)
        }

        override suspend fun listForDocument(documentId: String): List<BookmarkEntity> =
            rows.values.filter { it.documentId == documentId }
                .sortedWith(compareBy({ it.createdAt }, { it.id }))

        override suspend fun getAt(
            documentId: String,
            locationKind: String,
            location: Int,
        ): BookmarkEntity? = rows.values.firstOrNull {
            it.documentId == documentId &&
                it.locationKind == locationKind &&
                it.location == location
        }

        override suspend fun deleteAt(
            documentId: String,
            locationKind: String,
            location: Int,
        ): Int {
            val targets = rows.filterValues {
                it.documentId == documentId &&
                    it.locationKind == locationKind &&
                    it.location == location
            }.keys.toList()
            targets.forEach { rows.remove(it) }
            return targets.size
        }

        override suspend fun listForDocumentAllKinds(documentId: String): List<BookmarkEntity> =
            rows.values.filter { it.documentId == documentId }

        override fun observeAll(): Flow<List<BookmarkWithDocument>> = error("unused")
    }

    private class NoFavorites : FavoriteDocumentDao {
        override suspend fun insert(favorite: FavoriteDocumentEntity): Long = throw unused()
        override suspend fun delete(documentId: String) = throw unused()
        override suspend fun getAll(): List<FavoriteDocumentEntity> = throw unused()
        override suspend fun get(documentId: String): FavoriteDocumentEntity? = throw unused()
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

    private fun unused(): Nothing = throw IllegalStateException("unused")

    private fun repo(dao: FakeBookmarkDao = FakeBookmarkDao()) =
        ReadingRepository(
            bookmarkDao = dao,
            favoriteDao = NoFavorites(),
            highlightDao = NoHighlights(),
            noteDao = NoNotes(),
            searchRepository = com.m1racle.reader.testing.testSearchRepository(),
        ) to dao

    @Test
    fun `create bookmark assigns auto id and keeps optional fields`() = runTest {
        val (repo, dao) = repo()
        val full = BookmarkEntity(
            documentId = "doc-1",
            locationKind = BookmarkLocationKind.PAGE_INDEX.name,
            location = 41,
            offset = 1024L,
            title = "第三章",
            note = "回头看推导",
            createdAt = 100L,
        )
        val firstId = repo.createBookmark(full)
        assertEquals(1L, firstId)
        assertEquals("doc-1", dao.rows[firstId]?.documentId)
        assertEquals(41, dao.rows[firstId]?.location)
        assertEquals(1024L, dao.rows[firstId]?.offset)
        assertEquals("回头看推导", dao.rows[firstId]?.note)

        val secondId = repo.createBookmark(
            BookmarkEntity(
                documentId = "doc-1",
                locationKind = BookmarkLocationKind.SCROLL_POSITION.name,
                location = 7,
                createdAt = 200L,
            ),
        )
        assertEquals(2L, secondId)
        assertEquals(null, dao.rows[secondId]?.offset)
        assertEquals(null, dao.rows[secondId]?.title)
        assertEquals(null, dao.rows[secondId]?.note)
    }

    @Test
    fun `location kinds stay document neutral across pdf and markdown bookmarks`() = runTest {
        val (repo, dao) = repo()
        repo.createBookmark(
            BookmarkEntity(
                documentId = "pdf-doc",
                locationKind = BookmarkLocationKind.PAGE_INDEX.name,
                location = 12,
                createdAt = 1L,
            ),
        )
        repo.createBookmark(
            BookmarkEntity(
                documentId = "md-doc",
                locationKind = BookmarkLocationKind.SCROLL_POSITION.name,
                location = 340,
                offset = 88L,
                createdAt = 2L,
            ),
        )
        val byKind = dao.rows.values.associate { it.kind to it }
        // 定位语义由 kind 显式声明，实体层不解释坐标系 —— 无 PDF 分页绑定。
        assertEquals(BookmarkLocationKind.PAGE_INDEX, byKind[BookmarkLocationKind.PAGE_INDEX]!!.kind)
        assertEquals(BookmarkLocationKind.SCROLL_POSITION, byKind[BookmarkLocationKind.SCROLL_POSITION]!!.kind)
    }

    @Test
    fun `delete removes only the target bookmark`() = runTest {
        val (repo, dao) = repo()
        val a = repo.createBookmark(pageBookmark("doc-1", page = 1, createdAt = 1L))
        val b = repo.createBookmark(pageBookmark("doc-1", page = 2, createdAt = 2L))
        repo.deleteBookmark(a)
        assertEquals(setOf(b), dao.rows.keys)
        assertTrue(repo.bookmarksForDocument("doc-1").all { it.id == b })
    }

    @Test
    fun `list by document filters others and orders by creation time`() = runTest {
        val (repo, _) = repo()
        repo.createBookmark(pageBookmark("doc-1", page = 3, createdAt = 30L))
        repo.createBookmark(pageBookmark("doc-2", page = 9, createdAt = 10L))
        repo.createBookmark(pageBookmark("doc-1", page = 1, createdAt = 10L))
        repo.createBookmark(pageBookmark("doc-1", page = 2, createdAt = 20L))

        val list = repo.bookmarksForDocument("doc-1")
        assertEquals(listOf(1, 2, 3), list.map { it.location }) // createdAt 升序：10,20,30
        assertFalse(list.any { it.documentId == "doc-2" })
    }

    private fun pageBookmark(documentId: String, page: Int, createdAt: Long) = BookmarkEntity(
        documentId = documentId,
        locationKind = BookmarkLocationKind.PAGE_INDEX.name,
        location = page,
        createdAt = createdAt,
    )
}

private fun unused() = UnsupportedOperationException("not used in this test")
