package com.m1racle.reader.search

import com.m1racle.reader.markdown.InlineMarkdownParser
import com.m1racle.reader.markdown.MarkdownBlock
import com.m1racle.reader.markdown.blockId

/**
 * Pure visible-text extraction for in-document search (R9 P0).
 * Does not touch Compose, IO, or raw Markdown control characters for inline markup.
 */
object MarkdownSearchTextExtractor {

    /**
     * One searchable segment mapped to a block (and optional list item).
     * [visibleText] uses the same indexing as [String] / Compose (UTF-16 code units).
     */
    data class SearchableSegment(
        val documentKey: String,
        val blockId: String,
        val blockIndex: Int,
        val listItemIndex: Int,
        val visibleText: String,
    )

    /**
     * Expand [blocks] into searchable segments for [documentKey].
     * Skips leading YAML front matter fences when modeled as plain blocks (`---` … `---`).
     */
    fun extract(
        documentKey: String,
        blocks: List<MarkdownBlock>,
        blockIds: List<String> = emptyList(),
    ): List<SearchableSegment> {
        if (blocks.isEmpty()) return emptyList()
        val startIndex = leadingYamlFrontMatterEndExclusive(blocks)
        val out = ArrayList<SearchableSegment>(blocks.size)
        for (i in startIndex until blocks.size) {
            val block = blocks[i]
            val id = blockIds.getOrElse(i) { block.blockId(i) }
            appendSegments(documentKey, id, i, block, out)
        }
        return out
    }

    private fun appendSegments(
        documentKey: String,
        blockId: String,
        blockIndex: Int,
        block: MarkdownBlock,
        out: MutableList<SearchableSegment>,
    ) {
        when (block) {
            is MarkdownBlock.Heading -> {
                val t = plainInline(block.text)
                if (t.isNotEmpty()) {
                    out.add(segment(documentKey, blockId, blockIndex, -1, t))
                }
            }
            is MarkdownBlock.Paragraph -> {
                val t = plainInline(block.text)
                if (t.isNotEmpty()) {
                    out.add(segment(documentKey, blockId, blockIndex, -1, t))
                }
            }
            is MarkdownBlock.Quote -> {
                val t = plainInline(block.text)
                if (t.isNotEmpty()) {
                    out.add(segment(documentKey, blockId, blockIndex, -1, t))
                }
            }
            is MarkdownBlock.CodeBlock -> {
                // Body only — language fence markers never stored in [code].
                if (block.code.isNotEmpty()) {
                    out.add(segment(documentKey, blockId, blockIndex, -1, block.code))
                }
            }
            is MarkdownBlock.ListBlock -> {
                block.items.forEachIndexed { itemIndex, item ->
                    val t = plainInline(item)
                    if (t.isNotEmpty()) {
                        out.add(segment(documentKey, blockId, blockIndex, itemIndex, t))
                    }
                }
            }
            is MarkdownBlock.Table -> {
                // One segment per cell — offsets stay cell-local; no cross-cell matches (V1).
                // Pipe separators never appear in the model.
                var cellOrdinal = 0
                fun addCell(cell: String) {
                    val t = plainInline(cell)
                    if (t.isNotEmpty()) {
                        // Encode cell ordinal in listItemIndex (≥0); -1 reserved for non-list.
                        out.add(segment(documentKey, blockId, blockIndex, cellOrdinal, t))
                    }
                    cellOrdinal++
                }
                block.headers.forEach { addCell(it) }
                block.rows.forEach { row -> row.forEach { addCell(it) } }
            }
            is MarkdownBlock.Image -> {
                // P0: do not index Image blocks.
                // - source URI must never be searchable.
                // - alt is contentDescription (not on-screen text) while loading/success body;
                //   visible Text only on error placeholder.
                // - title only appears in success caption after decode.
                // Search is load-state decoupled, so alt/title would let users hit strings that
                // are often not currently visible. Exclude until a stable always-visible caption.
                Unit
            }
            MarkdownBlock.Divider -> Unit
        }
    }

    private fun segment(
        documentKey: String,
        blockId: String,
        blockIndex: Int,
        listItemIndex: Int,
        visibleText: String,
    ) = SearchableSegment(
        documentKey = documentKey,
        blockId = blockId,
        blockIndex = blockIndex,
        listItemIndex = listItemIndex,
        visibleText = visibleText,
    )

    /** Inline Markdown → visible text (labels only for links; no control markers). */
    fun plainInline(source: String): String = InlineMarkdownParser.toPlainText(source)

    /**
     * If the document begins with a YAML front matter fence modeled as blocks whose
     * plain text is exactly `---`, skip until the closing fence (exclusive end index).
     */
    fun leadingYamlFrontMatterEndExclusive(blocks: List<MarkdownBlock>): Int {
        if (blocks.isEmpty()) return 0
        fun plainOf(b: MarkdownBlock): String = when (b) {
            is MarkdownBlock.Paragraph -> plainInline(b.text).trim()
            is MarkdownBlock.Heading -> plainInline(b.text).trim()
            else -> ""
        }
        if (plainOf(blocks[0]) != "---") return 0
        for (i in 1 until blocks.size) {
            if (plainOf(blocks[i]) == "---") return i + 1
        }
        // Unclosed fence: treat as normal content (do not drop whole document).
        return 0
    }
}
