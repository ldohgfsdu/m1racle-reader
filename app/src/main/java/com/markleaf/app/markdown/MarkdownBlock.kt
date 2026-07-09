package com.markleaf.app.markdown

/**
 * Block-level Markdown model for LazyColumn rendering.
 * Inline styles (bold/italic/links) are out of scope for this step.
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

    data object Divider : MarkdownBlock()
}
