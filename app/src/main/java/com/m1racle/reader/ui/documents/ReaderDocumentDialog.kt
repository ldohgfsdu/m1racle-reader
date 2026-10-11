package com.m1racle.reader.ui.documents

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.m1racle.reader.ui.home.LocalDocumentOperations
import com.m1racle.reader.ui.home.documentLocation

@Composable
fun ReaderDocumentDialog(id: String, onDismiss: () -> Unit) {
    val operations = LocalDocumentOperations.current ?: return
    val document by remember(operations, id) { operations.document(id) }.collectAsState(initial = null)
    val doc = document ?: return
    val context = LocalContext.current
    var renaming by remember { mutableStateOf(false) }
    var name by remember(doc.displayName) { mutableStateOf(doc.displayName) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(doc.mimeType ?: "application/octet-stream")) { uri ->
        if (uri != null) operations.export(id, uri)
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("文件信息") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SelectionContainer { Text(doc.displayName) }
            Text(listOfNotNull(doc.type.name, doc.fileSize?.let { android.text.format.Formatter.formatShortFileSize(context, it) }).joinToString(" · "))
            Text("本地副本", style = MaterialTheme.typography.labelMedium)
            SelectionContainer { Text(documentLocation(doc.uri)) }
            doc.sourceUri?.takeIf { it != doc.uri }?.let { source ->
                Text("原文件位置", style = MaterialTheme.typography.labelMedium)
                SelectionContainer { Text(documentLocation(source)) }
            }
            TextButton(onClick = { renaming = true }) { Text("重命名") }
            TextButton(onClick = { export.launch(doc.displayName) }) { Text("导出完整文件") }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } })
    if (renaming) AlertDialog(onDismissRequest = { renaming = false }, title = { Text("重命名") }, text = {
        Column {
            OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("书库名称") })
            Text("保留阅读位置和批注，原文件名称不变。", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = { operations.rename(id, name) { renaming = false } }, enabled = name.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = { renaming = false }) { Text("取消") } })
}
