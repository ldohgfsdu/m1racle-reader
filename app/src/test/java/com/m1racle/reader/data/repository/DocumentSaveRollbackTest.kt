package com.m1racle.reader.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.m1racle.reader.core.model.ContentSha256
import com.m1racle.reader.data.local.AppDatabase
import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.data.local.DocumentType
import com.m1racle.reader.data.local.SearchContentType
import com.m1racle.reader.data.local.SearchIndexDao
import com.m1racle.reader.data.local.SearchIndexEntity
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.SQLiteMode

/**
 * Real Room transaction proof that a failed search-index write aborts a PDF save
 * instead of leaving a half-updated row.
 *
 * This guarantee used to be proven only through the Markdown annotation-rebase
 * transaction, which went away with the Markdown editor. The principle is not
 * format-specific: saveDocumentEdits writes the new revision and indexes it in
 * one transaction, so a failure in the second step must undo the first.
 *
 * Runs on a real in-memory Room database, not the immediate-runner test double,
 * because a double cannot demonstrate a rollback.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class DocumentSaveRollbackTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var source: File
    private lateinit var document: DocumentEntity

    @Before
    fun setup(): Unit = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        source = File(context.cacheDir, "rollback-${System.nanoTime()}.pdf").apply {
            writeText(ORIGINAL_BODY)
        }
        document = DocumentEntity(
            id = "pdf-document",
            displayName = "rollback.pdf",
            uri = source.toURI().toString(),
            mimeType = "application/pdf",
            fileSize = source.length(),
            lastOpenedAt = 1,
            createdAt = 1,
            lastScrollIndex = 12,
            lastScrollOffset = 3,
            readingProgress = 0.25f,
            title = null,
            headingCount = 0,
            documentType = DocumentType.PDF.name,
            contentSha256 = ContentSha256.of(source),
        )
        db.documentDao().insertDocument(document)
    }

    @After
    fun teardown() {
        db.close()
        source.delete()
    }

    @Test
    fun searchIndexFailureLeavesThePdfRowAndSourceUntouched() = runBlocking {
        val realIndex = db.searchIndexDao()
        val failingDao = object : SearchIndexDao by realIndex {
            override suspend fun upsert(entry: SearchIndexEntity) {
                if (entry.contentType == SearchContentType.DOCUMENT.name) {
                    throw IOException("Injected document index failure")
                }
                realIndex.upsert(entry)
            }
        }
        val candidate = File(context.cacheDir, "rollback-candidate-${System.nanoTime()}.pdf").apply {
            writeText(REPLACEMENT_BODY)
        }

        try {
            repository(failingDao).saveDocumentEdits(document.id, document.contentSha256!!, candidate)
            fail("Expected the index failure to abort the save")
        } catch (_: IOException) {
        }

        val after = db.documentDao().getById(document.id)!!
        assertEquals("row must keep the committed revision", document.uri, after.uri)
        assertEquals(document.contentSha256, after.contentSha256)
        assertEquals(document.fileSize, after.fileSize)
        assertEquals(document.lastScrollIndex, after.lastScrollIndex)
        assertEquals(document.readingProgress, after.readingProgress, 0f)
        assertEquals("source file must not be rewritten", ORIGINAL_BODY, source.readText())
    }

    @Test
    fun successfulSaveCommitsTheNewRevisionInOneTransaction() = runBlocking {
        val candidate = File(context.cacheDir, "commit-candidate-${System.nanoTime()}.pdf").apply {
            writeText(REPLACEMENT_BODY)
        }

        val saved = repository(db.searchIndexDao())
            .saveDocumentEdits(document.id, document.contentSha256!!, candidate)

        assertEquals(document.id, saved.id)
        assertEquals(ContentSha256.of(candidate), saved.contentSha256)
        assertEquals(document.lastScrollIndex, saved.lastScrollIndex)
        assertEquals("reading progress survives a save", document.readingProgress, saved.readingProgress, 0f)
    }

    private fun repository(searchIndexDao: SearchIndexDao): DocumentRepository =
        DocumentRepository(
            context = context,
            db = db,
            documentDao = db.documentDao(),
            pdfPrintPageAnchorDao = db.pdfPrintPageAnchorDao(),
            textbookOutlineNodeDao = db.textbookOutlineNodeDao(),
            searchRepository = SearchRepository(searchIndexDao),
        )

    private companion object {
        const val ORIGINAL_BODY = "%PDF-1.4\noriginal\n%%EOF\n"
        const val REPLACEMENT_BODY = "%PDF-1.4\nreplacement\n%%EOF\n"
    }
}
