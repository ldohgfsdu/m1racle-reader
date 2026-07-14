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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.markleaf.app.data.settings.ReaderSettings
import com.markleaf.app.markdown.MarkdownBlock
import com.markleaf.app.search.SearchMatch
import com.markleaf.app.ui.viewer.components.ReaderTypography.scaled

@Composable
fun ListBlockView(
    block: MarkdownBlock.ListBlock,
    settings: ReaderSettings,
    activeMatch: SearchMatch? = null,
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
            .searchBlockHighlight(activeMatch != null)
            .padding(vertical = 2.dp),
    ) {
        block.items.forEachIndexed { index, item ->
            val itemActive = activeMatch != null &&
                (activeMatch.listItemIndex < 0 || activeMatch.listItemIndex == index)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.5.dp)
                    .searchBlockHighlight(itemActive && activeMatch.listItemIndex == index),
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
                    onLinkClick = onLinkClick,
                )
            }
        }
    }
}
