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
        val Light = HighlightTheme(
            keyword = Color(0xFF0550AE),
            string = Color(0xFF0A3069),
            number = Color(0xFF0550AE),
            comment = Color(0xFF6E7781),
            typeOrTag = Color(0xFF116329),
            attribute = Color(0xFF953800),
            punctuation = Color(0xFF24292F),
            plain = Color(0xFF24292F),
        )

        val Dark = HighlightTheme(
            keyword = Color(0xFF79C0FF),
            string = Color(0xFFA5D6FF),
            number = Color(0xFF79C0FF),
            comment = Color(0xFF8B949E),
            typeOrTag = Color(0xFF7EE787),
            attribute = Color(0xFFFFA657),
            punctuation = Color(0xFFC9D1D9),
            plain = Color(0xFFC9D1D9),
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
