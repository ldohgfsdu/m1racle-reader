package com.markleaf.app.data.repository

import com.markleaf.app.data.anchor.DocumentAnchor
import com.markleaf.app.data.local.BookmarkDao
import com.markleaf.app.data.local.BookmarkEntity
import com.markleaf.app.data.local.BookmarkWithDocument
import com.markleaf.app.data.local.DocumentEntity
import com.markleaf.app.data.local.DocumentType
import com.markleaf.app.data.local.FavoriteDocumentDao
import com.markleaf.app.data.local.FavoriteDocumentEntity
import com.markleaf.app.data.local.HighlightDao
import com.markleaf.app.data.local.HighlightEntity
import com.markleaf.app.data.local.NoteDao
import com.markleaf.app.data.local.NoteEntity
import com.markleaf.app.data.local.SearchContentType
import com.markleaf.app.testing.TestSearchIndexDao
import com.markleaf.app.ui.navigation.Routes
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** R30 contracts across authoritative annotation writes, derived index and navigation. */
class SearchExperienceIntegrationTest {
    private lateinit var indexDao: TestSearchIndexDao
    private lateinit var search: SearchRepository
    private lateinit var highlights: FakeHighlightDao
    private lateinit var notes: FakeNoteDao
    private lateinit var reading: ReadingRepository

    @Before
    fun setUp() {
        indexDao = TestSearchIndexDao()
        search = SearchRepository(indexDao)
        highlights = FakeHighlightDao()
        notes = FakeNoteDao()
        reading = ReadingRepository(
            bookmarkDao = NoBookmarks(),
            favoriteDao = NoFavorites(),
            highlightDao = highlights,
            noteDao = notes,
            searchRepository = search,
        )
    }

    @Test
    fun `document create update and delete retain a complete searchable location`() = runTest {
        indexDao.documentTitles["pdf"] = "初始标题"
        search.index(document("pdf", "初始标题", DocumentType.PDF), indexedAt = 1L)
        val initial = search.search("初始标题").single()
        assertEquals("初始标题", initial.documentTitle)
        assertEquals(DocumentAnchor.PdfPage(0), initial.anchor)

        indexDao.documentTitles["pdf"] = "解析后标题"
        search.index(document("pdf", "解析后标题", DocumentType.PDF), indexedAt = 2L)
        assertTrue(search.search("初始标题").isEmpty())
        assertEquals("解析后标题", search.search("解析后标题").single().documentTitle)

        search.clearDocument("pdf")
        assertTrue(search.search("解析后标题").isEmpty())
    }

    @Test
    fun `highlight and note mutations incrementally update and delete search rows`() = runTest {
        indexDao.documentTitles["md"] = "生理学"
        val highlightId = reading.addHighlight(
            documentId = "md",
            anchor = DocumentAnchor.MarkdownBlock(blockIndex = 4, charStart = 2, charEnd = 8),
            excerpt = "呼吸高亮",
            createdAt = 10L,
        )
        val noteId = reading.addNote(
            documentId = "md",
            anchor = DocumentAnchor.MarkdownBlock(blockIndex = 7),
            content = "旧笔记",
            createdAt = 20L,
        )

        val highlight = search.search("呼吸高亮").single()
        assertEquals(SearchContentType.HIGHLIGHT, highlight.contentType)
        assertEquals(DocumentAnchor.MarkdownBlock(4, 2, 8), highlight.anchor)
        assertEquals("生理学", highlight.documentTitle)

        reading.updateNoteContent("md", noteId, "新笔记", updatedAt = 30L)
        assertTrue(search.search("旧笔记").isEmpty())
        val updated = search.search("新笔记").single()
        assertEquals(30L, updated.indexedAt)
        assertEquals(DocumentAnchor.MarkdownBlock(7), updated.anchor)
        assertEquals(20L, notes.rows.getValue(noteId).createdAt)

        reading.removeHighlight("md", highlightId)
        reading.removeNote("md", noteId)
        assertTrue(search.search("呼吸高亮").isEmpty())
        assertTrue(search.search("新笔记").isEmpty())
        assertTrue(highlights.rows.isEmpty())
        assertTrue(notes.rows.isEmpty())
    }

    @Test
    fun `document scoped search remains isolated with joined titles`() = runTest {
        indexDao.documentTitles.putAll(mapOf("a" to "文档 A", "b" to "文档 B"))
        reading.addNote("a", DocumentAnchor.MarkdownBlock(2), "共同关键词 A", createdAt = 1L)
        reading.addNote("b", DocumentAnchor.PdfPage(9), "共同关键词 B", createdAt = 2L)

        val onlyA = search.search("共同关键词", documentId = "a")
        assertEquals(listOf("a"), onlyA.map { it.documentId })
        assertEquals("文档 A", onlyA.single().documentTitle)
        assertEquals(2, onlyA.single().location)
    }

    @Test
    fun `search routes round trip canonical anchors and reuse reader authorities`() {
        val pdf = DocumentAnchor.PdfPage(page = 12)
        val markdown = DocumentAnchor.MarkdownBlock(blockIndex = 8, charStart = 1, charEnd = 5)
        assertEquals(pdf, Routes.decodeAnchor(Routes.pdfViewer("pdf", pdf).substringAfter("anchor=")))
        assertEquals(markdown, Routes.decodeAnchor(Routes.viewer("md", markdown).substringAfter("anchor=")))
        assertEquals("viewer/md", Routes.viewer("md"))

        val nav = source("ui/navigation/NavGraph.kt")
        val viewer = source("ui/viewer/ViewerScreen.kt")
        val pdfViewer = source("ui/pdf/PdfViewerScreen.kt")
        assertTrue(nav.contains("openDocument(navController, document, result.anchor)"))
        assertTrue(viewer.contains("scrollToBlockIndex("))
        assertTrue(pdfViewer.contains("viewModel.goToPage(target.page)"))
    }

    @Test
    fun `authoritative write boundaries own incremental indexing`() {
        val documents = source("data/repository/DocumentRepository.kt")
        val annotations = source("data/repository/ReadingRepository.kt")
        val searchDao = source("data/local/SearchIndexDao.kt")
        assertTrue(documents.contains("private suspend fun upsertAndIndex"))
        assertTrue(documents.contains("upsertAndIndex(entity)"))
        assertTrue(documents.contains("searchRepository.clearDocument(id)"))
        assertFalse(documents.contains("documentDao.upsert(entity)"))
        assertTrue(annotations.contains("searchRepository.index(row.copy(id = id)"))
        assertTrue(annotations.contains("searchRepository.delete(documentId, SearchContentType.HIGHLIGHT"))
        assertTrue(annotations.contains("searchRepository.delete(documentId, SearchContentType.NOTE"))
        assertTrue(searchDao.contains("INNER JOIN documents ON documents.id = search_index.documentId"))
        assertTrue(searchDao.contains("COALESCE(NULLIF(TRIM(documents.title), ''), documents.displayName)"))
    }

    @Test
    fun `search UI exposes both library entries and complete result presentation`() {
        val home = source("ui/home/HomeScreen.kt")
        val library = source("ui/hub/LibraryScreen.kt")
        val screen = source("ui/search/SearchScreen.kt")
        assertTrue(home.contains("onOpenSearch"))
        assertTrue(library.contains("onOpenSearch"))
        assertTrue(screen.contains("result.documentTitle"))
        assertTrue(screen.contains("result.typeLabel()"))
        assertTrue(screen.contains("result.locationLabel()"))
        assertTrue(screen.contains("result.content"))
    }

    private fun source(relative: String): String = listOf(
        File("app/src/main/java/com/markleaf/app/$relative"),
        File("src/main/java/com/markleaf/app/$relative"),
    ).first { it.isFile }.readText()

    private fun document(id: String, title: String, type: DocumentType) = DocumentEntity(
        id = id,
        displayName = title,
        uri = "content://test/$id",
        mimeType = null,
        fileSize = 100L,
        lastOpenedAt = 1L,
        createdAt = 1L,
        lastScrollIndex = 0,
        lastScrollOffset = 0,
        readingProgress = 0f,
        title = title,
        headingCount = 0,
        documentType = type.name,
    )

    private class FakeHighlightDao : HighlightDao {
        val rows = linkedMapOf<Long, HighlightEntity>()
        private var nextId = 1L
        override suspend fun insert(highlight: HighlightEntity): Long = nextId.also {
            rows[it] = highlight.copy(id = it)
            nextId++
        }
        override suspend fun delete(documentId: String, id: Long) {
            if (rows[id]?.documentId == documentId) rows.remove(id)
        }
        override suspend fun getById(documentId: String, id: Long) = rows[id]?.takeIf { it.documentId == documentId }
        override suspend fun listAtLocation(documentId: String, locationKind: String, location: Int) =
            rows.values.filter { it.documentId == documentId && it.locationKind == locationKind && it.location == location }
        override suspend fun listForDocument(documentId: String) = rows.values.filter { it.documentId == documentId }
    }

    private class FakeNoteDao : NoteDao {
        val rows = linkedMapOf<Long, NoteEntity>()
        private var nextId = 1L
        override suspend fun insert(note: NoteEntity): Long = nextId.also {
            rows[it] = note.copy(id = it)
            nextId++
        }
        override suspend fun updateContent(documentId: String, id: Long, content: String, updatedAt: Long) {
            rows[id]?.takeIf { it.documentId == documentId }?.let {
                rows[id] = it.copy(content = content, updatedAt = updatedAt)
            }
        }
        override suspend fun delete(documentId: String, id: Long) {
            if (rows[id]?.documentId == documentId) rows.remove(id)
        }
        override suspend fun getById(documentId: String, id: Long) = rows[id]?.takeIf { it.documentId == documentId }
        override suspend fun listAtLocation(documentId: String, locationKind: String, location: Int) =
            rows.values.filter { it.documentId == documentId && it.locationKind == locationKind && it.location == location }
        override suspend fun listForDocument(documentId: String) = rows.values.filter { it.documentId == documentId }
    }

    private class NoBookmarks : BookmarkDao {
        override suspend fun insert(bookmark: BookmarkEntity) = error("unused")
        override suspend fun delete(id: Long) = error("unused")
        override suspend fun listForDocument(documentId: String) = emptyList<BookmarkEntity>()
        override fun observeAll(): Flow<List<BookmarkWithDocument>> = emptyFlow()
        override suspend fun getAt(documentId: String, locationKind: String, location: Int) = null
        override suspend fun deleteAt(documentId: String, locationKind: String, location: Int) = 0
        override suspend fun listForDocumentAllKinds(documentId: String) = emptyList<BookmarkEntity>()
    }

    private class NoFavorites : FavoriteDocumentDao {
        override suspend fun insert(favorite: FavoriteDocumentEntity) = error("unused")
        override suspend fun delete(documentId: String) = error("unused")
        override suspend fun getAll() = emptyList<FavoriteDocumentEntity>()
        override suspend fun get(documentId: String): FavoriteDocumentEntity? = null
    }
}
