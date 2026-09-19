package com.m1racle.reader.ui.epub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.m1racle.reader.data.repository.DocumentRepository
import com.m1racle.reader.data.settings.ReaderSettingsRepository
import com.m1racle.reader.epub.EpubBlock
import com.m1racle.reader.epub.EpubBook
import com.m1racle.reader.epub.EpubTocNode
import com.m1racle.reader.ui.viewer.components.ReaderSettingsSheet
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

private sealed interface EpubRow {
    data class ChapterTitle(val text: String) : EpubRow
    data class Block(val block: EpubBlock) : EpubRow
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpubReaderScreen(
    documentId: String,
    repository: DocumentRepository,
    readerSettingsRepository: ReaderSettingsRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EpubReaderViewModel = viewModel(
        factory = EpubReaderViewModel.Factory(documentId, repository, readerSettingsRepository),
    ),
) {
    val uiState by viewModel.uiState.collectAsState()
    val settings by viewModel.readerSettings.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showSettings by remember { mutableStateOf(false) }

    when (val state = uiState) {
        is EpubUiState.Loading -> {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is EpubUiState.Error -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(state.displayName ?: "电子书") },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                            }
                        },
                    )
                },
            ) { padding ->
                Column(
                    Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                    Spacer(Modifier.height(12.dp))
                    Text(state.message, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onBack) { Text("返回") }
                }
            }
        }
        is EpubUiState.Success -> {
            val rows = remember(state.book) { flattenBook(state.book) }
            val chapterStartRow = remember(rows) {
                buildMap<Int, Int> {
                    var chapter = -1
                    rows.forEachIndexed { index, row ->
                        if (row is EpubRow.ChapterTitle) {
                            chapter++
                            putIfAbsent(chapter, index)
                        }
                    }
                }
            }
            val total = rows.size.coerceAtLeast(1)
            val restoredIndex = state.document.lastScrollIndex.coerceIn(0, total - 1)
            val listState = rememberLazyListState(
                initialFirstVisibleItemIndex = restoredIndex,
                initialFirstVisibleItemScrollOffset = state.document.lastScrollOffset.coerceAtLeast(0),
            )
            fun progressOf(index: Int): Float =
                if (total <= 1) 1f else (index.coerceIn(0, total - 1).toFloat() / (total - 1)).coerceIn(0f, 1f)

            fun flush() {
                viewModel.saveReadingPosition(
                    listState.firstVisibleItemIndex,
                    listState.firstVisibleItemScrollOffset,
                    progressOf(listState.firstVisibleItemIndex),
                )
            }

            LaunchedEffect(listState) {
                snapshotFlow { listState.isScrollInProgress }
                    .debounce(400)
                    .collectLatest { scrolling ->
                        if (!scrolling) flush()
                    }
            }
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP) flush()
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    flush()
                }
            }

            fun jumpTo(node: EpubTocNode) {
                val spine = node.spineIndex ?: return
                scope.launch {
                    drawerState.close()
                    val chapterRows = chapterStartRow[spine] ?: return@launch
                    val fragment = node.href.substringAfter("#", "")
                    val chapter = state.book.chapters.getOrNull(spine)
                    val anchorRow = if (fragment.isNotEmpty() && fragment != node.href) {
                        chapter?.idAnchors?.get(fragment)?.let { chapterRows + 1 + it }
                    } else {
                        null
                    }
                    listState.animateScrollToItem(anchorRow ?: chapterRows)
                }
            }

            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    EpubTocDrawer(
                        title = state.book.metadata.title ?: state.document.displayName,
                        toc = state.book.toc,
                        onSelect = ::jumpTo,
                        onClose = { scope.launch { drawerState.close() } },
                    )
                },
            ) {
                Scaffold(
                    modifier = modifier,
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    state.book.metadata.title ?: state.document.displayName,
                                    maxLines = 1,
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = onBack) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                                }
                            },
                            actions = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.FormatListBulleted,
                                        contentDescription = "目录",
                                    )
                                }
                                IconButton(onClick = { showSettings = true }) {
                                    Icon(Icons.Outlined.Settings, contentDescription = "设置")
                                }
                            },
                        )
                    },
                ) { padding ->
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = settings.contentPaddingLevel.horizontalDp,
                            vertical = 12.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        itemsIndexed(rows, key = { index, _ -> index }) { _, row ->
                            when (row) {
                                is EpubRow.ChapterTitle -> Text(
                                    row.text,
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontSize = 22.sp * settings.fontScale,
                                    ),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                                is EpubRow.Block -> EpubBlockView(
                                    row.block,
                                    fontScale = settings.fontScale,
                                    lineHeightScale = settings.lineHeightScale,
                                )
                            }
                        }
                    }
                }
            }
            if (showSettings) {
                ReaderSettingsSheet(
                    settings = settings,
                    onFontScale = viewModel::setFontScale,
                    onLineHeightScale = viewModel::setLineHeightScale,
                    onPaddingLevel = viewModel::setContentPaddingLevel,
                    onReset = viewModel::resetReaderSettings,
                    onDismiss = { showSettings = false },
                )
            }
        }
    }
}

private fun flattenBook(book: EpubBook): List<EpubRow> {
    val rows = mutableListOf<EpubRow>()
    book.readingOrder.forEach { chapter ->
        rows.add(EpubRow.ChapterTitle(chapter.title ?: "第 ${chapter.spineIndex + 1} 章"))
        chapter.blocks.forEach { rows.add(EpubRow.Block(it)) }
    }
    return rows
}

@Composable
private fun EpubTocDrawer(
    title: String,
    toc: List<EpubTocNode>,
    onSelect: (EpubTocNode) -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 2)
            TextButton(onClick = onClose) { Text("关闭") }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            toc.forEach { node ->
                item(key = "toc-${node.href}-${node.title}") {
                    TextButton(onClick = { onSelect(node) }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            node.title,
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                node.children.forEach { child ->
                    item(key = "toc-${child.href}-${child.title}") {
                        TextButton(onClick = { onSelect(child) }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "　　${child.title}",
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
            if (toc.isEmpty()) {
                item {
                    Text("无目录", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun EpubBlockView(block: EpubBlock, fontScale: Float, lineHeightScale: Float) {
    val bodySize = 16.sp * fontScale
    when (block) {
        is EpubBlock.Heading -> Text(
            block.text,
            style = when (block.level) {
                1 -> MaterialTheme.typography.headlineSmall
                2 -> MaterialTheme.typography.titleLarge
                else -> MaterialTheme.typography.titleMedium
            }.copy(fontSize = (20 - block.level.coerceIn(1, 4)).sp * fontScale),
            fontWeight = FontWeight.Bold,
        )
        is EpubBlock.Paragraph -> Text(
            buildAnnotatedString {
                block.inlines.forEach { inline ->
                    withStyle(
                        SpanStyle(
                            fontWeight = if (inline.bold) FontWeight.Bold else null,
                            fontStyle = if (inline.italic) FontStyle.Italic else null,
                            fontFamily = if (inline.code) FontFamily.Monospace else null,
                        ),
                    ) {
                        append(inline.text)
                    }
                }
            },
            fontSize = bodySize,
            lineHeight = bodySize * 1.6f * lineHeightScale,
        )
        is EpubBlock.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            block.items.forEachIndexed { index, inlines ->
                Row {
                    Text(
                        if (block.ordered) "${index + 1}. " else "• ",
                        fontSize = bodySize,
                    )
                    Text(
                        buildAnnotatedString {
                            inlines.forEach { inline ->
                                withStyle(
                                    SpanStyle(
                                        fontWeight = if (inline.bold) FontWeight.Bold else null,
                                        fontStyle = if (inline.italic) FontStyle.Italic else null,
                                    ),
                                ) {
                                    append(inline.text)
                                }
                            }
                        },
                        fontSize = bodySize,
                        lineHeight = bodySize * 1.6f * lineHeightScale,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        is EpubBlock.Quote -> Column(
            Modifier.background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                MaterialTheme.shapes.small,
            ).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            block.blocks.forEach { EpubBlockView(it, fontScale, lineHeightScale) }
        }
        is EpubBlock.Code -> Text(
            block.text,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp * fontScale,
            modifier = Modifier.background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                MaterialTheme.shapes.small,
            ).padding(12.dp).fillMaxWidth(),
        )
        is EpubBlock.Image -> Row(
            Modifier.fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    MaterialTheme.shapes.small,
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.BrokenImage, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                block.alt ?: "本书图片暂不支持显示",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        is EpubBlock.Divider -> HorizontalDivider(Modifier.padding(vertical = 8.dp))
        is EpubBlock.Unsupported -> Text(
            block.label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
