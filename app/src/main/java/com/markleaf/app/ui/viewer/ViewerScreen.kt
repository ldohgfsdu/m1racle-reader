package com.markleaf.app.ui.viewer

import android.os.Trace
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.windowInsetsTopHeight
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import com.markleaf.app.BuildConfig
import com.markleaf.app.R
import com.markleaf.app.data.anchor.DocumentAnchor
import com.markleaf.app.data.settings.ReaderSettings
import com.markleaf.app.markdown.MarkdownPerformance
import com.markleaf.app.markdown.TocItem
import com.markleaf.app.markdown.blockContentType
import com.markleaf.app.markdown.blockId
import com.markleaf.app.ui.common.StatusBarProtection
import com.markleaf.app.ui.viewer.components.MarkdownBlockView
import com.markleaf.app.ui.viewer.components.headingTopPadding
import com.markleaf.app.ui.viewer.components.ReaderSettingsSheet
import com.markleaf.app.ui.viewer.components.ReaderTypography
import com.markleaf.app.ui.viewer.components.SearchTopBar
import com.markleaf.app.ui.viewer.components.TocDrawerContent
import com.markleaf.app.ui.viewer.components.TableLayoutCache
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
    bookmarkController: com.markleaf.app.ui.reader.ReaderBookmarkController? = null,
    initialAnchor: DocumentAnchor? = null,
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
            ViewerSuccessScreen(
                state = state,
                title = title,
                viewModel = viewModel,
                onBack = onBack,
                bookmarkController = bookmarkController,
                initialAnchor = initialAnchor,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ViewerSuccessScreen(
    state: ViewerUiState.Success,
    title: String,
    viewModel: ViewerViewModel,
    onBack: () -> Unit,
    bookmarkController: com.markleaf.app.ui.reader.ReaderBookmarkController?,
    initialAnchor: DocumentAnchor?,
    modifier: Modifier = Modifier,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val hasToc = state.toc.isNotEmpty()
    val readerSettings by viewModel.readerSettings.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val density = LocalDensity.current
    val tableLayoutCache = remember(state.document.id) {
        TableLayoutCache(documentId = state.document.id)
    }
    // Keyed by document id so a new document never inherits a hidden bar / accumulators.
    // maxHiddenOffset is updated from live status-bar + chrome metrics (rotation / inset).
    val topBarScrollState = remember(state.document.id, density) {
        ReaderTopBarScrollState(
            maxHiddenOffsetPx = with(density) {
                // Placeholder until first composition measures status bars; updated below.
                (ReaderTopBarHeight + ReaderTopBarDividerHeight).toPx()
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
        statusBarTopPx + (ReaderTopBarHeight + ReaderTopBarDividerHeight).toPx()
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

    // Search navigation supplies the same canonical anchor used by annotations.
    // The existing block jump helper remains the sole Markdown positioning path.
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
            when (controller.toggle(index)) {
                com.markleaf.app.ui.reader.ReaderBookmarkController.ToggleResult.Added -> {
                    bookmarkedIndexes = bookmarkedIndexes + index
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.reader_bookmark_added),
                    )
                }
                com.markleaf.app.ui.reader.ReaderBookmarkController.ToggleResult.Removed -> {
                    bookmarkedIndexes = bookmarkedIndexes - index
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.reader_bookmark_removed),
                    )
                }
            }
        }
    }

    // Persist only from a settled list, plus leave/background flushes. Observing index/offset while
    // a 120 Hz gesture is active used to allocate and dispatch one ReadingScrollSnapshot for every
    // pixel-level position change even though the collector immediately discarded it.
    LaunchedEffect(listState, state.document.id, totalListItems) {
        if (totalListItems <= 0) return@LaunchedEffect
        var lastPersistedIndex = Int.MIN_VALUE
        var lastPersistedOffset = Int.MIN_VALUE

        snapshotFlow { listState.isScrollInProgress }
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
    // Flush on background (ON_STOP) and when leaving composition.
    DisposableEffect(state.document.id, listState, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                flushReadingPosition(listState, latestTotalListItems, viewModel)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            flushReadingPosition(listState, latestTotalListItems, viewModel)
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
    LaunchedEffect(listState, searchState.isActive, topBarScrollState) {
        if (searchState.isActive) {
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = hasToc,
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
            onOpenSearch = {
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
                searchState.isActive || ReaderPerformanceExperiments.disableTopBar
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
                onAnchorJump = topBarScrollState::forceShow,
            )
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

/**
 * Document body composition boundary: does not read TopBar visibility / offset.
 * Each Markdown block is its own LazyColumn item with stable key + contentType.
 */
@Composable
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
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val uriHandler = LocalUriHandler.current
    val contentPadding = readerSettings.contentPaddingLevel.horizontalDp
    // Initial top chrome reservation lives in LazyColumn contentPadding only — never as a
    // fixed outer Box padding — so fully-hidden chrome leaves zero permanent top blank.
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val listTopInset = statusBarTop + ReaderTopBarHeight + ReaderTopBarDividerHeight + 8.dp
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
            com.markleaf.app.search.groupMatchesByBlockIndex(searchState.matches)
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
                val action = com.markleaf.app.markdown.MarkdownLinkRouter.classify(destination)
            ) {
                is com.markleaf.app.markdown.MarkdownLinkRouter.Action.InPageAnchor -> {
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
                            as? com.markleaf.app.markdown.MarkdownBlock.Heading)?.level ?: 1
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
                is com.markleaf.app.markdown.MarkdownLinkRouter.Action.External -> {
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
                        com.markleaf.app.search.blockHighlightsFromGrouped(
                            blockIndex = index,
                            blockMatches = blockMatches,
                            activeStableKey = activeKeyForBlock,
                        )
                    }
                    MarkdownBlockView(
                        documentUri = state.document.uri,
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
                        modifier = if (ReaderPerformanceExperiments.clearBlockSemantics) {
                            Modifier.clearAndSetSemantics { }
                        } else {
                            Modifier
                        },
                    )
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

/** Reader chrome row height only — excludes status bar. */
private val ReaderTopBarHeight = 56.dp
private val ReaderTopBarDividerHeight = 0.5.dp
private val ReaderTopBarIconButton = 48.dp
private val ReaderTopBarIcon = 24.dp
/** Gap after back button so icon-edge → title ≈ 12dp (12dp internal + 4dp). */
private val ReaderTopBarTitleStartGap = 4.dp

/**
 * Custom reader top bar: [Back + title] left group, [Search][Settings][TOC] right group.
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
    modifier: Modifier = Modifier,
) {
    // Full-contrast chrome icons (not muted gray) for dark/light consistency.
    val iconTint = MaterialTheme.colorScheme.onSurface
    val titleColor = MaterialTheme.colorScheme.onSurface
    val titleSize = ReaderTypography.topBarTitleSize
    val titlePopupCd = stringResource(R.string.viewer_title_popup_cd)

    var showTitlePopup by remember { mutableStateOf(false) }
    val density = LocalDensity.current
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
            .height(ReaderTopBarHeight),
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

        // Fixed 48dp title hit-box, vertically centered with IconButtons (not fillMaxHeight).
        Box(
            modifier = Modifier
                .weight(1f)
                .height(ReaderTopBarIconButton)
                .semantics {
                    role = Role.Button
                    contentDescription = titlePopupCd
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true),
                    role = Role.Button,
                    onClickLabel = titlePopupCd,
                    onClick = { showTitlePopup = !showTitlePopup },
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
                    // Anchor is 48dp title box; bar is 56dp so bottom of bar is
                    // (56-48)/2 + 48 = 52dp from title box top; +4dp gap → 56dp.
                    (ReaderTopBarIconButton + 4.dp + 4.dp).roundToPx()
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
                        modifier = Modifier.widthIn(min = 160.dp, max = popupMaxWidth),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shadowElevation = 2.dp,
                        tonalElevation = 0.dp,
                    ) {
                        Text(
                            text = title,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            style = TextStyle(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal,
                                lineHeight = (15f * 1.4f).sp,
                                color = titleColor,
                            ),
                        )
                    }
                }
            }
        }

        // R23 书签切换（SCROLL_POSITION）：实心=当前位置已收藏；null 控制器不渲染。
        if (onToggleBookmark != null) {
            ReaderTopBarIconButton(
                imageVector = if (bookmarkActive) {
                    Icons.Filled.Bookmarks
                } else {
                    Icons.Outlined.Bookmarks
                },
                contentDescription = stringResource(
                    if (bookmarkActive) R.string.reader_bookmark_remove else R.string.reader_bookmark_add,
                ),
                onClick = { closePopupThen(onToggleBookmark) },
                enabled = true,
                tint = if (bookmarkActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    iconTint
                },
            )
        }
        ReaderTopBarIconButton(
            imageVector = Icons.Default.Search,
            contentDescription = stringResource(R.string.search_open),
            onClick = { closePopupThen(onOpenSearch) },
            enabled = searchEnabled,
            tint = iconTint,
        )
        ReaderTopBarIconButton(
            imageVector = Icons.Default.Tune,
            contentDescription = stringResource(R.string.reader_settings_open),
            onClick = { closePopupThen(onOpenSettings) },
            enabled = settingsEnabled,
            tint = iconTint,
        )
        ReaderTopBarIconButton(
            imageVector = Icons.AutoMirrored.Filled.List,
            contentDescription = if (tocEnabled) {
                stringResource(R.string.toc_open)
            } else {
                stringResource(R.string.toc_empty)
            },
            onClick = { closePopupThen(onOpenToc) },
            enabled = tocEnabled,
            tint = iconTint,
        )
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
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, radius = 24.dp),
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

                StatusBarProtection(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .zIndex(1f),
                )

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
                    onSearchQueryChange = onSearchQueryChange,
                    onCloseSearch = onCloseSearch,
                    onSearchPrevious = onSearchPrevious,
                    onSearchNext = onSearchNext,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .zIndex(2f),
                )
            }
        },
    )
}

/**
 * Solid top chrome (status fill + reading row or in-bar search).
 *
 * - Moves with [graphicsLayer] translationY = -[hiddenOffsetPx] only.
 * - Search replaces the reading row in place (same 56dp) so the body does not jump.
 * - LazyColumn contentPadding reserves status+bar once; body never reads offset.
 */
@Composable
private fun ReaderTopBarOverlay(
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
    onSearchQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
    onSearchPrevious: () -> Unit,
    onSearchNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Do NOT read hiddenOffsetPx in composition — that recomposes the whole overlay
    // every drag pixel. Read inside graphicsLayer so only the layer invalidates.
    val surface = MaterialTheme.colorScheme.surface
    val scrollState = topBarScrollState
    val searching = searchState != null

    Column(
        modifier = modifier
            .graphicsLayer {
                translationY = -(scrollState?.hiddenOffsetPx ?: 0f)
            }
            .background(surface),
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(surface),
        )
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
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ReaderTopBarDividerHeight)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
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
    anchorRegistry: com.markleaf.app.markdown.AnchorRegistry,
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
