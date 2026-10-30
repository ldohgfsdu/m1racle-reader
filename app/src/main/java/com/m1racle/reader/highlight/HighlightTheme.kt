package com.m1racle.reader.highlight

import androidx.compose.ui.graphics.Color
import com.m1racle.reader.design.RadixColors

/**
 * Soft syntax colors for light / dark reading themes.
 * Official Radix text steps, bundled for offline use.
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
        /** Radix text colors on the light code surface. */
        val Light = HighlightTheme(
            keyword = RadixColors.Blue11,
            string = RadixColors.Blue11,
            number = RadixColors.Pink11,
            comment = RadixColors.Gray11,
            typeOrTag = RadixColors.Red11,
            attribute = RadixColors.Red11,
            punctuation = RadixColors.Gray11,
            plain = RadixColors.Gray12,
        )

        /** Soft / low-sat colors for dark code wells. */
        val Dark = HighlightTheme(
            keyword = RadixColors.BlueDark11,
            string = RadixColors.BlueDark11,
            number = RadixColors.BlueDark11,
            comment = RadixColors.GrayDark11,
            typeOrTag = RadixColors.GreenDark11,
            attribute = RadixColors.AmberDark11,
            punctuation = RadixColors.GrayDark12,
            plain = RadixColors.GrayDark12,
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
