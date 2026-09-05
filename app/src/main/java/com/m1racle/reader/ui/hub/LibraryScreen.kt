package com.m1racle.reader.ui.hub

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.m1racle.reader.R
import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.data.repository.DocumentRepository
import com.m1racle.reader.ui.home.HomeDesignTokens
import com.m1racle.reader.ui.home.formatFileSize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow

/** 文库 UI 状态：过滤后的文档列表（保持最近打开序）。 */
data class LibraryUiState(
    val documents: List<DocumentEntity> = emptyList(),
    val filter: LibraryFilter = LibraryFilter.ALL,
)

internal fun libraryUiState(
    documents: List<DocumentEntity>,
    filter: LibraryFilter,
): LibraryUiState = LibraryUiState(
    documents = LibraryFilters.apply(documents, filter),
    filter = filter,
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

    val uiState: Flow<LibraryUiState> =
        combine(repository.observeRecentDocuments(), _filter) { docs, f ->
            libraryUiState(docs, f)
        }

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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle(initialValue = LibraryUiState())
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    HubGlassShell(
        onSelect = onNavigateHub,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
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
            }
            if (uiState.documents.isEmpty()) {
                EmptyLibraryHint(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = HomeDesignTokens.PageHorizontal,
                        end = HomeDesignTokens.PageHorizontal,
                        top = 12.dp,
                        // 底部避让 Dock（导航条 + Dock 高度 + 间隙）。
                        bottom = 110.dp,
                    ),
                ) {
                    items(uiState.documents, key = { it.id }) { document ->
                        LibraryDocumentCard(
                            document = document,
                            onOpen = { onOpenDocument(document) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = HomeDesignTokens.CardGap),
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
        label = { Text(stringResource(labelRes)) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
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

/** 文库卡：文件名 + 类型 + 进度为主，日期/大小弱化为单行次要信息。 */
@Composable
private fun LibraryDocumentCard(
    document: DocumentEntity,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val progress = document.readingProgress.coerceIn(0f, 1f)
    Surface(
        modifier = modifier.clickable(
            interactionSource = interaction,
            indication = null,
            onClick = onOpen,
        ),
        shape = HomeDesignTokens.CardShape,
        color = scheme.surfaceContainerLow,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.45f)),
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = document.displayName.ifBlank { "未命名文档" },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (document.isPdf) "PDF" else "MD",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = scheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (document.isPdf && document.pageCount > 0) {
                        "第 ${(document.lastScrollIndex + 1).coerceAtMost(document.pageCount)} / ${document.pageCount} 页"
                    } else {
                        val percent = (progress * 100).toInt()
                        if (percent <= 0) "未读" else "已读 $percent%"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // 次要信息弱化：低对比单行。
                Text(
                    text = formatFileSize(document.fileSize),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.55f),
                )
            }
            Spacer(Modifier.height(7.dp))
            LinearProgressIndicator(
                progress = { progress },
                trackColor = scheme.outlineVariant.copy(alpha = 0.45f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HomeDesignTokens.ProgressHeight),
            )
        }
    }
}
