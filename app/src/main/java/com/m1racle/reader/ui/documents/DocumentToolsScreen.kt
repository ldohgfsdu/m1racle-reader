package com.m1racle.reader.ui.documents

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
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
import com.m1racle.reader.documents.DocumentDiscovery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private val opened = Channel<DocumentEntity>(Channel.BUFFERED)
    val documents = opened.receiveAsFlow()
    private val tools = DocumentTools(application)
    private fun task(block: suspend () -> Unit) {
        if (busy) return
        busy = true; message = null
        job = viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message ?: "操作失败" }
            finally { busy = false }
        }
    }
    fun cancel() { job?.cancel(); message = "已取消" }
    fun create(text: String, pages: Int) = task { replace(tools.createPdf(text, pages)); message = "PDF已生成，请保存" }
    fun merge(uris: List<Uri>) = task { replace(tools.merge(uris)); message = "合并完成，请保存" }
    fun encrypt(uri: Uri, password: String) = task { replace(tools.encrypt(uri, password)); message = "加密完成，请保存" }
    fun convert(uri: Uri) = task { replace(tools.toMarkdown(uri)); message = "已提取文字，请保存；复杂版式不保留" }
    private fun replace(file: File) { output?.delete(); output = file }
    fun save(uri: Uri) = task {
        val file = output ?: error("尚未生成文件")
        withContext(Dispatchers.IO) {
            getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: error("无法写入目标文件")
            val actual = getApplication<Application>().contentResolver.openInputStream(uri)?.use(com.m1racle.reader.pdf.edit.PdfSourceWriteStore::sha256)
            check(actual == com.m1racle.reader.pdf.edit.PdfSourceWriteStore.sha256(file)) { "保存核验失败" }
        }
        file.delete(); output = null; message = "已保存并核验"
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
    override fun onCleared() { output?.delete(); super.onCleared() }
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
    Scaffold(topBar = { TopAppBar(title = { Text("文件与PDF工具") }, navigationIcon = { TextButton(onClick = onBack) { Text("返回") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("发现本机文档", style = MaterialTheme.typography.titleMedium)
                Text("扫描共享存储中的PDF、Markdown、文本和EPUB，不访问其他应用私有目录。")
                Button(onClick = {
                    if (Environment.isExternalStorageManager()) model.scan()
                    else permission.launch(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}")))
                }, enabled = !model.busy) { Text(if (Environment.isExternalStorageManager()) "扫描文档" else "授权并扫描") }
            }
            item {
                Text("默认查看器", style = MaterialTheme.typography.titleMedium)
                Text("从文件管理器打开PDF，选择M1racle Reader并点“始终”。如已有默认应用，先清除其默认打开设置。")
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("应用设置") }
            }
            item {
                HorizontalDivider(); Text("创建PDF", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(text, { text = it }, label = { Text("正文（留空创建空白页）") }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp))
                OutlinedTextField(pages, { pages = it.filter(Char::isDigit) }, label = { Text("空白页数") }, singleLine = true)
                Button(onClick = { model.create(text, pages.toIntOrNull() ?: 0) }, enabled = !model.busy && (pages.toIntOrNull() ?: 0) in 1..1000) { Text("生成PDF") }
            }
            item {
                HorizontalDivider(); Text("处理已有PDF", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { choose.launch(arrayOf("application/pdf")) }, enabled = !model.busy) { Text("选择PDF，可多选") }
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
                OutlinedTextField(password, { password = it }, label = { Text("打开密码") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                Button(onClick = { model.encrypt(Uri.parse(selected.single()), password); password = "" }, enabled = selected.size == 1 && password.isNotBlank() && !model.busy) { Text("生成加密副本") }
                Button(onClick = { model.convert(Uri.parse(selected.single())) }, enabled = selected.size == 1 && !model.busy) { Text("提取为Markdown") }
            }
            item {
                if (model.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); TextButton(onClick = model::cancel) { Text("取消") } }
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
