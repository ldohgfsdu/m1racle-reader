package com.markleaf.app.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.markleaf.app.data.local.DocumentEntity
import com.markleaf.app.data.repository.DocumentRepository
import com.markleaf.app.data.repository.ImportResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

    val uiState: StateFlow<HomeUiState> = repository
        .observeRecentDocuments()
        .map { list ->
            HomeUiState(
                documents = list,
                isEmpty = list.isEmpty(),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(),
        )

    private val _events = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

    fun deleteDocument(id: String) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }

    /**
     * Called after the user picks a document via SAF [OpenDocument].
     * Repository work runs off the main thread; the list refreshes via Flow.
     */
    fun onImportDocument(uri: Uri) {
        viewModelScope.launch {
            when (repository.importDocument(uri)) {
                is ImportResult.Success -> {
                    // Room Flow updates the list automatically.
                }
                ImportResult.UnsupportedFormat -> {
                    _events.emit(HomeEvent.UnsupportedFormat)
                }
                ImportResult.ReadFailed -> {
                    _events.emit(HomeEvent.ReadFailed)
                }
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
            throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
