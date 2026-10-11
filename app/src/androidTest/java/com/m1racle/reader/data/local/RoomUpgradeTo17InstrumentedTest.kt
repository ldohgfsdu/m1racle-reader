package com.m1racle.reader.data.local

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Validates the installed v15 and recovered v16 upgrade using real schema assets. */
@RunWith(AndroidJUnit4::class)
class RoomUpgradeTo17InstrumentedTest {
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java, emptyList(),
    )

    @Test fun installed15AndRecovered16PreserveDocumentAndCanonicalizeAnnotations() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        for (version in listOf(15, 16)) {
            val name = "optical_upgrade_${version}_17_test.db"
            context.deleteDatabase(name)
            try {
                helper.createDatabase(name, version).use { db ->
                    insertFixture(db, "documents", mapOf(
                        "id" to "fixture", "displayName" to "kept.md", "documentType" to "MARKDOWN",
                        "uri" to "file:///kept.md", "lastScrollIndex" to 8, "readingProgress" to .42,
                    ))
                    insertFixture(db, "documents", mapOf(
                        "id" to "pdf-fixture", "displayName" to "kept.pdf", "documentType" to "PDF",
                        "uri" to "file:///kept.pdf", "lastScrollIndex" to 7, "readingProgress" to .63,
                    ))
                    insertFixture(db, "highlights", mapOf(
                        "id" to 1, "documentId" to "fixture", "locationKind" to "SCROLL_POSITION",
                        "location" to 8, "anchor" to null, "anchorStart" to 2, "anchorEnd" to 9,
                        "excerpt" to "kept excerpt",
                    ))
                    insertFixture(db, "notes", mapOf(
                        "id" to 1, "documentId" to "pdf-fixture", "locationKind" to "PAGE_INDEX",
                        "location" to 7, "anchor" to null, "content" to "kept note",
                    ))
                }
                val migrations = if (version == 15) arrayOf(AppDatabase.MIGRATION_15_16, AppDatabase.MIGRATION_16_17)
                    else arrayOf(AppDatabase.MIGRATION_16_17)
                helper.runMigrationsAndValidate(name, 17, true, *migrations).use { db ->
                    db.query("SELECT displayName, lastScrollIndex, readingProgress FROM documents WHERE id='fixture'").use {
                        assertTrue(it.moveToFirst()); assertEquals("kept.md", it.getString(0))
                        assertEquals(8, it.getInt(1)); assertEquals(.42, it.getDouble(2), .0001)
                    }
                    db.query("SELECT anchor, excerpt FROM highlights WHERE id=1").use {
                        assertTrue(it.moveToFirst()); assertEquals("md:8|2,9", it.getString(0)); assertEquals("kept excerpt", it.getString(1))
                    }
                    db.query("SELECT anchor, content FROM notes WHERE id=1").use {
                        assertTrue(it.moveToFirst()); assertEquals("pdf:7|-", it.getString(0)); assertEquals("kept note", it.getString(1))
                    }
                }
            } finally { context.deleteDatabase(name) }
        }
    }

    private fun insertFixture(db: SupportSQLiteDatabase, table: String, overrides: Map<String, Any?>) {
        val values = ContentValues()
        db.query("PRAGMA table_info($table)").use { columns ->
            while (columns.moveToNext()) {
                val column = columns.getString(columns.getColumnIndexOrThrow("name"))
                val type = columns.getString(columns.getColumnIndexOrThrow("type"))
                val required = columns.getInt(columns.getColumnIndexOrThrow("notnull")) == 1
                if (required && !overrides.containsKey(column)) {
                    if (type == "TEXT") values.put(column, "fixture") else values.put(column, 0)
                }
            }
        }
        overrides.forEach { (column, value) ->
            when (value) {
                null -> values.putNull(column)
                is String -> values.put(column, value)
                is Int -> values.put(column, value)
                is Double -> values.put(column, value)
                else -> error("Unsupported fixture value")
            }
        }
        db.insert(table, SQLiteDatabase.CONFLICT_ABORT, values)
    }
}
