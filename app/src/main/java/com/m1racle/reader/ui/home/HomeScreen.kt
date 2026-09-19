package com.m1racle.reader.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
    onNavigateHub: (com.m1racle.reader.ui.hub.HubDestination) -> Unit = {},
    onOpenSearch: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val view = LocalView.current
    LaunchedEffect(Unit) {
        val activity = view.context as? android.app.Activity ?: return@LaunchedEffect
        val controller = WindowInsetsControllerCompat(activity.window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onImportDocument(uri)
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is HomeEvent.OpenDocument -> {
                    onOpenDocument(event.document)
                    return@collect
                }
                HomeEvent.UnsupportedFormat -> context.getString(R.string.error_unsupported_format)
                HomeEvent.SourceTooLarge -> context.getString(R.string.home_import_too_large,
                    com.m1racle.reader.share.ExternalDocumentImporter.MAX_BYTES / (1024L * 1024L))
                HomeEvent.DeleteFailed -> context.getString(R.string.home_delete_failed)
                HomeEvent.ReadFailed -> context.getString(R.string.error_read_failed)
            }
            snackbarHostState.showSnackbar(message)
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        HomeContent(
            uiState = uiState,
            isImporting = isImporting,
            onImport = { launcher.launch(arrayOf("text/*", "application/pdf", "application/epub+zip", "application/zip",
                "application/x-zip-compressed", "application/octet-stream")) },
            onOpenSearch = onOpenSearch,
            onOpen = onOpenDocument,
            onDelete = viewModel::deleteDocument,
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom =
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                    HomeDesignTokens.DockHeight + HomeDesignTokens.DockBottomGap + 8.dp),
        )
    }
}

/** Fixed safe-area owner: scroll content cannot draw or receive taps in system/dock regions. */
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
    val bottomReserved = navBottom + HomeDesignTokens.DockHeight + HomeDesignTokens.DockBottomGap + 8.dp
    Column(
        modifier = modifier.fillMaxSize()
            .background(com.m1racle.reader.ui.theme.M1racleReaderTheme.surface.background)
            .padding(top = statusTop, bottom = bottomReserved),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().testTag("home-header")
                .padding(start = HomeDesignTokens.PageHorizontal, end = 12.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = com.m1racle.reader.ui.theme.M1racleReaderTheme.typography.title.copy(
                    fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onOpenSearch) {
                Icon(Icons.Default.Search, stringResource(R.string.search_library_entry), modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = onImport, enabled = !isImporting) {
                Icon(Icons.Default.Add, stringResource(R.string.import_file), modifier = Modifier.size(24.dp))
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
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds()
                    .testTag("home-empty")
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = HomeDesignTokens.PageHorizontal, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.empty_title),
                    style = com.m1racle.reader.ui.theme.M1racleReaderTheme.typography.title,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.empty_subtitle),
                    style = com.m1racle.reader.ui.theme.M1racleReaderTheme.typography.body,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds().testTag("home-list"),
            )
        }
    }
}

@Composable
private fun RecentDocumentList(
    documents: List<DocumentEntity>,
    continueReading: DocumentEntity?,
    onOpen: (DocumentEntity) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = rememberLazyListState(),
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = HomeDesignTokens.PageHorizontal, vertical = 8.dp),
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
                    style = com.m1racle.reader.ui.theme.M1racleReaderTheme.typography.caption,
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
