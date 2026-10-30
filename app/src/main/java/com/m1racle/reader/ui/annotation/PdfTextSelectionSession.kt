package com.m1racle.reader.ui.annotation

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.m1racle.reader.pdf.PdfTextSelectionRange

/** A document owns one selection, even when several pages are visible. */
class PdfTextSelectionSession {
    private data class Selection(val page: Int, val range: PdfTextSelectionRange)

    private var selection by mutableStateOf<Selection?>(null)

    fun hasSelection(): Boolean = selection != null

    fun clear() {
        selection = null
    }

    fun clearPage(page: Int) {
        if (selection?.page == page) clear()
    }

    fun setSelection(page: Int, range: PdfTextSelectionRange?) {
        if (range == null) clearPage(page) else selection = Selection(page, range)
    }

    fun restoreAfterCancellation(page: Int, active: PdfTextSelectionRange, previous: PdfTextSelectionRange?) {
        // A disposed or superseded gesture cannot resurrect its old selection.
        if (selection == Selection(page, active)) setSelection(page, previous)
    }

    // Dragging one page's handle does not invalidate every other visible page.
    fun pageState(page: Int): State<PdfTextSelectionRange?> =
        derivedStateOf { selection?.takeIf { it.page == page }?.range }
}
