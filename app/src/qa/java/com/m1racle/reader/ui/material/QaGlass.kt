package com.m1racle.reader.ui.material

import android.content.Context
import androidx.compose.runtime.*
import com.m1racle.reader.BuildConfig

/** Live selection. Compose state so open screens update when QA flips it. */
object QaGlass {
    private const val PREFS = "qa-glass-variants-v1"
    private const val KEY_VARIANT = "variant"
    private const val KEY_LENS = "dock_night_lens"
    private const val KEY_PDF_NIGHT_INVERT = "pdf_night_invert"
    private const val KEY_UNIFIED_BLUR = "unified_blur"
    private const val KEY_COMPRESS_ORDER = "compress_order"

    var selection: QaGlassSelection by mutableStateOf(QaGlassSelection())

    val enabled: Boolean get() = BuildConfig.QA_GLASS_VARIANTS_ENABLED

    fun load(context: Context) {
        if (!enabled) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        selection = QaGlassSelection(
            variant = QaGlassVariant.entries.firstOrNull { it.name == prefs.getString(KEY_VARIANT, null) }
                ?: QaGlassVariant.BASELINE,
            dockNightLens = QaDockNightLens.entries.firstOrNull { it.name == prefs.getString(KEY_LENS, null) }
                ?: QaDockNightLens.WHITE_10,
            pdfNightInvert = prefs.getBoolean(KEY_PDF_NIGHT_INVERT, true),
            unifiedBlur = QaUnifiedBlur.entries.firstOrNull { it.name == prefs.getString(KEY_UNIFIED_BLUR, null) }
                ?: QaUnifiedBlur.B4,
            compressOrder = QaCompressOrder.entries.firstOrNull { it.name == prefs.getString(KEY_COMPRESS_ORDER, null) }
                ?: QaCompressOrder.VIBRANCY_FIRST,
        )
    }

    fun update(context: Context, next: QaGlassSelection) {
        if (!enabled) return
        selection = next
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_VARIANT, next.variant.name)
            .putString(KEY_LENS, next.dockNightLens.name)
            .putBoolean(KEY_PDF_NIGHT_INVERT, next.pdfNightInvert)
            .putString(KEY_UNIFIED_BLUR, next.unifiedBlur.name)
            .putString(KEY_COMPRESS_ORDER, next.compressOrder.name)
            .apply()
    }
}
