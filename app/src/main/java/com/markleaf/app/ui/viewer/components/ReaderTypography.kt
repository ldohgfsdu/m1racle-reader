package com.markleaf.app.ui.viewer.components

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.markleaf.app.data.settings.ReaderSettings

/**
 * Default Chinese long-form reading density.
 * Prefer comfortable density over display-scale marketing typography.
 * User scales apply via [scaled] / [scaledLineHeight].
 */
object ReaderTypography {
    /** Body: normal reading weight, not heading-like. */
    val bodyFontSize = 16.sp
    val bodyLineHeight = 25.sp
    val bodyWeight = FontWeight.Normal
    val bodyParagraphBottom = 12 // dp applied at call site

    val quoteFontSize = 15.sp
    val quoteLineHeight = 24.sp

    /** Slightly under body so chips stay flat in the line. */
    val inlineCodeFontSize = 14.5.sp
    val codeBlockFontSize = 13.sp
    val codeBlockLineHeight = 20.sp
    const val CODE_BLOCK_MAX_SP = 16f

    val h1Size = 26.sp
    val h1LineHeight = 34.sp
    val h2Size = 22.sp
    val h2LineHeight = 30.sp
    val h3Size = 19.sp
    val h3LineHeight = 26.sp
    val h4Size = 17.sp
    val h4LineHeight = 24.sp
    val h56Size = 16.sp
    val h56LineHeight = 22.sp

    val tocH1Size = 15.sp
    val tocH2Size = 14.sp
    val tocH3Size = 14.sp

    val topBarTitleSize = 16.sp

    fun TextUnit.scaled(fontScale: Float): TextUnit =
        (value * fontScale).sp

    fun TextUnit.scaledLineHeight(lineHeightScale: Float): TextUnit =
        (value * lineHeightScale).sp

    fun codeBlockSize(fontScale: Float): TextUnit {
        val raw = codeBlockFontSize.value * fontScale
        return raw.coerceAtMost(CODE_BLOCK_MAX_SP).sp
    }

    fun codeBlockLine(fontScale: Float, lineHeightScale: Float): TextUnit {
        val size = codeBlockSize(fontScale).value
        val ratio = codeBlockLineHeight.value / codeBlockFontSize.value
        return (size * ratio * lineHeightScale).sp
    }

    fun apply(settings: ReaderSettings) = settings
}
