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
import com.m1racle.reader.ui.annotation.AnnotationOverlayItem
import com.m1racle.reader.ui.annotation.AnnotationType
import com.m1racle.reader.ui.annotation.PdfHighlightGeometry
import com.m1racle.reader.ui.annotation.RepositoryAnnotationOverlaySource
import com.m1racle.reader.ui.annotation.TextSelection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R26.1 PDF 高亮闭环契约：
 * 1. selection → anchor → repository → overlay 完整链路；
 * 2. 不同页面尺寸恢复：归一化锚点 × 任意渲染尺寸，比例几何恒定（缩放不漂移）；
 * 3. 删除高亮后覆盖项消失。
 */
class PdfHighlightLoopTest {

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

        /** 覆盖源会查询 notes（合并渲染）——返回空而非抛错。 */
        override suspend fun insert(note: NoteEntity): Long = error("unused")
        override suspend fun updateContent(documentId: String, id: Long, content: String, updatedAt: Long) = error("unused")
        override suspend fun delete(documentId: String, id: Long) = error("unused")
        override suspend fun getById(documentId: String, id: Long): NoteEntity? = null
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

    /** 捕获：与渲染层同一几何入口。 */
    private fun capture(page: Int, l: Float, t: Float, r: Float, b: Float, w: Float, h: Float) =
        com.m1racle.reader.ui.annotation.TextSelection.PdfSelection(page,
            listOf(com.m1racle.reader.data.anchor.NormalizedRect(l/w,t/h,(r-l)/w,(b-t)/h)))

    @Test
    fun `full loop - selection to flow to repository to overlay items`() = runTest {
        val highlights = FakeHighlightDao()
        val repo = ReadingRepository(NoBookmarks(), NoFavorites(), highlights, FakeNoteDao(), com.m1racle.reader.testing.testSearchRepository())
        val controller = ReaderHighlightController(repo, "pdf-1")

        // 用户在页 9 拖出矩形（捕获时刻页面渲染 1000×1414）。
        val selection = capture(9, 100f, 200f, 400f, 320f, 1000f, 1414f)
        val committed = controller.commitHighlight(selection, nowMs = 777L)
        assertTrue(committed is ReaderHighlightController.CommitResult.Success)

        // Overlay 端按 (PAGE_INDEX, 9) 取快照。
        val source = RepositoryAnnotationOverlaySource(repo)
        val items = source.itemsFor("pdf-1", "PAGE_INDEX", 9)
        assertEquals(1, items.size)
        val item = items.single()
        assertEquals(AnnotationType.HIGHLIGHT, item.type)
        assertEquals(null, item.excerpt) // 本轮选区未携带摘录

        // 屏幕矩形恢复 == 选择时的归一化 × 当前渲染尺寸。
        val expected = selection.toAnchor() as DocumentAnchor.PdfPage
        val restoredRect = item.pdfRectsNormalized().single()
        assertEquals(expected.rects.single(), restoredRect)
    }

    @Test
    fun `same anchor restores consistently across different page render sizes`() = runTest {
        val repo = ReadingRepository(NoBookmarks(), NoFavorites(), FakeHighlightDao(), FakeNoteDao(), com.m1racle.reader.testing.testSearchRepository())
        val controller = ReaderHighlightController(repo, "pdf-1")
        // 在小尺寸页面捕获。
        val selection = capture(3, 40f, 60f, 240f, 180f, 595f, 842f)
        controller.commitHighlight(selection, nowMs = 1L)

        val item: AnnotationOverlayItem = RepositoryAnnotationOverlaySource(repo)
            .itemsFor("pdf-1", "PAGE_INDEX", 3).single()
        val n = item.pdfRectsNormalized().single()

        // 任意两种渲染尺寸下换算出的相对几何必须一致（缩放不漂移）。
        listOf(595f to 842f, 1785f to 2526f, 300f to 500f).forEach { (w, h) ->
            val screen = PdfHighlightGeometry.toScreenRect(n, w, h)
            val relativeLeft = screen.left / w
            val relativeTop = screen.top / h
            val relativeWidth = (screen.right - screen.left) / w
            val relativeHeight = (screen.bottom - screen.top) / h
            assertEquals(n.x, relativeLeft, 1e-6f)
            assertEquals(n.y, relativeTop, 1e-6f)
            assertEquals(n.width, relativeWidth, 1e-6f)
            assertEquals(n.height, relativeHeight, 1e-6f)
        }
    }

    @Test
    fun `delete highlight removes overlay item`() = runTest {
        val repo = ReadingRepository(NoBookmarks(), NoFavorites(), FakeHighlightDao(), FakeNoteDao(), com.m1racle.reader.testing.testSearchRepository())
        val controller = ReaderHighlightController(repo, "pdf-1")
        val selection = capture(2, 10f, 10f, 200f, 120f, 800f, 600f)
        val committed = controller.commitHighlight(selection) as ReaderHighlightController.CommitResult.Success
        assertEquals(1, controller.itemsForPage(2).size)

        controller.deleteHighlight((committed as ReaderHighlightController.CommitResult.Success).id)
        assertTrue(controller.itemsForPage(2).isEmpty())
    }

    @Test
    fun `multiline highlight restores and deletes as one item`() = runTest {
        val repo = ReadingRepository(NoBookmarks(), NoFavorites(), FakeHighlightDao(), FakeNoteDao(), com.m1racle.reader.testing.testSearchRepository())
        val controller = ReaderHighlightController(repo,"pdf-1")
        val rects = listOf(
            com.m1racle.reader.data.anchor.NormalizedRect(.2f,.2f,.3f,.02f),
            com.m1racle.reader.data.anchor.NormalizedRect(.1f,.3f,.2f,.02f),
        )
        val selection = com.m1racle.reader.ui.annotation.TextSelection.PdfSelection(3,rects,"first\nsecond")
        val result = controller.commitHighlight(selection) as ReaderHighlightController.CommitResult.Success
        val reopened = ReaderHighlightController(repo,"pdf-1").itemsForPage(3).single()
        assertEquals(rects,reopened.pdfRectsNormalized())
        assertEquals("first\nsecond",reopened.excerpt)
        controller.deleteHighlight(result.id)
        assertTrue(controller.itemsForPage(3).isEmpty())
    }

    @Test
    fun `blank text selection is rejected before persistence`() = runTest {
        val repo = ReadingRepository(NoBookmarks(), NoFavorites(), FakeHighlightDao(), FakeNoteDao(), com.m1racle.reader.testing.testSearchRepository())
        val controller = ReaderHighlightController(repo, "pdf-1")
        val result = controller.commitHighlight(com.m1racle.reader.ui.annotation.TextSelection.PdfSelection(1, emptyList()))
        assertTrue(result is ReaderHighlightController.CommitResult.Failure)
        assertTrue(controller.itemsForPage(1).isEmpty())
    }
}
