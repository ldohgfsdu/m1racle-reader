package com.m1racle.reader.ui.epub

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.*
import com.m1racle.reader.data.settings.EpubReadingMode
import eu.wewox.pagecurl.page.PageCurl
import eu.wewox.pagecurl.page.rememberPageCurlState
import eu.wewox.pagecurl.config.rememberPageCurlConfig
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.m1racle.reader.data.settings.ReaderSettings
import com.m1racle.reader.epub.EpubBook
import com.m1racle.reader.epub.EpubTocNode
import com.m1racle.reader.epub.EpubReadingAnchors
import com.m1racle.reader.epub.EpubRestoreTier
import com.m1racle.reader.epub.EpubSavedPosition
import com.m1racle.reader.epub.EpubRestoreSession
import com.m1racle.reader.image.LocalImageLoader
import com.m1racle.reader.image.LocalImageState
import com.m1racle.reader.ui.material.GlassBackdropHost
import com.m1racle.reader.ui.material.GlassBackdropSource
import com.m1racle.reader.ui.material.GlassEnvironment
import com.m1racle.reader.ui.material.LocalGlassBackdropHost
import com.m1racle.reader.ui.material.LocalGlassEnvironment
import com.m1racle.reader.ui.theme.LocalReaderDarkTheme
import com.m1racle.reader.ui.theme.M1racleMotion
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

private val EpubPageTurnSpec = spring<Float>(
    dampingRatio = 1f,
    stiffness = 400f,
    visibilityThreshold = 0.5f,
)

private fun turnTo(pager: PagerState, page: Int, scope: CoroutineScope) {
    if (page !in 0 until pager.pageCount || page == pager.currentPage) return
    scope.launch { pager.animateScrollToPage(page, animationSpec = EpubPageTurnSpec) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, eu.wewox.pagecurl.ExperimentalPageCurlApi::class)
@Composable
internal fun EpubReaderContent(
    book: EpubBook,
    settings: ReaderSettings,
    title: String,
    initial: EpubSavedPosition,
    onPosition: (EpubPosition) -> Unit,
    onFlush: () -> Unit,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    // Visibility-independent safe content bounds: system bars may overlay the
    // full-window paper without changing pagination or the saved text anchor.
    insets: WindowInsets = WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout),
    settingsVisible: Boolean = false,
    onDismissSettings: () -> Unit = {},
    settingsContent: @Composable () -> Unit = {},
    deviceCorners: EpubDeviceCorners = rememberEpubDeviceCorners(),
    restoreSession: EpubRestoreSession = remember(book, initial) {
        EpubRestoreSession(EpubReadingAnchors.restore(book, initial))
    },
) {
    var tocVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val baseStyle = MaterialTheme.typography.bodyLarge
    val style = baseStyle.copy(color = MaterialTheme.colorScheme.onSurface,
        fontFamily = rememberEpubReaderFont(settings) ?: baseStyle.fontFamily)
    // Measure both footer lines, including system font scaling and font padding.
    // Keep both lines laid out while chrome is hidden so a tap cannot repaginate.
    var footerHeight by remember { mutableIntStateOf(0) }
    val footerClearance = with(density) { footerHeight.toDp() } + 12.dp
    // Reserve this even in immersion mode: revealing controls must not cover
    // a line, resize the page, or change its character anchor.
    val topClearance = epubTopBarHeight()
    val background = MaterialTheme.colorScheme.background
    val drawPageBackdrop: ContentDrawScope.() -> Unit = remember(background) {
        { drawRect(background); drawContent() }
    }
    val backdrop = rememberLayerBackdrop(onDraw = drawPageBackdrop)
    val glassHost = remember(backdrop) { GlassBackdropHost(backdrop, backdrop) }
    val rows by produceState<List<EpubTextRow>?>(null, book, settings, style) {
        value = null
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val context = kotlinx.coroutines.currentCoroutineContext()
            epubTextRows(book, settings, style) { context.ensureActive() }
        }
    }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var sheetWidth by remember { mutableIntStateOf(0) }
    // KOReader / Foliate / Apple Books / 微信读书: the book is the default view.
    var chromeVisible by rememberSaveable { mutableStateOf(false) }
    EpubSystemBars(visible = false)
    val chromeProgress by animateFloatAsState(
        if (chromeVisible) 1f else 0f,
        animationSpec = if (chromeVisible) M1racleMotion.slow else M1racleMotion.normalIn,
        label = "epubChrome",
    )
    // Text offsets survive a settings/viewport reflow and are persisted as an
    // EPUB text anchor. anchorText < 0 means the saved position is not restored yet.
    var anchorRow by rememberSaveable { mutableIntStateOf(0) }
    var anchorText by rememberSaveable { mutableIntStateOf(-1) }
    val anchors = remember(book, rows) { rows?.let { EpubRowIndex(book, it) } }
    val gap = with(density) { com.m1racle.reader.ui.viewer.components.ReaderTypography.bodyParagraphBottom.dp.toPx() }
    val inset = with(density) { 12.dp.roundToPx() }
    // Probe image sizes off the main thread, then paginate in bounded slices. The
    // block is cancelled and restarted when the book, settings, or viewport change.
    val layout by produceState<EpubTextLayout?>(null, rows, viewport, measurer, density) {
        value = null
        val currentRows = rows
        val index = anchors
        if (currentRows != null && index != null && viewport.width > 0 && viewport.height > 0) {
            val bounds = probeEpubImageBounds(currentRows)
            val hint = if (anchorText >= 0) anchorRow else EpubReadingAnchors.restore(book, initial).let { restored ->
                restored.location?.let(index::sourceRow) ?: restored.legacyRow ?: 0
            }
            value = measureEpub(
                currentRows, measurer, viewport.width, viewport.height, gap, inset,
                imageBounds = bounds, anchorRow = currentRows.indexOfFirst { it.sourceRow == hint }.coerceAtLeast(0),
            )
        }
    }
    BackHandler(tocVisible) { tocVisible = false }
    val systemCaptureInProgress by rememberUpdatedState(androidx.compose.ui.platform.LocalScrollCaptureInProgress.current)
    val currentPosition by rememberUpdatedState(onPosition)
    val currentFlush by rememberUpdatedState(onFlush)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) currentFlush()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            currentFlush()
        }
    }
    val ready = layout
    LaunchedEffect(restoreSession) {
        snapshotFlow { layout }.first { it != null && it.pages.isNotEmpty() }
        if (restoreSession.takeDegradedNotice()) {
            snackbarHost.showSnackbar("没能精确回到上次的位置，已跳到附近")
        }
    }
    // Resolved once per layout; the drawer only does a map lookup per row.
    val tocPages = remember(ready) { ready?.let { epubTocPages(book, it) } ?: emptyMap() }
    val pager = rememberPagerState { (ready?.pages?.size ?: 1).coerceAtLeast(1) }
    // Pager's settledPage rebases between the drag and its fling, even with a
    // nonzero offset. Keep one committed reading page for paper, footer and save
    // until the complete gesture/animation reaches the snapped position.
    var readingPage by remember(pager) { mutableIntStateOf(pager.currentPage) }
    val mode = settings.epubReadingMode
    val continuousList = rememberLazyListState()
    val curl = rememberPageCurlState()
    val curlConfig = rememberPageCurlConfig(backPageColor = background,
        backPageContentAlpha = .08f, tapForwardEnabled = false, tapBackwardEnabled = false,
        tapCustomEnabled = true, onCustomTap = { _, _ -> chromeVisible = !chromeVisible; true })

    var dragStartPage by remember(pager) { mutableStateOf<Int?>(null) }
    LaunchedEffect(pager) {
        pager.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) {
                dragStartPage = readingPage
                chromeVisible = false
            }
        }
    }
    fun navigateTo(page: Int, animated: Boolean) {
        val currentLayout = ready ?: return
        if (page !in currentLayout.pages.indices) return
        // Even selecting the current TOC page is deliberate navigation.
        restoreSession.onUserNavigation()
        val position = currentLayout.positionAt(currentLayout.pages[page].slices.first())
        if (page == readingPage) {
            anchorRow = position.first
            anchorText = position.third
            anchors?.anchorAt(anchorRow, anchorText)?.let {
                val progress = if (currentLayout.pages.size <= 1) 1f else page.toFloat() / currentLayout.pages.lastIndex
                currentPosition(EpubPosition(it, position.first, position.second, progress))
            }
        }
        when (mode) {
            EpubReadingMode.Continuous -> scope.launch {
                if (animated) continuousList.animateScrollToItem(page) else continuousList.scrollToItem(page)
            }
            EpubReadingMode.Curl -> scope.launch {
                if (!animated || kotlin.math.abs(page - curl.current) != 1) curl.snapTo(page)
                else if (page > curl.current) curl.next() else curl.prev()
            }
            EpubReadingMode.Slide -> if (animated) turnTo(pager, page, scope) else scope.launch { pager.scrollToPage(page) }
        }
    }
    LaunchedEffect(pager, ready, mode) {
        val pages = ready?.pages ?: return@LaunchedEffect
        val anchors = anchors ?: return@LaunchedEffect
        if (pages.isEmpty()) return@LaunchedEffect
        dragStartPage = null
        // Finish restoring against the new layout before observing/persisting
        // its page numbers. An old page number has no meaning after reflow.
        snapshotFlow { !pager.isScrollInProgress && pager.currentPageOffsetFraction == 0f }.first { it }
        if (anchorText < 0) {
            val restored = restoreEpubText(book, anchors, ready, initial)
            // Never silent: a degraded restore is logged; it never falls back to page 1.
            if (restored.tier == EpubRestoreTier.ChapterStart || restored.tier == EpubRestoreTier.Progress) {
                android.util.Log.w("EpubReader", "定位已降级: ${restored.tier} ${initial.anchor?.spineHref}")
            }
            anchorRow = restored.sourceRow
            anchorText = restored.offset
        }
        val target = ready.pageForText(anchorRow, anchorText).coerceIn(0, pages.lastIndex)
        when (mode) {
            EpubReadingMode.Continuous -> continuousList.scrollToItem(target, ready.pagePixelForText(anchorRow, anchorText).second)
            EpubReadingMode.Curl -> { curl.snapTo(target) }
            EpubReadingMode.Slide -> if (pager.currentPage != target) pager.scrollToPage(target)
        }
        readingPage = target
        snapshotFlow {
            if (systemCaptureInProgress) null else when (mode) {
                EpubReadingMode.Slide -> if (!pager.isScrollInProgress && pager.currentPageOffsetFraction == 0f) pager.currentPage to 0 else null
                EpubReadingMode.Curl -> if (curl.progress == 0f) curl.current to 0 else null
                EpubReadingMode.Continuous -> if (!continuousList.isScrollInProgress) continuousList.firstVisibleItemIndex to continuousList.firstVisibleItemScrollOffset else null
            }
        }.filterNotNull().distinctUntilChanged().collect { (index, pixelOffset) ->
            val page = ready.pages.getOrNull(index) ?: return@collect
            val first = if (mode == EpubReadingMode.Continuous) {
                val y = pixelOffset.toFloat()
                val slice = page.slices.lastOrNull { it.pageTop <= y } ?: page.slices.first()
                val sourceY = slice.sourceTop + (y - slice.pageTop).coerceAtLeast(0f)
                val line = ready.rowMetrics[slice.row].lines.firstOrNull { it.bottom > sourceY && it.start >= slice.start }
                if (line != null && line.start < slice.end) slice.copy(start = line.start, sourceTop = line.top) else slice
            } else page.slices.first()
            val position = ready.positionAt(first)
            // Reflow can put the original character part way down a new page.
            // Keep that character until a real page turn; rounding to the new
            // page's first line here made repeated font changes drift backwards.
            if (index != readingPage || anchorText < 0 || mode == EpubReadingMode.Continuous) {
                if (index != readingPage && dragStartPage != null && dragStartPage != index) {
                    restoreSession.onUserNavigation()
                    dragStartPage = null
                }
                if (index != readingPage) chromeVisible = false
                anchorRow = position.first
                anchorText = position.third
            }
            readingPage = index
            val progress = if (ready.pages.size <= 1) 1f else index.toFloat() / ready.pages.lastIndex
            val anchor = anchors.anchorAt(anchorRow, anchorText)
                ?: anchors.anchorAt(position.first, position.third)
                ?: return@collect
            currentPosition(EpubPosition(anchor, position.first, position.second, progress,
                canSaveAnchor = restoreSession.anchorForSave(anchor) != null))
        }
    }
    Box(modifier.fillMaxSize()) {
        CompositionLocalProvider(
            LocalGlassEnvironment provides GlassEnvironment.reader(LocalReaderDarkTheme.current),
        ) {
            Box(Modifier.fillMaxSize().background(background)) {
                // Only paper and content are captured. Controls remain siblings
                // so their glass never samples itself or duplicates its icons.
                val scrollFromReading = if (mode == EpubReadingMode.Slide) pager.currentPage - readingPage + pager.currentPageOffsetFraction else 0f
                val backingPaper = if (scrollFromReading < 0f) lerp(background,
                    if (LocalReaderDarkTheme.current) Color(0xFF202022) else Color(0xFFF7F7F7),
                    1f - (1f - kotlin.math.abs(scrollFromReading).coerceIn(0f, 1f)).let { it * it }) else background
                GlassBackdropSource(backdrop, Modifier.fillMaxSize().background(backingPaper)) {
                    Box(Modifier.fillMaxSize().onSizeChanged { sheetWidth = it.width }) {
                        // Measure the text viewport, while the animated sheet spans the whole page.
                        Box(
                            Modifier.fillMaxSize()
                                .windowInsetsPadding(insets)
                                .padding(horizontal = settings.contentPaddingLevel.horizontalDp)
                                .padding(top = topClearance + 12.dp, bottom = footerClearance)
                                .onSizeChanged { size ->
                                    if (!epubViewportSettled(viewport, size)) viewport = size
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (ready == null) CircularProgressIndicator()
                            else if (ready.pages.isEmpty()) Text("本书没有可顺序阅读的章节")
                        }
                        if (ready != null && ready.pages.isNotEmpty()) {
                            val pageContent: @Composable (Int) -> Unit = { index ->
                                val turn = if (mode == EpubReadingMode.Slide) epubPageTurnLayer(
                                    pager.getOffsetDistanceInPages(index), pager.currentPageOffsetFraction,
                                    pager.currentPage, readingPage, sheetWidth.toFloat(),
                                ) else EpubPageTurnLayer(0f, 0f, 0f)
                                val paper = lerp(background,
                                    if (LocalReaderDarkTheme.current) Color(0xFF202022) else Color(0xFFF7F7F7),
                                    turn.paperLift)
                                Box(
                                    Modifier.fillMaxSize()
                                        .zIndex(turn.zIndex)
                                        .graphicsLayer { translationX = turn.translationX }
                                        .graphicsLayer {
                                            shape = deviceCorners.shape()
                                            shadowElevation = 12.dp.toPx() * turn.edgeShadow
                                            clip = true
                                        }
                                        .background(paper)
                                        .epubPaperEdge(deviceCorners, turn.zIndex > 0f, LocalReaderDarkTheme.current, turn.edgeShadow)
                                        .testTag("epub-sheet-$index"),
                                ) {
                                    Box(Modifier.fillMaxSize()
                                        .windowInsetsPadding(insets)
                                        .padding(horizontal = settings.contentPaddingLevel.horizontalDp)
                                        .padding(top = topClearance + 12.dp, bottom = footerClearance)
                                        .then(if (index == readingPage) Modifier.testTag("epub-reading-area")
                                            else Modifier.clearAndSetSemantics {})) {
                                        EpubPageView(ready, index, inset, viewport.height) { tap ->
                                            when (tap) {
                                                EpubPageTap.Previous -> {
                                                    chromeVisible = false
                                                    navigateTo(readingPage - 1, animated = true)
                                                }
                                                EpubPageTap.Next -> {
                                                    chromeVisible = false
                                                    navigateTo(readingPage + 1, animated = true)
                                                }
                                                EpubPageTap.ToggleChrome -> chromeVisible = !chromeVisible
                                            }
                                        }
                                    }
                                    Text(
                                        title,
                                        modifier = Modifier.align(Alignment.TopCenter).graphicsLayer { alpha = if (systemCaptureInProgress) 0f else 1f }
                                            .windowInsetsPadding(insets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                                            .padding(top = 18.dp, start = 24.dp + 40.dp * chromeProgress,
                                                end = 24.dp + 88.dp * chromeProgress)
                                            .testTag("epub-paper-title")
                                            .then(if (index == readingPage) Modifier else Modifier.clearAndSetSemantics {}),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                    EpubPageFooter(ready, index, chromeProgress,
                                        active = index == readingPage,
                                        modifier = Modifier.align(Alignment.BottomCenter)
                                            .windowInsetsPadding(insets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
                                        onHeight = { if (index == readingPage) footerHeight = it })
                                }
                            }
                            when (mode) {
                                EpubReadingMode.Slide -> HorizontalPager(
                                    state = pager, beyondViewportPageCount = 1,
                                    flingBehavior = PagerDefaults.flingBehavior(state = pager,
                                        pagerSnapDistance = PagerSnapDistance.atMost(1),
                                        snapAnimationSpec = EpubPageTurnSpec, snapPositionalThreshold = .33f),
                                    modifier = Modifier.fillMaxSize().testTag("epub-pager"),
                                ) { pageContent(it) }
                                EpubReadingMode.Curl -> PageCurl(count = ready.pages.size, state = curl,
                                    config = curlConfig, modifier = Modifier.fillMaxSize().testTag("epub-curl"), content = pageContent)
                                EpubReadingMode.Continuous -> {
                                    LaunchedEffect(continuousList.isScrollInProgress) {
                                        if (continuousList.isScrollInProgress && !systemCaptureInProgress) { chromeVisible = false; restoreSession.onUserNavigation() }
                                    }
                                    LazyColumn(state = continuousList,
                                        modifier = Modifier.fillMaxSize().windowInsetsPadding(insets)
                                            .padding(horizontal = settings.contentPaddingLevel.horizontalDp).testTag("epub-continuous"),
                                        contentPadding = PaddingValues(top = topClearance + 12.dp, bottom = footerClearance),
                                    ) {
                                        items(ready.pages.size, key = { it }) { index ->
                                            EpubPageView(ready, index, inset, 0) { chromeVisible = !chromeVisible }
                                        }
                                    }
                                    EpubPageFooter(ready, readingPage, chromeProgress, active = true,
                                        modifier = Modifier.align(Alignment.BottomCenter).graphicsLayer { alpha = if (systemCaptureInProgress) 0f else 1f }.background(background)
                                            .windowInsetsPadding(insets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
                                        onHeight = { footerHeight = it })
                                }
                            }
                        } else {
                            EpubPageFooter(ready, 0, chromeProgress, active = true,
                                modifier = Modifier.align(Alignment.BottomCenter)
                                    .windowInsetsPadding(insets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
                                onHeight = { footerHeight = it })
                        }
                    }
                }
                CompositionLocalProvider(LocalGlassBackdropHost provides glassHost) {
                    AnimatedVisibility(
                        visible = chromeVisible,
                        enter = fadeIn(M1racleMotion.slow) + slideInVertically(
                            animationSpec = androidx.compose.animation.core.tween(M1racleMotion.SlowMs, easing = M1racleMotion.easeOut),
                            initialOffsetY = { -it / 4 }),
                        exit = fadeOut(M1racleMotion.normalIn) + slideOutVertically(
                            animationSpec = androidx.compose.animation.core.tween(M1racleMotion.NormalMs, easing = M1racleMotion.easeIn),
                            targetOffsetY = { -it / 4 }),
                        modifier = Modifier.align(Alignment.TopCenter).graphicsLayer { alpha = if (systemCaptureInProgress) 0f else 1f }
                            .windowInsetsPadding(insets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
                    ) {
                        EpubReaderTopBar(onBack = onBack)
                    }
                EpubReaderMenu(settingsVisible, tocVisible,
                    onDismiss = { onDismissSettings(); tocVisible = false },
                    onToc = { onDismissSettings(); tocVisible = true },
                    onSettings = { tocVisible = false; onSettings() },
                    settingsContent = settingsContent,
                    tocContent = {
                        EpubTocPanel(title, book.toc, tocPages, readingPage,
                            onSelect = { page -> navigateTo(page, animated = false); tocVisible = false })
                    },
                    insets = insets.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                    modifier = Modifier.graphicsLayer { alpha = if (systemCaptureInProgress) 0f else 1f },
                    controlsVisible = chromeVisible)
                }
                SnackbarHost(snackbarHost, Modifier.align(Alignment.BottomCenter)
                    .windowInsetsPadding(insets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                    .padding(bottom = footerClearance))
            }
        }
    }
}

@Composable
private fun EpubPageFooter(
    layout: EpubTextLayout?, index: Int, chromeProgress: Float, active: Boolean,
    modifier: Modifier = Modifier, onHeight: (Int) -> Unit,
) {
    val page = layout?.pages?.getOrNull(index)
    val chapterPages = layout?.sectionPages(index) ?: IntRange.EMPTY
    val chapterTitle = page?.slices?.firstOrNull()?.let { layout?.rows?.get(it.row)?.chapterTitle }.orEmpty()
    Column(
        modifier.padding(bottom = 10.dp).fillMaxWidth().onSizeChanged { onHeight(it.height) }
            .then(if (active) Modifier else Modifier.clearAndSetSemantics {}),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (page != null) "$chapterTitle · ${chapterPages.indexOf(index) + 1}/${chapterPages.count()}" else "章节 · 1/1",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = chromeProgress),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = if (chromeProgress > 0f) Modifier else Modifier.clearAndSetSemantics {},
        )
        Text(
            when {
                layout == null -> "正在分页…"
                layout.pages.isEmpty() -> "无可阅读页面"
                else -> "第 ${index + 1} / ${layout.pages.size} 页"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f + 0.28f * chromeProgress),
            modifier = if (active) Modifier.testTag("epub-page-number") else Modifier,
        )
    }
}

@Composable
private fun EpubPageView(
    layout: EpubTextLayout,
    index: Int,
    inset: Int,
    viewportHeight: Int,
    onTap: (EpubPageTap) -> Unit,
) {
    val page = layout.pages[index]
    val density = LocalDensity.current
    // Visible rows only; the layout re-measures any row evicted from the bounded cache.
    val pageLayouts = remember(layout, index) {
        page.slices.associate { it.row to layout.layoutFor(it.row) }
    }
    val shade = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val visibleText = remember(page) {
        AnnotatedString(page.slices.joinToString("\n") { slice ->
            val text = layout.rows[slice.row].text
            if (text.isEmpty()) ""
            else text.subSequence(slice.start.coerceIn(0, text.length), slice.end.coerceIn(0, text.length)).toString()
        })
    }
    val imagePaths = remember(page) { page.slices.mapNotNull { layout.rows[it.row].imagePath }.distinct() }
    val images by produceState(emptyMap<String, ImageBitmap>(), imagePaths) {
        value = buildMap {
            imagePaths.forEach { path ->
                val loaded = LocalImageLoader.loadFile(File(path))
                if (loaded is LocalImageState.Success) put(path, loaded.image)
            }
        }
    }
    val currentOnTap by rememberUpdatedState(onTap)
    val onlyImage = page.slices.size == 1 && layout.rows[page.slices.first().row].imagePath != null
    // Very short multi-window viewports may not fit even one line at accessibility sizes.
    // Keep that single-line page reachable vertically instead of clipping its glyphs.
    Box(
        Modifier.fillMaxSize()
            .pointerInput(index) {
                detectTapGestures { offset -> currentOnTap(epubPageTap(offset.x, size.width.toFloat())) }
            }
            .then(if (viewportHeight > 0) Modifier.verticalScroll(rememberScrollState(), enabled = page.height > viewportHeight + 2f) else Modifier),
    ) {
        Canvas(Modifier.fillMaxWidth().height(with(density) { maxOf(page.height, viewportHeight.toFloat()).toDp() })
            .semantics { text = visibleText }) {
            page.slices.forEach { slice ->
                val row = layout.rows[slice.row]
                val height = slice.sourceBottom - slice.sourceTop
                val pageTop = if (onlyImage) ((size.height - height) / 2f).coerceAtLeast(0f) else slice.pageTop
                val path = row.imagePath
                val bitmap = path?.let { images[it] }
                if (bitmap != null) {
                    val fitted = epubFitImage(bitmap.width, bitmap.height,
                        size.width.roundToInt(), height.roundToInt())
                    drawImage(
                        image = bitmap,
                        dstOffset = IntOffset(((size.width - fitted.width) / 2f).roundToInt(), pageTop.roundToInt()),
                        dstSize = fitted,
                    )
                } else {
                    if (row.shaded) drawRect(shade, Offset(0f, pageTop), Size(size.width, height))
                    clipRect(0f, pageTop, size.width, pageTop + height) {
                        drawText(pageLayouts.getValue(slice.row), topLeft = Offset(
                            if (row.shaded) inset.toFloat() else 0f, pageTop - slice.sourceTop))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EpubTocPanel(
    title: String,
    toc: List<EpubTocNode>,
    pages: Map<String, Int>,
    currentPage: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = remember(toc) {
        buildList<Pair<EpubTocNode, Int>> {
            fun visit(nodes: List<EpubTocNode>, depth: Int) {
                nodes.forEach { node -> add(node to depth); visit(node.children, depth + 1) }
            }
            visit(toc, 0)
        }
    }
    val activeKey = pages.filterValues { it <= currentPage }.maxByOrNull { it.value }?.key
    val activeIndex = entries.indexOfFirst { tocKey(it.first) == activeKey }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = activeIndex)
    LaunchedEffect(activeIndex) { listState.scrollToItem(activeIndex) }
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp).testTag("epub-toc-safe-content")) {
            Text(title, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("第 ${currentPage + 1} 页", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("epub-toc-list"), state = listState) {
                itemsIndexed(entries) { _, (node, depth) ->
                    val page = pages[tocKey(node)]
                    val selected = tocKey(node) == activeKey
                    TextButton(onClick = { page?.let(onSelect) }, enabled = page != null,
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                            contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.fillMaxWidth().background(
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent,
                            androidx.compose.foundation.shape.RoundedCornerShape(12.dp))) {
                        Text(node.title, modifier = Modifier.weight(1f).padding(start = (depth.coerceAtMost(6) * 12).dp),
                            style = MaterialTheme.typography.bodyLarge)
                        if (page != null) Text("${page + 1} 页", modifier = Modifier.padding(start = 12.dp),
                            style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (entries.isEmpty()) item { Text("无目录") }
            }
    }
}
