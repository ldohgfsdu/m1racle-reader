package com.markleaf.app.search

import com.markleaf.app.markdown.MarkdownBlock
import java.util.Locale

/**
 * Lightweight in-document search over Markdown blocks. No index, no extra libraries.
 */
object DocumentSearch {

    fun search(blocks: List<MarkdownBlock>, rawQuery: String): List<SearchMatch> {
        val query = rawQuery.trim()
        if (query.isEmpty() || blocks.isEmpty()) return emptyList()

        val matches = mutableListOf<SearchMatch>()
        blocks.forEachIndexed { blockIndex, block ->
            when (block) {
                is MarkdownBlock.Heading ->
                    collectMatches(block.text, blockIndex, listItemIndex = -1, query, matches)
                is MarkdownBlock.Paragraph ->
                    collectMatches(block.text, blockIndex, listItemIndex = -1, query, matches)
                is MarkdownBlock.Quote ->
                    collectMatches(block.text, blockIndex, listItemIndex = -1, query, matches)
                is MarkdownBlock.CodeBlock ->
                    collectMatches(block.code, blockIndex, listItemIndex = -1, query, matches)
                is MarkdownBlock.ListBlock -> {
                    block.items.forEachIndexed { itemIndex, item ->
                        collectMatches(item, blockIndex, listItemIndex = itemIndex, query, matches)
                    }
                }
                is MarkdownBlock.Table -> {
                    block.headers.forEach { cell ->
                        collectMatches(cell, blockIndex, listItemIndex = -1, query, matches)
                    }
                    block.rows.forEach { row ->
                        row.forEach { cell ->
                            collectMatches(cell, blockIndex, listItemIndex = -1, query, matches)
                        }
                    }
                }
                is MarkdownBlock.Image -> {
                    collectMatches(block.alt, blockIndex, listItemIndex = -1, query, matches)
                    block.title?.let {
                        collectMatches(it, blockIndex, listItemIndex = -1, query, matches)
                    }
                    collectMatches(block.source, blockIndex, listItemIndex = -1, query, matches)
                }
                MarkdownBlock.Divider -> Unit
            }
        }
        return matches
    }

    private fun collectMatches(
        text: String,
        blockIndex: Int,
        listItemIndex: Int,
        query: String,
        out: MutableList<SearchMatch>,
    ) {
        if (text.isEmpty()) return
        val haystack = text.lowercase(Locale.getDefault())
        val needle = query.lowercase(Locale.getDefault())
        if (needle.isEmpty()) return

        var from = 0
        while (from <= haystack.length - needle.length) {
            val idx = haystack.indexOf(needle, from)
            if (idx < 0) break
            val end = idx + needle.length
            out.add(
                SearchMatch(
                    blockIndex = blockIndex,
                    matchStart = idx,
                    matchEnd = end,
                    preview = previewAround(text, idx, end),
                    listItemIndex = listItemIndex,
                ),
            )
            from = idx + needle.length.coerceAtLeast(1)
        }
    }

    private fun previewAround(text: String, start: Int, end: Int, radius: Int = 24): String {
        val from = (start - radius).coerceAtLeast(0)
        val to = (end + radius).coerceAtMost(text.length)
        val prefix = if (from > 0) "…" else ""
        val suffix = if (to < text.length) "…" else ""
        return prefix + text.substring(from, to).replace('\n', ' ') + suffix
    }
}
