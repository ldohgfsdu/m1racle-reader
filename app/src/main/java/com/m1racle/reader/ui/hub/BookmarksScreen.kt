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
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m1racle.reader.R
import com.m1racle.reader.data.local.BookmarkLocationKind
import com.m1racle.reader.data.local.BookmarkWithDocument
import com.m1racle.reader.data.repository.ReadingRepository
import com.m1racle.reader.ui.home.HomeDesignTokens
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class BookmarksUiState(
    val bookmarks: List<BookmarkWithDocument> = emptyList(),
    val ready: Boolean = false,
)

/** 书签页 ViewModel：列表查询在 DAO/Repository，格式化在纯 Formatter。 */
class BookmarksViewModel(
    readingRepository: ReadingRepository,
) : ViewModel() {
    val uiState: StateFlow<BookmarksUiState> =
        readingRepository.observeAllBookmarks()
            .map { BookmarksUiState(bookmarks = it, ready = true) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Lazily,
                initialValue = BookmarksUiState(),
            )
}

/**
 * H2.2 Phase 4 书签页：全量书签 + 所属文档名。locationKind 决定位置语义
 * （PAGE_INDEX → 页码；SCROLL_POSITION → 滚动位置），不假设都是 PDF 页码。
 */
@Composable
fun BookmarksScreen(
    viewModel: BookmarksViewModel,
    onOpenDocument: (documentId: String) -> Unit,
    onNavigateHub: (HubDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val bookmarks = uiState.bookmarks
    HubGlassShell(
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Text(
                text = stringResource(R.string.hub_bookmarks_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(
                    horizontal = HomeDesignTokens.PageHorizontal,
                    vertical = 12.dp,
                ),
            )
            if (uiState.ready && bookmarks.isEmpty()) {
                EmptyBookmarksHint(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            } else if (bookmarks.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = HomeDesignTokens.PageHorizontal,
                        end = HomeDesignTokens.PageHorizontal,
                        top = 4.dp,
                        bottom = HomeDesignTokens.DockContentClearance,
                    ),
                ) {
                    items(bookmarks, key = { it.id }) { bookmark ->
                        BookmarkRow(
                            bookmark = bookmark,
                            onClick = { onOpenDocument(bookmark.documentId) },
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
private fun BookmarkRow(
    bookmark: BookmarkWithDocument,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = HomeDesignTokens.CardShape,
        color = scheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.45f)),
        tonalElevation = 1.dp,
        modifier = modifier,
        onClick = onClick,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = BookmarkRowFormatter.headline(bookmark.title, bookmark.documentName),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = BookmarkRowFormatter.createdAtLine(
                        createdAtMs = bookmark.createdAt,
                        nowMs = System.currentTimeMillis(),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = bookmark.documentName,
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = BookmarkRowFormatter.locationLine(bookmark.locationKind, bookmark.location),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = scheme.primary,
                )
            }
            bookmark.note?.takeIf { it.isNotBlank() }?.let { note ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EmptyBookmarksHint(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = HomeDesignTokens.PageHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Bookmarks,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.hub_bookmarks_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.MenuBook,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
        )
    }
}
