package com.markleaf.app.ui.pdf

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.markleaf.app.R
import com.markleaf.app.pdf.export.ChapterExportState
import com.markleaf.app.pdf.export.ExportRangePlan
import com.markleaf.app.pdf.export.ExportRangeResolver
import com.markleaf.app.ui.theme.dialogContainer
import com.markleaf.app.ui.theme.markLeafOutlinedFieldColors

@Composable
fun ChapterExportConfirmDialog(
    plan: ExportRangePlan,
    documentPageCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (startIndex0: Int, endIndex0: Int) -> Unit,
) {
    var startText by remember(plan) {
        mutableStateOf(plan.pdfPageStart1Based.toString())
    }
    var endText by remember(plan) {
        mutableStateOf(plan.pdfPageEnd1Based.toString())
    }
    var error by remember { mutableStateOf<String?>(null) }

    val pathLabel = plan.pathTitles.joinToString("\n")

    fun submit() {
        val s1 = startText.toIntOrNull()
        val e1 = endText.toIntOrNull()
        if (s1 == null || e1 == null) {
            error = "请输入有效整数页码"
            return
        }
        val s0 = s1 - 1
        val e0 = e1 - 1
        val err = ExportRangeResolver.validateRange(s0, e0, documentPageCount)
        if (err != null) {
            error = err
            return
        }
        onConfirm(s0, e0)
    }

    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = scheme.dialogContainer,
        titleContentColor = scheme.onSurface,
        textContentColor = scheme.onSurfaceVariant,
        title = { Text(stringResource(R.string.pdf_export_title)) },
        text = {
            Column {
                Text(
                    text = pathLabel.ifBlank { plan.node.title },
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = scheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.pdf_export_range_info,
                        plan.pdfPageStart1Based,
                        plan.pdfPageEnd1Based,
                        plan.pageCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (plan.printPageStart != null && plan.printPageEnd != null) {
                    Text(
                        text = stringResource(
                            R.string.pdf_export_print_range,
                            plan.printPageStart,
                            plan.printPageEnd,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.pdf_export_print_unknown),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(
                        R.string.pdf_export_end_source,
                        plan.endSource.labelZh(),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = startText,
                    onValueChange = {
                        startText = it.filter { c -> c.isDigit() }.take(6)
                        error = null
                    },
                    label = { Text(stringResource(R.string.pdf_export_start_page)) },
                    singleLine = true,
                    colors = markLeafOutlinedFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = endText,
                    onValueChange = {
                        endText = it.filter { c -> c.isDigit() }.take(6)
                        error = null
                    },
                    label = { Text(stringResource(R.string.pdf_export_end_page)) },
                    singleLine = true,
                    colors = markLeafOutlinedFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { submit() }) {
                Text(stringResource(R.string.pdf_export_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.pdf_dialog_cancel))
            }
        },
    )
}

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

    AlertDialog(
        onDismissRequest = {
            if (state is ChapterExportState.Completed ||
                state is ChapterExportState.Failed ||
                state is ChapterExportState.Cancelled
            ) {
                onDismiss()
            }
        },
        containerColor = MaterialTheme.colorScheme.dialogContainer,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        Text(stringResource(R.string.pdf_export_zipping))
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
