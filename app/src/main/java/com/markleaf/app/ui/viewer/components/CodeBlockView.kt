package com.markleaf.app.ui.viewer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.markleaf.app.data.settings.ReaderSettings
import com.markleaf.app.markdown.MarkdownBlock

@Composable
fun CodeBlockView(
    block: MarkdownBlock.CodeBlock,
    settings: ReaderSettings,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    val bg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val codeSize = ReaderTypography.codeBlockSize(settings.fontScale)
    val codeLine = ReaderTypography.codeBlockLine(settings.fontScale, settings.lineHeightScale)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(shape)
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        val language = block.language
        if (!language.isNullOrBlank()) {
            Text(
                text = language,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            Text(
                text = block.code.ifEmpty { " " },
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = codeSize,
                    lineHeight = codeLine,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                softWrap = false,
            )
        }
    }
}
