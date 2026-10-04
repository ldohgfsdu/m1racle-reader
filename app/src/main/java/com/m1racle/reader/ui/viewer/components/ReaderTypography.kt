package com.m1racle.reader.ui.viewer.components

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.m1racle.reader.data.settings.ReaderSettings

/**
 * GPT-style long-form reading density.
 *
 * Font size uses [ReaderSettings.fontScale].
 * Line height uses [ReaderSettings.lineHeightScale] as **lh / fontSize ratio**
 * (Compact 1.35 · Default 1.55 · Relaxed 1.75), not a mild multiplier of a fixed sp.
 */
object ReaderTypography {
    /** Body ~16.5sp Regular */
    val bodyFontSize = 16.5.sp
    val bodyWeight = FontWeight.Normal
    val bodyParagraphBottom = 14 // dp applied at call site (paragraph gap, not line height)

    val quoteFontSize = 16.5.sp // match body metrics

    val inlineCodeFontSize = 14.5.sp
    val codeBlockFontSize = 13.5.sp
    val codeBlockLineHeight = 20.sp
    const val CODE_BLOCK_MAX_SP = 15.5f

    val h1Size = 24.sp
    val h2Size = 21.sp
    val h3Size = 19.sp
    val h4Size = 17.sp
    val h56Size = 16.sp

    val tocH1Size = 15.sp
    val tocH2Size = 14.sp
    val tocH3Size = 14.sp

    val topBarTitleSize = 16.sp

    fun headingSize(level: Int, fontScale: Float): TextUnit = when (level) {
        1 -> h1Size
        2 -> h2Size
        3 -> h3Size
        4 -> h4Size
        else -> h56Size
    }.scaled(fontScale)

    /** Tablet reading column max width. */
    val contentMaxWidthDp = 760

    fun TextUnit.scaled(fontScale: Float): TextUnit =
        (value * fontScale).sp

    /**
     * Body / list / quote / table cell line height from settings.
     * Example at fontScale=1: 16.5×1.35≈22.3sp · 16.5×1.55≈25.6sp · 16.5×1.75≈28.9sp
     * (clear step between presets; ~6.6sp Compact→Relaxed).
     */
    fun bodyLineHeight(settings: ReaderSettings): TextUnit {
        val size = bodyFontSize.scaled(settings.fontScale).value
        val ratio = ReaderSettings.clampLineHeightScale(settings.lineHeightScale)
        return (size * ratio).sp
    }

    fun quoteLineHeight(settings: ReaderSettings): TextUnit {
        val size = quoteFontSize.scaled(settings.fontScale).value
        val ratio = ReaderSettings.clampLineHeightScale(settings.lineHeightScale)
        return (size * ratio).sp
    }

    /** Caption / secondary body under images. */
    fun captionLineHeight(settings: ReaderSettings, fontSizeSp: Float): TextUnit {
        val ratio = ReaderSettings.clampLineHeightScale(settings.lineHeightScale)
        return (fontSizeSp * ratio).sp
    }

    /**
     * Heading line height: same preset ratio as body (must change with line-height chips).
     */
    fun headingLineHeight(fontSize: TextUnit, settings: ReaderSettings): TextUnit {
        val ratio = ReaderSettings.clampLineHeightScale(settings.lineHeightScale)
        return (fontSize.value * ratio).sp
    }

    fun codeBlockSize(fontScale: Float): TextUnit {
        val raw = codeBlockFontSize.value * fontScale
        return raw.coerceAtMost(CODE_BLOCK_MAX_SP).sp
    }

    /** Code blocks keep independent fixed ratio (not body line-height settings). */
    fun codeBlockLine(fontScale: Float): TextUnit {
        val size = codeBlockSize(fontScale).value
        val ratio = codeBlockLineHeight.value / codeBlockFontSize.value
        return (size * ratio).sp
    }
}
