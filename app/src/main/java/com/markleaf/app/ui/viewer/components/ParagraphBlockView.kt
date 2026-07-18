package com.markleaf.app.ui.viewer.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.markleaf.app.data.settings.ReaderSettings
import com.markleaf.app.markdown.MarkdownBlock
import com.markleaf.app.search.BlockSearchHighlights
import com.markleaf.app.ui.viewer.components.ReaderTypography.scaled
// BlockSearchHighlights.EmptyRanges used for zero-cost search-closed path

@Composable
fun ParagraphBlockView(
    block: MarkdownBlock.Paragraph,
    settings: ReaderSettings,
    searchHighlights: BlockSearchHighlights? = null,
    modifier: Modifier = Modifier,
    onLinkClick: ((String) -> Unit)? = null,
) {
    val bodyStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = ReaderTypography.bodyFontSize.scaled(settings.fontScale),
        lineHeight = ReaderTypography.bodyLineHeight(settings),
        color = MaterialTheme.colorScheme.onBackground,
    )
    InlineMarkdownText(
        text = block.text,
        settings = settings,
        style = bodyStyle,
        onLinkClick = onLinkClick,
        searchRanges = searchHighlights?.forWholeBlock() ?: BlockSearchHighlights.EmptyRanges,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 14.dp),
    )
}
