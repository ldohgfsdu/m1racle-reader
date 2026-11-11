package com.m1racle.reader.ui.viewer

import com.m1racle.reader.ui.components.readerHairlineBorder
import com.m1racle.reader.ui.theme.M1racleReaderTheme
import android.os.Trace
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.TextButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListPrefetchScope
import androidx.compose.foundation.lazy.LazyListPrefetchStrategy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.layout.NestedPrefetchScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m1racle.reader.BuildConfig
import com.m1racle.reader.R
import com.m1racle.reader.core.model.DocumentAnchor
import com.m1racle.reader.data.settings.ReaderSettings
import com.m1racle.reader.markdown.MarkdownBlock
import com.m1racle.reader.markdown.MarkdownPerformance
import com.m1racle.reader.markdown.TocItem
import com.m1racle.reader.markdown.activeAt
import com.m1racle.reader.markdown.blockContentType
import com.m1racle.reader.markdown.blockId
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.m1racle.reader.ui.material.GlassMaterialRole
import com.m1racle.reader.ui.material.glassMaterial
import com.m1racle.reader.ui.viewer.components.MarkdownBlockView
import com.m1racle.reader.ui.viewer.components.headingTopPadding
import com.m1racle.reader.ui.viewer.components.ReaderSettingsSheet
import com.m1racle.reader.ui.viewer.components.ReaderTypography
import com.m1racle.reader.ui.viewer.components.SearchTopBar
import com.m1racle.reader.ui.viewer.components.TocDrawerContent
import com.m1racle.reader.ui.viewer.components.TableLayoutCache
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    viewModel: ViewerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** R23 书签中介（NavGraph 注入；null = 能力未启用，顶栏不显示书签钮）。 */
    bookmarkController: com.m1racle.reader.ui.reader.ReaderBookmarkController? = null,
    /** P0-1 高亮中介（与 PDF 同一实现；null = 无长按入口与标注钮）。 */
    highlightController: com.m1racle.reader.ui.reader.ReaderHighlightController? = null,
    /** P0-1 批注中介（与 PDF 同一实现；null = 批注编辑不可用）。 */
    noteController: com.m1racle.reader.ui.reader.ReaderNoteController? = null,
    initialAnchor: DocumentAnchor? = null,
    initialBookmark: com.m1racle.reader.data.anchor.BookmarkTarget? = null,
    repository: com.m1racle.reader.data.repository.DocumentRepository? = null,
    startEditing: Boolean = false,
    onEditingChanged: (Boolean) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Prefer document.title (H1); fall back to displayName / index.md.
    val title = when (val state = uiState) {
        is ViewerUiState.Success ->
            state.document.title
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: state.document.displayName
                    .trim()
                    .takeIf {
                        it.isNotEmpty() &&
                            it != "未命名文档"
                    }
                ?: "index.md"

        is ViewerUiState.Error ->
            state.displayName ?: stringResource(R.string.viewer_title_fallback)

        ViewerUiState.Loading ->
            stringResource(R.string.viewer_loading)
    }

    when (val state = uiState) {
        is ViewerUiState.Success -> {
            com.m1racle.reader.ui.components.ReaderFontContent { ViewerSuccessScreen(
                state = state,
                title = title,
                repository = repository,
                startEditing = startEditing,
                onEditingChanged = onEditingChanged,
                viewModel = viewModel,
                onBack = onBack,
                bookmarkController = bookmarkController,
                highlightController = highlightController,
                noteController = noteController,
                initialAnchor = initialAnchor,
                initialBookmark = initialBookmark,
                modifier = modifier,
            ) }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ViewerSuccessScreen(
    state: ViewerUiState.Success,
    title: String,
    viewModel: ViewerViewModel,
    onBack: () -> Unit,
    bookmarkController: com.m1racle.reader.ui.reader.ReaderBookmarkController?,
    highlightController: com.m1racle.reader.ui.reader.ReaderHighlightController?,
    noteController: com.m1racle.reader.ui.reader.ReaderNoteController?,
    initialAnchor: DocumentAnchor?,
    initialBookmark: com.m1racle.reader.data.anchor.BookmarkTarget?,
    modifier: Modifier = Modifier,
    repository: com.m1racle.reader.data.repository.DocumentRepository?,
    startEditing: Boolean,
    onEditingChanged: (Boolean) -> Unit,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val markdownSelectionSession = remember(state.document.id) {
        com.m1racle.reader.ui.viewer.components.MarkdownSelectionSession()
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val hasToc = state.toc.isNotEmpty()
    val readerSettings by viewModel.readerSettings.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val density = LocalDensity.current
    val toolbarHeight = ReaderTypography.topBarHeight
    val tableLayoutCache = remember(state.document.id) {
        TableLayoutCache(documentId = state.document.id)
    }
    // Keyed by document id so a new document never inherits a hidden bar / accumulators.
    // maxHiddenOffset is updated from live status-bar + chrome metrics (rotation / inset).
    val topBarScrollState = remember(state.document.id, density) {
        ReaderTopBarScrollState(
            maxHiddenOffsetPx = with(density) {
                // Placeholder until first composition measures status bars; updated below.
                toolbarHeight.toPx()
            },
            // 8dp reverse noise: still debounces micro-jitter, closer to X re-show latency
            // than the previous 12dp gate (which felt late after full hide).
            jitterIgnorePx = with(density) { 8.dp.toPx() },
        )
    }
    val statusBarTopPx = with(density) {
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding().toPx()
    }
    val topChromeMaxPx = with(density) {
        statusBarTopPx + toolbarHeight.toPx()
    }
    // Keep max clamp in sync with inset / density without recreating the state object.
    SideEffect {
        topBarScrollState.updateMaxHiddenOffsetPx(topChromeMaxPx)
    }
    // Observe-only nested scroll: continuous offset + reverse jitter; never consumes body deltas.
    val topBarNestedScroll = remember(topBarScrollState) {
        topBarScrollState.asNestedScrollConnection()
    }
    /*
     * Index coordinate system (MUST stay consistent):
     * - Room lastScrollIndex / LazyListState use ABSOLUTE list item indices
     * - Optional parser-fallback header occupies index 0 when isParseFallback
     * - Markdown blocks start at listHeaderCount
     * - TOC jump target = listHeaderCount + TocItem.blockIndex
     * - Active TOC uses (firstVisibleItemIndex - listHeaderCount) as content index
     */
    val listHeaderCount = if (state.isParseFallback) 1 else 0
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
    // QA-only: swap in a no-op prefetch strategy when isolating first-entry item work.
    val prefetchStrategy = remember(ReaderPerformanceExperiments.disablePrefetch) {
        if (ReaderPerformanceExperiments.disablePrefetch) {
            DisabledLazyListPrefetchStrategy
        } else {
            LazyListPrefetchStrategy()
        }
    }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = restoredIndex,
        initialFirstVisibleItemScrollOffset = restoredOffset,
        prefetchStrategy = prefetchStrategy,
    )
    var editing by androidx.compose.runtime.saveable.rememberSaveable(state.document.id) { mutableStateOf(startEditing && repository != null) }
    var editLine by androidx.compose.runtime.saveable.rememberSaveable(state.document.id) { mutableStateOf(state.blockStartLines.getOrElse((restoredIndex - listHeaderCount).coerceAtLeast(0)) { 1 }) }
    var editSession by androidx.compose.runtime.saveable.rememberSaveable(state.document.id) { mutableStateOf(0) }
    SideEffect { onEditingChanged(editing) }
    fun beginEditing() {
        if (repository == null || state.document.type != com.m1racle.reader.data.local.DocumentType.MARKDOWN) return
        val index = (listState.firstVisibleItemIndex - listHeaderCount).coerceAtLeast(0)
        editLine = state.blockStartLines.getOrElse(index) { 1 }
        markdownSelectionSession.clear()
        viewModel.closeSearch()
        topBarScrollState.forceShow()
        editSession++
        editing = true
    }
    // Compose exposes the system ScrollCapture session; temporary capture scrolling must not
    // become the user's saved reading position.
    val systemCaptureInProgress by rememberUpdatedState(androidx.compose.ui.platform.LocalScrollCaptureInProgress.current)
    var captureOriginalPosition by remember(state.document.id) { mutableStateOf<Triple<Int, Int, Float>?>(null) }
    SideEffect {
        if (systemCaptureInProgress && captureOriginalPosition == null) {
            captureOriginalPosition = Triple(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, readingProgressOf(listState))
        } else if (!systemCaptureInProgress) captureOriginalPosition = null
    }

    // Search navigation supplies the same canonical anchor used by annotations.
    // The existing block jump helper remains the sole Markdown positioning path.
    LaunchedEffect(initialBookmark, state.document.id) {
        val saved = initialBookmark ?: return@LaunchedEffect
        val block = saved.anchor as? DocumentAnchor.MarkdownBlock ?: return@LaunchedEffect
        listState.scrollToItem((block.blockIndex + listHeaderCount).coerceIn(0, (state.blocks.size + listHeaderCount - 1).coerceAtLeast(0)), saved.scrollOffset)
    }
    LaunchedEffect(initialAnchor, state.document.id) {
        val target = initialAnchor as? DocumentAnchor.MarkdownBlock ?: return@LaunchedEffect
        scrollToBlockIndex(
            listState = listState,
            blockIndex = target.blockIndex,
            listHeaderCount = listHeaderCount,
            blockCount = state.blocks.size,
        )
    }

    // ── R23 Reader Bookmark（SCROLL_POSITION 坐标系；位置=绝对列表索引） ──
    var bookmarkedIndexes by remember(state.document.id) {
        mutableStateOf<Set<Int>>(emptySet())
    }
    LaunchedEffect(bookmarkController, state.document.id) {
        bookmarkedIndexes = bookmarkController?.bookmarkedLocations() ?: emptySet()
    }
    fun toggleBookmarkHere() {
        val controller = bookmarkController ?: return
        val index = listState.firstVisibleItemIndex
        scope.launch {
            when (controller.toggle(index, offset = listState.firstVisibleItemScrollOffset.toLong(),
                target = com.m1racle.reader.data.anchor.BookmarkTarget(
                    DocumentAnchor.MarkdownBlock((index - listHeaderCount).coerceAtLeast(0)),
                    listState.firstVisibleItemScrollOffset,
                ))) {
                com.m1racle.reader.ui.reader.ReaderBookmarkController.ToggleResult.Added -> {
                    bookmarkedIndexes = bookmarkedIndexes + index
                    snackbarHostState.showSnackbar(
                        resources.getString(R.string.reader_bookmark_added),
                    )
                }
                com.m1racle.reader.ui.reader.ReaderBookmarkController.ToggleResult.Removed -> {
                    bookmarkedIndexes = bookmarkedIndexes - index
                    snackbarHostState.showSnackbar(
                        resources.getString(R.string.reader_bookmark_removed),
                    )
                }
            }
        }
    }

    // ── P0-1 Markdown 标注（SCROLL_POSITION 坐标系；location = 内容块索引，
    // 与书签的绝对列表索引不同，不可混用） ──
    var annotationVersion by remember(state.document.id) { mutableStateOf(0) }
    var pendingTextSelection by remember(state.document.id) { mutableStateOf<com.m1racle.reader.ui.annotation.TextSelection.MarkdownSelection?>(null) }
    var annotatedBlocks by remember(state.document.id) { mutableStateOf<Set<Int>>(emptySet()) }
    var showAnnotationSheet by remember(state.document.id) { mutableStateOf(false) }
    var noteTarget by remember(state.document.id) { mutableStateOf<MarkdownNoteTarget?>(null) }
    var managementHighlights by remember(state.document.id) {
        mutableStateOf<List<com.m1racle.reader.data.local.HighlightEntity>>(emptyList())
    }
    var managementNotes by remember(state.document.id) {
        mutableStateOf<List<com.m1racle.reader.data.local.NoteEntity>>(emptyList())
    }
    LaunchedEffect(highlightController, noteController, state.document.id, annotationVersion) {
        annotatedBlocks = highlightController?.listForDocument()
            ?.filter { it.locationKind == "SCROLL_POSITION" }
            ?.map { it.location }
            ?.toSet() ?: emptySet()
        managementHighlights = highlightController?.listForDocument() ?: emptyList()
        managementNotes = noteController?.list() ?: emptyList()
    }

    /** 打开指定块的批注编辑器：优先关联既有高亮的锚点，否则用整块锚点新建。 */
    fun openNoteEditorForBlock(blockIndex: Int, returnToManagement: Boolean = false) {
        val highlights = highlightController ?: return
        if (noteController == null) return
        val block = state.blocks.getOrNull(blockIndex) ?: return
        val plain = block.plainTextForAnnotation()
        if (plain.isEmpty()) return
        scope.launch {
            val existingNote = managementNotes.firstOrNull { it.locationKind == "SCROLL_POSITION" && it.location == blockIndex }
            if (existingNote != null) {
                noteController.anchorOf(existingNote)?.let { anchor -> noteTarget = MarkdownNoteTarget(anchor, existingNote, returnToManagement) }
                return@launch
            }
            val highlight = highlights.listForDocument().firstOrNull {
                it.locationKind == "SCROLL_POSITION" && it.location == blockIndex
            }
            val anchor = highlight?.let { highlights.anchorOf(it) }
                ?: com.m1racle.reader.core.model.DocumentAnchor.MarkdownBlock(
                    blockIndex = blockIndex,
                    charStart = 0,
                    charEnd = plain.length,
                )
            val linked = noteController.linkedAt(anchor)
            noteTarget = MarkdownNoteTarget(
                anchor = anchor,
                note = linked,
                returnToManagement = returnToManagement,
            )
        }
    }

    fun openNoteEditorForSelection(
        selection: com.m1racle.reader.ui.annotation.TextSelection.MarkdownSelection,
    ) {
        val notes = noteController ?: return
        scope.launch {
            selection.toAnchor()?.let { anchor ->
                noteTarget = MarkdownNoteTarget(
                    anchor = anchor,
                    note = notes.linkedAt(anchor),
                    returnToManagement = false,
                )
            }
        }
    }

    /**
     * A body tap owns the chrome toggle. Reading scroll continues to drive the same
     * state through the existing nested-scroll path.
     */
    fun toggleChromeFromBodyTap() {
        if (markdownSelectionSession.isActive) {
            markdownSelectionSession.clear()
            return
        }
        if (
            editing ||
            searchState.isActive ||
            showSettings ||
            showAnnotationSheet ||
            drawerState.isOpen
        ) {
            return
        }
        topBarScrollState.snapTo(
            nextTopBarOffsetForBodyTap(
                collapseFraction = topBarScrollState.collapseFraction,
                maxHiddenOffsetPx = topBarScrollState.maxHiddenOffsetPx,
            ),
        )
    }

    pendingTextSelection?.let { selection ->
        com.m1racle.reader.ui.annotation.SelectionActionsDialog(selection,
            onDismiss = { pendingTextSelection = null },
            onHighlight = { style ->
                pendingTextSelection = null
                scope.launch {
                    when (highlightController?.commitHighlight(selection, style = style)) {
                        is com.m1racle.reader.ui.reader.ReaderHighlightController.CommitResult.Success -> annotationVersion++
                        else -> snackbarHostState.showSnackbar(resources.getString(R.string.reader_highlight_failed))
                    }
                }
            },
            onNote = if (noteController != null) ({
                pendingTextSelection = null
                openNoteEditorForSelection(selection)
            }) else null,
        )
    }

    // Persist only from a settled list, plus leave/background flushes. Observing index/offset while
    // a 120 Hz gesture is active used to allocate and dispatch one ReadingScrollSnapshot for every
    // pixel-level position change even though the collector immediately discarded it.
    LaunchedEffect(listState, state.document.id, totalListItems) {
        if (totalListItems <= 0) return@LaunchedEffect
        var lastPersistedIndex = Int.MIN_VALUE
        var lastPersistedOffset = Int.MIN_VALUE

        snapshotFlow { listState.isScrollInProgress || systemCaptureInProgress }
            .distinctUntilChanged()
            .collect { isScrolling ->
                if (isScrolling || listState.layoutInfo.totalItemsCount <= 0) return@collect
                val index = listState.firstVisibleItemIndex
                val offset = listState.firstVisibleItemScrollOffset
                if (index == lastPersistedIndex && offset == lastPersistedOffset) return@collect

                lastPersistedIndex = index
                lastPersistedOffset = offset
                viewModel.saveReadingPosition(
                    lastScrollIndex = index,
                    lastScrollOffset = offset,
                    readingProgress = readingProgressOf(listState),
                )
            }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val latestTotalListItems by rememberUpdatedState(totalListItems)
    fun flushPosition() {
        val original = captureOriginalPosition
        if (original != null) viewModel.saveReadingPosition(original.first, original.second, original.third)
        else flushReadingPosition(listState, latestTotalListItems, viewModel)
    }
    // Flush on background (ON_STOP) and when leaving composition.
    DisposableEffect(state.document.id, listState, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                flushPosition()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            flushPosition()
            viewModel.closeSearch()
        }
    }

    // Scroll to current search match (absolute list index = header + blockIndex).
    // P0: block-level only — not character-precise. Ignore stale keys / empty results.
    LaunchedEffect(
        searchState.currentIndex,
        searchState.matches,
        searchState.isActive,
        searchState.documentKey,
        state.document.id,
    ) {
        if (!searchState.isActive) return@LaunchedEffect
        if (searchState.documentKey != state.document.id) return@LaunchedEffect
        if (searchState.matches.isEmpty()) return@LaunchedEffect
        val match = searchState.currentMatch ?: return@LaunchedEffect
        if (match.documentKey != state.document.id) return@LaunchedEffect
        if (match.blockIndex !in state.blocks.indices) return@LaunchedEffect
        scrollToBlockIndex(
            listState = listState,
            blockIndex = match.blockIndex,
            listHeaderCount = listHeaderCount,
            blockCount = state.blocks.size,
        )
    }

    // Force chrome fully shown while searching, when the list *settles* at top, and settle
    // collapse after finger-up / fling (fraction or fling velocity). Body never reads offset.
    // collectLatest cancels an in-flight settle as soon as the user starts another gesture.
    LaunchedEffect(listState, searchState.isActive, showSettings, showAnnotationSheet, drawerState.isOpen, topBarScrollState) {
        if (searchState.isActive || showSettings || showAnnotationSheet || drawerState.isOpen) {
            topBarScrollState.forceShow()
            return@LaunchedEffect
        }
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collectLatest { scrolling ->
                if (scrolling) return@collectLatest
                topBarScrollState.onScrollIdle()
                // Wait one frame so onPostFling can record velocity before settle.
                withFrameNanos { }
                if (listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset <= 0
                ) {
                    topBarScrollState.forceShow()
                    return@collectLatest
                }
                // Low-elasticity settle to fully shown / fully hidden after the gesture ends.
                val velocityY = topBarScrollState.consumePendingFlingVelocity()
                val target = topBarScrollState.resolveSettleTarget(velocityY)
                val from = topBarScrollState.hiddenOffsetPx
                if (kotlin.math.abs(target - from) < 0.5f) {
                    topBarScrollState.snapTo(target)
                    return@collectLatest
                }
                val anim = Animatable(from)
                anim.animateTo(
                    targetValue = target,
                    animationSpec = spring(
                        // Critically damped: no overshoot/bounce; stiffer than 600 so
                        // settle finishes closer to X (less trailing drag after finger-up).
                        dampingRatio = 1f,
                        stiffness = 900f,
                    ),
                ) {
                    topBarScrollState.snapTo(value)
                }
            }
    }

    // QA-only: attribute first-entry block compose cost after the first *user* scroll settles.
    // Production builds never enable READER_PERFORMANCE_EXPERIMENTS_ENABLED.
    LaunchedEffect(listState, state.document.id) {
        if (!BuildConfig.READER_PERFORMANCE_EXPERIMENTS_ENABLED) return@LaunchedEffect
        MarkdownPerformance.reset()
        var sawScroll = false
        var dumped = false
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (scrolling) {
                    sawScroll = true
                    return@collect
                }
                if (!sawScroll || dumped) return@collect
                dumped = true
                MarkdownPerformance.dumpBlockCompose("MarkdownBlockPerf")
            }
    }

    // System back: close search first, then leave the viewer.
    BackHandler(enabled = searchState.isActive) {
        viewModel.closeSearch()
    }

    // Prefer closing the TOC drawer before leaving the screen.
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    BackHandler(enabled = markdownSelectionSession.isActive) {
        markdownSelectionSession.clear()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = hasToc && !editing,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(320.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                // Active TOC is derived inside the drawer — body does not subscribe.
                TocDrawerContent(
                    documentName = title,
                    toc = state.toc,
                    listState = listState,
                    listHeaderCount = listHeaderCount,
                    onItemClick = { item ->
                        scope.launch {
                            topBarScrollState.forceShow()
                            drawerState.close()
                            scrollToTocItem(
                                listState = listState,
                                item = item,
                                anchorRegistry = state.anchorRegistry,
                                listHeaderCount = listHeaderCount,
                                blockCount = state.blocks.size,
                                scrollOffset = with(density) {
                                    headingTopPadding(item.level).roundToPx()
                                },
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
        Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().then(if (editing) Modifier.graphicsLayer { alpha = 0f }.clearAndSetSemantics { } else Modifier)) {
        CompositionLocalProvider(
            com.m1racle.reader.ui.viewer.components.LocalMarkdownSelectionSession provides
                markdownSelectionSession,
            com.m1racle.reader.ui.documents.LocalDocumentEditor provides
            (if (repository != null && state.document.type == com.m1racle.reader.data.local.DocumentType.MARKDOWN) ({ _: String -> beginEditing() }) else null),
            com.m1racle.reader.ui.reader.LocalReaderProgress provides remember(listState, scope) {
            com.m1racle.reader.ui.reader.ReaderProgress(
                position = { readingProgressOf(listState) },
                seek = { fraction -> scope.launch {
                    val last = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                    listState.scrollToItem((last * fraction).toInt().coerceIn(0, last))
                    topBarScrollState.forceShow()
                } },
            )
        }) {
        ViewerScaffold(
            title = title,
            onBack = onBack,
            tocEnabled = hasToc,
            onOpenToc = {
                if (hasToc) {
                    markdownSelectionSession.clear()
                    scope.launch { drawerState.open() }
                }
            },
            settingsEnabled = true,
            onOpenSettings = {
                markdownSelectionSession.clear()
                showSettings = true
            },
            searchEnabled = true,
            onOpenSearch = {
                markdownSelectionSession.clear()
                topBarScrollState.forceShow()
                viewModel.openSearch()
            },
            searchState = searchState,
            onSearchQueryChange = viewModel::onSearchQueryChange,
            onCloseSearch = viewModel::closeSearch,
            onSearchPrevious = viewModel::goToPreviousMatch,
            onSearchNext = viewModel::goToNextMatch,
            onToggleBookmark = if (bookmarkController != null) {
                { toggleBookmarkHere() }
            } else {
                null
            },
            onOpenAnnotations = if (highlightController != null) {
                {
                    markdownSelectionSession.clear()
                    showAnnotationSheet = true
                }
            } else {
                null
            },
            bookmarkActive = listState.firstVisibleItemIndex in bookmarkedIndexes,
            snackbarHostState = snackbarHostState,
            // Pass state object only — body never reads .visible (avoids recompose cascade).
            // QA: nulling the state freezes the overlay at visible and drops hide/show flips.
            topBarScrollState = topBarScrollState.takeUnless {
                ReaderPerformanceExperiments.disableTopBar
            },
            // Search owns the scaffold topBar; disable scroll-hide while searching.
            // QA: also drop nestedScroll when isolating top-bar flip cost.
            nestedScrollConnection = if (
                searchState.isActive || showSettings || showAnnotationSheet || drawerState.isOpen || ReaderPerformanceExperiments.disableTopBar
            ) {
                null
            } else {
                topBarNestedScroll
            },
        ) { innerPadding ->
            ReaderDocumentBody(
                state = state,
                listState = listState,
                readerSettings = readerSettings,
                searchState = searchState,
                snackbarHostState = snackbarHostState,
                listHeaderCount = listHeaderCount,
                innerPadding = innerPadding,
                tableLayoutCache = tableLayoutCache,
                onAnchorJump = {
                    markdownSelectionSession.clear()
                    topBarScrollState.forceShow()
                },
                annotatedBlocks = annotatedBlocks,
                highlights = managementHighlights,
                noteBlocks = managementNotes.filter { it.locationKind == "SCROLL_POSITION" }.map { it.location }.toSet(),
                onNoteTap = { openNoteEditorForBlock(it) },
                onHighlightRequest = if (highlightController != null) ({
                    pendingTextSelection = it
                }) else null,
                onTextNote = if (noteController != null) ::openNoteEditorForSelection else null,
                onBodyTap = if (
                    editing ||
                    searchState.isActive ||
                    showSettings ||
                    showAnnotationSheet ||
                    drawerState.isOpen
                ) {
                    null
                } else {
                    ::toggleChromeFromBodyTap
                },
            )
        }

        }
        }
        if (editing && repository != null) androidx.compose.runtime.key(editSession) {
            com.m1racle.reader.ui.documents.MarkdownInlineEditor(
                id = state.document.id, title = title, initialLine = editLine, repository = repository,
                onBack = { editing = false; topBarScrollState.forceShow() },
                onSaved = viewModel::refreshAfterEdit,
            )
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

        if (showAnnotationSheet) {
            com.m1racle.reader.ui.annotation.AnnotationsManagementSheet(
                highlights = managementHighlights,
                notes = managementNotes,
                onDismiss = { showAnnotationSheet = false },
                onJump = { highlight ->
                    showAnnotationSheet = false
                    scope.launch {
                        scrollToBlockIndex(
                            listState = listState,
                            blockIndex = highlight.location,
                            listHeaderCount = listHeaderCount,
                            blockCount = state.blocks.size,
                        )
                    }
                },
                onDeleteHighlight = { highlight ->
                    scope.launch {
                        highlightController?.deleteHighlight(highlight.id)
                        annotationVersion++
                        snackbarHostState.showSnackbar(
                            resources.getString(R.string.reader_annotation_deleted),
                        )
                    }
                },
                onAddNoteForHighlight = { highlight ->
                    scope.launch {
                        val target = highlightController?.anchorOf(highlight)
                        if (target != null) {
                            noteTarget = MarkdownNoteTarget(
                                anchor = target,
                                note = null,
                                returnToManagement = true,
                            )
                        }
                    }
                },
                onEditNote = { note ->
                    scope.launch {
                        val target = noteController?.anchorOf(note)
                        if (target != null) {
                            noteTarget = MarkdownNoteTarget(
                                anchor = target,
                                note = note,
                                returnToManagement = true,
                            )
                        }
                    }
                },
                onDeleteNote = { note ->
                    scope.launch {
                        noteController?.delete(note.id)
                        annotationVersion++
                        snackbarHostState.showSnackbar(
                            resources.getString(R.string.reader_note_deleted),
                        )
                    }
                },
            )
        }

        noteTarget?.let { target ->
            val notes = noteController
            if (notes != null) {
                val blockLine = stringResource(
                    R.string.reader_note_editor_title,
                    resources.getString(
                        R.string.reader_note_block_line,
                        (target.anchor as? DocumentAnchor.MarkdownBlock)?.blockIndex?.plus(1) ?: 1,
                    ),
                )
                val closeEditor: () -> Unit = {
                    noteTarget = null
                    if (target.returnToManagement) showAnnotationSheet = true
                }
                com.m1racle.reader.ui.annotation.NoteEditDialog(
                    title = blockLine,
                    initialContent = target.note?.content.orEmpty(),
                    isEditing = target.note != null,
                    onSave = { content ->
                        scope.launch {
                            if (target.note == null) {
                                notes.create(target.anchor, content)
                            } else {
                                notes.update(target.note.id, content)
                            }
                            annotationVersion++
                            closeEditor()
                            snackbarHostState.showSnackbar(
                                resources.getString(R.string.reader_note_saved),
                            )
                        }
                    },
                    onDelete = target.note?.let { note ->
                        {
                            scope.launch {
                                notes.delete(note.id)
                                annotationVersion++
                                closeEditor()
                                snackbarHostState.showSnackbar(
                                    resources.getString(R.string.reader_note_deleted),
                                )
                            }
                        }
                    },
                    onDismiss = closeEditor,
                )
            }
        }
    }
}

/**
 * Document body composition boundary: does not read TopBar visibility / offset.
 * Each Markdown block is its own LazyColumn item with stable key + contentType.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ReaderDocumentBody(
    state: ViewerUiState.Success,
    listState: LazyListState,
    readerSettings: ReaderSettings,
    searchState: DocumentSearchUiState,
    snackbarHostState: SnackbarHostState,
    listHeaderCount: Int,
    innerPadding: PaddingValues,
    tableLayoutCache: TableLayoutCache,
    onAnchorJump: () -> Unit,
    /** 已高亮的内容块索引（SCROLL_POSITION）；空集 = 零绘制路径。 */
    annotatedBlocks: Set<Int> = emptySet(),
    highlights: List<com.m1racle.reader.data.local.HighlightEntity> = emptyList(),
    noteBlocks: Set<Int> = emptySet(),
    onNoteTap: (Int) -> Unit = {},
    onHighlightRequest: ((com.m1racle.reader.ui.annotation.TextSelection.MarkdownSelection) -> Unit)? = null,
    onTextNote: ((com.m1racle.reader.ui.annotation.TextSelection.MarkdownSelection) -> Unit)? = null,
    onBodyTap: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val toolbarHeight = ReaderTypography.topBarHeight
    val uriHandler = LocalUriHandler.current
    val contentPadding = readerSettings.contentPaddingLevel.horizontalDp
    // Initial top chrome reservation lives in LazyColumn contentPadding only — never as a
    // fixed outer Box padding — so fully-hidden chrome leaves zero permanent top blank.
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val listTopInset = statusBarTop + toolbarHeight + ReaderTypography.contentTopGap
    val navBottom = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    val listBottomInset = navBottom + 24.dp

    // Group matches once when the result set changes — not on every next/prev.
    // Search-closed: empty map + no active key → items get null highlights (zero paint path).
    val searchPaintActive = searchState.isActive && searchState.matches.isNotEmpty()
    val matchesByBlock = remember(searchPaintActive, searchState.documentKey, searchState.matches) {
        if (!searchPaintActive) {
            emptyMap()
        } else {
            com.m1racle.reader.search.groupMatchesByBlockIndex(searchState.matches)
        }
    }
    val activeStableKey = if (searchPaintActive) {
        searchState.currentMatch?.stableKey
    } else {
        null
    }
    val activeMatch = if (searchPaintActive) searchState.currentMatch else null
    val onLinkClick: (String) -> Unit = remember(
        state.anchorRegistry,
        listState,
        listHeaderCount,
        state.blocks.size,
        readerSettings.fontScale,
        onAnchorJump,
        density,
        uriHandler,
    ) {
        { destination: String ->
            when (
                val action = com.m1racle.reader.markdown.MarkdownLinkRouter.classify(destination)
            ) {
                is com.m1racle.reader.markdown.MarkdownLinkRouter.Action.InPageAnchor -> {
                    val anchorId = action.anchorId
                    val target = state.anchorRegistry.resolve(anchorId)
                    if (target == null) {
                        Log.w(
                            TOC_LOG_TAG,
                            "anchor_miss documentId=${state.document.id} anchor=$anchorId",
                        )
                    } else {
                        onAnchorJump()
                        val targetHeadingLevel = (state.blocks.getOrNull(target.blockIndex)
                            as? com.m1racle.reader.markdown.MarkdownBlock.Heading)?.level ?: 1
                        scope.launch {
                            scrollToBlockIndex(
                                listState = listState,
                                blockIndex = target.blockIndex,
                                listHeaderCount = listHeaderCount,
                                blockCount = state.blocks.size,
                                scrollOffset = with(density) {
                                    headingTopPadding(targetHeadingLevel).roundToPx()
                                },
                            )
                        }
                    }
                }
                is com.m1racle.reader.markdown.MarkdownLinkRouter.Action.External -> {
                    val uri = action.uri
                    if (uri.isNotBlank()) {
                        try {
                            uriHandler.openUri(uri)
                        } catch (error: Exception) {
                            Log.w(
                                TOC_LOG_TAG,
                                "external_link_open_failed destination=$uri",
                                error,
                            )
                        }
                    }
                }
            }
            Unit
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .passiveReaderBodyShortTap(onBodyTap)
            .padding(
                start = innerPadding.calculateStartPadding(
                    androidx.compose.ui.platform.LocalLayoutDirection.current,
                ),
                top = innerPadding.calculateTopPadding(),
                end = innerPadding.calculateEndPadding(
                    androidx.compose.ui.platform.LocalLayoutDirection.current,
                ),
                bottom = 0.dp,
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        if (state.blocks.isEmpty()) {
            Box(
                modifier = Modifier
                    .widthIn(max = ReaderTypography.contentMaxWidthDp.dp)
                    .fillMaxWidth()
                    .padding(horizontal = contentPadding)
                    .padding(top = listTopInset, bottom = listBottomInset),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                    .widthIn(max = ReaderTypography.contentMaxWidthDp.dp)
                    .fillMaxWidth()
                    .fillMaxSize(),
                contentPadding = PaddingValues(
                    start = contentPadding,
                    end = contentPadding,
                    top = listTopInset,
                    bottom = listBottomInset,
                ),
            ) {
                if (state.isParseFallback) {
                    item(
                        key = "list_header_notice",
                        contentType = "notice",
                    ) {
                        Text(
                            text = stringResource(R.string.viewer_parse_fallback_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                }
                itemsIndexed(
                    items = state.blocks,
                    key = { index, block ->
                        "${state.document.id}:${state.blockIds.getOrElse(index) { block.blockId(index) }}"
                    },
                    contentType = { _, block -> block.blockContentType() },
                ) { index, block ->
                    val stableBlockId = state.blockIds.getOrElse(index) { block.blockId(index) }
                    // Only this block's matches; active key is null when this block is inactive
                    // so inactive blocks keep equal props across next/prev on other blocks.
                    val blockMatches = matchesByBlock[index]
                    // Pass active key only for the active block so inactive hit-blocks
                    // keep stable props when active moves between other blocks.
                    val activeKeyForBlock =
                        if (blockMatches != null && activeMatch?.blockIndex == index) {
                            activeStableKey
                        } else {
                            null
                        }
                    // Memoize so parent recompose (e.g. next/prev elsewhere) does not
                    // allocate a new BlockSearchHighlights for every visible hit block.
                    val searchHighlights = remember(blockMatches, activeKeyForBlock) {
                        com.m1racle.reader.search.blockHighlightsFromGrouped(
                            blockIndex = index,
                            blockMatches = blockMatches,
                            activeStableKey = activeKeyForBlock,
                        )
                    }
                    val selectionContext = remember(
                        index,
                        block,
                        highlights,
                        onHighlightRequest,
                        onTextNote,
                    ) {
                        com.m1racle.reader.ui.viewer.components.MarkdownSelectionContext(
                            block = index,
                            plainText = block.plainTextForAnnotation(),
                            highlights = highlights.filter {
                                it.locationKind == "SCROLL_POSITION" && it.location == index
                            },
                            onHighlight = onHighlightRequest,
                            onNote = onTextNote,
                        )
                    }
                    CompositionLocalProvider(com.m1racle.reader.ui.viewer.components.LocalMarkdownSelection provides selectionContext) {
                    MarkdownBlockView(
                        documentUri = com.m1racle.reader.markdown.MarkdownAssetResolver.imageBaseUriFor(state.document),
                        block = block,
                        settings = readerSettings,
                        searchHighlights = searchHighlights,
                        stableBlockId = stableBlockId,
                        tableLayoutCache = tableLayoutCache,
                        onLinkClick = onLinkClick,
                        onCopyMessage = { message ->
                            scope.launch {
                                snackbarHostState.showSnackbar(message)
                            }
                        },
                        // QA: strip block semantics tree to measure a11y update cost in isolation.
                        modifier = (if (ReaderPerformanceExperiments.clearBlockSemantics) {
                            Modifier.clearAndSetSemantics { }
                        } else {
                            Modifier
                        })
                            // Text blocks paint persisted character ranges in their inline text.
                            // Images retain a block-level tint because they have no text spans.
                            .then(
                                if (annotatedBlocks.contains(index) && (block is MarkdownBlock.Image)) {
                                    Modifier.background(
                                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                    )
                    if (index in noteBlocks) TextButton(onClick = { onNoteTap(index) }) { Text("查看笔记") }
                    }
                }
            }
        }
    }
}

private fun flushReadingPosition(
    listState: LazyListState,
    totalListItems: Int,
    viewModel: ViewerViewModel,
) {
    if (totalListItems > 0 && listState.layoutInfo.totalItemsCount > 0) {
        viewModel.saveReadingPosition(
            lastScrollIndex = listState.firstVisibleItemIndex,
            lastScrollOffset = listState.firstVisibleItemScrollOffset,
            readingProgress = readingProgressOf(listState),
        )
    }
}

/**
 * QA diagnostic: do not schedule any lazy-list prefetch work.
 * Production always uses the default [LazyListPrefetchStrategy].
 */
@OptIn(ExperimentalFoundationApi::class)
private object DisabledLazyListPrefetchStrategy : LazyListPrefetchStrategy {
    override fun LazyListPrefetchScope.onScroll(
        delta: Float,
        layoutInfo: LazyListLayoutInfo,
    ) = Unit

    override fun LazyListPrefetchScope.onVisibleItemsUpdated(
        layoutInfo: LazyListLayoutInfo,
    ) = Unit

    override fun NestedPrefetchScope.onNestedPrefetch(firstVisibleItemIndex: Int) = Unit
}

private val ReaderTopBarIconButton = 48.dp
private val ReaderTopBarIcon = 24.dp
/** Gap after back button so icon-edge → title ≈ 12dp (12dp internal + 4dp). */
private val ReaderTopBarTitleStartGap = 4.dp

/**
 * Reader top bar: back/title/section, search, TOC, and a menu for secondary actions.
 * Title click shows a lightweight popover with the same title string fully expanded.
 *
 * Visibility is observed via [topBarScrollState] only for dismissing the popup —
 * translation is applied by the parent overlay graphicsLayer.
 */
@Composable
private fun ReaderTopBarRow(
    title: String,
    topBarScrollState: ReaderTopBarScrollState?,
    onBack: () -> Unit,
    searchEnabled: Boolean,
    onOpenSearch: () -> Unit,
    settingsEnabled: Boolean,
    onOpenSettings: () -> Unit,
    tocEnabled: Boolean,
    onOpenToc: () -> Unit,
    onToggleBookmark: (() -> Unit)? = null,
    bookmarkActive: Boolean = false,
    onOpenAnnotations: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // Full-contrast chrome icons (not muted gray) for dark/light consistency.
    val iconTint = MaterialTheme.colorScheme.onSurface
    val titleColor = MaterialTheme.colorScheme.onSurface
    val titleSize = ReaderTypography.topBarTitleSize
    val titlePopupCd = stringResource(R.string.viewer_title_popup_cd)

    var showTitlePopup by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    val documentEditor = com.m1racle.reader.ui.documents.LocalDocumentEditor.current
    val editingDocumentId = com.m1racle.reader.ui.documents.LocalReaderDocumentId.current
    var showFileInformation by remember { mutableStateOf(false) }
    val readingProgress = com.m1racle.reader.ui.reader.LocalReaderProgress.current
    var showProgress by remember { mutableStateOf(false) }
    if (showProgress && readingProgress != null) com.m1racle.reader.ui.reader.ReaderProgressDialog(readingProgress) { showProgress = false }
    if (showFileInformation && editingDocumentId != null) {
        com.m1racle.reader.ui.documents.ReaderDocumentDialog(editingDocumentId) { showFileInformation = false }
    }
    DisposableEffect(topBarScrollState, showTitlePopup, showMore) {
        topBarScrollState?.setInteractionPinned(showTitlePopup || showMore)
        onDispose { topBarScrollState?.setInteractionPinned(false) }
    }
    val density = LocalDensity.current
    val toolbarHeight = ReaderTypography.topBarHeight
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val popupMaxWidth = minOf(300.dp, (screenWidthDp - 32.dp).coerceAtLeast(160.dp))

    // Dismiss popup once chrome starts collapsing (without body reading offset).
    if (topBarScrollState != null) {
        LaunchedEffect(topBarScrollState) {
            snapshotFlow { topBarScrollState.hiddenOffsetPx > 0.5f }
                .distinctUntilChanged()
                .collect { collapsing ->
                    if (collapsing) showTitlePopup = false
                }
        }
    }
    BackHandler(enabled = showTitlePopup) {
        showTitlePopup = false
    }

    fun closePopupThen(action: () -> Unit) {
        showTitlePopup = false
        action()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .height(toolbarHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReaderTopBarIconButton(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.back),
            onClick = {
                if (showTitlePopup) {
                    showTitlePopup = false
                } else {
                    onBack()
                }
            },
            enabled = true,
            tint = iconTint,
        )
        Spacer(modifier = Modifier.width(ReaderTopBarTitleStartGap))

        // Keep one stable title row; the full value remains reachable from the popup.
        Box(
            modifier = Modifier
                .weight(1f)
                .height(toolbarHeight - 8.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = titlePopupCd
                }
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true),
                    role = Role.Button,
                    onClickLabel = titlePopupCd,
                    onClick = { showTitlePopup = !showTitlePopup },
                    onLongClick = { if (editingDocumentId != null) showFileInformation = true },
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = title,
                style = TextStyle(
                    fontSize = titleSize,
                    fontWeight = FontWeight.SemiBold,
                    // Match font size — no extra leading that shifts optical center.
                    lineHeight = titleSize,
                    color = titleColor,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (showTitlePopup) {
                val yOffsetPx = with(density) {
                    // Anchor stays 4dp below the adaptive title row.
                    toolbarHeight.roundToPx()
                }
                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(x = 0, y = yOffsetPx),
                    onDismissRequest = { showTitlePopup = false },
                    properties = PopupProperties(
                        focusable = true,
                        dismissOnBackPress = true,
                        dismissOnClickOutside = true,
                        clippingEnabled = true,
                    ),
                ) {
                    Surface(
                        modifier = Modifier.widthIn(min = 160.dp, max = popupMaxWidth)
                            .readerHairlineBorder(MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 0.dp,
                        tonalElevation = 0.dp,
                    ) {
                        Text(
                            text = title,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            style = TextStyle(
                                fontSize = ReaderTypography.tocH1Size,
                                fontWeight = FontWeight.Normal,
                                lineHeight = (ReaderTypography.tocH1Size.value * 1.4f).sp,
                                color = titleColor,
                            ),
                        )
                    }
                }
            }
        }

        Row {
            if (documentEditor != null && editingDocumentId != null) ReaderTopBarIconButton(
                imageVector = Icons.Default.Edit,
                contentDescription = "编辑正文",
                onClick = { closePopupThen { documentEditor(editingDocumentId) } },
                enabled = true, tint = iconTint,
            )
            ReaderTopBarIconButton(
                imageVector = Icons.AutoMirrored.Filled.List,
                contentDescription = stringResource(if (tocEnabled) R.string.toc_open else R.string.toc_empty),
                onClick = { closePopupThen(onOpenToc) },
                enabled = tocEnabled,
                tint = iconTint,
            )
            ReaderTopBarIconButton(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(R.string.search_open),
                onClick = { closePopupThen(onOpenSearch) },
                enabled = searchEnabled,
                tint = iconTint,
            )
        }
        Box {
            ReaderTopBarIconButton(
                imageVector = Icons.Default.MoreHoriz,
                contentDescription = "更多",
                onClick = { closePopupThen { showMore = true } },
                enabled = true,
                tint = iconTint,
            )
            if (showMore) {
                Popup(
                    alignment = Alignment.TopEnd,
                    offset = IntOffset(0, with(density) { 52.dp.roundToPx() }),
                    onDismissRequest = { showMore = false },
                    properties = PopupProperties(focusable = true),
                ) {
                    Column(
                        Modifier.width(224.dp)
                            .glassMaterial(
                                GlassMaterialRole.ExpandedMenu,
                                RoundedCornerShape(20.dp),
                                sampleBackdrop = false,
                                interaction = true,
                            )
                            .padding(vertical = 8.dp),
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.reader_settings_open)) },
                            leadingIcon = { Icon(Icons.Default.Tune, null) },
                            enabled = settingsEnabled,
                            onClick = { showMore = false; onOpenSettings() },
                        )
                        if (onToggleBookmark != null) DropdownMenuItem(
                            text = { Text(stringResource(if (bookmarkActive) R.string.reader_bookmark_remove else R.string.reader_bookmark_add)) },
                            leadingIcon = { Icon(if (bookmarkActive) Icons.Filled.Bookmarks else Icons.Outlined.Bookmarks, null) },
                            onClick = { showMore = false; onToggleBookmark() },
                        )
                        if (onOpenAnnotations != null) DropdownMenuItem(
                            text = { Text(stringResource(R.string.reader_annotations_title)) },
                            leadingIcon = { Icon(Icons.Filled.FormatListBulleted, null) },
                            onClick = { showMore = false; onOpenAnnotations() },
                        )
                        if (editingDocumentId != null) DropdownMenuItem(
                            text = { Text("文件信息与导出") },
                            onClick = { showMore = false; showFileInformation = true },
                        )
                        if (readingProgress != null) DropdownMenuItem(
                            text = { Text("拖动阅读进度") },
                            onClick = { showMore = false; showProgress = true },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderTopBarIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean,
    tint: Color,
) {
    // Explicit 48×48 hit target; icon centered (no per-button top/bottom padding).
    Box(
        modifier = Modifier
            .size(ReaderTopBarIconButton)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, radius = 24.dp),
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = Modifier.size(ReaderTopBarIcon),
            tint = if (enabled) {
                tint
            } else {
                tint.copy(alpha = 0.38f)
            },
        )
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
    /** R23 书签切换（null = 未启用，顶栏不渲染书签钮）。 */
    onToggleBookmark: (() -> Unit)? = null,
    bookmarkActive: Boolean = false,
    /** P0-1 标注列表入口（null = 未启用，顶栏不渲染标注钮）。 */
    onOpenAnnotations: (() -> Unit)? = null,
    snackbarHostState: SnackbarHostState? = null,
    /**
     * Null for loading/error chrome; when set, only [ReaderTopBarOverlay] reads
     * [ReaderTopBarScrollState.hiddenOffsetPx] (body never does).
     */
    topBarScrollState: ReaderTopBarScrollState? = null,
    nestedScrollConnection: NestedScrollConnection? = null,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    val systemCapturing = androidx.compose.ui.platform.LocalScrollCaptureInProgress.current
    val activeSearch = searchState?.takeIf { it.isActive }
    val readerBg = MaterialTheme.colorScheme.background

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (nestedScrollConnection != null) {
                    Modifier.nestedScroll(nestedScrollConnection)
                } else {
                    Modifier
                },
            ),
        containerColor = readerBg,
        contentColor = MaterialTheme.colorScheme.onBackground,
        // Reader owns insets: no Scaffold-level status bar consumption.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            if (snackbarHostState != null) {
                SnackbarHost(hostState = snackbarHostState)
            }
        },
        topBar = {},
        content = { scaffoldPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(readerBg),
            ) {
                content(scaffoldPadding)

                ReaderTopBarOverlay(
                    title = title,
                    topBarScrollState = if (activeSearch != null) null else topBarScrollState,
                    searchState = activeSearch,
                    onBack = onBack,
                    searchEnabled = searchEnabled,
                    onOpenSearch = onOpenSearch,
                    settingsEnabled = settingsEnabled,
                    onOpenSettings = onOpenSettings,
                    tocEnabled = tocEnabled,
                    onOpenToc = onOpenToc,
                    onToggleBookmark = onToggleBookmark,
                    bookmarkActive = bookmarkActive,
                    onOpenAnnotations = onOpenAnnotations,
                    onSearchQueryChange = onSearchQueryChange,
                    onCloseSearch = onCloseSearch,
                    onSearchPrevious = onSearchPrevious,
                    onSearchNext = onSearchNext,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .zIndex(2f)
                        .graphicsLayer { alpha = if (systemCapturing) 0f else 1f },
                )
            }
        },
    )
}

/**
 * The solid surface and foreground controls collapse together into the status-bar safe
 * area. Search replaces the reading row without moving the document.
 *
 * The optional backdrop argument keeps the existing test-call contract; this production
 * surface deliberately does not sample or blur scrolling text.
 */
@Composable
internal fun ReaderTopBarOverlay(
    @Suppress("UNUSED_PARAMETER")
    backdrop: LayerBackdrop? = null,
    title: String,
    topBarScrollState: ReaderTopBarScrollState?,
    searchState: DocumentSearchUiState?,
    onBack: () -> Unit,
    searchEnabled: Boolean,
    onOpenSearch: () -> Unit,
    settingsEnabled: Boolean,
    onOpenSettings: () -> Unit,
    tocEnabled: Boolean,
    onOpenToc: () -> Unit,
    onToggleBookmark: (() -> Unit)? = null,
    bookmarkActive: Boolean = false,
    onOpenAnnotations: (() -> Unit)? = null,
    onSearchQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
    onSearchPrevious: () -> Unit,
    onSearchNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = topBarScrollState
    val searching = searchState != null
    val collapseFraction = scrollState?.collapseFraction ?: 0f
    val controlsVisible = collapseFraction < 1f
    val statusHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val toolbarHeight = ReaderTypography.topBarHeight
    Box(modifier.height(statusHeight + toolbarHeight)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(statusHeight + toolbarHeight * (1f - collapseFraction))
                .clipToBounds()
                .background(MaterialTheme.colorScheme.background),
        ) {
            if (controlsVisible) Column(
                Modifier.requiredHeight(statusHeight + toolbarHeight).graphicsLayer {
                    alpha = 1f - collapseFraction
                    translationY = -toolbarHeight.toPx() * collapseFraction
                },
            ) {
                Spacer(Modifier.fillMaxWidth().height(statusHeight))
                AnimatedContent(
                    targetState = searching,
                    transitionSpec = {
                        fadeIn(tween(200)) togetherWith fadeOut(tween(120))
                    },
                    label = "readerSearchChrome",
                ) { showSearch ->
                    if (showSearch && searchState != null) {
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
                        ReaderTopBarRow(
                            title = title,
                            topBarScrollState = topBarScrollState,
                            onBack = onBack,
                            searchEnabled = searchEnabled,
                            onOpenSearch = onOpenSearch,
                            settingsEnabled = settingsEnabled,
                            onOpenSettings = onOpenSettings,
                            tocEnabled = tocEnabled,
                            onOpenToc = onOpenToc,
                            onToggleBookmark = onToggleBookmark,
                            bookmarkActive = bookmarkActive,
                            onOpenAnnotations = onOpenAnnotations,
                        )
                    }
                }
            }
            HorizontalDivider(
                modifier = Modifier.align(Alignment.BottomCenter),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

/**
 * Clamp saved absolute list index into the current list size.
 * Falls back to 0 when out of range or list is empty.
 */
private fun safeScrollIndex(savedIndex: Int, totalItems: Int): Int {
    if (totalItems <= 0) return 0
    if (savedIndex < 0 || savedIndex >= totalItems) return 0
    return savedIndex
}

internal fun nextTopBarOffsetForBodyTap(
    collapseFraction: Float,
    maxHiddenOffsetPx: Float,
): Float {
    val max = maxHiddenOffsetPx.coerceAtLeast(0f)
    if (max <= 0f) return 0f
    return if (collapseFraction.coerceIn(0f, 1f) >= 1f) 0f else max
}

/**
 * Observe short taps across the complete reading surface, including margins and gaps
 * between blocks. Child controls, text links, selection handles and scrolling get the
 * Main pass first; this detector reads Final and only acts when the complete gesture
 * remains unconsumed.
 */
@Composable
internal fun Modifier.passiveReaderBodyShortTap(onTap: (() -> Unit)?): Modifier {
    val currentOnTap by rememberUpdatedState(onTap)
    if (onTap == null) return this
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            var cancelled = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || event.changes.count { it.pressed } > 1) {
                    cancelled = true
                    break
                }
                if (!change.pressed) {
                    val shortTap =
                        change.uptimeMillis - down.uptimeMillis <
                            viewConfiguration.longPressTimeoutMillis
                    if (!cancelled && !change.isConsumed && shortTap) {
                        change.consume()
                        currentOnTap?.invoke()
                    }
                    break
                }
                if (
                    change.isConsumed ||
                    (change.position - down.position).getDistance() >
                    viewConfiguration.touchSlop
                ) {
                    cancelled = true
                }
            }
        }
    }
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
    anchorRegistry: com.m1racle.reader.markdown.AnchorRegistry,
    listHeaderCount: Int,
    blockCount: Int,
    scrollOffset: Int,
) {
    val blockIndex = anchorRegistry.resolve(item.anchor)?.blockIndex ?: item.blockIndex
    scrollToBlockIndex(
        listState = listState,
        blockIndex = blockIndex,
        listHeaderCount = listHeaderCount,
        blockCount = blockCount,
        scrollOffset = scrollOffset,
    )
}

private suspend fun scrollToBlockIndex(
    listState: LazyListState,
    blockIndex: Int,
    listHeaderCount: Int,
    blockCount: Int,
    scrollOffset: Int = 0,
) {
    if (blockCount <= 0) return
    val target = listHeaderCount + blockIndex
    val maxIndex = listHeaderCount + blockCount - 1
    if (target !in 0..maxIndex) return
    // Trace only around non-suspending work: animateScrollToItem is suspend and
    // can cancel mid-frame; lint UnclosedTrace requires begin/end without suspend gap.
    val mode = anchorJumpMode(listState.firstVisibleItemIndex, target)
    try {
        when (mode) {
            AnchorJumpMode.Direct -> {
                Trace.beginSection("AnchorJump")
                try {
                    listState.scrollToItem(target, scrollOffset)
                } finally {
                    Trace.endSection()
                }
            }
            AnchorJumpMode.Animated -> {
                // Section wraps only the call site start; end after resume in same
                // coroutine via try/finally (cancellation still runs finally).
                Trace.beginSection("AnchorJump")
                try {
                    listState.animateScrollToItem(target, scrollOffset)
                } finally {
                    Trace.endSection()
                }
            }
        }
    } catch (_: Exception) {
        // Ignore races from rapid clicks / disposed list.
    }
}

internal enum class AnchorJumpMode { Animated, Direct }

internal fun anchorJumpMode(
    currentItemIndex: Int,
    targetItemIndex: Int,
    animationDistanceItems: Int = 4,
): AnchorJumpMode = if (
    kotlin.math.abs(targetItemIndex - currentItemIndex) <= animationDistanceItems
) {
    AnchorJumpMode.Animated
} else {
    AnchorJumpMode.Direct
}

private const val TOC_LOG_TAG = "MARKDOWN_TOC"

/** P0-1 批注编辑器目标：锚点 + 既有批注（null = 新建）+ 是否返回管理表。 */
private data class MarkdownNoteTarget(
    val anchor: com.m1racle.reader.core.model.DocumentAnchor,
    val note: com.m1racle.reader.data.local.NoteEntity?,
    val returnToManagement: Boolean = false,
)

/**
 * P0-1 块纯文本（标注选区与摘录用）：与搜索文本口径一致的去标记文本；
 * 无文本块（分割线等）返回空串，调用方跳过。
 */
private fun MarkdownBlock.plainTextForAnnotation(): String = when (this) {
    is MarkdownBlock.Heading ->
        com.m1racle.reader.markdown.InlineMarkdownParser.toPlainText(text)
    is MarkdownBlock.Paragraph ->
        com.m1racle.reader.markdown.InlineMarkdownParser.toPlainText(text)
    is MarkdownBlock.Quote ->
        com.m1racle.reader.markdown.InlineMarkdownParser.toPlainText(text)
    is MarkdownBlock.CodeBlock -> code
    is MarkdownBlock.ListBlock -> items.joinToString("\n") { com.m1racle.reader.markdown.InlineMarkdownParser.toPlainText(it) }
    is MarkdownBlock.Table -> (headers + rows.flatten()).joinToString(" ") { com.m1racle.reader.markdown.InlineMarkdownParser.toPlainText(it) }
    is MarkdownBlock.Image -> alt
    is MarkdownBlock.Divider -> ""
}
