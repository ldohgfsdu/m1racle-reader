package com.m1racle.reader.ui.pdf

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import com.m1racle.reader.ui.material.QaGlass
import com.m1racle.reader.ui.material.qaPdfInvertPages
import com.m1racle.reader.ui.theme.LocalReaderDarkTheme

/** Same transform for the preview and every high-resolution tile; annotations stay above it. */
internal val PdfInversionFilter = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
    -1f, 0f, 0f, 0f, 255f,
    0f, -1f, 0f, 0f, 255f,
    0f, 0f, -1f, 0f, 255f,
    0f, 0f, 0f, 1f, 0f,
)))

@Composable
internal fun pdfPaperFilter(): ColorFilter? =
    // QA-only "PDF 夜间反色" toggle; release selection is fixed at default (invert on).
    if (qaPdfInvertPages(LocalReaderDarkTheme.current, QaGlass.selection)) PdfInversionFilter else null
