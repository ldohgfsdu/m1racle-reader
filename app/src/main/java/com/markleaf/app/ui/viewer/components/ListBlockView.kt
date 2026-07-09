package com.markleaf.app.ui.viewer.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.markleaf.app.data.settings.ReaderSettings
import com.markleaf.app.markdown.MarkdownBlock
import com.markleaf.app.ui.viewer.components.ReaderTypography.scaled
import com.markleaf.app.ui.viewer.components.ReaderTypography.scaledLineHeight

@Composable
fun ListBlockView(
    block: MarkdownBlock.ListBlock,
    settings: ReaderSettings,
    modifier: Modifier = Modifier,
) {
    val itemStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = ReaderTypography.bodyFontSize.scaled(settings.fontScale),
        lineHeight = ReaderTypography.bodyLineHeight.scaledLineHeight(settings.lineHeightScale),
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        block.items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.5.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = if (block.ordered) "${index + 1}." else "•",
                    style = itemStyle,
                    modifier = Modifier
                        .widthIn(min = 20.dp)
                        .padding(end = 6.dp),
                )
                InlineMarkdownText(
                    text = item,
                    settings = settings,
                    style = itemStyle,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
