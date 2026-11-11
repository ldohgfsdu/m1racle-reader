package com.m1racle.reader.data.local

import java.sql.Connection
import java.sql.DriverManager
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H2.1：v7 → v8 迁移 SQL 契约（宿主真实 SQLite，机制同 MigrationV6ToV7Test）。
 *
 * 逐条执行 [AppDatabase] 中 MIGRATION_7_8 的生产 SQL 常量（非测试副本 DDL），
 * 验证 user_version、三张新表的列结构、索引名与 FK CASCADE 行为。
 * Android 端 MigrationTestHelper 全链路验证仍归 androidTest（设备）。
 */
class MigrationV7ToV8Test {

    @Test
    fun `migration bumps user version to 8 and creates hub tables`() {
        withMigratedDatabase { db ->
            assertEquals(8, db.userVersion())
            val tables = db.tableNames()
            assertTrue(tables.contains("reading_states"))
            assertTrue(tables.contains("bookmarks"))
            assertTrue(tables.contains("favorite_documents"))

            // 列结构冻结（name:type:notnull:pk）。
            assertEquals(
                listOf(
                    "documentId:TEXT:1:1",
                    "lastPosition:INTEGER:1:0",
                    "progress:REAL:1:0",
                    "lastOpenedTime:INTEGER:1:0",
                ),
                db.columns("reading_states"),
            )
            assertEquals(
                listOf(
                    "id:INTEGER:1:1",
                    "documentId:TEXT:1:0",
                    "page:INTEGER:1:0",
                    "offset:INTEGER:0:0",
                    "title:TEXT:0:0",
                    "note:TEXT:0:0",
                    "createdAt:INTEGER:1:0",
                ),
                db.columns("bookmarks"),
            )
            assertEquals(
                listOf(
                    "documentId:TEXT:1:1",
                    "createdAt:INTEGER:1:0",
                ),
                db.columns("favorite_documents"),
            )

            // 索引名与实体声明一致（Room 运行时校验依赖这些名字）。
            val indices = db.indexNames()
            assertTrue(indices.contains("index_reading_states_lastOpenedTime"))
            assertTrue(indices.contains("index_reading_states_documentId"))
            assertTrue(indices.contains("index_bookmarks_documentId"))
            assertTrue(indices.contains("index_favorite_documents_createdAt"))
        }
    }

    @Test
    fun `hub rows cascade on document delete and reject orphans when enforced`() {
        withMigratedDatabase(seed = { db ->
            db.createStatement().use { it.execute("INSERT INTO documents(id) VALUES ('doc-1')") }
        }) { db ->
            db.createStatement().use { s ->
                s.execute(
                    "INSERT INTO reading_states VALUES ('doc-1', 12, 0.5, 100)",
                )
                s.execute(
                    "INSERT INTO bookmarks(documentId, page, createdAt) VALUES ('doc-1', 3, 10)",
                )
                s.execute(
                    "INSERT INTO favorite_documents VALUES ('doc-1', 20)",
                )
            }
            // FK 强制开启时拒绝孤儿行。
            db.createStatement().use { s -> s.execute("PRAGMA foreign_keys = ON") }
            var rejected = false
            try {
                db.createStatement().use {
                    it.execute("INSERT INTO favorite_documents VALUES ('ghost', 1)")
                }
            } catch (_: Exception) {
                rejected = true
            }
            assertTrue("orphan insert must fail under foreign_keys=ON", rejected)

            // 删除文档级联清理全部派生记录。
            db.createStatement().use { s ->
                s.execute("DELETE FROM documents WHERE id = 'doc-1'")
            }
            assertEquals(0, db.countOf("reading_states"))
            assertEquals(0, db.countOf("bookmarks"))
            assertEquals(0, db.countOf("favorite_documents"))
        }
    }

    @Test
    fun `bookmark autoincrement ids grow monotonically across deletes`() {
        withMigratedDatabase(seed = { db ->
            db.createStatement().use { it.execute("INSERT INTO documents(id) VALUES ('doc-1')") }
        }) { db ->
            fun insert() = db.createStatement().use { s ->
                s.executeUpdate(
                    "INSERT INTO bookmarks(documentId, page, createdAt) VALUES ('doc-1', 1, 1)",
                ).let {
                    db.createStatement().use { q ->
                        q.executeQuery("SELECT last_insert_rowid()").apply { next() }.getLong(1)
                    }
                }
            }
            val a = insert()
            val b = insert()
            db.createStatement().use { it.execute("DELETE FROM bookmarks WHERE id = $b") }
            val c = insert()
            assertEquals(1L, a)
            assertEquals(2L, b)
            assertTrue("AUTOINCREMENT never reuses ids", c > b)
        }
    }

    // ── harness ────────────────────────────────────────────────────────────

    private fun withMigratedDatabase(
        seed: (Connection) -> Unit = {},
        verify: (Connection) -> Unit,
    ) {
        val dbFile = Files.createTempFile("m1racle-reader-h21-v7-v8-", ".db")
        try {
            DriverManager.getConnection("jdbc:sqlite:${dbFile.toAbsolutePath()}").use { db ->
                createMinimalV7Schema(db)
                seed(db)
                db.autoCommit = false
                try {
                    db.createStatement().use { statement ->
                        statement.execute(AppDatabase.MIGRATION_7_8_CREATE_READING_STATES_SQL)
                        statement.execute(AppDatabase.MIGRATION_7_8_INDEX_READING_STATES_TIME_SQL)
                        statement.execute(AppDatabase.MIGRATION_7_8_INDEX_READING_STATES_DOC_SQL)
                        statement.execute(AppDatabase.MIGRATION_7_8_CREATE_BOOKMARKS_SQL)
                        statement.execute(AppDatabase.MIGRATION_7_8_INDEX_BOOKMARKS_DOC_SQL)
                        statement.execute(AppDatabase.MIGRATION_7_8_CREATE_FAVORITES_SQL)
                        statement.execute(AppDatabase.MIGRATION_7_8_INDEX_FAVORITES_TIME_SQL)
                        statement.execute("PRAGMA user_version = 8")
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

    /** v7 最小父表：迁移只做增量建表，FK 行为仅需 documents(id) 存在。 */
    private fun createMinimalV7Schema(db: Connection) {
        db.createStatement().use { s ->
            s.execute("CREATE TABLE documents (id TEXT NOT NULL PRIMARY KEY, displayName TEXT)")
            s.execute("PRAGMA user_version = 7")
        }
    }

    private fun Connection.userVersion(): Int = createStatement().use {
        it.executeQuery("PRAGMA user_version").apply { next() }.getInt(1)
    }

    private fun Connection.tableNames(): Set<String> = createStatement().use {
        it.executeQuery(
            "SELECT name FROM sqlite_master WHERE type = 'table'",
        ).let { rs -> buildSet { while (rs.next()) add(rs.getString(1)) } }
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
        it.executeQuery(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND name IS NOT NULL",
        ).let { rs -> buildSet { while (rs.next()) add(rs.getString(1)) } }
    }

    private fun Connection.countOf(table: String): Int = createStatement().use {
        it.executeQuery("SELECT COUNT(*) FROM $table").apply { next() }.getInt(1)
    }
}
