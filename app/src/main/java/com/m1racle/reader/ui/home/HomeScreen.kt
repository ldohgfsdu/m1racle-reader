package com.m1racle.reader.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.m1racle.reader.R
import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.markdown.DocumentListTitle
import com.m1racle.reader.ui.material.GlassBackdropHost
import com.m1racle.reader.ui.material.GlassBackdropSource
import com.m1racle.reader.ui.material.GlassMaterialEngine
import com.m1racle.reader.ui.material.GlassMaterialRole
import com.m1racle.reader.ui.material.LocalGlassBackdropHost
import com.m1racle.reader.ui.material.glassMaterial
import com.m1racle.reader.ui.material.glassPressedContentOverlayAlpha
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Home = plain, content-first file list. The unified glass engine is limited to
 * floating controls; document cards remain solid tonal content surfaces.
 *
 * - ONE LazyColumn owns the whole flow: brand header (墨阅 M1racleReader / subtitle /
 *   最近文件) is the first item and scrolls away with the cards — no fixed header,
 *   no clip boundary, no nested scrolling.
 * - Cards: solid tonal surface (no backdrop/refraction), compact 4-row layout.
 * - Overflow: bare ··· icon opening a compact engine-backed utility menu.
 * - FAB: engine-backed Control material.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenDocument: (document: DocumentEntity) -> Unit,
    onNavigateHub: (destination: com.m1racle.reader.ui.hub.HubDestination) -> Unit = {},
    onOpenSearch: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Scaffold FAB slot already insets 16dp from the screen edges;
    // these extras bring it to 20dp right margin / 20dp above safe area.
    val fabEndExtra = HomeDesignTokens.FabEdgePaddingExtra
    // H2.2: dock occupies the bottom center — FAB stacks above it.
    val dockStack = HomeDesignTokens.DockHeight + HomeDesignTokens.DockBottomGap
    val fabBottomInset = navBottom + HomeDesignTokens.FabEdgePaddingExtra + dockStack
    // Clears FAB AND the floating dock so content never hides behind either.
    val listBottomInset = navBottom + HomeDesignTokens.FabClearance + dockStack

    // Never inherit PDF immersive — always show status + nav on Home.
    val view = LocalView.current
    LaunchedEffect(Unit) {
        val activity = view.context as? android.app.Activity ?: return@LaunchedEffect
        val wic = WindowInsetsControllerCompat(activity.window, view)
        val all = androidx.core.view.WindowInsetsCompat.Type.systemBars()
        wic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        wic.show(all)
    }

    val listState = rememberLazyListState()

    // Shared backdrop host — ONLY floating controls (FAB / ··· menu) consume it;
    // list cards stay solid tonal (content-first, no glass waves).
    val rootBackdrop = rememberLayerBackdrop()
    val parentExport = rememberLayerBackdrop()
    val glassHost = remember(rootBackdrop, parentExport) {
        GlassBackdropHost(root = rootBackdrop, menuExport = parentExport)
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.onImportDocument(uri)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is HomeEvent.OpenDocument -> {
                    onOpenDocument(event.document)
                    return@collect
                }
                HomeEvent.UnsupportedFormat ->
                    context.getString(R.string.error_unsupported_format)
                HomeEvent.ReadFailed ->
                    context.getString(R.string.error_read_failed)
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    CompositionLocalProvider(LocalGlassBackdropHost provides glassHost) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            floatingActionButton = {
                // Unified Control recipe, rendered as a rounded square.
                GlassHomeFab(
                    onClick = {
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
                    contentDescription = stringResource(R.string.import_file),
                    modifier = Modifier.padding(
                        end = fabEndExtra,
                        bottom = fabBottomInset,
                    ),
                )
            },
        ) { scaffoldPadding ->
            val contentBottomInset = maxOf(
                listBottomInset,
                scaffoldPadding.calculateBottomPadding(),
            )
            // Ownership boundary: captured content cannot see its own host.
            // Sampling controls are sibling overlays after GlassBackdropSource.
            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                GlassBackdropSource(
                    backdrop = rootBackdrop,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (uiState.isEmpty) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            HomeHeaderSection(
                                statusTop = statusTop,
                                onOpenSearch = onOpenSearch,
                                // Empty state is a plain Column (no LazyColumn
                                // contentPadding) — keep the same 24dp rail.
                                modifier = Modifier.padding(
                                    horizontal = HomeDesignTokens.PageHorizontal,
                                ),
                            )
                            EmptyState(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(horizontal = 32.dp)
                                    .padding(bottom = contentBottomInset),
                            )
                        }
                    } else {
                        // Single flow: header item scrolls away with the cards — no
                        // fixed header, no nested/clipped boundary under 最近文件.
                        RecentDocumentList(
                            documents = uiState.documents,
                            continueReading = uiState.continueReading,
                            listState = listState,
                            statusTop = statusTop,
                            onOpen = onOpenDocument,
                            onDelete = viewModel::deleteDocument,
                            onOpenSearch = onOpenSearch,
                            contentBottomInset = contentBottomInset,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                // Sibling overlay: samples rootBackdrop without being captured by it.
                com.m1racle.reader.ui.hub.GlassDock(
                    onSelect = onNavigateHub,
                )
            }
        }
    }
}

/**
 * Brand header — plain page text, no plate, no Liquid. First item of the single
 * LazyColumn (or the fixed header of the empty state). Scrolls away with content.
 */
@Composable
private fun HomeHeaderSection(
    statusTop: Dp,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = statusTop + HomeDesignTokens.HeaderTopPad),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = HomeDesignTokens.AppNameSize,
                    lineHeight = HomeDesignTokens.AppNameLine,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = scheme.onSurface,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onOpenSearch) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.search_library_entry),
                )
            }
        }
        Spacer(modifier = Modifier.height(HomeDesignTokens.AppNameToSubtitle))
        Text(
            text = stringResource(R.string.app_subtitle),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = HomeDesignTokens.AppSubtitleSize,
                fontWeight = FontWeight.Normal,
            ),
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(HomeDesignTokens.SubtitleToSectionGap))
        Text(
            text = stringResource(R.string.recent_reads),
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = HomeDesignTokens.SectionTitleSize,
                fontWeight = FontWeight.Medium,
            ),
            color = scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
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
    continueReading: DocumentEntity?,
    listState: LazyListState,
    statusTop: Dp,
    onOpen: (DocumentEntity) -> Unit,
    onDelete: (String) -> Unit,
    onOpenSearch: () -> Unit,
    contentBottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    // Compose 1.8+: LocalOverscrollFactory (LocalOverscrollConfiguration is deprecated).
    CompositionLocalProvider(LocalOverscrollFactory provides null) {
        LazyColumn(
            state = listState,
            modifier = modifier,
            contentPadding = PaddingValues(
                start = HomeDesignTokens.PageHorizontal,
                end = HomeDesignTokens.PageHorizontal,
                bottom = contentBottomInset,
            ),
        ) {
            // Explicit per-item spacing: header → first card uses the tight
            // [HomeDesignTokens.HeaderToFirstCardGap]; cards use [HomeDesignTokens.CardGap].
            item(key = "home_brand_header") {
                HomeHeaderSection(
                    statusTop = statusTop,
                    onOpenSearch = onOpenSearch,
                    modifier = Modifier.padding(bottom = HomeDesignTokens.HeaderToFirstCardGap),
                )
            }
            // H2.2 Phase 1 继续阅读 hero（无阅读记录时优雅降级：整卡不渲染）。
            if (continueReading != null) {
                item(key = "home_continue_reading") {
                    ContinueReadingCard(
                        document = continueReading,
                        positionLine = readingPositionLine(continueReading),
                        progressPercent = progressPercent(continueReading),
                        onOpen = { onOpen(continueReading) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = HomeDesignTokens.CardGap),
                    )
                }
            }
            items(
                items = documents,
                // Stable identity — required for instant reflow after optimistic delete.
                key = { doc -> doc.id },
                contentType = { "home_doc_card" },
            ) { document ->
                DocumentCard(
                    document = document,
                    onOpen = { onOpen(document) },
                    onDelete = { onDelete(document.id) },
                    // No exit-placeholder animation — avoids empty slot residue.
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = HomeDesignTokens.CardGap),
                )
            }
        }
    }
}

/**
 * Stable tonal list card — solid surface, no liquid blur/refraction.
 * Dense scanning layout: title (≤2 lines) + type + meta + thin progress bar.
 * The ··· touch target overlays the top-right (end-padded text never collides),
 * so the invisible 48dp target does NOT inflate card height.
 */
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

    val titles = remember(
        document.id, document.title, document.displayName, document.uri, document.isPdf,
        document.isMarkdownPackage,
    ) { resolveDocumentCardTitles(document) }
    val meta = remember(
        document.id, document.lastOpenedAt, document.fileSize, document.readingProgress,
        document.isPdf, document.pageCount, document.lastScrollIndex,
    ) { formatMeta(document) }
    val spokenText = remember(titles.displayTitle, titles.typeLabel, meta) {
        listOfNotNull(titles.displayTitle, titles.typeLabel, meta).joinToString(", ")
    }

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
        shape = HomeDesignTokens.CardShape,
        color = scheme.surfaceContainerLow,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.45f)),
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = HomeDesignTokens.CardPaddingH,
                    end = HomeDesignTokens.CardPaddingH,
                    top = HomeDesignTokens.CardPaddingTop,
                    bottom = HomeDesignTokens.CardPaddingBottom,
                ),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                // Text block — end-padded by the full touch width so the ···
                // overlay never collides with title/type text.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = HomeDesignTokens.OverflowTouch),
                ) {
                    // Title: first visual level, ≤2 lines.
                    Text(
                        text = titles.displayTitle,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = HomeDesignTokens.FileTitleSize,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = HomeDesignTokens.FileTitleLine,
                        ),
                        color = scheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(modifier = Modifier.height(HomeDesignTokens.TitleToTypeGap))
                    // Type label (PDF / MD) — secondary, not loud.
                    Text(
                        text = titles.typeLabel,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = HomeDesignTokens.FileTypeSize,
                            lineHeight = HomeDesignTokens.FileTypeLine,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = scheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(modifier = Modifier.height(HomeDesignTokens.TypeToMetaGap))
                    // 时间 · 大小 · 进度 (unified order for every item).
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = HomeDesignTokens.MetaSize,
                            lineHeight = HomeDesignTokens.MetaLine,
                            fontWeight = FontWeight.Normal,
                        ),
                        color = scheme.onSurfaceVariant.copy(alpha = 0.70f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(modifier = Modifier.height(HomeDesignTokens.MetaToProgressGap))
                    ReadingProgressBar(
                        progress = if (hasProgress) progress else 0f,
                        activeColor = scheme.primary,
                        trackColor = if (hasProgress) {
                            scheme.outlineVariant
                        } else {
                            scheme.outlineVariant.copy(alpha = 0.40f)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // Bare ··· overlay — invisible touch target, no surface.
                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                    OverflowMenuButton(
                        onClick = { menuExpanded = true },
                        contentDescription = stringResource(R.string.delete_record),
                    )
                    DeleteRecordMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        onDelete = onDelete,
                    )
                }
            }
        }
    }
}

/**
 * Home import FAB — unified Control material, shared with compact reader chrome:
 * surface alpha / blur / lens / highlight / shadow / pressed boost).
 * Rounded square, light liquid surface + dark plus in light theme.
 */
@Composable
private fun GlassHomeFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .size(HomeDesignTokens.FabSize)
            .glassMaterial(
                role = GlassMaterialRole.Control,
                shape = HomeDesignTokens.FabShape,
                pressed = pressed,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(HomeDesignTokens.FabIcon),
        )
    }
}

/**
 * Bare overflow ··· icon — no circular surface, no liquid backdrop, no
 * highlight, no shadow. 48dp invisible touch target with only the ~22dp glyph
 * visible, overlaid at the card top-right (never inflates card height).
 */
@Composable
private fun OverflowMenuButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Icon(
        imageVector = Icons.Default.MoreVert,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        modifier = modifier
            .size(HomeDesignTokens.OverflowTouch)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(HomeDesignTokens.OverflowIconPad),
    )
}

/**
 * Delete utility menu — unified Menu material. Compact
 * single-row rectangle, right-edge aligned to the ··· anchor (expands
 * leftward). A [Popup] is a separate window, so backdrop sampling across
 * windows is unsafe, so the engine renders the same recipe as a soft plate.
 */
@Composable
private fun DeleteRecordMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!expanded) return
    val density = LocalDensity.current
    val offsetX = with(density) {
        -HomeDesignTokens.MenuAnchorOverhang.toPx()
    }.roundToInt()
    val offsetY = with(density) { HomeDesignTokens.OverflowTouch.toPx() }.roundToInt()
    Popup(
        onDismissRequest = onDismissRequest,
        offset = IntOffset(x = offsetX, y = offsetY),
        properties = PopupProperties(focusable = true),
    ) {
        val scheme = MaterialTheme.colorScheme
        Column(
            modifier = modifier
                .widthIn(
                    min = HomeDesignTokens.MenuMinWidth,
                    max = HomeDesignTokens.MenuMaxWidth,
                )
                .glassMaterial(
                    role = GlassMaterialRole.Menu,
                    shape = HomeDesignTokens.MenuShape,
                    sampleBackdrop = false,
                ),
        ) {
            val rowInteraction = remember { MutableInteractionSource() }
            val rowPressed by rowInteraction.collectIsPressedAsState()
            val pressedOverlay = glassPressedContentOverlayAlpha(GlassMaterialRole.Menu)
            val rowHighlight by animateFloatAsState(
                targetValue = if (rowPressed) pressedOverlay else 0f,
                animationSpec = GlassMaterialEngine.PressSpring,
                label = "homeMenuRowPress",
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HomeDesignTokens.MenuRowHeight)
                    .background(
                        color = scheme.onSurface.copy(alpha = rowHighlight),
                        shape = RoundedCornerShape(10.dp),
                    )
                    .clickable(
                        interactionSource = rowInteraction,
                        indication = null,
                        onClick = {
                            onDismissRequest()
                            onDelete()
                        },
                    )
                    .padding(horizontal = HomeDesignTokens.MenuRowPadH),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant.copy(alpha = 0.75f),
                    modifier = Modifier.size(HomeDesignTokens.MenuItemIcon),
                )
                Spacer(modifier = Modifier.width(HomeDesignTokens.MenuIconGap))
                Text(
                    text = stringResource(R.string.delete_record),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun ReadingProgressBar(
    progress: Float,
    activeColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
) {
    val fraction = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .height(HomeDesignTokens.ProgressHeight)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
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
            displayTitle = fullFileName,
            typeLabel = "PDF",
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

/** Unified meta: 时间 · 大小 · 进度. */
internal fun formatMeta(document: DocumentEntity): String {
    val time = formatRelativeOpenedAt(document.lastOpenedAt)
    val size = formatFileSize(document.fileSize)
    val progressLabel = if (document.isPdf) {
        formatPdfProgress(document)
    } else {
        formatReadingProgress(document.readingProgress)
    }
    return listOf(time, size, progressLabel).joinToString(" · ")
}

internal fun formatPdfProgress(document: DocumentEntity): String {
    val pageCount = document.pageCount
    val pageIndex = document.lastScrollIndex.coerceAtLeast(0)
    if (pageCount > 0) {
        val human = (pageIndex + 1).coerceIn(1, pageCount)
        return "第 $human / $pageCount 页"
    }
    return formatReadingProgress(document.readingProgress)
}

private fun formatRelativeOpenedAt(epochMs: Long): String {
    val locale = Locale.getDefault()
    val nowMs = System.currentTimeMillis()
    val deltaMs = (nowMs - epochMs).coerceAtLeast(0L)
    if (deltaMs < TimeUnit.MINUTES.toMillis(2)) return "刚刚"

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

internal fun formatReadingProgress(progress: Float): String {
    val percent = (progress.coerceIn(0f, 1f) * 100f).toInt()
    return when {
        percent <= 0 -> "未读"
        percent >= 99 -> "已读完"
        else -> "已读 $percent%"
    }
}

internal fun formatFileSize(bytes: Long?): String {
    if (bytes == null || bytes < 0) return "未知大小"
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) {
        val rounded = if (kb < 10) {
            String.format(Locale.getDefault(), "%.1f", kb)
        } else {
            String.format(Locale.getDefault(), "%.0f", kb)
        }
        return "$rounded KB"
    }
    val mb = kb / 1024.0
    return String.format(Locale.getDefault(), "%.1f MB", mb)
}
