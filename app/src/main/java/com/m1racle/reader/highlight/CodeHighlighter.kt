package com.m1racle.reader.highlight

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/**
 * Lightweight syntax highlighter → [AnnotatedString].
 * Regex / scanner based; never throws to callers.
 *
 * Results are cached by (code, language, theme) so LazyColumn recycle / re-entry
 * does not re-scan the same fence on the main thread.
 */
object CodeHighlighter {

    private const val CACHE_MAX = 48

    private data class CacheKey(
        val code: String,
        val language: String?,
        val isDarkTheme: Boolean,
    )

    /** LRU cache — access order; eldest evicted above [CACHE_MAX]. */
    private val cache = object : LinkedHashMap<CacheKey, AnnotatedString>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, AnnotatedString>?): Boolean =
            size > CACHE_MAX
    }

    fun highlightCode(
        code: String,
        language: String?,
        isDarkTheme: Boolean,
    ): AnnotatedString {
        if (code.isEmpty()) return AnnotatedString("")
        val key = CacheKey(code, language, isDarkTheme)
        synchronized(cache) {
            cache[key]?.let { return it }
        }
        val result = try {
            val lang = CodeLanguage.from(language)
            val theme = HighlightTheme.forTheme(isDarkTheme)
            when (lang) {
                CodeLanguage.PLAIN -> AnnotatedString(code)
                CodeLanguage.JSON -> highlightJson(code, theme)
                CodeLanguage.XML -> highlightXml(code, theme)
                CodeLanguage.MARKDOWN -> highlightMarkdown(code, theme)
                CodeLanguage.PYTHON -> highlightPython(code, theme)
                CodeLanguage.SHELL -> highlightShell(code, theme)
                CodeLanguage.KOTLIN,
                CodeLanguage.JAVA,
                CodeLanguage.JAVASCRIPT,
                -> highlightCFamily(code, theme, keywordsFor(lang))
            }
        } catch (_: Exception) {
            AnnotatedString(code)
        }
        synchronized(cache) {
            cache[key] = result
        }
        return result
    }

    private fun keywordsFor(lang: CodeLanguage): Set<String> = when (lang) {
        CodeLanguage.KOTLIN -> KOTLIN_KEYWORDS
        CodeLanguage.JAVA -> JAVA_KEYWORDS
        CodeLanguage.JAVASCRIPT -> JS_KEYWORDS
        else -> emptySet()
    }

    // region C-family (Kotlin / Java / JS)

    private fun highlightCFamily(
        code: String,
        theme: HighlightTheme,
        keywords: Set<String>,
    ): AnnotatedString = buildAnnotatedString {
        var i = 0
        while (i < code.length) {
            when {
                // Line comment //
                code.startsWith("//", i) -> {
                    val end = code.indexOf('\n', i).let { if (it < 0) code.length else it }
                    appendStyled(code, i, end, theme, HighlightKind.COMMENT)
                    i = end
                }
                // Block comment /* */
                code.startsWith("/*", i) -> {
                    val close = code.indexOf("*/", i + 2)
                    val end = if (close < 0) code.length else close + 2
                    appendStyled(code, i, end, theme, HighlightKind.COMMENT)
                    i = end
                }
                // String "
                code[i] == '"' -> {
                    val end = scanString(code, i, '"')
                    appendStyled(code, i, end, theme, HighlightKind.STRING)
                    i = end
                }
                // Char '
                code[i] == '\'' -> {
                    val end = scanString(code, i, '\'')
                    appendStyled(code, i, end, theme, HighlightKind.STRING)
                    i = end
                }
                // Number
                code[i].isDigit() -> {
                    val end = scanNumber(code, i)
                    appendStyled(code, i, end, theme, HighlightKind.NUMBER)
                    i = end
                }
                // Identifier / keyword
                code[i].isLetter() || code[i] == '_' -> {
                    val end = scanIdent(code, i)
                    val word = code.substring(i, end)
                    val kind = if (word in keywords) HighlightKind.KEYWORD else HighlightKind.PLAIN
                    appendStyled(code, i, end, theme, kind)
                    i = end
                }
                // Punctuation
                code[i] in "(){}[].,;:+-*/%&|<>=!?" -> {
                    appendStyled(code, i, i + 1, theme, HighlightKind.PUNCTUATION)
                    i++
                }
                else -> {
                    appendStyled(code, i, i + 1, theme, HighlightKind.PLAIN)
                    i++
                }
            }
        }
    }

    // endregion

    // region Python

    private fun highlightPython(code: String, theme: HighlightTheme): AnnotatedString =
        buildAnnotatedString {
            var i = 0
            while (i < code.length) {
                when {
                    code.startsWith("\"\"\"", i) || code.startsWith("'''", i) -> {
                        val q = code.substring(i, i + 3)
                        val close = code.indexOf(q, i + 3)
                        val end = if (close < 0) code.length else close + 3
                        appendStyled(code, i, end, theme, HighlightKind.STRING)
                        i = end
                    }
                    code[i] == '#' -> {
                        val end = code.indexOf('\n', i).let { if (it < 0) code.length else it }
                        appendStyled(code, i, end, theme, HighlightKind.COMMENT)
                        i = end
                    }
                    code[i] == '"' || code[i] == '\'' -> {
                        val end = scanString(code, i, code[i])
                        appendStyled(code, i, end, theme, HighlightKind.STRING)
                        i = end
                    }
                    code[i].isDigit() -> {
                        val end = scanNumber(code, i)
                        appendStyled(code, i, end, theme, HighlightKind.NUMBER)
                        i = end
                    }
                    code[i].isLetter() || code[i] == '_' -> {
                        val end = scanIdent(code, i)
                        val word = code.substring(i, end)
                        val kind = if (word in PYTHON_KEYWORDS) HighlightKind.KEYWORD else HighlightKind.PLAIN
                        appendStyled(code, i, end, theme, kind)
                        i = end
                    }
                    code[i] in "(){}[].,:-+*/%&|<>=!@" -> {
                        appendStyled(code, i, i + 1, theme, HighlightKind.PUNCTUATION)
                        i++
                    }
                    else -> {
                        appendStyled(code, i, i + 1, theme, HighlightKind.PLAIN)
                        i++
                    }
                }
            }
        }

    // endregion

    // region Shell

    private fun highlightShell(code: String, theme: HighlightTheme): AnnotatedString =
        buildAnnotatedString {
            var i = 0
            while (i < code.length) {
                when {
                    code[i] == '#' -> {
                        val end = code.indexOf('\n', i).let { if (it < 0) code.length else it }
                        appendStyled(code, i, end, theme, HighlightKind.COMMENT)
                        i = end
                    }
                    code[i] == '"' || code[i] == '\'' -> {
                        val end = scanString(code, i, code[i])
                        appendStyled(code, i, end, theme, HighlightKind.STRING)
                        i = end
                    }
                    code[i].isDigit() -> {
                        val end = scanNumber(code, i)
                        appendStyled(code, i, end, theme, HighlightKind.NUMBER)
                        i = end
                    }
                    code[i].isLetter() || code[i] == '_' -> {
                        val end = scanIdent(code, i)
                        val word = code.substring(i, end)
                        val kind = if (word in SHELL_KEYWORDS) HighlightKind.KEYWORD else HighlightKind.PLAIN
                        appendStyled(code, i, end, theme, kind)
                        i = end
                    }
                    else -> {
                        appendStyled(code, i, i + 1, theme, HighlightKind.PLAIN)
                        i++
                    }
                }
            }
        }

    // endregion

    // region JSON

    private fun highlightJson(code: String, theme: HighlightTheme): AnnotatedString =
        buildAnnotatedString {
            var i = 0
            while (i < code.length) {
                when {
                    code[i] == '"' -> {
                        val end = scanString(code, i, '"')
                        // Key if next non-space is :
                        var j = end
                        while (j < code.length && code[j].isWhitespace()) j++
                        val kind = if (j < code.length && code[j] == ':') {
                            HighlightKind.ATTRIBUTE
                        } else {
                            HighlightKind.STRING
                        }
                        appendStyled(code, i, end, theme, kind)
                        i = end
                    }
                    code[i].isDigit() || (code[i] == '-' && i + 1 < code.length && code[i + 1].isDigit()) -> {
                        val start = i
                        if (code[i] == '-') i++
                        val end = scanNumber(code, i)
                        appendStyled(code, start, end, theme, HighlightKind.NUMBER)
                        i = end
                    }
                    code.startsWith("true", i) || code.startsWith("false", i) || code.startsWith("null", i) -> {
                        val word = when {
                            code.startsWith("true", i) -> "true"
                            code.startsWith("false", i) -> "false"
                            else -> "null"
                        }
                        if (isWordBoundary(code, i + word.length)) {
                            appendStyled(code, i, i + word.length, theme, HighlightKind.KEYWORD)
                            i += word.length
                        } else {
                            appendStyled(code, i, i + 1, theme, HighlightKind.PLAIN)
                            i++
                        }
                    }
                    code[i] in "{}[],:" -> {
                        appendStyled(code, i, i + 1, theme, HighlightKind.PUNCTUATION)
                        i++
                    }
                    else -> {
                        appendStyled(code, i, i + 1, theme, HighlightKind.PLAIN)
                        i++
                    }
                }
            }
        }

    // endregion

    // region XML

    private fun highlightXml(code: String, theme: HighlightTheme): AnnotatedString =
        buildAnnotatedString {
            var i = 0
            while (i < code.length) {
                when {
                    code.startsWith("<!--", i) -> {
                        val close = code.indexOf("-->", i + 4)
                        val end = if (close < 0) code.length else close + 3
                        appendStyled(code, i, end, theme, HighlightKind.COMMENT)
                        i = end
                    }
                    code[i] == '<' -> {
                        val end = code.indexOf('>', i)
                        if (end < 0) {
                            appendStyled(code, i, code.length, theme, HighlightKind.TYPE_OR_TAG)
                            i = code.length
                        } else {
                            highlightXmlTag(code, i, end + 1, theme)
                            i = end + 1
                        }
                    }
                    else -> {
                        appendStyled(code, i, i + 1, theme, HighlightKind.PLAIN)
                        i++
                    }
                }
            }
        }

    private fun AnnotatedString.Builder.highlightXmlTag(
        code: String,
        start: Int,
        end: Int,
        theme: HighlightTheme,
    ) {
        var i = start
        // <
        appendStyled(code, i, i + 1, theme, HighlightKind.PUNCTUATION)
        i++
        if (i < end && code[i] == '/') {
            appendStyled(code, i, i + 1, theme, HighlightKind.PUNCTUATION)
            i++
        }
        // tag name
        val nameEnd = scanXmlName(code, i, end)
        if (nameEnd > i) {
            appendStyled(code, i, nameEnd, theme, HighlightKind.TYPE_OR_TAG)
            i = nameEnd
        }
        while (i < end) {
            when {
                code[i].isWhitespace() -> {
                    appendStyled(code, i, i + 1, theme, HighlightKind.PLAIN)
                    i++
                }
                code[i] == '/' || code[i] == '>' -> {
                    appendStyled(code, i, i + 1, theme, HighlightKind.PUNCTUATION)
                    i++
                }
                code[i] == '=' -> {
                    appendStyled(code, i, i + 1, theme, HighlightKind.PUNCTUATION)
                    i++
                }
                code[i] == '"' || code[i] == '\'' -> {
                    val sEnd = scanString(code, i, code[i]).coerceAtMost(end)
                    appendStyled(code, i, sEnd, theme, HighlightKind.STRING)
                    i = sEnd
                }
                code[i].isLetter() || code[i] == '_' || code[i] == ':' -> {
                    val aEnd = scanXmlName(code, i, end)
                    appendStyled(code, i, aEnd, theme, HighlightKind.ATTRIBUTE)
                    i = aEnd
                }
                else -> {
                    appendStyled(code, i, i + 1, theme, HighlightKind.PLAIN)
                    i++
                }
            }
        }
    }

    // endregion

    // region Markdown

    private fun highlightMarkdown(code: String, theme: HighlightTheme): AnnotatedString =
        buildAnnotatedString {
            val lines = code.split('\n', keepEmpty = true)
            lines.forEachIndexed { lineIndex, line ->
                if (lineIndex > 0) append('\n')
                when {
                    line.trimStart().startsWith("```") ->
                        appendStyled(line, 0, line.length, theme, HighlightKind.COMMENT)
                    line.trimStart().startsWith("#") -> {
                        val hashes = line.indexOfFirst { it != ' ' && it != '\t' }.let { start ->
                            var h = start
                            while (h < line.length && line[h] == '#') h++
                            start to h
                        }
                        if (hashes.first >= 0) {
                            if (hashes.first > 0) {
                                appendStyled(line, 0, hashes.first, theme, HighlightKind.PLAIN)
                            }
                            appendStyled(line, hashes.first, hashes.second, theme, HighlightKind.KEYWORD)
                            if (hashes.second < line.length) {
                                appendStyled(line, hashes.second, line.length, theme, HighlightKind.PLAIN)
                            }
                        } else {
                            appendStyled(line, 0, line.length, theme, HighlightKind.PLAIN)
                        }
                    }
                    line.trimStart().let { it.startsWith("- ") || it.startsWith("* ") } -> {
                        val start = line.indexOfFirst { it == '-' || it == '*' }
                        if (start > 0) appendStyled(line, 0, start, theme, HighlightKind.PLAIN)
                        appendStyled(line, start, start + 1, theme, HighlightKind.KEYWORD)
                        if (start + 1 < line.length) {
                            appendStyled(line, start + 1, line.length, theme, HighlightKind.PLAIN)
                        }
                    }
                    else -> appendStyled(line, 0, line.length, theme, HighlightKind.PLAIN)
                }
            }
        }

    // endregion

    // region scanners

    private fun scanString(code: String, start: Int, quote: Char): Int {
        var i = start + 1
        while (i < code.length) {
            when (code[i]) {
                '\\' -> i = (i + 2).coerceAtMost(code.length)
                quote -> return i + 1
                '\n' -> return i // unclosed string ends at newline for safety
                else -> i++
            }
        }
        return code.length
    }

    private fun scanNumber(code: String, start: Int): Int {
        var i = start
        if (code.startsWith("0x", i) || code.startsWith("0X", i)) {
            i += 2
            while (i < code.length && (
                    code[i].isDigit() ||
                        code[i] in 'a'..'f' ||
                        code[i] in 'A'..'F'
                    )
            ) {
                i++
            }
            return i
        }
        while (i < code.length && code[i].isDigit()) i++
        if (i < code.length && code[i] == '.' && i + 1 < code.length && code[i + 1].isDigit()) {
            i++
            while (i < code.length && code[i].isDigit()) i++
        }
        return i
    }

    private fun scanIdent(code: String, start: Int): Int {
        var i = start
        while (i < code.length && (code[i].isLetterOrDigit() || code[i] == '_')) i++
        return i
    }

    private fun scanXmlName(code: String, start: Int, end: Int): Int {
        var i = start
        while (i < end && (code[i].isLetterOrDigit() || code[i] in "_:.-")) i++
        return i
    }

    private fun isWordBoundary(code: String, index: Int): Boolean {
        if (index >= code.length) return true
        val c = code[index]
        return !c.isLetterOrDigit() && c != '_'
    }

    private fun AnnotatedString.Builder.appendStyled(
        code: String,
        start: Int,
        end: Int,
        theme: HighlightTheme,
        kind: HighlightKind,
    ) {
        if (start >= end || start >= code.length) return
        val safeEnd = end.coerceAtMost(code.length)
        withStyle(SpanStyle(color = theme.colorOf(kind))) {
            append(code, start, safeEnd)
        }
    }

    // endregion

    // region keyword sets

    private val KOTLIN_KEYWORDS = setOf(
        "as", "as?", "break", "class", "continue", "do", "else", "false", "for", "fun",
        "if", "in", "!in", "interface", "is", "!is", "null", "object", "package", "return",
        "super", "this", "throw", "true", "try", "typealias", "typeof", "val", "var",
        "when", "while", "by", "catch", "constructor", "delegate", "dynamic", "field",
        "file", "finally", "get", "import", "init", "param", "property", "receiver",
        "set", "setparam", "where", "actual", "abstract", "annotation", "companion",
        "const", "crossinline", "data", "enum", "expect", "external", "final", "infix",
        "inline", "inner", "internal", "lateinit", "noinline", "open", "operator",
        "out", "override", "private", "protected", "public", "reified", "sealed",
        "suspend", "tailrec", "value", "vararg",
    )

    private val JAVA_KEYWORDS = setOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
        "class", "const", "continue", "default", "do", "double", "else", "enum",
        "extends", "final", "finally", "float", "for", "goto", "if", "implements",
        "import", "instanceof", "int", "interface", "long", "native", "new", "package",
        "private", "protected", "public", "return", "short", "static", "strictfp",
        "super", "switch", "synchronized", "this", "throw", "throws", "transient",
        "try", "void", "volatile", "while", "true", "false", "null", "var", "record",
        "sealed", "permits", "yield",
    )

    private val JS_KEYWORDS = setOf(
        "break", "case", "catch", "class", "const", "continue", "debugger", "default",
        "delete", "do", "else", "export", "extends", "false", "finally", "for",
        "function", "if", "import", "in", "instanceof", "let", "new", "null", "return",
        "super", "switch", "this", "throw", "true", "try", "typeof", "var", "void",
        "while", "with", "yield", "async", "await", "static", "of",
    )

    private val PYTHON_KEYWORDS = setOf(
        "False", "None", "True", "and", "as", "assert", "async", "await", "break",
        "class", "continue", "def", "del", "elif", "else", "except", "finally", "for",
        "from", "global", "if", "import", "in", "is", "lambda", "nonlocal", "not",
        "or", "pass", "raise", "return", "try", "while", "with", "yield",
    )

    private val SHELL_KEYWORDS = setOf(
        "if", "then", "else", "elif", "fi", "case", "esac", "for", "select", "while",
        "until", "do", "done", "in", "function", "time", "coproc", "export", "local",
        "return", "exit", "echo", "cd", "pwd", "set", "unset", "source", "alias",
    )

    // endregion
}

/** Keep empty trailing lines when splitting markdown for highlight. */
private fun CharSequence.split(delimiter: Char, keepEmpty: Boolean): List<String> {
    if (!keepEmpty) return split(delimiter)
    val out = mutableListOf<String>()
    var start = 0
    for (i in indices) {
        if (this[i] == delimiter) {
            out.add(substring(start, i))
            start = i + 1
        }
    }
    out.add(substring(start))
    return out
}
