package com.m1racle.reader.data.local

import java.nio.file.Files
import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R23：v9 → v10 标注表迁移契约（宿主真实 SQLite，机制同前序迁移测试）。
 * highlights / notes 增量建表、索引名冻结、FK 拒孤儿 + CASCADE。
 */
class MigrationV9ToV10Test {

    @Test
    fun `migration creates annotation tables with frozen columns and indexes`() {
        withMigratedDatabase { db ->
            assertEquals(10, db.userVersion())
            assertTrue(db.tableNames().contains("highlights"))
            assertTrue(db.tableNames().contains("notes"))

            assertEquals(
                listOf(
                    "id:INTEGER:1:1",
                    "documentId:TEXT:1:0",
                    "locationKind:TEXT:1:0",
                    "location:INTEGER:1:0",
                    "anchorStart:INTEGER:0:0",
                    "anchorEnd:INTEGER:0:0",
                    "excerpt:TEXT:0:0",
                    "createdAt:INTEGER:1:0",
                ),
                db.columns("highlights"),
            )
            assertEquals(
                listOf(
                    "id:INTEGER:1:1",
                    "documentId:TEXT:1:0",
                    "locationKind:TEXT:1:0",
                    "location:INTEGER:1:0",
                    "anchorStart:INTEGER:0:0",
                    "anchorEnd:INTEGER:0:0",
                    "content:TEXT:1:0",
                    "createdAt:INTEGER:1:0",
                    "updatedAt:INTEGER:1:0",
                ),
                db.columns("notes"),
            )
            val indices = db.indexNames()
            assertTrue(indices.contains("index_highlights_documentId"))
            assertTrue(indices.contains("index_highlights_documentId_locationKind_location"))
            assertTrue(indices.contains("index_notes_documentId"))
            assertTrue(indices.contains("index_notes_documentId_locationKind_location"))
        }
    }

    @Test
    fun `annotations reject orphans and cascade with document`() {
        withMigratedDatabase(seed = { db ->
            db.createStatement().use { it.execute("INSERT INTO documents(id) VALUES ('doc-1')") }
        }) { db ->
            db.createStatement().use { s ->
                s.execute("PRAGMA foreign_keys = ON")
                s.execute(
                    """
                    INSERT INTO highlights(documentId, locationKind, location, createdAt)
                    VALUES ('doc-1', 'PAGE_INDEX', 2, 10)
                    """.trimIndent(),
                )
                s.execute(
                    """
                    INSERT INTO notes(documentId, locationKind, location, content, createdAt, updatedAt)
                    VALUES ('doc-1', 'SCROLL_POSITION', 40, '批注', 20, 20)
                    """.trimIndent(),
                )
            }
            var rejected = false
            try {
                db.createStatement().use { s ->
                    s.execute(
                        """
                        INSERT INTO notes(documentId, locationKind, location, content, createdAt, updatedAt)
                        VALUES ('ghost', 'PAGE_INDEX', 1, 'x', 1, 1)
                        """.trimIndent(),
                    )
                }
            } catch (_: Exception) {
                rejected = true
            }
            assertTrue("orphan annotation must fail", rejected)

            db.createStatement().use { it.execute("DELETE FROM documents WHERE id = 'doc-1'") }
            assertEquals(0, db.countOf("highlights"))
            assertEquals(0, db.countOf("notes"))
        }
    }

    @Test
    fun `exact page queries use composite indexes at 1000 rows`() {
        withMigratedDatabase(seed = { db ->
            db.createStatement().use { it.execute("INSERT INTO documents(id) VALUES ('doc-1')") }
        }) { db ->
            db.autoCommit = false
            try {
                db.prepareStatement(
                    """
                    INSERT INTO highlights(documentId, locationKind, location, createdAt)
                    VALUES ('doc-1', 'PAGE_INDEX', ?, ?)
                    """.trimIndent(),
                ).use { insert ->
                    repeat(1_000) { index ->
                        insert.setInt(1, index % 10)
                        insert.setLong(2, index.toLong())
                        insert.addBatch()
                    }
                    insert.executeBatch()
                }
                db.prepareStatement(
                    """
                    INSERT INTO notes(documentId, locationKind, location, content, createdAt, updatedAt)
                    VALUES ('doc-1', 'PAGE_INDEX', ?, 'n', ?, ?)
                    """.trimIndent(),
                ).use { insert ->
                    repeat(1_000) { index ->
                        insert.setInt(1, index % 10)
                        insert.setLong(2, index.toLong())
                        insert.setLong(3, index.toLong())
                        insert.addBatch()
                    }
                    insert.executeBatch()
                }
                db.commit()
            } finally {
                db.autoCommit = true
            }

            val highlightPlan = db.queryPlan(
                """
                SELECT * FROM highlights
                WHERE documentId = 'doc-1' AND locationKind = 'PAGE_INDEX' AND location = 7
                ORDER BY createdAt ASC, id ASC
                """.trimIndent(),
            )
            val notePlan = db.queryPlan(
                """
                SELECT * FROM notes
                WHERE documentId = 'doc-1' AND locationKind = 'PAGE_INDEX' AND location = 7
                ORDER BY createdAt ASC, id ASC
                """.trimIndent(),
            )
            assertTrue(highlightPlan.any { it.contains("index_highlights_documentId_locationKind_location") })
            assertTrue(notePlan.any { it.contains("index_notes_documentId_locationKind_location") })
        }
    }

    // ── harness ────────────────────────────────────────────────────────────

    private fun withMigratedDatabase(
        seed: (Connection) -> Unit = {},
        verify: (Connection) -> Unit,
    ) {
        val dbFile = Files.createTempFile("m1racle-reader-r23-v9-v10-", ".db")
        try {
            DriverManager.getConnection("jdbc:sqlite:${dbFile.toAbsolutePath()}").use { db ->
                createMinimalV9Schema(db)
                seed(db)
                db.autoCommit = false
                try {
                    db.createStatement().use { statement ->
                        statement.execute(AppDatabase.MIGRATION_9_10_CREATE_HIGHLIGHTS_SQL)
                        statement.execute(AppDatabase.MIGRATION_9_10_HIGHLIGHTS_DOC_INDEX_SQL)
                        statement.execute(AppDatabase.MIGRATION_9_10_HIGHLIGHTS_LOC_INDEX_SQL)
                        statement.execute(AppDatabase.MIGRATION_9_10_CREATE_NOTES_SQL)
                        statement.execute(AppDatabase.MIGRATION_9_10_NOTES_DOC_INDEX_SQL)
                        statement.execute(AppDatabase.MIGRATION_9_10_NOTES_LOC_INDEX_SQL)
                        statement.execute("PRAGMA user_version = 10")
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

    /** v9 最小父表（迁移为纯增量建表）。 */
    private fun createMinimalV9Schema(db: Connection) {
        db.createStatement().use { s ->
            s.execute("CREATE TABLE documents (id TEXT NOT NULL PRIMARY KEY, displayName TEXT)")
            s.execute("PRAGMA user_version = 9")
        }
    }

    private fun Connection.userVersion(): Int = createStatement().use {
        it.executeQuery("PRAGMA user_version").apply { next() }.getInt(1)
    }

    private fun Connection.tableNames(): Set<String> = createStatement().use {
        it.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table'")
            .let { rs -> buildSet { while (rs.next()) add(rs.getString(1)) } }
    }

    private fun Connection.columns(table: String): List<String> = createStatement().use {
        it.executeQuery("PRAGMA table_info($table)").let { rs ->
            buildList {
                while (rs.next()) {
                    add(
                        "${rs.getString("name")}:${rs.getString("type")}:" +
                            "${rs.getInt("notnull")}:${rs.getInt("pk")}",
                    )
                }
            }
        }
    }

    private fun Connection.indexNames(): Set<String> = createStatement().use {
        it.executeQuery("SELECT name FROM sqlite_master WHERE type = 'index' AND name IS NOT NULL")
            .let { rs -> buildSet { while (rs.next()) add(rs.getString(1)) } }
    }

    private fun Connection.countOf(table: String): Int = createStatement().use {
        it.executeQuery("SELECT COUNT(*) FROM $table").apply { next() }.getInt(1)
    }

    private fun Connection.queryPlan(sql: String): List<String> = createStatement().use {
        it.executeQuery("EXPLAIN QUERY PLAN $sql").let { rs ->
            buildList { while (rs.next()) add(rs.getString("detail")) }
        }
    }
}
