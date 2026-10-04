package com.m1racle.reader.ui.epub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.data.repository.DocumentRepository
import com.m1racle.reader.data.settings.ContentPaddingLevel
import com.m1racle.reader.data.settings.ReaderSettingsRepository
import com.m1racle.reader.data.settings.ReaderSettings
import com.m1racle.reader.epub.EpubBook
import com.m1racle.reader.epub.EpubException
import com.m1racle.reader.ui.viewer.ReadingPositionSaver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

sealed class EpubUiState {
    data object Loading : EpubUiState()
    data class Success(val document: DocumentEntity, val book: EpubBook) : EpubUiState()
    data class Error(val displayName: String?, val message: String) : EpubUiState()
}

class EpubReaderViewModel(
    private val documentId: String,
    private val repository: DocumentRepository,
    private val readerSettingsRepository: ReaderSettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<EpubUiState>(EpubUiState.Loading)
    val uiState: StateFlow<EpubUiState> = _uiState.asStateFlow()

    internal var restoreSession: com.m1racle.reader.epub.EpubRestoreSession? = null
        private set

    val readerSettings = readerSettingsRepository.settingsFlow.map<ReaderSettings, ReaderSettings?> { it }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000),
        null,
    )

    init {
        load()
    }

    fun setFontScale(scale: Float) {
        viewModelScope.launch {
            try {
                readerSettingsRepository.updateFontScale(scale)
            } catch (_: Exception) {
            }
        }
    }

    fun setLineHeightScale(scale: Float) {
        viewModelScope.launch {
            try {
                readerSettingsRepository.updateLineHeightScale(scale)
            } catch (_: Exception) {
            }
        }
    }

    fun setContentPaddingLevel(level: ContentPaddingLevel) {
        viewModelScope.launch {
            try {
                readerSettingsRepository.updateContentPaddingLevel(level)
            } catch (_: Exception) {
            }
        }
    }

    fun resetReaderSettings() {
        viewModelScope.launch {
            try {
                readerSettingsRepository.resetToDefaults()
            } catch (_: Exception) {
            }
        }
    }

    private val positionSaver = ReadingPositionSaver<EpubPosition>(viewModelScope) { position ->
        repository.updateEpubReadingPosition(
            id = documentId,
            anchor = position.anchor.takeIf { position.canSaveAnchor },
            lastScrollIndex = position.sourceRow,
            lastScrollOffset = position.pixelOffset,
            readingProgress = position.progress,
        )
    }

    /** The first save after opening a pre-anchor record is its one-time anchor migration. */
    internal fun saveReadingPosition(position: EpubPosition) {
        positionSaver.save(position)
    }

    fun flushReadingPosition() = positionSaver.flushFinal()

    private fun load() {
        viewModelScope.launch {
            _uiState.value = EpubUiState.Loading
            try {
                val entity = repository.getDocumentById(documentId)
                if (entity == null) {
                    _uiState.value = EpubUiState.Error(null, "文档不存在")
                    return@launch
                }
                val book = repository.openEpubBook(documentId)
                val document = repository.getDocumentById(documentId) ?: entity
                restoreSession = com.m1racle.reader.epub.EpubRestoreSession(
                    com.m1racle.reader.epub.EpubReadingAnchors.restore(book, document),
                )
                _uiState.value = EpubUiState.Success(
                    document = document,
                    book = book,
                )
            } catch (e: EpubException) {
                val entity = repository.getDocumentById(documentId)
                _uiState.value = EpubUiState.Error(
                    entity?.displayName,
                    e.message ?: "打开失败",
                )
            } catch (_: Exception) {
                val entity = repository.getDocumentById(documentId)
                _uiState.value = EpubUiState.Error(entity?.displayName, "文件可能已损坏")
            }
        }
    }

    override fun onCleared() {
        positionSaver.flushFinal()
        super.onCleared()
    }

    class Factory(
        private val documentId: String,
        private val repository: DocumentRepository,
        private val readerSettingsRepository: ReaderSettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(EpubReaderViewModel::class.java)) {
                return EpubReaderViewModel(
                    documentId = documentId,
                    repository = repository,
                    readerSettingsRepository = readerSettingsRepository,
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
