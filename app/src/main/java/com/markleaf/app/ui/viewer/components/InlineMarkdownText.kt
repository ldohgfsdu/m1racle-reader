package com.markleaf.app.ui.viewer.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.markleaf.app.data.settings.ReaderSettings
import com.markleaf.app.markdown.InlineMarkdownParser
import com.markleaf.app.markdown.InlineMarkdownStyles
import com.markleaf.app.ui.viewer.components.ReaderTypography.scaled

/**
 * Body text with lightweight inline Markdown (bold / italic / code / link).
 * Never used for fenced code blocks.
 */
@Composable
fun InlineMarkdownText(
    text: String,
    settings: ReaderSettings,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val codeSize = ReaderTypography.inlineCodeFontSize.scaled(settings.fontScale)
    val styles = remember(colorScheme.primary, colorScheme.surfaceVariant, codeSize) {
        InlineMarkdownStyles.themed(
            primary = colorScheme.primary,
            codeBackground = colorScheme.surfaceVariant.copy(alpha = 0.32f),
            codeFontSize = codeSize,
        )
    }
    val annotated = remember(text, styles) {
        InlineMarkdownParser.parse(text, styles)
    }
    Text(
        text = annotated,
        style = style,
        modifier = modifier,
    )
}
