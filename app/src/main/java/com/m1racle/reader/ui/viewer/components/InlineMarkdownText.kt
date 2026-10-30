package com.m1racle.reader.ui.viewer.components

import com.m1racle.reader.ui.theme.M1racleReaderTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.m1racle.reader.data.settings.ReaderSettings
import com.m1racle.reader.markdown.InlineMarkdownParser
import com.m1racle.reader.markdown.InlineMarkdownStyles
import com.m1racle.reader.search.BlockSearchHighlights
import com.m1racle.reader.search.SearchHighlightRange
import com.m1racle.reader.search.withSearchHighlights
import com.m1racle.reader.ui.viewer.components.ReaderTypography.scaled

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
    annotationOffset: Int? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val linkColor = M1racleReaderTheme.extra.link
    val codeSize = ReaderTypography.inlineCodeFontSize.scaled(settings.fontScale)
    // Keep code distinct without a heavy chip-shaped fill.
    val codeBg = colorScheme.surfaceContainerHigh
    val styles = remember(linkColor, codeBg, codeSize, strongColor) {
        InlineMarkdownStyles.themed(
            primary = linkColor,
            codeBackground = codeBg,
            codeFontSize = codeSize,
            boldColor = strongColor,
        )
    }
    val hasSearch = searchRanges.isNotEmpty()
    // Rendering text equals the search plain-text projection (parse keeps the
    // same walk as toPlainText), so search offsets apply directly.
    val rawRanges = searchRanges
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
        remember(rawRanges) {
            rawRanges.map { Triple(it.start, it.end, it.listItemIndex) }
        }
    } else {
        null
    }
    val activeKey = if (hasSearch) {
        remember(rawRanges) {
            rawRanges.firstOrNull { it.isActive }?.let { it.start to it.end }
        }
    } else {
        null
    }
    val searched = if (!hasSearch) {
        baseAnnotated
    } else {
        val withNormals = remember(baseAnnotated, normalKey, normalHl) {
            val normals = rawRanges.map {
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
    val inlineOffset = annotationOffset ?: LocalMarkdownInlineOffset.current
    val importedFont = com.m1racle.reader.ui.components.LocalReaderImportedFont.current
    val resolvedStyle = remember(style, importedFont) {
        style.copy(
            fontFamily = importedFont ?: style.fontFamily ?: FontFamily.Default,
            fontWeight = style.fontWeight ?: FontWeight.Normal,
        )
    }
    CompositionLocalProvider(LocalTextStyle provides resolvedStyle) {
        MarkdownSelectableText(
            searched = searched,
            style = resolvedStyle,
            modifier = modifier,
            annotationOffset = inlineOffset,
            onLinkClick = onLinkClick,
        )
    }
}
