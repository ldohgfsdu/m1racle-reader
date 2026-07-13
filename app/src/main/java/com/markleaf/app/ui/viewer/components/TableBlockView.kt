package com.markleaf.app.ui.viewer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.markleaf.app.data.settings.ReaderSettings
import com.markleaf.app.markdown.MarkdownBlock
import com.markleaf.app.markdown.TableAlignment
import com.markleaf.app.ui.viewer.components.ReaderTypography.scaled

private val EdgeFadeWidth = 22.dp

@Composable
fun TableBlockView(
    block: MarkdownBlock.Table,
    settings: ReaderSettings,
    isCurrentSearchMatch: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val shape = RoundedCornerShape(8.dp)

    val borderColor = scheme.outlineVariant
    val dividerColor = scheme.outlineVariant
    val bodyBg = scheme.surfaceContainerLow
    val headerBg = scheme.surfaceContainerHigh

    val cellMinWidth = 96.dp
    val cellPadV = 5.dp
    val cellPadH = 10.dp
    val fontSize = ReaderTypography.bodyFontSize.scaled(settings.fontScale)
    val lineHeight = ReaderTypography.bodyLineHeight(settings)

    val bodyStyle = androidx.compose.ui.text.TextStyle(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = fontSize,
        lineHeight = lineHeight,
        color = scheme.onSurface,
    )
    val headerStyle = bodyStyle.copy(fontWeight = FontWeight.SemiBold)

    val scrollState = rememberScrollState()
    // derivedStateOf: horizontal drag only invalidates fade edges, not cell content.
    val showLeftFade by remember { derivedStateOf { scrollState.canScrollBackward } }
    val showRightFade by remember { derivedStateOf { scrollState.canScrollForward } }

    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerHeightDp = with(density) { headerHeightPx.toDp() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .searchBlockHighlight(isCurrentSearchMatch)
            .clip(shape)
            .border(1.dp, borderColor, shape)
            .background(bodyBg),
    ) {
        // Fixed full-width header band (does not scroll) — no white gaps at corners.
        if (headerHeightPx > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(headerHeightDp)
                    .background(headerBg),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
        ) {
            Row(
                modifier = Modifier
                    .onSizeChanged { headerHeightPx = it.height }
                    .padding(vertical = 1.dp),
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
                            .padding(horizontal = cellPadH, vertical = cellPadV),
                    )
                }
            }
            // Row rules only — no vertical grid.
            HorizontalDivider(color = dividerColor, thickness = 1.dp)

            block.rows.forEachIndexed { rowIndex, row ->
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                                .padding(horizontal = cellPadH, vertical = cellPadV),
                        )
                    }
                }
                if (rowIndex < block.rows.lastIndex) {
                    HorizontalDivider(
                        color = dividerColor,
                        thickness = 0.5.dp,
                    )
                }
            }
        }

        // Transparent shadow only — never opaque white/body cover over header.
        // Soft edge fade from scrim — strength from surface luminance (no UI isDark forks).
        val edgeAlpha = if (scheme.background.luminance() < 0.5f) 0.28f else 0.08f
        if (showLeftFade) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(EdgeFadeWidth)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                scheme.scrim.copy(alpha = edgeAlpha),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
        }
        if (showRightFade) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(EdgeFadeWidth)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                scheme.scrim.copy(alpha = edgeAlpha),
                            ),
                        ),
                    ),
            )
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
