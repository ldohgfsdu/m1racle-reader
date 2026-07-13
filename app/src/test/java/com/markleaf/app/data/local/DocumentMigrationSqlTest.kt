package com.markleaf.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lightweight migration contract tests (no Android device).
 * Instrumented Room migration can be added later under androidTest.
 */
class DocumentMigrationSqlTest {

    @Test
    fun migration_1_2_startAndEndVersions() {
        assertEquals(1, AppDatabase.MIGRATION_1_2.startVersion)
        assertEquals(2, AppDatabase.MIGRATION_1_2.endVersion)
    }

    @Test
    fun migration_2_3_startAndEndVersions() {
        assertEquals(2, AppDatabase.MIGRATION_2_3.startVersion)
        assertEquals(3, AppDatabase.MIGRATION_2_3.endVersion)
    }

    @Test
    fun migration_3_4_startAndEndVersions() {
        assertEquals(3, AppDatabase.MIGRATION_3_4.startVersion)
        assertEquals(4, AppDatabase.MIGRATION_3_4.endVersion)
    }

    @Test
    fun migration_4_5_startAndEndVersions() {
        assertEquals(4, AppDatabase.MIGRATION_4_5.startVersion)
        assertEquals(5, AppDatabase.MIGRATION_4_5.endVersion)
    }

    @Test
    fun migration_5_6_addsViewportSessionSchema() {
        assertEquals(5, AppDatabase.MIGRATION_5_6.startVersion)
        assertEquals(6, AppDatabase.MIGRATION_5_6.endVersion)
    }

    @Test
    fun entityDefaults_markdownCompatible() {
        val row = DocumentEntity(
            id = "id",
            displayName = "old.md",
            uri = "content://x",
            mimeType = "text/markdown",
            fileSize = 10L,
            lastOpenedAt = 1L,
            createdAt = 1L,
            lastScrollIndex = 3,
            lastScrollOffset = 40,
            readingProgress = 0.25f,
            title = "H1",
            headingCount = 2,
        )
        assertEquals(DocumentType.MARKDOWN.name, row.documentType)
        assertEquals(0, row.pageCount)
        assertEquals(3, row.lastScrollIndex)
        assertEquals(0.25f, row.readingProgress, 0.001f)
        assertTrue(!row.isPdf)
    }

    @Test
    fun entity_pdfFields() {
        val row = DocumentEntity(
            id = "pdf",
            displayName = "book.pdf",
            uri = "content://pdf",
            mimeType = DocumentType.MIME_PDF,
            fileSize = 100L,
            lastOpenedAt = 1L,
            createdAt = 1L,
            lastScrollIndex = 4,
            lastScrollOffset = 0,
            readingProgress = 0.4f,
            title = null,
            headingCount = 0,
            documentType = DocumentType.PDF.name,
            pageCount = 10,
        )
        assertTrue(row.isPdf)
        assertEquals(4, row.lastScrollIndex)
        assertEquals(10, row.pageCount)
    }
}
