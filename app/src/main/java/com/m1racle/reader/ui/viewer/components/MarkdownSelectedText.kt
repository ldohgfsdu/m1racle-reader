package com.m1racle.reader.ui.viewer.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.text.ClickableText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.m1racle.reader.data.settings.ReaderSettings
import com.m1racle.reader.markdown.InlineMarkdownParser
import com.m1racle.reader.markdown.InlineMarkdownStyles
import com.m1racle.reader.search.BlockSearchHighlights
import com.m1racle.reader.search.SearchHighlightRange
import com.m1racle.reader.search.withSearchHighlights
import com.m1racle.reader.ui.viewer.components.ReaderTypography.scaled

internal data class MarkdownSelectedText(val text: AnnotatedString, val modifier: Modifier, val layout: (TextLayoutResult) -> Unit)

@Composable
internal fun rememberMarkdownSelectedText(searched: AnnotatedString, annotationOffset: Int = LocalMarkdownInlineOffset.current): MarkdownSelectedText {
    val selectionContext = LocalMarkdownSelection.current
    val inlineOffset = annotationOffset
    var textLayout by remember(searched.text) { mutableStateOf<TextLayoutResult?>(null) }
    var previewRange by remember(searched.text) { mutableStateOf<IntRange?>(null) }
    val annotated = remember(searched, selectionContext?.highlights, inlineOffset, previewRange) {
        if (selectionContext?.highlights.isNullOrEmpty() && previewRange == null) return@remember searched
        val builder = AnnotatedString.Builder(searched)
        selectionContext?.highlights?.forEach { highlight ->
            val anchor = com.m1racle.reader.data.anchor.AnchorCodec.decode(highlight.anchor) as? com.m1racle.reader.data.anchor.DocumentAnchor.MarkdownBlock
            val start = ((anchor?.charStart ?: 0) - inlineOffset).coerceIn(0, searched.length)
            val end = ((anchor?.charEnd ?: selectionContext.plainText.length) - inlineOffset).coerceIn(0, searched.length)
            val kind = com.m1racle.reader.data.anchor.HighlightStyle.fromStorage(highlight.style)
            val span = when (kind) {
                com.m1racle.reader.data.anchor.HighlightStyle.Underline -> SpanStyle(textDecoration = TextDecoration.Underline, color = Color(kind.argb))
                com.m1racle.reader.data.anchor.HighlightStyle.Strike -> SpanStyle(textDecoration = TextDecoration.LineThrough, color = Color(kind.argb))
                else -> SpanStyle(background = Color(kind.argb))
            }
            if (end > start) builder.addStyle(span, start, end)
        }
        previewRange?.let { range -> if (!range.isEmpty()) builder.addStyle(SpanStyle(background = Color(0x6653B8FF)), range.first, range.last + 1) }
        builder.toAnnotatedString()
    }
    val currentSelection by rememberUpdatedState(selectionContext)
    val selectionModifier = if (selectionContext == null) Modifier else Modifier.pointerInput(searched.text, inlineOffset) {
        var start = 0
        var pointer = Offset.Zero
        fun update() {
            val layout = textLayout ?: return
            if (searched.isEmpty()) return
            val end = layout.getOffsetForPosition(pointer).coerceIn(0, searched.lastIndex)
            var lower = minOf(start, end)
            var upper = maxOf(start, end) + 1
            if (lower > 0 && Character.isLowSurrogate(searched.text[lower])) lower--
            if (upper < searched.length && Character.isHighSurrogate(searched.text[upper - 1])) upper++
            previewRange = lower until upper
        }
        detectDragGesturesAfterLongPress(onDragStart = { position ->
            pointer = position
            start = textLayout?.getOffsetForPosition(position)?.coerceIn(0, searched.length.coerceAtLeast(1) - 1) ?: 0
            update()
        }, onDragCancel = { previewRange = null }, onDragEnd = {
            previewRange?.let { range -> currentSelection?.selected?.invoke(com.m1racle.reader.ui.annotation.TextSelection.MarkdownSelection(
                currentSelection!!.block, range.first + inlineOffset, range.last + 1 + inlineOffset, searched.text.substring(range))) }
            previewRange = null
        }) { change, _ -> change.consume(); pointer = change.position; update() }
    }
    return MarkdownSelectedText(annotated, selectionModifier) { textLayout = it }
}
