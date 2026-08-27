package com.markleaf.app.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.markleaf.app.data.local.DocumentEntity
import com.markleaf.app.data.repository.DocumentRepository
import com.markleaf.app.data.repository.ImportResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val documents: List<DocumentEntity> = emptyList(),
    val isEmpty: Boolean = true,
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
        continueReading = HomeHubSelectors.continueReading(visible),
    )
}

sealed class HomeEvent {
    data class OpenDocument(val document: DocumentEntity) : HomeEvent()
    data object UnsupportedFormat : HomeEvent()
    data object ReadFailed : HomeEvent()
}

internal fun ImportResult.toHomeEvent(): HomeEvent = when (this) {
    is ImportResult.Success -> HomeEvent.OpenDocument(document)
    ImportResult.UnsupportedFormat -> HomeEvent.UnsupportedFormat
    // UI frozen by policy-batch constraints: the dedicated "file too large"
    // copy lands with the next UI-touching batch; until then it shares the
    // generic failure toast.
    ImportResult.SourceTooLarge -> HomeEvent.ReadFailed
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
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    private val _events = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

    fun deleteDocument(id: String) {
        // Instant reflow — drop from UI before Room IO completes.
        pendingDeleteIds.update { it + id }
        viewModelScope.launch {
            try {
                repository.deleteById(id)
                // Wait until source no longer contains id, then clear pending entry.
                repository.observeRecentDocuments().first { docs ->
                    docs.none { it.id == id }
                }
                pendingDeleteIds.update { it - id }
            } catch (_: Exception) {
                pendingDeleteIds.update { it - id }
            }
        }
    }

    fun onImportDocument(uri: Uri) {
        viewModelScope.launch {
            _events.emit(repository.importDocument(uri).toHomeEvent())
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
