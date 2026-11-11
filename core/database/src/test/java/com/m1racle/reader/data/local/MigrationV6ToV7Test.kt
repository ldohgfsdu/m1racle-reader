package com.m1racle.reader.data.local

import java.nio.file.Files
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Executes the exact SQL used by [AppDatabase.MIGRATION_6_7] against a real
 * host SQLite database. The Android MigrationTestHelper chain remains under
 * androidTest; these tests make the destructive-data and NULL/index contracts
 * executable without requiring an adb target.
 */
class MigrationV6ToV7Test {

    @Test
    fun emptyDatabase_addsNullableContentHashAndUniqueNonPartialIndex() {
        withMigratedDatabase { db ->
            db.createStatement().use { statement ->
                statement.executeQuery("PRAGMA table_info(documents)").use { rows ->
                    var foundNullableHash = false
                    while (rows.next()) {
                        if (rows.getString("name") == "contentSha256") {
                            foundNullableHash = rows.getInt("notnull") == 0
                        }
                    }
                    assertTrue("contentSha256 exists and is nullable", foundNullableHash)
                }

                statement.executeQuery("PRAGMA index_list(documents)").use { rows ->
                    var foundUniqueIndex = false
                    while (rows.next()) {
                        if (rows.getString("name") == "index_documents_contentSha256") {
                            foundUniqueIndex = rows.getInt("unique") == 1
                            assertEquals("ordinary index, not partial", 0, rows.getInt("partial"))
                        }
                    }
                    assertTrue("contentSha256 unique index exists", foundUniqueIndex)
                }

                statement.executeQuery(
                    "PRAGMA index_info(index_documents_contentSha256)",
                ).use { rows ->
                    assertTrue(rows.next())
                    assertEquals("contentSha256", rows.getString("name"))
                    assertFalse(rows.next())
                }
            }
        }
    }

    @Test
    fun existingDocument_isPreservedWithNullContentHash() {
        withMigratedDatabase(
            seed = { db -> insertDocument(db, id = "legacy-doc", displayName = "legacy.md") },
        ) { db ->
            db.prepareStatement("SELECT * FROM documents WHERE id = ?").use { query ->
                query.setString(1, "legacy-doc")
                query.executeQuery().use { rows ->
                    assertTrue(rows.next())
                    assertEquals("legacy.md", rows.getString("displayName"))
                    assertEquals("content://fixture/legacy-doc", rows.getString("uri"))
                    assertEquals(19L, rows.getLong("fileSize"))
                    assertEquals(7, rows.getInt("lastScrollIndex"))
                    assertEquals(12, rows.getInt("lastScrollOffset"))
                    assertEquals(0.35f, rows.getFloat("readingProgress"), 0.0001f)
                    assertEquals("Legacy title", rows.getString("title"))
                    assertEquals(3, rows.getInt("headingCount"))
                    assertNull(rows.getString("contentSha256"))
                    assertFalse(rows.next())
                }
            }
        }
    }

    @Test
    fun multipleNullContentHashes_areAllowed() {
        withMigratedDatabase(
            seed = { db ->
                insertDocument(db, id = "legacy-a", displayName = "a.md")
                insertDocument(db, id = "legacy-b", displayName = "b.md")
            },
        ) { db ->
            insertDocument(db, id = "new-null", displayName = "new.md")
            db.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT COUNT(*) AS rowCount FROM documents WHERE contentSha256 IS NULL",
                ).use { rows ->
                    assertTrue(rows.next())
                    assertEquals(3, rows.getInt("rowCount"))
                }
            }
        }
    }

    @Test
    fun existingDocumentAndChildData_remainIntact() {
        withMigratedDatabase(
            seed = { db ->
                insertDocument(db, id = "pdf-doc", displayName = "book.pdf", pdf = true)
                db.createStatement().use { statement ->
                    statement.execute(
                        """
                        INSERT INTO pdf_print_page_anchors
                            (documentId, pdfPageIndex, printPageNumber, updatedAt)
                        VALUES ('pdf-doc', 4, 17, 4444)
                        """.trimIndent(),
                    )
                    statement.execute(
                        """
                        INSERT INTO textbook_outline_nodes
                            (id, documentId, parentId, type, title, startPageIndex,
                             endPageIndex, sortOrder, createdAt, updatedAt, source, importKey)
                        VALUES ('node-1', 'pdf-doc', NULL, 'CHAPTER', 'Chapter', 2,
                                8, 0, 1000, 2000, 'PDF_EMBEDDED', 'embedded-1')
                        """.trimIndent(),
                    )
                }
            },
        ) { db ->
            db.prepareStatement("SELECT * FROM documents WHERE id = 'pdf-doc'").use { query ->
                query.executeQuery().use { rows ->
                    assertTrue(rows.next())
                    assertEquals("PDF", rows.getString("documentType"))
                    assertEquals(20, rows.getInt("pageCount"))
                    assertEquals(2, rows.getInt("pdfViewportSessionVersion"))
                    assertEquals("VERTICAL_CONTINUOUS", rows.getString("pdfViewportReadingMode"))
                    assertEquals(7, rows.getInt("pdfViewportPageIndex"))
                    assertEquals(1.75f, rows.getFloat("pdfViewportScale"), 0.0001f)
                    assertEquals(99L, rows.getLong("pdfViewportGeneration"))
                    assertNull(rows.getString("contentSha256"))
                }
            }

            db.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT * FROM pdf_print_page_anchors WHERE documentId = 'pdf-doc'",
                ).use { rows ->
                    assertTrue(rows.next())
                    assertEquals(4, rows.getInt("pdfPageIndex"))
                    assertEquals(17, rows.getInt("printPageNumber"))
                    assertFalse(rows.next())
                }
                statement.executeQuery(
                    "SELECT * FROM textbook_outline_nodes WHERE documentId = 'pdf-doc'",
                ).use { rows ->
                    assertTrue(rows.next())
                    assertEquals("node-1", rows.getString("id"))
                    assertEquals("embedded-1", rows.getString("importKey"))
                    assertFalse(rows.next())
                }
            }
        }
    }

    @Test
    fun duplicateKnownContentHash_isRejectedByUniqueIndex() {
        withMigratedDatabase(
            seed = { db ->
                insertDocument(db, id = "known-a", displayName = "a.md")
                insertDocument(db, id = "known-b", displayName = "b.md")
            },
        ) { db ->
            val hash = "a".repeat(64)
            db.prepareStatement(
                "UPDATE documents SET contentSha256 = ? WHERE id = ?",
            ).use { update ->
                update.setString(1, hash)
                update.setString(2, "known-a")
                assertEquals(1, update.executeUpdate())

                update.setString(1, hash)
                update.setString(2, "known-b")
                val duplicate = runCatching { update.executeUpdate() }
                assertTrue(duplicate.exceptionOrNull() is SQLException)
            }

            db.prepareStatement(
                "SELECT id FROM documents WHERE contentSha256 = ?",
            ).use { query ->
                query.setString(1, hash)
                query.executeQuery().use { rows ->
                    assertTrue(rows.next())
                    assertEquals("known-a", rows.getString("id"))
                    assertFalse(rows.next())
                }
            }
        }
    }

    private fun withMigratedDatabase(
        seed: (Connection) -> Unit = {},
        verify: (Connection) -> Unit,
    ) {
        val dbFile = Files.createTempFile("m1racle-reader-d1-3-v6-v7-", ".db")
        try {
            DriverManager.getConnection("jdbc:sqlite:${dbFile.toAbsolutePath()}").use { db ->
                createV6Schema(db)
                seed(db)
                db.autoCommit = false
                try {
                    db.createStatement().use { statement ->
                        statement.execute(AppDatabase.MIGRATION_6_7_ADD_CONTENT_SHA_SQL)
                        statement.execute(AppDatabase.MIGRATION_6_7_CREATE_CONTENT_SHA_INDEX_SQL)
                        statement.execute("PRAGMA user_version = 7")
                    }
                    db.commit()
                } catch (error: Throwable) {
                    db.rollback()
                    throw error
                } finally {
                    db.autoCommit = true
                }
                verify(db)
            }
        } finally {
            Files.deleteIfExists(dbFile)
        }
    }

    private fun createV6Schema(db: Connection) {
        db.createStatement().use { statement ->
            statement.execute(
                """
                CREATE TABLE documents (
                    id TEXT NOT NULL,
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
                    documentType TEXT NOT NULL,
                    pageCount INTEGER NOT NULL,
                    pdfViewportSessionVersion INTEGER,
                    pdfViewportReadingMode TEXT,
                    pdfViewportPageIndex INTEGER,
                    pdfPageAnchorXFraction REAL,
                    pdfPageAnchorYFraction REAL,
                    pdfViewportAnchorXFraction REAL,
                    pdfViewportAnchorYFraction REAL,
                    pdfViewportScale REAL,
                    pdfViewportGeneration INTEGER,
                    PRIMARY KEY(id)
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE pdf_print_page_anchors (
                    documentId TEXT NOT NULL,
                    pdfPageIndex INTEGER NOT NULL,
                    printPageNumber INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    PRIMARY KEY(documentId, pdfPageIndex),
                    FOREIGN KEY(documentId) REFERENCES documents(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            statement.execute(
                "CREATE INDEX index_pdf_print_page_anchors_documentId " +
                    "ON pdf_print_page_anchors(documentId)",
            )
            statement.execute(
                """
                CREATE TABLE textbook_outline_nodes (
                    id TEXT NOT NULL,
                    documentId TEXT NOT NULL,
                    parentId TEXT,
                    type TEXT NOT NULL,
                    title TEXT NOT NULL,
                    startPageIndex INTEGER NOT NULL,
                    endPageIndex INTEGER NOT NULL,
                    sortOrder INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    source TEXT NOT NULL,
                    importKey TEXT,
                    PRIMARY KEY(id),
                    FOREIGN KEY(documentId) REFERENCES documents(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            statement.execute(
                "CREATE INDEX index_textbook_outline_nodes_documentId " +
                    "ON textbook_outline_nodes(documentId)",
            )
            statement.execute(
                "CREATE INDEX index_textbook_outline_nodes_documentId_parentId " +
                    "ON textbook_outline_nodes(documentId, parentId)",
            )
            statement.execute(
                "CREATE INDEX index_textbook_outline_nodes_documentId_startPageIndex_endPageIndex " +
                    "ON textbook_outline_nodes(documentId, startPageIndex, endPageIndex)",
            )
            statement.execute(
                "CREATE INDEX index_textbook_outline_nodes_documentId_importKey " +
                    "ON textbook_outline_nodes(documentId, importKey)",
            )
            statement.execute("PRAGMA user_version = 6")
        }
    }

    private fun insertDocument(
        db: Connection,
        id: String,
        displayName: String,
        pdf: Boolean = false,
    ) {
        db.prepareStatement(
            """
            INSERT INTO documents (
                id, displayName, uri, mimeType, fileSize, lastOpenedAt, createdAt,
                lastScrollIndex, lastScrollOffset, readingProgress, title, headingCount,
                documentType, pageCount, pdfViewportSessionVersion,
                pdfViewportReadingMode, pdfViewportPageIndex, pdfPageAnchorXFraction,
                pdfPageAnchorYFraction, pdfViewportAnchorXFraction,
                pdfViewportAnchorYFraction, pdfViewportScale, pdfViewportGeneration
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { insert ->
            val values = listOf(
                id,
                displayName,
                "content://fixture/$id",
                if (pdf) "application/pdf" else "text/markdown",
                19L,
                2000L,
                1000L,
                7,
                12,
                0.35f,
                "Legacy title",
                3,
                if (pdf) "PDF" else "MARKDOWN",
                if (pdf) 20 else 0,
                if (pdf) 2 else null,
                if (pdf) "VERTICAL_CONTINUOUS" else null,
                if (pdf) 7 else null,
                if (pdf) 0.25f else null,
                if (pdf) 0.5f else null,
                if (pdf) 0.5f else null,
                if (pdf) 0.5f else null,
                if (pdf) 1.75f else null,
                if (pdf) 99L else null,
            )
            values.forEachIndexed { index, value -> insert.setObject(index + 1, value) }
            assertEquals(1, insert.executeUpdate())
        }
    }
}
