package com.markleaf.app.markdown

/**
 * Table-of-contents entry for H1–H3.
 * [title] is plain display text (inline Markdown stripped, e.g. link labels only).
 * [blockIndex] points to the Heading in the blocks list.
 * [anchor] is the stable slug used for jump / active-row matching.
 */
data class TocItem(
    val level: Int,
    val title: String,
    val anchor: String,
    val blockIndex: Int,
)
