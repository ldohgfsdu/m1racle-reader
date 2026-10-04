package com.m1racle.reader.ui.viewer.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.m1racle.reader.ui.components.PillSegmentedControl
import com.m1racle.reader.R
import com.m1racle.reader.data.settings.ContentPaddingLevel
import com.m1racle.reader.data.settings.ReaderSettings
import com.m1racle.reader.ui.theme.sheetContainer
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
                .verticalScroll(rememberScrollState())
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
            PillSegmentedControl(
                labels = listOf(
                    stringResource(R.string.reader_settings_font_small),
                    stringResource(R.string.reader_settings_font_default),
                    stringResource(R.string.reader_settings_font_large),
                ),
                selectedIndex = listOf(near(settings.fontScale, ReaderSettings.FONT_SMALL), near(settings.fontScale, ReaderSettings.FONT_DEFAULT), near(settings.fontScale, ReaderSettings.FONT_LARGE)).indexOf(true),
                onSelect = { index ->
                    when (index) {
                        0 -> onFontScale(ReaderSettings.FONT_SMALL)
                        1 -> onFontScale(ReaderSettings.FONT_DEFAULT)
                        2 -> onFontScale(ReaderSettings.FONT_LARGE)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))
            SettingSectionLabel(stringResource(R.string.reader_settings_line_height))
            PillSegmentedControl(
                labels = listOf(
                    stringResource(R.string.reader_settings_line_compact),
                    stringResource(R.string.reader_settings_line_default),
                    stringResource(R.string.reader_settings_line_relaxed),
                ),
                selectedIndex = listOf(near(settings.lineHeightScale, ReaderSettings.LINE_COMPACT), near(settings.lineHeightScale, ReaderSettings.LINE_DEFAULT), near(settings.lineHeightScale, ReaderSettings.LINE_RELAXED)).indexOf(true),
                onSelect = { index ->
                    when (index) {
                        0 -> onLineHeightScale(ReaderSettings.LINE_COMPACT)
                        1 -> onLineHeightScale(ReaderSettings.LINE_DEFAULT)
                        2 -> onLineHeightScale(ReaderSettings.LINE_RELAXED)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))
            SettingSectionLabel(stringResource(R.string.reader_settings_padding))
            PillSegmentedControl(
                labels = listOf(
                    stringResource(R.string.reader_settings_padding_compact),
                    stringResource(R.string.reader_settings_padding_standard),
                    stringResource(R.string.reader_settings_padding_relaxed),
                ),
                selectedIndex = listOf(settings.contentPaddingLevel == ContentPaddingLevel.Compact, settings.contentPaddingLevel == ContentPaddingLevel.Standard, settings.contentPaddingLevel == ContentPaddingLevel.Relaxed).indexOf(true),
                onSelect = { index ->
                    when (index) {
                        0 -> onPaddingLevel(ContentPaddingLevel.Compact)
                        1 -> onPaddingLevel(ContentPaddingLevel.Standard)
                        2 -> onPaddingLevel(ContentPaddingLevel.Relaxed)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

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

private fun near(a: Float, b: Float, eps: Float = 0.02f): Boolean = abs(a - b) < eps
