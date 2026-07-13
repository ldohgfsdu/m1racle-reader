package com.markleaf.app.ui.viewer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.markleaf.app.data.settings.ReaderSettings
import com.markleaf.app.markdown.MarkdownBlock
import com.markleaf.app.ui.viewer.components.ReaderTypography.scaled

/**
 * Blockquote: left rail only, no card chrome.
 * Body uses onSurfaceVariant; strong spans use onSurface.
 */
@Composable
fun QuoteBlockView(
    block: MarkdownBlock.Quote,
    settings: ReaderSettings,
    isCurrentSearchMatch: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val bodySize = ReaderTypography.quoteFontSize.scaled(settings.fontScale)
    val bodyLine = ReaderTypography.quoteLineHeight(settings)
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

    val (body, attribution) = splitQuoteAttribution(block.text)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .searchBlockHighlight(isCurrentSearchMatch)
            .height(IntrinsicSize.Min),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(barColor, RoundedCornerShape(1.5.dp)),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            if (body.isNotEmpty()) {
                InlineMarkdownText(
                    text = body,
                    settings = settings,
                    style = bodyStyle,
                    strongColor = strongColor,
                )
            }
            if (attribution != null) {
                Spacer(modifier = Modifier.height(16.dp))
                InlineMarkdownText(
                    text = attribution,
                    settings = settings,
                    style = attributionStyle,
                    strongColor = strongColor,
                )
            }
        }
    }
}

/**
 * View-only split: last line starting with em/en dash is treated as attribution.
 * Does not change MarkdownParser.
 */
private fun splitQuoteAttribution(text: String): Pair<String, String?> {
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
