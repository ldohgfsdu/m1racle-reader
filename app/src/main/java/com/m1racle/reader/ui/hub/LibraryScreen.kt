package com.m1racle.reader.ui.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.m1racle.reader.R
import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.data.repository.DocumentRepository
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * H2.2 Phase 3 文库页：全部 / PDF / Markdown 分类（EPUB 为 PRD 外扩展位，
 * 仅留枚举位置不实现）。卡片主展示文件名/类型/进度，日期/大小弱化。
 */
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenDocument: (DocumentEntity) -> Unit,
    onNavigateHub: (HubDestination) -> Unit,
    onOpenSearch: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    HubGlassShell(
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = HomeDesignTokens.PageHorizontal,
                    vertical = 12.dp,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.hub_library_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onOpenSearch) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.search_library_entry),
                    )
                }
            }
            Row(
                modifier = Modifier.padding(horizontal = HomeDesignTokens.PageHorizontal),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LibraryFilterChip(
                    labelRes = R.string.hub_filter_all,
                    selected = filter == LibraryFilter.ALL,
                    onClick = { viewModel.setFilter(LibraryFilter.ALL) },
                )
                LibraryFilterChip(
                    labelRes = R.string.hub_filter_pdf,
                    selected = filter == LibraryFilter.PDF,
                    onClick = { viewModel.setFilter(LibraryFilter.PDF) },
                )
                LibraryFilterChip(
                    labelRes = R.string.hub_filter_markdown,
                    selected = filter == LibraryFilter.MARKDOWN,
                    onClick = { viewModel.setFilter(LibraryFilter.MARKDOWN) },
                )
                LibraryFilterChip(
                    labelRes = R.string.hub_filter_epub,
                    selected = filter == LibraryFilter.EPUB,
                    onClick = { viewModel.setFilter(LibraryFilter.EPUB) },
                )
            }
            if (uiState.ready && uiState.documents.isEmpty()) {
                EmptyLibraryHint(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            } else if (uiState.documents.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = HomeDesignTokens.PageHorizontal,
                        end = HomeDesignTokens.PageHorizontal,
                        top = 12.dp,
                        // 底部避让 Dock（导航条 + Dock 高度 + 间隙）。
                        bottom = HomeDesignTokens.DockContentClearance,
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
private fun LibraryFilterChip(
    labelRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(stringResource(labelRes), style = MaterialTheme.typography.labelLarge) },
        shape = RoundedCornerShape(50),
        border = null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

@Composable
private fun EmptyLibraryHint(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = HomeDesignTokens.PageHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.hub_library_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
