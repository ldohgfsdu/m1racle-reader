package com.m1racle.reader.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m1racle.reader.data.local.AppDatabase
import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.pdf.PdfReadingMode
import com.m1racle.reader.pdf.PdfViewportSessionState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P0A3: atomicity tests for [DocumentRepository.updatePdfProgressSnapshotAtomic].
 *
 * Spec section 三 requires:
 * 1. position + session success → both updated.
 * 2. session DAO throws → position rolled back.
 * 3. position DAO throws → both stay at old values.
 * 4. document does not exist → explicit behavior (no-op, not silent success).
 * 5. consecutive gen1 then gen2 → DB holds gen2 snapshot.
 *
 * These are instrumented tests (require real Room + SQLite). Marked NOT RUN
 * if no device is available.
 */
@RunWith(AndroidJUnit4::class)
class UpdatePdfProgressSnapshotAtomicTest {

    private lateinit var db: AppDatabase
    private lateinit var context: Context
    private lateinit var repository: DocumentRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = DocumentRepository(
            context = context,
            db = db,
            documentDao = db.documentDao(),
            pdfPrintPageAnchorDao = db.pdfPrintPageAnchorDao(),
            textbookOutlineNodeDao = db.textbookOutlineNodeDao(),
            searchRepository = SearchRepository(db.searchIndexDao()),
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun seedDocument(docId: String, pageCount: Int = 10): DocumentEntity {
        val now = System.currentTimeMillis()
        val entity = DocumentEntity(
            id = docId,
            displayName = "test-pdf",
            uri = "file:///test.pdf",
            mimeType = "application/pdf",
            fileSize = 1024L,
            lastOpenedAt = now,
            createdAt = now,
            lastScrollIndex = 0,
            lastScrollOffset = 0,
            readingProgress = 0f,
            title = "Test",
            headingCount = 0,
            documentType = "PDF",
            pageCount = pageCount,
        )
        runBlocking { db.documentDao().upsert(entity) }
        return entity
    }

    private fun makeSession(
        gen: Long,
        pageIndex: Int = 2,
        scale: Float = 2.5f,
    ): PdfViewportSessionState = PdfViewportSessionState(
        version = 1,
        readingMode = PdfReadingMode.VERTICAL_CONTINUOUS,
        pageIndex = pageIndex,
        pageAnchorXFraction = 0.1f,
        pageAnchorYFraction = 0.2f,
        viewportAnchorXFraction = 0.5f,
        viewportAnchorYFraction = 0.5f,
        scale = scale,
        generation = gen,
    )

    // ── 1. position + session success → both updated ──────────────────────────
    @Test
    fun positionAndSession_success_updatesBoth() = runBlocking {
        val docId = "doc-success"
        seedDocument(docId)

        repository.updatePdfProgressSnapshotAtomic(
            id = docId,
            pageIndex = 5,
            intraPageFractionEncoded = 42,
            pageCount = 10,
            readingProgress = 0.55f,
            session = makeSession(gen = 7, pageIndex = 5, scale = 3.0f),
        )

        val after = db.documentDao().getById(docId)
        assertNotNull(after)
        assertEquals(5, after!!.lastScrollIndex)
        assertEquals(42, after.lastScrollOffset)
        assertEquals(0.55f, after.readingProgress, 0.001f)
        assertEquals(10, after.pageCount)
        // session fields
        assertEquals(7L, after.pdfViewportGeneration)
        assertEquals(5, after.pdfViewportPageIndex)
        assertEquals(3.0f, after.pdfViewportScale!!, 0.001f)
        assertEquals(PdfReadingMode.VERTICAL_CONTINUOUS.toStorage(), after.pdfViewportReadingMode)
    }

    // ── 2. session DAO throws → position rolled back ──────────────────────────
    //
    // We simulate a session-DAO failure by using a wrapper DocumentDao that
    // throws on updatePdfViewportSession. The transaction must roll back the
    // position update that ran first.
    @Test
    fun sessionDaoThrows_positionRolledBack() = runBlocking {
        val docId = "doc-session-fail"
        seedDocument(docId)

        // First write a known baseline.
        repository.updatePdfProgressSnapshotAtomic(
            id = docId,
            pageIndex = 2,
            intraPageFractionEncoded = 10,
            pageCount = 10,
            readingProgress = 0.2f,
            session = makeSession(gen = 1, pageIndex = 2, scale = 1.0f),
        )

        // Now attempt an update with a failing session DAO.
        val failingRepo = DocumentRepository(
            context = context,
            db = db,
            documentDao = FailingViewportSessionDao(db.documentDao()),
            pdfPrintPageAnchorDao = db.pdfPrintPageAnchorDao(),
            textbookOutlineNodeDao = db.textbookOutlineNodeDao(),
            searchRepository = SearchRepository(db.searchIndexDao()),
        )

        try {
            failingRepo.updatePdfProgressSnapshotAtomic(
                id = docId,
                pageIndex = 8,
                intraPageFractionEncoded = 99,
                pageCount = 10,
                readingProgress = 0.8f,
                session = makeSession(gen = 2, pageIndex = 8, scale = 2.0f),
            )
            fail("expected transaction to throw")
        } catch (_: RuntimeException) {
            // expected
        }

        val after = db.documentDao().getById(docId)
        assertNotNull(after)
        assertEquals("position must be rolled back to baseline", 2, after!!.lastScrollIndex)
        assertEquals(10, after.lastScrollOffset)
        assertEquals(0.2f, after.readingProgress, 0.001f)
        assertEquals("session must be rolled back to baseline", 1L, after.pdfViewportGeneration)
        assertEquals(2, after.pdfViewportPageIndex)
        assertEquals(1.0f, after.pdfViewportScale!!, 0.001f)
    }

    // ── 3. position DAO throws → both stay at old values ──────────────────────
    @Test
    fun positionDaoThrows_bothStayAtOldValues() = runBlocking {
        val docId = "doc-position-fail"
        seedDocument(docId)

        repository.updatePdfProgressSnapshotAtomic(
            id = docId,
            pageIndex = 3,
            intraPageFractionEncoded = 15,
            pageCount = 10,
            readingProgress = 0.3f,
            session = makeSession(gen = 1, pageIndex = 3, scale = 1.5f),
        )

        val failingRepo = DocumentRepository(
            context = context,
            db = db,
            documentDao = FailingPositionDao(db.documentDao()),
            pdfPrintPageAnchorDao = db.pdfPrintPageAnchorDao(),
            textbookOutlineNodeDao = db.textbookOutlineNodeDao(),
            searchRepository = SearchRepository(db.searchIndexDao()),
        )

        try {
            failingRepo.updatePdfProgressSnapshotAtomic(
                id = docId,
                pageIndex = 9,
                intraPageFractionEncoded = 77,
                pageCount = 10,
                readingProgress = 0.9f,
                session = makeSession(gen = 2, pageIndex = 9, scale = 2.5f),
            )
            fail("expected transaction to throw")
        } catch (_: RuntimeException) {
            // expected
        }

        val after = db.documentDao().getById(docId)
        assertNotNull(after)
        assertEquals("position must be rolled back", 3, after!!.lastScrollIndex)
        assertEquals(15, after.lastScrollOffset)
        assertEquals(0.3f, after.readingProgress, 0.001f)
        assertEquals("session must be rolled back too", 1L, after.pdfViewportGeneration)
        assertEquals(3, after.pdfViewportPageIndex)
        assertEquals(1.5f, after.pdfViewportScale!!, 0.001f)
    }

    // ── 4. document does not exist → explicit behavior (no-op) ────────────────
    //
    // updatePdf*Position is a WHERE id = :id UPDATE. If the row does not exist,
    // the UPDATE affects 0 rows but does NOT throw. This is the explicit
    // behavior: the transaction completes successfully but no row is modified.
    // We assert the DB has no such document (not a silent fake success — the
    // caller can verify via getById if it needs to distinguish).
    @Test
    fun documentDoesNotExist_noopNoThrow() = runBlocking {
        val missingId = "doc-missing"

        // Should not throw.
        repository.updatePdfProgressSnapshotAtomic(
            id = missingId,
            pageIndex = 5,
            intraPageFractionEncoded = 42,
            pageCount = 10,
            readingProgress = 0.5f,
            session = makeSession(gen = 1, pageIndex = 5, scale = 2.0f),
        )

        // Document still absent — the UPDATE matched 0 rows.
        assertNull("document must not have been created", db.documentDao().getById(missingId))
    }

    // ── 5. consecutive gen1 then gen2 → DB holds gen2 ─────────────────────────
    @Test
    fun consecutiveGen1ThenGen2_dbHoldsGen2() = runBlocking {
        val docId = "doc-consecutive"
        seedDocument(docId)

        repository.updatePdfProgressSnapshotAtomic(
            id = docId,
            pageIndex = 2,
            intraPageFractionEncoded = 20,
            pageCount = 10,
            readingProgress = 0.2f,
            session = makeSession(gen = 1, pageIndex = 2, scale = 1.0f),
        )

        repository.updatePdfProgressSnapshotAtomic(
            id = docId,
            pageIndex = 7,
            intraPageFractionEncoded = 70,
            pageCount = 10,
            readingProgress = 0.7f,
            session = makeSession(gen = 2, pageIndex = 7, scale = 2.5f),
        )

        val after = db.documentDao().getById(docId)
        assertNotNull(after)
        assertEquals("position is gen2", 7, after!!.lastScrollIndex)
        assertEquals(70, after.lastScrollOffset)
        assertEquals(0.7f, after.readingProgress, 0.001f)
        assertEquals("session is gen2", 2L, after.pdfViewportGeneration)
        assertEquals(7, after.pdfViewportPageIndex)
        assertEquals(2.5f, after.pdfViewportScale!!, 0.001f)
    }

    // ── 6. session-only update (position=null) writes session atomically ──────
    //
    // P0A3: position may be null for zoom-settle where only the session changed.
    // The transaction must still succeed and not touch position fields.
    @Test
    fun sessionOnlyUpdate_positionNull_writesSessionOnly() = runBlocking {
        val docId = "doc-session-only"
        seedDocument(docId)

        // Baseline: write position + session.
        repository.updatePdfProgressSnapshotAtomic(
            id = docId,
            pageIndex = 4,
            intraPageFractionEncoded = 40,
            pageCount = 10,
            readingProgress = 0.4f,
            session = makeSession(gen = 1, pageIndex = 4, scale = 1.0f),
        )

        // Session-only update: position fields null.
        repository.updatePdfProgressSnapshotAtomic(
            id = docId,
            pageIndex = null,
            intraPageFractionEncoded = null,
            pageCount = null,
            readingProgress = null,
            session = makeSession(gen = 2, pageIndex = 4, scale = 3.0f),
        )

        val after = db.documentDao().getById(docId)
        assertNotNull(after)
        assertEquals("position must be unchanged", 4, after!!.lastScrollIndex)
        assertEquals(40, after.lastScrollOffset)
        assertEquals(0.4f, after.readingProgress, 0.001f)
        assertEquals("session must be gen2", 2L, after.pdfViewportGeneration)
        assertEquals(3.0f, after.pdfViewportScale!!, 0.001f)
    }

    // ── Failing DAO wrappers ──────────────────────────────────────────────────

    /**
     * Wraps [DocumentDao] and throws on [updatePdfViewportSession] to simulate
     * a session-DAO failure mid-transaction.
     */
    private class FailingViewportSessionDao(
        private val real: com.m1racle.reader.data.local.DocumentDao,
    ) : com.m1racle.reader.data.local.DocumentDao by real {
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
        ) {
            throw RuntimeException("simulated viewport session DAO failure")
        }
    }

    /**
     * Wraps [DocumentDao] and throws on [updatePdfReadingPosition] to simulate
     * a position-DAO failure mid-transaction.
     */
    private class FailingPositionDao(
        private val real: com.m1racle.reader.data.local.DocumentDao,
    ) : com.m1racle.reader.data.local.DocumentDao by real {
        override suspend fun updatePdfReadingPosition(
            id: String,
            pageIndex: Int,
            intraPageFractionEncoded: Int,
            pageCount: Int,
            readingProgress: Float,
        ) {
            throw RuntimeException("simulated reading position DAO failure")
        }
    }
}
