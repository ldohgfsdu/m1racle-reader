package com.m1racle.reader.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Styles applied to inline Markdown spans.
 * Colors should come from the current Material theme at the call site.
 */
data class InlineMarkdownStyles(
    // Emphasized spans only; surrounding body stays Normal.
    val bold: SpanStyle = SpanStyle(fontWeight = FontWeight.SemiBold),
    val italic: SpanStyle = SpanStyle(fontStyle = FontStyle.Italic),
    val code: SpanStyle = SpanStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 14.5.sp,
        fontWeight = FontWeight.Normal,
        background = Color(0x1A2F5D50),
    ),
    val link: SpanStyle = SpanStyle(
        color = Color(0xFF2F5D50),
        textDecoration = TextDecoration.Underline,
        fontWeight = FontWeight.Normal,
    ),
) {
    companion object {
        fun themed(
            primary: Color,
            codeBackground: Color,
            codeFontSize: TextUnit = 14.5.sp,
            /** When set, **strong** uses this color (e.g. near-black in quotes). */
            boldColor: Color? = null,
        ): InlineMarkdownStyles = InlineMarkdownStyles(
            bold = if (boldColor != null) {
                SpanStyle(fontWeight = FontWeight.SemiBold, color = boldColor)
            } else {
                SpanStyle(fontWeight = FontWeight.SemiBold)
            },
            code = SpanStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = codeFontSize,
                background = codeBackground,
                fontWeight = FontWeight.Normal,
            ),
            link = SpanStyle(
                color = primary,
                textDecoration = TextDecoration.Underline,
                fontWeight = FontWeight.Normal,
            ),
        )
    }
}

/**
 * Lightweight inline Markdown → [AnnotatedString].
 * Supports bold, italic, inline code, and links. No WebView / no heavy library.
 *
 * Unclosed markers are kept as literal text. On any unexpected failure, returns
 * the original string unchanged.
 */
object InlineMarkdownParser {

    const val LINK_ANNOTATION_TAG = "m1racle-reader.markdown.link"

    /**
     * True when [text] may contain inline Markdown that must be walked.
     *
     * Mid-word single underscores (e.g. `SCROLL_BODY`) do **not** count as emphasis
     * openers — matching CommonMark word-internal `_` rules and avoiding a full
     * AnnotatedString rebuild for plain body paragraphs that only use identifiers.
     */
    fun mayContainInlineMarkup(text: String): Boolean {
        if (text.isEmpty()) return false
        var i = 0
        val end = text.length
        while (i < end) {
            when (val c = text[i]) {
                // '&' (HTML entities) and breaks must also take the slow path:
                // the fast path returns the raw string, but toPlainText decodes
                // entities and maps breaks to spaces — offsets would drift.
                '`', '*', '[', '!', '\\', '<', '&', '\n', '\r' -> return true
                '_' -> {
                    // "__" is strong; a single mid-word "_" is plain text.
                    val next = text.getOrNull(i + 1)
                    if (next == '_') return true
                    val prev = text.getOrNull(i - 1)
                    val prevWord = prev != null && prev.isLetterOrDigit()
                    val nextWord = next != null && next.isLetterOrDigit()
                    if (!(prevWord && nextWord)) return true
                    i++
                }
                else -> i++
            }
        }
        return false
    }

    fun parse(
        text: String,
        styles: InlineMarkdownStyles = InlineMarkdownStyles(),
    ): AnnotatedString {
        if (text.isEmpty()) {
            MarkdownPerformance.recordInlineParse(usedFastPath = true)
            return AnnotatedString("")
        }
        // Fast path: plain paragraphs with no real markup skip buildAnnotatedString.
        if (!mayContainInlineMarkup(text)) {
            MarkdownPerformance.recordInlineParse(usedFastPath = true)
            return AnnotatedString(text)
        }
        return try {
            val result = buildAnnotatedString {
                appendInline(text, 0, text.length, styles)
            }
            MarkdownPerformance.recordInlineParse(usedFastPath = false)
            result
        } catch (_: Exception) {
            MarkdownPerformance.recordInlineParse(usedFastPath = false)
            AnnotatedString(text)
        }
    }

    /**
     * Convert inline Markdown to plain text for list titles etc.
     * Uses the same structure walk as [parse] (not a single mega-regex).
     * - strong/em: inner text only
     * - link: label only
     * - code: code text only
     * - image: non-empty alt only
     * - soft/hard breaks → single space
     */
    fun toPlainText(text: String): String {
        if (text.isEmpty()) return ""
        return try {
            buildString { appendPlain(text, 0, text.length) }
        } catch (_: Exception) {
            text
        }
    }

    /**
     * Collect link destinations from inline Markdown (same walk as [parse]).
     * Skips images (`![alt](url)`). Code spans are not scanned for links.
     * Used to register heading `#fragment` targets such as `## [德篇](#de-pian)`.
     */
    fun extractLinkDestinations(text: String): List<String> {
        if (text.isEmpty() || !text.contains('[')) return emptyList()
        return try {
            val result = mutableListOf<String>()
            var i = 0
            val end = text.length
            while (i < end) {
                when {
                    text[i] == '`' -> {
                        val close = text.indexOf('`', i + 1)
                        i = if (close == -1 || close >= end) i + 1 else close + 1
                    }
                    text[i] == '!' && i + 1 < end && text[i + 1] == '[' -> {
                        val link = tryParseLink(text, i + 1, end)
                        i = link?.endExclusive ?: (i + 1)
                    }
                    text[i] == '[' -> {
                        val link = tryParseLink(text, i, end)
                        if (link == null) {
                            i++
                        } else {
                            result += text.substring(link.urlStart, link.urlEnd).trim()
                            i = link.endExclusive
                        }
                    }
                    else -> i++
                }
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun StringBuilder.appendPlain(source: String, start: Int, end: Int) {
        var i = start
        while (i < end) {
            when {
                // Image: ![alt](url) — alt only if non-empty
                source[i] == '!' && i + 1 < end && source[i + 1] == '[' -> {
                    val link = tryParseLink(source, i + 1, end)
                    if (link == null) {
                        append('!')
                        i++
                    } else {
                        val alt = source.substring(link.titleStart, link.titleEnd)
                        val plainAlt = toPlainText(alt).trim()
                        if (plainAlt.isNotEmpty()) append(plainAlt)
                        i = link.endExclusive
                    }
                }

                source[i] == '`' -> {
                    val close = source.indexOf('`', i + 1)
                    if (close == -1 || close >= end) {
                        append('`')
                        i++
                    } else {
                        append(source.substring(i + 1, close))
                        i = close + 1
                    }
                }

                source[i] == '[' -> {
                    val link = tryParseLink(source, i, end)
                    if (link == null) {
                        append('[')
                        i++
                    } else {
                        appendPlain(source, link.titleStart, link.titleEnd)
                        i = link.endExclusive
                    }
                }

                matchAt(source, i, end, "**") -> {
                    val close = indexOfToken(source, i + 2, end, "**")
                    if (close == -1) {
                        append("**")
                        i += 2
                    } else {
                        appendPlain(source, i + 2, close)
                        i = close + 2
                    }
                }
                matchAt(source, i, end, "__") -> {
                    val close = indexOfToken(source, i + 2, end, "__")
                    if (close == -1) {
                        append("__")
                        i += 2
                    } else {
                        appendPlain(source, i + 2, close)
                        i = close + 2
                    }
                }

                source[i] == '*' -> {
                    val close = indexOfSingleDelimiter(source, i + 1, end, '*')
                    if (close == -1) {
                        append('*')
                        i++
                    } else {
                        appendPlain(source, i + 1, close)
                        i = close + 1
                    }
                }
                source[i] == '_' -> {
                    // Mid-word "_" is never emphasis — keep literal (matches appendInline).
                    val prev = if (i > start) source[i - 1] else null
                    val next = if (i + 1 < end) source[i + 1] else null
                    val midWord = prev != null && prev.isLetterOrDigit() &&
                        next != null && next.isLetterOrDigit()
                    if (midWord) {
                        append('_')
                        i++
                    } else {
                        val close = indexOfSingleDelimiter(source, i + 1, end, '_')
                        if (close == -1) {
                            append('_')
                            i++
                        } else {
                            appendPlain(source, i + 1, close)
                            i = close + 1
                        }
                    }
                }

                // Soft/hard break → space
                source[i] == '\n' || source[i] == '\r' -> {
                    append(' ')
                    i++
                }

                // Escape: \X → X
                source[i] == '\\' && i + 1 < end -> {
                    append(source[i + 1])
                    i += 2
                }

                // Skip crude HTML tags
                source[i] == '<' -> {
                    val close = source.indexOf('>', i + 1)
                    if (close == -1 || close >= end) {
                        append('<')
                        i++
                    } else {
                        i = close + 1
                    }
                }

                // Basic HTML entities
                source[i] == '&' -> {
                    val semi = source.indexOf(';', i + 1)
                    if (semi != -1 && semi < end && semi - i < 10) {
                        val ent = source.substring(i + 1, semi)
                        val decoded = decodeHtmlEntity(ent)
                        if (decoded != null) {
                            append(decoded)
                            i = semi + 1
                        } else {
                            append('&')
                            i++
                        }
                    } else {
                        append('&')
                        i++
                    }
                }

                else -> {
                    append(source[i])
                    i++
                }
            }
        }
    }

    private fun AnnotatedString.Builder.appendInline(
        source: String,
        start: Int,
        end: Int,
        styles: InlineMarkdownStyles,
    ) {
        var i = start
        while (i < end) {
            when {
                // Image: ![alt](url) — alt text only (matches toPlainText).
                source[i] == '!' && i + 1 < end && source[i + 1] == '[' -> {
                    val link = tryParseLink(source, i + 1, end)
                    if (link == null) {
                        append('!')
                        i++
                    } else {
                        val alt = source.substring(link.titleStart, link.titleEnd)
                        val plainAlt = toPlainText(alt).trim()
                        if (plainAlt.isNotEmpty()) append(plainAlt)
                        i = link.endExclusive
                    }
                }

                // Inline code: `code`
                source[i] == '`' -> {
                    val close = source.indexOf('`', i + 1)
                    if (close == -1 || close >= end) {
                        append('`')
                        i++
                    } else {
                        withStyle(styles.code) {
                            append(source.substring(i + 1, close))
                        }
                        i = close + 1
                    }
                }

                // Link: [title](url)
                source[i] == '[' -> {
                    val link = tryParseLink(source, i, end)
                    if (link == null) {
                        append('[')
                        i++
                    } else {
                        pushStringAnnotation(
                            tag = LINK_ANNOTATION_TAG,
                            annotation = source.substring(link.urlStart, link.urlEnd),
                        )
                        try {
                            withStyle(styles.link) {
                                // Title may contain nested inline markup.
                                appendInline(source, link.titleStart, link.titleEnd, styles)
                            }
                        } finally {
                            pop()
                        }
                        i = link.endExclusive
                    }
                }

                // Bold: **text** or __text__
                matchAt(source, i, end, "**") -> {
                    val close = indexOfToken(source, i + 2, end, "**")
                    if (close == -1) {
                        append("**")
                        i += 2
                    } else {
                        withStyle(styles.bold) {
                            appendInline(source, i + 2, close, styles)
                        }
                        i = close + 2
                    }
                }
                matchAt(source, i, end, "__") -> {
                    val close = indexOfToken(source, i + 2, end, "__")
                    if (close == -1) {
                        append("__")
                        i += 2
                    } else {
                        withStyle(styles.bold) {
                            appendInline(source, i + 2, close, styles)
                        }
                        i = close + 2
                    }
                }

                // Italic: *text* or _text_ (not part of ** / __)
                source[i] == '*' -> {
                    val close = indexOfSingleDelimiter(source, i + 1, end, '*')
                    if (close == -1) {
                        append('*')
                        i++
                    } else {
                        withStyle(styles.italic) {
                            appendInline(source, i + 1, close, styles)
                        }
                        i = close + 1
                    }
                }
                source[i] == '_' -> {
                    // Mid-word "_" (SCROLL_BODY) is never emphasis — keep literal.
                    val prev = if (i > start) source[i - 1] else null
                    val next = if (i + 1 < end) source[i + 1] else null
                    val midWord = prev != null && prev.isLetterOrDigit() &&
                        next != null && next.isLetterOrDigit()
                    if (midWord) {
                        append('_')
                        i++
                    } else {
                        val close = indexOfSingleDelimiter(source, i + 1, end, '_')
                        if (close == -1) {
                            append('_')
                            i++
                        } else {
                            withStyle(styles.italic) {
                                appendInline(source, i + 1, close, styles)
                            }
                            i = close + 1
                        }
                    }
                }

                else -> {
                    // Keep the plain walk identical to appendPlain so the
                    // rendered text equals the searchable text (offsets match):
                    // breaks → space, escapes, HTML tags skipped, entities decoded.
                    when {
                        // Soft/hard break → space
                        source[i] == '\n' || source[i] == '\r' -> {
                            append(' ')
                            i++
                        }

                        // Escape: \X → X
                        source[i] == '\\' && i + 1 < end -> {
                            append(source[i + 1])
                            i += 2
                        }

                        // Skip crude HTML tags
                        source[i] == '<' -> {
                            val close = source.indexOf('>', i + 1)
                            if (close == -1 || close >= end) {
                                append('<')
                                i++
                            } else {
                                i = close + 1
                            }
                        }

                        // Basic HTML entities
                        source[i] == '&' -> {
                            val semi = source.indexOf(';', i + 1)
                            if (semi != -1 && semi < end && semi - i < 10) {
                                val ent = source.substring(i + 1, semi)
                                val decoded = decodeHtmlEntity(ent)
                                if (decoded != null) {
                                    append(decoded)
                                    i = semi + 1
                                } else {
                                    append('&')
                                    i++
                                }
                            } else {
                                append('&')
                                i++
                            }
                        }

                        else -> {
                            append(source[i])
                            i++
                        }
                    }
                }
            }
        }
    }

    private fun decodeHtmlEntity(ent: String): String? = when {
        ent == "amp" -> "&"
        ent == "lt" -> "<"
        ent == "gt" -> ">"
        ent == "quot" -> "\""
        ent == "apos" -> "'"
        ent == "nbsp" -> " "
        ent.startsWith("#x") || ent.startsWith("#X") ->
            ent.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
        ent.startsWith("#") ->
            ent.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) }
        else -> null
    }

    private data class ParsedLink(
        val titleStart: Int,
        val titleEnd: Int,
        val urlStart: Int,
        val urlEnd: Int,
        val endExclusive: Int,
    )

    private fun tryParseLink(source: String, start: Int, end: Int): ParsedLink? {
        // [title](url)
        if (start >= end || source[start] != '[') return null
        val titleEnd = source.indexOf(']', start + 1)
        if (titleEnd == -1 || titleEnd >= end) return null
        if (titleEnd + 1 >= end || source[titleEnd + 1] != '(') return null
        val urlEnd = source.indexOf(')', titleEnd + 2)
        if (urlEnd == -1 || urlEnd >= end) return null
        // Empty title still OK — show empty styled span.
        return ParsedLink(
            titleStart = start + 1,
            titleEnd = titleEnd,
            urlStart = titleEnd + 2,
            urlEnd = urlEnd,
            endExclusive = urlEnd + 1,
        )
    }

    private fun matchAt(source: String, index: Int, end: Int, token: String): Boolean {
        if (index + token.length > end) return false
        return source.regionMatches(index, token, 0, token.length)
    }

    private fun indexOfToken(source: String, from: Int, end: Int, token: String): Int {
        var i = from
        while (i + token.length <= end) {
            if (source.regionMatches(i, token, 0, token.length)) return i
            i++
        }
        return -1
    }

    /**
     * Find a single-char delimiter that is not part of a double marker (** / __).
     */
    private fun indexOfSingleDelimiter(source: String, from: Int, end: Int, delim: Char): Int {
        var i = from
        while (i < end) {
            if (source[i] == delim) {
                val doubled = i + 1 < end && source[i + 1] == delim
                if (!doubled) return i
                i += 2 // skip ** or __
            } else {
                i++
            }
        }
        return -1
    }
}
