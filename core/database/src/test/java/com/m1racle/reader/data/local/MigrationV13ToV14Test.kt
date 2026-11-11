package com.m1racle.reader.data.local

import java.sql.DriverManager
import org.junit.Assert.*
import org.junit.Test

class MigrationV13ToV14Test {
    @Test fun sourceMetadataMigrationPreservesExistingDocumentsAndAnnotations() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { db ->
            db.createStatement().use { s ->
                s.execute("PRAGMA foreign_keys=ON")
                s.execute("CREATE TABLE documents(id TEXT PRIMARY KEY, uri TEXT NOT NULL, contentSha256 TEXT)")
                s.execute("CREATE TABLE bookmarks(id INTEGER PRIMARY KEY, documentId TEXT REFERENCES documents(id) ON DELETE CASCADE)")
                s.execute("INSERT INTO documents VALUES('book','file:///original.pdf','verified-hash')")
                s.execute("INSERT INTO bookmarks VALUES(7,'book')")
                s.execute(AppDatabase.MIGRATION_13_14_SOURCE_URI_SQL)
                s.execute(AppDatabase.MIGRATION_13_14_SOURCE_SHA_SQL)
                s.executeQuery("SELECT * FROM documents").use { r ->
                    assertTrue(r.next()); assertEquals("book",r.getString("id"))
                    assertEquals("file:///original.pdf",r.getString("uri"))
                    assertEquals("verified-hash",r.getString("contentSha256"))
                    assertNull(r.getString("sourceUri")); assertNull(r.getString("sourceSha256"))
                }
                s.executeQuery("SELECT documentId FROM bookmarks WHERE id=7").use { r -> assertTrue(r.next()); assertEquals("book",r.getString(1)) }
                s.executeQuery("PRAGMA foreign_key_check").use { assertFalse(it.next()) }
            }
        }
    }
}
