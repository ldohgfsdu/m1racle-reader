package com.m1racle.reader.ui.reader

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
import com.m1racle.reader.data.repository.ReadingRepository
import com.m1racle.reader.data.repository.RepositoryTransactionRunner
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R23 阅读器书签切换契约：创建/删除、PDF(PAGE_INDEX)/Markdown(SCROLL_POSITION)
 * 坐标系、同位幂等、跨位置/跨坐标系隔离。写入路径 = Controller → Repository → DAO，
 * UI 不直访数据库（结构钉见 ReadingAssetsAuthorityTest）。
 */
class ReaderBookmarkControllerTest {

    /** 内存 Fake：完整实现 DAO 契约（含 R23 定位查询）。 */
    private class FakeBookmarkDao(private val suspendReads: Boolean = false) : BookmarkDao {
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

        override fun observeAll(): Flow<List<BookmarkWithDocument>> =
            throw UnsupportedOperationException("unused")

        override suspend fun getAt(
            documentId: String,
            locationKind: String,
            location: Int,
        ): BookmarkEntity? {
            val found = rows.values.firstOrNull {
                it.documentId == documentId &&
                    it.locationKind == locationKind &&
                    it.location == location
            }
            if (suspendReads) yield()
            return found
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

    private class NoHighlights : HighlightDao {
        override suspend fun insert(highlight: HighlightEntity): Long =
            throw UnsupportedOperationException()
        override suspend fun delete(documentId: String, id: Long) = throw UnsupportedOperationException()
        override suspend fun listForDocument(documentId: String): List<HighlightEntity> =
            throw UnsupportedOperationException()

        override suspend fun getById(documentId: String, id: Long): HighlightEntity? =
            throw UnsupportedOperationException()
        override suspend fun listAtLocation(documentId: String, locationKind: String, location: Int): List<HighlightEntity> =
            throw UnsupportedOperationException()
    }

    private class NoNotes : NoteDao {
        override suspend fun insert(note: NoteEntity): Long =
            throw UnsupportedOperationException()
        override suspend fun updateContent(documentId: String, id: Long, content: String, updatedAt: Long) =
            throw UnsupportedOperationException()
        override suspend fun delete(documentId: String, id: Long) = throw UnsupportedOperationException()
        override suspend fun listForDocument(documentId: String): List<NoteEntity> =
            throw UnsupportedOperationException()

        override suspend fun getById(documentId: String, id: Long): NoteEntity? =
            throw UnsupportedOperationException()
        override suspend fun listAtLocation(documentId: String, locationKind: String, location: Int): List<NoteEntity> =
            throw UnsupportedOperationException()
    }

    private fun repo(
        dao: FakeBookmarkDao,
        transactions: RepositoryTransactionRunner = com.m1racle.reader.testing.ImmediateRepositoryTransactionRunner(),
    ) = ReadingRepository(
        bookmarkDao = dao,
        favoriteDao = NoFavorites(),
        highlightDao = NoHighlights(),
        noteDao = NoNotes(),
        searchRepository = com.m1racle.reader.testing.testSearchRepository(),
            transactionRunner = transactions,
        )

    private class SerialTransactions : RepositoryTransactionRunner {
        private val mutex = Mutex()
        var count = 0
        override suspend fun <T> run(block: suspend () -> T): T = mutex.withLock {
            count++
            block()
        }
    }

    @Test
    fun `overlapping reader toggles are serialized around the read and write`() = runTest {
        val dao = FakeBookmarkDao(suspendReads = true)
        val transactions = SerialTransactions()
        val repository = repo(dao, transactions)
        val first = ReaderBookmarkController(repository, "pdf", BookmarkLocationKind.PAGE_INDEX)
        val second = ReaderBookmarkController(repository, "pdf", BookmarkLocationKind.PAGE_INDEX)

        val results = listOf(async { first.toggle(7) }, async { second.toggle(7) }).awaitAll()

        assertEquals(listOf(ReaderBookmarkController.ToggleResult.Added, ReaderBookmarkController.ToggleResult.Removed), results)
        assertTrue("Two toggles must leave no bookmark, including when both initial reads can suspend", dao.rows.isEmpty())
        assertEquals(2, transactions.count)
    }

    @Test
    fun `many overlapping toggles preserve parity without creating duplicate locations`() = runTest {
        val dao = FakeBookmarkDao(suspendReads = true)
        val transactions = SerialTransactions()
        val repository = repo(dao, transactions)
        val controllers = List(3) { ReaderBookmarkController(repository, "pdf", BookmarkLocationKind.PAGE_INDEX) }

        val results = List(101) { index -> async { controllers[index % controllers.size].toggle(9) } }.awaitAll()

        assertEquals(51, results.count { it is ReaderBookmarkController.ToggleResult.Added })
        assertEquals(50, results.count { it is ReaderBookmarkController.ToggleResult.Removed })
        assertEquals(1, dao.rows.size)
        assertEquals(9, dao.rows.values.single().location)
    }

    @Test
    fun `pdf toggle adds page index bookmark with PAGE_INDEX kind`() = runTest {
        val dao = FakeBookmarkDao()
        val controller = ReaderBookmarkController(repo(dao), "pdf-1", BookmarkLocationKind.PAGE_INDEX)
        val result = controller.toggle(location = 77, nowMs = 1_000L)
        assertTrue(result is ReaderBookmarkController.ToggleResult.Added)
        val stored = dao.rows.values.single()
        assertEquals("pdf-1", stored.documentId)
        assertEquals(BookmarkLocationKind.PAGE_INDEX.name, stored.locationKind)
        assertEquals(77, stored.location)
        assertEquals(1_000L, stored.createdAt)
    }

    @Test
    fun `markdown toggle adds scroll position with SCROLL_POSITION kind`() = runTest {
        val dao = FakeBookmarkDao()
        val controller = ReaderBookmarkController(
            repo(dao),
            "md-1",
            BookmarkLocationKind.SCROLL_POSITION,
        )
        assertTrue(controller.toggle(location = 340, nowMs = 5L) is ReaderBookmarkController.ToggleResult.Added)
        val stored = dao.rows.values.single()
        assertEquals(BookmarkLocationKind.SCROLL_POSITION.name, stored.locationKind)
        assertEquals(340, stored.location)
    }

    @Test
    fun `toggle cycles add then remove then add on same location`() = runTest {
        val dao = FakeBookmarkDao()
        val controller = ReaderBookmarkController(repo(dao), "pdf-1", BookmarkLocationKind.PAGE_INDEX)
        assertTrue(controller.toggle(10) is ReaderBookmarkController.ToggleResult.Added)
        assertTrue(controller.toggle(10) is ReaderBookmarkController.ToggleResult.Removed)
        assertNull(dao.rows.values.firstOrNull { it.location == 10 })
        assertTrue(controller.toggle(10) is ReaderBookmarkController.ToggleResult.Added)
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun `removal is scoped to exact tuple - other locations and kinds untouched`() = runTest {
        val dao = FakeBookmarkDao()
        val pdf = ReaderBookmarkController(repo(dao), "pdf-1", BookmarkLocationKind.PAGE_INDEX)
        val mdSameDoc = ReaderBookmarkController(
            repo(dao),
            "pdf-1",
            BookmarkLocationKind.SCROLL_POSITION,
        )
        pdf.toggle(5)
        pdf.toggle(6)
        mdSameDoc.toggle(5) // 同文档同位置但不同坐标系
        assertTrue(pdf.toggle(5) is ReaderBookmarkController.ToggleResult.Removed)
        assertEquals(2, dao.rows.size)
        assertNotNull(dao.rows.values.firstOrNull { it.location == 6 })
        assertNotNull(
            dao.rows.values.firstOrNull {
                it.locationKind == BookmarkLocationKind.SCROLL_POSITION.name && it.location == 5
            },
        )
    }

    @Test
    fun `bookmarked locations filter by kind for state icons`() = runTest {
        val dao = FakeBookmarkDao()
        val pdf = ReaderBookmarkController(repo(dao), "doc", BookmarkLocationKind.PAGE_INDEX)
        val md = ReaderBookmarkController(repo(dao), "doc", BookmarkLocationKind.SCROLL_POSITION)
        pdf.toggle(3)
        pdf.toggle(9)
        md.toggle(3)
        assertEquals(setOf(3, 9), pdf.bookmarkedLocations())
        assertEquals(setOf(3), md.bookmarkedLocations())
        assertTrue(pdf.isBookmarked(pdf.bookmarkedLocations(), 9))
        assertFalse(pdf.isBookmarked(pdf.bookmarkedLocations(), 4))
    }
}
