package com.m1racle.reader.data.settings

import androidx.datastore.preferences.core.preferencesOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ReaderLineHeightMigrationTest {
    @Test fun historicalPresetsPersistAsRatiosAndDoNotMigrateAgain() = runBlocking {
        listOf(.95f to 1.35f, 1f to 1.55f, 1.15f to 1.75f).forEach { (old, ratio) ->
            val before = preferencesOf(ReaderLineHeightMigration.key to old)
            assertTrue(ReaderLineHeightMigration.shouldMigrate(before))
            val after = ReaderLineHeightMigration.migrate(before)
            assertEquals(ratio, after[ReaderLineHeightMigration.key])
            assertFalse(ReaderLineHeightMigration.shouldMigrate(after))
        }
    }

    @Test fun currentRatiosAndMissingPreferencesKeepTheirMeaning() = runBlocking {
        val current = ReaderLineHeightMigration.migrate(preferencesOf(ReaderLineHeightMigration.key to 1.91f))
        assertEquals(1.91f, current[ReaderLineHeightMigration.key])
        val empty = ReaderLineHeightMigration.migrate(preferencesOf())
        assertNull(empty[ReaderLineHeightMigration.key])
        assertFalse(ReaderLineHeightMigration.shouldMigrate(empty))
        assertEquals(ReaderSettings.MIN_LINE_HEIGHT_SCALE, ReaderSettings.clampLineHeightScale(.95f))
    }
}
