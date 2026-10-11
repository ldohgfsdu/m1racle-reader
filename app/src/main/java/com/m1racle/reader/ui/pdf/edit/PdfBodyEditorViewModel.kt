package com.m1racle.reader.ui.pdf.edit

import com.m1racle.reader.pdf.edit.PdfBodyEditor
import com.m1racle.reader.pdf.edit.PdfEditablePage
import com.m1racle.reader.pdf.edit.PdfEditRejected
import com.m1racle.reader.pdf.edit.PdfTextKey
import com.m1racle.reader.pdf.edit.PdfSourceWriteStore

import android.app.Application
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.m1racle.reader.data.repository.DocumentRepository
import com.m1racle.reader.data.repository.ImportResult
import com.m1racle.reader.pdf.PdfBoxRuntime
import com.m1racle.reader.pdf.PdfPageRenderer
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

data class PdfEditorState(
    val title: String = "正文编辑",
    val page: PdfEditablePage? = null,
    val image: ImageBitmap? = null,
    val busy: Boolean = true,
    val dirty: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val revision: Int = 0,
    val savedDocumentId: String? = null,
    val sourceUri: String? = null,
    val sourceWritable: Boolean = false,
    val recoveryAvailable: Boolean = false,
    val sourceNeedsUpdate: Boolean = false,
) {
    val canOverwriteSource: Boolean get() = dirty || sourceNeedsUpdate
}

class PdfBodyEditorViewModel(
    application: Application,
    private val repository: DocumentRepository,
    private val documentId: String,
    initialPage: Int,
    private val saved: SavedStateHandle,
) : AndroidViewModel(application) {
    private val mutable = MutableStateFlow(PdfEditorState())
    val state = mutable.asStateFlow()
    private val mutex = Mutex()
    private val sessionId = saved.get<String>("session") ?: java.util.UUID.randomUUID().toString().also { saved["session"] = it }
    private val directory = File(application.cacheDir, "pdf-edit-$sessionId")
    private val source = File(directory, "source.pdf")
    private var preview = source
    private var originalUri: Uri? = null
    private var originHash = ""
    private var edits: Map<PdfTextKey, String> = emptyMap()
    private val undo = ArrayDeque<Map<PdfTextKey, String>>()
    private val redo = ArrayDeque<Map<PdfTextKey, String>>()
    private var currentPage = saved.get<Int>("page") ?: initialPage

    init {
        viewModelScope.launch {
            try {
                val result = mutex.withLock { withContext(Dispatchers.IO) {
                    check(PdfBoxRuntime.ensureInit(application))
                    val recoveryErrors = repository.recoverPdfSourceWrites(documentId)
                    val document = repository.getById(documentId) ?: error("原文档已移除")
                    mutable.value = mutable.value.copy(sourceUri=document.sourceUri,
                        sourceWritable=canWriteSource(document.sourceUri),notice=recoveryErrors.firstOrNull(),
                        recoveryAvailable=repository.pdfSourceRecoveryBackup(documentId)!=null)
                    val committedDuringRestart = saved.get<String>("pendingCommitHash")?.let { it == document.contentSha256 } == true
                    if (committedDuringRestart) { saved["edits"]="[]"; source.delete(); saved.remove<String>("originHash") }
                    originalUri = Uri.parse(document.uri)
                    directory.mkdirs()
                    if (!source.isFile) {
                        application.contentResolver.openInputStream(originalUri!!)?.use { input ->
                            source.outputStream().use { input.copyTo(it) }
                        } ?: error("无法读取原文档")
                    }
                    originHash = PdfSourceWriteStore.sha256(source)
                    check(saved.get<String>("originHash")?.let { it == originHash } != false) { "文档已变化，无法恢复旧草稿；请重新打开编辑器" }
                    saved["originHash"] = originHash
                    mutable.value = mutable.value.copy(sourceNeedsUpdate=document.sourceSha256?.let { it != originHash } == true)
                    val json = JSONArray(saved.get<String>("edits") ?: "[]")
                    edits = (0 until json.length()).associate { i ->
                        val item = json.getJSONObject(i)
                        PdfTextKey(item.getInt("page"),item.getInt("operator")) to item.getString("text")
                    }
                    if (edits.isNotEmpty()) {
                        preview = File(directory,"restored.pdf")
                        PdfBodyEditor.writeCopy(source,preview,edits)
                    }
                    Triple(document.displayName, PdfBodyEditor.inspect(preview,currentPage), render(preview,currentPage))
                } }
                mutable.value = mutable.value.copy(title=result.first,page=result.second,image=result.third,busy=false,dirty=edits.isNotEmpty())
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.value = mutable.value.copy(busy=false,error=e.message ?: "无法打开编辑器") }
        }
    }

    private suspend fun render(file: File, page: Int): ImageBitmap {
        val renderer = PdfPageRenderer(getApplication())
        try {
            if (renderer.open(Uri.fromFile(file).toString()) !is PdfPageRenderer.OpenResult.Success) error("无法打开 PDF 预览")
            return when (val result = renderer.renderPage(page,1600)) {
                is PdfPageRenderer.RenderResult.Success -> result.image
                else -> error("页面预览失败，请重试")
            }
        } finally { withContext(NonCancellable) { renderer.closeAsync() } }
    }

    private fun work(action: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy=true,error=null,notice=null)
        viewModelScope.launch {
            try { mutex.withLock { action() } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.value = mutable.value.copy(error=e.message ?: "操作失败，请重试") }
            finally { mutable.value = mutable.value.copy(busy=false) }
        }
    }

    fun goTo(page: Int) = work {
        if (page !in 0 until (mutable.value.page?.pageCount ?: 0)) return@work
        val result = withContext(Dispatchers.IO) { PdfBodyEditor.inspect(preview,page) to render(preview,page) }
        currentPage = page; saved["page"] = page
        mutable.value = mutable.value.copy(page=result.first,image=result.second)
    }

    fun apply(key: PdfTextKey, text: String) = work {
        val original = withContext(Dispatchers.IO) { PdfBodyEditor.inspect(source,key.page).runs.single { it.key == key }.text }
        val next = edits.toMutableMap().apply { if (text == original) remove(key) else put(key,text) }.toMap()
        if (next == edits) { mutable.value = mutable.value.copy(revision=mutable.value.revision+1); return@work }
        if (next.size > 100 || next.values.sumOf { it.length } > 32768) throw PdfEditRejected("本次修改较多，请先另存副本后继续")
        val previous = edits
        replacePreview(next)
        undo.addLast(previous); if (undo.size > 100) undo.removeFirst()
        redo.clear(); publishHistory()
    }

    fun undo() = work {
        if (undo.isEmpty()) return@work
        val previous = edits; replacePreview(undo.last())
        undo.removeLast(); redo.addLast(previous); publishHistory()
    }
    fun redo() = work {
        if (redo.isEmpty()) return@work
        val previous = edits; replacePreview(redo.last())
        redo.removeLast(); undo.addLast(previous); publishHistory()
    }

    private suspend fun replacePreview(next: Map<PdfTextKey,String>) {
        val candidate = File(directory,"preview-${java.util.UUID.randomUUID()}.pdf")
        try {
            val result = withContext(Dispatchers.IO) {
                PdfBodyEditor.writeCopy(source,candidate,next)
                PdfBodyEditor.inspect(candidate,currentPage) to render(candidate,currentPage)
            }
            val old = preview; preview = candidate; edits = next
            if (old != source) withContext(Dispatchers.IO) { old.delete() }
            saved["edits"] = JSONArray().apply {
                next.forEach { (key,text) -> put(JSONObject().put("page",key.page).put("operator",key.operator).put("text",text)) }
            }.toString()
            mutable.value = mutable.value.copy(page=result.first,image=result.second,dirty=next.isNotEmpty(),revision=mutable.value.revision+1)
        } catch (e: Exception) { withContext(Dispatchers.IO + NonCancellable) { candidate.delete() }; throw e }
    }

    private fun publishHistory() {
        mutable.value = mutable.value.copy(canUndo=undo.isNotEmpty(),canRedo=redo.isNotEmpty())
    }
    fun clearMessage() { mutable.value = mutable.value.copy(error=null,notice=null) }

    private fun canWriteSource(value: String?): Boolean {
        if (value == null) return false
        val uri = Uri.parse(value)
        val app = getApplication<Application>()
        return if (uri.scheme == "file") uri.path?.let { File(it).canWrite() } == true
        else app.checkUriPermission(uri,android.os.Process.myPid(),android.os.Process.myUid(),
            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun saveCurrent(sourceTarget: Uri? = null) = work {
        try {
            saved["pendingCommitHash"] = withContext(Dispatchers.IO) { PdfSourceWriteStore.sha256(preview) }
            val result = repository.saveDocumentEdits(documentId,originHash,preview,sourceTarget)
            saved["edits"] = "[]"
            mutable.value = mutable.value.copy(savedDocumentId=result.id,dirty=false)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            mutable.value = mutable.value.copy(recoveryAvailable=repository.pdfSourceRecoveryBackup(documentId)!=null)
            throw e
        }
    }

    fun exportRecoveryBackup(uri: Uri) = work {
        val backup = repository.pdfSourceRecoveryBackup(documentId) ?: error("没有待恢复备份")
        withContext(Dispatchers.IO) {
            getApplication<Application>().contentResolver.openOutputStream(uri,"wt")?.use { out -> backup.inputStream().use { it.copyTo(out) } }
                ?: error("无法导出备份")
            val actual = getApplication<Application>().contentResolver.openInputStream(uri)?.use(PdfSourceWriteStore::sha256)
            check(actual == PdfSourceWriteStore.sha256(backup)) { "备份导出核验失败，请重试" }
        }
        mutable.value = mutable.value.copy(notice="原文件备份已导出")
    }

    fun saveCopy(uri: Uri) = work {
        check(uri != originalUri) { "请选择新的文件位置" }
        withContext(Dispatchers.IO) {
            val resolver = getApplication<Application>().contentResolver
            val expected = java.security.MessageDigest.getInstance("SHA-256")
            preview.inputStream().use { input ->
                java.security.DigestInputStream(input,expected).use { checked ->
                    resolver.openOutputStream(uri,"wt")?.use { checked.copyTo(it) }
                        ?: error("无法写入所选位置，请重新另存")
                }
            }
            val actual = java.security.MessageDigest.getInstance("SHA-256")
            resolver.openInputStream(uri)?.use { input ->
                java.security.DigestInputStream(input,actual).use { checked ->
                    val buffer = ByteArray(8192)
                    while (checked.read(buffer) != -1) ensureActive()
                }
            } ?: error("无法核验保存的副本，修改仍保留，可重新另存")
            check(expected.digest().contentEquals(actual.digest())) { "副本写入不完整，修改仍保留，请重新另存" }
        }
        // The exported file is already a validated snapshot. Import as a new reading identity.
        val imported = try { repository.importDocument(uri) } catch (e: CancellationException) { throw e } catch (_: Exception) { ImportResult.ReadFailed }
        when (imported) {
            is ImportResult.Success -> mutable.value = mutable.value.copy(savedDocumentId=imported.document.id,dirty=false)
            else -> mutable.value = mutable.value.copy(notice="副本已保存，但未能加入书库。可从保存位置手动打开。")
        }
    }

    override fun onCleared() {
        super.onCleared()
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch { mutex.withLock { directory.deleteRecursively() } }
    }

    class Factory(private val app: Application,private val repository: DocumentRepository,private val id: String,private val page: Int) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            PdfBodyEditorViewModel(app,repository,id,page,extras.createSavedStateHandle()) as T
    }
}
