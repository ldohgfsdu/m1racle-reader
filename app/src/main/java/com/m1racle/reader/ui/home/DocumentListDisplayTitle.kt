package com.m1racle.reader.ui.home

/**
 * List-title helpers for documents that are no longer readable.
 *
 * The Markdown reader is gone, so this file deliberately contains no Markdown
 * parser: it takes the title already stored in Room and makes it presentable.
 * Historic rows keep their `title` column (an H1 captured at import time), and
 * those rows must still show a sensible name in the library instead of a blank
 * cell or a wrong format label.
 *
 * PDF never reaches this file — `resolveDocumentCardTitles` handles PDF with the
 * file name directly.
 */
object DocumentListDisplayTitle {

    /**
     * Turn a stored title into a list display name.
     *
     * Steps: inline markers → whitespace → leading emoji → readable-content check
     * → file-name stem → placeholder. The inline step is a plain string clean-up,
     * not a parse: it only unwraps paired `**`, `*`, `` ` `` and `~~`.
     */
    fun normalizeListDisplayTitle(extractedTitle: String?, fileName: String): String {
        if (!extractedTitle.isNullOrBlank()) {
            val plain = normalizeWhitespace(stripInlineMarkers(extractedTitle))
            val stripped = stripLeadingEmojiClusters(plain).trim()
            if (stripped.isNotEmpty() && hasReadableContent(stripped)) {
                return stripped
            }
        }
        val stem = fileNameStem(fileName)
        return stem.ifBlank { "未命名文档" }
    }

    /**
     * Whether the stored title yields a display name on its own, rather than
     * falling back to the file name. The library uses this to decide whether to
     * show the full file name underneath.
     */
    fun hasUsableDocumentTitle(extractedTitle: String?, fileName: String): Boolean {
        if (extractedTitle.isNullOrBlank()) return false
        val plain = normalizeWhitespace(stripInlineMarkers(extractedTitle))
        val stripped = stripLeadingEmojiClusters(plain).trim()
        return stripped.isNotEmpty() && hasReadableContent(stripped)
    }

    fun fileNameStem(fileName: String): String {
        val name = fileName.trim().substringAfterLast('/').ifBlank { fileName.trim() }
        if (name.isEmpty()) return ""
        val lower = name.lowercase()
        // Same suffix set as before the move; the EPUB branch in
        // resolveDocumentCardTitles still trims ".epub" itself.
        for (suffix in listOf(".markdown", ".md", ".txt")) {
            if (lower.endsWith(suffix) && name.length > suffix.length) {
                return name.dropLast(suffix.length)
            }
        }
        return name
    }

    fun normalizeWhitespace(text: String): String =
        text.replace(Regex("""\s+"""), " ").trim()

    /**
     * Remove leading emoji / emoji-presentation clusters, ZWJ sequences,
     * variation selectors, and spaces that follow them. Mid-string and trailing
     * emoji are left alone.
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
                sawEmoji && Character.isWhitespace(cp) -> {
                    i += count
                }
                else -> break
            }
        }
        return if (sawEmoji) text.substring(i) else text
    }

    /**
     * Unwrap paired inline markers captured in a stored H1.
     *
     * Each pattern requires content on both sides of the pair, so an unpaired or
     * trailing marker is left alone: `**未闭合` keeps its asterisks instead of
     * losing them to the single-asterisk rule. Longer markers are applied first
     * so `**x**` is not split by the `*` pass.
     *
     * `_` and `__` are intentionally not handled: underscores occur inside real
     * file names and titles, and unwrapping them would corrupt the text.
     */
    private fun stripInlineMarkers(text: String): String {
        var out = text
        for (pattern in INLINE_MARKER_PATTERNS) {
            while (true) {
                val next = pattern.replace(out) { it.groupValues[1] }
                if (next == out) break
                out = next
            }
        }
        return out
    }

    private fun hasReadableContent(text: String): Boolean =
        text.any { ch -> ch.isLetterOrDigit() || ch in '\u4e00'..'\u9fff' }

    /**
     * Longest-first. Each requires non-space content inside the pair, which is why
     * `**未闭合` and `a * b * c` survive untouched.
     */
    private val INLINE_MARKER_PATTERNS = listOf(
        Regex("""\*\*(?=\S)(.+?)(?<=\S)\*\*"""),
        Regex("""~~(?=\S)(.+?)(?<=\S)~~"""),
        Regex("""`(?=\S)(.+?)(?<=\S)`"""),
        Regex("""\*(?=\S)(.+?)(?<=\S)\*"""),
    )

    private fun isEmojiRelated(cp: Int): Boolean {
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
        if (cp in 0x1F3FB..0x1F3FF) return true // skin tone modifiers
        return false
    }
}
