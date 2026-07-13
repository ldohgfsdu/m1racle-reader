package com.markleaf.app.ui.viewer.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.markleaf.app.R
import com.markleaf.app.data.settings.ContentPaddingLevel
import com.markleaf.app.data.settings.ReaderSettings
import com.markleaf.app.ui.theme.sheetContainer
import androidx.compose.material3.BottomSheetDefaults
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(
    settings: ReaderSettings,
    onFontScale: (Float) -> Unit,
    onLineHeightScale: (Float) -> Unit,
    onPaddingLevel: (ContentPaddingLevel) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    // skipPartiallyExpanded: open fully to content height (not half-screen).
    // No fillMaxHeight / minHeight — Column wraps content so short settings stay short.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scheme = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = scheme.sheetContainer,
        contentColor = scheme.onSurface,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = scheme.onSurfaceVariant)
        },
        // Avoid double system-inset padding that leaves a large empty band under content.
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.reader_settings_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(14.dp))

            SettingSectionLabel(stringResource(R.string.reader_settings_font_size))
            ChipRow {
                SettingChip(
                    label = stringResource(R.string.reader_settings_font_small),
                    selected = near(settings.fontScale, ReaderSettings.FONT_SMALL),
                    onClick = { onFontScale(ReaderSettings.FONT_SMALL) },
                )
                SettingChip(
                    label = stringResource(R.string.reader_settings_font_default),
                    selected = near(settings.fontScale, ReaderSettings.FONT_DEFAULT),
                    onClick = { onFontScale(ReaderSettings.FONT_DEFAULT) },
                )
                SettingChip(
                    label = stringResource(R.string.reader_settings_font_large),
                    selected = near(settings.fontScale, ReaderSettings.FONT_LARGE),
                    onClick = { onFontScale(ReaderSettings.FONT_LARGE) },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            SettingSectionLabel(stringResource(R.string.reader_settings_line_height))
            ChipRow {
                SettingChip(
                    label = stringResource(R.string.reader_settings_line_compact),
                    selected = near(settings.lineHeightScale, ReaderSettings.LINE_COMPACT),
                    onClick = { onLineHeightScale(ReaderSettings.LINE_COMPACT) },
                )
                SettingChip(
                    label = stringResource(R.string.reader_settings_line_default),
                    selected = near(settings.lineHeightScale, ReaderSettings.LINE_DEFAULT),
                    onClick = { onLineHeightScale(ReaderSettings.LINE_DEFAULT) },
                )
                SettingChip(
                    label = stringResource(R.string.reader_settings_line_relaxed),
                    selected = near(settings.lineHeightScale, ReaderSettings.LINE_RELAXED),
                    onClick = { onLineHeightScale(ReaderSettings.LINE_RELAXED) },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            SettingSectionLabel(stringResource(R.string.reader_settings_padding))
            ChipRow {
                SettingChip(
                    label = stringResource(R.string.reader_settings_padding_compact),
                    selected = settings.contentPaddingLevel == ContentPaddingLevel.Compact,
                    onClick = { onPaddingLevel(ContentPaddingLevel.Compact) },
                )
                SettingChip(
                    label = stringResource(R.string.reader_settings_padding_standard),
                    selected = settings.contentPaddingLevel == ContentPaddingLevel.Standard,
                    onClick = { onPaddingLevel(ContentPaddingLevel.Standard) },
                )
                SettingChip(
                    label = stringResource(R.string.reader_settings_padding_relaxed),
                    selected = settings.contentPaddingLevel == ContentPaddingLevel.Relaxed,
                    onClick = { onPaddingLevel(ContentPaddingLevel.Relaxed) },
                )
            }

            Spacer(modifier = Modifier.height(2.dp))
            TextButton(onClick = onReset) {
                Text(
                    text = stringResource(R.string.reader_settings_reset),
                    fontWeight = FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun SettingSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Normal),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = { content() },
    )
}

@Composable
private fun SettingChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                fontWeight = FontWeight.Normal,
            )
        },
    )
}

private fun near(a: Float, b: Float, eps: Float = 0.02f): Boolean = abs(a - b) < eps
