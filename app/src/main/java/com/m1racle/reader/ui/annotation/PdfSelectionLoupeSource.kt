package com.m1racle.reader.ui.annotation

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Page pixels the selection loupe magnifies.
 *
 * The reader already renders this page; the loupe reuses that bitmap instead of
 * re-rendering PDFium output, so the enlarged glyphs are exactly what the user sees.
 */
data class PdfSelectionLoupeSource(
    val bitmap: ImageBitmap,
    /** Same paper inversion the page image uses, so the loupe matches dark mode. */
    val colorFilter: ColorFilter?,
)
