package com.markleaf.app.ui.viewer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.markleaf.app.data.settings.ReaderSettings
import com.markleaf.app.markdown.MarkdownBlock
import com.markleaf.app.markdown.TableAlignment
import com.markleaf.app.ui.viewer.components.ReaderTypography.scaled
import com.markleaf.app.ui.viewer.components.ReaderTypography.scaledLineHeight

@Composable
fun TableBlockView(
    block: MarkdownBlock.Table,
    settings: ReaderSettings,
    isCurrentSearchMatch: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    val headerBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val cellMinWidth = 96.dp
    val fontSize = ReaderTypography.bodyFontSize.scaled(settings.fontScale)
    val lineHeight = ReaderTypography.bodyLineHeight.scaledLineHeight(settings.lineHeightScale)

    val bodyStyle = MaterialTheme.typography.bodyMedium.copy(
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurface,
    )
    val headerStyle = bodyStyle.copy(fontWeight = FontWeight.SemiBold)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .searchBlockHighlight(isCurrentSearchMatch)
            .clip(shape)
            .border(1.dp, borderColor, shape)
            .horizontalScroll(rememberScrollState()),
    ) {
        // Header
        Row(
            modifier = Modifier
                .background(headerBg)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            block.headers.forEachIndexed { col, cell ->
                val align = block.alignments.getOrElse(col) { TableAlignment.Start }
                TableCell(
                    text = cell,
                    settings = settings,
                    style = headerStyle,
                    alignment = align,
                    modifier = Modifier
                        .widthIn(min = cellMinWidth)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
        HorizontalDivider(color = borderColor, thickness = 1.dp)

        // Body rows
        block.rows.forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier.padding(vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val colCount = block.headers.size.coerceAtLeast(1)
                for (col in 0 until colCount) {
                    val cell = row.getOrElse(col) { "" }
                    val align = block.alignments.getOrElse(col) { TableAlignment.Start }
                    TableCell(
                        text = cell,
                        settings = settings,
                        style = bodyStyle,
                        alignment = align,
                        modifier = Modifier
                            .widthIn(min = cellMinWidth)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
            if (rowIndex < block.rows.lastIndex) {
                HorizontalDivider(
                    color = borderColor.copy(alpha = 0.5f),
                    thickness = 0.5.dp,
                )
            }
        }
    }
}

@Composable
private fun TableCell(
    text: String,
    settings: ReaderSettings,
    style: androidx.compose.ui.text.TextStyle,
    alignment: TableAlignment,
    modifier: Modifier = Modifier,
) {
    val boxAlign = when (alignment) {
        TableAlignment.Start -> Alignment.CenterStart
        TableAlignment.Center -> Alignment.Center
        TableAlignment.End -> Alignment.CenterEnd
    }
    Box(
        modifier = modifier.widthIn(min = 96.dp),
        contentAlignment = boxAlign,
    ) {
        InlineMarkdownText(
            text = text.ifEmpty { " " },
            settings = settings,
            style = style,
        )
    }
}
