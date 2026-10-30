package com.m1racle.reader.ui.annotation

import androidx.compose.ui.graphics.Color
import com.m1racle.reader.data.anchor.HighlightStyle
import com.m1racle.reader.design.RadixColors

/** Resolve saved style identities for display without changing stored annotations. */
internal fun readerHighlightColor(style: HighlightStyle): Color = when (style) {
    HighlightStyle.Yellow -> RadixColors.Amber9.copy(alpha = 0.40f)
    HighlightStyle.Green -> RadixColors.Green9.copy(alpha = 0.40f)
    HighlightStyle.Blue -> RadixColors.Blue9.copy(alpha = 0.40f)
    HighlightStyle.Pink -> RadixColors.Pink9.copy(alpha = 0.40f)
    HighlightStyle.Underline -> RadixColors.Blue11
    HighlightStyle.Strike -> RadixColors.Red11
}
