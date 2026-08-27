package com.m1racle.reader.pdf

import android.content.Context

/** Top-of-reader presentation mode (user-visible setting). */
enum class PdfTopBarMode {
    /** Status bar stays visible; edge-to-edge reader, no scrim/gradient painted. */
    NORMAL,

    /** Hide only the status bar; keep the transparent gesture navigation bar usable. */
    IMMERSIVE,
}

/** Lightweight global prefs for the PDF reader chrome (SharedPreferences, app-wide). */
class PdfChromePrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getTopBarMode(): PdfTopBarMode {
        val raw = prefs.getString(KEY_TOP_BAR_MODE, null)
        return PdfTopBarMode.entries.firstOrNull { it.name == raw } ?: PdfTopBarMode.NORMAL
    }

    fun setTopBarMode(mode: PdfTopBarMode) {
        prefs.edit().putString(KEY_TOP_BAR_MODE, mode.name).apply()
    }

    companion object {
        const val PREFS_NAME = "pdf_chrome_prefs"
        const val KEY_TOP_BAR_MODE = "top_bar_mode"
    }
}
