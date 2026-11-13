package com.m1racle.reader.ui.annotation

import androidx.compose.ui.graphics.Color
import com.m1racle.reader.design.RadixColors

/** Live selection fill; blended Multiply on white paper and Screen on inverted paper. */
internal val PdfSelectionFill: Color = RadixColors.Blue9.copy(alpha = 0.30f)

/** Opaque, screen-fixed Android-style selection handles. */
internal val PdfSelectionHandleFill: Color = RadixColors.Blue9

/** Loupe character boundaries. */
internal val PdfSelectionBoundary: Color = RadixColors.Blue11
