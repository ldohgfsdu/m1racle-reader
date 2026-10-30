package com.m1racle.reader.ui.documents

import com.m1racle.reader.ui.components.ReaderOutlinedTextField
import com.m1racle.reader.ui.theme.M1racleReaderTheme
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.data.repository.DocumentRepository
import com.m1racle.reader.data.repository.ImportResult
import com.m1racle.reader.documents.DocumentTools
import com.m1racle.reader.documents.DocumentOutputSave
import com.m1racle.reader.documents.DocumentDiscovery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DocumentToolsViewModel(application: Application, private val repository: DocumentRepository) : AndroidViewModel(application) {
    var busy by mutableStateOf(false); private set
    var output by mutableStateOf<File?>(null); private set
    var message by mutableStateOf<String?>(null); private set
    var discovered by mutableStateOf<List<File>>(emptyList()); private set
    private var job: Job? = null
    private var activeSave: DocumentOutputSave? = null
    var cancelling by mutableStateOf(false); private set
    private val opened = Channel<DocumentEntity>(Channel.BUFFERED)
    val documents = opened.receiveAsFlow()
    private val tools = DocumentTools(application)
    private fun task(block: suspend () -> Unit) {
        if (busy) return
        busy = true; cancelling = false; message = null
        job = viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) {
                val saving = activeSave
                if (saving != null && !saving.committed)
                    withContext(NonCancellable + Dispatchers.IO) { saving.discardUncommitted() }
                if (activeSave?.committed == true) savedOutput()
                else message = if (activeSave?.partialOutputRemains == true)
                    "已停止保存，部分文件未能删除，请在保存位置删除" else "已取消"
                throw e
            }
            catch (e: Exception) {
                message = if (activeSave?.partialOutputRemains == true)
                    "保存失败，部分文件未能删除，请在保存位置删除；可重新保存" else e.message ?: "操作失败"
            }
            finally { activeSave = null; cancelling = false; busy = false }
        }
    }
    fun cancel() {
        if (!busy || cancelling || activeSave?.committed == true) return
        cancelling = true; message = "正在取消…"; job?.cancel()
    }
    fun create(text: String, pages: Int) = task { replace(tools.createPdf(text, pages)); message = "PDF已生成，请保存" }
    fun merge(uris: List<Uri>) = task { replace(tools.merge(uris)); message = "合并完成，请保存" }
    fun encrypt(uri: Uri, password: String) = task { replace(tools.encrypt(uri, password)); message = "加密完成，请保存" }
    fun convert(uri: Uri) = task { replace(tools.toMarkdown(uri)); message = "已提取文字，请保存；复杂版式不保留" }
    private fun replace(file: File) { output?.delete(); output = file }
    private fun savedOutput() { output?.delete(); output = null; message = "已保存并核验" }
    fun save(uri: Uri) = task {
        val file = output ?: error("尚未生成文件")
        check(uri != Uri.fromFile(file)) { "请选择新的保存位置" }
        val resolver = getApplication<Application>().contentResolver
        val save = DocumentOutputSave(openSource = { file.inputStream() },
            openOutput = { resolver.openOutputStream(uri, "wt") },
            openVerification = { resolver.openInputStream(uri) },
            deleteDestination = {
                if (uri.scheme == "file") uri.path?.let { File(it).delete() } == true
                else DocumentsContract.deleteDocument(resolver, uri)
            })
        activeSave = save
        withContext(Dispatchers.IO) {
            save.write()
        }
        savedOutput()
    }
    fun scan() = task {
        check(Environment.isExternalStorageManager()) { "请先授予所有文件访问权限" }
        discovered = DocumentDiscovery.scan(Environment.getExternalStorageDirectory())
        message = "发现 ${discovered.size} 份文档，点击导入阅读"
    }
    fun open(file: File) = task {
        when (val imported = repository.importDocument(Uri.fromFile(file))) {
            is ImportResult.Success -> opened.send(imported.document)
            else -> error("无法导入此文档")
        }
    }
    override fun onCleared() {
        val retained = output
        super.onCleared()
        val running = job
        if (running != null && !running.isCompleted) running.invokeOnCompletion { retained?.delete() }
        else retained?.delete()
    }
    class Factory(private val app: Application, private val repository: DocumentRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = DocumentToolsViewModel(app, repository) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentToolsScreen(repository: DocumentRepository, onBack: () -> Unit, onOpen: (DocumentEntity) -> Unit) {
    val context = LocalContext.current
    val model: DocumentToolsViewModel = viewModel(factory = DocumentToolsViewModel.Factory(context.applicationContext as Application, repository))
    var text by rememberSaveable { mutableStateOf("") }
    var pages by rememberSaveable { mutableStateOf("1") }
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var password by remember { mutableStateOf("") }
    val choose = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { selected = it.map(Uri::toString) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(if (model.output?.extension == "md") "text/markdown" else "application/pdf")) { it?.let(model::save) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Environment.isExternalStorageManager()) model.scan()
    }
    LaunchedEffect(model) { model.documents.collect(onOpen) }
    LaunchedEffect(model) {
        if (Environment.isExternalStorageManager() && model.discovered.isEmpty()) model.scan()
    }
    Scaffold(topBar = { TopAppBar(title = { Text("文件与 PDF 工具") }, navigationIcon = { TextButton(onClick = onBack) { Text("返回") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("发现本机文档", style = MaterialTheme.typography.titleMedium)
                Text("扫描 PDF、Markdown、文本和 EPUB 文件。")
                Button(onClick = {
                    if (Environment.isExternalStorageManager()) model.scan()
                    else permission.launch(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}")))
                }, enabled = !model.busy) { Text(if (Environment.isExternalStorageManager()) "扫描文档" else "授权并扫描") }
            }
            item {
                Text("默认查看器", style = MaterialTheme.typography.titleMedium)
                Text("在文件管理器中打开文档，选择 M1racle Reader 并设为默认应用。")
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("应用设置") }
            }
            item {
                HorizontalDivider(thickness = M1racleReaderTheme.hairline); Text("创建 PDF", style = MaterialTheme.typography.titleMedium)
                ReaderOutlinedTextField(text, { text = it }, label = { Text("正文（留空创建空白页）") }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp))
                ReaderOutlinedTextField(pages, { pages = it.filter(Char::isDigit) }, label = { Text("空白页数") }, singleLine = true)
                Button(onClick = { model.create(text, pages.toIntOrNull() ?: 0) }, enabled = !model.busy && (pages.toIntOrNull() ?: 0) in 1..1000) { Text("生成 PDF") }
            }
            item {
                HorizontalDivider(thickness = M1racleReaderTheme.hairline); Text("处理 PDF", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { choose.launch(arrayOf("application/pdf")) }, enabled = !model.busy) { Text("选择 PDF") }
            }
            items(selected, key = { it }) { value ->
                val index = selected.indexOf(value)
                Column {
                    Text("${index + 1}. ${Uri.decode(Uri.parse(value).lastPathSegment ?: value)}", maxLines = 2)
                    Row {
                        TextButton(onClick = { selected = selected.toMutableList().apply { java.util.Collections.swap(this, index, index - 1) } }, enabled = index > 0 && !model.busy) { Text("上移") }
                        TextButton(onClick = { selected = selected - value }, enabled = !model.busy) { Text("移除") }
                    }
                }
            }
            item {
                Button(onClick = { model.merge(selected.map(Uri::parse)) }, enabled = selected.size >= 2 && !model.busy) { Text("按顺序合并") }
                ReaderOutlinedTextField(password, { password = it }, label = { Text("打开密码") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                Button(onClick = { model.encrypt(Uri.parse(selected.single()), password); password = "" }, enabled = selected.size == 1 && password.isNotBlank() && !model.busy) { Text("生成加密副本") }
                Button(onClick = { model.convert(Uri.parse(selected.single())) }, enabled = selected.size == 1 && !model.busy) { Text("提取为 Markdown") }
            }
            item {
                if (model.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); TextButton(onClick = model::cancel, enabled = !model.cancelling) { Text(if (model.cancelling) "正在取消…" else "取消") } }
                model.message?.let { Text(it) }
                model.output?.let { file -> Button(onClick = { save.launch(if (file.extension == "md") "提取文字.md" else "新文档.pdf") }, enabled = !model.busy) { Text("选择保存位置") } }
            }
            items(model.discovered, key = { it.absolutePath }) { file ->
                Column(Modifier.fillMaxWidth().clickable(enabled = !model.busy) { model.open(file) }.padding(vertical = 8.dp)) {
                    Text(file.name, style = MaterialTheme.typography.titleSmall)
                    Text(file.parent ?: "", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
