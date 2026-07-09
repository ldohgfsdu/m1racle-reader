package com.markleaf.app.ui.viewer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.markleaf.app.R
import com.markleaf.app.data.settings.ReaderSettings
import com.markleaf.app.markdown.TocItem
import com.markleaf.app.ui.viewer.components.MarkdownBlockView
import com.markleaf.app.ui.viewer.components.ReaderSettingsSheet
import com.markleaf.app.ui.viewer.components.ReaderTypography
import com.markleaf.app.ui.viewer.components.SearchTopBar
import com.markleaf.app.ui.viewer.components.TocDrawerContent
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    viewModel: ViewerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Top bar: file displayName only (never long H1) — single line + ellipsis in Scaffold.
    val title = when (val state = uiState) {
        is ViewerUiState.Success -> state.document.displayName
        is ViewerUiState.Error ->
            state.displayName ?: stringResource(R.string.viewer_title_fallback)
        ViewerUiState.Loading -> stringResource(R.string.viewer_loading)
    }

    when (val state = uiState) {
        is ViewerUiState.Success -> {
            ViewerSuccessScreen(
                state = state,
                title = title,
                viewModel = viewModel,
                onBack = onBack,
                modifier = modifier,
            )
        }

        ViewerUiState.Loading -> {
            ViewerScaffold(
                title = title,
                onBack = onBack,
                tocEnabled = false,
                onOpenToc = {},
                settingsEnabled = false,
                onOpenSettings = {},
                searchEnabled = false,
                onOpenSearch = {},
                searchState = null,
                onSearchQueryChange = {},
                onCloseSearch = {},
                onSearchPrevious = {},
                onSearchNext = {},
                modifier = modifier,
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.viewer_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        is ViewerUiState.Error -> {
            ViewerScaffold(
                title = title,
                onBack = onBack,
                tocEnabled = false,
                onOpenToc = {},
                settingsEnabled = false,
                onOpenSettings = {},
                searchEnabled = false,
                onOpenSearch = {},
                searchState = null,
                onSearchQueryChange = {},
                onCloseSearch = {},
                onSearchPrevious = {},
                onSearchNext = {},
                modifier = modifier,
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(onClick = onBack) {
                            Text(text = stringResource(R.string.back_to_home))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun ViewerSuccessScreen(
    state: ViewerUiState.Success,
    title: String,
    viewModel: ViewerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val hasToc = state.toc.isNotEmpty()
    val readerSettings by viewModel.readerSettings.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    val contentPadding = readerSettings.contentPaddingLevel.horizontalDp
    val snackbarHostState = remember { SnackbarHostState() }
    /*
     * Index coordinate system (MUST stay consistent):
     * - Room lastScrollIndex / LazyListState use ABSOLUTE list item indices
     * - Optional preview header occupies index 0 when isTruncated || isParseFallback
     * - Markdown blocks start at listHeaderCount
     * - TOC jump target = listHeaderCount + TocItem.blockIndex
     * - Active TOC uses (firstVisibleItemIndex - listHeaderCount) as content index
     */
    val listHeaderCount = when {
        state.isTruncated || state.isParseFallback -> 1
        else -> 0
    }
    val totalListItems = listHeaderCount + state.blocks.size

    val restoredIndex = remember(state.document.id, totalListItems) {
        safeScrollIndex(
            savedIndex = state.document.lastScrollIndex,
            totalItems = totalListItems,
        )
    }
    val restoredOffset = remember(state.document.id) {
        state.document.lastScrollOffset.coerceAtLeast(0)
    }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = restoredIndex,
        initialFirstVisibleItemScrollOffset = restoredOffset,
    )

    val activeTocItem by rememberActiveTocItem(
        toc = state.toc,
        listState = listState,
        listHeaderCount = listHeaderCount,
    )

    // Debounced save after scroll settles (~750ms). Leave flush is in DisposableEffect.
    LaunchedEffect(listState, state.document.id, totalListItems) {
        if (totalListItems <= 0) return@LaunchedEffect
        snapshotFlow {
            ReadingPosition(
                index = listState.firstVisibleItemIndex,
                offset = listState.firstVisibleItemScrollOffset,
                progress = readingProgressOf(listState),
            )
        }
            .distinctUntilChanged()
            .debounce(750L)
            .collect { position ->
                // Skip until LazyColumn has laid out; otherwise progress=0 wipes prior progress.
                if (listState.layoutInfo.totalItemsCount <= 0) return@collect
                viewModel.saveReadingPosition(
                    lastScrollIndex = position.index,
                    lastScrollOffset = position.offset,
                    readingProgress = position.progress,
                )
            }
    }

    // Flush latest position when leaving the screen.
    DisposableEffect(state.document.id, listState) {
        onDispose {
            // Only persist when the list has real layout info (avoid zeroing progress on quick back).
            if (totalListItems > 0 && listState.layoutInfo.totalItemsCount > 0) {
                viewModel.saveReadingPosition(
                    lastScrollIndex = listState.firstVisibleItemIndex,
                    lastScrollOffset = listState.firstVisibleItemScrollOffset,
                    readingProgress = readingProgressOf(listState),
                )
            }
            viewModel.closeSearch()
        }
    }

    // Scroll to current search match (absolute list index = header + blockIndex).
    LaunchedEffect(searchState.currentIndex, searchState.matches) {
        val match = searchState.currentMatch ?: return@LaunchedEffect
        if (!searchState.isActive) return@LaunchedEffect
        scrollToBlockIndex(
            listState = listState,
            blockIndex = match.blockIndex,
            listHeaderCount = listHeaderCount,
            blockCount = state.blocks.size,
        )
    }

    // System back: close search first, then leave the viewer.
    BackHandler(enabled = searchState.isActive) {
        viewModel.closeSearch()
    }

    // Prefer closing the TOC drawer before leaving the screen.
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = hasToc,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(320.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
            ) {
                TocDrawerContent(
                    documentName = title,
                    toc = state.toc,
                    activeAnchor = activeTocItem?.anchor,
                    onItemClick = { item ->
                        scope.launch {
                            drawerState.close()
                            scrollToTocItem(
                                listState = listState,
                                item = item,
                                listHeaderCount = listHeaderCount,
                                blockCount = state.blocks.size,
                            )
                            // Persist jump target immediately so quick back still restores it.
                            viewModel.saveReadingPosition(
                                lastScrollIndex = listState.firstVisibleItemIndex,
                                lastScrollOffset = listState.firstVisibleItemScrollOffset,
                                readingProgress = readingProgressOf(listState),
                            )
                        }
                    },
                )
            }
        },
        modifier = modifier.fillMaxSize(),
    ) {
        ViewerScaffold(
            title = title,
            onBack = onBack,
            tocEnabled = hasToc,
            onOpenToc = {
                if (hasToc) {
                    scope.launch { drawerState.open() }
                }
            },
            settingsEnabled = true,
            onOpenSettings = { showSettings = true },
            searchEnabled = true,
            onOpenSearch = viewModel::openSearch,
            searchState = searchState,
            onSearchQueryChange = viewModel::onSearchQueryChange,
            onCloseSearch = viewModel::closeSearch,
            onSearchPrevious = viewModel::goToPreviousMatch,
            onSearchNext = viewModel::goToNextMatch,
            snackbarHostState = snackbarHostState,
        ) { innerPadding ->
            if (state.blocks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = contentPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (state.isTruncated) {
                            Text(
                                text = stringResource(R.string.viewer_preview_notice),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                        }
                        Text(
                            text = stringResource(R.string.viewer_empty_document),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(
                        start = contentPadding,
                        end = contentPadding,
                        top = 8.dp,
                        bottom = 32.dp,
                    ),
                ) {
                    // Keep a single optional header so listHeaderCount stays 0 or 1.
                    if (state.isTruncated || state.isParseFallback) {
                        item(key = "list_header_notice") {
                            val notice = when {
                                state.isParseFallback ->
                                    stringResource(R.string.viewer_parse_fallback_notice)
                                else ->
                                    stringResource(R.string.viewer_preview_notice)
                            }
                            Text(
                                text = notice,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                        }
                    }
                    itemsIndexed(
                        items = state.blocks,
                        key = { index, block -> "${block::class.simpleName}-$index" },
                    ) { index, block ->
                        val current = searchState.currentMatch
                        val isActiveMatch = searchState.isActive &&
                            current != null &&
                            current.blockIndex == index
                        MarkdownBlockView(
                            block = block,
                            settings = readerSettings,
                            isSearchActive = isActiveMatch,
                            activeMatch = if (isActiveMatch) current else null,
                            onCopyMessage = { message ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(message)
                                }
                            },
                        )
                    }
                }
            }
        }

        if (showSettings) {
            ReaderSettingsSheet(
                settings = readerSettings,
                onFontScale = viewModel::setFontScale,
                onLineHeightScale = viewModel::setLineHeightScale,
                onPaddingLevel = viewModel::setContentPaddingLevel,
                onReset = viewModel::resetReaderSettings,
                onDismiss = { showSettings = false },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewerScaffold(
    title: String,
    onBack: () -> Unit,
    tocEnabled: Boolean,
    onOpenToc: () -> Unit,
    settingsEnabled: Boolean,
    onOpenSettings: () -> Unit,
    searchEnabled: Boolean,
    onOpenSearch: () -> Unit,
    searchState: DocumentSearchUiState?,
    onSearchQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
    onSearchPrevious: () -> Unit,
    onSearchNext: () -> Unit,
    snackbarHostState: SnackbarHostState? = null,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    val inSearch = searchState?.isActive == true
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = {
            if (snackbarHostState != null) {
                SnackbarHost(hostState = snackbarHostState)
            }
        },
        topBar = {
            if (inSearch && searchState != null) {
                SearchTopBar(
                    query = searchState.query,
                    matchLabel = searchState.counterLabel,
                    hasMatches = searchState.matches.isNotEmpty(),
                    onQueryChange = onSearchQueryChange,
                    onClose = onCloseSearch,
                    onPrevious = onSearchPrevious,
                    onNext = onSearchNext,
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = ReaderTypography.topBarTitleSize,
                                fontWeight = FontWeight.Medium,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    },
                    actions = {
                        // Order: search → settings → TOC
                        IconButton(
                            onClick = onOpenSearch,
                            enabled = searchEnabled,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = stringResource(R.string.search_open),
                            )
                        }
                        IconButton(
                            onClick = onOpenSettings,
                            enabled = settingsEnabled,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = stringResource(R.string.reader_settings_open),
                            )
                        }
                        IconButton(
                            onClick = onOpenToc,
                            enabled = tocEnabled,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.List,
                                contentDescription = if (tocEnabled) {
                                    stringResource(R.string.toc_open)
                                } else {
                                    stringResource(R.string.toc_empty)
                                },
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }
        },
        content = content,
    )
}

/**
 * Active TOC item = last entry whose blockIndex is at or before the first visible
 * content block. Defaults to the first TOC item when still above all headings.
 */
@Composable
private fun rememberActiveTocItem(
    toc: List<TocItem>,
    listState: LazyListState,
    listHeaderCount: Int,
): State<TocItem?> {
    return remember(toc, listHeaderCount) {
        derivedStateOf {
            if (toc.isEmpty()) {
                null
            } else {
                val contentIndex =
                    (listState.firstVisibleItemIndex - listHeaderCount).coerceAtLeast(0)
                toc.lastOrNull { it.blockIndex <= contentIndex } ?: toc.first()
            }
        }
    }
}

private data class ReadingPosition(
    val index: Int,
    val offset: Int,
    val progress: Float,
)

/**
 * Clamp saved absolute list index into the current list size.
 * Falls back to 0 when out of range or list is empty.
 */
private fun safeScrollIndex(savedIndex: Int, totalItems: Int): Int {
    if (totalItems <= 0) return 0
    if (savedIndex < 0 || savedIndex >= totalItems) return 0
    return savedIndex
}

/**
 * Progress in 0f..1f from absolute LazyList position.
 * Uses firstVisibleItemIndex / (totalItems - 1) so the last item reaches ~1f.
 */
private fun readingProgressOf(listState: LazyListState): Float {
    val total = listState.layoutInfo.totalItemsCount
        .takeIf { it > 0 }
        ?: return 0f
    if (total == 1) {
        return if (listState.firstVisibleItemScrollOffset > 0) 1f else 0f
    }
    val index = listState.firstVisibleItemIndex.coerceIn(0, total - 1)
    return (index.toFloat() / (total - 1).toFloat()).coerceIn(0f, 1f)
}

private suspend fun scrollToTocItem(
    listState: LazyListState,
    item: TocItem,
    listHeaderCount: Int,
    blockCount: Int,
) {
    scrollToBlockIndex(
        listState = listState,
        blockIndex = item.blockIndex,
        listHeaderCount = listHeaderCount,
        blockCount = blockCount,
    )
}

private suspend fun scrollToBlockIndex(
    listState: LazyListState,
    blockIndex: Int,
    listHeaderCount: Int,
    blockCount: Int,
) {
    if (blockCount <= 0) return
    val target = listHeaderCount + blockIndex
    val maxIndex = listHeaderCount + blockCount - 1
    if (target !in 0..maxIndex) return
    try {
        listState.animateScrollToItem(target)
    } catch (_: Exception) {
        // Ignore races from rapid clicks / disposed list.
    }
}
