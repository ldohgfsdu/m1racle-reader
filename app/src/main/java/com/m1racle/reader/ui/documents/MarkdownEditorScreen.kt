package com.m1racle.reader.ui.documents

import android.app.Application
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.data.local.DocumentType
import com.m1racle.reader.data.repository.DocumentRepository
import com.m1racle.reader.data.settings.ReaderSettings
import com.m1racle.reader.markdown.MarkdownParser
import com.m1racle.reader.markdown.MarkdownSource
import com.m1racle.reader.pdf.edit.PdfSourceWriteStore
import com.m1racle.reader.ui.viewer.components.MarkdownBlockView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

val LocalDocumentEditor = staticCompositionLocalOf<((String) -> Unit)?> { null }
val LocalReaderDocumentId = staticCompositionLocalOf<String?> { null }

class MarkdownEditorViewModel(app: Application, private val id: String, private val repository: DocumentRepository) : AndroidViewModel(app) {
    var document by mutableStateOf<DocumentEntity?>(null); private set
    var value by mutableStateOf(TextFieldValue()); private set
    var busy by mutableStateOf(true); private set
    var message by mutableStateOf<String?>(null); private set
    var draftConflict by mutableStateOf(false); private set
    private var original = ""
    private var hash = ""
    private var draftBaseHash: String? = null
    private val drafts = com.m1racle.reader.documents.MarkdownDraftStore(app.filesDir, id)
    private val draftChanges = kotlinx.coroutines.channels.Channel<com.m1racle.reader.documents.MarkdownDraftStore.Draft>(kotlinx.coroutines.channels.Channel.CONFLATED)
    val dirty get() = value.text != original
    private val history = ArrayDeque<TextFieldValue>()
    private val redo = ArrayDeque<TextFieldValue>()
    private val draftWriter = viewModelScope.launch(Dispatchers.IO) {
        for (draft in draftChanges) {
            try { drafts.write(draft) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { withContext(Dispatchers.Main) { message = "草稿备份失败，请及时保存或导出" } }
        }
    }
    init { viewModelScope.launch {
        try {
            val doc = repository.getDocumentById(id) ?: error("文档不存在")
            check(doc.type == DocumentType.MARKDOWN) { "请先将文档导出为独立Markdown文件" }
            val text = repository.readDocumentText(doc.uri, doc.contentSha256).getOrThrow()
            hash = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(Uri.parse(doc.uri))?.use(PdfSourceWriteStore::sha256) ?: error("无法读取文档")
            }
            document = doc; original = text; value = TextFieldValue(text)
            val draft = withContext(Dispatchers.IO) { drafts.read() }
            if (draft != null && draft.text != text) {
                draftBaseHash = draft.baseHash
                value = TextFieldValue(draft.text)
                draftConflict = draft.baseHash != hash
                message = if (draftConflict) "原文已更新，旧草稿已恢复；请导出草稿，核对后重新编辑。" else "已恢复未保存的草稿"
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { message = e.message }
        finally { busy = false }
    } }
    fun change(next: TextFieldValue) {
        if (value.text != next.text) {
            history.addLast(value)
            while (history.size > 50 || (history.size > 1 && history.sumOf { it.text.length.toLong() } > 1_000_000)) history.removeFirst()
            redo.clear()
            draftChanges.trySend(com.m1racle.reader.documents.MarkdownDraftStore.Draft(draftBaseHash ?: hash, next.text))
        }
        value = next
    }
    fun undo() { if (history.isNotEmpty()) { redo.addLast(value); value = history.removeLast(); draftChanges.trySend(com.m1racle.reader.documents.MarkdownDraftStore.Draft(draftBaseHash ?: hash, value.text)) } }
    fun redo() { if (redo.isNotEmpty()) { history.addLast(value); value = redo.removeLast(); draftChanges.trySend(com.m1racle.reader.documents.MarkdownDraftStore.Draft(draftBaseHash ?: hash, value.text)) } }
    fun discardDraft(onDone: () -> Unit) {
        viewModelScope.launch {
            try { draftWriter.cancel(); draftWriter.join(); withContext(Dispatchers.IO) { drafts.delete() }; onDone() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { message = "未能清理草稿，请重试" }
        }
    }
    fun replace(range: IntRange, text: String) { change(TextFieldValue(MarkdownSource.replace(value.text, range, text), TextRange(range.first + text.length))) }
    fun save(source: Uri? = null) {
        if (busy || draftConflict) return
        val text = value.text
        busy = true
        viewModelScope.launch {
            var candidate: File? = null
            try {
                candidate = withContext(Dispatchers.IO) { File.createTempFile("markdown-edit-", ".md", getApplication<Application>().cacheDir).apply { writeText(text, Charsets.UTF_8) } }
                val updated = repository.saveDocumentEdits(id, hash, candidate, source)
                document = updated; hash = requireNotNull(updated.contentSha256); original = text; message = "已保存，阅读记录及批注保留"
                draftBaseHash = hash
                draftChanges.trySend(com.m1racle.reader.documents.MarkdownDraftStore.Draft(hash, text))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message ?: "保存失败，修改仍保留" }
            finally { candidate?.delete(); busy = false }
        }
    }
    fun export(uri: Uri) {
        if (busy) return
        val snapshot = value.text; busy = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(snapshot) } ?: error("无法写入")
                    val read = getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    check(read == snapshot) { "导出核验失败" }
                }
                message = "当前内容已导出"
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message }
            finally { busy = false }
        }
    }
    class Factory(private val app: Application, private val id: String, private val repository: DocumentRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MarkdownEditorViewModel(app, id, repository) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkdownEditorScreen(id: String, repository: DocumentRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val model: MarkdownEditorViewModel = viewModel(factory = MarkdownEditorViewModel.Factory(context.applicationContext as Application, id, repository))
    var preview by rememberSaveable { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<IntRange?>(null) }
    var blockText by remember { mutableStateOf("") }
    var editingSource by remember { mutableStateOf("") }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { it?.let(model::export) }
    val source = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) result.data?.let { intent -> intent.data?.let { uri ->
            runCatching { context.contentResolver.takePersistableUriPermission(uri,
                when (intent.flags and (android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)) {
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION -> android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION -> android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    (android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) ->
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    else -> 0
                }) }
            model.save(uri)
        } }
    }
    fun back() { if (model.dirty) leaving = true else onBack() }
    BackHandler { back() }
    if (leaving) AlertDialog(onDismissRequest = { leaving = false }, title = { Text("修改尚未保存") }, text = { Text("返回会放弃当前修改。") },
        confirmButton = { TextButton(onClick = { model.discardDraft(onBack) }) { Text("放弃并返回") } }, dismissButton = { TextButton(onClick = { leaving = false }) { Text("继续编辑") } })
    editing?.let { range -> AlertDialog(onDismissRequest = { editing = null }, title = { Text("编辑这一段") }, text = {
        OutlinedTextField(blockText, { blockText = it }, Modifier.fillMaxWidth(), minLines = 5)
    }, confirmButton = { TextButton(onClick = { model.replace(range, blockText); editing = null }, enabled = editingSource == model.value.text && !model.busy) { Text("应用") } },
        dismissButton = { TextButton(onClick = { editing = null }) { Text("取消") } }) }
    Scaffold(topBar = { TopAppBar(title = { Text(model.document?.displayName ?: "Markdown编辑") }, navigationIcon = { TextButton(onClick = { back() }) { Text("返回") } }, actions = {
        TextButton(onClick = { preview = !preview }) { Text(if (preview) "源码" else "预览") }
        TextButton(onClick = { model.save() }, enabled = model.dirty && !model.busy && !model.draftConflict) { Text("保存") }
    }) }, bottomBar = {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 12.dp)) {
            model.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Row {
                TextButton(onClick = model::undo, enabled = !model.busy) { Text("撤销") }
                TextButton(onClick = model::redo, enabled = !model.busy) { Text("重做") }
                TextButton(onClick = { export.launch(model.document?.displayName ?: "文档.md") }, enabled = !model.busy && model.document != null) { Text("导出") }
                TextButton(onClick = { source.launch(android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(android.content.Intent.CATEGORY_OPENABLE).setType("text/*")
                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION or android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)) }, enabled = !model.busy && model.document != null && !model.draftConflict) { Text("覆盖授权原文件") }
            }
        }
    }) { padding ->
        if (model.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(padding))
        if (!preview) OutlinedTextField(model.value, model::change, Modifier.fillMaxSize().padding(padding).padding(12.dp), enabled = !model.busy && model.document != null)
        else {
            val sourceSnapshot = model.value.text
            val chunks by produceState<List<Pair<IntRange, MarkdownParser.ParseResult>>>(emptyList(), sourceSnapshot) {
                value = emptyList()
                value = withContext(Dispatchers.Default) { MarkdownSource.chunks(sourceSnapshot).map { it to MarkdownParser.parse(sourceSnapshot.substring(it)) } }
            }
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp)) {
                itemsIndexed(chunks) { _, (range, parsed) ->
                    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        SelectionContainer { Column { parsed.blocks.forEach { block -> MarkdownBlockView(block = block, settings = ReaderSettings.Default, documentUri = model.document?.uri ?: "") } } }
                        TextButton(onClick = { blockText = sourceSnapshot.substring(range); editingSource = sourceSnapshot; editing = range }, enabled = !model.busy && sourceSnapshot == model.value.text) { Text("编辑这一段") }
                    }
                }
            }
        }
    }
}
