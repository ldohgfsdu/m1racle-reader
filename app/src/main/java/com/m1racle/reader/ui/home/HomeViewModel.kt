package com.m1racle.reader.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.data.repository.DocumentRepository
import com.m1racle.reader.data.repository.ImportResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val documents: List<DocumentEntity> = emptyList(),
    val isEmpty: Boolean = false,
    /** First Room emission has arrived. Empty-copy is forbidden until this is true. */
    val ready: Boolean = false,
    /** H2.2 继续阅读 hero 目标（无阅读记录时为 null → UI 降级隐藏）。 */
    val continueReading: DocumentEntity? = null,
)

/**
 * 状态推导纯函数（HomeViewModelTest 直测）：乐观删除过滤后，
 * 继续阅读取最近一条有阅读记录的文档（唯一事实源 = DocumentEntity 字段族）。
 */
internal fun homeUiState(
    source: List<DocumentEntity>,
    pendingDeleteIds: Set<String>,
): HomeUiState {
    val visible = HomeListUpdate.visibleDocuments(source, pendingDeleteIds)
    return HomeUiState(
        documents = visible,
        isEmpty = visible.isEmpty(),
        ready = true,
        continueReading = HomeHubSelectors.continueReading(visible),
    )
}

sealed class HomeEvent {
    data class OpenDocument(val document: DocumentEntity) : HomeEvent()
    data object UnsupportedFormat : HomeEvent()
    data object ReadFailed : HomeEvent()
    data object SourceTooLarge : HomeEvent()
    data object DeleteFailed : HomeEvent()
}

internal fun ImportResult.toHomeEvent(): HomeEvent = when (this) {
    is ImportResult.Success -> HomeEvent.OpenDocument(document)
    ImportResult.UnsupportedFormat -> HomeEvent.UnsupportedFormat
    ImportResult.SourceTooLarge -> HomeEvent.SourceTooLarge
    ImportResult.ReadFailed -> HomeEvent.ReadFailed
}

class HomeViewModel(
    private val repository: DocumentRepository,
) : ViewModel() {

    /**
     * Ids removed optimistically so LazyColumn reflows before Room Flow emits.
     * Prevents residual blank/half-card slots after delete.
     */
    private val pendingDeleteIds = MutableStateFlow<Set<String>>(emptySet())

    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeRecentDocuments(),
        pendingDeleteIds,
    ) { source, pending ->
        homeUiState(source, pending)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = HomeUiState(),
    )

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    fun deleteDocument(id: String) {
        if (id in pendingDeleteIds.value) return
        // Instant reflow — drop from UI before Room IO completes.
        pendingDeleteIds.update { it + id }
        viewModelScope.launch {
            try {
                repository.deleteById(id)
                // Wait until source no longer contains id, then clear pending entry.
                repository.observeRecentDocuments().first { docs ->
                    docs.none { it.id == id }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _events.send(HomeEvent.DeleteFailed)
            } finally {
                pendingDeleteIds.update { it - id }
            }
        }
    }

    fun onImportDocument(uri: Uri) {
        if (!_isImporting.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                _events.send(repository.importDocument(uri).toHomeEvent())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _events.send(HomeEvent.ReadFailed)
            } finally {
                _isImporting.value = false
            }
        }
    }

    class Factory(
        private val repository: DocumentRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                return HomeViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel: $modelClass")
        }
    }
}
