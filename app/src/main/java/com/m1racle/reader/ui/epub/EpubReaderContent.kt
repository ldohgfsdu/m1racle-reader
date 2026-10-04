package com.m1racle.reader.ui.epub

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
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
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

private val EpubPageTurnSpec = tween<Float>(
    durationMillis = 460,
    easing = CubicBezierEasing(0.22f, 0.0f, 0.18f, 1f),
)

private fun turnTo(pager: PagerState, page: Int, scope: CoroutineScope) {
    if (page !in 0 until pager.pageCount || page == pager.currentPage) return
    scope.launch { pager.animateScrollToPage(page, animationSpec = EpubPageTurnSpec) }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    insets: WindowInsets = WindowInsets.safeDrawing,
    restoreSession: EpubRestoreSession = remember(book, initial) {
        EpubRestoreSession(EpubReadingAnchors.restore(book, initial))
    },
) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
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
    // KOReader / Foliate / Apple Books / 微信读书: the book is the default view.
    var chromeVisible by rememberSaveable { mutableStateOf(false) }
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
    BackHandler(drawer.isOpen) { scope.launch { drawer.close() } }
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
    // Resolved once per layout; the drawer only does a map lookup per row.
    val tocPages = remember(ready) { ready?.let { epubTocPages(book, it) } ?: emptyMap() }
    val pager = rememberPagerState { (ready?.pages?.size ?: 1).coerceAtLeast(1) }
    var dragStartPage by remember(pager) { mutableStateOf<Int?>(null) }
    LaunchedEffect(pager) {
        pager.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) dragStartPage = pager.settledPage
        }
    }
    fun navigateTo(page: Int, animated: Boolean) {
        val currentLayout = ready ?: return
        if (page !in currentLayout.pages.indices) return
        // Even selecting the current TOC page is deliberate navigation.
        restoreSession.onUserNavigation()
        val position = currentLayout.positionAt(currentLayout.pages[page].slices.first())
        if (page == pager.settledPage) {
            anchorRow = position.first
            anchorText = position.third
            anchors?.anchorAt(anchorRow, anchorText)?.let {
                val progress = if (currentLayout.pages.size <= 1) 1f else page.toFloat() / currentLayout.pages.lastIndex
                currentPosition(EpubPosition(it, position.first, position.second, progress))
            }
        }
        if (animated) turnTo(pager, page, scope)
        else scope.launch { pager.scrollToPage(page) }
    }
    LaunchedEffect(pager, ready) {
        val pages = ready?.pages ?: return@LaunchedEffect
        val anchors = anchors ?: return@LaunchedEffect
        if (pages.isEmpty()) return@LaunchedEffect
        dragStartPage = null
        // Finish restoring against the new layout before observing/persisting
        // its page numbers. An old page number has no meaning after reflow.
        snapshotFlow { pager.isScrollInProgress }.first { !it }
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
        if (pager.currentPage != target) pager.scrollToPage(target)
        var lastSettled = target
        snapshotFlow { pager.settledPage }.collect { index ->
            val page = ready.pages.getOrNull(index) ?: return@collect
            val first = page.slices.first()
            val position = ready.positionAt(first)
            // Reflow can put the original character part way down a new page.
            // Keep that character until a real page turn; rounding to the new
            // page's first line here made repeated font changes drift backwards.
            if (index != lastSettled || anchorText < 0) {
                if (index != lastSettled && dragStartPage != null && dragStartPage != index) {
                    restoreSession.onUserNavigation()
                    dragStartPage = null
                }
                if (index != lastSettled) chromeVisible = false
                anchorRow = position.first
                anchorText = position.third
            }
            lastSettled = index
            val progress = if (ready.pages.size <= 1) 1f else index.toFloat() / ready.pages.lastIndex
            val anchor = anchors.anchorAt(anchorRow, anchorText)
                ?: anchors.anchorAt(position.first, position.third)
                ?: return@collect
            currentPosition(EpubPosition(anchor, position.first, position.second, progress,
                canSaveAnchor = restoreSession.anchorForSave(anchor) != null))
        }
    }
    ModalNavigationDrawer(
        modifier = modifier.fillMaxSize(),
        drawerState = drawer,
        // A rightward page swipe must never open the table of contents.
        gesturesEnabled = drawer.isOpen,
        drawerContent = {
            EpubTocDrawer(title, book.toc, tocPages,
                onSelect = { page -> navigateTo(page, animated = false); scope.launch { drawer.close() } },
                onClose = { scope.launch { drawer.close() } }, insets = insets)
        },
    ) {
        CompositionLocalProvider(
            LocalGlassEnvironment provides GlassEnvironment.reader(LocalReaderDarkTheme.current),
        ) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(insets).background(background)) {
                // Only paper and content are captured. Controls remain siblings
                // so their glass never samples itself or duplicates its icons.
                GlassBackdropSource(backdrop, Modifier.fillMaxSize().background(background)) {
                    Box(
                        Modifier.fillMaxSize()
                            .padding(horizontal = settings.contentPaddingLevel.horizontalDp)
                            .padding(top = topClearance + 12.dp, bottom = footerClearance)
                            .onSizeChanged { size ->
                                if (!epubViewportSettled(viewport, size)) viewport = size
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (ready == null) CircularProgressIndicator()
                        else if (ready.pages.isEmpty()) Text("本书没有可顺序阅读的章节")
                        else HorizontalPager(
                            state = pager,
                            beyondViewportPageCount = 1,
                            modifier = Modifier.fillMaxSize().testTag("epub-pager"),
                        ) { index ->
                            val pageOffset = pager.getOffsetDistanceInPages(index)
                            val fraction = pager.currentPageOffsetFraction
                            val scrollFromSettled = pager.currentPage - pager.settledPage + fraction
                            val turn = remember(index, pageOffset, fraction, pager.currentPage, pager.settledPage, viewport.width) {
                                epubPageTurnLayer(pageOffset, fraction, pager.currentPage, pager.settledPage, viewport.width.toFloat())
                            }
                            Box(
                                Modifier
                                    .zIndex(turn.zIndex)
                                    .graphicsLayer { translationX = turn.translationX }
                                    .fillMaxSize()
                                    .drawBehind {
                                        if (turn.edgeShadow > 0f) {
                                            // The shadow falls on the sheet underneath, outside this
                                            // page's edge. Painting it inside obscured the first glyphs.
                                            val shadowWidth = 10.dp.toPx()
                                            val forward = scrollFromSettled > 0f
                                            val left = if (forward) -shadowWidth else size.width
                                            drawRect(
                                                Brush.horizontalGradient(
                                                    if (forward) listOf(Color.Transparent, Color.Black.copy(alpha = 0.14f * turn.edgeShadow))
                                                    else listOf(Color.Black.copy(alpha = 0.14f * turn.edgeShadow), Color.Transparent),
                                                    startX = left, endX = left + shadowWidth,
                                                ),
                                                topLeft = Offset(left, 0f),
                                                size = Size(shadowWidth, size.height),
                                            )
                                        }
                                    }
                                    .background(MaterialTheme.colorScheme.background),
                            ) {
                                EpubPageView(ready, index, inset, viewport.height) { tap ->
                                    when (tap) {
                                        EpubPageTap.Previous -> {
                                            chromeVisible = false
                                            navigateTo(pager.currentPage - 1, animated = true)
                                        }
                                        EpubPageTap.Next -> {
                                            chromeVisible = false
                                            navigateTo(pager.currentPage + 1, animated = true)
                                        }
                                        EpubPageTap.ToggleChrome -> chromeVisible = !chromeVisible
                                    }
                                }
                            }
                        }
                    }
                    Column(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).fillMaxWidth()
                            .onSizeChanged { footerHeight = it.height },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        run {
                            val page = ready?.pages?.getOrNull(pager.settledPage)
                            val chapterPages = remember(ready, pager.settledPage) {
                                ready?.sectionPages(pager.settledPage) ?: IntRange.EMPTY
                            }
                            val chapterTitle = page?.slices?.firstOrNull()?.let { ready.rows[it.row].chapterTitle }.orEmpty()
                            val chapterPage = chapterPages.indexOf(pager.settledPage) + 1
                                Text(
                                    if (page != null) "$chapterTitle · $chapterPage/${chapterPages.count()}" else "章节 · 1/1",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (chromeVisible) 1f else 0f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = if (chromeVisible) Modifier else Modifier.clearAndSetSemantics {},
                                )
                        }
                        Text(
                            when {
                                ready == null -> "正在分页…"
                                ready.pages.isEmpty() -> "无可阅读页面"
                                else -> "第 ${pager.settledPage + 1} / ${ready.pages.size} 页"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (chromeVisible) 1f else 0.72f),
                            modifier = Modifier.testTag("epub-page-number"),
                        )
                    }
                }
                CompositionLocalProvider(LocalGlassBackdropHost provides glassHost) {
                    AnimatedVisibility(
                        visible = chromeVisible,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.TopCenter),
                    ) {
                        EpubReaderTopBar(title = title, onBack = onBack,
                            onToc = { scope.launch { drawer.open() } }, onSettings = onSettings)
                    }
                }
            }
        }
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
            .verticalScroll(rememberScrollState(), enabled = page.height > viewportHeight + 2f),
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
internal fun EpubTocDrawer(
    title: String,
    toc: List<EpubTocNode>,
    pages: Map<String, Int>,
    onSelect: (Int) -> Unit,
    onClose: () -> Unit,
    insets: WindowInsets = WindowInsets.safeDrawing,
) {
    val entries = remember(toc) {
        buildList<Pair<EpubTocNode, Int>> {
            fun visit(nodes: List<EpubTocNode>, depth: Int) {
                nodes.forEach { node -> add(node to depth); visit(node.children, depth + 1) }
            }
            visit(toc, 0)
        }
    }
    ModalDrawerSheet(windowInsets = insets, modifier = Modifier.fillMaxHeight().widthIn(max = 360.dp)) {
        Column(Modifier.fillMaxSize().padding(16.dp).testTag("epub-toc-safe-content")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = onClose) { Text("关闭") }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            LazyColumn(Modifier.weight(1f).testTag("epub-toc-list")) {
                itemsIndexed(entries) { _, (node, depth) ->
                    val page = pages[tocKey(node)]
                    TextButton(onClick = { page?.let(onSelect) }, enabled = page != null,
                        modifier = Modifier.fillMaxWidth()) {
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
}
