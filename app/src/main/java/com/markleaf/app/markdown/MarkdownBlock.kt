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

    /**
     * Standalone image line: ![alt](source) or ![alt](source "title")
     */
    data class Image(
        val alt: String,
        val source: String,
        val title: String? = null,
    ) : MarkdownBlock()

    data object Divider : MarkdownBlock()
}

/**
 * Stable LazyColumn [contentType] tag for block recycling.
 * Identity uses list index (document blocks are immutable after open).
 */
fun MarkdownBlock.blockContentType(): String = when (this) {
    is MarkdownBlock.Heading -> "heading"
    is MarkdownBlock.Paragraph -> "paragraph"
    is MarkdownBlock.Quote -> "quote"
    is MarkdownBlock.CodeBlock -> "code"
    is MarkdownBlock.ListBlock -> "list"
    is MarkdownBlock.Table -> "table"
    is MarkdownBlock.Image -> "image"
    MarkdownBlock.Divider -> "divider"
}

/** Session-stable LazyColumn key: blocks never reorder after parse. */
fun MarkdownBlock.blockKey(index: Int): String = "${blockContentType()}-$index"

/** Parse-stable block id; document id is deliberately added by the LazyColumn caller. */
fun MarkdownBlock.blockId(index: Int): String = blockKey(index)

/** Cross-document-safe LazyColumn key. */
fun MarkdownBlock.blockKey(documentId: String, index: Int): String =
    "$documentId:${blockId(index)}"

enum class TableAlignment {
    Start,
    Center,
    End,
}
