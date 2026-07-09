package com.markleaf.app.markdown

/**
 * Block-level Markdown model for LazyColumn rendering.
 */
sealed class MarkdownBlock {
    data class Heading(
        val level: Int,
        val text: String,
        val anchor: String,
    ) : MarkdownBlock()

    data class Paragraph(
        val text: String,
    ) : MarkdownBlock()

    data class Quote(
        val text: String,
    ) : MarkdownBlock()

    data class CodeBlock(
        val language: String?,
        val code: String,
    ) : MarkdownBlock()

    data class ListBlock(
        val ordered: Boolean,
        val items: List<String>,
    ) : MarkdownBlock()

    /**
     * Pipe table (GFM-style). [alignments] size should match column count.
     */
    data class Table(
        val headers: List<String>,
        val rows: List<List<String>>,
        val alignments: List<TableAlignment>,
    ) : MarkdownBlock()

    data object Divider : MarkdownBlock()
}

enum class TableAlignment {
    Start,
    Center,
    End,
}
