package com.m1racle.reader.data.local

import java.sql.DriverManager
import org.junit.Assert.*
import org.junit.Test

class MigrationV15ToV16Test {
    @Test fun existingHighlightPreservesAnchorExcerptAndOwnershipWithDefaultYellow() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { db ->
            db.createStatement().use { sql ->
                sql.execute("CREATE TABLE highlights(id INTEGER PRIMARY KEY, documentId TEXT NOT NULL, locationKind TEXT NOT NULL, location INTEGER NOT NULL, anchor TEXT, anchorStart INTEGER, anchorEnd INTEGER, excerpt TEXT, createdAt INTEGER NOT NULL)")
                sql.execute("CREATE INDEX index_highlights_documentId ON highlights(documentId)")
                sql.execute("INSERT INTO highlights VALUES(71,'book','SCROLL_POSITION',8,'md:8|2,9',2,9,'旧的笔记🌟',12345)")
                sql.execute(AppDatabase.MIGRATION_15_16_HIGHLIGHT_STYLE_SQL)
                sql.executeQuery("SELECT * FROM highlights").use { row ->
                    assertTrue(row.next()); assertEquals(71, row.getInt("id"))
                    assertEquals("book", row.getString("documentId")); assertEquals("SCROLL_POSITION", row.getString("locationKind"))
                    assertEquals(8, row.getInt("location")); assertEquals("md:8|2,9", row.getString("anchor"))
                    assertEquals(2, row.getInt("anchorStart")); assertEquals(9, row.getInt("anchorEnd"))
                    assertEquals("旧的笔记🌟", row.getString("excerpt")); assertEquals(12345, row.getLong("createdAt"))
                    assertEquals("Yellow", row.getString("style")); assertFalse(row.next())
                }
                sql.execute("UPDATE highlights SET style = 'Underline' WHERE id = 71")
                sql.executeQuery("SELECT style FROM highlights WHERE id = 71").use { row -> assertTrue(row.next()); assertEquals("Underline", row.getString(1)) }
                sql.executeQuery("PRAGMA index_list(highlights)").use { row -> assertTrue(row.next()); assertEquals("index_highlights_documentId", row.getString("name")) }
            }
        }
    }
}
