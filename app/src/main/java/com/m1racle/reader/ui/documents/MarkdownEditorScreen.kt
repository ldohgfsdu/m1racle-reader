package com.m1racle.reader.ui.documents

import com.m1racle.reader.ui.theme.M1racleReaderTheme
import android.app.Application
import android.net.Uri
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.MoreVert
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.data.local.DocumentType
import com.m1racle.reader.data.repository.DocumentRepository
import com.m1racle.reader.data.settings.ReaderSettings
import com.m1racle.reader.core.model.ContentSha256
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
    private var latestDraft: com.m1racle.reader.documents.MarkdownDraftStore.Draft? = null
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
                getApplication<Application>().contentResolver.openInputStream(Uri.parse(doc.uri))?.use(ContentSha256::of) ?: error("无法读取文档")
            }
            original = text; value = TextFieldValue(text)
            val draft = withContext(Dispatchers.IO) { drafts.read() }
            if (draft != null && draft.text != text) {
                draftBaseHash = draft.baseHash
                value = TextFieldValue(draft.text)
                draftConflict = draft.baseHash != hash
                message = if (draftConflict) "原文已更新，旧草稿已恢复；请导出草稿，核对后重新编辑。" else "已恢复未保存的草稿"
            }
            // Publishing the document mounts the WebView once. Its initial
            // source must already include any restored draft, even on slow IO.
            document = doc
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { message = e.message }
        finally { busy = false }
    } }
    private fun queueDraft(draft: com.m1racle.reader.documents.MarkdownDraftStore.Draft) {
        latestDraft = draft
        draftChanges.trySend(draft)
    }
    override fun onCleared() {
        val finalDraft = latestDraft
        draftWriter.cancel()
        if (finalDraft != null) kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.NonCancellable + Dispatchers.IO).launch {
            // Finish any in-flight AtomicFile write before the final snapshot.
            draftWriter.join()
            runCatching { drafts.write(finalDraft) }
        }
        super.onCleared()
    }
    fun finish(onSaved: () -> Unit, onDone: () -> Unit) {
        if (busy || draftConflict) return
        if (!dirty) { onDone(); return }
        save(onSaved = {
            onSaved()
            if (!dirty) onDone() else message = "已保存，仍有未保存修改"
        })
    }
    fun change(next: TextFieldValue) {
        if (value.text != next.text) {
            queueDraft(com.m1racle.reader.documents.MarkdownDraftStore.Draft(draftBaseHash ?: hash, next.text))
        }
        value = next
    }
    fun inform(text: String) { message = text }
    fun discardDraft(onDone: () -> Unit) {
        viewModelScope.launch {
            try { draftWriter.cancel(); draftWriter.join(); withContext(Dispatchers.IO) { drafts.delete() }; latestDraft = null; onDone() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { message = "未能清理草稿，请重试" }
        }
    }
    fun save(source: Uri? = null, onSaved: () -> Unit = {}) {
        if (busy || draftConflict) return
        val text = value.text
        busy = true
        viewModelScope.launch {
            var candidate: File? = null
            try {
                candidate = withContext(Dispatchers.IO) { File.createTempFile("markdown-edit-", ".md", getApplication<Application>().cacheDir).apply { writeText(text, Charsets.UTF_8) } }
                val updated = repository.saveDocumentEdits(id, hash, candidate, source)
                document = updated; hash = requireNotNull(updated.contentSha256); original = text; message = "已保存，批注已按新内容重新定位"
                draftBaseHash = hash
                // A queued renderer input may arrive while the snapshot saves.
                // Keep those later edits as a draft against the new revision.
                queueDraft(com.m1racle.reader.documents.MarkdownDraftStore.Draft(hash, value.text))
                onSaved()
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

/** Destination-owned child store survives rotation; only leaving edit mode clears it. */
class MarkdownInlineSessionOwner : ViewModel(), androidx.lifecycle.ViewModelStoreOwner {
    override val viewModelStore = androidx.lifecycle.ViewModelStore()
    fun end() { viewModelStore.clear() }
    override fun onCleared() { end(); super.onCleared() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkdownInlineEditor(id: String, title: String, initialLine: Int, repository: DocumentRepository, onBack: () -> Unit, onSaved: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as com.m1racle.reader.M1racleReaderApplication
    val owner: MarkdownInlineSessionOwner = viewModel()
    val model: MarkdownEditorViewModel = viewModel(viewModelStoreOwner = owner, factory = MarkdownEditorViewModel.Factory(app, id, repository))
    fun endMode() { owner.end(); onBack() }
    val settings by app.readerSettingsRepository.settingsFlow.collectAsState(initial = ReaderSettings.Default)
    val dark = com.m1racle.reader.ui.theme.LocalReaderDarkTheme.current
    val controller = remember(id) { MarkdownEditorController() }
    var sourceMode by rememberSaveable(id) { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { it?.let(model::export) }
    val source = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) result.data?.let { intent -> intent.data?.let { uri ->
            runCatching { context.contentResolver.takePersistableUriPermission(uri,
                when (intent.flags and (android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)) {
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION -> android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION -> android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    (android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) ->
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    else -> 0
                }) }
            model.save(uri, onSaved)
        } }
    }
    fun snapshot(action: () -> Unit) {
        controller.snapshot(onSnapshot = { text -> model.change(TextFieldValue(text)); action() },
            onComposing = { model.inform("请先完成当前输入") })
    }
    fun back() {
        if (model.busy && model.document != null) return
        if (model.document == null) { endMode(); return }
        if (!controller.ready) { if (model.dirty) leaving = true else endMode(); return }
        snapshot { if (model.dirty) leaving = true else endMode() }
    }
    BackHandler { back() }
    LaunchedEffect(model.message) { model.message?.let { snackbar.showSnackbar(it) } }
    if (leaving) AlertDialog(onDismissRequest = { leaving = false }, title = { Text("修改尚未保存") },
        text = { Text("放弃后将返回阅读，原文保持不变。") },
        confirmButton = { TextButton(onClick = { model.discardDraft(::endMode) }) { Text("放弃修改") } },
        dismissButton = { TextButton(onClick = { leaving = false }) { Text("继续编辑") } })
    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(title,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp)) },
                    navigationIcon = { IconButton(onClick = { back() }, enabled = !model.busy || model.document == null) {
                        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, "退出编辑")
                    } },
                    actions = {
                        IconButton(onClick = { controller.command("undo()") }, enabled = canUndo && !model.busy) {
                            Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.Undo, "撤销")
                        }
                        IconButton(onClick = { controller.command("redo()") }, enabled = canRedo && !model.busy) {
                            Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.Redo, "重做")
                        }
                        TextButton(onClick = { snapshot { keyboard?.hide(); model.finish(onSaved, ::endMode) } },
                            enabled = controller.ready && !model.busy && !model.draftConflict) {
                            Text("完成", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    expandedHeight = 56.dp,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = M1racleReaderTheme.hairline)
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column(Modifier.navigationBarsPadding()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = M1racleReaderTheme.hairline)
                    Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Spacer(Modifier.width(48.dp))
                        Spacer(Modifier.weight(1f))
                        SingleChoiceSegmentedButtonRow {
                            SegmentedButton(selected = !sourceMode, onClick = { controller.setMode(false,
                                accepted = { sourceMode = false; keyboard?.hide() }, composing = { model.inform("请先完成当前输入") }) },
                                shape = SegmentedButtonDefaults.itemShape(0, 2), icon = {}, enabled = controller.ready && !model.busy) { Text("正文", maxLines = 1) }
                            SegmentedButton(selected = sourceMode, onClick = { controller.setMode(true,
                                accepted = { sourceMode = true; keyboard?.hide() }, composing = { model.inform("请先完成当前输入") }) },
                                shape = SegmentedButtonDefaults.itemShape(1, 2), icon = {}, enabled = controller.ready && !model.busy) { Text("源码") }
                        }
                        Spacer(Modifier.weight(1f))
                        Box {
                            IconButton(onClick = { showMore = true }, enabled = controller.ready && !model.busy) {
                                Icon(androidx.compose.material.icons.Icons.Default.MoreVert, "编辑选项")
                            }
                            DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                                DropdownMenuItem(text = { Text("导出 Markdown") }, onClick = { showMore = false; snapshot { export.launch(model.document?.displayName ?: "文档.md") } })
                                DropdownMenuItem(text = { Text("保存到原文件…") }, enabled = !model.draftConflict, onClick = {
                                    showMore = false
                                    snapshot { source.launch(android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT)
                                        .addCategory(android.content.Intent.CATEGORY_OPENABLE).setType("text/*")
                                        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION or android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)) }
                                })
                            }
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (model.draftConflict) Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("原文件已更新，请先导出草稿核对。", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { snapshot { export.launch(model.document?.displayName ?: "草稿.md") } },
                        enabled = controller.ready && !model.busy) { Text("导出草稿") }
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                if (model.document != null) {
                    MarkdownWebEditor(id = id, initialMarkdown = model.value.text, sourceMode = sourceMode,
                        readOnly = model.busy, dark = dark, settings = settings, controller = controller,
                        onChange = { model.change(TextFieldValue(it)) },
                        onHistory = { undo, redo -> canUndo = undo; canRedo = redo },
                        onLink = { url ->
                            val uri = Uri.parse(url)
                            if (uri.scheme in listOf("http", "https", "mailto")) runCatching {
                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
                            }.onFailure { model.inform("没有可打开此链接的应用") }
                        },
                        onError = model::inform, modifier = Modifier.fillMaxSize(), initialLine = initialLine)
                } else if (!model.busy) {
                    Text(model.message ?: "无法打开文档", Modifier.padding(24.dp))
                }
                if (model.busy || (model.document != null && !controller.ready)) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}
