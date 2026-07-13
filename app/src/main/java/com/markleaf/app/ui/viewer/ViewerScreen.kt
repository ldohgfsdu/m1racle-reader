package com.markleaf.app.ui.viewer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import com.markleaf.app.R
import com.markleaf.app.data.settings.ReaderSettings
import com.markleaf.app.markdown.TocItem
import com.markleaf.app.markdown.blockContentType
import com.markleaf.app.markdown.blockKey
import com.markleaf.app.ui.viewer.components.MarkdownBlockView
import com.markleaf.app.ui.viewer.components.ReaderSettingsSheet
import com.markleaf.app.ui.viewer.components.ReaderTypography
import com.markleaf.app.ui.viewer.components.SearchTopBar
import com.markleaf.app.ui.viewer.components.TocDrawerContent
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
    val snackbarHostState = remember { SnackbarHostState() }
    val density = LocalDensity.current
    val topBarScrollState = remember(density) {
        ReaderTopBarScrollState(
            hideThresholdPx = with(density) { 32.dp.toPx() },
            // Continuous upward drag only — not fling velocity.
            showThresholdPx = with(density) { 88.dp.toPx() },
            jitterIgnorePx = with(density) { 12.dp.toPx() },
            cooldownMs = 250L,
        )
    }
    val topBarNestedScroll = remember(topBarScrollState) {
        topBarScrollState.asNestedScrollConnection()
    }
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

    // Persist: scroll stop, integer percent change, leave, or app background.
    // Never write Room on every scroll delta.
    LaunchedEffect(listState, state.document.id, totalListItems) {
        if (totalListItems <= 0) return@LaunchedEffect
        var lastPersistedPercent = -1
        var lastPersistedIndex = Int.MIN_VALUE
        var lastPersistedOffset = Int.MIN_VALUE
        var wasScrolling = false

        snapshotFlow {
            val total = listState.layoutInfo.totalItemsCount
            val progress = if (total <= 0) 0f else readingProgressOf(listState)
            ReadingScrollSnapshot(
                isScrolling = listState.isScrollInProgress,
                index = listState.firstVisibleItemIndex,
                offset = listState.firstVisibleItemScrollOffset,
                progress = progress,
                percent = (progress * 100f).toInt().coerceIn(0, 100),
                hasLayout = total > 0,
            )
        }
            .distinctUntilChanged()
            .collect { snap ->
                if (!snap.hasLayout) return@collect
                val scrollJustStopped = wasScrolling && !snap.isScrolling
                wasScrolling = snap.isScrolling

                val percentChanged = snap.percent != lastPersistedPercent
                val positionChanged =
                    snap.index != lastPersistedIndex || snap.offset != lastPersistedOffset

                // While scrolling: only integer-percent steps (coarse home progress).
                // On settle: full index/offset snapshot. Never every pixel delta.
                val shouldPersist = when {
                    snap.isScrolling -> percentChanged
                    scrollJustStopped -> positionChanged || percentChanged
                    else -> percentChanged
                }
                if (!shouldPersist) return@collect

                lastPersistedPercent = snap.percent
                lastPersistedIndex = snap.index
                lastPersistedOffset = snap.offset
                viewModel.saveReadingPosition(
                    lastScrollIndex = snap.index,
                    lastScrollOffset = snap.offset,
                    readingProgress = snap.progress,
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

    // Force TopAppBar visible at document top and while searching.
    LaunchedEffect(listState, searchState.isActive) {
        if (searchState.isActive) {
            topBarScrollState.forceShow()
            return@LaunchedEffect
        }
        snapshotFlow {
            listState.firstVisibleItemIndex == 0 &&
                listState.firstVisibleItemScrollOffset <= 0
        }
            .distinctUntilChanged()
            .collect { atTop ->
                if (atTop) topBarScrollState.forceShow()
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
            // Pass state object only — body never reads .visible (avoids recompose cascade).
            topBarScrollState = topBarScrollState,
            nestedScrollConnection = if (searchState.isActive) null else topBarNestedScroll,
        ) { innerPadding ->
            ReaderDocumentBody(
                state = state,
                listState = listState,
                readerSettings = readerSettings,
                searchState = searchState,
                snackbarHostState = snackbarHostState,
                listHeaderCount = listHeaderCount,
                innerPadding = innerPadding,
                searchActive = searchState.isActive,
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
    searchActive: Boolean,
) {
    val scope = rememberCoroutineScope()
    val contentPadding = readerSettings.contentPaddingLevel.horizontalDp
    val listTopInset = if (searchActive) {
        8.dp
    } else {
        ReaderTopBarHeight + 8.dp
    }
    val navBottom = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    val listBottomInset = navBottom + 24.dp

    // Only the active match block needs search highlight data.
    val activeMatch = searchState.currentMatch.takeIf { searchState.isActive }
    val activeMatchBlockIndex = activeMatch?.blockIndex

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
                if (state.isTruncated || state.isParseFallback) {
                    item(
                        key = "list_header_notice",
                        contentType = "notice",
                    ) {
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
                    key = { index, block -> block.blockKey(index) },
                    contentType = { _, block -> block.blockContentType() },
                ) { index, block ->
                    val isActiveMatch = activeMatchBlockIndex == index
                    MarkdownBlockView(
                        documentUri = state.document.uri,
                        block = block,
                        settings = readerSettings,
                        isSearchActive = isActiveMatch,
                        activeMatch = if (isActiveMatch) activeMatch else null,
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

/** Reader chrome height only — excludes status bar. */
private val ReaderTopBarHeight = 56.dp
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

    // Dismiss popup when bar hides without subscribing the body tree to visibility.
    if (topBarScrollState != null) {
        LaunchedEffect(topBarScrollState) {
            snapshotFlow { topBarScrollState.visible }
                .distinctUntilChanged()
                .collect { visible ->
                    if (!visible) showTitlePopup = false
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
    snackbarHostState: SnackbarHostState? = null,
    /** Null for loading/error chrome; when set, only [ReaderTopBarOverlay] reads .visible. */
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
        // Consume nothing here — status bar reserved by inner safe region.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            if (snackbarHostState != null) {
                SnackbarHost(hostState = snackbarHostState)
            }
        },
        topBar = {
            if (activeSearch != null) {
                // Search: under status bar (root content also uses statusBarsPadding).
                Column(Modifier.statusBarsPadding()) {
                    SearchTopBar(
                        query = activeSearch.query,
                        matchLabel = activeSearch.counterLabel,
                        hasMatches = activeSearch.matches.isNotEmpty(),
                        onQueryChange = onSearchQueryChange,
                        onClose = onCloseSearch,
                        onPrevious = onSearchPrevious,
                        onNext = onSearchNext,
                    )
                }
            }
        },
        content = { scaffoldPadding ->
            if (activeSearch != null) {
                // Search: Scaffold topBar already under statusBars; body uses scaffold padding only.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(readerBg),
                ) {
                    content(scaffoldPadding)
                }
            } else {
                // Fixed safe-area structure — insets NEVER translate.
                // Layer 1: full screen background (status bar paint area matches theme).
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(readerBg),
                ) {
                    // Solid status-bar fill (does not move).
                    Spacer(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .windowInsetsTopHeight(WindowInsets.statusBars)
                            .background(readerBg),
                    )

                    // Layer 2: fixed safe region below status bar (does not move).
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(
                                WindowInsets.statusBars.only(WindowInsetsSides.Top),
                            ),
                    ) {
                        // Layer 3: clip so sliding TopBar cannot paint into status bar.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clipToBounds(),
                        ) {
                            // Body first — never reads TopBar offset / visibility.
                            content(scaffoldPadding)

                            // Layer 4: isolated TopBar composition boundary.
                            ReaderTopBarOverlay(
                                title = title,
                                topBarScrollState = topBarScrollState,
                                onBack = onBack,
                                searchEnabled = searchEnabled,
                                onOpenSearch = onOpenSearch,
                                settingsEnabled = settingsEnabled,
                                onOpenSettings = onOpenSettings,
                                tocEnabled = tocEnabled,
                                onOpenToc = onOpenToc,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth()
                                    .zIndex(1f),
                            )
                        }
                    }
                }
            }
        },
    )
}

/**
 * Isolated TopBar chrome: only this subtree observes show/hide and animates
 * [graphicsLayer] translationY. Snap animation runs once per visibility flip
 * (not per scroll delta). LazyColumn contentPadding stays fixed.
 */
@Composable
private fun ReaderTopBarOverlay(
    title: String,
    topBarScrollState: ReaderTopBarScrollState?,
    onBack: () -> Unit,
    searchEnabled: Boolean,
    onOpenSearch: () -> Unit,
    settingsEnabled: Boolean,
    onOpenSettings: () -> Unit,
    tocEnabled: Boolean,
    onOpenToc: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val topBarHeightPx = with(density) { ReaderTopBarHeight.toPx() }
    val translationY = remember { Animatable(0f) }

    // Single animation per visibility change — not per drag delta.
    LaunchedEffect(topBarScrollState, topBarHeightPx) {
        if (topBarScrollState == null) {
            translationY.snapTo(0f)
            return@LaunchedEffect
        }
        // Sync initial without animation.
        translationY.snapTo(if (topBarScrollState.visible) 0f else -topBarHeightPx)
        snapshotFlow { topBarScrollState.visible }
            .distinctUntilChanged()
            .collect { visible ->
                val target = if (visible) 0f else -topBarHeightPx
                translationY.animateTo(
                    targetValue = target,
                    animationSpec = tween(durationMillis = 190),
                )
            }
    }

    Column(
        modifier = modifier
            .graphicsLayer {
                // High-frequency animated value is read only here (layer invalidate).
                this.translationY = translationY.value
            }
            .background(MaterialTheme.colorScheme.surface),
    ) {
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
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}

private data class ReadingScrollSnapshot(
    val isScrolling: Boolean,
    val index: Int,
    val offset: Int,
    val progress: Float,
    val percent: Int,
    val hasLayout: Boolean,
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
