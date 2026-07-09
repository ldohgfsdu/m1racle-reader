package com.markleaf.app.markdown

/**
 * Table-of-contents entry for H1–H3.
 * [blockIndex] points to the Heading in the blocks list.
 * Drawer / jump navigation is reserved for a later step.
 */
data class TocItem(
    val level: Int,
    val title: String,
    val anchor: String,
    val blockIndex: Int,
)
