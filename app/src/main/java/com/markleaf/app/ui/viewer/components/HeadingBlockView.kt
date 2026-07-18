package com.markleaf.app.ui.viewer.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.markleaf.app.data.settings.ReaderSettings
import com.markleaf.app.markdown.InlineMarkdownParser
import com.markleaf.app.markdown.MarkdownBlock
import com.markleaf.app.search.BlockSearchHighlights
import com.markleaf.app.search.withSearchHighlights
import com.markleaf.app.ui.viewer.components.ReaderTypography.scaled

@Composable
fun HeadingBlockView(
    block: MarkdownBlock.Heading,
    settings: ReaderSettings,
    searchHighlights: BlockSearchHighlights? = null,
    modifier: Modifier = Modifier,
) {
    val fs = settings.fontScale
    val fontSize = when (block.level) {
        1 -> ReaderTypography.h1Size.scaled(fs)
        2 -> ReaderTypography.h2Size.scaled(fs)
        3 -> ReaderTypography.h3Size.scaled(fs)
        4 -> ReaderTypography.h4Size.scaled(fs)
        else -> ReaderTypography.h56Size.scaled(fs)
    }
    val lineHeight = ReaderTypography.headingLineHeight(fontSize, settings)
    val weight = when (block.level) {
        in 1..3 -> FontWeight.SemiBold
        else -> FontWeight.Normal
    }
    val base = when (block.level) {
        1 -> MaterialTheme.typography.headlineMedium
        2 -> MaterialTheme.typography.headlineSmall
        3 -> MaterialTheme.typography.titleLarge
        4 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleSmall
    }
    val style = base.copy(
        fontWeight = weight,
        fontSize = fontSize,
        lineHeight = lineHeight,
        textAlign = TextAlign.Start,
    )

    val top = headingTopPadding(block.level)
    val bottom = when (block.level) {
        1 -> 10.dp
        2 -> 8.dp
        3 -> 6.dp
        else -> 6.dp
    }

    val ranges = searchHighlights?.forWholeBlock() ?: BlockSearchHighlights.EmptyRanges
    val hasSearch = ranges.isNotEmpty()
    val normalHl = if (hasSearch) searchMatchNormalBackground() else Color.Unspecified
    val activeHl = if (hasSearch) searchMatchActiveBackground() else Color.Unspecified
    val source = block.text.ifBlank { " " }
    val baseAnnotated = remember(source) {
        InlineMarkdownParser.parse(source)
    }
    val annotated = if (!hasSearch) {
        baseAnnotated
    } else {
        remember(baseAnnotated, ranges, normalHl, activeHl) {
            baseAnnotated.withSearchHighlights(ranges, normalHl, activeHl)
        }
    }

    Text(
        text = annotated,
        style = style,
        textAlign = TextAlign.Start,
        color = if (block.level >= 5) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(top = top, bottom = bottom),
    )
}

internal fun headingTopPadding(level: Int) = when (level) {
    1 -> 28.dp
    2 -> 24.dp
    3 -> 20.dp
    else -> 16.dp
}
