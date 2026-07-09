package com.markleaf.app.ui.viewer.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.markleaf.app.data.settings.ReaderSettings
import com.markleaf.app.markdown.MarkdownBlock
import com.markleaf.app.search.SearchMatch
import com.markleaf.app.search.highlightPlainText
import com.markleaf.app.ui.viewer.components.ReaderTypography.scaled

@Composable
fun HeadingBlockView(
    block: MarkdownBlock.Heading,
    settings: ReaderSettings,
    activeMatch: SearchMatch? = null,
    modifier: Modifier = Modifier,
) {
    val fs = settings.fontScale
    val style = when (block.level) {
        1 -> MaterialTheme.typography.headlineMedium.copy(
            fontWeight = FontWeight.Bold,
            fontSize = ReaderTypography.h1Size.scaled(fs),
            lineHeight = ReaderTypography.h1LineHeight.scaled(fs),
        )
        2 -> MaterialTheme.typography.headlineSmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = ReaderTypography.h2Size.scaled(fs),
            lineHeight = ReaderTypography.h2LineHeight.scaled(fs),
        )
        3 -> MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = ReaderTypography.h3Size.scaled(fs),
            lineHeight = ReaderTypography.h3LineHeight.scaled(fs),
        )
        4 -> MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = ReaderTypography.h4Size.scaled(fs),
            lineHeight = ReaderTypography.h4LineHeight.scaled(fs),
        )
        else -> MaterialTheme.typography.titleSmall.copy(
            fontWeight = FontWeight.Medium,
            fontSize = ReaderTypography.h56Size.scaled(fs),
            lineHeight = ReaderTypography.h56LineHeight.scaled(fs),
        )
    }

    val top = when (block.level) {
        1 -> 20.dp
        2 -> 18.dp
        3 -> 14.dp
        else -> 12.dp
    }
    val bottom = when (block.level) {
        1 -> 12.dp
        2 -> 10.dp
        3 -> 8.dp
        else -> 6.dp
    }

    val annotated = if (activeMatch != null) {
        highlightPlainText(
            text = block.text.ifBlank { " " },
            activeStart = activeMatch.matchStart,
            activeEnd = activeMatch.matchEnd,
            highlightColor = searchFragmentHighlightColor(),
        )
    } else {
        null
    }

    Text(
        text = annotated ?: androidx.compose.ui.text.AnnotatedString(block.text.ifBlank { " " }),
        style = style,
        color = if (block.level >= 5) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = modifier
            .fillMaxWidth()
            .searchBlockHighlight(activeMatch != null)
            .padding(top = top, bottom = bottom),
    )
}
