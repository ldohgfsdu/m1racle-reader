package com.m1racle.reader.ui.viewer.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m1racle.reader.data.settings.ReaderSettings
import com.m1racle.reader.markdown.MarkdownBlock
import com.m1racle.reader.search.BlockSearchHighlights
import com.m1racle.reader.ui.viewer.components.ReaderTypography.scaled
// EmptyRanges shared constant

/**
 * Blockquote: left rail only, no card chrome.
 * When search highlights are active, render full quote text as one unit so
 * UTF-16 offsets (from whole-block plain text) stay valid.
 */
@Composable
fun QuoteBlockView(
    block: MarkdownBlock.Quote,
    settings: ReaderSettings,
    searchHighlights: BlockSearchHighlights? = null,
    modifier: Modifier = Modifier,
    onLinkClick: ((String) -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val bodySize = ReaderTypography.quoteFontSize.scaled(settings.fontScale)
    val bodyLine = ReaderTypography.quoteLineHeight(settings)
    // Attribution runs 1sp under body with an 11sp floor (labelSmall) — formula, intentionally untokenized.
    val attrSize = (bodySize.value - 1f).coerceAtLeast(11f).sp
    val attrLine = ReaderTypography.captionLineHeight(settings, attrSize.value)

    val bodyStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = bodySize,
        lineHeight = bodyLine,
        color = scheme.onSurfaceVariant,
    )
    val attributionStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = attrSize,
        lineHeight = attrLine,
        color = scheme.onSurfaceVariant,
    )
    val strongColor = scheme.onSurface
    val barColor = scheme.outline

    val ranges = searchHighlights?.forWholeBlock() ?: BlockSearchHighlights.EmptyRanges
    val (body, attribution) = splitQuoteAttribution(block.text)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .drawBehind {
                val railWidth = 3.dp.toPx()
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset.Zero,
                    size = Size(railWidth, size.height),
                    cornerRadius = CornerRadius(railWidth / 2f),
                )
            }
            .padding(start = 19.dp),
    ) {
        if (ranges.isNotEmpty()) {
            // Single text unit keeps search offsets aligned with extractor plain text.
            InlineMarkdownText(
                text = block.text,
                settings = settings,
                style = bodyStyle,
                strongColor = strongColor,
                onLinkClick = onLinkClick,
                searchRanges = ranges,
            )
        } else {
            if (body.isNotEmpty()) {
                InlineMarkdownText(
                    text = body,
                    settings = settings,
                    style = bodyStyle,
                    strongColor = strongColor,
                    onLinkClick = onLinkClick,
                )
            }
            if (attribution != null) {
                Spacer(modifier = Modifier.height(16.dp))
                InlineMarkdownText(
                    text = attribution,
                    annotationOffset = com.m1racle.reader.markdown.InlineMarkdownParser.toPlainText(block.text)
                        .lastIndexOf(com.m1racle.reader.markdown.InlineMarkdownParser.toPlainText(attribution)).coerceAtLeast(0),
                    settings = settings,
                    style = attributionStyle,
                    strongColor = strongColor,
                    onLinkClick = onLinkClick,
                )
            }
        }
    }
}

/**
 * View-only split: last line starting with em/en dash is treated as attribution.
 * Does not change MarkdownParser.
 */
internal fun splitQuoteAttribution(text: String): Pair<String, String?> {
    val trimmed = text.trimEnd()
    if (trimmed.isEmpty()) return "" to null
    val lines = trimmed.lines()
    if (lines.size < 2) return trimmed to null
    val last = lines.last().trim()
    val isAttribution =
        last.startsWith("—") ||
            last.startsWith("–") ||
            last.startsWith("——") ||
            last.startsWith("--")
    if (!isAttribution) return trimmed to null
    val body = lines.dropLast(1).joinToString("\n").trimEnd()
    return body to last
}
