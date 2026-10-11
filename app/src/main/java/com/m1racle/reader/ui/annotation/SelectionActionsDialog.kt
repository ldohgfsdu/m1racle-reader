package com.m1racle.reader.ui.annotation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.m1racle.reader.data.anchor.HighlightStyle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelectionActionsDialog(selection: TextSelection, onDismiss: () -> Unit,
    onHighlight: (HighlightStyle) -> Unit, onNote: (() -> Unit)? = null) {
    var style by remember(selection) { mutableStateOf(HighlightStyle.Yellow) }
    val clipboard = LocalClipboardManager.current
    AlertDialog(onDismissRequest = onDismiss, title = { Text("所选文字") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SelectionContainer { Text(selection.excerpt.orEmpty(), Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HighlightStyle.entries.forEach { option -> FilterChip(selected = style == option, onClick = { style = option }, label = { Text(option.label) }) }
            }
            Row {
                TextButton(onClick = { clipboard.setText(AnnotatedString(selection.excerpt.orEmpty())); onDismiss() }, enabled = !selection.excerpt.isNullOrEmpty()) { Text("复制") }
                if (onNote != null) TextButton(onClick = onNote) { Text("写笔记") }
            }
        }
    }, confirmButton = { TextButton(onClick = { onHighlight(style) }) { Text("标记") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
