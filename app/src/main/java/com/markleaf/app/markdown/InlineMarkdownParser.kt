package com.markleaf.app.markdown

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
    val bold: SpanStyle = SpanStyle(fontWeight = FontWeight.Bold),
    val italic: SpanStyle = SpanStyle(fontStyle = FontStyle.Italic),
    val code: SpanStyle = SpanStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 14.5.sp,
        background = Color(0x1A2F5D50),
    ),
    val link: SpanStyle = SpanStyle(
        color = Color(0xFF2F5D50),
        textDecoration = TextDecoration.Underline,
    ),
) {
    companion object {
        fun themed(
            primary: Color,
            codeBackground: Color,
            codeFontSize: TextUnit = 14.5.sp,
        ): InlineMarkdownStyles = InlineMarkdownStyles(
            code = SpanStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = codeFontSize,
                background = codeBackground,
                // Keep chip flat so it does not inflate body line height.
                fontWeight = FontWeight.Normal,
            ),
            link = SpanStyle(
                color = primary,
                textDecoration = TextDecoration.Underline,
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

    fun parse(
        text: String,
        styles: InlineMarkdownStyles = InlineMarkdownStyles(),
    ): AnnotatedString {
        if (text.isEmpty()) return AnnotatedString("")
        return try {
            buildAnnotatedString {
                appendInline(text, 0, text.length, styles)
            }
        } catch (_: Exception) {
            AnnotatedString(text)
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
                        withStyle(styles.link) {
                            // Title may contain nested inline markup.
                            appendInline(source, link.titleStart, link.titleEnd, styles)
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

                else -> {
                    append(source[i])
                    i++
                }
            }
        }
    }

    private data class ParsedLink(
        val titleStart: Int,
        val titleEnd: Int,
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
