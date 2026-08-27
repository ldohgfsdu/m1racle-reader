package com.markleaf.app.ui.hub

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
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material3.Icon
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.markleaf.app.R
import com.markleaf.app.data.local.BookmarkLocationKind
import com.markleaf.app.data.local.BookmarkWithDocument
import com.markleaf.app.data.repository.ReadingRepository
import com.markleaf.app.ui.home.HomeDesignTokens

/** 书签页 ViewModel：冷流直出（列表查询在 DAO/Repository，格式化在纯 Formatter）。 */
class BookmarksViewModel(
    readingRepository: ReadingRepository,
) : ViewModel() {
    val bookmarks: kotlinx.coroutines.flow.Flow<List<BookmarkWithDocument>> =
        readingRepository.observeAllBookmarks()
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
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle(initialValue = emptyList())
    HubGlassShell(
        selected = HubDestination.BOOKMARKS,
        onSelect = onNavigateHub,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
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
            if (bookmarks.isEmpty()) {
                EmptyBookmarksHint(
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
                        top = 4.dp,
                        bottom = 110.dp,
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
    val interaction = remember { MutableInteractionSource() }
    Surface(
        shape = HomeDesignTokens.CardShape,
        color = scheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.45f)),
        tonalElevation = 1.dp,
        modifier = modifier.clickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
        ),
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
                    color = scheme.onSurfaceVariant.copy(alpha = 0.55f),
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
