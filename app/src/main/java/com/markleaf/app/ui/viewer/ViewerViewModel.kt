package com.markleaf.app.ui.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.markleaf.app.data.local.DocumentEntity
import com.markleaf.app.data.repository.DocumentReadException
import com.markleaf.app.data.repository.DocumentRepository
import com.markleaf.app.data.settings.ContentPaddingLevel
import com.markleaf.app.data.settings.ReaderSettings
import com.markleaf.app.data.settings.ReaderSettingsRepository
import com.markleaf.app.markdown.MarkdownBlock
import com.markleaf.app.markdown.MarkdownParser
import com.markleaf.app.markdown.AnchorRegistry
import com.markleaf.app.markdown.TocItem
import com.markleaf.app.search.DocumentSearch
import com.markleaf.app.search.SearchMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class ViewerUiState {
    data object Loading : ViewerUiState()

    data class Success(
        val document: DocumentEntity,
        val blocks: List<MarkdownBlock>,
        val toc: List<TocItem>,
        val anchorRegistry: AnchorRegistry,
        val blockIds: List<String>,
        val isTruncated: Boolean,
        /** True when parser failed and content is a raw-text fallback block. */
        val isParseFallback: Boolean = false,
    ) : ViewerUiState()

    data class Error(
        val displayName: String?,
        val message: String,
    ) : ViewerUiState()
}

data class DocumentSearchUiState(
    val isActive: Boolean = false,
    val query: String = "",
    val matches: List<SearchMatch> = emptyList(),
    val currentIndex: Int = 0,
) {
    val matchCount: Int get() = matches.size
    val currentMatch: SearchMatch?
        get() = matches.getOrNull(currentIndex)

    val counterLabel: String
        get() {
            if (matches.isEmpty()) return "0 / 0"
            return "${currentIndex + 1} / ${matches.size}"
        }
}

class ViewerViewModel(
    private val documentId: String,
    private val repository: DocumentRepository,
    private val readerSettingsRepository: ReaderSettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ViewerUiState>(ViewerUiState.Loading)
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    private val _searchState = MutableStateFlow(DocumentSearchUiState())
    val searchState: StateFlow<DocumentSearchUiState> = _searchState.asStateFlow()

    private var searchJob: Job? = null

    val readerSettings: StateFlow<ReaderSettings> = readerSettingsRepository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ReaderSettings.Default,
        )

    init {
        load()
    }

    fun retry() {
        load()
    }

    fun openSearch() {
        _searchState.value = DocumentSearchUiState(isActive = true)
    }

    fun closeSearch() {
        searchJob?.cancel()
        _searchState.value = DocumentSearchUiState()
    }

    fun onSearchQueryChange(query: String) {
        _searchState.update { it.copy(query = query) }
        val blocks = (_uiState.value as? ViewerUiState.Success)?.blocks.orEmpty()
        searchJob?.cancel()
        if (query.trim().isEmpty()) {
            _searchState.update { it.copy(matches = emptyList(), currentIndex = 0) }
            return
        }
        searchJob = viewModelScope.launch {
            val matches = withContext(Dispatchers.Default) {
                DocumentSearch.search(blocks, query)
            }
            _searchState.update {
                it.copy(
                    matches = matches,
                    currentIndex = 0,
                )
            }
        }
    }

    fun goToNextMatch() {
        _searchState.update { state ->
            if (state.matches.isEmpty()) return@update state
            val next = (state.currentIndex + 1) % state.matches.size
            state.copy(currentIndex = next)
        }
    }

    fun goToPreviousMatch() {
        _searchState.update { state ->
            if (state.matches.isEmpty()) return@update state
            val prev = if (state.currentIndex <= 0) {
                state.matches.lastIndex
            } else {
                state.currentIndex - 1
            }
            state.copy(currentIndex = prev)
        }
    }

    fun setFontScale(scale: Float) {
        viewModelScope.launch {
            try {
                readerSettingsRepository.updateFontScale(scale)
            } catch (_: Exception) {
                // Ignore persistence errors.
            }
        }
    }

    fun setLineHeightScale(scale: Float) {
        viewModelScope.launch {
            try {
                readerSettingsRepository.updateLineHeightScale(scale)
            } catch (_: Exception) {
                // Ignore persistence errors.
            }
        }
    }

    fun setContentPaddingLevel(level: ContentPaddingLevel) {
        viewModelScope.launch {
            try {
                readerSettingsRepository.updateContentPaddingLevel(level)
            } catch (_: Exception) {
                // Ignore persistence errors.
            }
        }
    }

    fun resetReaderSettings() {
        viewModelScope.launch {
            try {
                readerSettingsRepository.resetToDefaults()
            } catch (_: Exception) {
                // Ignore persistence errors.
            }
        }
    }

    /**
     * Persist absolute LazyList indices (including any list header items).
     * Uses [NonCancellable] so a final save on leave is not dropped when the scope cancels.
     */
    fun saveReadingPosition(
        lastScrollIndex: Int,
        lastScrollOffset: Int,
        readingProgress: Float,
    ) {
        viewModelScope.launch(NonCancellable + Dispatchers.IO) {
            try {
                repository.updateReadingPosition(
                    id = documentId,
                    lastScrollIndex = lastScrollIndex,
                    lastScrollOffset = lastScrollOffset,
                    readingProgress = readingProgress,
                )
            } catch (_: Exception) {
                // Never crash on background save.
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.value = ViewerUiState.Loading

            val entity = try {
                repository.getDocumentById(documentId)
            } catch (_: Exception) {
                null
            }

            if (entity == null) {
                _uiState.value = ViewerUiState.Error(
                    displayName = null,
                    message = "文件可能已被移动或删除",
                )
                return@launch
            }

            try {
                repository.touchLastOpenedAt(entity.id)
            } catch (_: Exception) {
                // Non-fatal.
            }

            val textResult = repository.readDocumentText(entity.uri)
            textResult.fold(
                onSuccess = { preview ->
                    var parseFallback = false
                    val parseResult = try {
                        withContext(Dispatchers.Default) {
                            MarkdownParser.parse(preview.text)
                        }
                    } catch (_: Exception) {
                        parseFallback = true
                        MarkdownParser.ParseResult(
                            blocks = if (preview.text.isBlank()) {
                                emptyList()
                            } else {
                                listOf(MarkdownBlock.Paragraph(preview.text))
                            },
                            toc = emptyList(),
                            anchorRegistry = AnchorRegistry.Empty,
                            blockIds = if (preview.text.isBlank()) {
                                emptyList()
                            } else {
                                listOf("paragraph-0")
                            },
                        )
                    }

                    val h1Title = parseResult.toc
                        .firstOrNull { it.level == 1 }
                        ?.title
                        ?.takeIf { it.isNotBlank() }

                    val updated = try {
                        repository.updateParseMetadata(
                            id = entity.id,
                            title = h1Title,
                            headingCount = parseResult.toc.size,
                        )
                        entity.copy(
                            title = h1Title,
                            headingCount = parseResult.toc.size,
                        )
                    } catch (_: Exception) {
                        entity.copy(
                            title = h1Title,
                            headingCount = parseResult.toc.size,
                        )
                    }

                    _uiState.value = ViewerUiState.Success(
                        document = updated,
                        blocks = parseResult.blocks,
                        toc = parseResult.toc,
                        anchorRegistry = parseResult.anchorRegistry,
                        blockIds = parseResult.blockIds,
                        isTruncated = preview.isTruncated,
                        isParseFallback = parseFallback,
                    )
                    // Re-run search if user is already in search mode (rare race).
                    val q = _searchState.value.query
                    if (_searchState.value.isActive && q.trim().isNotEmpty()) {
                        onSearchQueryChange(q)
                    }
                },
                onFailure = { error ->
                    val message = when (error) {
                        is DocumentReadException.NotFound ->
                            "文件可能已被移动或删除"
                        is DocumentReadException.PermissionDenied ->
                            "文件访问权限已失效，请重新导入"
                        else ->
                            "无法读取该文件，请重新选择"
                    }
                    _uiState.value = ViewerUiState.Error(
                        displayName = entity.displayName,
                        message = message,
                    )
                },
            )
        }
    }

    class Factory(
        private val documentId: String,
        private val repository: DocumentRepository,
        private val readerSettingsRepository: ReaderSettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ViewerViewModel::class.java)) {
                return ViewerViewModel(
                    documentId = documentId,
                    repository = repository,
                    readerSettingsRepository = readerSettingsRepository,
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
