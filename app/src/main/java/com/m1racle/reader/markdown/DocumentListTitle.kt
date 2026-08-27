package com.m1racle.reader.markdown

/**
 * Pure helpers for homepage "最近文件" card display titles.
 * Does not mutate Markdown source or affect Reader H1 rendering.
 *
 * Pipeline for list displayTitle:
 * 1. Locate structural H1 (ATX / Setext), ignoring fences & YAML front matter
 * 2. Inline → plain text (via [InlineMarkdownParser.toPlainText])
 * 3. Normalize whitespace
 * 4. Strip leading emoji clusters + following spaces
 * 5. Empty → fileName stem → "未命名文档"
 */
object DocumentListTitle {

    private val atxH1 = Regex("""^#\s+(.*)$""")
    private val setextUnderline = Regex("""^=+\s*$""")
    private val fenceOpen = Regex("""^```""")
    private val fenceClose = Regex("""^```\s*$""")

    /**
     * Full pipeline from raw markdown + file name → list card primary title.
     */
    fun extractDocumentTitle(markdown: String, fileName: String): String {
        val rawH1 = findFirstValidH1Source(markdown)
        val plain = rawH1?.let { InlineMarkdownParser.toPlainText(it) }
            ?.let { normalizeWhitespace(it) }
            ?.takeIf { it.isNotEmpty() }
        return normalizeListDisplayTitle(plain, fileName)
    }

    /**
     * Normalize an already-extracted H1 (e.g. Room [title] field = heading source text)
     * into a list displayTitle. Applies plain-text conversion if [extractedTitle] still
     * contains inline markers, then leading-emoji strip, then fileName fallback.
     */
    fun normalizeListDisplayTitle(extractedTitle: String?, fileName: String): String {
        if (!extractedTitle.isNullOrBlank()) {
            val plain = normalizeWhitespace(InlineMarkdownParser.toPlainText(extractedTitle))
            val stripped = stripLeadingEmojiClusters(plain).trim()
            if (stripped.isNotEmpty() && hasReadableContent(stripped)) {
                return stripped
            }
        }
        val stem = fileNameStem(fileName)
        return stem.ifBlank { "未命名文档" }
    }

    /**
     * Whether [extractedTitle] yields a list title from document H1 (not file-name fallback).
     * Used to decide if supporting line shows the full file name.
     */
    fun hasUsableDocumentTitle(extractedTitle: String?, fileName: String): Boolean {
        if (extractedTitle.isNullOrBlank()) return false
        val plain = normalizeWhitespace(InlineMarkdownParser.toPlainText(extractedTitle))
        val stripped = stripLeadingEmojiClusters(plain).trim()
        return stripped.isNotEmpty() && hasReadableContent(stripped)
    }

    fun fileNameStem(fileName: String): String {
        val name = fileName.trim().substringAfterLast('/').ifBlank { fileName.trim() }
        if (name.isEmpty()) return ""
        val lower = name.lowercase()
        for (suffix in listOf(".markdown", ".md", ".txt")) {
            if (lower.endsWith(suffix) && name.length > suffix.length) {
                return name.dropLast(suffix.length)
            }
        }
        return name
    }

    // region extract H1 source from markdown structure

    /**
     * Returns H1 inline source (without leading `#`), or null.
     * Skips fenced code and YAML front matter. Prefers first ATX H1, then Setext H1.
     */
    internal fun findFirstValidH1Source(markdown: String): String? {
        val normalized = markdown.replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.split('\n')
        var i = 0

        // YAML front matter at file start.
        if (lines.isNotEmpty() && lines[0].trim() == "---") {
            i = 1
            while (i < lines.size) {
                if (lines[i].trim() == "---") {
                    i++
                    break
                }
                i++
            }
        }

        var inFence = false
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            if (!inFence && fenceOpen.containsMatchIn(trimmed) && !fenceClose.matches(trimmed)) {
                inFence = true
                i++
                continue
            }
            if (inFence) {
                if (fenceClose.matches(trimmed)) inFence = false
                i++
                continue
            }
            if (fenceClose.matches(trimmed)) {
                // stray close
                i++
                continue
            }

            // ATX H1 only (exactly one #).
            val atx = atxH1.matchEntire(trimmed)
            if (atx != null) {
                val body = atx.groupValues[1].trimEnd()
                if (isPlausibleH1Source(body)) return body
                i++
                continue
            }

            // Setext H1: text line + ==== underline.
            if (i + 1 < lines.size &&
                trimmed.isNotEmpty() &&
                !trimmed.startsWith("#") &&
                setextUnderline.matches(lines[i + 1].trim())
            ) {
                if (isPlausibleH1Source(trimmed)) return trimmed
                i += 2
                continue
            }

            i++
        }
        return null
    }

    private fun isPlausibleH1Source(source: String): Boolean {
        val plain = normalizeWhitespace(InlineMarkdownParser.toPlainText(source))
        if (plain.isEmpty()) return false
        val stripped = stripLeadingEmojiClusters(plain).trim()
        // Accept if after emoji strip we still have readable content, OR if plain
        // has only emoji (will fall back later in normalize).
        return plain.isNotEmpty()
    }

    // endregion

    fun normalizeWhitespace(text: String): String =
        text.replace(Regex("""\s+"""), " ").trim()

    /**
     * Remove leading emoji / emoji-presentation clusters, ZWJ sequences,
     * variation selectors, and spaces that follow them. Does not touch mid/end emoji.
     */
    fun stripLeadingEmojiClusters(text: String): String {
        if (text.isEmpty()) return text
        var i = 0
        var sawEmoji = false
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val count = Character.charCount(cp)
            when {
                isEmojiRelated(cp) -> {
                    sawEmoji = true
                    i += count
                }
                // Spaces only after we've started consuming a leading emoji run.
                sawEmoji && Character.isWhitespace(cp) -> {
                    i += count
                }
                else -> break
            }
        }
        return if (sawEmoji) text.substring(i) else text
    }

    private fun hasReadableContent(text: String): Boolean =
        text.any { ch ->
            ch.isLetterOrDigit() || ch in '\u4e00'..'\u9fff'
        }

    private fun isEmojiRelated(cp: Int): Boolean {
        // Variation selectors / ZWJ / keycap / tags — only strip when part of leading run
        if (cp == 0x200D) return true // ZWJ
        if (cp in 0xFE00..0xFE0F) return true // variation selectors
        if (cp == 0x20E3) return true // combining enclosing keycap
        if (cp in 0xE0020..0xE007F) return true // tags
        if (cp in 0x1F1E0..0x1F1FF) return true // regional indicators (flags)
        if (cp in 0x1F300..0x1FAFF) return true // emoji blocks
        if (cp in 0x1F000..0x1F02F) return true // mahjong etc.
        if (cp in 0x2600..0x26FF) return true // misc symbols (often emoji)
        if (cp in 0x2700..0x27BF) return true // dingbats
        if (cp in 0x2300..0x23FF) {
            // technical — only a few are emoji presentation; be conservative
            return cp in setOf(0x231A, 0x231B, 0x23E9, 0x23EA, 0x23EB, 0x23EC, 0x23F0, 0x23F3)
        }
        if (cp in 0x2B50..0x2B55) return true
        if (cp == 0x3030 || cp == 0x303D || cp == 0x3297 || cp == 0x3299) return true
        // Skin tone modifiers
        if (cp in 0x1F3FB..0x1F3FF) return true
        return false
    }
}
