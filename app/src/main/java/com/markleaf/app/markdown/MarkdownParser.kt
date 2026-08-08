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
        /**
         * Empty HTML anchors that appeared after the last renderable block.
         * The registry only maps to block tops, so EOF anchors cannot be placed
         * at document end / last-block bottom without mis-jumping to the last
         * block top — they are intentionally ignored (fail-soft).
         */
        val ignoredTrailingHtmlAnchors: List<String> = emptyList(),
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

    /**
     * Whole-line empty HTML anchor only, e.g. `<a id="x"></a>` / `<a name='y'></a>` /
     * self-closing `<a id="x"/>`. Case-insensitive; allows attribute whitespace / quote style.
     * Body content is not accepted (those stay as ordinary paragraph text).
     */
    private val emptyHtmlAnchorLineRegex = Regex(
        """^<a\s+([^>]+?)(?:\s*/>|>\s*</a\s*>)$""",
        RegexOption.IGNORE_CASE,
    )

    /** Captures id= or name= values (double / single / unquoted). Prefer id when both present. */
    private val htmlAnchorIdOrNameAttrRegex = Regex(
        """\b(id|name)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+))""",
        RegexOption.IGNORE_CASE,
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
        /** Empty HTML anchors waiting for the next renderable block (prefer heading). */
        val pendingHtmlAnchors = mutableListOf<String>()

        fun registerAnchor(anchorId: String, blockIndex: Int) {
            val id = anchorId.trim()
            if (id.isEmpty()) return
            anchorTargets += MarkdownAnchorTarget(
                anchorId = id,
                blockIndex = blockIndex,
                headingIndex = anchorTargets.size,
            )
            // Also index the slug form so authored #fragments resolve like headings.
            val slug = MarkdownAnchorSlug.normalize(id)
            if (slug.isNotEmpty() && slug != id) {
                anchorTargets += MarkdownAnchorTarget(
                    anchorId = slug,
                    blockIndex = blockIndex,
                    headingIndex = anchorTargets.size,
                )
            }
        }

        fun flushPendingHtmlAnchors(blockIndex: Int) {
            if (pendingHtmlAnchors.isEmpty()) return
            for (id in pendingHtmlAnchors) {
                registerAnchor(id, blockIndex)
            }
            pendingHtmlAnchors.clear()
        }

        fun addBlock(block: MarkdownBlock) {
            val index = blocks.size
            blocks.add(block)
            flushPendingHtmlAnchors(index)
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            // Blank lines separate blocks.
            if (trimmed.isEmpty()) {
                i++
                continue
            }

            // Standalone empty HTML anchors: invisible, no spacing; bind to next block.
            val htmlAnchorId = matchEmptyHtmlAnchorId(trimmed)
            if (htmlAnchorId != null) {
                if (htmlAnchorId.isNotEmpty()) {
                    pendingHtmlAnchors += htmlAnchorId
                }
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
                addBlock(
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
                // TOC / slug use plain label text so ## [德篇](#de-pian) → title "德篇".
                val plainTitle = normalizeHeadingPlainTitle(text)
                val anchor = makeAnchor(
                    text = plainTitle.ifBlank { text },
                    counts = anchorCounts,
                )
                val blockIndex = blocks.size
                addBlock(
                    MarkdownBlock.Heading(
                        level = level,
                        // Keep raw source so body still renders inline links / emphasis.
                        text = text,
                        anchor = anchor,
                    ),
                )
                // Heading slug after pending HTML anchors so both ids map to this block.
                registerAnchor(anchor, blockIndex)
                // Also register authored #fragments inside the heading label.
                for (fragment in inPageFragmentsFromInline(text)) {
                    if (fragment != anchor) {
                        registerAnchor(fragment, blockIndex)
                    }
                }
                if (level in 1..3) {
                    toc.add(
                        TocItem(
                            level = level,
                            title = plainTitle.ifBlank { text },
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
                addBlock(MarkdownBlock.Divider)
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
                    addBlock(
                        MarkdownBlock.Paragraph(
                            text = if (alt.isNotBlank()) alt else "![ ]()",
                        ),
                    )
                } else {
                    addBlock(
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
                    addBlock(consumed.table)
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
                addBlock(
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
                    addBlock(
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
                    addBlock(
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
                addBlock(MarkdownBlock.Paragraph(text = paraText))
            }
        }

        // EOF-only empty HTML anchors: no next renderable block.
        // Registry can only address a block *top*. Binding to lastIndex would
        // jump to the last block's top (wrong vs document end / last-block
        // bottom). Prefer fail-soft: drop + surface ids for logging.
        val ignoredTrailing = if (pendingHtmlAnchors.isNotEmpty()) {
            pendingHtmlAnchors.toList().also { pendingHtmlAnchors.clear() }
        } else {
            emptyList()
        }

        ParseResult(
            blocks = blocks.toList(),
            toc = toc.toList(),
            anchorRegistry = AnchorRegistry.fromTargets(anchorTargets),
            blockIds = blocks.mapIndexed { index, block -> block.blockId(index) },
            ignoredTrailingHtmlAnchors = ignoredTrailing,
        )
    }

    /**
     * If [trimmed] is a whole-line empty `<a id|name=…></a>` (or self-closing),
     * return the id/name value (may be empty string). Prefer `id` over `name`.
     * Null if not an empty anchor line.
     */
    internal fun matchEmptyHtmlAnchorId(trimmed: String): String? {
        val match = emptyHtmlAnchorLineRegex.matchEntire(trimmed) ?: return null
        val attrs = match.groupValues[1]
        var idValue: String? = null
        var nameValue: String? = null
        for (attr in htmlAnchorIdOrNameAttrRegex.findAll(attrs)) {
            val attrName = attr.groupValues[1].lowercase()
            val value = attr.groupValues[2]
                .ifEmpty { attr.groupValues[3] }
                .ifEmpty { attr.groupValues[4] }
            when (attrName) {
                "id" -> if (idValue == null) idValue = value
                "name" -> if (nameValue == null) nameValue = value
            }
        }
        // Require id or name so unrelated empty tags are not silently dropped.
        val resolved = idValue ?: nameValue ?: return null
        return resolved
    }

    /**
     * Side-TOC / slug display title: strip inline Markdown to plain label text.
     * `## [德篇](#de-pian)` → `德篇`; does not mutate the original source file.
     */
    internal fun normalizeHeadingPlainTitle(source: String): String =
        InlineMarkdownParser.toPlainText(source)
            .replace(Regex("""\s+"""), " ")
            .trim()

    /**
     * In-page fragments authored as link destinations inside heading source text.
     * Only `#…` destinations; external URLs are ignored.
     */
    internal fun inPageFragmentsFromInline(source: String): List<String> {
        if (source.isEmpty()) return emptyList()
        val seen = linkedSetOf<String>()
        for (dest in InlineMarkdownParser.extractLinkDestinations(source)) {
            when (val action = MarkdownLinkRouter.classify(dest)) {
                is MarkdownLinkRouter.Action.InPageAnchor -> {
                    val id = action.anchorId.trim()
                    if (id.isNotEmpty()) seen += id
                }
                is MarkdownLinkRouter.Action.External -> Unit
            }
        }
        return seen.toList()
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
        if (matchEmptyHtmlAnchorId(trimmed) != null) return true
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
