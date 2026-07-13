package com.markleaf.app.ui.pdf

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.markleaf.app.R
import com.markleaf.app.data.local.TextbookOutlineNodeEntity
import com.markleaf.app.data.local.TextbookOutlineNodeType
import com.markleaf.app.pdf.TextbookOutlineResolver
import com.markleaf.app.ui.theme.dialogContainer
import com.markleaf.app.ui.theme.markLeafOutlinedFieldColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutlineEditDialog(
    state: OutlineEditorState,
    allNodes: List<TextbookOutlineNodeEntity>,
    pageCount: Int,
    onDismiss: () -> Unit,
    onSave: (
        id: String?,
        parentId: String?,
        type: TextbookOutlineNodeType,
        title: String,
        startPageIndex: Int,
        endPageIndex: Int,
    ) -> Unit,
    onPickRangeFromThumbs: () -> Unit,
    onUseCurrentPage: () -> Unit,
) {
    var title by remember { mutableStateOf(state.title) }
    var type by remember { mutableStateOf(state.type) }
    var parentId by remember { mutableStateOf(state.parentId) }
    var startText by remember { mutableStateOf((state.startPageIndex + 1).toString()) }
    var endText by remember { mutableStateOf((state.endPageIndex + 1).toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    var typeExpanded by remember { mutableStateOf(false) }
    var parentExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        title = state.title
        type = state.type
        parentId = state.parentId
        startText = (state.startPageIndex + 1).toString()
        endText = (state.endPageIndex + 1).toString()
        error = null
    }

    val parentOptions = remember(allNodes, state.id) {
        val forbidden = if (state.id != null) {
            TextbookOutlineResolver.subtreeIds(state.id, allNodes)
        } else {
            emptySet()
        }
        allNodes.filter { it.id !in forbidden }
    }

    val parentLabel = parentOptions.firstOrNull { it.id == parentId }?.let {
        "${it.nodeType.displayLabelZh()} · ${it.title}"
    } ?: stringResource(R.string.pdf_outline_parent_none)

    fun submit() {
        val startH = startText.trim().toIntOrNull()
        val endH = endText.trim().toIntOrNull()
        if (title.isBlank()) {
            error = "请输入标题"
            return
        }
        if (startH == null || endH == null || startH < 1 || endH < 1 ||
            startH > pageCount || endH > pageCount
        ) {
            error = "页码须为 1 – $pageCount"
            return
        }
        val (s, e) = TextbookOutlineResolver.normalizeRange(startH - 1, endH - 1, pageCount)
        onSave(state.id, parentId, type, title.trim(), s, e)
    }

    val scheme = MaterialTheme.colorScheme
    val fieldColors = markLeafOutlinedFieldColors()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = scheme.dialogContainer,
        titleContentColor = scheme.onSurface,
        textContentColor = scheme.onSurfaceVariant,
        title = {
            Text(
                if (state.id == null) {
                    stringResource(R.string.pdf_outline_add)
                } else {
                    stringResource(R.string.pdf_outline_edit)
                },
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; error = null },
                    label = { Text(stringResource(R.string.pdf_outline_field_title)) },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it },
                ) {
                    OutlinedTextField(
                        value = type.displayLabelZh(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.pdf_outline_field_type)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded)
                        },
                        colors = fieldColors,
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false },
                    ) {
                        TextbookOutlineNodeType.entries.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t.displayLabelZh()) },
                                onClick = {
                                    type = t
                                    typeExpanded = false
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = parentExpanded,
                    onExpandedChange = { parentExpanded = it },
                ) {
                    OutlinedTextField(
                        value = parentLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.pdf_outline_field_parent)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = parentExpanded)
                        },
                        colors = fieldColors,
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = parentExpanded,
                        onDismissRequest = { parentExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.pdf_outline_parent_none)) },
                            onClick = {
                                parentId = null
                                parentExpanded = false
                            },
                        )
                        parentOptions.forEach { n ->
                            DropdownMenuItem(
                                text = {
                                    Text("${n.nodeType.displayLabelZh()} · ${n.title}")
                                },
                                onClick = {
                                    parentId = n.id
                                    parentExpanded = false
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = startText,
                    onValueChange = {
                        startText = it.filter { ch -> ch.isDigit() }.take(6)
                        error = null
                    },
                    label = { Text(stringResource(R.string.pdf_outline_start_page)) },
                    singleLine = true,
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = endText,
                    onValueChange = {
                        endText = it.filter { ch -> ch.isDigit() }.take(6)
                        error = null
                    },
                    label = { Text(stringResource(R.string.pdf_outline_end_page)) },
                    singleLine = true,
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = onUseCurrentPage) {
                    Text(stringResource(R.string.pdf_outline_use_current))
                }
                TextButton(onClick = onPickRangeFromThumbs) {
                    Text(stringResource(R.string.pdf_outline_pick_thumbs))
                }
                if (error != null) {
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
                Text(stringResource(R.string.pdf_outline_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.pdf_dialog_cancel))
            }
        },
    )
}
