package com.markleaf.app.ui.viewer.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.markleaf.app.R
import com.markleaf.app.data.settings.ReaderSettings
import com.markleaf.app.markdown.MarkdownBlock
import com.markleaf.app.search.SearchMatch
import com.markleaf.app.search.highlightPlainText

@Composable
fun CodeBlockView(
    block: MarkdownBlock.CodeBlock,
    settings: ReaderSettings,
    onCopyMessage: (String) -> Unit,
    activeMatch: SearchMatch? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(10.dp)
    // Slightly deeper than page in light mode; slightly lighter in dark (surfaceVariant).
    val bg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
    val toolbarBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
    val codeSize = ReaderTypography.codeBlockSize(settings.fontScale)
    val codeLine = ReaderTypography.codeBlockLine(settings.fontScale, settings.lineHeightScale)
    val languageLabel = formatLanguageLabel(block.language)
    val copiedMsg = stringResource(R.string.code_copied)
    val failedMsg = stringResource(R.string.code_copy_failed)

    val codeAnnotated = highlightPlainText(
        text = block.code.ifEmpty { " " },
        activeStart = activeMatch?.matchStart,
        activeEnd = activeMatch?.matchEnd,
        highlightColor = searchFragmentHighlightColor(),
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .searchBlockHighlight(activeMatch != null)
            .clip(shape)
            .background(bg),
    ) {
        // Toolbar: language + copy
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(toolbarBg)
                .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = languageLabel,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Medium,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = stringResource(R.string.code_copy),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // Code body — width constrained by parent; long lines scroll horizontally only.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
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
                color = MaterialTheme.colorScheme.onSurface,
                softWrap = false,
            )
        }
    }
}

private fun formatLanguageLabel(language: String?): String {
    val trimmed = language?.trim().orEmpty()
    return if (trimmed.isEmpty()) "code" else trimmed.lowercase()
}

/**
 * Copy plain text to the system clipboard. No permissions required.
 */
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
