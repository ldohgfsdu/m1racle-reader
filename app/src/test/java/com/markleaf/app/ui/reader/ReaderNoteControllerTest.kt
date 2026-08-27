package com.markleaf.app.ui.reader

import com.markleaf.app.data.anchor.AnchorCodec
import com.markleaf.app.data.anchor.DocumentAnchor
import com.markleaf.app.data.local.BookmarkDao
import com.markleaf.app.data.local.BookmarkEntity
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R27 Note 闭环契约：
 * 1. 创建（经 Controller → Repository；字段与关联键正确）；
 * 2. 更新不覆盖 createdAt；
 * 3. 删除只解除批注自身，同锚点高亮不受影响（关联关系语义）；
 * 4. 跨文档隔离；
 * 5. 重新打开恢复（新 Controller/Repository 实例读同一持久化数据）。
 */
class ReaderNoteControllerTest {

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
            val current = rows[id] ?: return
            if (current.documentId == documentId) {
                rows[id] = current.copy(content = content, updatedAt = updatedAt)
            }
        }

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

    private class Harness {
        val highlightDao = FakeHighlightDao()
        val noteDao = FakeNoteDao()
        val repo = ReadingRepository(NoBookmarks(), NoFavorites(), highlightDao, noteDao, com.markleaf.app.testing.testSearchRepository())

        /** 种子高亮：模拟 R26.1 已创建的高亮（含 anchor 列）。 */
        suspend fun seedHighlight(
            documentId: String,
            page: Int,
            createdAt: Long,
        ): HighlightEntity {
            val controller = ReaderHighlightController(repo, documentId)
            controller.commitHighlight(
                com.markleaf.app.ui.annotation.TextSelection.PdfSelection(
                    page = page,
                    leftPx = 10f, topPx = 20f, rightPx = 210f, bottomPx = 120f,
                    pageWidthPx = 800f, pageHeightPx = 1131f,
                ),
                nowMs = createdAt,
            )
            // commitHighlight 内部经 flow 落库；此处直接取 DAO 行。
            val row = highlightDao.rows.values.last { it.documentId == documentId && it.location == page }
            assertEquals(page, (AnchorCodec.decode(row.anchor) as DocumentAnchor.PdfPage).page)
            return row
        }

        fun anchorOf(highlight: HighlightEntity): DocumentAnchor =
            checkNotNull(AnchorCodec.decode(highlight.anchor))

        fun noteController(documentId: String) =
            ReaderNoteController(repo, documentId)

        /** 模拟阅读器重开：重建 Repository/Controller，但复用持久化 DAO。 */
        fun reopenedNoteController(documentId: String) = ReaderNoteController(
            ReadingRepository(NoBookmarks(), NoFavorites(), highlightDao, noteDao, com.markleaf.app.testing.testSearchRepository()),
            documentId,
        )
    }

    @Test
    fun `create note linked to highlight anchor`() = runTest {
        val h = Harness()
        val hl = h.seedHighlight("pdf-1", page = 9, createdAt = 100L)
        val notes = h.noteController("pdf-1")
        val linkedAnchor = h.anchorOf(hl)

        val id = notes.create(
            anchor = linkedAnchor,
            content = "这一页的批注",
            nowMs = 200L,
        )
        val stored = h.noteDao.rows[id]!!
        assertEquals("pdf-1", stored.documentId)
        assertEquals(9, stored.location)
        assertEquals("PAGE_INDEX", stored.locationKind)
        assertEquals(200L, stored.createdAt)
        // 关联键：与高亮 anchor 编码一致。
        assertEquals(hl.anchor, stored.anchor)

        // findLinked 命中同锚点批注（按编码串关联键匹配）。
        assertEquals(id, ReaderNoteController.findLinked(notes.list(), linkedAnchor)?.id)
    }

    @Test
    fun `update keeps createdAt and advances updatedAt`() = runTest {
        val h = Harness()
        val highlight = h.seedHighlight("pdf-1", page = 2, createdAt = 10L)
        val notes = h.noteController("pdf-1")
        val id = notes.create(h.anchorOf(highlight), "v1", nowMs = 100L)

        notes.update(id, "v2", nowMs = 900L)

        val stored = h.noteDao.rows[id]!!
        assertEquals("v2", stored.content)
        assertEquals(100L, stored.createdAt) // 创建时间不被更新覆盖
        assertEquals(900L, stored.updatedAt)
    }

    @Test
    fun `delete removes note only - linked highlight stays intact`() = runTest {
        val h = Harness()
        val hl = h.seedHighlight("pdf-1", page = 5, createdAt = 10L)
        val notes = h.noteController("pdf-1")
        val id = notes.create(h.anchorOf(hl), "tmp", nowMs = 20L)

        notes.delete(id)

        assertNull(h.noteDao.rows[id])
        // 关联的另一侧（高亮）原样保留。
        assertTrue(h.highlightDao.rows.containsKey(hl.id))
        assertTrue(notes.list().isEmpty())
    }

    @Test
    fun `notes are isolated per document`() = runTest {
        val h = Harness()
        val highlightA = h.seedHighlight("doc-A", page = 1, createdAt = 1L)
        val highlightB = h.seedHighlight("doc-B", page = 1, createdAt = 2L)
        val notesA = h.noteController("doc-A")
        val notesB = h.noteController("doc-B")
        notesA.create(h.anchorOf(highlightA), "A 的批注", nowMs = 10L)
        val noteBId = notesB.create(h.anchorOf(highlightB), "B 的批注", nowMs = 20L)

        // doc-A 控制器不能按全局 id 修改或删除 doc-B 的行。
        notesA.update(noteBId, "越界修改", nowMs = 30L)
        notesA.delete(noteBId)

        assertEquals(listOf("A 的批注"), notesA.list().map { it.content })
        assertEquals(listOf("B 的批注"), notesB.list().map { it.content })

        // 新 Repository/Controller 实例仍按文档隔离恢复。
        assertEquals(listOf("A 的批注"), h.reopenedNoteController("doc-A").list().map { it.content })
        assertEquals(listOf("B 的批注"), h.reopenedNoteController("doc-B").list().map { it.content })
    }

    @Test
    fun `highlight deletion is document scoped and does not delete linked note`() = runTest {
        val h = Harness()
        val highlightA = h.seedHighlight("doc-A", page = 3, createdAt = 1L)
        val highlightB = h.seedHighlight("doc-B", page = 3, createdAt = 2L)
        val notesB = h.noteController("doc-B")
        val noteBId = notesB.create(h.anchorOf(highlightB), "B", nowMs = 3L)

        ReaderHighlightController(h.repo, "doc-A").deleteHighlight(highlightB.id)
        assertTrue(h.highlightDao.rows.containsKey(highlightA.id))
        assertTrue(h.highlightDao.rows.containsKey(highlightB.id))

        ReaderHighlightController(h.repo, "doc-B").deleteHighlight(highlightB.id)
        assertNull(h.highlightDao.rows[highlightB.id])
        assertEquals("B", h.noteDao.rows[noteBId]?.content)
    }

    @Test
    fun `notes survive reopen with fresh controller instance`() = runTest {
        val h = Harness()
        val highlight = h.seedHighlight("pdf-1", page = 6, createdAt = 5L)
        val first = h.noteController("pdf-1")
        val anchor = h.anchorOf(highlight)
        first.create(anchor, "持久化批注", nowMs = 50L)

        // 模拟重开：全新实例读取同一存储。
        val reopened = h.reopenedNoteController("pdf-1")
        val restored = reopened.list().single()
        assertEquals("持久化批注", restored.content)
        assertNotEquals(null, restored.anchor)
        assertEquals(ReaderNoteController.anchorKey(anchor), ReaderNoteController.anchorKeyOf(restored))
        val restoredHighlights = ReaderHighlightController(
            ReadingRepository(NoBookmarks(), NoFavorites(), h.highlightDao, h.noteDao, com.markleaf.app.testing.testSearchRepository()),
            "pdf-1",
        ).itemsForPage(6)
        assertEquals(1, restoredHighlights.size)
        assertEquals(highlight.id, restoredHighlights.single().id)
    }
}
