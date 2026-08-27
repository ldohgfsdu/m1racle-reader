package com.m1racle.reader.ui.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.data.repository.DocumentReadException
import com.m1racle.reader.data.repository.DocumentRepository
import com.m1racle.reader.data.settings.ContentPaddingLevel
import com.m1racle.reader.data.settings.ReaderSettings
import com.m1racle.reader.data.settings.ReaderSettingsRepository
import com.m1racle.reader.markdown.MarkdownBlock
import com.m1racle.reader.markdown.MarkdownParser
import com.m1racle.reader.markdown.AnchorRegistry
import com.m1racle.reader.markdown.TocItem
import com.m1racle.reader.search.DocumentSearch
import com.m1racle.reader.search.SearchMatch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

sealed class ViewerUiState {
    data object Loading : ViewerUiState()

    data class Success(
        val document: DocumentEntity,
        val blocks: List<MarkdownBlock>,
        val toc: List<TocItem>,
        val anchorRegistry: AnchorRegistry,
        val blockIds: List<String>,
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
    /** Bound document identity — results from another key must not be applied. */
    val documentKey: String = "",
    /** Raw user query (not trimmed) for UI. */
    val query: String = "",
    val matches: List<SearchMatch> = emptyList(),
    val currentIndex: Int = 0,
    /** True when [DocumentSearch.MAX_MATCHES] stopped further hits. */
    val resultsTruncated: Boolean = false,
) {
    val matchCount: Int get() = matches.size
    val currentMatch: SearchMatch?
        get() = matches.getOrNull(currentIndex)

    val counterLabel: String
        get() {
            // Empty query: no scan — avoid implying a completed zero-result search.
            if (query.isEmpty()) return "—"
            if (matches.isEmpty()) return "0 / 0"
            val total = if (resultsTruncated) {
                "${matches.size}+"
            } else {
                matches.size.toString()
            }
            // Guard against bad index (should not happen after reset).
            val idx = currentIndex.coerceIn(0, (matches.size - 1).coerceAtLeast(0)) + 1
            return "$idx / $total"
        }

    val hasMatches: Boolean get() = matches.isNotEmpty()
}

class ViewerViewModel(
    private val documentId: String,
    private val repository: DocumentRepository,
    private val readerSettingsRepository: ReaderSettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ViewerUiState>(ViewerUiState.Loading)
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    private val _searchState = MutableStateFlow(
        DocumentSearchUiState(documentKey = documentId),
    )
    val searchState: StateFlow<DocumentSearchUiState> = _searchState.asStateFlow()

    private var searchJob: Job? = null
    /** Last query that completed a scan (for distinctUntilChanged-style skip). */
    private var lastScannedQuery: String? = null
    /**
     * Monotonic generation: bumped on close/open and every query schedule.
     * Late results must match [searchGeneration] to apply.
     */
    private var searchGeneration: Long = 0L

    val readerSettings: StateFlow<ReaderSettings> = readerSettingsRepository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ReaderSettings.Default,
        )

    init {
        load()
    }

    override fun onCleared() {
        searchJob?.cancel()
        searchJob = null
        // Bounded final flush: the last known position survives scope
        // cancellation (single NonCancellable write, at most once).
        positionSaver.flushFinal()
        super.onCleared()
    }

    fun retry() {
        load()
    }

    fun openSearch() {
        searchJob?.cancel()
        lastScannedQuery = null
        searchGeneration++
        _searchState.value = DocumentSearchUiState(
            isActive = true,
            documentKey = documentId,
        )
    }

    fun closeSearch() {
        searchJob?.cancel()
        searchJob = null
        lastScannedQuery = null
        searchGeneration++
        _searchState.value = DocumentSearchUiState(documentKey = documentId)
    }

    /**
     * Updates the search query with debounce. Leading/trailing spaces are kept for matching.
     * Empty string clears matches without scanning. Identical re-entries skip re-scan.
     */
    fun onSearchQueryChange(query: String) {
        _searchState.update {
            it.copy(
                query = query,
                documentKey = documentId,
            )
        }
        searchJob?.cancel()

        if (query.isEmpty()) {
            lastScannedQuery = ""
            searchGeneration++
            _searchState.update {
                it.copy(
                    matches = emptyList(),
                    currentIndex = 0,
                    resultsTruncated = false,
                    documentKey = documentId,
                )
            }
            return
        }

        if (query == lastScannedQuery && _searchState.value.documentKey == documentId) {
            // Same query already scanned for this document — skip re-scan.
            return
        }

        val success = _uiState.value as? ViewerUiState.Success
        val blocks = success?.blocks.orEmpty()
        val blockIds = success?.blockIds.orEmpty()
        val keyAtSchedule = documentId
        val generationAtSchedule = ++searchGeneration

        searchJob = viewModelScope.launch {
            try {
                delay(SEARCH_DEBOUNCE_MS)
                // Re-check after debounce: may have been superseded.
                if (generationAtSchedule != searchGeneration) return@launch
                if (_searchState.value.query != query) return@launch
                if (keyAtSchedule != documentId) return@launch

                val result = withContext(Dispatchers.Default) {
                    DocumentSearch.search(
                        documentKey = keyAtSchedule,
                        blocks = blocks,
                        rawQuery = query,
                        blockIds = blockIds,
                    )
                }

                // Drop late results (cancel, newer query, or wrong document).
                if (generationAtSchedule != searchGeneration) return@launch
                if (keyAtSchedule != documentId) return@launch
                if (_searchState.value.query != query) return@launch
                if (result.documentKey != documentId) return@launch

                lastScannedQuery = query
                _searchState.update {
                    it.copy(
                        documentKey = documentId,
                        matches = result.matches,
                        currentIndex = 0,
                        resultsTruncated = result.truncated,
                    )
                }
            } catch (e: CancellationException) {
                throw e
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
     * Latest-wins and cancellable for high-frequency calls; the bounded final
     * flush on [onCleared] guarantees the last known position is persisted.
     */
    private val positionSaver = ReadingPositionSaver(viewModelScope) { index, offset, progress ->
        repository.updateReadingPosition(
            id = documentId,
            lastScrollIndex = index,
            lastScrollOffset = offset,
            readingProgress = progress,
        )
    }

    fun saveReadingPosition(
        lastScrollIndex: Int,
        lastScrollOffset: Int,
        readingProgress: Float,
    ) {
        positionSaver.save(lastScrollIndex, lastScrollOffset, readingProgress)
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
                onSuccess = { text ->
                    var parseFallback = false
                    val parseResult = try {
                        withContext(Dispatchers.Default) {
                            MarkdownParser.parse(text)
                        }
                    } catch (_: Exception) {
                        parseFallback = true
                        MarkdownParser.ParseResult(
                            blocks = if (text.isBlank()) {
                                emptyList()
                            } else {
                                listOf(MarkdownBlock.Paragraph(text))
                            },
                            toc = emptyList(),
                            anchorRegistry = AnchorRegistry.Empty,
                            blockIds = if (text.isBlank()) {
                                emptyList()
                            } else {
                                listOf("paragraph-0")
                            },
                        )
                    }

                    if (parseResult.ignoredTrailingHtmlAnchors.isNotEmpty()) {
                        android.util.Log.w(
                            "MARKDOWN_TOC",
                            "html_anchor_trailing_ignored documentId=${entity.id} " +
                                "ids=${parseResult.ignoredTrailingHtmlAnchors.joinToString(",")}",
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
                        isParseFallback = parseFallback,
                    )
                    // Re-run search if user is already in search mode (rare race after reload).
                    lastScannedQuery = null
                    val q = _searchState.value.query
                    if (_searchState.value.isActive && q.isNotEmpty()) {
                        onSearchQueryChange(q)
                    }
                },
                onFailure = { error ->
                    val message = when (error) {
                        is DocumentReadException.NotFound ->
                            "文件可能已被移动或删除"
                        is DocumentReadException.PermissionDenied ->
                            "文件访问权限已失效，请重新导入"
                        is DocumentReadException.TooLarge ->
                            "Markdown 文件过大，当前最多支持 ${error.maxBytes / (1024 * 1024)} MB"
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

    companion object {
        /** Debounce before scanning so rapid typing cancels prior work. */
        const val SEARCH_DEBOUNCE_MS: Long = 200L
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
