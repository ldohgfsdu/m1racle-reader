package com.m1racle.reader.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.m1racle.reader.M1racleReaderApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

val LocalReaderImportedFont = staticCompositionLocalOf<FontFamily?> { null }

@Composable
fun ReaderFontContent(content: @Composable () -> Unit) {
    val app = LocalContext.current.applicationContext as? M1racleReaderApplication
    if (app == null) { content(); return }
    val settings by app.readerSettingsRepository.settingsFlow.collectAsState(initial = null)
    val fonts by app.readerFontRepository.fonts.collectAsState()
    if (settings == null || fonts == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val font = fonts?.firstOrNull { it.id == settings?.importedFontId }
    val family = remember(font) { font?.let { FontFamily(android.graphics.Typeface.createFromFile(it.file)) } }
    CompositionLocalProvider(LocalReaderImportedFont provides family, content = content)
}

/** The same private library serves the hub and both reflowable readers. */
@Composable
fun ReaderFontPicker(modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as? M1racleReaderApplication ?: return
    val fonts by app.readerFontRepository.fonts.collectAsState()
    val settings by app.readerSettingsRepository.settingsFlow.collectAsState(initial = null)
    var open by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun run(action: suspend () -> Unit) { scope.launch {
        busy = true; error = null
        try { action() } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "字体操作失败" }
        finally { busy = false }
    } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) run {
            val font = app.readerFontRepository.import(uri)
            app.readerSettingsRepository.updateImportedFont(font.id)
        }
    }
    val selected = fonts?.firstOrNull { it.id == settings?.importedFontId }
    TextButton(onClick = { open = true }, modifier = modifier.fillMaxWidth()) {
        Text(selected?.let { "字体：${it.name}" } ?: "导入 / 管理字体", maxLines = 2)
    }
    if (open) AlertDialog(
        onDismissRequest = { if (!busy) open = false },
        title = { Text("阅读字体") },
        text = { Column {
            Text("支持 TTF / OTF，最大 50 MB。用于 EPUB 和 Markdown 正文。",
                style = MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                item {
                    TextButton(enabled = !busy, onClick = { run {
                        app.readerSettingsRepository.updateImportedFont(null)
                    } }) { Text(if (selected == null) "✓ 默认字体" else "默认字体") }
                }
                items(fonts.orEmpty(), key = { it.id }) { font ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(enabled = !busy, modifier = Modifier.weight(1f), onClick = { run {
                            app.readerSettingsRepository.updateImportedFont(font.id)
                        } }) { Text((if (selected?.id == font.id) "✓ " else "") + font.name, maxLines = 2) }
                        TextButton(enabled = !busy, onClick = { run {
                            app.readerFontRepository.remove(font, app.readerSettingsRepository)
                        } }) { Text("移除", modifier = Modifier, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(enabled = !busy && fonts != null, onClick = { picker.launch(arrayOf("*/*")) }) {
            Text("导入字体")
        } },
        dismissButton = { TextButton(enabled = !busy, onClick = { open = false }) { Text("完成") } },
    )
}
