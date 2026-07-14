package com.markleaf.app.markdown

/**
 * Lightweight block-level Markdown parser for MarkLeaf MVP.
 * No third-party library; intentionally limited syntax.
 */
object MarkdownParser {

    data class ParseResult(
        val blocks: List<MarkdownBlock>,
        val toc: List<TocItem>,
        val anchorRegistry: AnchorRegistry,
        val blockIds: List<String>,
    )

    private val headingRegex = Regex("""^(#{1,6})\s+(.*)$""")
    private val unorderedListRegex = Regex("""^([-*+])\s+(.*)$""")
    private val orderedListRegex = Regex("""^(\d+)\.\s+(.*)$""")
    private val quoteRegex = Regex("""^>\s?(.*)$""")
    private val fenceOpenRegex = Regex("""^```(\S*)\s*$""")
    private val fenceCloseRegex = Regex("""^```\s*$""")
    private val dividerRegex = Regex("""^\s*([-*_])\1{2,}\s*$""")
    private val separatorCellRegex = Regex("""^:?-{3,}:?$""")
    /** Whole-line image only: ![alt](source) optional "title" */
    private val imageLineRegex = Regex(
        """^!\[([^]]*)]\((\S+?)(?:\s+"([^"]*)")?\s*\)$""",
    )

    fun parse(source: String): ParseResult = MarkdownPerformance.trace("MarkdownParse") {
        MarkdownPerformance.recordParse()
        val normalized = stripHtmlComments(source)
            .replace("\r\n", "\n")
            .replace('\r', '\n')
        val lines = normalized.split('\n')

        val blocks = mutableListOf<MarkdownBlock>()
        val toc = mutableListOf<TocItem>()
        val anchorTargets = mutableListOf<MarkdownAnchorTarget>()
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

            // Fenced code block (pipes inside never become tables).
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
                if (!closed) {
                    // Unclosed fence: consume rest of file (no crash).
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
                anchorTargets += MarkdownAnchorTarget(
                    anchorId = anchor,
                    blockIndex = blockIndex,
                    headingIndex = anchorTargets.size,
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

            // Divider (plain --- without pipe table separators).
            if (dividerRegex.matchEntire(trimmed) != null) {
                blocks.add(MarkdownBlock.Divider)
                i++
                continue
            }

            // Standalone image line (not inside fenced code).
            val imageMatch = imageLineRegex.matchEntire(trimmed)
            if (imageMatch != null) {
                val alt = imageMatch.groupValues[1]
                val source = imageMatch.groupValues[2].trim()
                val title = imageMatch.groupValues[3].ifBlank { null }
                if (source.isEmpty()) {
                    blocks.add(
                        MarkdownBlock.Paragraph(
                            text = if (alt.isNotBlank()) alt else "![ ]()",
                        ),
                    )
                } else {
                    blocks.add(
                        MarkdownBlock.Image(
                            alt = alt,
                            source = source,
                            title = title,
                        ),
                    )
                }
                i++
                continue
            }

            // Pipe table: header + separator (+ optional body rows).
            if (i + 1 < lines.size && looksLikeTableStart(trimmed, lines[i + 1].trim())) {
                val consumed = parseTable(lines, i)
                if (consumed != null) {
                    blocks.add(consumed.table)
                    i = consumed.nextIndex
                    continue
                }
                // Fall through to paragraph on invalid table shape.
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
                // Don't swallow a table that starts on the next two lines.
                if (i + 1 < lines.size && looksLikeTableStart(nextTrim, lines[i + 1].trim())) break
                paraLines.add(next.trimEnd())
                i++
            }
            val paraText = paraLines.joinToString("\n").trim()
            if (paraText.isNotEmpty()) {
                blocks.add(MarkdownBlock.Paragraph(text = paraText))
            }
        }

        ParseResult(
            blocks = blocks.toList(),
            toc = toc.toList(),
            anchorRegistry = AnchorRegistry.fromTargets(anchorTargets),
            blockIds = blocks.mapIndexed { index, block -> block.blockId(index) },
        )
    }

    private data class TableParse(
        val table: MarkdownBlock.Table,
        val nextIndex: Int,
    )

    private fun looksLikeTableStart(headerLine: String, separatorLine: String): Boolean {
        if (headerLine.isEmpty() || separatorLine.isEmpty()) return false
        if (!headerLine.contains('|') && !separatorLine.contains('|')) return false
        // Header must not be a separator-only row.
        if (isTableSeparatorLine(headerLine)) return false
        return isTableSeparatorLine(separatorLine)
    }

    private fun parseTable(lines: List<String>, start: Int): TableParse? {
        return try {
            val headerCells = splitTableCells(lines[start])
            if (headerCells.isEmpty()) return null
            val sepCells = splitTableCells(lines[start + 1])
            if (sepCells.isEmpty() || !sepCells.all { isSeparatorCell(it) }) return null

            val columnCount = maxOf(headerCells.size, sepCells.size).coerceAtLeast(1)
            val headers = padCells(headerCells, columnCount)
            val alignments = padAlignments(
                sepCells.map { parseAlignment(it) },
                columnCount,
            )

            val rows = mutableListOf<List<String>>()
            var i = start + 2
            while (i < lines.size) {
                val raw = lines[i]
                val trimmed = raw.trim()
                if (trimmed.isEmpty()) break
                if (isBlockStart(raw, trimmed) && !trimmed.contains('|')) break
                // Stop if next section is clearly another table header (header+sep pair).
                if (i + 1 < lines.size &&
                    looksLikeTableStart(trimmed, lines[i + 1].trim()) &&
                    isTableSeparatorLine(lines[i + 1].trim())
                ) {
                    // Body row that looks like table start is still a row if it's only one line;
                    // only break when this is a NEW table (header + sep). So if current is header-like
                    // and next is sep, break to let outer loop re-parse.
                    if (!isTableSeparatorLine(trimmed)) {
                        break
                    }
                }
                if (!trimmed.contains('|') && splitTableCells(trimmed).size <= 1) {
                    // A plain paragraph line without pipes ends the table.
                    break
                }
                if (isTableSeparatorLine(trimmed)) {
                    // Extra separator: ignore and stop.
                    break
                }
                val cells = padCells(splitTableCells(trimmed), columnCount)
                rows.add(cells)
                i++
            }

            TableParse(
                table = MarkdownBlock.Table(
                    headers = headers,
                    rows = rows,
                    alignments = alignments,
                ),
                nextIndex = i,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun isTableSeparatorLine(line: String): Boolean {
        val cells = splitTableCells(line)
        if (cells.isEmpty()) return false
        return cells.all { isSeparatorCell(it) }
    }

    private fun isSeparatorCell(cell: String): Boolean =
        separatorCellRegex.matches(cell.trim())

    private fun parseAlignment(cell: String): TableAlignment {
        val t = cell.trim()
        val left = t.startsWith(':')
        val right = t.endsWith(':')
        return when {
            left && right -> TableAlignment.Center
            right -> TableAlignment.End
            else -> TableAlignment.Start
        }
    }

    /**
     * Split a table row into cells. Supports optional leading/trailing pipes.
     */
    private fun splitTableCells(line: String): List<String> {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (!trimmed.contains('|')) {
            // Single-column table rows are rare; treat as one cell only if separator-like or explicit.
            return listOf(trimmed)
        }
        var s = trimmed
        if (s.startsWith("|")) s = s.drop(1)
        if (s.endsWith("|")) s = s.dropLast(1)
        return s.split('|').map { it.trim() }
    }

    private fun padCells(cells: List<String>, count: Int): List<String> {
        if (cells.size >= count) return cells
        return cells + List(count - cells.size) { "" }
    }

    private fun padAlignments(
        alignments: List<TableAlignment>,
        count: Int,
    ): List<TableAlignment> {
        if (alignments.size >= count) return alignments.take(count)
        return alignments + List(count - alignments.size) { TableAlignment.Start }
    }

    /**
     * Remove HTML comments so they never render as paragraphs.
     * Non-greedy; supports multi-line comments. Code fences still parse after strip
     * (comments inside fences are rare in packages; accepted MVP tradeoff).
     */
    fun stripHtmlComments(source: String): String {
        if (!source.contains("<!--")) return source
        return HTML_COMMENT_REGEX.replace(source, "")
    }

    private val HTML_COMMENT_REGEX = Regex("""<!--[\s\S]*?-->""")

    private fun isBlockStart(raw: String, trimmed: String): Boolean {
        if (fenceOpenRegex.matchEntire(trimmed) != null) return true
        if (headingRegex.matchEntire(trimmed) != null) return true
        if (dividerRegex.matchEntire(trimmed) != null) return true
        if (imageLineRegex.matchEntire(trimmed) != null) return true
        if (quoteRegex.matchEntire(raw) != null) return true
        if (unorderedListRegex.matchEntire(trimmed) != null) return true
        if (orderedListRegex.matchEntire(trimmed) != null) return true
        return false
    }

    private fun makeAnchor(text: String, counts: MutableMap<String, Int>): String {
        val base = MarkdownAnchorSlug.normalize(text)
        val n = counts[base] ?: 0
        counts[base] = n + 1
        return if (n == 0) base else "$base-${n + 1}"
    }
}
