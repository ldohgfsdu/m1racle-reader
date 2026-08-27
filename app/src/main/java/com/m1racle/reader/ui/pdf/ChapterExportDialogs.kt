package com.m1racle.reader.ui.pdf

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.m1racle.reader.R
import com.m1racle.reader.pdf.export.ChapterExportState
import com.m1racle.reader.ui.material.GlassAlertDialog

/**
 * Export progress — the system-flow status surface (kept; it is a dialog,
 * not a Reader More BottomSheet).
 */
@Composable
fun ChapterExportProgressDialog(
    state: ChapterExportState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    val canCancel = state is ChapterExportState.Preparing ||
        state is ChapterExportState.CopyingPages ||
        state is ChapterExportState.BuildingMetadata ||
        state is ChapterExportState.WritingArchive

    GlassAlertDialog(
        onDismissRequest = {
            if (state is ChapterExportState.Completed ||
                state is ChapterExportState.Failed ||
                state is ChapterExportState.Cancelled
            ) {
                onDismiss()
            }
        },
        title = { Text(stringResource(R.string.pdf_export_progress_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when (state) {
                    ChapterExportState.Preparing -> {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(stringResource(R.string.pdf_export_preparing))
                    }
                    is ChapterExportState.CopyingPages -> {
                        LinearProgressIndicator(
                            progress = {
                                if (state.total <= 0) 0f
                                else state.current.toFloat() / state.total.toFloat()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(
                                R.string.pdf_export_copying,
                                state.current,
                                state.total,
                            ),
                        )
                    }
                    ChapterExportState.BuildingMetadata -> {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(stringResource(R.string.pdf_export_metadata))
                    }
                    ChapterExportState.WritingArchive -> {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(stringResource(R.string.pdf_export_writing))
                    }
                    is ChapterExportState.Completed -> {
                        Text(
                            stringResource(R.string.pdf_export_done, state.displayName),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    ChapterExportState.Cancelled -> {
                        Text(stringResource(R.string.pdf_export_cancelled))
                    }
                    is ChapterExportState.Failed -> {
                        Text(
                            stringResource(R.string.pdf_export_failed, state.message),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (state) {
                is ChapterExportState.Completed,
                ChapterExportState.Cancelled,
                -> {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.pdf_export_close))
                    }
                }
                is ChapterExportState.Failed -> {
                    TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.pdf_export_retry))
                    }
                }
                else -> { /* no confirm while running */ }
            }
        },
        dismissButton = {
            if (canCancel) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.pdf_export_cancel_job))
                }
            } else if (state is ChapterExportState.Failed) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.pdf_dialog_cancel))
                }
            }
        },
    )
}
