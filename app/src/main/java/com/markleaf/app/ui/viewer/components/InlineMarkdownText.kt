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
import com.markleaf.app.search.BlockSearchHighlights
import com.markleaf.app.search.SearchHighlightRange
import com.markleaf.app.search.withSearchHighlights
import com.markleaf.app.ui.viewer.components.ReaderTypography.scaled

/**
 * Body text with lightweight inline Markdown (bold / italic / code / link).
 *
 * When [searchRanges] is empty (search closed / no local hits), the highlight path is
 * never entered: no theme color reads for highlight, no Builder, identity AnnotatedString.
 */
@Composable
fun InlineMarkdownText(
    text: String,
    settings: ReaderSettings,
    style: TextStyle,
    modifier: Modifier = Modifier,
    strongColor: Color? = null,
    onLinkClick: ((String) -> Unit)? = null,
    searchRanges: List<SearchHighlightRange> = BlockSearchHighlights.EmptyRanges,
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
    val hasSearch = searchRanges.isNotEmpty()
    // Only subscribe to highlight theme when painting — avoids every Text reading
    // tertiaryContainer on the search-closed scroll path.
    val normalHl = if (hasSearch) searchMatchNormalBackground() else Color.Unspecified
    val activeHl = if (hasSearch) searchMatchActiveBackground() else Color.Unspecified

    // Base parse memoized independently of highlight overlay.
    val baseAnnotated = remember(text, styles) {
        InlineMarkdownParser.parse(text, styles)
    }
    // Normal-only ranges key (ignore isActive) so next/prev does not rebuild all normal spans.
    val normalKey = if (hasSearch) {
        remember(searchRanges) {
            searchRanges.map { Triple(it.start, it.end, it.listItemIndex) }
        }
    } else {
        null
    }
    val activeKey = if (hasSearch) {
        remember(searchRanges) {
            searchRanges.firstOrNull { it.isActive }?.let { it.start to it.end }
        }
    } else {
        null
    }
    val annotated = if (!hasSearch) {
        baseAnnotated
    } else {
        val withNormals = remember(baseAnnotated, normalKey, normalHl) {
            val normals = searchRanges.map {
                it.copy(isActive = false)
            }
            baseAnnotated.withSearchHighlights(normals, normalHl, activeHl)
        }
        if (activeKey == null) {
            withNormals
        } else {
            remember(withNormals, activeKey, activeHl) {
                val onlyActive = listOf(
                    SearchHighlightRange(
                        start = activeKey.first,
                        end = activeKey.second,
                        isActive = true,
                    ),
                )
                withNormals.withSearchHighlights(onlyActive, normalHl, activeHl)
            }
        }
    }
    val resolvedStyle = remember(style) {
        style.copy(
            fontFamily = style.fontFamily ?: FontFamily.Default,
            fontWeight = style.fontWeight ?: FontWeight.Normal,
        )
    }
    // Link lookup is keyed by the annotated result — not re-scanned every frame for free.
    val hasLinks = remember(annotated, onLinkClick != null) {
        onLinkClick != null && annotated.getStringAnnotations(
            tag = InlineMarkdownParser.LINK_ANNOTATION_TAG,
            start = 0,
            end = annotated.length,
        ).isNotEmpty()
    }
    CompositionLocalProvider(LocalTextStyle provides resolvedStyle) {
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
