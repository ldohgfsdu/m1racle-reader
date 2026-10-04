package com.m1racle.reader.data.local

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v14 -> v15 on real SQLite (same mechanism as MigrationV7ToV8Test).
 *
 * The v14 database is built from the committed 14.json, filled with rows, migrated
 * with the production SQL constants, then compared with a fresh database built from
 * the exported 15.json: every table's columns and indices must match exactly, and
 * every existing value must survive with NULL anchors.
 */
class MigrationV14ToV15Test {

    @Test fun `migration adds nullable anchors and preserves every existing row`() {
        connect().use { db ->
            createFromSchema(db, 14)
            db.createStatement().use { s ->
                s.execute(
                    "INSERT INTO documents(id, displayName, uri, lastOpenedAt, createdAt, lastScrollIndex, " +
                        "lastScrollOffset, readingProgress, headingCount, documentType, pageCount, contentSha256) " +
                        "VALUES('c:book', '书.epub', 'file:///books/a.epub', 10, 5, 42, 318, 0.37, 7, 'EPUB', 0, 'sha')",
                )
                s.execute(
                    "INSERT INTO bookmarks(id, documentId, locationKind, location, offset, title, note, createdAt) " +
                        "VALUES(3, 'c:book', 'SCROLL_POSITION', 12, 9, '标题', '笔记', 99)",
                )
                s.execute(
                    "INSERT INTO highlights(id, documentId, locationKind, location, anchor, createdAt) " +
                        "VALUES(4, 'c:book', 'SCROLL_POSITION', 12, 'md:12|-', 100)",
                )
                s.execute("PRAGMA user_version = 14")

                s.execute(AppDatabase.MIGRATION_14_15_DOCUMENTS_READING_ANCHOR_SQL)
                s.execute(AppDatabase.MIGRATION_14_15_BOOKMARKS_ANCHOR_SQL)
                s.execute("PRAGMA user_version = 15")

                s.executeQuery("SELECT * FROM documents WHERE id = 'c:book'").use { r ->
                    assertTrue(r.next())
                    assertEquals("书.epub", r.getString("displayName"))
                    assertEquals(42, r.getInt("lastScrollIndex"))
                    assertEquals(318, r.getInt("lastScrollOffset"))
                    assertEquals(0.37, r.getDouble("readingProgress"), 1e-6)
                    assertEquals("sha", r.getString("contentSha256"))
                    assertNull(r.getString("readingAnchor"))
                }
                s.executeQuery("SELECT * FROM bookmarks WHERE id = 3").use { r ->
                    assertTrue(r.next())
                    assertEquals("SCROLL_POSITION", r.getString("locationKind"))
                    assertEquals(12, r.getInt("location"))
                    assertEquals(9L, r.getLong("offset"))
                    assertEquals("笔记", r.getString("note"))
                    assertNull(r.getString("anchor"))
                }
                s.executeQuery("SELECT anchor FROM highlights WHERE id = 4").use { r ->
                    assertTrue(r.next()); assertEquals("md:12|-", r.getString(1))
                }
                // The new columns accept the stored anchor forms.
                s.execute("UPDATE documents SET readingAnchor = 'epub:text%2Fc1.xhtml|3|10,-|v1|q' WHERE id = 'c:book'")
                s.execute("UPDATE bookmarks SET locationKind = 'EPUB_TEXT', anchor = 'epub:a|0|0,-|v1|' WHERE id = 3")
                s.executeQuery("PRAGMA foreign_key_check").use { assertFalse(it.next()) }
                s.executeQuery("PRAGMA user_version").use { assertTrue(it.next()); assertEquals(15, it.getInt(1)) }
            }
            connect().use { fresh ->
                createFromSchema(fresh, 15)
                assertEquals(shape(fresh), shape(db))
            }
        }
    }

    @Test fun `exported v15 schema differs from v14 only by the two anchor columns`() {
        connect().use { v14 ->
            connect().use { v15 ->
                createFromSchema(v14, 14)
                createFromSchema(v15, 15)
                val before = shape(v14)
                val after = shape(v15)
                assertEquals(before.keys, after.keys)
                after.forEach { (table, columns) ->
                    val added = columns - before.getValue(table).toSet()
                    val expected = when (table) {
                        "documents" -> listOf("column readingAnchor:TEXT:0:0:null")
                        "bookmarks" -> listOf("column anchor:TEXT:0:0:null")
                        else -> emptyList()
                    }
                    assertEquals(table, expected, added)
                    assertEquals(table, before.getValue(table), columns - added.toSet())
                }
            }
        }
    }

    private fun connect(): Connection = DriverManager.getConnection("jdbc:sqlite::memory:").also { db ->
        db.createStatement().use { it.execute("PRAGMA foreign_keys=ON") }
    }

    /** Creates every table and index of the committed schema export. */
    private fun createFromSchema(db: Connection, version: Int) {
        val json = File(projectRoot(), "app/schemas/com.m1racle.reader.data.local.AppDatabase/$version.json").readText()
        val createSql = Regex("\"createSql\": \"((?:[^\"\\\\]|\\\\.)*)\"")
        db.createStatement().use { s ->
            json.split("\"tableName\": \"").drop(1).forEach { entity ->
                val table = entity.substringBefore('"')
                createSql.findAll(entity).forEach { match ->
                    s.execute(match.groupValues[1].replace("\\\"", "\"").replace("\${TABLE_NAME}", table))
                }
            }
        }
    }

    /** table -> ordered "column name:type:notnull:pk:default" and "index name:unique:columns". */
    private fun shape(db: Connection): Map<String, List<String>> {
        val tables = db.createStatement().use { s ->
            s.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name").use { r ->
                buildList { while (r.next()) add(r.getString(1)) }
            }
        }
        return tables.associateWith { table ->
            db.createStatement().use { s ->
                val columns = s.executeQuery("PRAGMA table_info(`$table`)").use { r ->
                    buildList {
                        while (r.next()) add("column ${r.getString("name")}:${r.getString("type")}:" +
                            "${r.getInt("notnull")}:${r.getInt("pk")}:${r.getString("dflt_value")}")
                    }
                }
                val indexNames = s.executeQuery("PRAGMA index_list(`$table`)").use { r ->
                    buildList { while (r.next()) add(r.getString("name") to r.getInt("unique")) }
                }.sortedBy { it.first }
                val indices = indexNames.map { (name, unique) ->
                    val cols = s.executeQuery("PRAGMA index_info(`$name`)").use { r ->
                        buildList { while (r.next()) add(r.getString("name")) }
                    }
                    "index $name:$unique:${cols.joinToString(",")}"
                }
                columns + indices
            }
        }
    }

    private fun projectRoot(): File = generateSequence(File(".").canonicalFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile && File(it, "app").isDirectory }
}
