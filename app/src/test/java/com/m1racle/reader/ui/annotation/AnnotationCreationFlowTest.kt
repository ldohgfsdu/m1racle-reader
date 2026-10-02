package com.m1racle.reader.ui.annotation

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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R25 创建流状态机契约：Selection → Anchor → Draft → Repository 全链路、
 * 非法路径失败、取消重置、commit 幂等保护。
 */
class AnnotationCreationFlowTest {

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
                ?: throw IllegalStateException()
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

    private fun flow(
        highlights: FakeHighlightDao,
        notes: FakeNoteDao,
        documentId: String = "doc-1",
    ) = AnnotationCreationFlow(
        readingRepository = ReadingRepository(
            bookmarkDao = NoBookmarks(),
            favoriteDao = NoFavorites(),
            highlightDao = highlights,
            noteDao = notes,
            searchRepository = com.m1racle.reader.testing.testSearchRepository(),
            transactionRunner = com.m1racle.reader.testing.ImmediateRepositoryTransactionRunner(),
        ),
        documentId = documentId,
    )

    @Test
    fun `happy path - pdf selection to committed highlight`() = runTest {
        val highlights = FakeHighlightDao()
        val notes = FakeNoteDao()
        val f = flow(highlights, notes)
        assertEquals(AnnotationCreationFlow.Stage.Idle, f.current())

        val selection = TextSelection.PdfSelection(page = 9, rects = listOf(com.m1racle.reader.data.anchor.NormalizedRect((10f) / (500f), (20f) / (700f), ((110f) - (10f)).coerceAtLeast(0f) / (500f), ((60f) - (20f)).coerceAtLeast(0f) / (700f))), excerpt = "高亮原文")
        assertTrue(f.select(selection) is AnnotationCreationFlow.Stage.SelectionReady)
        assertTrue(f.prepareDraft(AnnotationType.HIGHLIGHT) is AnnotationCreationFlow.Stage.DraftReady)

        val committed = f.commit(nowMs = 123L) as AnnotationCreationFlow.Stage.Committed
        assertEquals(AnnotationType.HIGHLIGHT, committed.type)
        assertEquals(1L, committed.id)
        assertEquals(AnnotationCreationFlow.Stage.Idle, f.current()) // 终态自动回 Idle

        val stored = highlights.rows.values.single()
        assertEquals("doc-1", stored.documentId)
        assertEquals(9, stored.location)
        assertEquals("PAGE_INDEX", stored.locationKind)
        // anchor 列已写入规范化锚点。
        assertTrue(stored.anchor!!.startsWith("pdf:9|"))
        assertEquals("高亮原文", stored.excerpt)
        // 恢复端经仓库 API 还原等价锚点。
        val repo = ReadingRepository(
            bookmarkDao = NoBookmarks(),
            favoriteDao = NoFavorites(),
            highlightDao = highlights,
            noteDao = notes,
            searchRepository = com.m1racle.reader.testing.testSearchRepository(),
            transactionRunner = com.m1racle.reader.testing.ImmediateRepositoryTransactionRunner(),
        )
        assertEquals(selection.toAnchor(), repo.resolveHighlightAnchor("doc-1", committed.id))
    }

    @Test
    fun `note path requires non blank content and persists content`() = runTest {
        val highlights = FakeHighlightDao()
        val notes = FakeNoteDao()
        val f = flow(highlights, notes)
        f.select(TextSelection.MarkdownSelection(blockIndex = 4, charStart = 0, charEnd = 12))

        val rejected = f.prepareDraft(AnnotationType.NOTE, content = "   ")
        assertTrue(rejected is AnnotationCreationFlow.Stage.Failed)
        assertEquals(
            AnnotationCreationFlow.Stage.Reason.EMPTY_NOTE_CONTENT,
            (rejected as AnnotationCreationFlow.Stage.Failed).reason,
        )
        // 失败后仍处于 Idle（终态回收），可重新走流程。
        assertEquals(AnnotationCreationFlow.Stage.Idle, f.current())

        f.select(TextSelection.MarkdownSelection(4, 0, 12))
        assertTrue(f.prepareDraft(AnnotationType.NOTE, content = "  批注内容  ") is AnnotationCreationFlow.Stage.DraftReady)
        f.commit(nowMs = 9L)
        val note = notes.rows.values.single()
        assertEquals("批注内容", note.content) // trim 生效
        assertEquals("SCROLL_POSITION", note.locationKind)
        assertTrue(note.anchor!!.startsWith("md:4|"))
    }

    @Test
    fun `invalid selection fails without entering draft stage`() = runTest {
        val f = flow(FakeHighlightDao(), FakeNoteDao())
        val failed = f.select(TextSelection.MarkdownSelection(1, 5, 5))
        assertTrue(failed is AnnotationCreationFlow.Stage.Failed)
        assertEquals(
            AnnotationCreationFlow.Stage.Reason.INVALID_SELECTION,
            (failed as AnnotationCreationFlow.Stage.Failed).reason,
        )
        // 不允许在失败态直接 prepare/commit（各自返回终态失败，内部已回 Idle）。
        val prepFail = f.prepareDraft(AnnotationType.HIGHLIGHT)
        assertTrue(prepFail is AnnotationCreationFlow.Stage.Failed)
        assertEquals(
            AnnotationCreationFlow.Stage.Reason.INVALID_SELECTION,
            (prepFail as AnnotationCreationFlow.Stage.Failed).reason,
        )
        assertEquals(AnnotationCreationFlow.Stage.Idle, f.current())
        val commitFail = f.commit()
        assertTrue(commitFail is AnnotationCreationFlow.Stage.Failed)
        assertTrue((commitFail as AnnotationCreationFlow.Stage.Failed).message.isNotEmpty())
        assertEquals(AnnotationCreationFlow.Stage.Idle, f.current())
    }

    @Test
    fun `cancel resets from any stage`() = runTest {
        val f = flow(FakeHighlightDao(), FakeNoteDao())
        f.select(TextSelection.PdfSelection(page = 2, rects = listOf(com.m1racle.reader.data.anchor.NormalizedRect((0f) / (400f), (0f) / (400f), ((100f) - (0f)).coerceAtLeast(0f) / (400f), ((100f) - (0f)).coerceAtLeast(0f) / (400f)))))
        f.cancel()
        assertEquals(AnnotationCreationFlow.Stage.Idle, f.current())
        // Idle 上 prepare 失败但不残留脏状态。
        assertTrue(f.prepareDraft(AnnotationType.NOTE, content = "x") is AnnotationCreationFlow.Stage.Failed)
        f.cancel()
        assertEquals(AnnotationCreationFlow.Stage.Idle, f.current())
    }

    @Test
    fun `double commit is guarded and does not duplicate rows`() = runTest {
        val highlights = FakeHighlightDao()
        val f = flow(highlights, FakeNoteDao())
        f.select(TextSelection.PdfSelection(page = 1, rects = listOf(com.m1racle.reader.data.anchor.NormalizedRect((0f) / (200f), (0f) / (200f), ((50f) - (0f)).coerceAtLeast(0f) / (200f), ((50f) - (0f)).coerceAtLeast(0f) / (200f)))))
        f.prepareDraft(AnnotationType.HIGHLIGHT)
        assertTrue(f.commit(nowMs = 1L) is AnnotationCreationFlow.Stage.Committed)
        // 状态已回 Idle：再次 commit 无草稿 → Failed 且不落库。
        val second = f.commit(nowMs = 2L)
        assertTrue(second is AnnotationCreationFlow.Stage.Failed)
        assertEquals(
            AnnotationCreationFlow.Stage.Reason.ALREADY_COMMITTED,
            (second as AnnotationCreationFlow.Stage.Failed).reason,
        )
        assertEquals(1, highlights.rows.size)
    }
}
