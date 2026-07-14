package com.markleaf.app.ui.viewer.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import android.os.Trace
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.markleaf.app.data.settings.ReaderSettings
import com.markleaf.app.markdown.MarkdownBlock
import com.markleaf.app.search.SearchMatch

@Composable
fun MarkdownBlockView(
    block: MarkdownBlock,
    settings: ReaderSettings,
    isSearchActive: Boolean = false,
    activeMatch: SearchMatch? = null,
    onCopyMessage: (String) -> Unit = {},
    documentUri: String? = null,
    stableBlockId: String? = null,
    tableLayoutCache: TableLayoutCache? = null,
    onLinkClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Trace.beginSection("MarkdownBlockCompose")
    val highlight = isSearchActive && activeMatch != null
    when (block) {
        is MarkdownBlock.Heading -> HeadingBlockView(
            block = block,
            settings = settings,
            activeMatch = if (highlight) activeMatch else null,
            modifier = modifier,
        )
        is MarkdownBlock.Paragraph -> ParagraphBlockView(
            block = block,
            settings = settings,
            isCurrentSearchMatch = highlight,
            modifier = modifier,
            onLinkClick = onLinkClick,
        )
        is MarkdownBlock.Quote -> QuoteBlockView(
            block = block,
            settings = settings,
            isCurrentSearchMatch = highlight,
            modifier = modifier,
            onLinkClick = onLinkClick,
        )
        is MarkdownBlock.CodeBlock -> CodeBlockView(
            block = block,
            settings = settings,
            activeMatch = if (highlight) activeMatch else null,
            onCopyMessage = onCopyMessage,
            modifier = modifier,
        )
        is MarkdownBlock.ListBlock -> ListBlockView(
            block = block,
            settings = settings,
            activeMatch = if (highlight) activeMatch else null,
            modifier = modifier,
            onLinkClick = onLinkClick,
        )
        is MarkdownBlock.Table -> TableBlockView(
            block = block,
            settings = settings,
            isCurrentSearchMatch = highlight,
            stableTableId = stableBlockId,
            layoutCache = tableLayoutCache,
            modifier = modifier,
        )
        is MarkdownBlock.Image -> ImageBlockView(
            block = block,
            settings = settings,
            isCurrentSearchMatch = highlight,
            documentUri = documentUri,
            modifier = modifier,
        )
        MarkdownBlock.Divider -> {
            // 1dp solid hairline, content width, quiet vertical rhythm.
            HorizontalDivider(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 1.dp,
            )
        }
    }
    Trace.endSection()
}
