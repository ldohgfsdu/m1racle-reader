package com.markleaf.app.ui.viewer.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.markleaf.app.data.settings.ReaderSettings
import com.markleaf.app.markdown.InlineMarkdownParser
import com.markleaf.app.markdown.InlineMarkdownStyles
import com.markleaf.app.ui.viewer.components.ReaderTypography.scaled

/**
 * Body text with lightweight inline Markdown (bold / italic / code / link).
 *
 * Base [style] applies to unstyled runs; **strong** / *em* / code / link spans
 * from the parser override per range and are not flattened.
 *
 * @param strongColor optional color for **strong** spans (e.g. near-black in quotes).
 */
@Composable
fun InlineMarkdownText(
    text: String,
    settings: ReaderSettings,
    style: TextStyle,
    modifier: Modifier = Modifier,
    strongColor: Color? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val codeSize = ReaderTypography.inlineCodeFontSize.scaled(settings.fontScale)
    val codeBg = colorScheme.surfaceContainerHighest
    val styles = remember(colorScheme.primary, codeBg, codeSize, strongColor) {
        InlineMarkdownStyles.themed(
            primary = colorScheme.primary,
            codeBackground = codeBg,
            codeFontSize = codeSize,
            boldColor = strongColor,
        )
    }
    val annotated = remember(text, styles) {
        InlineMarkdownParser.parse(text, styles)
    }
    val resolvedStyle = style.copy(
        fontFamily = style.fontFamily ?: FontFamily.Default,
        fontWeight = style.fontWeight ?: FontWeight.Normal,
    )
    // Clear ambient Medium/SemiBold so base runs stay as [style]; spans still apply.
    CompositionLocalProvider(LocalTextStyle provides resolvedStyle) {
        Text(
            text = annotated,
            style = resolvedStyle,
            modifier = modifier,
        )
    }
}
