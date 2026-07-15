package com.markleaf.app.share

import java.util.Locale

/**
 * Formats raw [sizeBytes] for UI. Storage always keeps the Long; only UI calls this.
 */
object FileSizeFormatter {
    /** Provider could not pre-size the stream; final size is copy byte count after import. */
    const val SIZE_COMPUTE_AFTER_OPEN = "大小将在打开时确认"

    fun format(sizeBytes: Long?): String {
        if (sizeBytes == null || sizeBytes < 0L) return "未知大小"
        if (sizeBytes < 1024L) return "$sizeBytes B"
        val units = arrayOf("KB", "MB", "GB")
        var value = sizeBytes.toDouble()
        var unitIndex = -1
        while (value >= 1024.0 && unitIndex < units.lastIndex) {
            value /= 1024.0
            unitIndex++
        }
        val pattern = if (value < 10.0) "%.1f %s" else "%.0f %s"
        return String.format(Locale.US, pattern, value, units[unitIndex.coerceAtLeast(0)])
    }

    /** Confirm-dialog size line: known metadata vs deferred computation. */
    fun formatForConfirmDialog(sizeBytes: Long?, sizeUnknownUntilOpen: Boolean): String {
        if (sizeUnknownUntilOpen || sizeBytes == null || sizeBytes < 0L) {
            return SIZE_COMPUTE_AFTER_OPEN
        }
        return format(sizeBytes)
    }
}
