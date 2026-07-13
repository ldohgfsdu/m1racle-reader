package com.markleaf.app.ui.pdf

import android.util.Log
import android.content.pm.ApplicationInfo

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.markleaf.app.R
import com.markleaf.app.pdf.PdfReadingMode
import com.markleaf.app.pdf.PdfRenderQuality
import com.markleaf.app.pdf.HorizontalPanState
import com.markleaf.app.pdf.HorizontalZoomPanPhysics
import com.markleaf.app.pdf.ContinuousScrollDirection
import com.markleaf.app.pdf.ContinuousScrollPhase
import com.markleaf.app.pdf.ContinuousViewportSnapshot
import com.markleaf.app.pdf.OverlayOffset
import com.markleaf.app.pdf.OverlayRect
import com.markleaf.app.pdf.VisiblePageTarget
import com.markleaf.app.pdf.ZoomPageOverlayMath
import com.markleaf.app.pdf.TwoFingerGestureArbitration
import com.markleaf.app.pdf.TwoFingerGesturePhase
import com.markleaf.app.pdf.PageTap
import com.markleaf.app.pdf.ContinuousZoomSession
import com.markleaf.app.pdf.ContinuousZoomSessionReducer
import com.markleaf.app.pdf.export.ChapterExportState
import com.markleaf.app.ui.theme.MarkLeafTheme
import com.markleaf.app.ui.theme.dialogContainer
import com.markleaf.app.ui.theme.markLeafOutlinedFieldColors
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/**
 * PDF reader: jump / scrubber / print anchors (P0.2A) + on-demand thumbnails (P0.2B)
 * + vertical continuous / horizontal paged reading modes.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PdfViewerScreen(
    viewModel: PdfViewerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val embeddedOffer by viewModel.embeddedOutlineOffer.collectAsStateWithLifecycle()
    val importMessage by viewModel.importMessage.collectAsStateWithLifecycle()
    val exportPlan by viewModel.exportPlan.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showJumpDialog by remember { mutableStateOf(false) }
    var showPrintDialog by remember { mutableStateOf(false) }
    var showThumbSheet by remember { mutableStateOf(false) }
    var showOutlineSheet by remember { mutableStateOf(false) }
    var showReadingModeSheet by remember { mutableStateOf(false) }
    var thumbRangePick by remember { mutableStateOf(false) }
    var outlineEditor by remember { mutableStateOf<OutlineEditorState?>(null) }
    var pendingExportMime by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val createZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            viewModel.runExportToUri(uri)
        }
        // User cancel of SAF is not an error
    }

    LaunchedEffect(importMessage) {
        val msg = importMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeImportMessage()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(viewModel, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.flushProgress()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.flushProgress()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
    when (val state = uiState) {
        PdfViewerUiState.Loading -> {
            PdfChrome(
                title = stringResource(R.string.viewer_loading),
                pageLabel = null,
                printLabel = null,
                outlinePathLabel = null,
                onBack = onBack,
                canPrevious = false,
                canNext = false,
                onPrevious = {},
                onNext = {},
                onPageLabelClick = null,
                onSetPrintClick = null,
                onThumbsClick = null,
                onOutlineClick = null,
                scrubber = null,
                modifier = Modifier.fillMaxSize(),
                content = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.pdf_loading),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        }

        is PdfViewerUiState.Error -> {
            PdfChrome(
                title = state.displayName ?: stringResource(R.string.viewer_title_fallback),
                pageLabel = null,
                printLabel = null,
                outlinePathLabel = null,
                onBack = onBack,
                canPrevious = false,
                canNext = false,
                onPrevious = {},
                onNext = {},
                onPageLabelClick = null,
                onSetPrintClick = null,
                onThumbsClick = null,
                onOutlineClick = null,
                scrubber = null,
                modifier = Modifier.fillMaxSize(),
                content = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(onClick = viewModel::retry) {
                                Text(text = stringResource(R.string.pdf_retry))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = onBack) {
                                Text(text = stringResource(R.string.back_to_home))
                            }
                        }
                    }
                },
            )
        }

        is PdfViewerUiState.Ready -> {
            val printNum = state.printPageNumber
            val printLabel = when {
                printNum == null -> stringResource(R.string.pdf_print_unset)
                state.isPrintExactAnchor ->
                    stringResource(R.string.pdf_print_exact, printNum)
                else ->
                    stringResource(R.string.pdf_print_inferred, printNum)
            }

            val outlinePath = state.outlinePathLabel.takeIf { it.isNotBlank() }
            val isVertical = state.readingMode == PdfReadingMode.VERTICAL_CONTINUOUS
            val pageJump by viewModel.pageJump.collectAsStateWithLifecycle()
            // R6: TopBar is stable and always present. Horizontal controls are viewport overlays.
            var horizontalControlsVisible by remember(state.readingMode) { mutableStateOf(true) }

            PdfChrome(
                title = state.document.displayName,
                pageLabel = state.pageLabel,
                printLabel = printLabel,
                outlinePathLabel = outlinePath,
                onBack = onBack,
                canPrevious = state.canGoPrevious,
                canNext = state.canGoNext,
                // Bottom flip chrome removed from fixed bar — horizontal uses overlay.
                showPageButtons = false,
                topBarVisible = true,
                onPrevious = viewModel::goToPreviousPage,
                onNext = viewModel::goToNextPage,
                onPageLabelClick = { showJumpDialog = true },
                onSetPrintClick = { showPrintDialog = true },
                onThumbsClick = {
                    thumbRangePick = false
                    viewModel.prepareThumbnails()
                    showThumbSheet = true
                },
                onOutlineClick = { showOutlineSheet = true },
                onReadingModeClick = { showReadingModeSheet = true },
                scrubber = if (isVertical) {
                    {
                        // Read-only 2dp progress — not a page slider.
                        val progress = if (state.pageCount > 1) {
                            state.displayPageIndex.toFloat() / (state.pageCount - 1).toFloat()
                        } else {
                            0f
                        }
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp),
                            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        )
                    }
                } else {
                    null
                },
                modifier = Modifier.fillMaxSize(),
                content = {
                    when (state.readingMode) {
                        PdfReadingMode.VERTICAL_CONTINUOUS -> {
                            // R7 V2 production path. PdfVerticalZoomLockArea remains below as
                            // retained legacy code only until physical-device acceptance completes.
                            ContinuousDocumentViewport(
                                state = state,
                                pageJump = pageJump,
                                onPageJumpConsumed = viewModel::onContinuousPageJumpConsumed,
                                onViewportWidthPx = viewModel::onViewportWidth,
                                onContinuousGeometryReady = viewModel::onContinuousGeometryReady,
                                onContinuousEntryRestoreApplied = viewModel::onContinuousEntryRestoreApplied,
                                onDocumentStateSettled = viewModel::commitContinuousDocumentState,
                                onViewportSessionSettled = viewModel::commitViewportSession,
                                onTilePlan = viewModel::requestPredictiveTiles,
                                tileOverlaySnapshots = viewModel.tileOverlaySnapshot,
                                onZoomGestureActive = viewModel::setZoomGestureActive,
                                onPageSettled = viewModel::onVerticalPageSettled,
                                onViewportChanged = viewModel::onContinuousViewportChanged,
                                onEnsureAround = viewModel::ensurePagesAround,
                                onRetryPage = { viewModel.ensurePagesAround(it) },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        PdfReadingMode.HORIZONTAL_PAGED -> {
                            PdfHorizontalPagedArea(
                                state = state,
                                pageJump = pageJump,
                                controlsVisible = horizontalControlsVisible,
                                onToggleChrome = {
                                    horizontalControlsVisible = !horizontalControlsVisible
                                },
                                onShowChrome = {
                                    horizontalControlsVisible = true
                                },
                                onPrevious = viewModel::goToPreviousPage,
                                onNext = viewModel::goToNextPage,
                                onPageLabelClick = { showJumpDialog = true },
                                onViewportWidthPx = viewModel::onViewportWidth,
                                onPageChanged = viewModel::onHorizontalPageSettled,
                                onZoomScaleSettled = viewModel::onZoomScaleSettled,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                },
            )

            if (showReadingModeSheet) {
                PdfReadingModeSheet(
                    mode = state.readingMode,
                    showPageButtons = state.showPageButtons,
                    onSelectMode = { mode ->
                        viewModel.setReadingMode(mode)
                        showReadingModeSheet = false
                    },
                    onShowButtonsChange = viewModel::setShowPageButtons,
                    onDismiss = { showReadingModeSheet = false },
                )
            }

            if (showJumpDialog) {
                JumpToPageDialog(
                    currentHumanPage = state.displayPageIndex + 1,
                    pageCount = state.pageCount,
                    onDismiss = { showJumpDialog = false },
                    onConfirm = { human ->
                        if (viewModel.jumpToHumanPage(human)) {
                            showJumpDialog = false
                        }
                    },
                )
            }

            if (showPrintDialog) {
                SetPrintPageDialog(
                    currentPdfHumanPage = state.pageIndex + 1,
                    currentPrint = state.printPageNumber,
                    isExact = state.isPrintExactAnchor,
                    onDismiss = { showPrintDialog = false },
                    onConfirm = { print ->
                        viewModel.setPrintPageForCurrent(print)
                        showPrintDialog = false
                    },
                    onClear = {
                        viewModel.clearPrintPageForCurrent()
                        showPrintDialog = false
                    },
                )
            }

            if (showOutlineSheet && !showThumbSheet && outlineEditor == null) {
                TextbookOutlineSheet(
                    nodes = state.outlineNodes,
                    currentPageIndex = state.pageIndex,
                    currentPathLabel = state.outlinePathLabel,
                    hasEmbeddedDrafts = viewModel.hasCachedEmbeddedOutline(),
                    onJumpToNode = { node ->
                        viewModel.goToPage(node.startPageIndex)
                        showOutlineSheet = false
                    },
                    onRequestEdit = { draft ->
                        outlineEditor = draft
                    },
                    onDelete = viewModel::deleteOutlineNode,
                    onExportNode = { node ->
                        showOutlineSheet = false
                        viewModel.beginExportNode(node)
                    },
                    onImportEmbedded = {
                        showOutlineSheet = false
                        viewModel.requestEmbeddedImportUi()
                    },
                    onDismiss = { showOutlineSheet = false },
                )
            }

            exportPlan?.let { plan ->
                val pageCount = (uiState as? PdfViewerUiState.Ready)?.pageCount ?: 1
                ChapterExportConfirmDialog(
                    plan = plan,
                    documentPageCount = pageCount,
                    onDismiss = viewModel::dismissExportPlan,
                    onConfirm = { start0, end0 ->
                        val name = viewModel.confirmExportRange(start0, end0)
                        if (name != null) {
                            createZipLauncher.launch(name)
                        }
                    },
                )
            }

            exportState?.let { st ->
                ChapterExportProgressDialog(
                    state = st,
                    onCancel = viewModel::cancelExport,
                    onDismiss = viewModel::dismissExportState,
                    onRetry = {
                        // Keep pending plan; pick a new SAF location and re-run.
                        createZipLauncher.launch(viewModel.suggestedExportFileName())
                    },
                )
            }

            outlineEditor?.let { draft ->
                OutlineEditDialog(
                    state = draft,
                    allNodes = state.outlineNodes,
                    pageCount = state.pageCount,
                    onDismiss = {
                        outlineEditor = null
                        showOutlineSheet = true
                    },
                    onSave = { id, parentId, type, title, start, end ->
                        viewModel.saveOutlineNode(
                            id = id,
                            parentId = parentId,
                            type = type,
                            title = title,
                            startPageIndex = start,
                            endPageIndex = end,
                        )
                        outlineEditor = null
                        showOutlineSheet = true
                    },
                    onPickRangeFromThumbs = {
                        thumbRangePick = true
                        showOutlineSheet = false
                        viewModel.prepareThumbnails()
                        showThumbSheet = true
                    },
                    onUseCurrentPage = {
                        outlineEditor = draft.copy(
                            startPageIndex = state.pageIndex,
                            endPageIndex = state.pageIndex,
                        )
                    },
                )
            }

            embeddedOffer?.let { offer ->
                EmbeddedOutlineImportDialog(
                    drafts = offer.drafts,
                    hasLocalNodes = offer.hasLocalNodes,
                    alreadyImported = offer.alreadyImported,
                    onImport = { mode ->
                        viewModel.importEmbeddedOutline(mode)
                    },
                    onDismiss = viewModel::dismissEmbeddedOutlineOffer,
                )
            }

            if (showThumbSheet) {
                LaunchedEffect(Unit) {
                    viewModel.prepareThumbnails()
                }
                PdfThumbnailSheet(
                    pageCount = state.pageCount,
                    currentPageIndex = state.pageIndex,
                    printAnchors = state.printAnchors,
                    loader = viewModel.thumbnailLoader(),
                    rangePickMode = thumbRangePick,
                    onPageSelected = { index ->
                        viewModel.goToPage(index)
                        showThumbSheet = false
                        thumbRangePick = false
                    },
                    onRangePicked = if (thumbRangePick) {
                        { start, end ->
                            outlineEditor = outlineEditor?.copy(
                                startPageIndex = start,
                                endPageIndex = end,
                            )
                            thumbRangePick = false
                            showThumbSheet = false
                        }
                    } else {
                        null
                    },
                    onDismiss = {
                        showThumbSheet = false
                        if (thumbRangePick) {
                            thumbRangePick = false
                            // Keep editor open for user to continue.
                        }
                    },
                )
            }
        }
    }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
        )
    }
}

@Composable
private fun PdfChrome(
    title: String,
    pageLabel: String?,
    printLabel: String?,
    outlinePathLabel: String?,
    onBack: () -> Unit,
    canPrevious: Boolean,
    canNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPageLabelClick: (() -> Unit)?,
    onSetPrintClick: (() -> Unit)?,
    onThumbsClick: (() -> Unit)?,
    onOutlineClick: (() -> Unit)?,
    onReadingModeClick: (() -> Unit)? = null,
    showPageButtons: Boolean = true,
    /** When false, TopBar is hidden (horizontal reading chrome overlay mode). */
    topBarVisible: Boolean = true,
    scrubber: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
            .statusBarsPadding(),
    ) {
        if (topBarVisible) {
        Surface(
            color = scheme.surface,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = scheme.onSurface,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (pageLabel != null) {
                            val interaction = remember { MutableInteractionSource() }
                            Text(
                                text = pageLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.onSurface,
                                modifier = if (onPageLabelClick != null) {
                                    Modifier.clickable(
                                        interactionSource = interaction,
                                        indication = null,
                                        role = Role.Button,
                                        onClick = onPageLabelClick,
                                    )
                                } else {
                                    Modifier
                                },
                            )
                        }
                    }
                    if (onReadingModeClick != null) {
                        IconButton(onClick = onReadingModeClick) {
                            Icon(
                                imageVector = Icons.Outlined.SwapVert,
                                contentDescription = stringResource(R.string.pdf_reading_mode_open),
                                tint = scheme.onSurface,
                            )
                        }
                    }
                    if (onOutlineClick != null) {
                        IconButton(onClick = onOutlineClick) {
                            Icon(
                                imageVector = Icons.Outlined.MenuBook,
                                contentDescription = stringResource(R.string.pdf_outline_open),
                                tint = scheme.onSurface,
                            )
                        }
                    }
                    if (onThumbsClick != null) {
                        IconButton(onClick = onThumbsClick) {
                            Icon(
                                imageVector = Icons.Outlined.GridView,
                                contentDescription = stringResource(R.string.pdf_thumbs_open),
                                tint = scheme.onSurface,
                            )
                        }
                    }
                    if (onSetPrintClick != null) {
                        IconButton(onClick = onSetPrintClick) {
                            Icon(
                                imageVector = Icons.Outlined.Tag,
                                contentDescription = stringResource(R.string.pdf_set_print_page),
                                tint = scheme.onSurface,
                            )
                        }
                    }
                }
                if (printLabel != null) {
                    Text(
                        text = printLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 56.dp, end = 16.dp, bottom = 2.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (outlinePathLabel != null) {
                    Text(
                        text = stringResource(R.string.pdf_outline_here, outlinePathLabel),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurface,
                        modifier = Modifier.padding(start = 56.dp, end = 16.dp, bottom = 6.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else if (printLabel != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(scheme.outlineVariant),
                )
            }
        }
        } // topBarVisible

        // PDF viewport — clipped; zoom must stay inside this box (never over TopBar).
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
                .background(MarkLeafTheme.extra.pdfCanvas),
        ) {
            content()
        }

        if (scrubber != null || showPageButtons) {
            Surface(
                color = scheme.surface,
                shadowElevation = 0.dp,
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(scheme.outlineVariant),
                    )
                    if (scrubber != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding(),
                        ) {
                            scrubber()
                        }
                    }
                    if (showPageButtons) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            IconButton(onClick = onPrevious, enabled = canPrevious) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                                    contentDescription = stringResource(R.string.pdf_previous_page),
                                    tint = if (canPrevious) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f)
                                    },
                                )
                            }
                            val pageInteraction = remember { MutableInteractionSource() }
                            Text(
                                text = pageLabel ?: "",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = if (onPageLabelClick != null) {
                                    Modifier
                                        .clickable(
                                            interactionSource = pageInteraction,
                                            indication = null,
                                            role = Role.Button,
                                            onClick = onPageLabelClick,
                                        )
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                } else {
                                    Modifier
                                },
                            )
                            IconButton(onClick = onNext, enabled = canNext) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                                    contentDescription = stringResource(R.string.pdf_next_page),
                                    tint = if (canNext) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Compact horizontal-mode scrubber only (continuous mode uses thin LinearProgressIndicator).
 * Touch row ~44dp; track thin; side inset 24dp.
 */
@Composable
private fun PdfPageScrubber(
    pageCount: Int,
    currentIndex: Int,
    onPreview: (Int) -> Unit,
    onFinished: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (pageCount <= 1) return
    val maxIndex = (pageCount - 1).toFloat()
    var sliderValue by remember(currentIndex, pageCount) {
        mutableFloatStateOf(currentIndex.toFloat().coerceIn(0f, maxIndex))
    }
    var dragging by remember { mutableStateOf(false) }

    LaunchedEffect(currentIndex, pageCount) {
        if (!dragging) {
            sliderValue = currentIndex.toFloat().coerceIn(0f, maxIndex)
        }
    }

    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Slider(
            value = sliderValue,
            onValueChange = { v ->
                dragging = true
                sliderValue = v
                onPreview(v.roundToInt().coerceIn(0, pageCount - 1))
            },
            onValueChangeFinished = {
                dragging = false
                val idx = sliderValue.roundToInt().coerceIn(0, pageCount - 1)
                onFinished(idx)
            },
            valueRange = 0f..maxIndex,
            steps = 0,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = scheme.primary,
                activeTrackColor = scheme.primary,
                inactiveTrackColor = scheme.outlineVariant.copy(alpha = 0.45f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun JumpToPageDialog(
    currentHumanPage: Int,
    pageCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(currentHumanPage.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val rangeHint = stringResource(R.string.pdf_jump_range, pageCount)
    val invalidMsg = stringResource(R.string.pdf_jump_invalid, pageCount)

    LaunchedEffect(Unit) {
        focus.requestFocus()
    }

    fun submit() {
        val n = text.trim().toIntOrNull()
        if (n == null || n < 1 || n > pageCount) {
            error = invalidMsg
            return
        }
        error = null
        keyboard?.hide()
        onConfirm(n)
    }

    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = scheme.dialogContainer,
        titleContentColor = scheme.onSurface,
        textContentColor = scheme.onSurfaceVariant,
        title = { Text(stringResource(R.string.pdf_jump_title)) },
        text = {
            Column {
                Text(
                    text = rangeHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it.filter { ch -> ch.isDigit() }.take(6)
                        error = null
                    },
                    singleLine = true,
                    label = { Text(stringResource(R.string.pdf_jump_label)) },
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    colors = markLeafOutlinedFieldColors(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { submit() }) {
                Text(stringResource(R.string.pdf_jump_go))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.pdf_dialog_cancel))
            }
        },
    )
}

@Composable
private fun SetPrintPageDialog(
    currentPdfHumanPage: Int,
    currentPrint: Int?,
    isExact: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    onClear: () -> Unit,
) {
    var text by remember {
        mutableStateOf(currentPrint?.toString().orEmpty())
    }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val invalidMsg = stringResource(R.string.pdf_print_invalid)

    LaunchedEffect(Unit) {
        focus.requestFocus()
    }

    fun submit() {
        val n = text.trim().toIntOrNull()
        if (n == null || n < 1) {
            error = invalidMsg
            return
        }
        onConfirm(n)
    }

    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = scheme.dialogContainer,
        titleContentColor = scheme.onSurface,
        textContentColor = scheme.onSurfaceVariant,
        title = { Text(stringResource(R.string.pdf_set_print_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.pdf_set_print_hint, currentPdfHumanPage),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                if (currentPrint != null && !isExact) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.pdf_print_currently_inferred, currentPrint),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it.filter { ch -> ch.isDigit() }.take(6)
                        error = null
                    },
                    singleLine = true,
                    label = { Text(stringResource(R.string.pdf_print_label)) },
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    colors = markLeafOutlinedFieldColors(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 360.dp)
                        .focusRequester(focus),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { submit() }) {
                Text(stringResource(R.string.pdf_print_save))
            }
        },
        dismissButton = {
            Row {
                if (isExact) {
                    TextButton(onClick = onClear) {
                        Text(stringResource(R.string.pdf_print_clear))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.pdf_dialog_cancel))
                }
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PdfHorizontalPagedArea(
    state: PdfViewerUiState.Ready,
    pageJump: PdfViewerViewModel.PageJumpRequest?,
    controlsVisible: Boolean,
    onToggleChrome: () -> Unit,
    onShowChrome: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPageLabelClick: () -> Unit,
    onViewportWidthPx: (Int) -> Unit,
    onPageChanged: (Int) -> Unit,
    onZoomScaleSettled: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(
        initialPage = state.pageIndex,
        pageCount = { state.pageCount.coerceAtLeast(1) },
    )
    // GPU zoom only inside clipped viewport — never covers TopBar.
    var scale by remember(state.document.id) { mutableFloatStateOf(1f) }
    var offset by remember(state.document.id) { mutableStateOf(Offset.Zero) }
    var horizontalPan by remember(state.document.id) { mutableStateOf(HorizontalPanState()) }
    var horizontalPinching by remember(state.document.id) { mutableStateOf(false) }
    val horizontalTransforms=remember(state.document.id){mutableStateMapOf<Int,Pair<Float,Offset>>()}
    var activeHorizontalPage by remember(state.document.id){mutableIntStateOf(state.pageIndex)}
    var viewportSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val scrubbing = state.scrubPreviewIndex != null
    val scheme = MaterialTheme.colorScheme
    val viewConfig = LocalViewConfiguration.current
    val horizontalDensity = LocalDensity.current
    val horizontalScope = rememberCoroutineScope()
    var horizontalReboundJob by remember(state.document.id) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var consumedJumpId by remember { mutableStateOf(pageJump?.id) }
    var lastTapTimeMs by remember { mutableLongStateOf(0L) }
    var lastTapPosition by remember { mutableStateOf<Offset?>(null) }
    val currentHorizontalStatePage by rememberUpdatedState(state.pageIndex)

    // VM state does not drive Pager. Only a new explicit request id may reposition it.
    LaunchedEffect(pageJump?.id) {
        val jump = pageJump ?: return@LaunchedEffect
        if (!com.markleaf.app.pdf.PagerRequestPolicy.shouldConsume(jump.id, consumedJumpId)) {
            return@LaunchedEffect
        }
        consumedJumpId = jump.id
        pagerState.scrollToPage(jump.targetPageIndex.coerceIn(0, state.pageCount - 1))
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                if (page != currentHorizontalStatePage) onPageChanged(page)
            }
    }

    fun clampOffset(s: Float, o: Offset): Offset {
        if (s <= 1.01f || viewportSize.width <= 0) return Offset.Zero
        val maxX = viewportSize.width * (s - 1f) / 2f
        val maxY = viewportSize.height * (s - 1f) / 2f
        return Offset(
            o.x.coerceIn(-maxX, maxX),
            o.y.coerceIn(-maxY, maxY),
        )
    }
    LaunchedEffect(state.pageIndex) {
        if(activeHorizontalPage!=state.pageIndex){
            horizontalReboundJob?.cancel()
            val legal=clampOffset(scale,Offset(horizontalPan.contentX,horizontalPan.contentY))
            horizontalTransforms[activeHorizontalPage]=scale to legal
            activeHorizontalPage=state.pageIndex
            val restored=horizontalTransforms[state.pageIndex]?: (1f to Offset.Zero)
            scale=restored.first;offset=restored.second
            horizontalPan=HorizontalPanState(contentX=offset.x,contentY=offset.y)
            onZoomScaleSettled(scale)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged {
                viewportSize = it
                onViewportWidthPx(it.width)
            },
        contentAlignment = Alignment.Center,
    ) {
        if (scrubbing) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.pageLabel,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = scheme.onSurface,
                )
            }
            return@Box
        }

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = scale <= 1.01f && !horizontalPinching,
            beyondViewportPageCount = 0,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val bmp = if (page == state.pageIndex) {
                state.pageImage ?: state.pageBitmaps[page]
            } else {
                state.pageBitmaps[page]
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .pointerInput(page, currentHorizontalStatePage) {
                        if (page != currentHorizontalStatePage) return@pointerInput
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            horizontalReboundJob?.cancel()
                            val continuous=clampOffset(scale,offset)
                            horizontalPan=HorizontalPanState(contentX=continuous.x,contentY=continuous.y)
                            offset=continuous
                            var transformed = false
                            var activeIds = emptyList<Long>()
                            var pair: Pair<Long,Long>? = null
                            var baseScale = scale
                            var baseOffset = offset
                            var baseCentroid = Offset.Zero
                            var baseSpan = 1f
                            var focal = Offset.Zero
                            do {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                val ids=pressed.map{it.id.value}.sorted()
                                val changed=ids!=activeIds
                                if(changed && pressed.isNotEmpty()) {
                                    horizontalPinching=pressed.size>=2
                                    horizontalPan=horizontalPan.copy(overscrollX=0f,overscrollY=0f,edgeAccumulation=0f,handedOff=false)
                                    activeIds=ids
                                    pair=if(pressed.size>=2) {
                                        val old=pair
                                        if(old!=null&&old.first in ids&&old.second in ids) old else {
                                            val retained=listOfNotNull(old?.first,old?.second).filter{it in ids}
                                            val chosen=(retained+ids.filterNot{it in retained}).take(2)
                                            chosen[0] to chosen[1]
                                        }
                                    } else null
                                    baseScale=scale;baseOffset=offset
                                    val selected=if(pair!=null) pressed.filter{it.id.value==pair!!.first||it.id.value==pair!!.second} else pressed.take(1)
                                    baseCentroid=selected.map{it.position}.reduce{a,b->a+b}/selected.size.toFloat()
                                    baseSpan=if(selected.size==2)(selected[0].position-selected[1].position).getDistance().coerceAtLeast(.001f) else 1f
                                    val center=Offset(viewportSize.width/2f,viewportSize.height/2f)
                                    focal=(baseCentroid-center-baseOffset)/baseScale.coerceAtLeast(.001f)
                                    pressed.forEach{if(it.positionChanged())it.consume()}
                                } else if (pressed.size >= 2) {
                                    transformed = true
                                    horizontalPinching=true
                                    val locked=pair?.let{p->pressed.filter{it.id.value==p.first||it.id.value==p.second}}?:emptyList()
                                    if(locked.size!=2) continue
                                    val centroid=(locked[0].position+locked[1].position)/2f
                                    val span=(locked[0].position-locked[1].position).getDistance().coerceAtLeast(.001f)
                                    val newScale = (baseScale * span/baseSpan).coerceIn(
                                        PdfRenderQuality.MIN_ZOOM,
                                        PdfRenderQuality.MAX_ZOOM,
                                    )
                                    scale = newScale
                                    val center=Offset(viewportSize.width/2f,viewportSize.height/2f)
                                    offset = if (newScale <= 1.01f) Offset.Zero else clampOffset(newScale,centroid-center-focal*newScale)
                                    horizontalPan=horizontalPan.copy(contentX=offset.x,contentY=offset.y,overscrollX=0f,overscrollY=0f)
                                    pressed.forEach { if (it.positionChanged()) it.consume() }
                                } else if (pressed.size == 1 && scale > 1.01f) {
                                    val change = pressed.first()
                                    val pan = change.position - change.previousPosition
                                    val maxX=viewportSize.width*(scale-1f)/2f;val maxY=viewportSize.height*(scale-1f)/2f
                                    val result=HorizontalZoomPanPhysics.pan(horizontalPan,pan.x,pan.y,maxX,maxY,with(horizontalDensity){56.dp.toPx()},with(horizontalDensity){32.dp.toPx()})
                                    horizontalPan=result.state;offset=Offset(result.visualX,result.visualY)
                                    if(result.navigateDirection>0)onPrevious() else if(result.navigateDirection<0)onNext()
                                    if (change.positionChanged()) change.consume()
                                }
                            } while (event.changes.any { it.pressed })
                            horizontalPinching=false
                            val reboundStart=offset
                            horizontalPan=HorizontalZoomPanPhysics.settle(horizontalPan)
                            val reboundEnd=clampOffset(scale,Offset(horizontalPan.contentX,horizontalPan.contentY))
                            horizontalReboundJob=horizontalScope.launch {
                                Animatable(0f).animateTo(1f,spring(dampingRatio=Spring.DampingRatioNoBouncy,stiffness=Spring.StiffnessMedium)){ 
                                    offset=Offset(reboundStart.x+(reboundEnd.x-reboundStart.x)*value,reboundStart.y+(reboundEnd.y-reboundStart.y)*value)
                                }
                            }
                            if (transformed) onZoomScaleSettled(scale)
                        }
                    }
                    .pointerInput(page, scale, currentHorizontalStatePage) {
                        if (page != currentHorizontalStatePage) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var maxDistance = 0f
                            var lastPosition = down.position
                            var upTimeMs = down.uptimeMillis
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull()
                                if (change != null) {
                                    lastPosition = change.position
                                    upTimeMs = change.uptimeMillis
                                    maxDistance = maxOf(
                                        maxDistance, (change.position - down.position).getDistance(),
                                    )
                                }
                            } while (event.changes.any { it.pressed })
                            if (maxDistance > viewConfig.touchSlop || pagerState.isScrollInProgress) {
                                return@awaitEachGesture
                            }
                            val now = upTimeMs
                            val previousTap = lastTapPosition
                            val doubleTap = previousTap != null &&
                                now - lastTapTimeMs <= viewConfig.doubleTapTimeoutMillis &&
                                (lastPosition - previousTap).getDistance() <= viewConfig.touchSlop
                            if (doubleTap) {
                                val target = if (scale < 1.5f) {
                                    PdfRenderQuality.DOUBLE_TAP_ZOOM
                                } else PdfRenderQuality.MIN_ZOOM
                                scale = target
                                if (target <= 1.01f) { offset = Offset.Zero; horizontalPan=HorizontalPanState() }
                                lastTapTimeMs = 0L
                                lastTapPosition = null
                                onZoomScaleSettled(target)
                            } else {
                                lastTapTimeMs = now
                                lastTapPosition = lastPosition
                                if (scale > 1.01f) {
                                    onShowChrome()
                                    return@awaitEachGesture
                                }
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                when (ZoomPageOverlayMath.pageTap(lastPosition.x, w, false, scale)) {
                                    PageTap.PREVIOUS -> if (state.canGoPrevious) onPrevious() else onShowChrome()
                                    PageTap.NEXT -> if (state.canGoNext) onNext() else onShowChrome()
                                    PageTap.NONE -> onToggleChrome()
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    bmp != null -> {
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            color = Color.White,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                scheme.outlineVariant,
                            ),
                            modifier = Modifier
                                .padding(8.dp)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = offset.x
                                    translationY = offset.y
                                },
                        ) {
                            Image(
                                bitmap = bmp,
                                contentDescription = stringResource(
                                    R.string.pdf_page_cd,
                                    page + 1,
                                    state.pageCount,
                                ),
                                contentScale = ContentScale.Fit,
                                filterQuality = FilterQuality.Medium,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    page == state.pageIndex && state.renderError != null -> {
                        Text(
                            text = state.renderError,
                            color = scheme.error,
                            modifier = Modifier.padding(24.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                    // Keep spinner only when no bitmap at all — never flash white over old page.
                    else -> CircularProgressIndicator(modifier = Modifier.size(36.dp))
                }
            }
        }

        // Semi-transparent bottom overlay controls (not a fixed Scaffold bottom bar).
        // Edge/center taps handled via detectTapGestures on the page (does not block pager swipe).
        if (controlsVisible) {
            Surface(
                color = scheme.surface.copy(alpha = 0.88f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        IconButton(onClick = onPrevious, enabled = state.canGoPrevious) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                                contentDescription = stringResource(R.string.pdf_previous_page),
                            )
                        }
                        Text(
                            text = state.pageLabel,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                            ),
                            color = scheme.onSurface,
                            modifier = Modifier
                                .clickable(onClick = onPageLabelClick)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                        IconButton(onClick = onNext, enabled = state.canGoNext) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                                contentDescription = stringResource(R.string.pdf_next_page),
                            )
                        }
                    }
                }
            }
        }
    }

}

/** R6 continuous mode: fixed LazyColumn geometry plus a viewport-clipped current-page overlay. */
@OptIn(FlowPreview::class)
@Composable
private fun PdfVerticalZoomLockArea(
    state: PdfViewerUiState.Ready,
    pageJump: PdfViewerViewModel.PageJumpRequest?,
    onViewportWidthPx: (Int) -> Unit,
    onZoomScaleSettled: (Float, Int?) -> Unit,
    onZoomGestureActive: (Boolean) -> Unit,
    onPageSettled: (Int) -> Unit,
    onViewportChanged: (ContinuousViewportSnapshot) -> Unit,
    onEnsureAround: (Int) -> Unit,
    onRetryPage: (Int) -> Unit,
    onFastScrollPreview: (Int) -> Unit,
    onFastScrollFinished: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = state.pageIndex.coerceAtLeast(0),
    )
    val directlyDragged by listState.interactionSource.collectIsDraggedAsState()
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val docId = state.document.id
    val TAG = "MarkLeafZoom"
    var viewportSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    /** Per-page bounds in ReadingViewport-local coordinates for every visible item. */
    val pageBoundsInViewport = remember { mutableStateMapOf<Int, Rect>() }
    var viewportCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // This state freezes LazyColumn as soon as the second pointer arrives. It is
    // intentionally independent from ZoomSession creation/hit testing.
    var twoFingerCaptured by remember(docId) { mutableStateOf(false) }
    // Stable for this document composition; a new value in logs proves detector recreation.
    val detectorInstanceId = remember(docId) { System.nanoTime() }
    // Stable owner above the conditional overlay. Never keyed by gesture/bitmap/page/quality.
    var zoomSession by remember { mutableStateOf(ContinuousZoomSession.inactive()) }
    val lockedPage = zoomSession.lockedPage
    val currentLockedPage by rememberUpdatedState(lockedPage)
    val context = LocalContext.current
    val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    fun overlayRect(): OverlayRect = OverlayRect(
        zoomSession.lockedBounds.left,
        zoomSession.lockedBounds.top,
        zoomSession.lockedBounds.width,
        zoomSession.lockedBounds.height,
    )

    /** Build hit-test candidates from the currently visible items and their known bounds. */
    fun buildCandidates(): List<VisiblePageTarget> {
        val visiblePages = listState.layoutInfo.visibleItemsInfo
            .filter { it.index in 0 until state.pageCount }
            .map { it.index }
            .toSet()
        return pageBoundsInViewport
            .filterKeys { it in visiblePages }
            .map { (page, rect) ->
                VisiblePageTarget(page, OverlayRect(rect.left, rect.top, rect.width, rect.height))
            }
    }

    fun enterLock(page: Int, bounds: OverlayRect): Boolean {
        if (zoomSession.active || bounds.width <= 0f || bounds.height <= 0f) return false
        // Require a last-good bitmap for the exact hit-tested page; never fall back to pageImage.
        val bitmapReady = state.pageBitmaps[page] != null
        if (!bitmapReady) {
            if (isDebuggable) Log.d(TAG, "BITMAP_REJECT page=$page reason=noBitmapForLockedPage")
            return false
        }
        zoomSession = ContinuousZoomSessionReducer.begin(
            previous = zoomSession,
            page = page,
            bounds = bounds,
            listIndex = listState.firstVisibleItemIndex,
            listOffset = listState.firstVisibleItemScrollOffset,
        )
        if (isDebuggable) {
            Log.d(
                TAG,
                "SESSION_CREATE instanceId=$detectorInstanceId lockedPage=$page bitmapPage=$page " +
                    "listIndex=${listState.firstVisibleItemIndex} " +
                    "offset=${listState.firstVisibleItemScrollOffset} " +
                    "sessionId=${zoomSession.sessionId}",
            )
        }
        return true
    }

    fun exitLock(reason: String = "explicit") {
        val page = zoomSession.lockedPage
        // One snapshot restores bottom content and removes the sole overlay atomically.
        zoomSession = ContinuousZoomSessionReducer.exit(zoomSession)
        onZoomGestureActive(false)
        onZoomScaleSettled(1f, null)
        if (isDebuggable) {
            Log.d(TAG, "SESSION_EXIT reason=$reason wasPage=$page")
            if (page != null) Log.d(TAG, "LOCKED_ITEM_SHOW page=$page")
        }
    }

    DisposableEffect(docId, detectorInstanceId) {
        onDispose {
            if (isDebuggable) {
                Log.d(
                    TAG,
                    "DETECTOR_DISPOSE instanceId=$detectorInstanceId " +
                        "listIndex=${listState.firstVisibleItemIndex} " +
                        "offset=${listState.firstVisibleItemScrollOffset}",
                )
            }
        }
    }

    LaunchedEffect(pageJump?.id) {
        if (lockedPage != null) return@LaunchedEffect
        val jump = pageJump ?: return@LaunchedEffect
        val target = jump.targetPageIndex.coerceIn(0, (state.pageCount - 1).coerceAtLeast(0))
        if (jump.source.startsWith("fastScroll") || abs(target - listState.firstVisibleItemIndex) > 3) {
            listState.scrollToItem(target)
        } else {
            listState.animateScrollToItem(target)
        }
    }

    LaunchedEffect(listState, state.pageCount, lockedPage) {
        if (lockedPage != null) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index
        }.distinctUntilChanged().debounce(150).collect { page ->
            if (page != null) onPageSettled(page)
        }
    }

    LaunchedEffect(listState, state.pageCount, directlyDragged, lockedPage) {
        if (lockedPage != null) return@LaunchedEffect
        var previousIndex = listState.firstVisibleItemIndex
        var previousOffset = listState.firstVisibleItemScrollOffset
        var direction = ContinuousScrollDirection.IDLE
        var last: ContinuousViewportSnapshot? = null
        snapshotFlow {
            val info = listState.layoutInfo
            val valid = info.visibleItemsInfo.filter { it.index in 0 until state.pageCount }
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            val primary = valid.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index
                ?: listState.firstVisibleItemIndex.coerceIn(0, state.pageCount - 1)
            ViewportRaw(
                valid.map { it.index }.toSet(), primary,
                listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset,
                listState.isScrollInProgress,
            )
        }.collect { raw ->
            val moved = raw.firstIndex.compareTo(previousIndex).takeIf { it != 0 }
                ?: raw.firstOffset.compareTo(previousOffset)
            if (moved > 0) direction = ContinuousScrollDirection.FORWARD
            if (moved < 0) direction = ContinuousScrollDirection.BACKWARD
            val phase = when {
                directlyDragged -> ContinuousScrollPhase.DRAGGING
                raw.scrolling -> ContinuousScrollPhase.FLINGING
                else -> ContinuousScrollPhase.IDLE
            }
            val snapshot = ContinuousViewportSnapshot(
                raw.visible, raw.primary,
                if (phase == ContinuousScrollPhase.IDLE) ContinuousScrollDirection.IDLE else direction,
                phase,
            )
            if (snapshot != last) onViewportChanged(snapshot)
            last = snapshot
            previousIndex = raw.firstIndex
            previousOffset = raw.firstOffset
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged {
                viewportSize = it
                onViewportWidthPx(it.width)
            }
            // Stable ReadingViewport space for both pointer centroids and page bounds.
            .onGloballyPositioned { viewportCoordinates = it }
            .pointerInput(docId) {
                if (isDebuggable) {
                    Log.d(
                        TAG,
                        "DETECTOR_START instanceId=$detectorInstanceId documentId=$docId " +
                            "listIndex=${listState.firstVisibleItemIndex} " +
                            "offset=${listState.firstVisibleItemScrollOffset}",
                    )
                }
                try {
                    awaitEachGesture {
                    val firstDown = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    if (isDebuggable) {
                        Log.d(
                            TAG,
                            "FIRST_DOWN instanceId=$detectorInstanceId pointerId=${firstDown.id} " +
                                "listIndex=${listState.firstVisibleItemIndex} " +
                                "offset=${listState.firstVisibleItemScrollOffset}",
                        )
                    }
                    var phase = if (zoomSession.active) {
                        TwoFingerGesturePhase.ZOOM_SESSION_ACTIVE
                    } else {
                        TwoFingerGesturePhase.IDLE
                    }
                    var capturedThisGesture = false
                    var sessionCreatedThisGesture = false
                    var pinchReleased = false
                    var hitAttempts = 0
                    var captureListIndex = 0
                    var captureListOffset = 0

                    fun consumeCapturedChanges(event: androidx.compose.ui.input.pointer.PointerEvent) {
                        event.changes
                            .filter { it.pressed || it.previousPressed }
                            .forEach { it.consume() }
                    }

                    fun attemptSessionCreate(centroid: Offset) {
                        if (zoomSession.active || !TwoFingerGestureArbitration.canRetryHit(hitAttempts)) {
                            return
                        }
                        val firstHitAttempt = hitAttempts == 0
                        hitAttempts += 1
                        val candidates = buildCandidates()
                        val hitPage = ZoomPageOverlayMath.hitTestPage(
                            centroid.x,
                            centroid.y,
                            candidates,
                        )
                        if (isDebuggable && firstHitAttempt) {
                            val pages = candidates.joinToString(prefix = "[", postfix = "]") { target ->
                                val rect = target.bounds
                                "pageIndex=${target.pageIndex},left=${rect.left},top=${rect.top}," +
                                    "right=${rect.left + rect.width},bottom=${rect.top + rect.height}"
                            }
                            val result = if (hitPage == null) "MISS" else "HIT"
                            Log.d(
                                TAG,
                                "$result instanceId=$detectorInstanceId " +
                                    "coordinateSpace=ReadingViewportLocal " +
                                    "centroidX=${centroid.x} centroidY=${centroid.y} " +
                                    "pages=$pages hitPage=${hitPage ?: "MISS"}",
                            )
                        }
                        val hitBounds = hitPage?.let(pageBoundsInViewport::get) ?: return
                        val overlayBounds = OverlayRect(
                            hitBounds.left,
                            hitBounds.top,
                            hitBounds.width,
                            hitBounds.height,
                        )
                        if (enterLock(hitPage, overlayBounds)) {
                            sessionCreatedThisGesture = true
                            phase = TwoFingerGesturePhase.ZOOM_SESSION_ACTIVE
                            onZoomGestureActive(true)
                        }
                    }

                    do {
                        // Initial pass precedes LazyColumn scrollable Main-pass processing. The
                        // second finger therefore takes ownership before drag velocity is recorded.
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = event.changes.filter { it.pressed }
                        if (TwoFingerGestureArbitration.shouldCapture(pressed.size)) {
                            if (!capturedThisGesture) {
                                // The first operation on SECOND_POINTER_DOWN. This is deliberately
                                // before logging, hit testing, session work, or scroll cancellation.
                                twoFingerCaptured = true
                                capturedThisGesture = true
                                phase = TwoFingerGestureArbitration.onSecondPointer()
                                captureListIndex = listState.firstVisibleItemIndex
                                captureListOffset = listState.firstVisibleItemScrollOffset
                                if (isDebuggable) {
                                    Log.d(
                                        TAG,
                                        "SECOND_DOWN instanceId=$detectorInstanceId pointerCount=${pressed.size} " +
                                            "listIndex=$captureListIndex offset=$captureListOffset",
                                    )
                                    Log.d(
                                        TAG,
                                        "CAPTURE instanceId=$detectorInstanceId pointerCount=${pressed.size} " +
                                            "listIndex=$captureListIndex offset=$captureListOffset",
                                    )
                                }
                                // Consume before a suspending scroll mutation so this very event
                                // cannot reach LazyColumn Main-pass handling.
                                consumeCapturedChanges(event)
                                // Start cancellation in this dispatch turn. The Initial-pass consume
                                // above already prevents this event and all later movement from
                                // reaching LazyColumn while the scroll mutation acquires its lock.
                                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                    listState.stopScroll(MutatePriority.PreventUserInput)
                                }
                            } else {
                                consumeCapturedChanges(event)
                            }
                            // Capture holds even if no bounds are ready, so LazyColumn cannot
                            // receive later movement or release velocity.
                            val centroid = event.calculateCentroid(useCurrent = true)
                            attemptSessionCreate(centroid)
                            val activeLockedPage = zoomSession.lockedPage
                            if (activeLockedPage != null) {
                                phase = TwoFingerGesturePhase.ZOOM_SESSION_ACTIVE
                                if (!zoomSession.gestureActive) {
                                    zoomSession = ContinuousZoomSessionReducer.begin(
                                        zoomSession, activeLockedPage, overlayRect(),
                                        zoomSession.entryListIndex, zoomSession.entryListOffset,
                                    )
                                    onZoomGestureActive(true)
                                }
                                val oldScale = zoomSession.scale
                                val newScale = ZoomPageOverlayMath.targetScale(
                                    oldScale, event.calculateZoom(),
                                )
                                val pan = event.calculatePan()
                                val anchored = ZoomPageOverlayMath.anchoredOffset(
                                    oldScale, newScale, zoomSession.offset,
                                    centroid.x, centroid.y, overlayRect(), pan.x, pan.y,
                                )
                                val clampedOffset = ZoomPageOverlayMath.clampOffset(
                                    newScale, anchored, overlayRect(),
                                    viewportSize.width.toFloat(), viewportSize.height.toFloat(),
                                )
                                zoomSession = ContinuousZoomSessionReducer.updateTransform(
                                    zoomSession, newScale, clampedOffset,
                                )
                            }
                        } else if (capturedThisGesture) {
                            // After one finger lifts, retain ownership until every pointer is up.
                            // A captured miss must also never turn into a LazyColumn fling.
                            consumeCapturedChanges(event)
                            if (!pinchReleased) {
                                pinchReleased = true
                                if (zoomSession.active && zoomSession.gestureActive) {
                                    val ended = ContinuousZoomSessionReducer.gestureEnd(zoomSession)
                                    zoomSession = ended
                                    onZoomGestureActive(false)
                                    if (ended.active) onZoomScaleSettled(ended.scale, ended.lockedPage)
                                    else onZoomScaleSettled(1f, null)
                                }
                                val moved = captureListIndex != listState.firstVisibleItemIndex ||
                                    captureListOffset != listState.firstVisibleItemScrollOffset
                                if (isDebuggable) {
                                    Log.d(
                                        TAG,
                                        "RELEASE instanceId=$detectorInstanceId " +
                                            "sessionCreated=$sessionCreatedThisGesture " +
                                            "listIndex=${listState.firstVisibleItemIndex} " +
                                            "offset=${listState.firstVisibleItemScrollOffset} " +
                                            "listMovedDuringGesture=$moved",
                                    )
                                }
                            }
                            if (pressed.size == 1 && zoomSession.active) {
                                val change = pressed.first()
                                val pan = OverlayOffset(
                                    zoomSession.offset.x + change.position.x - change.previousPosition.x,
                                    zoomSession.offset.y + change.position.y - change.previousPosition.y,
                                )
                                val clampedOffset = ZoomPageOverlayMath.clampOffset(
                                    zoomSession.scale, pan, overlayRect(),
                                    viewportSize.width.toFloat(), viewportSize.height.toFloat(),
                                )
                                zoomSession = zoomSession.copy(offset = clampedOffset)
                            }
                        } else if (pressed.size == 1 && zoomSession.lockedPage != null) {
                            phase = TwoFingerGesturePhase.ZOOM_SESSION_ACTIVE
                            val change = pressed.first()
                            val pan = OverlayOffset(
                                zoomSession.offset.x + change.position.x - change.previousPosition.x,
                                zoomSession.offset.y + change.position.y - change.previousPosition.y,
                            )
                            val clampedOffset = ZoomPageOverlayMath.clampOffset(
                                zoomSession.scale, pan, overlayRect(),
                                viewportSize.width.toFloat(), viewportSize.height.toFloat(),
                            )
                            zoomSession = zoomSession.copy(offset = clampedOffset)
                            if (change.positionChanged()) change.consume()
                        } else {
                            phase = TwoFingerGestureArbitration.onSinglePointer(phase)
                        }
                    } while (event.changes.any { it.pressed })

                    if (!pinchReleased && zoomSession.active && zoomSession.gestureActive) {
                        val ended = ContinuousZoomSessionReducer.gestureEnd(zoomSession)
                        zoomSession = ended
                        onZoomGestureActive(false)
                        if (isDebuggable) Log.d(TAG, "GESTURE_END active=${ended.active} scale=${ended.scale} exitRequested=${ended.exitRequested} page=${ended.lockedPage}")
                        if (ended.active) {
                            // Gesture end persists the session and transform.
                            onZoomScaleSettled(ended.scale, ended.lockedPage)
                        } else {
                            onZoomScaleSettled(1f, null)
                        }
                    }
                    if (!zoomSession.active) twoFingerCaptured = false
                    }
                } finally {
                    if (isDebuggable) {
                        Log.d(
                            TAG,
                            "DETECTOR_CANCEL instanceId=$detectorInstanceId " +
                                "listIndex=${listState.firstVisibleItemIndex} " +
                                "offset=${listState.firstVisibleItemScrollOffset}",
                        )
                    }
                }
            }
            .pointerInput(docId) {
                detectTapGestures(onDoubleTap = { centroid ->
                    if (currentLockedPage != null) {
                        exitLock("doubleTap")
                    } else {
                        // R6.2: hit-test centroid against visible page bounds.
                        val candidates = buildCandidates()
                        val hitPage = ZoomPageOverlayMath.hitTestPage(
                            centroid.x,
                            centroid.y,
                            candidates,
                        )
                        if (isDebuggable) {
                            Log.d(
                                TAG,
                                "ZOOM_TARGET_HIT coordinateSpace=ReadingViewportLocal " +
                                    "centroidX=${centroid.x} centroidY=${centroid.y} " +
                                    "hitPage=$hitPage (doubleTap)",
                            )
                        }
                        if (hitPage != null) {
                            val hitBounds = pageBoundsInViewport[hitPage]
                            if (hitBounds != null) {
                                val overlayBounds = OverlayRect(
                                    hitBounds.left, hitBounds.top,
                                    hitBounds.width, hitBounds.height,
                                )
                                scope.launch {
                                    listState.stopScroll()
                                    if (enterLock(hitPage, overlayBounds)) {
                                        val target = PdfRenderQuality.DOUBLE_TAP_ZOOM
                                        val anchored = ZoomPageOverlayMath.anchoredOffset(
                                            1f, target, OverlayOffset(0f, 0f),
                                            centroid.x, centroid.y, overlayBounds,
                                        )
                                        val clampedOffset = ZoomPageOverlayMath.clampOffset(
                                            target, anchored, overlayBounds,
                                            viewportSize.width.toFloat(), viewportSize.height.toFloat(),
                                        )
                                        zoomSession = ContinuousZoomSessionReducer.updateTransform(
                                            zoomSession.copy(gestureActive = false), target, clampedOffset,
                                        )
                                        onZoomScaleSettled(target, hitPage)
                                    }
                                }
                            }
                        }
                    }
                })
            },
    ) {
        LazyColumn(
            state = listState,
            userScrollEnabled = TwoFingerGestureArbitration.listScrollEnabled(
                twoFingerCaptured = twoFingerCaptured,
                sessionActive = zoomSession.active,
            ),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(
                count = state.pageCount,
                key = { page -> "$docId|$page" },
            ) { page ->
                val aspect = state.pageAspects[page] ?: 1.414f
                val bitmap = state.pageBitmaps[page]
                val current = page == state.pageIndex
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (current) scheme.surfaceContainerHigh else MarkLeafTheme.extra.pdfCanvas)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, scheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .aspectRatio(1f / aspect.coerceIn(0.4f, 3f))
                                .onGloballyPositioned { coordinates ->
                                    // Convert every item to ReadingViewport-local coordinates. Pointer
                                    // centroids from the root detector use this exact same space.
                                    if (lockedPage == null) {
                                        val viewport = viewportCoordinates
                                            ?: return@onGloballyPositioned
                                        val topLeft = viewport.localPositionOf(coordinates, Offset.Zero)
                                        pageBoundsInViewport[page] = Rect(
                                            left = topLeft.x,
                                            top = topLeft.y,
                                            right = topLeft.x + coordinates.size.width,
                                            bottom = topLeft.y + coordinates.size.height,
                                        )
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                !ContinuousZoomSessionReducer.bottomContentVisible(
                                    zoomSession, page,
                                ) -> Box(Modifier.fillMaxSize().background(Color.White))
                                bitmap != null -> Image(
                                    bitmap = bitmap,
                                    contentDescription = stringResource(
                                        R.string.pdf_page_cd, page + 1, state.pageCount,
                                    ),
                                    contentScale = ContentScale.FillWidth,
                                    filterQuality = FilterQuality.Medium,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                current && state.renderError != null -> TextButton(
                                    onClick = { onRetryPage(page) },
                                ) { Text(stringResource(R.string.pdf_retry)) }
                                else -> {
                                    Box(Modifier.fillMaxSize().background(Color.White))
                                    LaunchedEffect(page, docId) { onEnsureAround(page) }
                                }
                            }
                        }
                    }
                }
            }
        }

        val page = lockedPage
        // R6.2: log when a higher-quality bitmap arrives for the locked page during an active session.
        if (zoomSession.active && page != null && isDebuggable) {
            val currentBitmap = state.pageBitmaps[page]
            LaunchedEffect(currentBitmap) {
                if (currentBitmap != null) {
                    Log.d(TAG, "BITMAP_ACCEPT page=$page sessionId=${zoomSession.sessionId}")
                }
            }
        }
        if (zoomSession.active && page != null &&
            zoomSession.lockedBounds.width > 0f && zoomSession.lockedBounds.height > 0f
        ) {
            // R6.2: strictly use only the locked page's own bitmap — never fall back to state.pageImage.
            val bitmap = state.pageBitmaps[page]
            if (bitmap == null && isDebuggable) Log.d(TAG, "BITMAP_REJECT page=$page reason=overlayMissingBitmap")
            val bounds = zoomSession.lockedBounds
            if (isDebuggable) Log.d(TAG, "OVERLAY_SHOW page=$page scale=${zoomSession.scale} hasBitmap=${bitmap != null}")
            DisposableEffect(page) {
                onDispose {
                    if (isDebuggable) Log.d(TAG, "OVERLAY_DISPOSE page=$page")
                }
            }
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt())
                    }
                    .width(with(density) { bounds.width.toDp() })
                    .height(with(density) { bounds.height.toDp() })
                    .graphicsLayer {
                        scaleX = zoomSession.scale
                        scaleY = zoomSession.scale
                        translationX = zoomSession.offset.x
                        translationY = zoomSession.offset.y
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                    .background(Color.White),
            ) {
                if (bitmap != null) Image(
                    bitmap = bitmap,
                    contentDescription = stringResource(
                        R.string.pdf_page_cd, page + 1, state.pageCount,
                    ),
                    contentScale = ContentScale.FillBounds,
                    filterQuality = FilterQuality.Medium,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (lockedPage == null) {
            PdfFastScroller(
                pageCount = state.pageCount,
                currentIndex = state.displayPageIndex,
                printPageNumber = state.printPageNumber,
                outlineLabel = state.outlinePathLabel.takeIf { it.isNotBlank() },
                listState = listState,
                onPreview = onFastScrollPreview,
                onFinished = onFastScrollFinished,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(28.dp),
            )
        }
    }

}

private data class ViewportRaw(
    val visible: Set<Int>,
    val primary: Int,
    val firstIndex: Int,
    val firstOffset: Int,
    val scrolling: Boolean,
)

/**
 * Right-side fast scroller for continuous mode.
 * Track ~3dp, touch ≥28dp, thumb min 32dp; auto-hide after idle.
 */
@Composable
private fun PdfFastScroller(
    pageCount: Int,
    currentIndex: Int,
    printPageNumber: Int?,
    outlineLabel: String?,
    listState: LazyListState,
    onPreview: (Int) -> Unit,
    onFinished: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (pageCount <= 1) return
    val scheme = MaterialTheme.colorScheme
    var visible by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var bubblePage by remember { mutableIntStateOf(currentIndex) }
    var lastUiEmitMs by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()

    // Show on list scroll, hide after idle.
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.isScrollInProgress to listState.firstVisibleItemIndex
        }.collect { (scrolling, _) ->
            if (scrolling) {
                visible = true
            } else if (!dragging) {
                delay(PdfRenderQuality.FAST_SCROLL_HIDE_MS)
                if (!dragging) visible = false
            }
        }
    }

    LaunchedEffect(currentIndex) {
        if (!dragging) bubblePage = currentIndex
    }

    val alpha = when {
        dragging -> 1f
        visible -> 0.55f
        else -> 0.12f
    }
    val fraction = if (pageCount <= 1) {
        0f
    } else {
        currentIndex.toFloat() / (pageCount - 1).toFloat()
    }

    var trackHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val thumbH = with(density) { 32.dp.roundToPx() }
    val thumbFrac = if (dragging) {
        bubblePage.toFloat() / (pageCount - 1).toFloat()
    } else {
        fraction
    }.coerceIn(0f, 1f)
    val thumbOffsetY = ((trackHeightPx - thumbH).coerceAtLeast(0) * thumbFrac).roundToInt()

    Box(modifier = modifier) {
        // Touch strip (28dp) + track/thumb — only the right strip, does not cover content.
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(28.dp)
                .onSizeChanged { trackHeightPx = it.height }
                .pointerInput(pageCount) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        dragging = true
                        visible = true
                        val h = size.height.coerceAtLeast(1).toFloat()
                        fun pageAt(y: Float): Int {
                            val f = (y / h).coerceIn(0f, 1f)
                            return (f * (pageCount - 1)).roundToInt().coerceIn(0, pageCount - 1)
                        }
                        var page = pageAt(down.position.y)
                        bubblePage = page
                        val now0 = System.currentTimeMillis()
                        if (now0 - lastUiEmitMs >= PdfRenderQuality.FAST_SCROLL_THROTTLE_MS) {
                            lastUiEmitMs = now0
                            onPreview(page)
                        }
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.pressed } ?: break
                            page = pageAt(change.position.y)
                            bubblePage = page
                            val now = System.currentTimeMillis()
                            if (now - lastUiEmitMs >= PdfRenderQuality.FAST_SCROLL_THROTTLE_MS) {
                                lastUiEmitMs = now
                                onPreview(page)
                            }
                            if (change.positionChanged()) change.consume()
                        } while (event.changes.any { it.pressed })
                        dragging = false
                        onFinished(page)
                        scope.launch {
                            delay(PdfRenderQuality.FAST_SCROLL_HIDE_MS)
                            if (!dragging) visible = false
                        }
                    }
                },
        ) {
            // Visual track 3dp
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp)
                    .width(3.dp)
                    .fillMaxSize()
                    .background(
                        scheme.onSurface.copy(alpha = 0.15f * alpha),
                        RoundedCornerShape(2.dp),
                    ),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 2.dp)
                    .offset { IntOffset(0, thumbOffsetY) }
                    .width(8.dp)
                    .height(32.dp)
                    .background(
                        scheme.primary.copy(alpha = alpha),
                        RoundedCornerShape(4.dp),
                    ),
            )
        }

        if (dragging) {
            Surface(
                color = scheme.surfaceContainerHigh.copy(alpha = 0.94f),
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 36.dp)
                    .offset { IntOffset(0, thumbOffsetY - 24) },
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        text = "PDF ${bubblePage + 1} / $pageCount",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = scheme.onSurface,
                    )
                    if (printPageNumber != null) {
                        // Use print of bubble page if available from parent state (approx during drag).
                        Text(
                            text = "教材页 $printPageNumber",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    if (!outlineLabel.isNullOrBlank()) {
                        Text(
                            text = outlineLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 180.dp),
                        )
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfReadingModeSheet(
    mode: PdfReadingMode,
    showPageButtons: Boolean,
    onSelectMode: (PdfReadingMode) -> Unit,
    onShowButtonsChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val horizontal = mode == PdfReadingMode.HORIZONTAL_PAGED
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = stringResource(R.string.pdf_reading_mode_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(bottom = 12.dp),
            )
            ModeOptionRow(
                label = stringResource(R.string.pdf_reading_mode_vertical),
                selected = mode == PdfReadingMode.VERTICAL_CONTINUOUS,
                onClick = { onSelectMode(PdfReadingMode.VERTICAL_CONTINUOUS) },
            )
            ModeOptionRow(
                label = stringResource(R.string.pdf_reading_mode_horizontal),
                selected = mode == PdfReadingMode.HORIZONTAL_PAGED,
                onClick = { onSelectMode(PdfReadingMode.HORIZONTAL_PAGED) },
            )
            // Flip buttons only apply to horizontal paged mode.
            if (horizontal) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.pdf_reading_mode_show_buttons),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Switch(
                        checked = showPageButtons,
                        onCheckedChange = onShowButtonsChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
