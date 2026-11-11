package com.m1racle.reader.data.local

import java.nio.file.Files
import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Executes the v12 -> v13 production SQL against host SQLite. */
class MigrationV12ToV13Test {
    private val pathAId = "a".repeat(64)
    private val pathBSha = "b".repeat(64)
    private val pathBId = "c:$pathBSha"

    @Test
    fun `migration preserves documents and child rows while adding aliases`() {
        withDatabase { db ->
            createV12Schema(db)
            db.createStatement().use { statement ->
                statement.execute(documentInsert(pathAId, null, 10, 20))
                statement.execute(documentInsert(pathBId, null, 30, 40))
                statement.execute("INSERT INTO bookmarks VALUES (1, '$pathAId', 'PAGE_INDEX', 7, 'keep')")
                statement.execute("INSERT INTO highlights VALUES (1, '$pathBId', 'PAGE_INDEX', 8, 'keep')")
                statement.execute("INSERT INTO notes VALUES (1, '$pathAId', 'SCROLL_POSITION', 9, 'keep')")
            }

            migrate(db)

            assertEquals(13, db.scalarInt("PRAGMA user_version"))
            assertEquals(setOf(pathAId, pathBId), db.strings("SELECT id FROM documents"))
            assertNull(db.nullableString("SELECT contentSha256 FROM documents WHERE id='$pathAId'"))
            assertEquals(pathBSha, db.nullableString("SELECT contentSha256 FROM documents WHERE id='$pathBId'"))
            assertEquals("legacy-raw-uri-v1:$pathAId", db.nullableString("SELECT aliasKey FROM document_aliases"))
            assertEquals(listOf(pathAId), db.stringList("SELECT documentId FROM bookmarks"))
            assertEquals(listOf(pathBId), db.stringList("SELECT documentId FROM highlights"))
            assertEquals(listOf(pathAId), db.stringList("SELECT documentId FROM notes"))
            assertTrue(db.indexNames().contains("index_document_aliases_documentId"))
        }
    }

    @Test
    fun `collision abort rolls back the entire schema migration`() {
        withDatabase { db ->
            createV12Schema(db)
            db.createStatement().use { statement ->
                statement.execute(documentInsert(pathBId, null, 1, 1))
                statement.execute(documentInsert("existing-owner", pathBSha, 1, 1))
            }

            var failed = false
            try {
                migrate(db)
            } catch (_: IllegalStateException) {
                failed = true
            }

            assertTrue(failed)
            assertEquals(12, db.scalarInt("PRAGMA user_version"))
            assertEquals(0, db.scalarInt("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='document_aliases'"))
            assertNull(db.nullableString("SELECT contentSha256 FROM documents WHERE id='$pathBId'"))
            assertEquals(setOf(pathBId, "existing-owner"), db.strings("SELECT id FROM documents"))
        }
    }

    private fun migrate(db: Connection) {
        db.autoCommit = false
        try {
            db.createStatement().use { statement ->
                statement.execute(AppDatabase.MIGRATION_12_13_CREATE_ALIASES_SQL)
                statement.execute(AppDatabase.MIGRATION_12_13_INDEX_ALIASES_DOC_SQL)
                val collisions = statement.executeQuery(AppDatabase.MIGRATION_12_13_COLLISION_COUNT_SQL).use {
                    it.next()
                    it.getLong(1)
                }
                check(collisions == 0L) { "contentSha256 backfill collision" }
                statement.execute(AppDatabase.MIGRATION_12_13_BACKFILL_CONTENT_SHA_SQL)
                statement.executeQuery(AppDatabase.MIGRATION_12_13_BACKFILL_VALIDATE_SQL).use { rows ->
                    while (rows.next()) {
                        val id = rows.getString(1)
                        val hash = rows.getString(2)
                        check(id.startsWith("c:") && hash == id.substring(2))
                    }
                }
                statement.execute(AppDatabase.MIGRATION_12_13_BACKFILL_LEGACY_URI_ALIASES_SQL)
                statement.execute("PRAGMA user_version = 13")
            }
            db.commit()
        } catch (error: Throwable) {
            db.rollback()
            throw error
        } finally {
            db.autoCommit = true
        }
    }

    private fun createV12Schema(db: Connection) {
        db.createStatement().use { statement ->
            statement.execute("PRAGMA foreign_keys = ON")
            statement.execute(
                """
                CREATE TABLE documents (
                    id TEXT NOT NULL PRIMARY KEY,
                    contentSha256 TEXT,
                    createdAt INTEGER NOT NULL,
                    lastOpenedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            statement.execute("CREATE UNIQUE INDEX index_documents_contentSha256 ON documents(contentSha256)")
            statement.execute(childTableSql("bookmarks"))
            statement.execute(childTableSql("highlights"))
            statement.execute(childTableSql("notes"))
            statement.execute("PRAGMA user_version = 12")
        }
    }

    private fun childTableSql(name: String): String =
        """
        CREATE TABLE $name (
            id INTEGER NOT NULL PRIMARY KEY,
            documentId TEXT NOT NULL,
            locationKind TEXT NOT NULL,
            location INTEGER NOT NULL,
            payload TEXT NOT NULL,
            FOREIGN KEY(documentId) REFERENCES documents(id) ON DELETE CASCADE
        )
        """.trimIndent()

    private fun documentInsert(id: String, hash: String?, created: Long, opened: Long): String =
        "INSERT INTO documents VALUES ('$id', ${hash?.let { "'$it'" } ?: "NULL"}, $created, $opened)"

    private fun withDatabase(block: (Connection) -> Unit) {
        val file = Files.createTempFile("m1racle-reader-r55-v12-v13-", ".db")
        try {
            DriverManager.getConnection("jdbc:sqlite:${file.toAbsolutePath()}").use(block)
        } finally {
            Files.deleteIfExists(file)
        }
    }

    private fun Connection.scalarInt(sql: String): Int = createStatement().use {
        it.executeQuery(sql).apply { next() }.getInt(1)
    }

    private fun Connection.nullableString(sql: String): String? = createStatement().use {
        it.executeQuery(sql).apply { next() }.getString(1)
    }

    private fun Connection.strings(sql: String): Set<String> = stringList(sql).toSet()

    private fun Connection.stringList(sql: String): List<String> = createStatement().use {
        it.executeQuery(sql).let { rows -> buildList { while (rows.next()) add(rows.getString(1)) } }
    }

    private fun Connection.indexNames(): Set<String> = strings(
        "SELECT name FROM sqlite_master WHERE type='index' AND name IS NOT NULL",
    )
}
