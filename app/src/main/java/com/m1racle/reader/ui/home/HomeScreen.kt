package com.m1racle.reader.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import com.m1racle.reader.ui.hub.HubPageHeader
import com.m1racle.reader.ui.hub.HubEmptyState
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m1racle.reader.R
import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.markdown.DocumentListTitle

import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Home keeps its actions fixed while the bounded reading list scrolls. */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenDocument: (DocumentEntity) -> Unit,
    onImport: () -> Unit,
    onOpenSearch: () -> Unit = {},
    onNavigateHub: (com.m1racle.reader.ui.hub.HubDestination) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val view = LocalView.current
    LaunchedEffect(Unit) {
        val activity = view.context as? android.app.Activity ?: return@LaunchedEffect
        val controller = WindowInsetsControllerCompat(activity.window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
    }
    Box(modifier = modifier.fillMaxSize()) {
        HomeContent(
            uiState = uiState,
            isImporting = isImporting,
            onImport = onImport,
            onOpenSearch = onOpenSearch,
            onOpen = onOpenDocument,
            onDelete = viewModel::deleteDocument,
        )
    }
}

/** Fixed header and system insets; list content scrolls behind the floating glass. */
@Composable
internal fun HomeContent(
    uiState: HomeUiState,
    isImporting: Boolean,
    onImport: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpen: (DocumentEntity) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
    statusTop: Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
    navBottom: Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
) {
    Column(
        modifier = modifier.fillMaxSize()
            .background(com.m1racle.reader.ui.theme.M1racleReaderTheme.surface.background)
            .padding(top = statusTop),
    ) {
        HubPageHeader(title = stringResource(R.string.app_name), modifier = Modifier.testTag("home-header")) {
            IconButton(onClick = onOpenSearch) {
                Icon(Icons.Default.Search, stringResource(R.string.search_library_entry), modifier = Modifier.size(22.dp))
            }
        }
        if (isImporting) {
            Text(
                text = stringResource(R.string.home_importing),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = HomeDesignTokens.PageHorizontal, vertical = 4.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (uiState.ready && uiState.isEmpty) {
            HubEmptyState(
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                title = stringResource(R.string.empty_title),
                description = stringResource(R.string.empty_subtitle),
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .padding(bottom = HomeDesignTokens.DockContentClearance + navBottom)
                    .clipToBounds().testTag("home-empty"),
            ) {
                Spacer(Modifier.height(24.dp))
                Button(onClick = onImport, enabled = !isImporting) {
                    Text(stringResource(R.string.import_file), textAlign = TextAlign.Center)
                }
            }
        } else {
            RecentDocumentList(
                documents = uiState.documents,
                continueReading = uiState.continueReading,
                onOpen = onOpen,
                onDelete = onDelete,
                navBottom = navBottom,
                modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds().testTag("home-list"),
            )
        }
    }
}

@Composable
private fun RecentDocumentList(
    documents: List<DocumentEntity>,
    continueReading: DocumentEntity?,
    navBottom: Dp,
    onOpen: (DocumentEntity) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = rememberLazyListState(),
        modifier = modifier,
        contentPadding = PaddingValues(
            start = HomeDesignTokens.PageHorizontal,
            end = HomeDesignTokens.PageHorizontal,
            top = 8.dp,
            bottom = HomeDesignTokens.DockContentClearance + navBottom,
        ),
    ) {
        if (continueReading != null) {
            item(key = "home_continue_reading") {
                ContinueReadingCard(
                    document = continueReading,
                    positionLine = readingPositionLine(continueReading),
                    onOpen = { onOpen(continueReading) },
                    onDelete = { onDelete(continueReading.id) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                )
            }
        }
        val recentDocuments = documents.filter { it.id != continueReading?.id }
        if (recentDocuments.isNotEmpty()) {
            item(key = "home_recent_section") {
                Text(stringResource(R.string.recent_reads),
                    style = com.m1racle.reader.ui.theme.M1racleReaderTheme.typography.section,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp).semantics { heading() })
            }
            items(recentDocuments, key = { doc -> doc.id }, contentType = { "home_doc_row" }) { document ->
                DocumentListRow(document, onOpen = { onOpen(document) },
                    onDelete = { onDelete(document.id) }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

// ── Title / meta pure helpers ───────────────────────────────────────────────

internal data class DocumentCardTitles(
    val displayTitle: String,
    /** Short type/format label (PDF / MD / package). */
    val typeLabel: String,
)

internal fun resolveDocumentCardTitles(document: DocumentEntity): DocumentCardTitles {
    val fullFileName = fullFileNameOf(document)
    if (document.isPdf) {
        return DocumentCardTitles(
            displayTitle = if (fullFileName.endsWith(".pdf", ignoreCase = true)) fullFileName.dropLast(4).trimEnd() else fullFileName,
            typeLabel = "PDF",
        )
    }
    if (document.isEpub) {
        val displayTitle = DocumentListTitle.normalizeListDisplayTitle(
            extractedTitle = document.title,
            fileName = fullFileName,
        )
        return DocumentCardTitles(
            displayTitle = if (fullFileName.endsWith(".epub", ignoreCase = true) && displayTitle == fullFileName) {
                fullFileName.dropLast(5).trimEnd()
            } else {
                displayTitle
            },
            typeLabel = "EPUB",
        )
    }
    if (document.isMarkdownPackage) {
        val displayTitle = DocumentListTitle.normalizeListDisplayTitle(
            extractedTitle = document.title,
            fileName = fullFileName,
        )
        return DocumentCardTitles(
            displayTitle = displayTitle,
            typeLabel = "MD 教材包",
        )
    }
    val displayTitle = DocumentListTitle.normalizeListDisplayTitle(
        extractedTitle = document.title,
        fileName = fullFileName,
    )
    return DocumentCardTitles(
        displayTitle = displayTitle,
        typeLabel = "Markdown",
    )
}

private fun fullFileNameOf(document: DocumentEntity): String {
    val fromDisplay = document.displayName.trim()
    if (fromDisplay.isNotEmpty()) return fromDisplay
    val fromUri = fileNameFromUri(document.uri)
    return fromUri.ifBlank { "未命名文档" }
}

private fun fileNameFromUri(uri: String): String {
    val decoded = try {
        java.net.URLDecoder.decode(uri, Charsets.UTF_8.name())
    } catch (_: Exception) {
        uri
    }
    val path = decoded.substringBefore('?').substringBefore('#')
    return path.substringAfterLast('/').ifBlank { decoded }
}

/** List metadata: format · reading progress once. No last-opened time. */
internal fun formatMeta(document: DocumentEntity): String = formatListMetadata(document)

internal fun formatPdfProgress(document: DocumentEntity): String {
    val pageCount = document.pageCount
    val pageIndex = document.lastScrollIndex.coerceAtLeast(0)
    if (pageCount > 0) {
        val human = (pageIndex + 1).coerceIn(1, pageCount)
        return "$human / $pageCount"
    }
    return formatReadingProgress(document.readingProgress)
}


internal fun formatReadingProgress(progress: Float): String {
    val percent = (progress.coerceIn(0f, 1f) * 100f).toInt()
    return when {
        percent <= 0 -> "未读"
        percent >= 99 -> "已读完"
        else -> "已读 $percent%"
    }
}
