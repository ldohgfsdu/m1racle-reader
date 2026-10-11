package com.m1racle.reader.data.local

import java.sql.DriverManager
import org.junit.Assert.*
import org.junit.Test

class MigrationV16ToV17Test {
    @Test fun canonicalAnchorsPreserveAssetsAndUpdateSearchWithoutOverwritingModernCoordinates() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { db ->
            db.createStatement().use { sql ->
                listOf("highlights", "notes").forEach { table ->
                    sql.execute("CREATE TABLE $table(id INTEGER PRIMARY KEY, documentId TEXT, locationKind TEXT, location INTEGER, anchor TEXT, anchorStart INTEGER, anchorEnd INTEGER, content TEXT)")
                    sql.execute("INSERT INTO $table VALUES(1,'book','SCROLL_POSITION',8,NULL,2,9,'批注🌟'),(2,'book','PAGE_INDEX',7,NULL,NULL,NULL,'PDF'),(3,'book','PAGE_INDEX',2,'pdf:2|0.1,0.2,0.3,0.4',NULL,NULL,'modern'),(4,'book','UNKNOWN',3,NULL,NULL,NULL,'unknown')")
                }
                sql.execute("CREATE TABLE search_index(documentId TEXT, contentType TEXT, sourceId TEXT, anchor TEXT)")
                sql.execute("INSERT INTO search_index VALUES('book','HIGHLIGHT','1','md:8|-'),('book','NOTE','2','pdf:7|-')")
                repeat(2) { AnnotationAnchorMigration.statements.forEach(sql::execute) }
                listOf("highlights", "notes").forEach { table ->
                    sql.executeQuery("SELECT * FROM $table ORDER BY id").use { row ->
                        assertTrue(row.next()); assertEquals("md:8|2,9", row.getString("anchor")); assertEquals("批注🌟", row.getString("content"))
                        assertTrue(row.next()); assertEquals("pdf:7|-", row.getString("anchor"))
                        assertTrue(row.next()); assertEquals("pdf:2|0.1,0.2,0.3,0.4", row.getString("anchor"))
                        assertTrue(row.next()); assertNull(row.getString("anchor")); assertFalse(row.next())
                    }
                }
                sql.executeQuery("SELECT anchor FROM search_index WHERE contentType='HIGHLIGHT'").use { row ->
                    assertTrue(row.next()); assertEquals("md:8|2,9", row.getString(1))
                }
            }
        }
        assertEquals(16, AppDatabase.MIGRATION_16_17.startVersion)
        assertEquals(17, AppDatabase.MIGRATION_16_17.endVersion)
    }
}
