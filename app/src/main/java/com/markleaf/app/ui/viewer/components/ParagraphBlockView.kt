package com.markleaf.app.ui.viewer.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.markleaf.app.data.settings.ReaderSettings
import com.markleaf.app.markdown.MarkdownBlock
import com.markleaf.app.ui.viewer.components.ReaderTypography.scaled
import com.markleaf.app.ui.viewer.components.ReaderTypography.scaledLineHeight

@Composable
fun ParagraphBlockView(
    block: MarkdownBlock.Paragraph,
    settings: ReaderSettings,
    modifier: Modifier = Modifier,
) {
    InlineMarkdownText(
        text = block.text,
        settings = settings,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = ReaderTypography.bodyFontSize.scaled(settings.fontScale),
            lineHeight = ReaderTypography.bodyLineHeight.scaledLineHeight(settings.lineHeightScale),
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 12.dp),
    )
}
