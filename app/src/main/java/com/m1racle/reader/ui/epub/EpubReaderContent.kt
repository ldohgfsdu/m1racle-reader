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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
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
import com.m1racle.reader.image.LocalImageLoader
import com.m1racle.reader.image.LocalImageState
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.ceil
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
    initialRow: Int,
    initialOffset: Int,
    onPosition: (Int, Int, Float) -> Unit,
    onFlush: () -> Unit,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    insets: WindowInsets = WindowInsets.safeDrawing,
) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
    val rows = remember(book, settings, style) { epubTextRows(book, settings, style) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    // KOReader / Foliate / Apple Books / 微信读书: the book is the default view.
    var chromeVisible by rememberSaveable { mutableStateOf(false) }
    // Text offsets survive a settings/viewport reflow. Persisted data remains row + pixel offset.
    var anchorRow by rememberSaveable { mutableIntStateOf(initialRow) }
    var anchorText by rememberSaveable { mutableIntStateOf(-1) }
    val gap = with(density) { 10.dp.toPx() }
    val inset = with(density) { 12.dp.roundToPx() }
    val layout by produceState<EpubTextLayout?>(null, rows, viewport, measurer, density) {
        if (viewport.width > 0 && viewport.height > 0) {
            value = measureEpub(rows, measurer, viewport.width, viewport.height, gap, inset)
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
    val pager = rememberPagerState { (ready?.pages?.size ?: 1).coerceAtLeast(1) }
    LaunchedEffect(ready) {
        val pages = ready?.pages ?: return@LaunchedEffect
        if (pager.isScrollInProgress) return@LaunchedEffect
        val page = if (anchorText >= 0) pages.pageForText(anchorRow, anchorText)
        else pages.pageForPosition(anchorRow, initialOffset)
        val target = page.coerceIn(0, pages.lastIndex.coerceAtLeast(0))
        if (pager.currentPage != target) pager.scrollToPage(target)
    }
    LaunchedEffect(pager, ready) {
        if (ready == null) return@LaunchedEffect
        var lastSettled = pager.settledPage
        snapshotFlow { pager.settledPage }.collect { index ->
            if (index != lastSettled) chromeVisible = false
            lastSettled = index
            val page = ready.pages.getOrNull(index) ?: return@collect
            val first = page.slices.first()
            anchorRow = first.row
            anchorText = first.start
            val progress = if (ready.pages.size <= 1) 1f else index.toFloat() / ready.pages.lastIndex
            currentPosition(first.row, ceil(first.sourceTop).toInt(), progress)
        }
    }
    ModalNavigationDrawer(
        modifier = modifier.fillMaxSize(),
        drawerState = drawer,
        // A rightward page swipe must never open the table of contents.
        gesturesEnabled = drawer.isOpen,
        drawerContent = {
            EpubTocDrawer(title, book.toc, ready?.let { measured -> { node: EpubTocNode -> measured.pageForToc(book, node) } },
                onSelect = { page -> scope.launch { pager.scrollToPage(page); drawer.close() } },
                onClose = { scope.launch { drawer.close() } }, insets = insets)
        },
    ) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(insets)) {
            Box(
                Modifier.fillMaxSize()
                    .padding(horizontal = settings.contentPaddingLevel.horizontalDp, vertical = 12.dp)
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
                    val turn = epubPageTurnLayer(pageOffset, fraction, pager.currentPage, pager.settledPage, 1f)
                    Box(
                        Modifier
                            .zIndex(turn.zIndex)
                            .graphicsLayer {
                                translationX = epubPageTurnLayer(pageOffset, fraction, pager.currentPage, pager.settledPage, size.width).translationX
                            }
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        EpubPageView(ready, index, inset, viewport.height) { tap ->
                            when (tap) {
                                EpubPageTap.Previous -> {
                                    chromeVisible = false
                                    turnTo(pager, pager.currentPage - 1, scope)
                                }
                                EpubPageTap.Next -> {
                                    chromeVisible = false
                                    turnTo(pager, pager.currentPage + 1, scope)
                                }
                                EpubPageTap.ToggleChrome -> chromeVisible = !chromeVisible
                            }
                        }
                        if (turn.dim > 0f) {
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = turn.dim)))
                        }
                        if (turn.edgeShadow > 0f) {
                            val goingForward = scrollFromSettled >= 0f
                            Box(
                                Modifier
                                    .align(if (goingForward) Alignment.CenterStart else Alignment.CenterEnd)
                                    .fillMaxHeight()
                                    .width(18.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = if (goingForward) {
                                                listOf(Color.Black.copy(alpha = 0.22f * turn.edgeShadow), Color.Transparent)
                                            } else {
                                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.22f * turn.edgeShadow))
                                            },
                                        ),
                                    ),
                            )
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = chromeVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                TopAppBar(
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                    },
                    actions = {
                        IconButton(onClick = { scope.launch { drawer.open() } }) {
                            Icon(Icons.AutoMirrored.Filled.FormatListBulleted, "目录")
                        }
                        IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "设置") }
                    },
                )
            }
            Column(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (chromeVisible && ready != null) {
                    val page = ready.pages.getOrNull(pager.currentPage)
                    val chapterPages = ready.pages.indices.filter { ready.pages[it].spineIndex == page?.spineIndex }
                    val chapterTitle = page?.slices?.firstOrNull()?.let { ready.rows[it.row].chapterTitle }.orEmpty()
                    val chapterPage = chapterPages.indexOf(pager.currentPage) + 1
                    if (page != null) {
                        Text(
                            "$chapterTitle · $chapterPage/${chapterPages.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    when {
                        ready == null -> "正在分页…"
                        ready.pages.isEmpty() -> "无可阅读页面"
                        else -> "第 ${pager.currentPage + 1} / ${ready.pages.size} 页"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (chromeVisible) 1f else 0.72f),
                    modifier = Modifier.testTag("epub-page-number"),
                )
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
                        drawText(layout.layouts[slice.row], topLeft = Offset(
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
    pageForNode: ((EpubTocNode) -> Int?)?,
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
                    val page = pageForNode?.invoke(node)
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
