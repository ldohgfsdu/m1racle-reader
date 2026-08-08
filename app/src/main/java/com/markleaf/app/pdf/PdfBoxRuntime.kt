package com.markleaf.app.pdf

import android.content.Context
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Single process-wide PDFBox init for outline + page export.
 * Page **display** never goes through PDFBox ([PdfPageRenderer] / system PdfRenderer).
 */
object PdfBoxRuntime {
    private const val TAG = "PdfBoxRuntime"
    private val ready = AtomicBoolean(false)

    fun ensureInit(context: Context): Boolean {
        if (ready.get()) return true
        return synchronized(this) {
            if (ready.get()) return true
            try {
                PDFBoxResourceLoader.init(context.applicationContext)
                ready.set(true)
                true
            } catch (t: Throwable) {
                Log.w(TAG, "PDFBoxResourceLoader.init failed", t)
                ready.set(false)
                false
            }
        }
    }

    /** Prefer temp-file backing for large textbooks; fall back to heap if needed. */
    fun memorySetting(): MemoryUsageSetting =
        try {
            MemoryUsageSetting.setupTempFileOnly()
        } catch (_: Throwable) {
            MemoryUsageSetting.setupMainMemoryOnly()
        }
}
