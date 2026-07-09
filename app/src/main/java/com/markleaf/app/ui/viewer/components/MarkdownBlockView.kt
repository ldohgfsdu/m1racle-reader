package com.markleaf.app.ui.viewer.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.markleaf.app.data.settings.ReaderSettings
import com.markleaf.app.markdown.MarkdownBlock

@Composable
fun MarkdownBlockView(
    block: MarkdownBlock,
    settings: ReaderSettings,
    modifier: Modifier = Modifier,
) {
    when (block) {
        is MarkdownBlock.Heading -> HeadingBlockView(
            block = block,
            settings = settings,
            modifier = modifier,
        )
        is MarkdownBlock.Paragraph -> ParagraphBlockView(
            block = block,
            settings = settings,
            modifier = modifier,
        )
        is MarkdownBlock.Quote -> QuoteBlockView(
            block = block,
            settings = settings,
            modifier = modifier,
        )
        is MarkdownBlock.CodeBlock -> CodeBlockView(
            block = block,
            settings = settings,
            modifier = modifier,
        )
        is MarkdownBlock.ListBlock -> ListBlockView(
            block = block,
            settings = settings,
            modifier = modifier,
        )
        MarkdownBlock.Divider -> {
            HorizontalDivider(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                thickness = 1.dp,
            )
        }
    }
}
