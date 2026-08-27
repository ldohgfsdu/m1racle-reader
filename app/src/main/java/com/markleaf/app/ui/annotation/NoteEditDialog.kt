package com.markleaf.app.ui.annotation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.markleaf.app.R

/**
 * R27 轻量批注编辑器（创建/编辑双模式）。非空校验在本地；删除仅在编辑态提供。
 * 不承载任何富文本能力——保持「轻量 Note 闭环」边界。
 */
@Composable
fun NoteEditDialog(
    title: String,
    initialContent: String,
    isEditing: Boolean,
    onSave: (String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialContent) }
    val trimmed = text.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
        },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.reader_note_hint)) },
                    minLines = 3,
                    maxLines = 6,
                    singleLine = false,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = trimmed.isNotEmpty(),
                onClick = { onSave(trimmed) },
            ) { Text(stringResource(R.string.reader_note_save)) }
        },
        dismissButton = {
            Row {
                if (isEditing && onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(
                            stringResource(R.string.delete_record),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
    )
}
