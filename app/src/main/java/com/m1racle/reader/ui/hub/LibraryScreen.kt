package com.m1racle.reader.ui.hub

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.m1racle.reader.ui.components.PillSegmentedControl
import com.m1racle.reader.R
import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.data.repository.DocumentRepository
import com.m1racle.reader.ui.home.DocumentListRow
import com.m1racle.reader.ui.home.HomeDesignTokens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** 文库 UI 状态：过滤后的文档列表（保持最近打开序）。 */
data class LibraryUiState(
    val documents: List<DocumentEntity> = emptyList(),
    val filter: LibraryFilter = LibraryFilter.ALL,
    val ready: Boolean = false,
)

internal fun libraryUiState(
    documents: List<DocumentEntity>,
    filter: LibraryFilter,
): LibraryUiState = LibraryUiState(
    documents = LibraryFilters.apply(documents, filter),
    filter = filter,
    ready = true,
)

/** 文库 ViewModel：薄壳——状态推导在 [libraryUiState] 纯函数，冷流直出。 */
class LibraryViewModel(
    repository: DocumentRepository,
) : ViewModel() {

    private val _filter = MutableStateFlow(LibraryFilter.ALL)
    val filter: StateFlow<LibraryFilter> = _filter

    fun setFilter(filter: LibraryFilter) {
        _filter.value = filter
    }

    val uiState: StateFlow<LibraryUiState> =
        combine(repository.observeRecentDocuments(), _filter) { docs, f ->
            libraryUiState(docs, f)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = LibraryUiState(),
        )

    class Factory(
        private val repository: DocumentRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LibraryViewModel::class.java)) {
                return LibraryViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel")
        }
    }
}

/**
 * Shared document rows with real format filters; selection is separate from content.
 */
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenDocument: (DocumentEntity) -> Unit,
    onOpenSearch: () -> Unit = {},
    onNavigateHub: (HubDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    HubGlassShell(
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            HubPageHeader(title = stringResource(R.string.hub_library_title)) {
                IconButton(onClick = onOpenSearch) {
                    Icon(Icons.Default.Search, stringResource(R.string.search_library_entry))
                }
            }
            val filters = LibraryFilter.entries
            PillSegmentedControl(
                labels = listOf(
                    stringResource(R.string.hub_filter_all),
                    stringResource(R.string.hub_filter_pdf),
                    stringResource(R.string.hub_filter_markdown),
                    stringResource(R.string.hub_filter_epub),
                ),
                selectedIndex = filters.indexOf(filter),
                onSelect = { viewModel.setFilter(filters[it]) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = HomeDesignTokens.PageHorizontal),
            )
            if (uiState.ready && uiState.documents.isEmpty()) {
                EmptyLibraryHint(
                    filtered = filter != LibraryFilter.ALL,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(bottom = HomeDesignTokens.DockContentClearance + navBottom),
                )
            } else if (uiState.documents.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = HomeDesignTokens.PageHorizontal,
                        end = HomeDesignTokens.PageHorizontal,
                        top = 12.dp,
                        // 底部避让 Dock（导航条 + Dock 高度 + 间隙）。
                        bottom = HomeDesignTokens.DockContentClearance + navBottom,
                    ),
                ) {
                    items(uiState.documents, key = { it.id }) { document ->
                        DocumentListRow(
                            document = document,
                            onOpen = { onOpenDocument(document) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryHint(filtered: Boolean, modifier: Modifier = Modifier) {
    HubEmptyState(icon = Icons.Filled.Description,
        title = stringResource(if (filtered) R.string.hub_library_filter_empty_title else R.string.hub_library_empty_title),
        description = stringResource(if (filtered) R.string.hub_library_filter_empty else R.string.hub_library_empty),
        modifier = modifier)
}
