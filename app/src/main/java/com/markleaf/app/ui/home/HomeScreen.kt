package com.markleaf.app.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.markleaf.app.R
import com.markleaf.app.data.local.DocumentEntity
import com.markleaf.app.markdown.DocumentListTitle
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

private val HomeCardShape = RoundedCornerShape(16.dp)
private val FabShape = RoundedCornerShape(16.dp)
private val ProgressBarHeight = 4.dp
private val ProgressAreaTopGap = 6.dp
/** Fixed subtitle row height so cards with/without supporting text match. */
private val SupportingLineHeight = 16.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenDocument: (document: DocumentEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.onImportDocument(uri)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                HomeEvent.UnsupportedFormat ->
                    context.getString(R.string.error_unsupported_format)
                HomeEvent.ReadFailed ->
                    context.getString(R.string.error_read_failed)
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.app_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    // SAF: Markdown text + PDF; business layer validates extension/mime.
                    openDocumentLauncher.launch(
                        arrayOf(
                            "text/*",
                            "application/pdf",
                            "application/zip",
                            "application/x-zip-compressed",
                            "application/octet-stream",
                        ),
                    )
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 2.dp,
                    pressedElevation = 3.dp,
                    focusedElevation = 2.dp,
                    hoveredElevation = 3.dp,
                ),
                shape = FabShape,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.import_file),
                )
            }
        },
    ) { innerPadding ->
        if (uiState.isEmpty) {
            EmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        } else {
            RecentDocumentList(
                documents = uiState.documents,
                onOpen = onOpenDocument,
                onDelete = viewModel::deleteDocument,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        }
    }
}

@Composable
private fun EmptyState(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 16.dp),
            )
            Text(
                text = stringResource(R.string.empty_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentDocumentList(
    documents: List<DocumentEntity>,
    onOpen: (DocumentEntity) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Short recent lists spend most drag time near edges; stretch overscroll reads as sticky.
    CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 88.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(contentType = "home_header") {
                Text(
                    text = stringResource(R.string.recent_reads),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp, start = 4.dp),
                )
            }
            items(
                items = documents,
                key = { it.id },
                contentType = { "home_doc_card" },
            ) { document ->
                DocumentCard(
                    document = document,
                    onOpen = { onOpen(document) },
                    onDelete = { onDelete(document.id) },
                )
            }
        }
    }
}

@Composable
private fun DocumentCard(
    document: DocumentEntity,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val progress = document.readingProgress.coerceIn(0f, 1f)
    val hasProgress = progress > 0f

    val scheme = MaterialTheme.colorScheme
    // surfaceContainerLow lifts the card above background in both themes.
    val cardBg = scheme.surfaceContainerLow
    val cardBorder = scheme.outline
    val titleColor = scheme.onSurface
    val metaColor = scheme.onSurfaceVariant
    val progressActive = scheme.primary
    val progressTrack = scheme.outlineVariant
    // Unread: same footprint, faint track so height matches started cards.
    val progressTrackUnread = scheme.outlineVariant.copy(alpha = 0.45f)

    // Pure function of Room fields only — no file IO / re-parse on recompose.
    val titles = remember(
        document.id,
        document.title,
        document.displayName,
        document.uri,
    ) {
        resolveDocumentCardTitles(document)
    }
    val meta = remember(
        document.id,
        document.lastOpenedAt,
        document.fileSize,
        document.readingProgress,
        document.isPdf,
        document.pageCount,
        document.lastScrollIndex,
    ) {
        formatMeta(document)
    }

    // Combined a11y for the three visible lines (menu stays a separate node).
    val spokenText = remember(titles.displayTitle, titles.supportingText, meta) {
        listOfNotNull(titles.displayTitle, titles.supportingText, meta).joinToString(", ")
    }

    // Surface + null indication: avoids Material Card press/ripple fighting the finger
    // (human sticky FAIL on canvas-draw candidate; feel-v2 accepted with this shell).
    val cardInteraction = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = spokenText }
            .clickable(
                interactionSource = cardInteraction,
                indication = null,
                onClick = onOpen,
            ),
        shape = HomeCardShape,
        color = cardBg,
        border = BorderStroke(1.dp, cardBorder),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            // Restored three independent Text lines (title / supporting / meta).
            // feel-v2 single AnnotatedString + softWrap=false clipped/hid card content.
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(end = 40.dp),
                ) {
                    Text(
                        text = titles.displayTitle,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 24.sp,
                        ),
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(SupportingLineHeight),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (titles.supportingText != null) {
                            Text(
                                text = titles.supportingText,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal,
                                    lineHeight = 16.sp,
                                ),
                                color = metaColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                    val menuInteraction = remember { MutableInteractionSource() }
                    val menuDesc = stringResource(R.string.delete_record)
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = menuDesc,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(40.dp)
                            .clickable(
                                interactionSource = menuInteraction,
                                indication = null,
                                onClick = { menuExpanded = true },
                            )
                            .padding(8.dp),
                    )
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete_record)) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                )
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = meta,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                ),
                color = metaColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Fixed progress footprint so all cards share the same height.
            Spacer(modifier = Modifier.height(ProgressAreaTopGap))
            ReadingProgressBar(
                progress = if (hasProgress) progress else 0f,
                activeColor = progressActive,
                trackColor = if (hasProgress) progressTrack else progressTrackUnread,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Simple determinate bar: one draw pass for track + fill (no nested clip children).
 * Avoids Material3 [LinearProgressIndicator] stop indicator (endpoint dot).
 */
@Composable
private fun ReadingProgressBar(
    progress: Float,
    activeColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
) {
    val fraction = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .height(ProgressBarHeight)
            .drawBehind {
                val radius = CornerRadius(size.height / 2f, size.height / 2f)
                drawRoundRect(color = trackColor, cornerRadius = radius)
                if (fraction > 0f) {
                    drawRoundRect(
                        color = activeColor,
                        size = Size(width = size.width * fraction, height = size.height),
                        cornerRadius = radius,
                    )
                }
            },
    )
}

/**
 * Card title pair from Room fields only (no file IO / re-parse on recompose).
 * [DocumentEntity.title] = first H1 source text cached at open via updateParseMetadata.
 * List displayTitle runs through [DocumentListTitle.normalizeListDisplayTitle]
 * (inline → plain, leading emoji strip). Reader H1 is unchanged.
 */
private data class DocumentCardTitles(
    val displayTitle: String,
    val supportingText: String?,
)

private fun resolveDocumentCardTitles(document: DocumentEntity): DocumentCardTitles {
    val fullFileName = fullFileNameOf(document)
    // PDF: no Markdown H1; show file name as title.
    if (document.isPdf) {
        return DocumentCardTitles(
            displayTitle = fullFileName,
            supportingText = "PDF",
        )
    }
    if (document.isMarkdownPackage) {
        val displayTitle = DocumentListTitle.normalizeListDisplayTitle(
            extractedTitle = document.title,
            fileName = fullFileName,
        )
        return DocumentCardTitles(
            displayTitle = displayTitle,
            supportingText = when {
                fullFileName.isNotBlank() &&
                    fullFileName != "未命名文档" &&
                    fullFileName != displayTitle -> fullFileName
                else -> "Markdown 教材包"
            },
        )
    }
    val displayTitle = DocumentListTitle.normalizeListDisplayTitle(
        extractedTitle = document.title,
        fileName = fullFileName,
    )
    val usedDocumentTitle = DocumentListTitle.hasUsableDocumentTitle(
        extractedTitle = document.title,
        fileName = fullFileName,
    )
    return if (usedDocumentTitle) {
        DocumentCardTitles(
            displayTitle = displayTitle,
            supportingText = fullFileName,
        )
    } else {
        DocumentCardTitles(
            displayTitle = displayTitle,
            supportingText = meaningfulParentLabel(document.uri),
        )
    }
}

/** Full file name from displayName, falling back to URI last segment. */
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

/**
 * Optional parent folder label for supporting line when there is no H1.
 * Skips generic SAF / storage noise; returns null when nothing meaningful.
 */
private fun meaningfulParentLabel(uri: String): String? {
    val decoded = try {
        java.net.URLDecoder.decode(uri, Charsets.UTF_8.name())
    } catch (_: Exception) {
        uri
    }
    val path = decoded.substringBefore('?').substringBefore('#')
    val parts = path.split('/').filter { it.isNotBlank() }
    if (parts.size < 2) return null
    val parent = parts[parts.lastIndex - 1]
    val skip = setOf(
        "document", "tree", "primary", "storage", "emulated", "0",
        "Download", "Downloads", "download", "downloads",
        "Documents", "documents", "file", "content",
    )
    if (parent in skip) return null
    if (parent.contains(':')) return null
    if (parent.length > 48) return null
    if (parent.matches(Regex("""^[0-9a-fA-F-]{16,}$"""))) return null
    // Must have some readable content.
    if (!parent.any { it.isLetterOrDigit() || it in '\u4e00'..'\u9fff' }) return null
    return parent
}

private fun formatMeta(document: DocumentEntity): String {
    val time = formatRelativeOpenedAt(document.lastOpenedAt)
    val size = formatFileSize(document.fileSize)
    val progressLabel = if (document.isPdf) {
        formatPdfProgress(document)
    } else {
        formatReadingProgress(document.readingProgress)
    }
    return listOf(time, size, progressLabel).joinToString(" · ")
}

/** PDF: "第 n 页" when pageCount known, else percent / 未读. */
private fun formatPdfProgress(document: DocumentEntity): String {
    val pageCount = document.pageCount
    val pageIndex = document.lastScrollIndex.coerceAtLeast(0)
    if (pageCount > 0) {
        val human = (pageIndex + 1).coerceIn(1, pageCount)
        return "第 $human / $pageCount 页"
    }
    return formatReadingProgress(document.readingProgress)
}

/**
 * 刚刚 / 今天 HH:mm / 昨天 HH:mm / 同年 M月d日 / 跨年 yyyy年M月d日
 */
private fun formatRelativeOpenedAt(epochMs: Long): String {
    val locale = Locale.getDefault()
    val nowMs = System.currentTimeMillis()
    val deltaMs = (nowMs - epochMs).coerceAtLeast(0L)
    if (deltaMs < TimeUnit.MINUTES.toMillis(2)) {
        return "刚刚"
    }

    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = epochMs }

    val startOfToday = (now.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val startOfThatDay = (then.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val dayDiff = TimeUnit.MILLISECONDS.toDays(
        startOfToday.timeInMillis - startOfThatDay.timeInMillis,
    )
    val hhmm = String.format(
        locale,
        "%02d:%02d",
        then.get(Calendar.HOUR_OF_DAY),
        then.get(Calendar.MINUTE),
    )
    return when {
        dayDiff == 0L -> "今天 $hhmm"
        dayDiff == 1L -> "昨天 $hhmm"
        then.get(Calendar.YEAR) == now.get(Calendar.YEAR) ->
            String.format(
                locale,
                "%d月%d日",
                then.get(Calendar.MONTH) + 1,
                then.get(Calendar.DAY_OF_MONTH),
            )
        else ->
            String.format(
                locale,
                "%d年%d月%d日",
                then.get(Calendar.YEAR),
                then.get(Calendar.MONTH) + 1,
                then.get(Calendar.DAY_OF_MONTH),
            )
    }
}

private fun formatReadingProgress(progress: Float): String {
    val percent = (progress.coerceIn(0f, 1f) * 100f).toInt()
    return when {
        percent <= 0 -> "未读"
        percent >= 99 -> "已读完"
        else -> "已读 $percent%"
    }
}

private fun formatFileSize(bytes: Long?): String {
    // Keep literal in sync with R.string.unknown_size
    if (bytes == null || bytes < 0) return "未知大小"
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) {
        val rounded = if (kb < 10) String.format(Locale.getDefault(), "%.1f", kb)
        else String.format(Locale.getDefault(), "%.0f", kb)
        return "$rounded KB"
    }
    val mb = kb / 1024.0
    return String.format(Locale.getDefault(), "%.1f MB", mb)
}
