package com.m1racle.reader.ui.home

import android.net.Uri
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.m1racle.reader.data.repository.DocumentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class DocumentOperations(private val repository: DocumentRepository) : ViewModel() {
    fun document(id: String) = repository.observeRecentDocuments().map { list -> list.firstOrNull { it.id == id } }
    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()
    fun rename(id: String, name: String, onSuccess: () -> Unit) = viewModelScope.launch {
        try { repository.renameDocument(id, name); onSuccess(); messages.send("已重命名书库条目") }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { messages.send(e.message ?: "重命名失败") }
    }
    fun export(id: String, target: Uri) = viewModelScope.launch {
        try { repository.exportDocument(id, target); messages.send("文件已导出并核验") }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { messages.send(e.message ?: "导出失败") }
    }
    class Factory(private val repository: DocumentRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = DocumentOperations(repository) as T
    }
}

val LocalDocumentOperations = staticCompositionLocalOf<DocumentOperations?> { null }

internal fun documentLocation(uri: String): String {
    val parsed = Uri.parse(uri)
    if (parsed.scheme == "file") return parsed.path ?: uri
    if (parsed.authority == "com.android.externalstorage.documents") {
        val id = runCatching { android.provider.DocumentsContract.getDocumentId(parsed) }.getOrNull()
        if (id?.startsWith("primary:") == true) return "/storage/emulated/0/${id.removePrefix("primary:")}"
    }
    return uri
}
