package com.m1racle.reader.ui.viewer.components

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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.m1racle.reader.data.settings.ReaderSettings
import com.m1racle.reader.markdown.MarkdownBlock
import com.m1racle.reader.search.BlockSearchHighlights
import com.m1racle.reader.ui.viewer.components.ReaderTypography.scaled
// EmptyRanges: shared empty list for search-closed composition

@Composable
fun ListBlockView(
    block: MarkdownBlock.ListBlock,
    settings: ReaderSettings,
    searchHighlights: BlockSearchHighlights? = null,
    modifier: Modifier = Modifier,
    onLinkClick: ((String) -> Unit)? = null,
) {
    val itemStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = ReaderTypography.bodyFontSize.scaled(settings.fontScale),
        lineHeight = ReaderTypography.bodyLineHeight(settings),
        color = MaterialTheme.colorScheme.onBackground,
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
                // Bullet / number decoration — never search-highlighted.
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
                    onLinkClick = onLinkClick,
                    searchRanges = searchHighlights?.forListItem(index)
                        ?: BlockSearchHighlights.EmptyRanges,
                )
            }
        }
    }
}
