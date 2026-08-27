package com.m1racle.reader.ui.annotation

import com.m1racle.reader.data.anchor.AnchorCodec
import com.m1racle.reader.data.anchor.DocumentAnchor
import com.m1racle.reader.data.anchor.NormalizedRect
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
import com.m1racle.reader.ui.reader.ReaderHighlightController
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R28: annotation page-load/query-count and Compose state-scope contracts. */
class AnnotationPerformanceContractTest {

    private class CountingHighlightDao : HighlightDao {
        val rows = LinkedHashMap<Long, HighlightEntity>()
        var listAtLocationCalls = 0
        var listForDocumentCalls = 0

        override suspend fun insert(highlight: HighlightEntity): Long = error("unused")
        override suspend fun delete(documentId: String, id: Long) = error("unused")
        override suspend fun getById(documentId: String, id: Long): HighlightEntity? = error("unused")
        override suspend fun listAtLocation(
            documentId: String,
            locationKind: String,
            location: Int,
        ): List<HighlightEntity> {
            listAtLocationCalls++
            return rows.values.filter {
                it.documentId == documentId && it.locationKind == locationKind && it.location == location
            }.sortedWith(compareBy({ it.createdAt }, { it.id }))
        }

        override suspend fun listForDocument(documentId: String): List<HighlightEntity> {
            listForDocumentCalls++
            return rows.values.filter { it.documentId == documentId }
        }
    }

    private class CountingNoteDao : NoteDao {
        val rows = LinkedHashMap<Long, NoteEntity>()
        var listAtLocationCalls = 0
        var listForDocumentCalls = 0

        override suspend fun insert(note: NoteEntity): Long = error("unused")
        override suspend fun updateContent(documentId: String, id: Long, content: String, updatedAt: Long) = error("unused")
        override suspend fun delete(documentId: String, id: Long) = error("unused")
        override suspend fun getById(documentId: String, id: Long): NoteEntity? = error("unused")
        override suspend fun listAtLocation(
            documentId: String,
            locationKind: String,
            location: Int,
        ): List<NoteEntity> {
            listAtLocationCalls++
            return rows.values.filter {
                it.documentId == documentId && it.locationKind == locationKind && it.location == location
            }.sortedWith(compareBy({ it.createdAt }, { it.id }))
        }

        override suspend fun listForDocument(documentId: String): List<NoteEntity> {
            listForDocumentCalls++
            return rows.values.filter { it.documentId == documentId }
        }
    }

    @Test
    fun `highlight overlay uses one page query at 10 100 and 1000 rows`() = runTest {
        for (total in listOf(10, 100, 1_000)) {
            val highlights = CountingHighlightDao()
            val notes = CountingNoteDao()
            repeat(total) { index ->
                val page = index % 10
                val id = index + 1L
                val anchor = DocumentAnchor.PdfPage(
                    page = page,
                    rect = NormalizedRect(0.1f, 0.2f, 0.3f, 0.1f),
                )
                highlights.rows[id] = HighlightEntity(
                    id = id,
                    documentId = "doc",
                    locationKind = "PAGE_INDEX",
                    location = page,
                    anchor = AnchorCodec.encode(anchor),
                    excerpt = "h-$id",
                    createdAt = id,
                )
                notes.rows[id] = NoteEntity(
                    id = id,
                    documentId = "doc",
                    locationKind = "PAGE_INDEX",
                    location = page,
                    anchor = AnchorCodec.encode(anchor),
                    content = "n-$id",
                    createdAt = id,
                    updatedAt = id,
                )
            }

            val items = ReaderHighlightController(repository(highlights, notes), "doc")
                .itemsForPage(page = 7)

            assertEquals("target-page rows at scale $total", total / 10, items.size)
            assertTrue(items.all { it.type == AnnotationType.HIGHLIGHT })
            assertEquals(1, highlights.listAtLocationCalls)
            assertEquals(0, highlights.listForDocumentCalls)
            assertEquals(0, notes.listAtLocationCalls)
            assertEquals(0, notes.listForDocumentCalls)
        }
    }

    @Test
    fun `combined overlay makes one exact query per annotation type`() = runTest {
        val highlights = CountingHighlightDao()
        val notes = CountingNoteDao()
        highlights.rows[1] = highlight(id = 1, documentId = "doc", page = 4)
        highlights.rows[2] = highlight(id = 2, documentId = "doc", page = 5)
        notes.rows[1] = note(id = 1, documentId = "doc", page = 4)
        notes.rows[2] = note(id = 2, documentId = "other", page = 4)

        val items = RepositoryAnnotationOverlaySource(repository(highlights, notes))
            .itemsFor("doc", "PAGE_INDEX", 4)

        assertEquals(listOf(AnnotationType.HIGHLIGHT, AnnotationType.NOTE), items.map { it.type })
        assertEquals(1, highlights.listAtLocationCalls)
        assertEquals(1, notes.listAtLocationCalls)
        assertEquals(0, highlights.listForDocumentCalls)
        assertEquals(0, notes.listForDocumentCalls)
    }

    @Test
    fun `reader keeps overlay state page-local and management uses one lazy list`() {
        val screen = source("app/src/main/java/com/m1racle/reader/ui/pdf/PdfViewerScreen.kt")
        assertTrue(screen.contains("var pageItems by remember(state.document.id, page)"))
        assertFalse(screen.contains("pageHighlightItems"))
        assertFalse(screen.contains("mutableStateMapOf<Int, List<AnnotationOverlayItem>>"))

        val sheet = source("app/src/main/java/com/m1racle/reader/ui/annotation/AnnotationsManagementSheet.kt")
        assertEquals(1, Regex("\\bLazyColumn\\s*\\(").findAll(sheet).count())
    }

    private fun repository(highlights: HighlightDao, notes: NoteDao) =
        ReadingRepository(NoBookmarks(), NoFavorites(), highlights, notes, com.m1racle.reader.testing.testSearchRepository())

    private fun highlight(id: Long, documentId: String, page: Int): HighlightEntity {
        val anchor = DocumentAnchor.PdfPage(page, NormalizedRect(0.1f, 0.2f, 0.3f, 0.1f))
        return HighlightEntity(
            id = id,
            documentId = documentId,
            locationKind = "PAGE_INDEX",
            location = page,
            anchor = AnchorCodec.encode(anchor),
            createdAt = id,
        )
    }

    private fun note(id: Long, documentId: String, page: Int): NoteEntity {
        val anchor = DocumentAnchor.PdfPage(page, NormalizedRect(0.1f, 0.2f, 0.3f, 0.1f))
        return NoteEntity(
            id = id,
            documentId = documentId,
            locationKind = "PAGE_INDEX",
            location = page,
            anchor = AnchorCodec.encode(anchor),
            content = "note-$id",
            createdAt = id,
            updatedAt = id,
        )
    }

    private fun source(relativePath: String): String {
        val candidates = listOf(File(relativePath), File("../$relativePath"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("source not found: $relativePath")
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
}
