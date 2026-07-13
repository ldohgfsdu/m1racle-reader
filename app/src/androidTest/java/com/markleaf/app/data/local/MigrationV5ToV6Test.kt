package com.markleaf.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.markleaf.app.data.repository.DocumentRepository
import com.markleaf.app.pdf.PdfReadingMode
import com.markleaf.app.pdf.PdfViewportSessionState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P0A lifecycle hardening: Room v5 → v6 migration test.
 *
 * Spec section 五 minimum requirements:
 * 1. Export v6 schema (done via exportSchema=true + KSP schemaLocation).
 * 2. Build a real v5 database fixture (manual SQL — no v5 schema JSON exists).
 * 3. Insert: document, last reading position, outline, print anchor.
 * 4. Execute MIGRATION_5_6.
 * 5. Open with current AppDatabase and verify schema.
 * 6. Assert old data fully preserved.
 * 7. New zoom session columns are null.
 * 8. Write zoom session and successfully read.
 * 9. No destructive migration.
 *
 * Historical 1→5 schema backfill is a separate task; not faked here.
 *
 * Instrumented test — requires a device. Marked NOT RUN if no device.
 */
@RunWith(AndroidJUnit4::class)
class MigrationV5ToV6Test {

    private lateinit var context: Context
    private val dbName = "migration_v5_v6_test.db"
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Delete any leftover database file from prior runs.
        context.deleteDatabase(dbName)
    }

    @After
    fun teardown() {
        context.deleteDatabase(dbName)
    }

    /**
     * Create a real v5 database file with the v5 schema (no zoom session
     * columns on documents). This mirrors the schema after migrations
     * 1→2→3→4→5 have been applied, but is created directly via SQL since
     * we do not have historical schema JSONs.
     */
    private fun createV5Database(): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration(
            context = context,
            name = dbName,
            callback = object : SupportSQLiteOpenHelper.Callback(5) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // v5 documents table (no zoom session columns).
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS documents (
                            id TEXT NOT NULL PRIMARY KEY,
                            displayName TEXT NOT NULL,
                            uri TEXT NOT NULL,
                            mimeType TEXT,
                            fileSize INTEGER,
                            lastOpenedAt INTEGER NOT NULL,
                            createdAt INTEGER NOT NULL,
                            lastScrollIndex INTEGER NOT NULL,
                            lastScrollOffset INTEGER NOT NULL,
                            readingProgress REAL NOT NULL,
                            title TEXT,
                            headingCount INTEGER NOT NULL,
                            documentType TEXT NOT NULL DEFAULT 'MARKDOWN',
                            pageCount INTEGER NOT NULL DEFAULT 0
                        )
                        """.trimIndent()
                    )

                    // v5 pdf_print_page_anchors (created in v2→v3).
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS pdf_print_page_anchors (
                            documentId TEXT NOT NULL,
                            pdfPageIndex INTEGER NOT NULL,
                            printPageNumber INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL,
                            PRIMARY KEY(documentId, pdfPageIndex),
                            FOREIGN KEY(documentId) REFERENCES documents(id) ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_pdf_print_page_anchors_documentId " +
                            "ON pdf_print_page_anchors(documentId)"
                    )

                    // v5 textbook_outline_nodes (created v3→v4, source/importKey added v4→v5).
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS textbook_outline_nodes (
                            id TEXT NOT NULL PRIMARY KEY,
                            documentId TEXT NOT NULL,
                            parentId TEXT,
                            type TEXT NOT NULL,
                            title TEXT NOT NULL,
                            startPageIndex INTEGER NOT NULL,
                            endPageIndex INTEGER NOT NULL,
                            sortOrder INTEGER NOT NULL,
                            createdAt INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL,
                            source TEXT NOT NULL DEFAULT 'MANUAL',
                            importKey TEXT,
                            FOREIGN KEY(documentId) REFERENCES documents(id) ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_textbook_outline_nodes_documentId " +
                            "ON textbook_outline_nodes(documentId)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS " +
                            "index_textbook_outline_nodes_documentId_parentId " +
                            "ON textbook_outline_nodes(documentId, parentId)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS " +
                            "index_textbook_outline_nodes_documentId_startPageIndex_endPageIndex " +
                            "ON textbook_outline_nodes(documentId, startPageIndex, endPageIndex)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS " +
                            "index_textbook_outline_nodes_documentId_importKey " +
                            "ON textbook_outline_nodes(documentId, importKey)"
                    )
                }

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ) {
                    // No-op — we create at v5 directly.
                }
            },
        )
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase
        // Set the database version to 5 so Room detects v5 and runs MIGRATION_5_6.
        db.execSQL("PRAGMA user_version = 5")
        return db
    }

    private fun seedV5Data(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        // Document with reading position (lastScrollIndex = page index).
        db.execSQL(
            """
            INSERT INTO documents (id, displayName, uri, mimeType, fileSize,
                lastOpenedAt, createdAt, lastScrollIndex, lastScrollOffset,
                readingProgress, title, headingCount, documentType, pageCount)
            VALUES ('doc-migrate', 'Test PDF', 'file:///test.pdf', 'application/pdf', 2048,
                $now, $now, 5, 0, 0.5, 'Test', 2, 'PDF', 10)
            """.trimIndent()
        )
        // Outline node.
        db.execSQL(
            """
            INSERT INTO textbook_outline_nodes (id, documentId, parentId, type, title,
                startPageIndex, endPageIndex, sortOrder, createdAt, updatedAt, source, importKey)
            VALUES ('node-migrate', 'doc-migrate', NULL, 'CHAPTER', 'Chapter 1',
                0, 4, 0, $now, $now, 'PDF_EMBEDDED', 'import-key-1')
            """.trimIndent()
        )
        // Print page anchor.
        db.execSQL(
            """
            INSERT INTO pdf_print_page_anchors (documentId, pdfPageIndex, printPageNumber, updatedAt)
            VALUES ('doc-migrate', 0, 1, $now)
            """.trimIndent()
        )
    }

    /**
     * Full migration: v5 fixture → MIGRATION_5_6 → validate v6 schema → verify data.
     */
    @Test
    fun migrate5To6_preservesData_newColumnsNull() {
        // 1. Create v5 database and seed data.
        val v5Db = createV5Database()
        seedV5Data(v5Db)
        v5Db.close()

        // 2. Run MIGRATION_5_6 and validate against v6 schema.
        val migrationHelper = MigrationTestHelper(
            instrumentation,
            AppDatabase::class.java,
            emptyList(),
        )
        val migratedDb = migrationHelper.runMigrationsAndValidate(
            dbName, 6, true, AppDatabase.MIGRATION_5_6
        )

        // 3. Open with current AppDatabase (Room) to verify schema compatibility.
        val roomDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        val docDao = roomDb.documentDao()
        val outlineDao = roomDb.textbookOutlineNodeDao()
        val anchorDao = roomDb.pdfPrintPageAnchorDao()

        // 4. Assert old data is fully preserved.
        runBlocking {
            val doc = docDao.getById("doc-migrate")
            assertNotNull("document preserved after migration", doc)
            assertEquals("Test PDF", doc!!.displayName)
            assertEquals("file:///test.pdf", doc.uri)
            assertEquals("application/pdf", doc.mimeType)
            assertEquals(2048L, doc.fileSize)
            assertEquals(5, doc.lastScrollIndex) // reading position page
            assertEquals(0.5f, doc.readingProgress, 0.001f)
            assertEquals("Test", doc.title)
            assertEquals(2, doc.headingCount)
            assertEquals("PDF", doc.documentType)
            assertEquals(10, doc.pageCount)

            // 5. New zoom session columns must be null after migration.
            assertNull("pdfViewportSessionVersion is null", doc.pdfViewportSessionVersion)
            assertNull("pdfViewportReadingMode is null", doc.pdfViewportReadingMode)
            assertNull("pdfViewportPageIndex is null", doc.pdfViewportPageIndex)
            assertNull("pdfPageAnchorXFraction is null", doc.pdfPageAnchorXFraction)
            assertNull("pdfPageAnchorYFraction is null", doc.pdfPageAnchorYFraction)
            assertNull("pdfViewportAnchorXFraction is null", doc.pdfViewportAnchorXFraction)
            assertNull("pdfViewportAnchorYFraction is null", doc.pdfViewportAnchorYFraction)
            assertNull("pdfViewportScale is null", doc.pdfViewportScale)
            assertNull("pdfViewportGeneration is null", doc.pdfViewportGeneration)

            // Outline preserved.
            val outlines = outlineDao.listForDocument("doc-migrate")
            assertEquals("outline preserved", 1, outlines.size)
            assertEquals("node-migrate", outlines[0].id)
            assertEquals("Chapter 1", outlines[0].title)
            assertEquals("PDF_EMBEDDED", outlines[0].source)
            assertEquals("import-key-1", outlines[0].importKey)

            // Anchor preserved.
            val anchors = anchorDao.listForDocument("doc-migrate")
            assertEquals("anchor preserved", 1, anchors.size)
            assertEquals(0, anchors[0].pdfPageIndex)
            assertEquals(1, anchors[0].printPageNumber)

            // 6. Write zoom session and successfully read back.
            val session = PdfViewportSessionState(
                version = 1,
                readingMode = PdfReadingMode.VERTICAL_CONTINUOUS,
                pageIndex = 5,
                pageAnchorXFraction = 0f,
                pageAnchorYFraction = 0.2f,
                viewportAnchorXFraction = 0.5f,
                viewportAnchorYFraction = 0.5f,
                scale = 2.0f,
                generation = 1L,
            )
            val repo = DocumentRepository(
                context = context,
                db = roomDb,
                documentDao = docDao,
                pdfPrintPageAnchorDao = anchorDao,
                textbookOutlineNodeDao = outlineDao,
            )
            repo.updatePdfViewportSession("doc-migrate", session)

            val updated = docDao.getById("doc-migrate")
            assertNotNull("document still exists after viewport write", updated)
            assertEquals(1, updated!!.pdfViewportSessionVersion)
            assertEquals(
                PdfReadingMode.VERTICAL_CONTINUOUS.toStorage(),
                updated.pdfViewportReadingMode,
            )
            assertEquals(5, updated.pdfViewportPageIndex)
            assertEquals(0f, updated.pdfPageAnchorXFraction!!, 0.001f)
            assertEquals(0.2f, updated.pdfPageAnchorYFraction!!, 0.001f)
            assertEquals(0.5f, updated.pdfViewportAnchorXFraction!!, 0.001f)
            assertEquals(0.5f, updated.pdfViewportAnchorYFraction!!, 0.001f)
            assertEquals(2.0f, updated.pdfViewportScale!!, 0.001f)
            assertEquals(1L, updated.pdfViewportGeneration)
        }

        migratedDb.close()
        roomDb.close()
    }
}
