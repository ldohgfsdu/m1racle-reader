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
import com.markleaf.app.markdown.MarkdownPerformance
import com.markleaf.app.markdown.blockContentType
import com.markleaf.app.search.BlockSearchHighlights

@Composable
fun MarkdownBlockView(
    block: MarkdownBlock,
    settings: ReaderSettings,
    /** Local search highlights for this block only; null when search closed / no hits. */
    searchHighlights: BlockSearchHighlights? = null,
    onCopyMessage: (String) -> Unit = {},
    documentUri: String? = null,
    stableBlockId: String? = null,
    tableLayoutCache: TableLayoutCache? = null,
    onLinkClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val type = block.blockContentType()
    MarkdownPerformance.traceBlockCompose(type) {
        when (block) {
            is MarkdownBlock.Heading -> HeadingBlockView(
                block = block,
                settings = settings,
                searchHighlights = searchHighlights,
                modifier = modifier,
            )
            is MarkdownBlock.Paragraph -> ParagraphBlockView(
                block = block,
                settings = settings,
                searchHighlights = searchHighlights,
                modifier = modifier,
                onLinkClick = onLinkClick,
            )
            is MarkdownBlock.Quote -> QuoteBlockView(
                block = block,
                settings = settings,
                searchHighlights = searchHighlights,
                modifier = modifier,
                onLinkClick = onLinkClick,
            )
            is MarkdownBlock.CodeBlock -> CodeBlockView(
                block = block,
                settings = settings,
                searchHighlights = searchHighlights,
                onCopyMessage = onCopyMessage,
                modifier = modifier,
            )
            is MarkdownBlock.ListBlock -> ListBlockView(
                block = block,
                settings = settings,
                searchHighlights = searchHighlights,
                modifier = modifier,
                onLinkClick = onLinkClick,
            )
            is MarkdownBlock.Table -> TableBlockView(
                block = block,
                settings = settings,
                searchHighlights = searchHighlights,
                stableTableId = stableBlockId,
                layoutCache = tableLayoutCache,
                modifier = modifier,
            )
            is MarkdownBlock.Image -> ImageBlockView(
                block = block,
                settings = settings,
                documentUri = documentUri,
                modifier = modifier,
            )
            MarkdownBlock.Divider -> {
                HorizontalDivider(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 1.dp,
                )
            }
        }
    }
}
