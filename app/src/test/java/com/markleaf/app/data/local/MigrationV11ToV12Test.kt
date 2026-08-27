package com.markleaf.app.data.local

import java.nio.file.Files
import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R29 v11 -> v12 search-index schema, identity and lifecycle contract. */
class MigrationV11ToV12Test {

    @Test
    fun `migration creates scoped search index and cascades with document`() {
        val dbFile = Files.createTempFile("markleaf-r29-v11-v12-", ".db")
        try {
            DriverManager.getConnection("jdbc:sqlite:${dbFile.toAbsolutePath()}").use { db ->
                db.createStatement().use { statement ->
                    statement.execute("PRAGMA foreign_keys = ON")
                    statement.execute("CREATE TABLE documents (id TEXT NOT NULL PRIMARY KEY)")
                    statement.execute("INSERT INTO documents(id) VALUES ('doc-A'), ('doc-B')")
                    statement.execute("PRAGMA user_version = 11")
                    statement.execute(AppDatabase.MIGRATION_11_12_CREATE_SEARCH_INDEX_SQL)
                    statement.execute(AppDatabase.MIGRATION_11_12_SEARCH_DOCUMENT_INDEX_SQL)
                    statement.execute(AppDatabase.MIGRATION_11_12_SEARCH_TYPE_INDEX_SQL)
                    statement.execute("PRAGMA user_version = 12")
                }

                assertEquals(12, db.userVersion())
                assertEquals(
                    listOf(
                        "documentId:TEXT:1:1",
                        "contentType:TEXT:1:2",
                        "sourceId:TEXT:1:3",
                        "content:TEXT:1:0",
                        "locationKind:TEXT:1:0",
                        "location:INTEGER:1:0",
                        "anchor:TEXT:1:0",
                        "indexedAt:INTEGER:1:0",
                    ),
                    db.columns("search_index"),
                )
                assertTrue(db.indexNames().contains("index_search_index_documentId"))
                assertTrue(db.indexNames().contains("index_search_index_contentType"))

                db.createStatement().use { statement ->
                    statement.execute(searchInsert("doc-A", "NOTE", "1", "A"))
                    statement.execute(searchInsert("doc-B", "NOTE", "1", "B"))
                }
                assertEquals(2, db.countOf("search_index"))

                var orphanRejected = false
                try {
                    db.createStatement().use {
                        it.execute(searchInsert("ghost", "NOTE", "1", "orphan"))
                    }
                } catch (_: Exception) {
                    orphanRejected = true
                }
                assertTrue("orphan search rows must be rejected", orphanRejected)

                db.createStatement().use { it.execute("DELETE FROM documents WHERE id = 'doc-A'") }
                assertEquals(1, db.countOf("search_index"))
                assertFalse(db.documentIds().contains("doc-A"))
                assertEquals(setOf("doc-B"), db.searchDocumentIds())
            }
        } finally {
            Files.deleteIfExists(dbFile)
        }
    }

    private fun searchInsert(documentId: String, type: String, sourceId: String, content: String) =
        """
        INSERT INTO search_index(
            documentId, contentType, sourceId, content,
            locationKind, location, anchor, indexedAt
        ) VALUES (
            '$documentId', '$type', '$sourceId', '$content',
            'PAGE_INDEX', 0, 'pdf|page=0|rect=', 1
        )
        """.trimIndent()

    private fun Connection.userVersion(): Int = createStatement().use {
        it.executeQuery("PRAGMA user_version").apply { next() }.getInt(1)
    }

    private fun Connection.columns(table: String): List<String> = createStatement().use {
        it.executeQuery("PRAGMA table_info($table)").let { result ->
            buildList {
                while (result.next()) {
                    add(
                        "${result.getString("name")}:${result.getString("type")}:" +
                            "${result.getInt("notnull")}:${result.getInt("pk")}",
                    )
                }
            }
        }
    }

    private fun Connection.indexNames(): Set<String> = createStatement().use {
        it.executeQuery("SELECT name FROM sqlite_master WHERE type = 'index' AND name IS NOT NULL")
            .let { result -> buildSet { while (result.next()) add(result.getString(1)) } }
    }

    private fun Connection.countOf(table: String): Int = createStatement().use {
        it.executeQuery("SELECT COUNT(*) FROM $table").apply { next() }.getInt(1)
    }

    private fun Connection.documentIds(): Set<String> = createStatement().use {
        it.executeQuery("SELECT id FROM documents").let { result ->
            buildSet { while (result.next()) add(result.getString(1)) }
        }
    }

    private fun Connection.searchDocumentIds(): Set<String> = createStatement().use {
        it.executeQuery("SELECT documentId FROM search_index").let { result ->
            buildSet { while (result.next()) add(result.getString(1)) }
        }
    }
}
