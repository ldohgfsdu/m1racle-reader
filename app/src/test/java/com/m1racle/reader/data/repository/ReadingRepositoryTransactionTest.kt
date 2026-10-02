package com.m1racle.reader.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.m1racle.reader.data.anchor.DocumentAnchor
import com.m1racle.reader.data.local.*
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class ReadingRepositoryTransactionTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: ReadingRepository
    private var failIndex = false

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        db.documentDao().insertDocument(DocumentEntity("doc", "book", "file:///book.pdf", "application/pdf",
            10, 1, 1, 0, 0, 0f, null, 0, DocumentType.PDF.name))
        val realIndex = db.searchIndexDao()
        val index = object : SearchIndexDao by realIndex {
            override suspend fun upsert(entry: SearchIndexEntity) {
                if (failIndex) throw IOException("index write failed")
                realIndex.upsert(entry)
            }
            override suspend fun delete(documentId: String, contentType: String, sourceId: String) {
                if (failIndex) throw IOException("index delete failed")
                realIndex.delete(documentId, contentType, sourceId)
            }
        }
        repository = ReadingRepository(db.bookmarkDao(), db.favoriteDocumentDao(), db.highlightDao(),
            db.noteDao(), SearchRepository(index), RoomRepositoryTransactionRunner(db))
    }

    @After fun tearDown() { db.close() }

    private suspend fun rejected(block: suspend () -> Unit) {
        try { block(); fail("Expected failure") } catch (_: IOException) { }
    }

    @Test fun failedIndexInsertRollsBackBothAnnotationTypes() = runBlocking {
        failIndex = true
        rejected { repository.addHighlight("doc", DocumentAnchor.PdfPage(0), 1, "quote") }
        rejected { repository.addNote("doc", DocumentAnchor.PdfPage(0), "note", 1) }
        assertTrue(repository.highlightsForDocument("doc").isEmpty())
        assertTrue(repository.notesForDocument("doc").isEmpty())
    }

    @Test fun failedIndexUpdateOrDeletePreservesOriginalSourceAndIndex() = runBlocking {
        val note = repository.addNote("doc", DocumentAnchor.PdfPage(0), "original", 1)
        val highlight = repository.addHighlight("doc", DocumentAnchor.PdfPage(0), 1, "excerpt")
        failIndex = true
        rejected { repository.updateNoteContent("doc", note, "changed", 2) }
        rejected { repository.removeNote("doc", note) }
        rejected { repository.removeHighlight("doc", highlight) }
        assertEquals("original", repository.notesForDocument("doc").single().content)
        assertEquals("excerpt", repository.highlightsForDocument("doc").single().excerpt)
        assertEquals(1, SearchRepository(db.searchIndexDao()).search("original").size)
        assertEquals(1, SearchRepository(db.searchIndexDao()).search("excerpt").size)
        failIndex = false
        repository.removeNote("doc", note)
        repository.removeHighlight("doc", highlight)
        assertTrue(SearchRepository(db.searchIndexDao()).search("original").isEmpty())
        assertTrue(SearchRepository(db.searchIndexDao()).search("excerpt").isEmpty())
    }
}
