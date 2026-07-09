package com.markleaf.app.search

/**
 * One hit inside the current document's [com.markleaf.app.markdown.MarkdownBlock] list.
 * [blockIndex] is the index in the blocks list (not LazyList absolute index).
 */
data class SearchMatch(
    val blockIndex: Int,
    val matchStart: Int,
    val matchEnd: Int,
    val preview: String = "",
    /** For [com.markleaf.app.markdown.MarkdownBlock.ListBlock]: item index, else -1. */
    val listItemIndex: Int = -1,
)
