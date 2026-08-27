package com.m1racle.reader.search

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle

/**
 * One UTF-16 half-open highlight range on a single searchable text unit
 * (block body, list item, or table cell).
 */
data class SearchHighlightRange(
    val start: Int,
    val end: Int,
    val isActive: Boolean,
    /** List item index or table cell ordinal; -1 for whole-block text. */
    val listItemIndex: Int = -1,
)

/**
 * Local highlight payload for one LazyColumn block — never the full document matches list.
 *
 * Ranges are pre-bucketed so composition does not run [List.filter] per item/cell.
 */
data class BlockSearchHighlights(
    val blockIndex: Int,
    /** Whole-block ranges (paragraph/heading/code/quote); listItemIndex < 0. */
    val wholeBlockRanges: List<SearchHighlightRange> = emptyList(),
    /** listItemIndex / cell ordinal → ranges (list items and table cells). */
    val byListItem: Map<Int, List<SearchHighlightRange>> = emptyMap(),
) {
    fun forListItem(itemIndex: Int): List<SearchHighlightRange> =
        byListItem[itemIndex] ?: EmptyRanges

    fun forCell(cellOrdinal: Int): List<SearchHighlightRange> =
        byListItem[cellOrdinal] ?: EmptyRanges

    fun forWholeBlock(): List<SearchHighlightRange> = wholeBlockRanges

    val isEmpty: Boolean
        get() = wholeBlockRanges.isEmpty() && byListItem.isEmpty()

    companion object {
        /** Shared empty payload — single instance for stable compose props. */
        val None: BlockSearchHighlights? = null

        val EmptyRanges: List<SearchHighlightRange> = emptyList()
    }
}

/**
 * Group search hits by [SearchMatch.blockIndex]. Call only when [matches] changes —
 * not on every active-index change.
 */
fun groupMatchesByBlockIndex(matches: List<SearchMatch>): Map<Int, List<SearchMatch>> {
    if (matches.isEmpty()) return emptyMap()
    return matches.groupBy { it.blockIndex }
}

/**
 * Build per-block highlight ranges from a pre-grouped map + current active match.
 * Does not re-scan the document; O(matches in block) only.
 *
 * Returns null when there is nothing to paint (callers must treat null as zero-cost).
 */
fun blockHighlightsFromGrouped(
    blockIndex: Int,
    blockMatches: List<SearchMatch>?,
    activeStableKey: String?,
): BlockSearchHighlights? {
    if (blockMatches.isNullOrEmpty()) return null
    val whole = ArrayList<SearchHighlightRange>(4)
    val byItem = HashMap<Int, MutableList<SearchHighlightRange>>(4)
    for (m in blockMatches) {
        val range = SearchHighlightRange(
            start = m.matchStart,
            end = m.matchEnd,
            isActive = activeStableKey != null && m.stableKey == activeStableKey,
            listItemIndex = m.listItemIndex,
        )
        if (m.listItemIndex < 0) {
            whole.add(range)
        } else {
            byItem.getOrPut(m.listItemIndex) { ArrayList(2) }.add(range)
        }
    }
    return BlockSearchHighlights(
        blockIndex = blockIndex,
        wholeBlockRanges = if (whole.isEmpty()) BlockSearchHighlights.EmptyRanges else whole,
        byListItem = if (byItem.isEmpty()) emptyMap() else byItem,
    )
}

/**
 * Validate [start, end) against [length]. Illegal ranges are skipped (no coerce-to-wrong).
 */
fun isValidHighlightRange(start: Int, end: Int, length: Int): Boolean {
    if (start < 0 || end > length || start >= end) return false
    return true
}

/**
 * Overlay search backgrounds on an existing styled [AnnotatedString].
 * Normal matches first, active last so active wins visually when overlapping.
 * Preserves string annotations (e.g. links) and existing span styles.
 *
 * **Zero-cost empty path:** returns the same instance when [ranges] is empty.
 */
fun AnnotatedString.withSearchHighlights(
    ranges: List<SearchHighlightRange>,
    normalColor: Color,
    activeColor: Color,
): AnnotatedString {
    // Identity short-circuit — no Builder, no span copy, no loops.
    if (ranges.isEmpty() || length == 0) return this
    val builder = AnnotatedString.Builder(this)
    fun add(range: SearchHighlightRange, color: Color) {
        if (!isValidHighlightRange(range.start, range.end, length)) return
        builder.addStyle(SpanStyle(background = color), range.start, range.end)
    }
    for (r in ranges) {
        if (!r.isActive) add(r, normalColor)
    }
    for (r in ranges) {
        if (r.isActive) add(r, activeColor)
    }
    return builder.toAnnotatedString()
}
