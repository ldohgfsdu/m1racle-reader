package com.markleaf.app.pdf

import android.content.Context

/**
 * Per-document PDF reading chrome prefs (no Room migration).
 * Keys: pdf_reading_mode_<documentId>, pdf_show_page_buttons_<documentId>
 */
class PdfReadingPrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getMode(documentId: String): PdfReadingMode {
        val raw = prefs.getString(modeKey(documentId), null)
        return PdfReadingMode.fromStorage(raw)
    }

    fun setMode(documentId: String, mode: PdfReadingMode) {
        prefs.edit().putString(modeKey(documentId), mode.toStorage()).apply()
    }

    fun getShowPageButtons(documentId: String): Boolean =
        prefs.getBoolean(buttonsKey(documentId), true)

    fun setShowPageButtons(documentId: String, show: Boolean) {
        prefs.edit().putBoolean(buttonsKey(documentId), show).apply()
    }

    companion object {
        private const val PREFS_NAME = "pdf_reading_prefs"

        fun modeKey(documentId: String) = "pdf_reading_mode_$documentId"
        fun buttonsKey(documentId: String) = "pdf_show_page_buttons_$documentId"
    }
}
