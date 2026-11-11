package com.m1racle.reader.data.local

import java.nio.file.Files
import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** R24：v10 → v11 锚点列迁移契约（增量 ADD COLUMN，历史行保持 NULL）。 */
class MigrationV10ToV11Test {

    @Test
    fun `migration adds nullable anchor columns to annotations`() {
        val dbFile = Files.createTempFile("m1racle-reader-r24-v10-v11-", ".db")
        try {
            DriverManager.getConnection("jdbc:sqlite:${dbFile.toAbsolutePath()}").use { db ->
                db.createStatement().use { s ->
                    s.execute("CREATE TABLE documents (id TEXT NOT NULL PRIMARY KEY, displayName TEXT)")
                    s.execute(AppDatabase.MIGRATION_9_10_CREATE_HIGHLIGHTS_SQL)
                    s.execute(AppDatabase.MIGRATION_9_10_HIGHLIGHTS_DOC_INDEX_SQL)
                    s.execute(AppDatabase.MIGRATION_9_10_HIGHLIGHTS_LOC_INDEX_SQL)
                    s.execute(AppDatabase.MIGRATION_9_10_CREATE_NOTES_SQL)
                    s.execute(AppDatabase.MIGRATION_9_10_NOTES_DOC_INDEX_SQL)
                    s.execute(AppDatabase.MIGRATION_9_10_NOTES_LOC_INDEX_SQL)
                    s.execute("PRAGMA user_version = 10")
                    // v10 历史行存在。
                    s.execute("INSERT INTO documents(id) VALUES ('doc-1')")
                    s.execute(
                        """
                        INSERT INTO highlights(documentId, locationKind, location, createdAt)
                        VALUES ('doc-1', 'PAGE_INDEX', 5, 1)
                        """.trimIndent(),
                    )
                    s.execute(
                        """
                        INSERT INTO notes(documentId, locationKind, location, content, createdAt, updatedAt)
                        VALUES ('doc-1', 'SCROLL_POSITION', 8, 'n', 2, 2)
                        """.trimIndent(),
                    )
                    // 迁移：ADD COLUMN anchor TEXT。
                    s.execute(AppDatabase.MIGRATION_10_11_HIGHLIGHTS_ANCHOR_SQL)
                    s.execute(AppDatabase.MIGRATION_10_11_NOTES_ANCHOR_SQL)
                    s.execute("PRAGMA user_version = 11")
                }
                assertEquals(11, db.createStatement().use {
                    it.executeQuery("PRAGMA user_version").apply { next() }.getInt(1)
                })
                assertTrue(db.columnsOf("highlights").contains("anchor"))
                assertTrue(db.columnsOf("notes").contains("anchor"))
                // 历史行 anchor 为 NULL 且数据完整保留。
                db.createStatement().use { s ->
                    val hAnchorRs = s.executeQuery(
                        "SELECT anchor FROM highlights WHERE documentId = 'doc-1'",
                    )
                    hAnchorRs.next()
                    assertEquals(null, hAnchorRs.getString(1))
                    hAnchorRs.close()
                }
                db.createStatement().use { s ->
                    val countRs = s.executeQuery("SELECT COUNT(*) FROM notes")
                    countRs.next()
                    assertEquals(1, countRs.getInt(1))
                    countRs.close()
                }
            }
        } finally {
            Files.deleteIfExists(dbFile)
        }
    }

    private fun Connection.columnsOf(table: String): Set<String> = createStatement().use {
        it.executeQuery("PRAGMA table_info($table)").let { rs ->
            buildSet { while (rs.next()) add(rs.getString("name")) }
        }
    }
}
