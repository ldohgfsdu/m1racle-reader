package com.markleaf.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.markleaf.app.data.local.AppDatabase
import com.markleaf.app.data.local.DocumentDao
import com.markleaf.app.data.local.DocumentEntity
import com.markleaf.app.data.local.PdfPrintPageAnchorDao
import com.markleaf.app.data.local.PdfPrintPageAnchorEntity
import com.markleaf.app.data.local.TextbookOutlineNodeDao
import com.markleaf.app.data.local.TextbookOutlineNodeEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P0A lifecycle hardening: deleteById transaction consistency.
 *
 * Spec section 四 requires:
 * - Step 1 (outline delete) fails → all rollback.
 * - Step 2 (anchor delete) fails → all rollback.
 * - Step 3 (document delete) fails → all rollback.
 * - Success → document / outline / anchor all gone.
 *
 * These are instrumented tests (require real Room + SQLite). Marked NOT RUN
 * if no device is available.
 */
@RunWith(AndroidJUnit4::class)
class DeleteByIdTransactionTest {

    private lateinit var db: AppDatabase
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun seedDocument(docId: String) {
        val now = System.currentTimeMillis()
        runBlocking {
            db.documentDao().upsert(
                DocumentEntity(
                    id = docId,
                    displayName = "test-pdf",
                    uri = "file:///test.pdf",
                    mimeType = "application/pdf",
                    fileSize = 1024L,
                    lastOpenedAt = now,
                    createdAt = now,
                    lastScrollIndex = 3,
                    lastScrollOffset = 0,
                    readingProgress = 0.3f,
                    title = "Test",
                    headingCount = 1,
                    documentType = "PDF",
                    pageCount = 10,
                )
            )
            db.textbookOutlineNodeDao().upsert(
                TextbookOutlineNodeEntity(
                    id = "node-1",
                    documentId = docId,
                    parentId = null,
                    type = "CHAPTER",
                    title = "Chapter 1",
                    startPageIndex = 0,
                    endPageIndex = 5,
                    sortOrder = 0,
                    createdAt = now,
                    updatedAt = now,
                )
            )
            db.pdfPrintPageAnchorDao().upsert(
                PdfPrintPageAnchorEntity(
                    documentId = docId,
                    pdfPageIndex = 0,
                    printPageNumber = 1,
                    updatedAt = now,
                )
            )
        }
    }

    // ── Failing DAO wrappers ──────────────────────────────────────────

    /**
     * Wrapper that throws on deleteAllForDocument to simulate step-1 failure.
     */
    private class FailingOutlineDao(
        private val real: TextbookOutlineNodeDao,
    ) : TextbookOutlineNodeDao {
        override suspend fun listForDocument(documentId: String) =
            real.listForDocument(documentId)
        override suspend fun getById(id: String) = real.getById(id)
        override suspend fun upsert(node: TextbookOutlineNodeEntity) = real.upsert(node)
        override suspend fun update(node: TextbookOutlineNodeEntity) = real.update(node)
        override suspend fun deleteById(id: String) = real.deleteById(id)
        override suspend fun deleteAllForDocument(documentId: String) {
            throw RuntimeException("simulated outline delete failure")
        }
        override suspend fun listChildren(documentId: String, parentId: String) =
            real.listChildren(documentId, parentId)
        override suspend fun listRoots(documentId: String) = real.listRoots(documentId)
    }

    /**
     * Wrapper that throws on deleteAllForDocument to simulate step-2 failure.
     */
    private class FailingAnchorDao(
        private val real: PdfPrintPageAnchorDao,
    ) : PdfPrintPageAnchorDao {
        override suspend fun listForDocument(documentId: String) =
            real.listForDocument(documentId)
        override suspend fun upsert(anchor: PdfPrintPageAnchorEntity) = real.upsert(anchor)
        override suspend fun delete(documentId: String, pdfPageIndex: Int) =
            real.delete(documentId, pdfPageIndex)
        override suspend fun deleteAllForDocument(documentId: String) {
            throw RuntimeException("simulated anchor delete failure")
        }
    }

    /**
     * Wrapper that throws on deleteById to simulate step-3 failure.
     */
    private class FailingDocumentDao(
        private val real: DocumentDao,
    ) : DocumentDao {
        override fun observeRecentDocuments() = real.observeRecentDocuments()
        override suspend fun getById(id: String) = real.getById(id)
        override suspend fun getAll() = real.getAll()
        override suspend fun upsert(document: DocumentEntity) = real.upsert(document)
        override suspend fun deleteById(id: String) {
            throw RuntimeException("simulated document delete failure")
        }
        override suspend fun updateLastOpenedAt(id: String, time: Long) =
            real.updateLastOpenedAt(id, time)
        override suspend fun updateParseMetadata(id: String, title: String?, headingCount: Int) =
            real.updateParseMetadata(id, title, headingCount)
        override suspend fun updateReadingPosition(
            id: String,
            lastScrollIndex: Int,
            lastScrollOffset: Int,
            readingProgress: Float,
        ) = real.updateReadingPosition(id, lastScrollIndex, lastScrollOffset, readingProgress)
        override suspend fun updatePdfReadingPosition(
            id: String,
            pageIndex: Int,
            intraPageFractionEncoded: Int,
            pageCount: Int,
            readingProgress: Float,
        ) = real.updatePdfReadingPosition(
            id, pageIndex, intraPageFractionEncoded, pageCount, readingProgress
        )
        override suspend fun updatePdfViewportSession(
            id: String,
            version: Int,
            readingMode: String,
            pageIndex: Int,
            pageAnchorXFraction: Float,
            pageAnchorYFraction: Float,
            viewportAnchorXFraction: Float,
            viewportAnchorYFraction: Float,
            scale: Float,
            generation: Long,
        ) = real.updatePdfViewportSession(
            id, version, readingMode, pageIndex,
            pageAnchorXFraction, pageAnchorYFraction,
            viewportAnchorXFraction, viewportAnchorYFraction,
            scale, generation,
        )
        override suspend fun deleteAll() = real.deleteAll()
    }

    // ── Tests ─────────────────────────────────────────────────────────

    /**
     * Success: deleteById removes document, outline, and anchor.
     */
    @Test
    fun success_deletesAllThree() = runBlocking {
        seedDocument("doc-success")
        val repo = DocumentRepository(
            context = context,
            db = db,
            documentDao = db.documentDao(),
            pdfPrintPageAnchorDao = db.pdfPrintPageAnchorDao(),
            textbookOutlineNodeDao = db.textbookOutlineNodeDao(),
        )
        repo.deleteById("doc-success")

        assertNull("document deleted", db.documentDao().getById("doc-success"))
        assertTrue("outline empty", db.textbookOutlineNodeDao().listForDocument("doc-success").isEmpty())
        assertTrue("anchor empty", db.pdfPrintPageAnchorDao().listForDocument("doc-success").isEmpty())
    }

    /**
     * Step 1 (outline delete) fails → document and anchor must survive (rollback).
     */
    @Test
    fun step1OutlineFails_rollsBackAll() = runBlocking {
        seedDocument("doc-step1")
        val repo = DocumentRepository(
            context = context,
            db = db,
            documentDao = db.documentDao(),
            pdfPrintPageAnchorDao = db.pdfPrintPageAnchorDao(),
            textbookOutlineNodeDao = FailingOutlineDao(db.textbookOutlineNodeDao()),
        )
        try {
            repo.deleteById("doc-step1")
            fail("expected RuntimeException from outline delete")
        } catch (e: RuntimeException) {
            // expected
        }

        assertNotNull("document survived rollback", db.documentDao().getById("doc-step1"))
        assertTrue("outline survived rollback", db.textbookOutlineNodeDao().listForDocument("doc-step1").isNotEmpty())
        assertTrue("anchor survived rollback", db.pdfPrintPageAnchorDao().listForDocument("doc-step1").isNotEmpty())
    }

    /**
     * Step 2 (anchor delete) fails → document and outline must survive (rollback).
     * Outline was already deleted in step 1, but the transaction must roll back
     * that deletion, restoring the outline row.
     */
    @Test
    fun step2AnchorFails_rollsBackAll() = runBlocking {
        seedDocument("doc-step2")
        val repo = DocumentRepository(
            context = context,
            db = db,
            documentDao = db.documentDao(),
            pdfPrintPageAnchorDao = FailingAnchorDao(db.pdfPrintPageAnchorDao()),
            textbookOutlineNodeDao = db.textbookOutlineNodeDao(),
        )
        try {
            repo.deleteById("doc-step2")
            fail("expected RuntimeException from anchor delete")
        } catch (e: RuntimeException) {
            // expected
        }

        assertNotNull("document survived rollback", db.documentDao().getById("doc-step2"))
        assertTrue("outline survived rollback (step 1 restored)", db.textbookOutlineNodeDao().listForDocument("doc-step2").isNotEmpty())
        assertTrue("anchor survived rollback", db.pdfPrintPageAnchorDao().listForDocument("doc-step2").isNotEmpty())
    }

    /**
     * Step 3 (document delete) fails → document, outline, and anchor must
     * all survive (rollback restores outline and anchor deleted in steps 1-2).
     */
    @Test
    fun step3DocumentFails_rollsBackAll() = runBlocking {
        seedDocument("doc-step3")
        val repo = DocumentRepository(
            context = context,
            db = db,
            documentDao = FailingDocumentDao(db.documentDao()),
            pdfPrintPageAnchorDao = db.pdfPrintPageAnchorDao(),
            textbookOutlineNodeDao = db.textbookOutlineNodeDao(),
        )
        try {
            repo.deleteById("doc-step3")
            fail("expected RuntimeException from document delete")
        } catch (e: RuntimeException) {
            // expected
        }

        assertNotNull("document survived rollback", db.documentDao().getById("doc-step3"))
        assertTrue("outline survived rollback (steps 1-2 restored)", db.textbookOutlineNodeDao().listForDocument("doc-step3").isNotEmpty())
        assertTrue("anchor survived rollback (step 2 restored)", db.pdfPrintPageAnchorDao().listForDocument("doc-step3").isNotEmpty())
    }
}
