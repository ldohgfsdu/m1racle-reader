package com.markleaf.app.ui.viewer.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.markleaf.app.R
import com.markleaf.app.data.settings.ReaderSettings
import com.markleaf.app.highlight.CodeHighlighter
import com.markleaf.app.markdown.MarkdownBlock
import com.markleaf.app.search.BlockSearchHighlights
import com.markleaf.app.search.withSearchHighlights
// EmptyRanges for search-closed

private object CodeBlockChrome {
    val corner = 12.dp
    val headerMinHeight = 36.dp
    val bodyMinHeightEmpty = 40.dp
    val bodyPadH = 16.dp
    val bodyPadV = 14.dp
    const val MAX_FONT_SP = 15.5f
}

@Composable
fun CodeBlockView(
    block: MarkdownBlock.CodeBlock,
    settings: ReaderSettings,
    onCopyMessage: (String) -> Unit,
    searchHighlights: BlockSearchHighlights? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(CodeBlockChrome.corner)
    val scheme = MaterialTheme.colorScheme
    // Semantic surfaces — same roles light/dark, no isDark branching for chrome.
    val headerBg = scheme.surfaceContainerHigh
    val bodyBg = scheme.surfaceContainerLow
    val border = scheme.outline
    val labelColor = scheme.onSurfaceVariant
    val iconColor = scheme.onSurfaceVariant
    val plainCode = scheme.onSurface
    // Highlighter still needs luminance; derive from background without scattering UI isDark.
    val isDarkHighlight = scheme.background.luminance() < 0.5f

    val codeSize = localCodeFontSize(settings.fontScale)
    // Code keeps independent line height (not body line-height presets).
    val codeLine = ReaderTypography.codeBlockLine(settings.fontScale)
    val languageLabel = formatLanguageLabel(block.language)
    val copiedMsg = stringResource(R.string.code_copied)
    val failedMsg = stringResource(R.string.code_copy_failed)
    val isEmptyCode = block.code.isEmpty()

    val ranges = searchHighlights?.forWholeBlock() ?: BlockSearchHighlights.EmptyRanges
    val hasSearch = ranges.isNotEmpty()
    val normalHl = if (hasSearch) searchMatchNormalBackground() else Color.Unspecified
    val activeHl = if (hasSearch) searchMatchActiveBackground() else Color.Unspecified
    val codeBase = remember(block.code, block.language, isDarkHighlight) {
        CodeHighlighter.highlightCode(
            code = block.code.ifEmpty { " " },
            language = block.language,
            isDarkTheme = isDarkHighlight,
        )
    }
    val codeAnnotated = if (!hasSearch) {
        codeBase
    } else {
        remember(codeBase, ranges, normalHl, activeHl) {
            codeBase.withSearchHighlights(ranges, normalHl, activeHl)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .clip(shape)
            .border(1.dp, border, shape)
            .background(bodyBg),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerBg)
                .defaultMinSize(minHeight = CodeBlockChrome.headerMinHeight)
                .padding(start = 16.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = languageLabel,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    letterSpacing = 0.2.sp,
                ),
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
            )
            IconButton(
                onClick = {
                    onCopyMessage(
                        copyCodeToClipboard(
                            context = context,
                            code = block.code,
                            successMessage = copiedMsg,
                            failureMessage = failedMsg,
                        ),
                    )
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = stringResource(R.string.code_copy),
                    tint = iconColor,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(bodyBg)
                .defaultMinSize(
                    minHeight = if (isEmptyCode) CodeBlockChrome.bodyMinHeightEmpty else 0.dp,
                )
                .padding(
                    horizontal = CodeBlockChrome.bodyPadH,
                    vertical = CodeBlockChrome.bodyPadV,
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                Text(
                    text = codeAnnotated,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = codeSize,
                        lineHeight = codeLine,
                        fontWeight = FontWeight.Normal,
                    ),
                    color = plainCode,
                    softWrap = false,
                )
            }
        }
    }
}

private fun localCodeFontSize(fontScale: Float) =
    (ReaderTypography.codeBlockFontSize.value * fontScale)
        .coerceAtMost(CodeBlockChrome.MAX_FONT_SP)
        .sp

private fun formatLanguageLabel(language: String?): String {
    val trimmed = language?.trim().orEmpty()
    return if (trimmed.isEmpty()) "code" else trimmed.lowercase()
}

private fun copyCodeToClipboard(
    context: Context,
    code: String,
    successMessage: String,
    failureMessage: String,
): String {
    return try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return failureMessage
        clipboard.setPrimaryClip(ClipData.newPlainText("code", code))
        successMessage
    } catch (_: Exception) {
        failureMessage
    }
}
