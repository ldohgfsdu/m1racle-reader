package com.m1racle.reader.data.local

import java.sql.Connection
import java.sql.DriverManager
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H2.1 权威收口：v8 → v9 迁移契约（宿主真实 SQLite，机制同前序迁移测试）。
 *
 * 验证：reading_states 表被删除（第二阅读状态存储不复存在）；bookmarks 从
 * PDF 分页绑定的 page 列重建为文档中性的 (locationKind, location)，旧行映射
 * PAGE_INDEX 且数据零丢失；favorites 不受影响。
 */
class MigrationV8ToV9Test {

    @Test
    fun `migration drops reading_states and rebuilds bookmarks with neutral location`() {
        withMigratedDatabase { db ->
            assertEquals(9, db.userVersion())
            val tables = db.tableNames()
            assertFalse("second reading store must be gone", tables.contains("reading_states"))
            assertTrue(tables.contains("bookmarks"))
            assertTrue(tables.contains("favorite_documents"))

            assertEquals(
                listOf(
                    "id:INTEGER:1:1",
                    "documentId:TEXT:1:0",
                    "locationKind:TEXT:1:0",
                    "location:INTEGER:1:0",
                    "offset:INTEGER:0:0",
                    "title:TEXT:0:0",
                    "note:TEXT:0:0",
                    "createdAt:INTEGER:1:0",
                ),
                db.columns("bookmarks"),
            )
            assertFalse(db.columns("bookmarks").any { it.startsWith("page:") })
            assertTrue(db.indexNames().contains("index_bookmarks_documentId"))
        }
    }

    @Test
    fun `legacy bookmark rows map to PAGE_INDEX with zero data loss`() {
        withMigratedDatabase(seed = { db ->
            db.createStatement().use { s ->
                s.execute("INSERT INTO documents(id) VALUES ('doc-1')")
                // v8 bookmarks 旧结构行。
                s.execute(
                    """
                    INSERT INTO bookmarks(id, documentId, page, `offset`, title, note, createdAt)
                    VALUES (7, 'doc-1', 41, 1024, '第三章', 'note', 100)
                    """.trimIndent(),
                )
                // v8 reading_states 存在残留行 → 应随表一起消失。
                s.execute(
                    "INSERT INTO reading_states VALUES ('doc-1', 12, 0.5, 999)",
                )
            }
        }) { db ->
            db.createStatement().use { s ->
                val rs = s.executeQuery(
                    "SELECT id, documentId, locationKind, location, `offset`, title, note, createdAt FROM bookmarks",
                )
                rs.next()
                assertEquals(7L, rs.getLong("id"))
                assertEquals("doc-1", rs.getString("documentId"))
                assertEquals("PAGE_INDEX", rs.getString("locationKind"))
                assertEquals(41, rs.getInt("location"))
                assertEquals(1024L, rs.getLong("offset"))
                assertEquals("第三章", rs.getString("title"))
                assertEquals("note", rs.getString("note"))
                assertEquals(100L, rs.getLong("createdAt"))
                assertFalse(rs.next())
            }
            // 第二存储随迁移彻底消失（连表都不在）。
            assertFalse(db.tableNames().contains("reading_states"))
            assertEquals(1, db.countOf("bookmarks"))
        }
    }

    @Test
    fun `favorites survive and cascade still enforced after rebuild`() {
        withMigratedDatabase(seed = { db ->
            db.createStatement().use { s ->
                s.execute("INSERT INTO documents(id) VALUES ('doc-1')")
                s.execute("INSERT INTO favorite_documents VALUES ('doc-1', 20)")
                s.execute(
                    "INSERT INTO bookmarks(documentId, page, createdAt) VALUES ('doc-1', 3, 10)",
                )
            }
        }) { db ->
            assertEquals(1, db.countOf("favorite_documents"))
            assertEquals(20L, db.oneLong("SELECT createdAt FROM favorite_documents WHERE documentId='doc-1'"))

            // FK 强制：重建后的 bookmarks 依旧拒绝孤儿。
            db.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
            var rejected = false
            try {
                db.createStatement().use {
                    it.execute(
                        "INSERT INTO bookmarks(documentId, locationKind, location, createdAt) " +
                            "VALUES ('ghost', 'PAGE_INDEX', 1, 1)",
                    )
                }
            } catch (_: Exception) {
                rejected = true
            }
            assertTrue(rejected)

            // CASCADE：删文档清空书签与收藏。
            db.createStatement().use { it.execute("DELETE FROM documents WHERE id = 'doc-1'") }
            assertEquals(0, db.countOf("bookmarks"))
            assertEquals(0, db.countOf("favorite_documents"))
        }
    }

    @Test
    fun `rebuilt bookmark autoincrement continues past legacy ids`() {
        withMigratedDatabase(seed = { db ->
            db.createStatement().use { s ->
                s.execute("INSERT INTO documents(id) VALUES ('doc-1')")
                s.execute(
                    """
                    INSERT INTO bookmarks(id, documentId, page, createdAt)
                    VALUES (7, 'doc-1', 1, 1)
                    """.trimIndent(),
                )
            }
        }) { db ->
            db.createStatement().use {
                it.executeUpdate(
                    "INSERT INTO bookmarks(documentId, locationKind, location, createdAt) " +
                        "VALUES ('doc-1', 'SCROLL_POSITION', 5, 2)",
                )
            }
            assertEquals(8L, db.oneLong("SELECT MAX(id) FROM bookmarks"))
        }
    }

    // ── harness ────────────────────────────────────────────────────────────

    /** 建 v8 schema（documents 最小父表 + 真实 7→8 常量）→ 灌种子 → 执行 8→9。 */
    private fun withMigratedDatabase(
        seed: (Connection) -> Unit = {},
        verify: (Connection) -> Unit,
    ) {
        val dbFile = Files.createTempFile("m1racle-reader-h21-authority-v8-v9-", ".db")
        try {
            DriverManager.getConnection("jdbc:sqlite:${dbFile.toAbsolutePath()}").use { db ->
                createMinimalV7Schema(db)
                db.autoCommit = false
                try {
                    db.createStatement().use { statement ->
                        // 真实历史链前半：7→8 建三表。
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
                // 种子 = v8 迁移完成后的存量用户数据。
                seed(db)
                db.autoCommit = false
                try {
                    db.createStatement().use { statement ->
                        statement.execute(AppDatabase.MIGRATION_8_9_DROP_READING_STATES_SQL)
                        statement.execute(AppDatabase.MIGRATION_8_9_CREATE_BOOKMARKS_V2_SQL)
                        statement.execute(AppDatabase.MIGRATION_8_9_COPY_BOOKMARKS_SQL)
                        statement.execute(AppDatabase.MIGRATION_8_9_DROP_OLD_BOOKMARKS_SQL)
                        statement.execute(AppDatabase.MIGRATION_8_9_RENAME_BOOKMARKS_SQL)
                        statement.execute(AppDatabase.MIGRATION_8_9_BOOKMARKS_INDEX_SQL)
                        statement.execute("PRAGMA user_version = 9")
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

    private fun Connection.oneLong(sql: String): Long = createStatement().use {
        it.executeQuery(sql).apply { next() }.getLong(1)
    }
}
