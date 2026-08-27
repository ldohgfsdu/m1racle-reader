package com.m1racle.reader.search

/**
 * One hit inside the current document's block list.
 *
 * Offsets [matchStart]/[matchEnd] are exclusive-end indices into the **visible**
 * searchable text of that segment (UTF-16 code units, same as [String]).
 * [blockIndex] is the index in the blocks list (not LazyList absolute index).
 */
data class SearchMatch(
    val documentKey: String,
    val blockId: String,
    val blockIndex: Int,
    val matchStart: Int,
    val matchEnd: Int,
    val matchedText: String = "",
    val preview: String = "",
    /** For [com.m1racle.reader.markdown.MarkdownBlock.ListBlock]: item index, else -1. */
    val listItemIndex: Int = -1,
) {
    val start: Int get() = matchStart
    val end: Int get() = matchEnd

    val stableKey: String
        get() = "$documentKey:$blockId:$listItemIndex:$matchStart:$matchEnd"
}
