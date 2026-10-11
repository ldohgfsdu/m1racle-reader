package com.m1racle.reader.ui.viewer.components

import androidx.compose.runtime.staticCompositionLocalOf
import com.m1racle.reader.data.local.HighlightEntity
import com.m1racle.reader.ui.annotation.TextSelection

class MarkdownSelectionContext(val block: Int, val plainText: String,
    val highlights: List<HighlightEntity>, val selected: (TextSelection.MarkdownSelection) -> Unit)
val LocalMarkdownSelection = staticCompositionLocalOf<MarkdownSelectionContext?> { null }
val LocalMarkdownInlineOffset = staticCompositionLocalOf { 0 }
