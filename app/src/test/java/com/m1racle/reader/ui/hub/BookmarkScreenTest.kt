package com.m1racle.reader.ui.hub

import com.m1racle.reader.data.local.BookmarkDao
import com.m1racle.reader.data.local.BookmarkEntity
import com.m1racle.reader.data.local.BookmarkLocationKind
import com.m1racle.reader.data.local.HighlightDao
import com.m1racle.reader.data.local.HighlightEntity
import com.m1racle.reader.data.local.NoteDao
import com.m1racle.reader.data.local.NoteEntity
import com.m1racle.reader.data.local.BookmarkWithDocument
import com.m1racle.reader.data.local.FavoriteDocumentDao
import com.m1racle.reader.data.local.FavoriteDocumentEntity
import com.m1racle.reader.data.repository.ReadingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H2.2 Phase 4 契约：书签列表查询（JOIN 文档名、时间倒序）、locationKind 语义
 * 分流（PAGE_INDEX→页码 / SCROLL_POSITION→滚动位置 / 未知中性回退）、空态。
 * 宿主无 Room SQLite 后端 → DAO 用内存 Fake（仓库既定测试策略）。
 */
class BookmarkScreenTest {

    private class FakeBookmarkDao : BookmarkDao {
        val rows = MutableStateFlow<List<BookmarkWithDocument>>(emptyList())

        override suspend fun insert(bookmark: BookmarkEntity): Long = error("unused")
        override suspend fun delete(id: Long) = error("unused")
        override suspend fun listForDocument(documentId: String): List<BookmarkEntity> = error("unused")
        override suspend fun getAt(
            documentId: String,
            locationKind: String,
            location: Int,
        ): BookmarkEntity? = error("unused")

        override suspend fun deleteAt(
            documentId: String,
            locationKind: String,
            location: Int,
        ): Int = error("unused")

        override suspend fun listForDocumentAllKinds(documentId: String): List<BookmarkEntity> =
            error("unused")

        override fun observeAll(): Flow<List<BookmarkWithDocument>> = rows
    }

    private class NoHighlights : HighlightDao {
        override suspend fun insert(highlight: HighlightEntity): Long = error("unused")
        override suspend fun delete(documentId: String, id: Long) = error("unused")
        override suspend fun listForDocument(documentId: String): List<HighlightEntity> = error("unused")
        override suspend fun getById(documentId: String, id: Long): HighlightEntity? = error("unused")
        override suspend fun listAtLocation(documentId: String, locationKind: String, location: Int): List<HighlightEntity> = error("unused")
    }

    private class NoNotes : NoteDao {
        override suspend fun insert(note: NoteEntity): Long = error("unused")
        override suspend fun updateContent(documentId: String, id: Long, content: String, updatedAt: Long) = error("unused")
        override suspend fun delete(documentId: String, id: Long) = error("unused")
        override suspend fun listForDocument(documentId: String): List<NoteEntity> = error("unused")
        override suspend fun getById(documentId: String, id: Long): NoteEntity? = error("unused")
        override suspend fun listAtLocation(documentId: String, locationKind: String, location: Int): List<NoteEntity> = error("unused")
    }

    private class NoFavorites : FavoriteDocumentDao {
        override suspend fun insert(favorite: FavoriteDocumentEntity): Long = error("unused")
        override suspend fun delete(documentId: String) = error("unused")
        override suspend fun getAll(): List<FavoriteDocumentEntity> = error("unused")
        override suspend fun get(documentId: String): FavoriteDocumentEntity? = error("unused")
    }

    private fun row(
        id: Long,
        kind: String,
        location: Int,
        createdAt: Long,
        documentName: String = "doc.md",
        title: String? = null,
        note: String? = null,
    ) = BookmarkWithDocument(
        id = id,
        documentId = "doc-$id",
        locationKind = kind,
        location = location,
        offset = null,
        title = title,
        note = note,
        createdAt = createdAt,
        documentName = documentName,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `bookmarks query emits joined rows newest first`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val dao = FakeBookmarkDao()
            val repo = ReadingRepository(
                bookmarkDao = dao,
                favoriteDao = NoFavorites(),
                highlightDao = NoHighlights(),
                noteDao = NoNotes(),
                searchRepository = com.m1racle.reader.testing.testSearchRepository(),
            )
            val vm = BookmarksViewModel(repo)

            assertEquals(false, vm.uiState.value.ready)
            assertTrue(vm.uiState.first { it.ready }.bookmarks.isEmpty())

            dao.rows.value = listOf(
                row(id = 2, kind = "SCROLL_POSITION", location = 340, createdAt = 200L, documentName = "b.md"),
                row(id = 1, kind = "PAGE_INDEX", location = 41, createdAt = 100L, documentName = "a.pdf", note = "回头看"),
            )
            val list = vm.uiState.first { it.bookmarks.isNotEmpty() }.bookmarks
            assertEquals(listOf(2L, 1L), list.map { it.id }) // DAO 排序直通（新在前）
            assertEquals("b.md", list[0].documentName)
            assertEquals("回头看", list[1].note)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `location line follows locationKind - not everything is a pdf page`() {
        assertEquals(
            "页码 42",
            BookmarkRowFormatter.locationLine(BookmarkLocationKind.PAGE_INDEX.name, 41),
        )
        assertEquals(
            "滚动位置 340",
            BookmarkRowFormatter.locationLine(BookmarkLocationKind.SCROLL_POSITION.name, 340),
        )
        // 未来未知 kind：中性回退，不崩、不臆造页码语义。
        assertEquals("位置 7", BookmarkRowFormatter.locationLine("ANCHOR", 7))
    }

    @Test
    fun `headline prefers bookmark title over document name`() {
        assertEquals("我的批注", BookmarkRowFormatter.headline(title = "我的批注", documentName = "a.pdf"))
        assertEquals("a.pdf", BookmarkRowFormatter.headline(title = null, documentName = "a.pdf"))
        assertEquals("a.pdf", BookmarkRowFormatter.headline(title = "  ", documentName = "a.pdf"))
    }

    @Test
    fun `created at formats same-day as clock and older as date`() {
        val now = java.util.Calendar.getInstance().apply {
            set(2026, 7, 26, 18, 30, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val sameMorning = now - 60L * 60L * 1000L
        val lastYear = java.util.Calendar.getInstance().apply {
            set(2025, 0, 15, 9, 0, 0)
        }.timeInMillis
        assertTrue(BookmarkRowFormatter.createdAtLine(sameMorning, now).contains(":"))
        assertEquals("2025-01-15", BookmarkRowFormatter.createdAtLine(lastYear, now))
    }
}
