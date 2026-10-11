package com.m1racle.reader.markdown

/** Source spans, including fenced blank lines, never reconstructed from rendered text. */
object MarkdownSource {
    fun chunks(source: String): List<IntRange> {
        val result = mutableListOf<IntRange>()
        var start = -1
        var offset = 0
        var fence: Char? = null
        source.splitToSequence('\n').forEach { line ->
            val trimmed = line.trim()
            val marker = when { trimmed.startsWith("```") -> '`'; trimmed.startsWith("~~~") -> '~'; else -> null }
            if (start < 0 && trimmed.isNotEmpty()) start = offset
            if (marker != null) fence = if (fence == marker) null else if (fence == null) marker else fence
            if (trimmed.isEmpty() && fence == null && start >= 0) {
                result += start until offset
                start = -1
            }
            offset += line.length + 1
        }
        if (start >= 0) result += start until source.length
        return result
    }

    fun replace(source: String, range: IntRange, replacement: String): String {
        require(range.first >= 0 && range.last < source.length && !range.isEmpty())
        return source.replaceRange(range.first, range.last + 1, replacement)
    }
}
