package com.m1racle.reader.pdf

/**
 * PDF reader layout mode (per document).
 * Flip buttons are chrome only — not a third render mode.
 */
enum class PdfReadingMode {
    VERTICAL_CONTINUOUS,
    HORIZONTAL_PAGED,
    ;

    fun toStorage(): String = when (this) {
        VERTICAL_CONTINUOUS -> STORAGE_VERTICAL
        HORIZONTAL_PAGED -> STORAGE_HORIZONTAL
    }

    companion object {
        const val STORAGE_VERTICAL = "vertical_continuous"
        const val STORAGE_HORIZONTAL = "horizontal_paged"

        fun fromStorage(value: String?): PdfReadingMode = when (value?.trim()?.lowercase()) {
            STORAGE_HORIZONTAL, "horizontal", "paged" -> HORIZONTAL_PAGED
            else -> VERTICAL_CONTINUOUS
        }

        fun default(): PdfReadingMode = VERTICAL_CONTINUOUS
    }
}

/** Pure helpers for page index + mode (unit-testable). */
object PdfReadingNavigation {
    fun clampPageIndex(pageIndex: Int, pageCount: Int): Int {
        if (pageCount <= 0) return 0
        return pageIndex.coerceIn(0, pageCount - 1)
    }

    fun previousPage(pageIndex: Int, pageCount: Int): Int =
        clampPageIndex(pageIndex - 1, pageCount)

    fun nextPage(pageIndex: Int, pageCount: Int): Int =
        clampPageIndex(pageIndex + 1, pageCount)

    /** Switching modes keeps the same clamped page. */
    fun pageAfterModeSwitch(currentPageIndex: Int, pageCount: Int): Int =
        clampPageIndex(currentPageIndex, pageCount)
}
