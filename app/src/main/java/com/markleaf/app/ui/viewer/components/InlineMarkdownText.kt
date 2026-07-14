package com.markleaf.app.ui.viewer.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.text.ClickableText
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
    onLinkClick: ((String) -> Unit)? = null,
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
        val hasLinks = onLinkClick != null && annotated.getStringAnnotations(
            tag = InlineMarkdownParser.LINK_ANNOTATION_TAG,
            start = 0,
            end = annotated.length,
        ).isNotEmpty()
        if (hasLinks) {
            @Suppress("DEPRECATION")
            ClickableText(
                text = annotated,
                style = resolvedStyle,
                modifier = modifier,
                onClick = { offset ->
                    annotated.getStringAnnotations(
                        tag = InlineMarkdownParser.LINK_ANNOTATION_TAG,
                        start = offset,
                        end = offset,
                    ).firstOrNull()?.let { onLinkClick?.invoke(it.item) }
                },
            )
        } else {
            Text(
                text = annotated,
                style = resolvedStyle,
                modifier = modifier,
            )
        }
    }
}
