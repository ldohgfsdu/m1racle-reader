package com.m1racle.reader.data.settings

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import kotlin.math.abs

/** Persists modern ratios before settings can be read, exactly once per DataStore. */
internal object ReaderLineHeightMigration : DataMigration<Preferences> {
    val key = floatPreferencesKey("line_height_scale")
    private val completed = booleanPreferencesKey("line_height_ratio_migrated_v1")

    override suspend fun shouldMigrate(currentData: Preferences) = currentData[completed] != true

    override suspend fun migrate(currentData: Preferences): Preferences = currentData.toMutablePreferences().apply {
        currentData[key]?.let { value ->
            this[key] = when {
                abs(value - .95f) < .03f -> ReaderSettings.LINE_COMPACT
                abs(value - 1f) < .03f -> ReaderSettings.LINE_DEFAULT
                abs(value - 1.15f) < .03f -> ReaderSettings.LINE_RELAXED
                else -> ReaderSettings.clampLineHeightScale(value)
            }
        }
        this[completed] = true
    }

    override suspend fun cleanUp() = Unit
}
