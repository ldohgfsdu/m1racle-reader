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
)

sealed class HomeEvent {
    data object UnsupportedFormat : HomeEvent()
    data object ReadFailed : HomeEvent()
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
        val visible = HomeListUpdate.visibleDocuments(source, pending)
        HomeUiState(
            documents = visible,
            isEmpty = visible.isEmpty(),
        )
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
            when (repository.importDocument(uri)) {
                is ImportResult.Success -> Unit
                ImportResult.UnsupportedFormat -> _events.emit(HomeEvent.UnsupportedFormat)
                ImportResult.ReadFailed -> _events.emit(HomeEvent.ReadFailed)
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
