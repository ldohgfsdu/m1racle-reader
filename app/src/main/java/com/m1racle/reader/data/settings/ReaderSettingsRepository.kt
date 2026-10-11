package com.m1racle.reader.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.readerSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "reader_settings",
)

/**
 * Preferences DataStore for [ReaderSettings].
 * Always emits a safe default on I/O errors or invalid stored values.
 */
class ReaderSettingsRepository(
    private val context: Context,
) {
    private val dataStore = context.applicationContext.readerSettingsDataStore

    val settingsFlow: Flow<ReaderSettings> = dataStore.data
        .catch { e ->
            if (e is IOException) {
                emit(emptyPreferences())
            } else {
                throw e
            }
        }
        .map { prefs -> prefs.toReaderSettings() }

    suspend fun updateFontScale(scale: Float) {
        dataStore.edit { prefs ->
            prefs[KEY_FONT_SCALE] = ReaderSettings.clampFontScale(scale)
        }
    }

    suspend fun updateLineHeightScale(scale: Float) {
        dataStore.edit { prefs ->
            prefs[KEY_LINE_HEIGHT_SCALE] = ReaderSettings.clampLineHeightScale(scale)
        }
    }

    suspend fun updateContentPaddingLevel(level: ContentPaddingLevel) {
        dataStore.edit { prefs ->
            prefs[KEY_PADDING_LEVEL] = level.name
        }
    }

    suspend fun updateEpubFontFamily(font: EpubFontFamily) {
        dataStore.edit { it[KEY_EPUB_FONT] = font.name; it.remove(KEY_IMPORTED_FONT) }
    }

    suspend fun updateEpubReadingMode(mode: EpubReadingMode) {
        dataStore.edit { it[KEY_EPUB_MODE] = mode.name }
    }

    suspend fun updateImportedFont(id: String?) {
        require(id == null || id.matches(Regex("[0-9a-f]{64}")))
        dataStore.edit { if (id == null) it.remove(KEY_IMPORTED_FONT) else it[KEY_IMPORTED_FONT] = id }
    }

    suspend fun clearImportedFont(id: String) {
        dataStore.edit { if (it[KEY_IMPORTED_FONT] == id) it.remove(KEY_IMPORTED_FONT) }
    }

    suspend fun resetToDefaults() {
        dataStore.edit { prefs ->
            prefs[KEY_FONT_SCALE] = ReaderSettings.DEFAULT_FONT_SCALE
            prefs[KEY_LINE_HEIGHT_SCALE] = ReaderSettings.DEFAULT_LINE_HEIGHT_SCALE
            prefs[KEY_PADDING_LEVEL] = ContentPaddingLevel.Standard.name
            prefs[KEY_EPUB_FONT] = EpubFontFamily.Default.name
            prefs[KEY_EPUB_MODE] = EpubReadingMode.Slide.name
            prefs.remove(KEY_IMPORTED_FONT)
        }
    }

    private fun Preferences.toReaderSettings(): ReaderSettings {
        val font = ReaderSettings.clampFontScale(
            this[KEY_FONT_SCALE] ?: ReaderSettings.DEFAULT_FONT_SCALE,
        )
        val line = ReaderSettings.clampLineHeightScale(
            this[KEY_LINE_HEIGHT_SCALE] ?: ReaderSettings.DEFAULT_LINE_HEIGHT_SCALE,
        )
        val padding = ContentPaddingLevel.fromStorage(this[KEY_PADDING_LEVEL])
        return ReaderSettings(
            epubReadingMode = EpubReadingMode.entries.firstOrNull { it.name == this[KEY_EPUB_MODE] } ?: EpubReadingMode.Slide,
            importedFontId = this[KEY_IMPORTED_FONT]?.takeIf { it.matches(Regex("[0-9a-f]{64}")) },
            fontScale = font,
            lineHeightScale = line,
            contentPaddingLevel = padding,
            epubFontFamily = EpubFontFamily.entries.firstOrNull { it.name == this[KEY_EPUB_FONT] }
                ?: EpubFontFamily.Default,
        )
    }

    private companion object {
        val KEY_FONT_SCALE = floatPreferencesKey("font_scale")
        val KEY_LINE_HEIGHT_SCALE = floatPreferencesKey("line_height_scale")
        val KEY_PADDING_LEVEL = stringPreferencesKey("content_padding_level")
        val KEY_EPUB_FONT = stringPreferencesKey("epub_font_family")
        val KEY_EPUB_MODE = stringPreferencesKey("epub_reading_mode")
        val KEY_IMPORTED_FONT = stringPreferencesKey("imported_font_id")
    }
}
