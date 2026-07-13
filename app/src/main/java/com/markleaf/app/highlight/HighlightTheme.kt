package com.markleaf.app.highlight

import androidx.compose.ui.graphics.Color

/**
 * Soft syntax colors for light / dark reading themes.
 * Centralized hard-coded palette (no network / no dynamic download).
 */
data class HighlightTheme(
    val keyword: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val typeOrTag: Color,
    val attribute: Color,
    val punctuation: Color,
    val plain: Color,
) {
    companion object {
        /** GPT-style light code well — low-sat tokens on pale gray. */
        val Light = HighlightTheme(
            keyword = Color(0xFF1677A3),
            string = Color(0xFF275D8C),
            number = Color(0xFFA13D63),
            comment = Color(0xFF8A8A8A),
            typeOrTag = Color(0xFFB5485D),
            attribute = Color(0xFFB5485D),
            punctuation = Color(0xFF666666),
            plain = Color(0xFF1F1F1F),
        )

        /** Soft / low-sat colors for dark code wells. */
        val Dark = HighlightTheme(
            keyword = Color(0xFF8AB4E8),
            string = Color(0xFF9ECBFF),
            number = Color(0xFF8AB4E8),
            comment = Color(0xFF8B949E),
            typeOrTag = Color(0xFF8FDB9A),
            attribute = Color(0xFFE0B07A),
            punctuation = Color(0xFFD4D4D4),
            plain = Color(0xFFD4D4D4),
        )

        fun forTheme(isDark: Boolean): HighlightTheme =
            if (isDark) Dark else Light
    }
}

enum class HighlightKind {
    KEYWORD,
    STRING,
    NUMBER,
    COMMENT,
    TYPE_OR_TAG,
    ATTRIBUTE,
    PUNCTUATION,
    PLAIN,
}

fun HighlightTheme.colorOf(kind: HighlightKind): Color = when (kind) {
    HighlightKind.KEYWORD -> keyword
    HighlightKind.STRING -> string
    HighlightKind.NUMBER -> number
    HighlightKind.COMMENT -> comment
    HighlightKind.TYPE_OR_TAG -> typeOrTag
    HighlightKind.ATTRIBUTE -> attribute
    HighlightKind.PUNCTUATION -> punctuation
    HighlightKind.PLAIN -> plain
}
