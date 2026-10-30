package com.m1racle.reader.ui.viewer.components

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.m1racle.reader.data.local.HighlightEntity
import com.m1racle.reader.ui.annotation.TextSelection

class MarkdownSelectionContext(
    val block: Int,
    val plainText: String,
    val highlights: List<HighlightEntity>,
    /** Called only when the user explicitly chooses the highlight action. */
    val onHighlight: ((TextSelection.MarkdownSelection) -> Unit)?,
    /** Opens the existing note editor directly when the user chooses the note action. */
    val onNote: ((TextSelection.MarkdownSelection) -> Unit)? = null,
)
val LocalMarkdownSelection = staticCompositionLocalOf<MarkdownSelectionContext?> { null }
val LocalMarkdownInlineOffset = staticCompositionLocalOf { 0 }

internal data class MarkdownSelectionOwner(
    val block: Int,
    val inlineOffset: Int,
)

internal data class MarkdownSelectionEndpoints(
    val owner: MarkdownSelectionOwner,
    val anchor: Int,
    val focus: Int,
)

/** Document-scoped selection kept outside the lazy item so scrolling does not recreate it. */
@Stable
internal class MarkdownSelectionSession {
    var endpoints by mutableStateOf<MarkdownSelectionEndpoints?>(null)
        private set

    val isActive: Boolean get() = endpoints != null

    fun begin(owner: MarkdownSelectionOwner, offset: Int) {
        endpoints = MarkdownSelectionEndpoints(owner, anchor = offset, focus = offset)
    }

    fun moveAnchor(owner: MarkdownSelectionOwner, offset: Int) {
        endpoints = endpoints
            ?.takeIf { it.owner == owner }
            ?.copy(anchor = offset)
    }

    fun moveFocus(owner: MarkdownSelectionOwner, offset: Int) {
        endpoints = endpoints
            ?.takeIf { it.owner == owner }
            ?.copy(focus = offset)
    }

    fun clear() {
        endpoints = null
    }

    fun restore(previous: MarkdownSelectionEndpoints?) {
        endpoints = previous
    }
}

internal val LocalMarkdownSelectionSession = staticCompositionLocalOf<MarkdownSelectionSession?> { null }

/**
 * Convert layout cursor offsets into an inclusive UTF-16 text range.
 *
 * The drag endpoint is clamped to the last character, so dragging beyond a line end
 * still includes that character. Surrogate pairs are treated as one selectable unit.
 */
internal fun markdownSelectionRange(
    text: String,
    anchorOffset: Int,
    dragOffset: Int,
): IntRange {
    if (text.isEmpty()) return 0 until 0

    fun characterAt(rawOffset: Int): Int {
        val offset = rawOffset.coerceIn(0, text.lastIndex)
        return if (
            offset > 0 &&
            Character.isLowSurrogate(text[offset]) &&
            Character.isHighSurrogate(text[offset - 1])
        ) {
            offset - 1
        } else {
            offset
        }
    }

    val anchor = characterAt(anchorOffset)
    val drag = characterAt(dragOffset)
    val first = minOf(anchor, drag)
    var endExclusive = maxOf(anchor, drag) + 1
    if (
        endExclusive < text.length &&
        Character.isHighSurrogate(text[endExclusive - 1]) &&
        Character.isLowSurrogate(text[endExclusive])
    ) {
        endExclusive++
    }
    return first until endExclusive
}

/** Map a range from one rendered inline fragment back into the block's canonical text. */
internal fun MarkdownSelectionContext.selectionFor(
    range: IntRange,
    inlineOffset: Int,
): TextSelection.MarkdownSelection? {
    if (range.isEmpty() || plainText.isEmpty()) return null
    val start = (range.first + inlineOffset).coerceIn(0, plainText.length)
    val end = (range.last + 1 + inlineOffset).coerceIn(start, plainText.length)
    if (end <= start) return null
    return TextSelection.MarkdownSelection(
        blockIndex = block,
        charStart = start,
        charEnd = end,
        excerpt = plainText.substring(start, end),
    )
}
