package com.markleaf.app.markdown

/**
 * Lightweight block-level Markdown parser for MarkLeaf MVP.
 * No third-party library; intentionally limited syntax.
 */
object MarkdownParser {

    data class ParseResult(
        val blocks: List<MarkdownBlock>,
        val toc: List<TocItem>,
    )

    private val headingRegex = Regex("""^(#{1,6})\s+(.*)$""")
    private val unorderedListRegex = Regex("""^([-*+])\s+(.*)$""")
    private val orderedListRegex = Regex("""^(\d+)\.\s+(.*)$""")
    private val quoteRegex = Regex("""^>\s?(.*)$""")
    private val fenceOpenRegex = Regex("""^```(\S*)\s*$""")
    private val fenceCloseRegex = Regex("""^```\s*$""")
    private val dividerRegex = Regex("""^\s*([-*_])\1{2,}\s*$""")

    fun parse(source: String): ParseResult {
        val normalized = source
            .replace("\r\n", "\n")
            .replace('\r', '\n')
        val lines = normalized.split('\n')

        val blocks = mutableListOf<MarkdownBlock>()
        val toc = mutableListOf<TocItem>()
        val anchorCounts = mutableMapOf<String, Int>()

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            // Blank lines separate blocks.
            if (trimmed.isEmpty()) {
                i++
                continue
            }

            // Fenced code block.
            val fenceMatch = fenceOpenRegex.matchEntire(trimmed)
            if (fenceMatch != null) {
                val language = fenceMatch.groupValues[1].ifBlank { null }
                val codeLines = mutableListOf<String>()
                i++
                var closed = false
                while (i < lines.size) {
                    if (fenceCloseRegex.matchEntire(lines[i].trim()) != null) {
                        closed = true
                        i++
                        break
                    }
                    codeLines.add(lines[i])
                    i++
                }
                // Unclosed fence: consume rest of file (no crash).
                if (!closed) {
                    // already consumed to end
                }
                blocks.add(
                    MarkdownBlock.CodeBlock(
                        language = language,
                        code = codeLines.joinToString("\n"),
                    ),
                )
                continue
            }

            // Heading.
            val headingMatch = headingRegex.matchEntire(trimmed)
            if (headingMatch != null) {
                val level = headingMatch.groupValues[1].length
                val text = headingMatch.groupValues[2].trimEnd()
                val anchor = makeAnchor(text, anchorCounts)
                val blockIndex = blocks.size
                blocks.add(
                    MarkdownBlock.Heading(
                        level = level,
                        text = text,
                        anchor = anchor,
                    ),
                )
                if (level in 1..3) {
                    toc.add(
                        TocItem(
                            level = level,
                            title = text,
                            anchor = anchor,
                            blockIndex = blockIndex,
                        ),
                    )
                }
                i++
                continue
            }

            // Divider.
            if (dividerRegex.matchEntire(trimmed) != null) {
                blocks.add(MarkdownBlock.Divider)
                i++
                continue
            }

            // Quote block (merge consecutive > lines).
            val quoteMatch = quoteRegex.matchEntire(line)
            if (quoteMatch != null) {
                val quoteLines = mutableListOf(quoteMatch.groupValues[1])
                i++
                while (i < lines.size) {
                    val next = lines[i]
                    if (next.trim().isEmpty()) break
                    val qm = quoteRegex.matchEntire(next)
                    if (qm == null) break
                    quoteLines.add(qm.groupValues[1])
                    i++
                }
                blocks.add(
                    MarkdownBlock.Quote(
                        text = quoteLines.joinToString("\n").trimEnd(),
                    ),
                )
                continue
            }

            // Unordered list.
            if (unorderedListRegex.matchEntire(trimmed) != null) {
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val t = lines[i].trim()
                    if (t.isEmpty()) break
                    val m = unorderedListRegex.matchEntire(t) ?: break
                    items.add(m.groupValues[2])
                    i++
                }
                if (items.isNotEmpty()) {
                    blocks.add(
                        MarkdownBlock.ListBlock(
                            ordered = false,
                            items = items,
                        ),
                    )
                }
                continue
            }

            // Ordered list.
            if (orderedListRegex.matchEntire(trimmed) != null) {
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val t = lines[i].trim()
                    if (t.isEmpty()) break
                    val m = orderedListRegex.matchEntire(t) ?: break
                    items.add(m.groupValues[2])
                    i++
                }
                if (items.isNotEmpty()) {
                    blocks.add(
                        MarkdownBlock.ListBlock(
                            ordered = true,
                            items = items,
                        ),
                    )
                }
                continue
            }

            // Paragraph: merge consecutive ordinary lines.
            val paraLines = mutableListOf(line.trimEnd())
            i++
            while (i < lines.size) {
                val next = lines[i]
                val nextTrim = next.trim()
                if (nextTrim.isEmpty()) break
                if (isBlockStart(next, nextTrim)) break
                paraLines.add(next.trimEnd())
                i++
            }
            val paraText = paraLines.joinToString("\n").trim()
            if (paraText.isNotEmpty()) {
                blocks.add(MarkdownBlock.Paragraph(text = paraText))
            }
        }

        return ParseResult(blocks = blocks, toc = toc)
    }

    private fun isBlockStart(raw: String, trimmed: String): Boolean {
        if (fenceOpenRegex.matchEntire(trimmed) != null) return true
        if (headingRegex.matchEntire(trimmed) != null) return true
        if (dividerRegex.matchEntire(trimmed) != null) return true
        if (quoteRegex.matchEntire(raw) != null) return true
        if (unorderedListRegex.matchEntire(trimmed) != null) return true
        if (orderedListRegex.matchEntire(trimmed) != null) return true
        return false
    }

    private fun makeAnchor(text: String, counts: MutableMap<String, Int>): String {
        val base = text
            .lowercase()
            .replace(Regex("""[^\p{L}\p{N}\s-]"""), "")
            .trim()
            .replace(Regex("""\s+"""), "-")
            .ifBlank { "heading" }
        val n = counts[base] ?: 0
        counts[base] = n + 1
        return if (n == 0) base else "$base-$n"
    }
}
