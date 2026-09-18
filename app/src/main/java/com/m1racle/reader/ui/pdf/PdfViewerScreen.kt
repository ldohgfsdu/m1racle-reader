package com.m1racle.reader.ui.pdf

import android.util.Log
import android.content.pm.ApplicationInfo
import android.os.SystemClock

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
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
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m1racle.reader.BuildConfig
import com.m1racle.reader.R
import com.m1racle.reader.data.anchor.DocumentAnchor
import com.m1racle.reader.data.local.HighlightEntity
import com.m1racle.reader.data.local.NoteEntity
import com.m1racle.reader.ui.common.clearContrastScrim
import com.m1racle.reader.pdf.PdfChromeLevel
import com.m1racle.reader.pdf.PdfReadingMode
import com.m1racle.reader.pdf.PdfRenderQuality
import com.m1racle.reader.pdf.AxisOverscrollPolicy
import com.m1racle.reader.pdf.HorizontalPanState
import com.m1racle.reader.pdf.HorizontalZoomPanPhysics
import com.m1racle.reader.pdf.HorizontalTilePlanning
import com.m1racle.reader.pdf.HorizontalPageTransitionDirection
import com.m1racle.reader.pdf.PageStripSlotRole
import com.m1racle.reader.pdf.PageStripTransition
import com.m1racle.reader.pdf.ThreePageStripPhysics
import com.m1racle.reader.pdf.ThreePageStripState
import com.m1racle.reader.pdf.LatestViewportConflator
import com.m1racle.reader.pdf.PdfTileDisplay
import com.m1racle.reader.pdf.PdfTilePlan
import com.m1racle.reader.pdf.PdfGestureTransformController
import com.m1racle.reader.pdf.PdfGesturePhase
import com.m1racle.reader.pdf.PdfGestureFrameSample
import com.m1racle.reader.pdf.PdfGestureFrameTelemetryRing
import com.m1racle.reader.pdf.PdfViewportSessionState
import com.m1racle.reader.pdf.PdfViewportSnapshot
import com.m1racle.reader.pdf.PdfZoomBuckets
import com.m1racle.reader.pdf.TileInteractionPhase
import com.m1racle.reader.pdf.TileOverlaySnapshot
import com.m1racle.reader.pdf.TileRenderPipelinePolicy
import com.m1racle.reader.pdf.ZoomPanPhysics
import com.m1racle.reader.pdf.ZoomPanPhysicsProfile
import com.m1racle.reader.pdf.ZoomPanPoint
import com.m1racle.reader.pdf.ZoomPanPointer
import com.m1racle.reader.pdf.ZoomPanPointerMode
import com.m1racle.reader.pdf.ZoomedPageTransitionPhase
import com.m1racle.reader.pdf.asHorizontalPanState
import com.m1racle.reader.pdf.ContinuousScrollDirection
import com.m1racle.reader.pdf.ContinuousScrollPhase
import com.m1racle.reader.pdf.ContinuousViewportSnapshot
import com.m1racle.reader.pdf.ZoomPageOverlayMath
import com.m1racle.reader.pdf.PageTap
import com.m1racle.reader.pdf.export.ChapterExportState
import com.m1racle.reader.ui.theme.M1racleReaderTheme
import com.m1racle.reader.ui.annotation.AnnotationOverlayItem
import com.m1racle.reader.ui.annotation.AnnotationsManagementSheet
import com.m1racle.reader.ui.annotation.NoteEditDialog
import com.m1racle.reader.ui.annotation.PdfHighlightLayer
import com.m1racle.reader.ui.material.GlassBackdropHost
import com.m1racle.reader.ui.material.GlassBackdropSource
import com.m1racle.reader.ui.material.GlassEnvironment
import com.m1racle.reader.ui.material.GlassMaterialRole
import com.m1racle.reader.ui.material.LocalGlassBackdropHost
import com.m1racle.reader.ui.material.LocalGlassEnvironment
import com.m1racle.reader.ui.material.glassMaterial
import com.m1racle.reader.ui.theme.LocalReaderDarkTheme
import com.m1racle.reader.ui.reader.ReaderBookmarkController
import com.m1racle.reader.ui.reader.ReaderHighlightController
import com.m1racle.reader.ui.reader.ReaderNoteController
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.yield

/**
 * PDF reader: jump / scrubber / print anchors (P0.2A) + on-demand thumbnails (P0.2B)
 * + vertical continuous / horizontal paged reading modes.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
// ── Chrome show/hide tuning constants ─────────────────────────────────────
// All scroll-driven chrome thresholds live in PdfChromeScrollParams
// (pdf package); the UI layer only carries the menu/cooldown constants.

/** Capsule anchored jump-card payload (rendered by PdfChrome below the capsule). */
data class JumpCardParams(
    val currentHumanPage: Int,
    val pageCount: Int,
    val onConfirm: (Int) -> Unit,
    val onCancel: () -> Unit = {},
)

data class PdfCapsuleData(
    val title: String,
    val positionLine: String?,
    val progressPercent: Int?,
    val printDetail: String? = null,
)

private data class NoteEditorTarget(
    val anchor: DocumentAnchor,
    val pageIndex: Int,
    val note: NoteEntity?,
    val returnToManagement: Boolean,
)

/** Duration of the menu card's reverse morph back into the circle (ms). */
private const val MENU_CLOSE_MORPH_MS = 200L

private val CapsuleMorphSpring = spring<Float>(dampingRatio = 0.8f, stiffness = 450f)



@Composable
fun PdfViewerScreen(
    viewModel: PdfViewerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** R23/R26/R27 阅读资产控制器；UI 不直接访问 Repository/DAO。 */
    bookmarkController: ReaderBookmarkController? = null,
    highlightController: ReaderHighlightController? = null,
    noteController: ReaderNoteController? = null,
    initialAnchor: DocumentAnchor? = null,
    onEditBody: ((Int) -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val embeddedOffer by viewModel.embeddedOutlineOffer.collectAsStateWithLifecycle()
    val importMessage by viewModel.importMessage.collectAsStateWithLifecycle()
    val exportPlan by viewModel.exportPlan.collectAsStateWithLifecycle()

    // Keyboard contract (r19): while the PDF reader is on screen the window
    // uses ADJUST_NOTHING, so the IME overlays the content instead of
    // resizing it — the expanded location capsule stays top-anchored, the
    // Reader Chrome is never pushed away and the PDF viewport never changes.
    // The manifest default (ADJUST_RESIZE) is restored when the reader leaves.
    val readerWindowContext = LocalContext.current
    DisposableEffect(readerWindowContext) {
        val activity = readerWindowContext as? android.app.Activity
        val previousMode = activity?.window?.attributes?.softInputMode
        activity?.window?.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING,
        )
        onDispose {
            if (activity != null && previousMode != null) {
                activity.window.setSoftInputMode(previousMode)
            }
        }
    }

    // Scroll-driven chrome state machine (pure logic, unit-tested). The UI
    // layer feeds ONLY the measured content anchor delta (ground truth screen
    // motion), mapped by mapScrollToContentMotion — never the raw delta sign.
    val chromeStateMachine = remember { com.m1racle.reader.pdf.PdfChromeScrollStateMachine() }
    var chromeLevel by remember { mutableStateOf(com.m1racle.reader.pdf.PdfChromeLevel.FULL) }
    fun applyChromeChange(c: com.m1racle.reader.pdf.ChromeStateChange) {
        chromeLevel = c.levelAfter
        if (BuildConfig.READER_PERFORMANCE_EXPERIMENTS_ENABLED) {
            Log.i(
                "CHROME_STATE",
                "userGestureId=${c.userGestureId} dir=${c.direction} rawDelta=${c.rawDeltaPx} " +
                    "anchorDeltaY=${c.anchorDeltaY} effectiveDistanceDp=${c.effectiveDistanceDp} " +
                    "levelBefore=${c.levelBefore} levelAfter=${c.levelAfter} reason=${c.reason}",
            )
        }
    }


    // ── Chrome scroll-driven state machine (rules live in PdfChromeScrollStateMachine) ──
    // Direction semantics: the adapter below maps raw scrollDeltaY to
    // CONTENT_MOVING_UP/DOWN (PDF content's actual on-screen motion); the
    // state machine never interprets raw deltas. Programmatic scrolls
    // (phase IDLE: settle/restore/snap) never feed the machine.
    var chromeGestureActive by remember { mutableStateOf(false) }
    var chromeFlingAccumDp by remember { mutableFloatStateOf(0f) }
    val chromeDensity = LocalDensity.current
    // Direction-diagnostic state (temporary, DEBUG builds only): last logged
    // anchor screenY and throttle for the CHROME_DIR stream.
    var lastChromeDirLogMs by remember { mutableLongStateOf(0L) }
    var lastChromeAnchorScreenY by remember { mutableFloatStateOf(0f) }
    val chromeAppContext = androidx.compose.ui.platform.LocalContext.current
    val chromeDebuggable = remember(chromeAppContext) {
        (chromeAppContext.applicationInfo.flags and
            android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    // Initial visibility lock: the document opens in FULL and stays FULL —
    // init scrolls / layout settling / programmatic restore may not change
    // chromeLevel; only real user scrolls may. The capsule has NO timer: its
    // visibility is purely `chromeLevel == FULL`.
    var chromeInitialLockUntilMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        chromeInitialLockUntilMs = android.os.SystemClock.uptimeMillis() +
            com.m1racle.reader.pdf.PdfChromeScrollParams().initialLockMs
        delay(com.m1racle.reader.pdf.PdfChromeScrollParams().initialLockMs)
        // Lock expiry is passive: the state machine checks the timestamp itself.
    }

    val toggleChromeByTap: () -> Unit = {
        chromeStateMachine.onCenterTap()?.let { applyChromeChange(it) }
    }
    val topBarMode by viewModel.topBarMode.collectAsStateWithLifecycle()

    // ── System bar presentation (PDF reader only) ────────────────────────────
    // IMMERSIVE hides the status bar. Do NOT use transient swipe-to-reveal:
    // a downward page swipe would flash a SystemUI scrim and jump the PDF.
    // After a shade pull, delayed re-hide + focus-regain re-enter immersive.
    // Never read live statusBars insets in this composition — that recomposes
    // the whole reader on every bar animation. Leaving MUST restore bars.
    val view = LocalView.current
    LaunchedEffect(topBarMode) {
        applyPdfSystemBars(view, topBarMode)
        if (topBarMode != com.m1racle.reader.pdf.PdfTopBarMode.IMMERSIVE) return@LaunchedEffect
        while (isActive) {
            delay(com.m1racle.reader.pdf.PdfImmersiveSystemBars.REHIDE_DELAY_MS)
            if (com.m1racle.reader.pdf.PdfImmersiveSystemBars.shouldReassertHide(
                    topBarMode,
                    pdfStatusBarVisiblePx(view),
                )
            ) {
                applyPdfSystemBars(view, com.m1racle.reader.pdf.PdfTopBarMode.IMMERSIVE)
            }
        }
    }
    DisposableEffect(view, topBarMode) {
        if (topBarMode != com.m1racle.reader.pdf.PdfTopBarMode.IMMERSIVE) {
            return@DisposableEffect onDispose { }
        }
        val listener = android.view.ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) applyPdfSystemBars(view, com.m1racle.reader.pdf.PdfTopBarMode.IMMERSIVE)
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener(listener)
        onDispose { view.viewTreeObserver.removeOnWindowFocusChangeListener(listener) }
    }
    DisposableEffect(view) {
        onDispose {
            applyPdfSystemBars(view, com.m1racle.reader.pdf.PdfTopBarMode.NORMAL)
            if (BuildConfig.READER_PERFORMANCE_EXPERIMENTS_ENABLED) {
                Log.i("PDF_SYSBARS", "leave reader → restore all system bars")
            }
        }
    }

    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // THE more control state (single source of truth — ReaderMoreState, the
    // explicit 8-state machine): the three-dot circle morphs into the menu
    // card; the card can keep changing into a Foreground Advanced Card (R25).
    var moreState by remember { mutableStateOf<ReaderMoreState>(ReaderMoreState.CLOSED) }
    // Morph-back phase: the card is shrinking back into the circle before the
    // state flips (also the handover window before a secondary surface opens).
    var moreClosing by remember { mutableStateOf(false) }
    // Advanced reverse-morph phase: while true the state STAYS ADVANCED_* and
    // the Foreground Advanced Card collapses back to its OWN source row; the
    // host calls onChildCollapsed when the morph completes (→ MAIN). The
    // collapse identity is the state itself — no origin fallback exists.
    var childClosing by remember { mutableStateOf(false) }
    // When user taps 阅读方式/阅读界面 while a Child is open: close Child first,
    // then open the pending inline section after reverse-morph completes.
    var pendingInlineAfterChild by remember { mutableStateOf<ReaderMoreState?>(null) }
    // r26 §17: a document switch must close any open child and drop focus /
    // thumbnail state — never leave a half-expanded overlay or stale source
    // bounds on the next document.
    val currentDocumentId = (uiState as? PdfViewerUiState.Ready)?.document?.id
    LaunchedEffect(currentDocumentId) {
        if (currentDocumentId != null &&
            (moreState != ReaderMoreState.CLOSED || moreClosing || childClosing)
        ) {
            moreState = ReaderMoreState.CLOSED
            moreClosing = false
            childClosing = false
            pendingInlineAfterChild = null
        }
    }
    // Capsule anchored expansion (jump card): the capsule is its own header
    // and the body grows from its bottom edge. Mutually exclusive with the
    // overflow menu; overlay controller state — never touches chrome levels.
    var capsuleExpanded by remember { mutableStateOf(false) }
    val closeCapsule: () -> Unit = {
        if (capsuleExpanded) capsuleExpanded = false
    }
    val toggleCapsule: () -> Unit = { capsuleExpanded = !capsuleExpanded }
    val menuActive: Boolean = moreState != ReaderMoreState.CLOSED || moreClosing
    val openPdfMenu: () -> Unit = {
        if (!menuActive) {
            capsuleExpanded = false // overlay 互斥
            moreState = ReaderMoreStateMachine.onToggleOpen(moreState)
        }
    }
    val closePdfMenu: () -> Unit = {
        moreState = ReaderMoreState.CLOSED
        moreClosing = false
        // Menu open/close freezes scroll-driven chrome; on close, clear all
        // accumulated distance / direction / fling residue and start a fresh
        // gesture session from the current chrome level (rule 8).
        chromeStateMachine.resetForNewSession()
        chromeFlingAccumDp = 0f
        chromeGestureActive = false
    }
    var thumbRangePick by remember { mutableStateOf(false) }
    var outlineEditor by remember { mutableStateOf<OutlineEditorState?>(null) }
    val scope = rememberCoroutineScope()

    // ── R23/R26/R27 reading assets (document-scoped, UI consumes controllers only) ──
    var bookmarkedPages by remember(currentDocumentId) { mutableStateOf<Set<Int>>(emptySet()) }
    var annotationManagementVisible by remember(currentDocumentId) { mutableStateOf(false) }
    var managementHighlights by remember(currentDocumentId) {
        mutableStateOf<List<HighlightEntity>>(emptyList())
    }
    var managementNotes by remember(currentDocumentId) {
        mutableStateOf<List<NoteEntity>>(emptyList())
    }
    var highlightVersion by remember(currentDocumentId) { mutableIntStateOf(0) }
    var noteVersion by remember(currentDocumentId) { mutableIntStateOf(0) }
    var noteEditorTarget by remember(currentDocumentId) {
        mutableStateOf<NoteEditorTarget?>(null)
    }
    // Close / dismiss (Back AND outside tap): level-returning. The Advanced
    // Card collapses back to Main (never straight to the circle); the menu
    // reverse-morphs into the circle, then the state flips.
    val requestClosePdfMenu: () -> Unit = {
        when {
            moreState.isChild() && !childClosing -> {
                // Reverse morph to the source row: the state stays ADVANCED_*
                // (the card's identity) until the morph completes.
                childClosing = true
            }
            moreState != ReaderMoreState.CLOSED && !moreClosing -> {
                moreClosing = true
                scope.launch {
                    delay(MENU_CLOSE_MORPH_MS)
                    closePdfMenu()
                }
            }
        }
    }
    // Advanced reverse-morph completed (host): restore MAIN, or pending inline.
    val onChildCollapsed: () -> Unit = {
        childClosing = false
        val pending = pendingInlineAfterChild
        pendingInlineAfterChild = null
        moreState = ReaderMoreTransitions.afterChildCollapsed(pending)
    }
    // Collapse the foreground Advanced Card through its reverse morph — used
    // by header/Back/outside taps AND by business completion (保存/取消/清空/
    // SAF 接管): every advanced interface returns to its OWN source row, then
    // the state machine lands on MAIN.
    val collapseChild: () -> Unit = {
        if (moreState.isChild() && !childClosing) childClosing = true
    }
    // Back priority: Child → inline → Parent dismiss (ReaderMoreTransitions).
    BackHandler(enabled = menuActive || capsuleExpanded) {
        when (ReaderMoreTransitions.onBack(moreState, childClosing, capsuleExpanded)) {
            ReaderMoreBackAction.CLOSE_CAPSULE -> closeCapsule()
            ReaderMoreBackAction.CLOSE_CHILD -> childClosing = true
            ReaderMoreBackAction.COLLAPSE_INLINE -> moreState = ReaderMoreState.MAIN
            ReaderMoreBackAction.DISMISS_PARENT -> requestClosePdfMenu()
        }
    }
    // Section toggle: mutual exclusion with Child — if Child open, close it
    // first then expand inline; else same-card accordion handover.
    val switchPdfMenuSubmenu: (ReaderMoreState) -> Unit = { next ->
        val decision = ReaderMoreTransitions.onSectionToggle(moreState, next, childClosing)
        if (decision.closeChildFirst) {
            pendingInlineAfterChild = decision.pendingInline
            if (!childClosing) childClosing = true
        } else {
            decision.immediateState?.let { moreState = it }
        }
    }

    val createZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            viewModel.runExportToUri(uri)
        }
    }
    val createPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        if (uri != null) {
            viewModel.runExportToUri(uri)
        }
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
                capsuleData = PdfCapsuleData(
                    title = stringResource(R.string.viewer_loading),
                    positionLine = null,
                    progressPercent = null,
                ),
                onBack = onBack,
                onCapsuleToggle = null,
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
                capsuleData = PdfCapsuleData(
                    title = state.displayName ?: stringResource(R.string.viewer_title_fallback),
                    positionLine = null,
                    progressPercent = null,
                ),
                onBack = onBack,
                onCapsuleToggle = null,
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
            val isVertical = state.readingMode == PdfReadingMode.VERTICAL_CONTINUOUS
            val pageJump by viewModel.pageJump.collectAsStateWithLifecycle()

            // Reuse the reader's canonical page transition authority for search hits.
            LaunchedEffect(initialAnchor, state.document.id) {
                val target = initialAnchor as? DocumentAnchor.PdfPage ?: return@LaunchedEffect
                viewModel.goToPage(target.page)
            }

            LaunchedEffect(bookmarkController, state.document.id) {
                bookmarkedPages = bookmarkController?.bookmarkedLocations() ?: emptySet()
            }
            LaunchedEffect(
                annotationManagementVisible,
                highlightVersion,
                noteVersion,
                highlightController,
                noteController,
                state.document.id,
            ) {
                if (annotationManagementVisible) {
                    managementHighlights = highlightController?.listForDocument().orEmpty()
                    managementNotes = noteController?.list().orEmpty()
                }
            }

            suspend fun editorTargetForAnchor(
                anchor: DocumentAnchor,
                pageIndex: Int,
                returnToManagement: Boolean,
            ): NoteEditorTarget? {
                val notes = noteController ?: return null
                return NoteEditorTarget(
                    anchor = anchor,
                    pageIndex = pageIndex,
                    note = notes.linkedAt(anchor),
                    returnToManagement = returnToManagement,
                )
            }

            suspend fun editorTargetForHighlight(
                highlight: HighlightEntity,
                returnToManagement: Boolean,
            ): NoteEditorTarget? {
                val highlights = highlightController ?: return null
                val anchor = highlights.anchorOf(highlight) ?: return null
                return editorTargetForAnchor(
                    anchor = anchor,
                    pageIndex = highlight.location,
                    returnToManagement = returnToManagement,
                )
            }

            suspend fun editorTargetForNote(
                note: NoteEntity,
                returnToManagement: Boolean,
            ): NoteEditorTarget? {
                val notes = noteController ?: return null
                val anchor = notes.anchorOf(note) ?: return null
                return NoteEditorTarget(
                    anchor = anchor,
                    pageIndex = note.location,
                    note = note,
                    returnToManagement = returnToManagement,
                )
            }

            val pageAnnotation: @Composable androidx.compose.foundation.layout.BoxScope.(Int) -> Unit = { page ->
                var pageItems by remember(state.document.id, page) {
                    mutableStateOf<List<AnnotationOverlayItem>>(emptyList())
                }
                LaunchedEffect(page, highlightController, highlightVersion) {
                    pageItems = highlightController?.itemsForPage(page).orEmpty()
                }
                var textPage by remember(state.document.uri, page) {
                    mutableStateOf<com.m1racle.reader.pdf.PdfSelectablePage?>(null)
                }
                var textError by remember(state.document.uri, page) { mutableStateOf<String?>(null) }
                LaunchedEffect(state.document.uri, page) {
                    try {
                        textPage = viewModel.selectableText(page)
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { textError = "此页文字读取失败，请重新打开文档后重试" }
                }
                if (highlightController != null) {
                    PdfHighlightLayer(
                        pageIndex = page,
                        items = pageItems,
                        selectionEnabled = true,
                        textPage = textPage,
                        onSelectionUnavailable = {
                            scope.launch { snackbarHostState.showSnackbar(textError ?: textPage?.unavailableReason ?: "正在读取此页文字，请稍后长按选择") }
                        },
                        onSelectCompleted = { selection ->
                            scope.launch {
                                when (highlightController.commitHighlight(selection)) {
                                    is ReaderHighlightController.CommitResult.Success -> {
                                        highlightVersion++
                                        snackbarHostState.showSnackbar(
                                            readerWindowContext.getString(R.string.reader_highlight_added),
                                        )
                                    }
                                    is ReaderHighlightController.CommitResult.Failure -> {
                                        snackbarHostState.showSnackbar(
                                            readerWindowContext.getString(R.string.reader_highlight_failed),
                                        )
                                    }
                                }
                            }
                        },
                        onDeleteRequested = { itemId, _ ->
                            scope.launch {
                                highlightController.deleteHighlight(itemId)
                                highlightVersion++
                                snackbarHostState.showSnackbar(
                                    readerWindowContext.getString(R.string.reader_annotation_deleted),
                                )
                            }
                        },
                        onNoteRequested = if (noteController != null) {
                            { item, selectedPage ->
                                scope.launch {
                                    noteEditorTarget = editorTargetForAnchor(
                                        anchor = item.anchor,
                                        pageIndex = selectedPage,
                                        returnToManagement = false,
                                    )
                                }
                            }
                        } else {
                            null
                        },
                    )
                }
            }
            // R6: TopBar is stable and always present. Horizontal controls are viewport overlays.

            val capsuleData = PdfCapsuleData(
                title = state.currentOutlineNode?.title?.takeIf { it.isNotBlank() }
                    ?: state.document.displayName,
                positionLine = state.pageLabel.takeIf { state.pageCount > 0 },
                progressPercent = if (state.pageCount > 0) {
                    kotlin.math.round(
                        (((state.pageIndex + 1) * 100.0) / state.pageCount),
                    ).toInt().coerceIn(0, 100)
                } else {
                    null
                },
                printDetail = printLabel.takeIf { printNum != null },
            )
            // Advanced Cards are Reader overlays (like Main), not sheets:
            // the chrome keeps its normal level (the capsule retracts via
            // menuActive; the back circle stays).
            PdfChrome(
                capsuleData = capsuleData,
                onBack = onBack,
                onCapsuleToggle = toggleCapsule,
                chromeLevel = chromeLevel,
                capsuleExpanded = capsuleExpanded,
                onCapsuleDismiss = closeCapsule,
                jumpCardParams = JumpCardParams(
                    currentHumanPage = state.pageIndex + 1,
                    pageCount = state.pageCount,
                    onConfirm = { human ->
                        if (viewModel.jumpToHumanPage(human)) closeCapsule()
                    },
                    onCancel = closeCapsule,
                ),
                menuExpanded = moreState != ReaderMoreState.CLOSED,
                menuClosing = moreClosing,
                menuState = moreState,
                onMenuToggle = openPdfMenu,
                onMenuDismiss = requestClosePdfMenu,
                onSubmenuChange = switchPdfMenuSubmenu,
                childClosing = childClosing,
                onChildCollapsed = onChildCollapsed,
                onPreparingLayoutReady = { target ->
                    // Promote PREPARING_* → CHILD_* after Main geometry is stable.
                    if (moreState.isPreparing() && target.isChild()) {
                        moreState = target
                    }
                },
                nestedData = ReaderAdvancedData(
                    textbookPage = TextbookPageAdvancedData(
                        currentPdfHumanPage = state.pageIndex + 1,
                        currentPrint = state.printPageNumber,
                        isExact = state.isPrintExactAnchor,
                        onConfirm = { print ->
                            viewModel.setPrintPageForCurrent(print)
                            // 保存后 Advanced → 逆变换回来源 row → Main（层级返回）
                            collapseChild()
                        },
                        onClear = { viewModel.clearPrintPageForCurrent() },
                    ),
                    outline = OutlineAdvancedData(
                        nodes = state.outlineNodes,
                        currentPageIndex = state.pageIndex,
                        hasEmbeddedDrafts = viewModel.hasCachedEmbeddedOutline(),
                        onJumpToNode = { node ->
                            viewModel.goToPage(node.startPageIndex) // 卡保持打开
                        },
                        onRequestEdit = { draft ->
                            outlineEditor = draft
                            collapseChild() // 编辑器 dialog 接管（先逆变换回教材目录 row）
                        },
                        onDelete = viewModel::deleteOutlineNode,
                        onImportEmbedded = {
                            if (viewModel.hasCachedEmbeddedOutline()) {
                                viewModel.requestEmbeddedImportUi()
                            } else {
                                viewModel.rescanEmbeddedOutline()
                            }
                        },
                    ),
                    thumbnails = ThumbnailsAdvancedData(
                        pageCount = state.pageCount,
                        currentPageIndex = state.pageIndex,
                        printAnchors = state.printAnchors,
                        loader = viewModel.thumbnailLoader(),
                        rangePickMode = thumbRangePick,
                        onPageSelected = { index ->
                            viewModel.goToPage(index) // 卡保持打开，连续浏览
                        },
                        onRangePicked = if (thumbRangePick) {
                            { start, end ->
                                outlineEditor = outlineEditor?.copy(
                                    startPageIndex = start,
                                    endPageIndex = end,
                                )
                                thumbRangePick = false
                                collapseChild()
                            }
                        } else {
                            null
                        },
                    ),
                    export = ExportAdvancedData(
                        plan = exportPlan,
                        documentPageCount = state.pageCount,
                        allNodes = state.outlineNodes,
                        initialFormat = viewModel.lastExportFormat,
                        onSelectNode = viewModel::beginExportNode,
                        onConfirm = { start0, end0, format ->
                            val name = viewModel.confirmExportRange(start0, end0, format)
                            if (name != null) {
                                collapseChild() // 系统流程接管（先逆变换回导出章节 row）
                                when (format) {
                                    com.m1racle.reader.pdf.export.ChapterExportFormat.ZIP_PACKAGE ->
                                        createZipLauncher.launch(name)
                                    com.m1racle.reader.pdf.export.ChapterExportFormat.PDF_ANNOTATED ->
                                        createPdfLauncher.launch(name)
                                }
                            }
                        },
                    ),
                ),
                submenuEntries = listOf(
                    PdfChromeSubmenuEntry(
                        kind = ReaderMoreState.INLINE_READING_MODE,
                        icon = Icons.Outlined.SwapVert,
                        label = stringResource(R.string.pdf_reading_mode_open),
                    ),
                    PdfChromeSubmenuEntry(
                        kind = ReaderMoreState.INLINE_READING_UI,
                        icon = Icons.Outlined.AutoAwesome,
                        label = stringResource(R.string.pdf_top_bar_mode),
                    ),
                ),
                plainMenuItems = listOfNotNull(
                    onEditBody?.let { edit ->
                        PdfChromeMenuItem(
                            icon = Icons.Outlined.Edit,
                            label = "编辑正文",
                            onClick = { viewModel.flushProgress(); edit(state.pageIndex) },
                        )
                    },
                    // Four advanced entries — each opens nested child from its own row.
                    PdfChromeMenuItem(
                        icon = Icons.Outlined.Tag,
                        label = stringResource(R.string.pdf_set_print_page),
                        closeOnClick = false,
                        onClick = {
                            moreState = ReaderMoreStateMachine.onOpenChild(
                                moreState,
                                ReaderMoreState.CHILD_PAGE_MAPPING,
                            )
                        },
                    ),
                    PdfChromeMenuItem(
                        icon = Icons.AutoMirrored.Outlined.MenuBook,
                        label = stringResource(R.string.pdf_outline_open),
                        closeOnClick = false,
                        onClick = {
                            moreState = ReaderMoreStateMachine.onOpenChild(
                                moreState,
                                ReaderMoreState.CHILD_TOC,
                            )
                        },
                    ),
                    PdfChromeMenuItem(
                        icon = Icons.Outlined.GridView,
                        label = stringResource(R.string.pdf_thumbs_open),
                        closeOnClick = false,
                        onClick = {
                            thumbRangePick = false
                            viewModel.prepareThumbnails()
                            moreState = ReaderMoreStateMachine.onOpenChild(
                                moreState,
                                ReaderMoreState.CHILD_THUMBNAILS,
                            )
                        },
                    ),
                    PdfChromeMenuItem(
                        icon = Icons.Outlined.IosShare,
                        label = stringResource(R.string.pdf_export_entry),
                        closeOnClick = false,
                        onClick = {
                            viewModel.beginExportCurrent()
                            moreState = ReaderMoreStateMachine.onOpenChild(
                                moreState,
                                ReaderMoreState.CHILD_EXPORT,
                            )
                        },
                    ),
                    if (highlightController != null) {
                        PdfChromeMenuItem(
                            icon = Icons.Outlined.Notes,
                            label = stringResource(R.string.reader_annotations_open),
                            closeOnClick = true,
                            onClick = { annotationManagementVisible = true },
                        )
                    } else {
                        null
                    },
                    if (bookmarkController != null) {
                        val bookmarked = bookmarkController.isBookmarked(
                            bookmarkedPages,
                            state.pageIndex,
                        )
                        PdfChromeMenuItem(
                            icon = if (bookmarked) Icons.Filled.Bookmarks else Icons.Outlined.Bookmarks,
                            label = stringResource(
                                if (bookmarked) {
                                    R.string.reader_bookmark_remove
                                } else {
                                    R.string.reader_bookmark_add
                                },
                            ),
                            closeOnClick = true,
                            onClick = {
                                val page = state.pageIndex
                                scope.launch {
                                    when (bookmarkController.toggle(page)) {
                                        ReaderBookmarkController.ToggleResult.Added -> {
                                            bookmarkedPages = bookmarkedPages + page
                                            snackbarHostState.showSnackbar(
                                                readerWindowContext.getString(R.string.reader_bookmark_added),
                                            )
                                        }
                                        ReaderBookmarkController.ToggleResult.Removed -> {
                                            bookmarkedPages = bookmarkedPages - page
                                            snackbarHostState.showSnackbar(
                                                readerWindowContext.getString(R.string.reader_bookmark_removed),
                                            )
                                        }
                                    }
                                }
                            },
                        )
                    } else {
                        null
                    },
                ),
                readingModeOptions = listOf(
                    PdfChromeMenuOption(
                        label = stringResource(R.string.pdf_reading_mode_continuous),
                        selected = state.readingMode == PdfReadingMode.VERTICAL_CONTINUOUS,
                        onClick = {
                            viewModel.setReadingMode(PdfReadingMode.VERTICAL_CONTINUOUS)
                        },
                    ),
                    PdfChromeMenuOption(
                        label = stringResource(R.string.pdf_reading_mode_horizontal),
                        selected = state.readingMode == PdfReadingMode.HORIZONTAL_PAGED,
                        onClick = {
                            viewModel.setReadingMode(PdfReadingMode.HORIZONTAL_PAGED)
                        },
                    ),
                ),
                topBarModeOptions = listOf(
                    PdfChromeMenuOption(
                        label = stringResource(R.string.pdf_top_bar_mode_normal),
                        selected = topBarMode == com.m1racle.reader.pdf.PdfTopBarMode.NORMAL,
                        onClick = {
                            viewModel.setTopBarMode(com.m1racle.reader.pdf.PdfTopBarMode.NORMAL)
                        },
                    ),
                    PdfChromeMenuOption(
                        label = stringResource(R.string.pdf_top_bar_mode_immersive),
                        selected = topBarMode == com.m1racle.reader.pdf.PdfTopBarMode.IMMERSIVE,
                        onClick = {
                            viewModel.setTopBarMode(com.m1racle.reader.pdf.PdfTopBarMode.IMMERSIVE)
                        },
                    ),
                    // Material rows removed (r26.1): Liquid is the only chrome surface.
                ),
                modifier = Modifier.fillMaxSize(),
                content = {
                    when (state.readingMode) {
                        PdfReadingMode.VERTICAL_CONTINUOUS -> {
                            // R7 V2 production path (the legacy PdfVerticalZoomLockArea
                            // zoom-lock implementation was deleted in the R25 closure —
                            // single continuous-viewport path only). Static tap toggles
                            // the chrome (fully decoupled from scroll:
                            // detectTapGestures consumes nothing until a stationary up, so
                            // drag/zoom/fling paths are untouched).
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectTapGestures(onTap = {
                                            if (menuActive) {
                                                closePdfMenu()
                                            } else {
                                                toggleChromeByTap()
                                            }
                                        })
                                    },
                            ) {
                            ContinuousDocumentViewport(
                                state = state,
                                pageJump = pageJump,
                                onPageJumpConsumed = viewModel::onContinuousPageJumpConsumed,
                                onViewportWidthPx = viewModel::onViewportWidth,
                                onContinuousGeometryReady = viewModel::onContinuousGeometryReady,
                                onContinuousEntryRestoreApplied = viewModel::onContinuousEntryRestoreApplied,
                                onDocumentStateSettled = viewModel::commitContinuousDocumentState,
                                onViewportSessionSettled = viewModel::commitViewportSession,
                                onViewportSessionPreview = viewModel::previewViewportSession,
                                onTilePlan = viewModel::requestPredictiveTiles,
                                tileOverlaySnapshots = viewModel.tileOverlaySnapshot,
                                onZoomGestureActive = viewModel::setZoomGestureActive,
                                onPageSettled = viewModel::onVerticalPageSettled,
                                onViewportChanged = { snapshot ->
                                    val now = android.os.SystemClock.uptimeMillis()
                                    val phase = snapshot.phase
                                    val chromeFrozen = menuActive || capsuleExpanded
                                    if (!chromeFrozen) {
                                        // Gesture boundary: end clears residue (keeps level),
                                        // begin starts a new gesture id.
                                        val wasActive = chromeGestureActive
                                        if (wasActive && phase == com.m1racle.reader.pdf.ContinuousScrollPhase.IDLE) {
                                            chromeStateMachine.endGesture()
                                        }
                                        chromeGestureActive = phase != com.m1racle.reader.pdf.ContinuousScrollPhase.IDLE
                                        if (!wasActive && chromeGestureActive) {
                                            chromeStateMachine.beginGesture()
                                        }

                                        // Adapter layer: ground truth direction from the
                                        // MEASURED content anchor displacement (same PDF
                                        // anchor's on-screen y delta), never from the raw
                                        // scrollDeltaY sign. Programmatic scrolls (phase
                                        // IDLE) never feed the machine; neither does
                                        // anything during the init lock.
                                        if (phase != com.m1racle.reader.pdf.ContinuousScrollPhase.IDLE &&
                                            now >= chromeInitialLockUntilMs
                                        ) {
                                            val rawDelta = snapshot.scrollDeltaY
                                            val anchorDeltaY = snapshot.contentAnchorDeltaY
                                            val dir = com.m1racle.reader.pdf
                                                .mapScrollToContentMotion(anchorDeltaY)
                                            // Temporary direction diagnostic (DEBUG only):
                                            // correlates the raw scroll delta with the
                                            // measured on-screen anchor motion. MUST match
                                            // the eye: content visibly up => anchorDeltaY < 0,
                                            // visibly down => anchorDeltaY > 0.
                                            if (BuildConfig.READER_PERFORMANCE_EXPERIMENTS_ENABLED &&
                                                chromeDebuggable && dir != null &&
                                                now - lastChromeDirLogMs >= 80L
                                            ) {
                                                lastChromeDirLogMs = now
                                                Log.i(
                                                    "CHROME_DIR",
                                                    "gestureId=${chromeStateMachine.userGestureId} " +
                                                        "rawDelta=$rawDelta consumedDelta=$rawDelta " +
                                                        "oldAnchorY=$lastChromeAnchorScreenY " +
                                                        "newAnchorY=${snapshot.contentAnchorScreenY} " +
                                                        "anchorDeltaY=$anchorDeltaY mapped=$dir",
                                                )
                                                lastChromeAnchorScreenY = snapshot.contentAnchorScreenY
                                            }
                                            if (dir != null) {
                                                val deltaDp = with(chromeDensity) {
                                                    kotlin.math.abs(anchorDeltaY) / density
                                                }
                                                chromeStateMachine
                                                    .onScrollSample(dir, deltaDp, rawDelta, anchorDeltaY, now)
                                                    .forEach { applyChromeChange(it) }
                                                // Fling fast path obeys the same direction rules.
                                                if (phase == com.m1racle.reader.pdf.ContinuousScrollPhase.FLINGING) {
                                                    chromeFlingAccumDp += deltaDp
                                                    val speed = kotlin.math.abs(snapshot.velocityYDpPerSec)
                                                    val minSpeed = com.m1racle.reader.pdf
                                                        .PdfChromeScrollParams().flingMinSpeedDpPerSec
                                                    val minDist = com.m1racle.reader.pdf
                                                        .PdfChromeScrollParams().flingMinDistanceDp
                                                    if (speed >= minSpeed && chromeFlingAccumDp >= minDist) {
                                                        val change = chromeStateMachine.onFling(
                                                            dir = dir,
                                                            velocityDpPerSec = speed,
                                                            distanceDp = chromeFlingAccumDp,
                                                            anchorDeltaYPx = anchorDeltaY,
                                                            nowMs = now,
                                                        )
                                                        if (change != null) applyChromeChange(change)
                                                        chromeFlingAccumDp = 0f
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    viewModel.onContinuousViewportChanged(snapshot)
                                },
                                onEnsureAround = viewModel::ensurePagesAround,
                                onRetryPage = { viewModel.ensurePagesAround(it) },
                                pageAnnotation = pageAnnotation,
                                modifier = Modifier.fillMaxSize(),
                            )
                            }
                        }
                        PdfReadingMode.HORIZONTAL_PAGED -> {
                            PdfHorizontalPagedArea(
                                state = state,
                                pageJump = pageJump,
                                onToggleChrome = {
                                    if (menuActive) closePdfMenu()
                                    else toggleChromeByTap()
                                },
                                onPrevious = viewModel::goToPreviousPage,
                                onNext = viewModel::goToNextPage,
                                onPageLabelClick = toggleCapsule,
                                onViewportWidthPx = viewModel::onViewportWidth,
                                onPageChanged = viewModel::onHorizontalPageSettled,
                                onZoomScaleSettled = viewModel::onZoomScaleSettled,
                                onViewportSessionSettled = viewModel::commitViewportSession,
                                onViewportSessionPreview = viewModel::previewViewportSession,
                                onTilePlan = viewModel::requestPredictiveTiles,
                                tileOverlaySnapshots = viewModel.tileOverlaySnapshot,
                                onZoomGestureActive = viewModel::setZoomGestureActive,
                                onEnsureAround = viewModel::ensurePagesAround,
                                pageAnnotation = pageAnnotation,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                },
            )

            if (annotationManagementVisible) {
                AnnotationsManagementSheet(
                    highlights = managementHighlights,
                    notes = managementNotes,
                    onDismiss = { annotationManagementVisible = false },
                    onJump = { highlight ->
                        annotationManagementVisible = false
                        viewModel.goToPage(highlight.location)
                    },
                    onDeleteHighlight = { highlight ->
                        scope.launch {
                            highlightController?.deleteHighlight(highlight.id)
                            highlightVersion++
                            snackbarHostState.showSnackbar(
                                readerWindowContext.getString(R.string.reader_annotation_deleted),
                            )
                        }
                    },
                    onAddNoteForHighlight = { highlight ->
                        scope.launch {
                            val target = editorTargetForHighlight(
                                highlight,
                                returnToManagement = true,
                            )
                            if (target != null) {
                                annotationManagementVisible = false
                                noteEditorTarget = target
                            }
                        }
                    },
                    onEditNote = { note ->
                        scope.launch {
                            val target = editorTargetForNote(
                                note,
                                returnToManagement = true,
                            )
                            if (target != null) {
                                annotationManagementVisible = false
                                noteEditorTarget = target
                            }
                        }
                    },
                    onDeleteNote = { note ->
                        scope.launch {
                            noteController?.delete(note.id)
                            noteVersion++
                            snackbarHostState.showSnackbar(
                                readerWindowContext.getString(R.string.reader_note_deleted),
                            )
                        }
                    },
                )
            }

            noteEditorTarget?.let { target ->
                val pageLine = stringResource(
                    R.string.reader_annotation_page_line,
                    target.pageIndex + 1,
                )
                val closeEditor: () -> Unit = {
                    noteEditorTarget = null
                    if (target.returnToManagement) annotationManagementVisible = true
                }
                NoteEditDialog(
                    title = stringResource(R.string.reader_note_editor_title, pageLine),
                    initialContent = target.note?.content.orEmpty(),
                    isEditing = target.note != null,
                    onSave = { content ->
                        scope.launch {
                            val notes = noteController ?: return@launch
                            if (target.note == null) {
                                notes.create(target.anchor, content)
                            } else {
                                notes.update(target.note.id, content)
                            }
                            noteVersion++
                            closeEditor()
                            snackbarHostState.showSnackbar(
                                readerWindowContext.getString(R.string.reader_note_saved),
                            )
                        }
                    },
                    onDelete = target.note?.let { note ->
                        {
                            scope.launch {
                                noteController?.delete(note.id)
                                noteVersion++
                                closeEditor()
                                snackbarHostState.showSnackbar(
                                    readerWindowContext.getString(R.string.reader_note_deleted),
                                )
                            }
                        }
                    },
                    onDismiss = closeEditor,
                )
            }

            // R25: the four task surfaces are Foreground Advanced Cards
            // rendered inside PdfChrome (from the tapped row). No BottomSheets
            // in the Reader More chain anymore. The export PROGRESS dialog is
            // the system-flow status surface (kept).
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
                        moreState = ReaderMoreStateMachine.onOpenChild(
                            ReaderMoreState.MAIN,
                            ReaderMoreState.CHILD_TOC,
                        )
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
                        moreState = ReaderMoreStateMachine.onOpenChild(
                            ReaderMoreState.MAIN,
                            ReaderMoreState.CHILD_TOC,
                        )
                    },
                    onPickRangeFromThumbs = {
                        thumbRangePick = true
                        moreState = ReaderMoreStateMachine.onOpenChild(
                            ReaderMoreState.MAIN,
                            ReaderMoreState.CHILD_THUMBNAILS,
                        )
                        viewModel.prepareThumbnails()
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

private fun applyPdfSystemBars(view: android.view.View, mode: com.m1racle.reader.pdf.PdfTopBarMode) {
    val activity = view.context as? android.app.Activity ?: return
    val wic = androidx.core.view.WindowInsetsControllerCompat(activity.window, view)
    val status = androidx.core.view.WindowInsetsCompat.Type.statusBars()
    val nav = androidx.core.view.WindowInsetsCompat.Type.navigationBars()
    val all = androidx.core.view.WindowInsetsCompat.Type.systemBars()
    when (mode) {
        com.m1racle.reader.pdf.PdfTopBarMode.NORMAL -> {
            wic.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            wic.show(all)
        }
        com.m1racle.reader.pdf.PdfTopBarMode.IMMERSIVE -> {
            wic.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            wic.show(nav)
            wic.hide(status)
        }
    }
    activity.window.clearContrastScrim(view)
    if (BuildConfig.READER_PERFORMANCE_EXPERIMENTS_ENABLED) {
        Log.i("PDF_SYSBARS", "apply topBarMode=$mode wic behavior=" + wic.systemBarsBehavior)
    }
}

private fun pdfStatusBarVisiblePx(view: android.view.View): Int {
    val insets = androidx.core.view.ViewCompat.getRootWindowInsets(view) ?: return 0
    return insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PdfChrome(
    capsuleData: PdfCapsuleData,
    onBack: () -> Unit,
    /** Capsule tap: toggle the anchored jump card (never changes chrome levels). */
    onCapsuleToggle: (() -> Unit)?,
    modifier: Modifier = Modifier,
    /** Four-state chrome visibility: FULL / BUTTONS_ONLY / CAPSULE_ONLY / HIDDEN. */
    chromeLevel: PdfChromeLevel = PdfChromeLevel.FULL,
    /** Capsule anchored expansion (jump card) state — overlay controller. */
    capsuleExpanded: Boolean = false,
    onCapsuleDismiss: () -> Unit = {},
    jumpCardParams: JumpCardParams? = null,
    /** Anchored popover state (non-modal, no dim) — the morphing more control. */
    menuExpanded: Boolean = false,
    /** Morph-back phase: the card is shrinking back into the circle. */
    menuClosing: Boolean = false,
    /** The more control's full state (the explicit 8-state machine). */
    menuState: ReaderMoreState = ReaderMoreState.CLOSED,
    onMenuToggle: () -> Unit = {},
    onMenuDismiss: () -> Unit = {},
    onSubmenuChange: (ReaderMoreState) -> Unit = {},
    /** Advanced reverse-morph phase: the state STAYS ADVANCED_* while the
     *  Foreground Advanced Card collapses back to its own source row; the
     *  host fires [onChildCollapsed] when the morph completes. */
    childClosing: Boolean = false,
    onChildCollapsed: () -> Unit = {},
    /** PREPARING_* layout ready → promote to CHILD_* (r26.1 two-phase). */
    onPreparingLayoutReady: (ReaderMoreState) -> Unit = {},
    /** Expandable entries (chevron rows): reading mode + top-bar mode. */
    submenuEntries: List<PdfChromeSubmenuEntry> = emptyList(),
    /** Plain one-level entries. */
    plainMenuItems: List<PdfChromeMenuItem> = emptyList(),
    /** Reading-mode submenu options (check-marked). */
    readingModeOptions: List<PdfChromeMenuOption> = emptyList(),
    /** Top-bar-mode submenu options (check-marked). */
    topBarModeOptions: List<PdfChromeMenuOption> = emptyList(),
    /** Foreground Advanced Card payloads (R25). */
    nestedData: ReaderAdvancedData? = null,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // Four-state orthogonal derivation: capsule and the two circles are
    // independent dims. FULL=(T,T) BUTTONS_ONLY=(F,T) CAPSULE_ONLY=(T,F)
    // HIDDEN=(F,F). Tap toggles the capsule only; scroll drives both.
    val capsuleVisible = chromeLevel == PdfChromeLevel.FULL ||
        chromeLevel == PdfChromeLevel.CAPSULE_ONLY
    val edgeButtonsVisible = chromeLevel == PdfChromeLevel.FULL ||
        chromeLevel == PdfChromeLevel.BUTTONS_ONLY

    // Liquid-only chrome: stable root capture and menu export.
    // Parent exportedBackdrop → Child samples export (glass-on-glass CORRECT).
    // Capture the same opaque page surround seen on screen, including gaps.
    // Keep the recording callback stable across scroll/zoom recompositions.
    val backdropBackground = scheme.background
    val drawPdfBackdrop: ContentDrawScope.() -> Unit = remember(backdropBackground) {
        {
            drawRect(backdropBackground)
            drawContent()
        }
    }
    val rootBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop(
        onDraw = drawPdfBackdrop,
    )
    val parentExportBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
    val backdropHost = GlassBackdropHost(
        root = rootBackdrop,
        menuExport = parentExportBackdrop,
    )

    CompositionLocalProvider(
        LocalGlassBackdropHost provides backdropHost,
        LocalGlassEnvironment provides GlassEnvironment.reader(LocalReaderDarkTheme.current),
    ) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background),
    ) {
        // PDF content feeds the root LayerBackdrop for Liquid chrome sampling.
        GlassBackdropSource(
            backdrop = rootBackdrop,
            modifier = Modifier.fillMaxSize(),
        ) {
            content()
        }
        // ── Three independent, position-stable anchors (Box overlay) ──────────
        // The round buttons are FIXED screen anchors: left = TopStart, right =
        // TopEnd, fixed margins and sizes. The capsule is an independent
        // centered layer; hiding it (COMPACT) must NEVER re-layout or move the
        // buttons. Level changes only affect alpha/visibility/translationY.
        //
        // Top anchor uses the STABLE status-bar inset (statusBarsIgnoringVisibility):
        // immersive mode hides the system bar — which extends PdfContent, but must
        // never move the app chrome (no jump when the visible inset becomes 0).
        val chromeDensity = LocalDensity.current
        val stableTopInsetDp = WindowInsets.statusBarsIgnoringVisibility
            .asPaddingValues()
            .calculateTopPadding()
        val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
        // Capsule hard cap: <=52% of the screen AND leaves >=12dp clearance to
        // each round button (12 margin + 44 slot + 12 gap per side). Never
        // fillMaxWidth, never weight(1f).
        val capsuleMaxWidthDp = minOf(
            screenWidthDp - (12 + 44 + 12) * 2,
            (screenWidthDp * 0.52f).toInt(),
        ).coerceAtLeast(120)

        // ── Overlay contract ────────────────────────────────────────────────
        //   content = z0, chrome row (back / capsule / more circle) = z1,
        //   outside-tap catcher = z2, popover surface = z3.
        // The more control is ONE surface family: the three-dot circle morphs
        // into the menu card, and the card can KEEP morphing into a foreground
        // advanced child-card (same container, same anchor — top/right pinned
        // to the circle's anchor; the capsule RETRACTS while the family is up
        // so nothing overlaps). Advanced tasks (page mapping / TOC / thumbs /
        // export) open anchored children rendered above Main — no BottomSheet,
        // no secondary window, no handoff.
        val chromeRowTopDp = stableTopInsetDp + 8.dp
        val screenHeightDpFull = LocalConfiguration.current.screenHeightDp.dp
        // IME-available height (0 while the keyboard is hidden): the form may
        // shrink/scroll into this space, but the window itself is
        // ADJUST_NOTHING, so the chrome never moves and the PDF viewport is
        // never resized by the keyboard.
        val imeBottomPx = WindowInsets.ime.getBottom(chromeDensity)
        val imeAvailableHeightDp = with(chromeDensity) {
            LocalConfiguration.current.screenHeightDp.dp - imeBottomPx.toDp()
        } - chromeRowTopDp
        val viewportBottomRootPx = with(chromeDensity) {
            screenHeightDpFull.toPx() - WindowInsets.navigationBars
                .asPaddingValues()
                .calculateBottomPadding()
                .toPx()
        }

        val moreChromeSlidePx = with(chromeDensity) { 22.dp.roundToPx() }

        // Left back button — fixed TopStart anchor; visible IFF the two circles
        // are on (FULL / BUTTONS_ONLY). Scroll NEVER hides it alone; only the
        // full-hide stage (or explicit entry) does.
        PdfChromeVisibility(
            role = GlassMaterialRole.Control,
            visible = edgeButtonsVisible,
            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { -moreChromeSlidePx },
            // Both round buttons exit with a 100ms delay so the hide order is
            // always capsule first, then the two circles (staggered by the
            // scroll rules; buttons re-enter immediately on restore).
            exit = fadeOut(tween(180, delayMillis = 100)) +
                slideOutVertically(tween(180, delayMillis = 100)) { -moreChromeSlidePx },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 20.dp, top = chromeRowTopDp)
                .zIndex(1f),
        ) {
            FloatingChromeButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                onClick = onBack,
            )
        }

        // Right more control — ONE node, ONE TopEnd anchor, both states
        // (r22: the three-dot circle morphs INTO the menu card; the card is
        // the circle grown leftward/downward, never a second surface). The
        // layer stays alive while the menu is active so the morph container
        // is never torn down mid-animation.
        val menuActiveLayer = menuExpanded || menuClosing
        PdfChromeVisibility(
            role = GlassMaterialRole.Menu,
            visible = edgeButtonsVisible || menuActiveLayer,
            // Slide amount is pinned to the circle size (the layer itself is
            // full-screen because it hosts the outside-tap catcher).
            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { -moreChromeSlidePx },
            exit = fadeOut(tween(180, delayMillis = 100)) +
                slideOutVertically(tween(180, delayMillis = 100)) { -moreChromeSlidePx },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 20.dp, top = chromeRowTopDp)
                .zIndex(1f),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Outside-tap catcher while the menu is up: consumes taps only
                // (drags still reach the PDF), closes via the reverse morph.
                if (menuActiveLayer) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(1f)
                            .pointerInput(menuActiveLayer) {
                                detectTapGestures(onTap = { onMenuDismiss() })
                            },
                    )
                }
                val nestedMaxH = AdvancedChildLayoutSpec.maxHeightDp(
                    windowHeightDp = screenHeightDpFull.value,
                    statusInsetDp = chromeRowTopDp.value,
                    navInsetDp = with(chromeDensity) {
                        WindowInsets.navigationBars
                            .asPaddingValues()
                            .calculateBottomPadding()
                            .value
                    },
                    marginDp = 20f,
                ).dp.coerceAtLeast(240.dp)
                ReaderMoreHost(
                    state = menuState,
                    closing = menuClosing,
                    childClosing = childClosing,
                    contentDescription = stringResource(R.string.pdf_menu_open),
                    onToggleOpen = onMenuToggle,
                    onCloseRequest = onMenuDismiss,
                    onChildCollapsed = onChildCollapsed,
                    onSectionToggle = onSubmenuChange,
                    submenuEntries = submenuEntries,
                    plainMenuItems = plainMenuItems,
                    readingModeOptions = readingModeOptions,
                    topBarModeOptions = topBarModeOptions,
                    nestedData = nestedData,
                    nestedMaxHeightDp = nestedMaxH,
                    viewportBottomRoot = viewportBottomRootPx,
                    onPreparingLayoutReady = onPreparingLayoutReady,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .zIndex(2f),
                )
            }
        }

        // ── Center capsule layer: ONE surface, two states ────────────────────
        // Collapsed it is the position pill; expanded the SAME surface grows
        // downward and widens below the header — top edge stays the pill's
        // exact width and position (CapsulePopoverShape), so the jump input
        // reads as the capsule's own expanded popover, never a second card.
        // While the more menu is up (or morphing shut) the capsule RETRACTS
        // entirely (overlay behavior — chrome levels untouched, no re-layout);
        // it fades back in when the menu has fully closed.
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = chromeRowTopDp)
                .zIndex(if (capsuleExpanded) 3f else 1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PdfChromeVisibility(
                role = GlassMaterialRole.Capsule,
                visible = capsuleVisible && !menuActiveLayer,
                enter = fadeIn(tween(180, delayMillis = 100)),
                exit = fadeOut(tween(180)),
            ) {
                PdfInfoCapsule(
                    data = capsuleData,
                    onClick = onCapsuleToggle,
                    maxWidthDp = capsuleMaxWidthDp,
                    expanded = capsuleExpanded && jumpCardParams != null,
                    jumpParams = jumpCardParams,
                    imeAvailableHeightDp = imeAvailableHeightDp,
                )
            }
        }
        // Outside-tap catcher while the jump card is open (no indication).
        if (capsuleExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2f)
                    .pointerInput(capsuleExpanded) {
                        detectTapGestures(onTap = { onCapsuleDismiss() })
                    },
            )
        }
        // The textbook-page form lives INSIDE the morphing more container
        // (ReaderMoreHost): the menu card keeps changing into the form surface
        // — never a separate popup layer here.
    }
    } // CompositionLocalProvider (backdrop host)
}

/**
 * X/iOS-style floating chrome button: 42dp circle, translucent surface + scrim
 * + hairline border + very light shadow. Backdrop blur is approximated with a
 * near-opaque translucent surface so page outlines remain faintly perceptible
 * while glyph strokes never read through behind the icon.
 */
@Composable
private fun FloatingChromeButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(44.dp)
            .glassMaterial(
                role = GlassMaterialRole.Control,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = scheme.onSurface,
        )
    }
}

/**
 * Small frosted capsule: document title (maxLines=1, ellipsis) plus
 * page · section line. Tap → jump-to-page.
 */
/**
 * Center chrome surface: collapsed it is the position pill; expanded it is
 * the SAME surface grown downward AND widened below the header — the top
 * edge keeps the pill's exact width and position (CapsulePopoverShape), so
 * the jump input reads as a popover growing out of the capsule, never a
 * second, boxier card. Never a BottomSheet, never a full-screen dim.
 * The system keyboard may appear at the bottom; the surface stays
 * top-anchored (ADJUST_NOTHING), and the body may scroll within the
 * IME-available height.
 */
/** Finger-local Plus glow on the capsule pill. Does not scale the jump card. */
@Composable
private fun Modifier.capsuleLocalPressHighlight(
    interaction: MutableInteractionSource,
): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    var pressPos by remember { mutableStateOf<Offset?>(null) }
    return this
        .pointerInput(interaction) {
            awaitEachGesture {
                val down = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial,
                )
                pressPos = down.position
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    pressPos = change.position
                    if (!change.pressed) break
                }
            }
        }
        .drawWithContent {
            drawContent()
            if (!pressed) return@drawWithContent
            val pos = pressPos
            val center = if (pos != null) {
                Offset(
                    pos.x.coerceIn(0f, size.width),
                    pos.y.coerceIn(0f, size.height),
                )
            } else {
                Offset(size.width / 2f, size.height / 2f)
            }
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.18f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = size.minDimension * 1.2f,
                ),
                blendMode = BlendMode.Plus,
            )
        }
}

/** Capsule motion-spec radii: collapsed pill + popover flare (single source for all 3 sites). */
private val CapsuleRadiusDp = 22.dp
private val CapsuleFlareRadiusDp = 16.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfInfoCapsule(
    data: PdfCapsuleData,
    onClick: (() -> Unit)?,
    maxWidthDp: Int = 260,
    expanded: Boolean = false,
    jumpParams: JumpCardParams? = null,
    imeAvailableHeightDp: Dp = Dp.Infinity,
) {
    val scheme = MaterialTheme.colorScheme
    val widthCap = maxWidthDp.dp
    val collapsedWidth = 200.dp.coerceAtMost(widthCap)
    val expandedWidth = 240.dp.coerceAtMost(widthCap)
    val morph = remember { Animatable(if (expanded) 1f else 0f) }
    LaunchedEffect(expanded) {
        morph.animateTo(if (expanded) 1f else 0f, CapsuleMorphSpring)
    }
    val fraction = morph.value
    val surfaceWidthDp = collapsedWidth + (expandedWidth - collapsedWidth) * fraction
    val shape = if (fraction > 0f && expandedWidth > collapsedWidth) {
        CapsulePopoverShape(
            topWidth = collapsedWidth,
            bottomWidth = surfaceWidthDp,
            topRadius = CapsuleRadiusDp,
            bottomRadius = CapsuleFlareRadiusDp,
        )
    } else {
        RoundedCornerShape(CapsuleRadiusDp)
    }
    var lastCapsuleAnchor by remember { mutableStateOf(IntArray(0)) }
    var bodyNaturalHeightPx by remember { mutableFloatStateOf(0f) }
    val bodyDensity = LocalDensity.current
    val capsuleInteraction = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .width(surfaceWidthDp)
            .glassMaterial(
                role = GlassMaterialRole.Capsule,
                shape = shape,
                interaction = expanded,
            )
            .onGloballyPositioned { coords ->
                val bounds = coords.boundsInWindow()
                val anchor = intArrayOf(
                    bounds.top.roundToInt(),
                    (bounds.left + bounds.width / 2f).roundToInt(),
                )
                if (lastCapsuleAnchor.size != 2 ||
                    lastCapsuleAnchor[0] != anchor[0] ||
                    lastCapsuleAnchor[1] != anchor[1]
                ) {
                    if (BuildConfig.READER_PERFORMANCE_EXPERIMENTS_ENABLED) {
                        Log.i(
                            "CAPSULE_SURFACE",
                            "expanded=$expanded top=${anchor[0]} centerX=${anchor[1]} " +
                                "w=${surfaceWidthDp.value.toInt()}",
                        )
                    }
                    lastCapsuleAnchor = anchor
                }
            },
    ) {
        Box(
            modifier = Modifier
                .width(collapsedWidth)
                .height(44.dp)
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(CapsuleRadiusDp))
                .then(
                    if (onClick != null) {
                        Modifier
                            .clickable(
                                interactionSource = capsuleInteraction,
                                indication = null,
                                onClick = onClick,
                            )
                            .capsuleLocalPressHighlight(capsuleInteraction)
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 16.dp, vertical = 4.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = 1f - fraction },
            ) {
                Text(
                    text = data.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = scheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.capsuleTitleMarquee(),
                )
                data.positionLine?.let { position ->
                    val progress = data.progressPercent?.let {
                        stringResource(R.string.pdf_capsule_progress_percent, it)
                    }
                    Text(
                        text = listOfNotNull(position, progress).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (fraction > 0f) {
                Text(
                    text = data.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = scheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .capsuleTitleMarquee()
                        .graphicsLayer { alpha = fraction },
                )
            }
        }

        val shouldComposeBody = expanded || fraction > 0f
        val bodyRegionHeightDp = with(bodyDensity) {
            (bodyNaturalHeightPx * fraction).toDp()
        }
        val bodyParams = jumpParams
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(bodyRegionHeightDp)
                .clipToBounds(),
        ) {
            if (shouldComposeBody && bodyParams != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .reportNaturalHeight { bodyNaturalHeightPx = it.toFloat() }
                        .graphicsLayer { alpha = (fraction * 1.5f).coerceAtMost(1f) },
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(scheme.outlineVariant.copy(alpha = 0.55f)),
                    )
                    data.positionLine?.let { position ->
                        Text(
                            text = listOfNotNull(position, data.printDetail).joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurface,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                    data.progressPercent?.let { progress ->
                        Text(
                            text = stringResource(R.string.pdf_capsule_reading_progress, progress),
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    JumpPageBody(
                        params = bodyParams,
                        bodyMaxHeightDp = (imeAvailableHeightDp - 44.dp).coerceAtLeast(96.dp),
                    )
                }
            }
        }
    }
}

private fun Modifier.capsuleTitleMarquee(): Modifier = basicMarquee(
    iterations = Int.MAX_VALUE,
    initialDelayMillis = 1500,
    repeatDelayMillis = 1200,
)

/** Capsule jump-input corner: clip + border share it so they cannot drift apart. */
private val JumpInputCornerDp = 11.dp

/**
 * Jump-to-page body: grows inside the capsule surface, under the header.
 * Compact expansion: small title + single helper line + 50dp number field
 * (11dp radius, only slightly deeper than the capsule glass) + light
 * 取消 text action + compact 跳转 primary action. The header above keeps the
 * current position, so the input is a tight extension of the pill — never a
 * form-like dialog. Body stays IME-adaptive (scrolls within the space above
 * the keyboard; the window is ADJUST_NOTHING so neither the chrome nor the
 * PDF viewport ever moves).
 */
@Composable
private fun JumpPageBody(
    params: JumpCardParams,
    bodyMaxHeightDp: Dp,
) {
    var text by remember { mutableStateOf(params.currentHumanPage.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val scheme = MaterialTheme.colorScheme
    val invalidMsg = stringResource(R.string.pdf_jump_invalid, params.pageCount)

    // R25 closure P2-1: request focus only after the body morph completes
    // (200ms) — the IME slides up over an already-grown body instead of
    // fighting the expanding region on frame 1.
    LaunchedEffect(Unit) {
        delay(200)
        focus.requestFocus()
    }

    fun submit() {
        val n = text.trim().toIntOrNull()
        if (n == null || n < 1 || n > params.pageCount) {
            error = invalidMsg
            return
        }
        error = null
        keyboard?.hide()
        params.onConfirm(n)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = bodyMaxHeightDp)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
    ) {
        // Restrained number field: 44dp tall, 11dp radius, filled only
        // slightly deeper than the capsule glass — never a solid black
        // rectangle, never a bulky form field.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(JumpInputCornerDp))
                .background(scheme.onSurface.copy(alpha = 0.06f))
                .border(
                    width = 1.dp,
                    color = if (error != null) {
                        scheme.error.copy(alpha = 0.7f)
                    } else {
                        scheme.outline.copy(alpha = 0.4f)
                    },
                    shape = RoundedCornerShape(JumpInputCornerDp),
                )
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (text.isEmpty()) {
                Text(
                    text = stringResource(R.string.pdf_jump_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            BasicTextField(
                value = text,
                onValueChange = {
                    text = it.filter { ch -> ch.isDigit() }.take(6)
                    error = null
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurface),
                cursorBrush = SolidColor(scheme.primary),
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
        val errorMsg = error
        if (errorMsg != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = errorMsg,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.error,
                maxLines = 1,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 取消 = light text action that folds the capsule back.
            TextButton(onClick = {
                keyboard?.hide()
                params.onCancel()
            }) {
                Text(
                    text = stringResource(R.string.pdf_dialog_cancel),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            // 跳转 = compact primary action (Material min-height only, no
            // oversized padding).
            Button(
                onClick = { submit() },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
            ) {
                Text(
                    text = stringResource(R.string.pdf_jump_go),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}



private data class HorizontalTilePlannerInput(
    val snapshot: PdfViewportSnapshot,
    val visible: com.m1racle.reader.pdf.PageTileViewport,
    val ahead: com.m1racle.reader.pdf.PageTileViewport?,
    val currentBucket: Int,
    val targetBucket: Int,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PdfHorizontalPagedArea(
    state: PdfViewerUiState.Ready,
    pageJump: PdfViewerViewModel.PageJumpRequest?,
    onToggleChrome: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPageLabelClick: () -> Unit,
    onViewportWidthPx: (Int) -> Unit,
    onPageChanged: (Int) -> Unit,
    onZoomScaleSettled: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onViewportSessionSettled: (PdfViewportSessionState) -> Unit = {},
    onViewportSessionPreview: (PdfViewportSessionState) -> Unit = {},
    onTilePlan: (PdfTilePlan) -> Unit = {},
    tileOverlaySnapshots: StateFlow<TileOverlaySnapshot>? = null,
    onZoomGestureActive: (Boolean) -> Unit = {},
    onEnsureAround: (Int) -> Unit = {},
    pageAnnotation: @Composable androidx.compose.foundation.layout.BoxScope.(Int) -> Unit = {},
    qaProbe: HorizontalInteractionProbe? = null,
) {
    // GPU zoom only inside clipped viewport — never covers TopBar.
    var scale by remember(state.document.id) {
        mutableFloatStateOf(
            (state.viewportSessionState?.scale ?: state.zoomScale)
                .coerceIn(PdfRenderQuality.MIN_ZOOM, PdfRenderQuality.MAX_ZOOM),
        )
    }
    var horizontalPan by remember(state.document.id) { mutableStateOf(HorizontalPanState()) }
    var horizontalPinching by remember(state.document.id) { mutableStateOf(false) }
    var activeHorizontalPage by remember(state.document.id){mutableIntStateOf(state.pageIndex)}
    var viewportSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val scheme = MaterialTheme.colorScheme
    val horizontalViewportBackground = M1racleReaderTheme.extra.pdfCanvas
    val viewConfig = LocalViewConfiguration.current
    val horizontalDensity = LocalDensity.current
    val horizontalContext = LocalContext.current
    val horizontalDebuggable = remember(horizontalContext) {
        (horizontalContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
    val pageContentGap = 8.dp
    val pageContentMarginPx = with(horizontalDensity) { pageContentGap.toPx() }
    val horizontalOverscrollLimitXPx = with(horizontalDensity) { 24.dp.toPx() }
    val horizontalOverscrollLimitYPx = with(horizontalDensity) { 20.dp.toPx() }
    val horizontalOverscrollPolicy = AxisOverscrollPolicy(
        horizontalEnabled = true,
        verticalEnabled = true,
        maxDistancePx = horizontalOverscrollLimitXPx,
        horizontalMaxDistancePx = horizontalOverscrollLimitXPx,
        verticalMaxDistancePx = horizontalOverscrollLimitYPx,
    )
    val horizontalScope = rememberCoroutineScope()
    val platformFlingRunner = remember(horizontalContext) { PlatformPdfFlingRunner(horizontalContext) }
    val gestureTelemetryRing = remember(state.document.id) { PdfGestureFrameTelemetryRing() }
    var horizontalMotionJob by remember(state.document.id) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var horizontalGestureGeneration by remember(state.document.id) { mutableLongStateOf(0L) }
    var pageStrip by remember(state.document.id) {
        mutableStateOf(
            ThreePageStripState(
                currentPage = state.pageIndex.coerceIn(0, state.pageCount.coerceAtLeast(1) - 1),
                pageCount = state.pageCount.coerceAtLeast(1),
            ),
        )
    }
    var pageTransition by remember(state.document.id) {
        mutableStateOf<PageStripTransition>(ThreePageStripPhysics.idle(0L))
    }
    var pageStripOffsetPx by remember(state.document.id) { mutableFloatStateOf(0f) }
    var pageTransitionTargetSession by remember(state.document.id) {
        mutableStateOf<PdfViewportSessionState?>(null)
    }
    var pageStripSettlingGeneration by remember(state.document.id) { mutableLongStateOf(-1L) }
    var pageIndexChangeCountThisGesture by remember(state.document.id) { mutableIntStateOf(0) }
    var slotRotationCountThisGesture by remember(state.document.id) { mutableIntStateOf(0) }
    var settleStartCountThisGesture by remember(state.document.id) { mutableIntStateOf(0) }
    var offsetRebaseCountThisGesture by remember(state.document.id) { mutableIntStateOf(0) }
    var consumedJumpId by remember { mutableStateOf(pageJump?.id) }
    var lastTapTimeMs by remember { mutableLongStateOf(0L) }
    var lastTapPosition by remember { mutableStateOf<Offset?>(null) }
    val currentOnTilePlan by rememberUpdatedState(onTilePlan)
    val currentOnZoomGestureActive by rememberUpdatedState(onZoomGestureActive)
    val currentOnEnsureAround by rememberUpdatedState(onEnsureAround)
    val currentOnViewportSessionSettled by rememberUpdatedState(onViewportSessionSettled)
    val currentOnViewportSessionPreview by rememberUpdatedState(onViewportSessionPreview)
    // P1-7: group overlay tiles once per published sequence; the per-slot
    // filter below drops from O(whole cache) to O(per-page tiles). The
    // non-flow fallback wrapper is remembered instead of re-allocated per
    // composition.
    val collectedHorizontalOverlay: TileOverlaySnapshot? = if (tileOverlaySnapshots != null) {
        tileOverlaySnapshots.collectAsStateWithLifecycle().value
    } else {
        null
    }
    val horizontalOverlaySnapshot = collectedHorizontalOverlay
        ?: remember(state.tileEntries) { TileOverlaySnapshot(entries = state.tileEntries) }
    val horizontalTilesByPage = remember(
        collectedHorizontalOverlay?.sequence, state.tileEntries, state.tileDocumentGeneration,
    ) {
        (collectedHorizontalOverlay?.entries ?: state.tileEntries)
            .values
            .filter { it.key.documentGeneration == state.tileDocumentGeneration }
            .groupBy { it.key.pageIndex }
    }
    val currentHorizontalOverlaySnapshot by rememberUpdatedState(horizontalOverlaySnapshot)
    val horizontalViewportConflator = remember(state.document.id) {
        LatestViewportConflator<HorizontalTilePlannerInput>()
    }
    val horizontalViewportSignal = remember(state.document.id) { Channel<Unit>(Channel.CONFLATED) }
    val horizontalViewportSequence = remember(state.document.id) { AtomicLong(0L) }
    var horizontalTileBucket by remember(state.document.id) { mutableIntStateOf(0) }
    var horizontalTileTargetBucket by remember(state.document.id) { mutableIntStateOf(0) }
    var horizontalPointerDown by remember(state.document.id) { mutableStateOf(false) }
    var appliedHorizontalSessionGeneration by remember(state.document.id) { mutableLongStateOf(-1L) }
    var horizontalTileRequestCount by remember(state.document.id) { mutableIntStateOf(0) }
    var horizontalPageAheadRequestCount by remember(state.document.id) { mutableIntStateOf(0) }
    var horizontalVisibleTileKeys by remember(state.document.id) {
        mutableStateOf(emptySet<com.m1racle.reader.pdf.PdfTileKey>())
    }
    val sharedGestureController = remember(state.document.id) {
        PdfGestureTransformController(horizontalPan.asZoomPanTransform(scale))
    }

    LaunchedEffect(qaProbe) {
        qaProbe?.recordPage(state.pageIndex)
        qaProbe?.recordTransform(scale, horizontalPan)
    }

    fun pageLayout(page: Int) = HorizontalZoomPanPhysics.fitPage(
        viewportWidthPx = viewportSize.width.toFloat(),
        viewportHeightPx = viewportSize.height.toFloat(),
        pageAspect = state.pageAspects[page] ?: HorizontalZoomPanPhysics.DEFAULT_PAGE_ASPECT,
        contentMarginPx = pageContentMarginPx,
        horizontalMarginPx = 0f,
    )

    fun panBounds(page: Int, targetScale: Float) =
        HorizontalZoomPanPhysics.bounds(pageLayout(page), targetScale)

    fun horizontalSession(page: Int, generation: Long = System.nanoTime()) =
        HorizontalZoomPanPhysics.viewportSession(
            pageIndex = page,
            layout = pageLayout(page),
            scale = scale,
            pan = horizontalPan,
            generation = generation,
            phase = sharedGestureController.snapshot().phase,
            pointerEpoch = sharedGestureController.snapshot().pointerEpoch?.epochId,
        )

    fun commitHorizontalSession(page: Int) {
        val session = horizontalSession(page)
        appliedHorizontalSessionGeneration = session.generation
        currentOnViewportSessionSettled(session)
    }

    fun publishHorizontalTileSnapshot(
        page: Int,
        pointerDown: Boolean,
        rebaseFrame: Boolean = false,
    ) {
        if (viewportSize.width <= 0 || viewportSize.height <= 0 || page !in 0 until state.pageCount) return
        val layout = pageLayout(page)
        currentOnViewportSessionPreview(horizontalSession(page))
        val visible = HorizontalTilePlanning.visiblePage(page, layout, scale, horizontalPan) ?: return
        val now = System.nanoTime()
        val sequence = horizontalViewportSequence.incrementAndGet()
        val currentBucket = PdfZoomBuckets.index(scale, horizontalTileBucket)
        horizontalTileBucket = currentBucket
        val targetBucket = PdfZoomBuckets.index(scale, currentBucket)
        horizontalTileTargetBucket = targetBucket
        val phase = when {
            !pointerDown -> TileInteractionPhase.IDLE
            horizontalPinching -> TileInteractionPhase.PINCHING
            else -> TileInteractionPhase.DRAGGING
        }
        val snapshot = PdfViewportSnapshot(
            documentGeneration = state.tileDocumentGeneration,
            geometryGeneration = visible.geometryGeneration,
            viewportRectDocument = com.m1racle.reader.pdf.DocumentViewportRect(
                visible.visibleRect.left,
                visible.visibleRect.top,
                visible.visibleRect.right,
                visible.visibleRect.bottom,
            ),
            scale = scale,
            focalPointViewport = null,
            velocityX = 0f,
            velocityY = 0f,
            zoomVelocity = 0f,
            motionConfidence = if (pageTransition.direction != null) 1f else 0f,
            interactionPhase = phase,
            timestampNanos = now,
            sequence = sequence,
            pointerDown = pointerDown,
            currentPage = page,
            rebaseFrame = rebaseFrame,
        )
        val ahead = HorizontalTilePlanning.pageAhead(
            pageIndex = page,
            pageCount = state.pageCount,
            direction = pageTransition.direction,
            layoutForPage = ::pageLayout,
            visible = visible,
        )
        horizontalViewportConflator.offer(
            HorizontalTilePlannerInput(snapshot, visible, ahead, currentBucket, targetBucket),
        )
        horizontalViewportSignal.trySend(Unit)
    }

    fun displayOffset(pan: HorizontalPanState) =
        Offset(pan.displayOffsetX, pan.displayOffsetY)

    fun transformOrigin(page: Int): Offset {
        val layout = pageLayout(page)
        return Offset(
            x = layout.viewportWidthPx / 2f,
            y = layout.contentTopPx,
        )
    }

    fun logHorizontalBounds(page: Int, targetScale: Float) {
        if (!horizontalDebuggable || viewportSize.width <= 0 || viewportSize.height <= 0) return
        val layout = pageLayout(page)
        val bounds = HorizontalZoomPanPhysics.bounds(layout, targetScale)
        Log.d(
            "M1racleReaderH1",
            "H1_BOUNDS pageIndex=$page viewportWidthPx=${layout.viewportWidthPx} " +
                "viewportHeightPx=${layout.viewportHeightPx} fittedWidthPx=${layout.fittedWidthPx} " +
                "fittedHeightPx=${layout.fittedHeightPx} scale=$targetScale " +
                "scaledWidthPx=${layout.fittedWidthPx * targetScale} " +
                "scaledHeightPx=${layout.fittedHeightPx * targetScale} " +
                "minX=${bounds.minX} maxX=${bounds.maxX} " +
                "minY=${bounds.minY} maxY=${bounds.maxY}",
        )
    }

    LaunchedEffect(state.document.id) {
        var lastPlannerTickNanos = 0L
        for (ignored in horizontalViewportSignal) {
            val throttleNanos = TileRenderPipelinePolicy.PLANNER_THROTTLE_MS * 1_000_000L
            val elapsed = System.nanoTime() - lastPlannerTickNanos
            if (elapsed in 1 until throttleNanos) {
                delay((throttleNanos - elapsed + 999_999L) / 1_000_000L)
            }
            val input = horizontalViewportConflator.pollLatest() ?: continue
            val plan = HorizontalTilePlanning.plan(
                documentId = state.document.id,
                documentGeneration = state.tileDocumentGeneration,
                snapshot = input.snapshot,
                visible = input.visible,
                currentBucket = input.currentBucket,
                targetBucket = input.targetBucket,
                ahead = input.ahead,
            )
            horizontalTileRequestCount += plan.requests.size
            horizontalVisibleTileKeys = plan.currentVisibleKeys
            horizontalPageAheadRequestCount += plan.requests.count {
                it.priority == com.m1racle.reader.pdf.TilePriority.PAGE_AHEAD
            }
            currentOnTilePlan(plan)
            HorizontalRealPathTelemetry.onPlan(plan)
            if (horizontalDebuggable) {
                Log.d(
                    "M1racleReaderH13",
                    "{\"type\":\"tilePlanner\",\"eventTimeNanos\":${System.nanoTime()}," +
                        "\"pointerDown\":${input.snapshot.pointerDown}," +
                        "\"gesturePhase\":\"${input.snapshot.interactionPhase}\"," +
                        "\"pageIndex\":${input.snapshot.currentPage}," +
                        "\"scale\":${input.snapshot.scale}," +
                        "\"tilePlannerTick\":${input.snapshot.sequence}," +
                        "\"tileRequestCount\":$horizontalTileRequestCount," +
                        "\"tileReadyCount\":${currentHorizontalOverlaySnapshot.entries.size}," +
                        "\"tileSwapCount\":${currentHorizontalOverlaySnapshot.sequence}," +
                        "\"pageAheadRequestCount\":$horizontalPageAheadRequestCount}",
                )
            }
            lastPlannerTickNanos = System.nanoTime()
        }
    }

    DisposableEffect(horizontalViewportSignal) {
        onDispose { horizontalViewportSignal.close() }
    }

    fun cancelHorizontalMotion() {
        sharedGestureController.cancelMotion(horizontalPan.asZoomPanTransform(scale))
        horizontalMotionJob?.cancel()
        horizontalMotionJob = null
    }

    suspend fun animateHorizontalSpring(generation: Long, page: Int) {
        if (horizontalPan.visualOverscrollX == 0f && horizontalPan.visualOverscrollY == 0f) {
            horizontalPan = HorizontalZoomPanPhysics.settle(horizontalPan)
            return
        }
        val startedAtNs = System.nanoTime()
        var previousNanos = 0L
        var settled = false
        while (
            generation == sharedGestureController.snapshot().motionGeneration &&
            page == activeHorizontalPage &&
            !settled
        ) {
            withFrameNanos { now ->
                if (previousNanos != 0L) {
                    val frame = sharedGestureController.stepSpring(
                        generation = generation,
                        elapsedNanos = now - previousNanos,
                        config = ZoomPanPhysicsProfile.HorizontalSpring,
                        overscrollPolicy = horizontalOverscrollPolicy,
                    )
                    if (frame != null) {
                        settled = frame.settled
                        scale = frame.snapshot.transform.scale
                        horizontalPan = frame.snapshot.transform.asHorizontalPanState()
                    }
                    qaProbe?.recordTransform(scale, horizontalPan)
                }
                previousNanos = now
            }
        }
        if (generation == sharedGestureController.snapshot().motionGeneration && page == activeHorizontalPage) {
            val snapshot = sharedGestureController.finishMotion(generation, settleOverscroll = true)
            if (snapshot != null) {
                scale = snapshot.transform.scale
                horizontalPan = snapshot.transform.asHorizontalPanState()
            }
            val durationMs = (System.nanoTime() - startedAtNs) / 1_000_000L
            qaProbe?.recordTransform(scale, horizontalPan)
            qaProbe?.recordSpring(durationMs)
            if (horizontalDebuggable) {
                Log.d(
                    "M1racleReaderH1",
                    "H1_SPRING durationMs=$durationMs legalOffset=" +
                        "(${horizontalPan.legalOffsetX},${horizontalPan.legalOffsetY})",
                )
            }
            commitHorizontalSession(page)
        }
    }

    fun startHorizontalSpring(page: Int) {
        cancelHorizontalMotion()
        val generation = sharedGestureController.beginSpring(horizontalPan.asZoomPanTransform(scale))
        horizontalMotionJob = horizontalScope.launch {
            try {
                animateHorizontalSpring(generation, page)
            } finally {
                if (generation == sharedGestureController.snapshot().motionGeneration) horizontalMotionJob = null
            }
        }
    }

    fun startHorizontalFling(page: Int, fingerVelocity: Offset) {
        cancelHorizontalMotion()
        if (fingerVelocity.getDistance() < platformFlingRunner.minimumFlingVelocityPxPerSecond ||
            HorizontalZoomPanPhysics.owner(scale) != com.m1racle.reader.pdf.HorizontalGestureOwner.INNER_PAN
        ) {
            commitHorizontalSession(page)
            return
        }
        val generation = sharedGestureController.beginPlatformFling(
            horizontalPan.asZoomPanTransform(scale),
        )
        horizontalMotionJob = horizontalScope.launch {
            var flingResult: PlatformPdfFlingResult? = null
            try {
                flingResult = platformFlingRunner.run(
                    releaseVelocityPxPerSecond = fingerVelocity,
                    isCurrent = {
                        generation == sharedGestureController.snapshot().motionGeneration &&
                            page == activeHorizontalPage &&
                            HorizontalZoomPanPhysics.owner(scale) ==
                            com.m1racle.reader.pdf.HorizontalGestureOwner.INNER_PAN
                    },
                    applyDelta = { requested ->
                        val frame = sharedGestureController.applyPlatformFlingDelta(
                            generation = generation,
                            deltaPx = ZoomPanPoint(requested.x, requested.y),
                            bounds = panBounds(page, scale).asZoomPanBounds(),
                            overscrollPolicy = AxisOverscrollPolicy.Disabled,
                        )
                        if (frame == null) {
                            Offset.Zero
                        } else {
                            scale = frame.snapshot.transform.scale
                            horizontalPan = frame.snapshot.transform.asHorizontalPanState()
                            qaProbe?.recordTransform(scale, horizontalPan)
                            Offset(frame.appliedDeltaPx.x, frame.appliedDeltaPx.y)
                        }
                    },
                )
            } finally {
                if (generation == sharedGestureController.snapshot().motionGeneration) {
                    horizontalMotionJob = null
                    sharedGestureController.finishMotion(generation, settleOverscroll = false)
                    if (horizontalDebuggable) {
                        flingResult?.let { result ->
                            Log.d(
                                "M1racleReaderH1",
                                "H1_PLATFORM_FLING release=(${result.releaseVelocityPxPerSecond.x}," +
                                    "${result.releaseVelocityPxPerSecond.y}) splinePosition=" +
                                    "(${result.splinePositionPx.x},${result.splinePositionPx.y}) " +
                                    "plannedFinal=(${result.plannedFinalPositionPx.x}," +
                                    "${result.plannedFinalPositionPx.y}) " +
                                    "displayedPosition=(${result.displayedPositionPx.x}," +
                                    "${result.displayedPositionPx.y}) " +
                                    "durationMs=${result.durationMs} interrupted=${result.interrupted} " +
                                    "terminatedByBounds=${result.terminatedByBounds} " +
                                    "legalOffset=(${horizontalPan.legalOffsetX},${horizontalPan.legalOffsetY})",
                            )
                        }
                    }
                    if (horizontalPan.visualOverscrollX != 0f || horizontalPan.visualOverscrollY != 0f) {
                        startHorizontalSpring(page)
                    } else {
                        commitHorizontalSession(page)
                    }
                }
            }
        }
    }

    // Explicit navigation re-centres the stable strip. It never animates through intermediate pages.
    LaunchedEffect(pageJump?.id, horizontalPointerDown, pageTransition.phase) {
        val jump = pageJump ?: return@LaunchedEffect
        if (horizontalPointerDown || pageTransition.phase != ZoomedPageTransitionPhase.IDLE) {
            return@LaunchedEffect
        }
        if (!com.m1racle.reader.pdf.PagerRequestPolicy.shouldConsume(jump.id, consumedJumpId)) {
            return@LaunchedEffect
        }
        consumedJumpId = jump.id
        cancelHorizontalMotion()
        val target = jump.targetPageIndex.coerceIn(0, state.pageCount - 1)
        pageStripOffsetPx = 0f
        pageTransition = ThreePageStripPhysics.idle(horizontalGestureGeneration)
        pageTransitionTargetSession = null
        pageStrip = ThreePageStripState(target, state.pageCount.coerceAtLeast(1))
        activeHorizontalPage = target
    }

    LaunchedEffect(state.pageIndex) {
        if (horizontalPointerDown) return@LaunchedEffect
        if(activeHorizontalPage!=state.pageIndex){
            cancelHorizontalMotion()
            val outgoingScale = scale
            val outgoingPage = activeHorizontalPage
            val pageDelta = state.pageIndex - activeHorizontalPage
            val outgoingSession = horizontalSession(outgoingPage)
            activeHorizontalPage=state.pageIndex
            val entryDirection = when {
                pageDelta > 0 -> HorizontalPageTransitionDirection.NEXT
                pageDelta < 0 -> HorizontalPageTransitionDirection.PREVIOUS
                else -> null
            }
            val continued = HorizontalZoomPanPhysics.restoreViewportSession(
                session = outgoingSession.copy(pageIndex = state.pageIndex),
                layout = pageLayout(state.pageIndex),
                entryDirection = entryDirection,
            )
            scale = continued.scale
            horizontalPan = continued.pan
            pageStripOffsetPx = 0f
            pageTransition = ThreePageStripPhysics.idle(horizontalGestureGeneration)
            pageTransitionTargetSession = null
            pageStrip = ThreePageStripState(
                currentPage = state.pageIndex,
                pageCount = state.pageCount.coerceAtLeast(1),
            )
            sharedGestureController.replaceTransform(horizontalPan.asZoomPanTransform(scale))
            if (horizontalDebuggable) {
                val targetSession = horizontalSession(state.pageIndex)
                Log.d(
                    "M1racleReaderH14",
                    "H14_PAGE_COMMIT outgoingPage=$outgoingPage targetPage=${state.pageIndex} " +
                        "direction=$entryDirection scaleBefore=$outgoingScale scaleAfter=$scale " +
                        "anchorYBefore=${outgoingSession.pageAnchorYFraction} " +
                        "anchorYAfter=${targetSession.pageAnchorYFraction} " +
                        "entryOffsetX=${horizontalPan.legalOffsetX}",
                )
            }
            qaProbe?.recordPageCommit(outgoingScale, scale, state.pageIndex)
            qaProbe?.recordTransform(scale, horizontalPan)
            onZoomScaleSettled(scale)
            commitHorizontalSession(state.pageIndex)
            logHorizontalBounds(state.pageIndex, scale)
        }
    }

    val activeHorizontalAspect = state.pageAspects[activeHorizontalPage] ?:
        HorizontalZoomPanPhysics.DEFAULT_PAGE_ASPECT
    LaunchedEffect(activeHorizontalPage, viewportSize, activeHorizontalAspect) {
        if (viewportSize.width <= 0 || viewportSize.height <= 0) return@LaunchedEffect
        if (horizontalPointerDown) {
            if (horizontalDebuggable) {
                Log.d(
                    "M1racleReaderH15",
                    "H15_GEOMETRY_APPLY_DEFERRED pointerDown=true pageIndex=$activeHorizontalPage",
                )
            }
            return@LaunchedEffect
        }
        cancelHorizontalMotion()
        val pendingSession = state.viewportSessionState?.normalized(state.pageCount, activeHorizontalPage)
        if (pendingSession != null &&
            pendingSession.generation != appliedHorizontalSessionGeneration &&
            pendingSession.pageIndex == activeHorizontalPage
        ) {
            val restored = HorizontalZoomPanPhysics.restoreViewportSession(
                pendingSession,
                pageLayout(activeHorizontalPage),
            )
            scale = restored.scale
            horizontalPan = restored.pan
            appliedHorizontalSessionGeneration = pendingSession.generation
            if (horizontalDebuggable && pendingSession.readingMode == PdfReadingMode.HORIZONTAL_PAGED) {
                val appliedSession = horizontalSession(activeHorizontalPage)
                Log.d(
                    "M1racleReaderH14",
                    "H14_MODE_APPLY mode=${PdfReadingMode.HORIZONTAL_PAGED} " +
                        "page=${appliedSession.pageIndex} scale=${appliedSession.scale} " +
                        "anchorY=${appliedSession.pageAnchorYFraction} " +
                        "generation=${pendingSession.generation}",
                )
            }
        } else {
            horizontalPan = if (scale <= HorizontalZoomPanPhysics.PAGE_STRIP_SCALE_THRESHOLD) {
                HorizontalPanState()
            } else {
                HorizontalZoomPanPhysics.clampToBounds(
                    horizontalPan,
                    panBounds(activeHorizontalPage, scale),
                )
            }
        }
        sharedGestureController.replaceTransform(horizontalPan.asZoomPanTransform(scale))
        logHorizontalBounds(activeHorizontalPage, scale)
        val layout = pageLayout(activeHorizontalPage)
        qaProbe?.recordLayout(
            layout.viewportWidthPx,
            layout.viewportHeightPx,
            layout.contentTopPx,
            layout.fittedWidthPx,
            layout.fittedHeightPx,
        )
        currentOnEnsureAround(activeHorizontalPage)
        publishHorizontalTileSnapshot(activeHorizontalPage, pointerDown = false)
    }

    fun settleZoomedPageTransition(page: Int, fingerVelocityX: Float) {
        check(!horizontalPointerDown) {
            "page-strip settle cannot start while pointerDown=true"
        }
        if (pageStripSettlingGeneration == pageTransition.gestureGeneration) return
        cancelHorizontalMotion()
        val released = ThreePageStripPhysics.release(
            transition = pageTransition,
            displayedOffsetPx = pageStripOffsetPx,
            viewportWidthPx = viewportSize.width.toFloat(),
            releaseVelocityXPxPerSecond = fingerVelocityX,
            minimumFlingVelocityPxPerSecond = platformFlingRunner.minimumFlingVelocityPxPerSecond,
        )
        pageTransition = released
        pageStripSettlingGeneration = released.gestureGeneration
        settleStartCountThisGesture += 1
        check(settleStartCountThisGesture == 1) {
            "page-strip settle started more than once for gesture ${released.gestureGeneration}"
        }
        qaProbe?.recordStripSettleStart()
        qaProbe?.recordPageTransition(pageTransition, pageStripOffsetPx)
        val targetOffset = ThreePageStripPhysics.settleTargetPx(
            released,
            viewportSize.width.toFloat(),
        )
        if (horizontalDebuggable) {
            val targetBitmap = released.targetPage?.let(state.pageBitmaps::get)
            Log.d(
                "M1racleReaderH15",
                "H15_STRIP_RELEASE pageIndex=$page eventTimeNanos=${System.nanoTime()} " +
                    "offsetPx=$pageStripOffsetPx releaseVelocityX=$fingerVelocityX " +
                    "decision=${released.phase} settleTargetPx=$targetOffset " +
                    "targetPage=${released.targetPage} " +
                    "targetBaseReady=${targetBitmap != null} " +
                    "visibleNeighborBitmapId=${targetBitmap?.let(System::identityHashCode) ?: -1}",
            )
        }
        horizontalMotionJob = horizontalScope.launch {
            val animation = Animatable(pageStripOffsetPx)
            animation.animateTo(targetOffset, animationSpec = spring()) {
                val width = viewportSize.width.toFloat().coerceAtLeast(1f)
                val boundedValue = when (released.direction) {
                    HorizontalPageTransitionDirection.PREVIOUS -> value.coerceIn(0f, width)
                    HorizontalPageTransitionDirection.NEXT -> value.coerceIn(-width, 0f)
                    null -> 0f
                }
                pageStripOffsetPx = boundedValue
                qaProbe?.recordPageTransition(released, boundedValue)
            }
            pageStripOffsetPx = targetOffset
            val settledOffset = pageStripOffsetPx
            if (released.phase == ZoomedPageTransitionPhase.COMMITTING) {
                val targetPage = released.targetPage ?: page
                val outgoingScale = scale
                val beforeRotation = pageStrip
                val oldBitmap = state.pageBitmaps[page] ?: state.pageImage
                val targetBitmap = state.pageBitmaps[targetPage]
                val outgoingSession = horizontalSession(page)
                val continued = HorizontalZoomPanPhysics.restoreViewportSession(
                    session = outgoingSession.copy(pageIndex = targetPage),
                    layout = pageLayout(targetPage),
                    entryDirection = released.direction,
                )
                // At -/+viewport the neighbour is already at visual zero. Rotate its stable
                // physical slot to CURRENT and rebase the strip in the same snapshot.
                val width = viewportSize.width.toFloat().coerceAtLeast(1f)
                val targetRole = when (checkNotNull(released.direction)) {
                    HorizontalPageTransitionDirection.PREVIOUS ->
                        com.m1racle.reader.pdf.PageStripSlotRole.PREVIOUS
                    HorizontalPageTransitionDirection.NEXT ->
                        com.m1racle.reader.pdf.PageStripSlotRole.NEXT
                }
                val beforeRotationTargetX = ThreePageStripPhysics.slotTranslationPx(
                    targetRole,
                    width,
                    settledOffset,
                )
                pageStrip = pageStrip.committed(checkNotNull(released.direction))
                slotRotationCountThisGesture += 1
                activeHorizontalPage = targetPage
                scale = continued.scale
                horizontalPan = continued.pan
                sharedGestureController.replaceTransform(horizontalPan.asZoomPanTransform(scale))
                pageStripOffsetPx = 0f
                offsetRebaseCountThisGesture += 1
                pageIndexChangeCountThisGesture += 1
                check(pageIndexChangeCountThisGesture <= 1)
                check(slotRotationCountThisGesture <= 1)
                pageTransition = ThreePageStripPhysics.idle(released.gestureGeneration)
                pageTransitionTargetSession = null
                val afterRotationCurrentX = ThreePageStripPhysics.slotTranslationPx(
                    com.m1racle.reader.pdf.PageStripSlotRole.CURRENT,
                    width,
                    pageStripOffsetPx,
                )
                val slotRotationVisualJumpPx = abs(afterRotationCurrentX - beforeRotationTargetX)
                check(slotRotationVisualJumpPx <= ThreePageStripPhysics.FINAL_OFFSET_EPSILON_PX) {
                    "slot rotation visual jump $slotRotationVisualJumpPx px"
                }
                qaProbe?.recordSlotRotation(pageStrip, slotRotationVisualJumpPx)
                qaProbe?.recordOffsetRebase()
                qaProbe?.recordStripSettle(targetOffset, settledOffset, pageStripOffsetPx)
                qaProbe?.recordPageCommit(outgoingScale, scale, targetPage)
                qaProbe?.recordTransform(scale, horizontalPan)
                onPageChanged(targetPage)
                onZoomScaleSettled(scale)
                commitHorizontalSession(targetPage)
                currentOnEnsureAround(targetPage)
                if (horizontalDebuggable) {
                    Log.d(
                        "M1racleReaderH15",
                        "H15_PAGE_TRANSITION_COMMIT sourcePage=$page targetPage=$targetPage " +
                            "commitTimeNanos=${System.nanoTime()} " +
                            "settleTargetPx=$targetOffset settleFinalPx=$settledOffset " +
                            "rebaseFinalPx=$pageStripOffsetPx viewportWidthPx=${viewportSize.width} " +
                            "scaleBefore=$outgoingScale scaleAfter=$scale " +
                            "oldCurrentSlotId=${beforeRotation.currentSlotId} " +
                            "oldTargetSlotId=${beforeRotation.nextSlotId.takeIf { released.direction == HorizontalPageTransitionDirection.NEXT } ?: beforeRotation.previousSlotId} " +
                            "newCurrentSlotId=${pageStrip.currentSlotId} " +
                            "slotRotationVisualJumpPx=$slotRotationVisualJumpPx " +
                            "pageIndexChangeCount=$pageIndexChangeCountThisGesture " +
                            "slotRotationCount=$slotRotationCountThisGesture " +
                            "settleStartCount=$settleStartCountThisGesture " +
                            "offsetRebaseCount=$offsetRebaseCountThisGesture " +
                            "pagerProgrammaticScrollCount=0 " +
                            "oldBitmapId=${oldBitmap?.let(System::identityHashCode) ?: -1} " +
                            "targetBitmapId=${targetBitmap?.let(System::identityHashCode) ?: -1}",
                    )
                }
            } else {
                pageStripOffsetPx = 0f
                pageTransition = ThreePageStripPhysics.idle(released.gestureGeneration)
                pageTransitionTargetSession = null
                sharedGestureController.replaceTransform(horizontalPan.asZoomPanTransform(scale))
                qaProbe?.recordStripSettle(targetOffset, settledOffset, pageStripOffsetPx)
                commitHorizontalSession(page)
                if (horizontalDebuggable) {
                    Log.d(
                        "M1racleReaderH15",
                        "H15_PAGE_TRANSITION_CANCEL page=$page settleTargetPx=0.0 " +
                            "settleFinalPx=$settledOffset pageIndexChangeCount=$pageIndexChangeCountThisGesture " +
                            "slotRotationCount=$slotRotationCountThisGesture " +
                            "settleStartCount=$settleStartCountThisGesture " +
                            "offsetRebaseCount=$offsetRebaseCountThisGesture " +
                            "pagerProgrammaticScrollCount=0",
                    )
                }
            }
            horizontalMotionJob = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(horizontalViewportBackground)
            .testTag("h1-horizontal-viewport")
            .onSizeChanged {
                viewportSize = it
                onViewportWidthPx(it.width)
            },
        contentAlignment = Alignment.Center,
    ) {
        pageStrip.slots().forEach { slot ->
            val page = slot.pageIndex ?: return@forEach
            key(slot.stableId) {
            val bmp = if (page == state.pageIndex) {
                state.pageImage ?: state.pageBitmaps[page]
            } else {
                state.pageBitmaps[page]
            }
            val fittedPage = pageLayout(page)
            val fittedGeometry = com.m1racle.reader.pdf.pageGeometryGeneration(
                fittedPage.fittedWidthPx,
                fittedPage.fittedHeightPx,
                rotation = 0,
            )
            val horizontalTiles = PdfTileDisplay.forPage(
                horizontalTilesByPage[page].orEmpty().filter { tile ->
                    tile.key.geometryGeneration == fittedGeometry &&
                        tile.key.fittedPageWidthVersion == fittedPage.fittedWidthPx.roundToInt()
                },
                page,
            )
            val pendingHorizontalRenderSession = state.viewportSessionState
                ?.normalized(state.pageCount, activeHorizontalPage)
                ?.takeIf { session ->
                    session.readingMode == PdfReadingMode.HORIZONTAL_PAGED &&
                        session.pageIndex == activeHorizontalPage &&
                        session.generation != appliedHorizontalSessionGeneration
                }
            val staticRenderedTransform = if (page == activeHorizontalPage) {
                pendingHorizontalRenderSession?.let { session ->
                    HorizontalZoomPanPhysics.restoreViewportSession(session, fittedPage)
                }
            } else if (page == pageTransitionTargetSession?.pageIndex) {
                HorizontalZoomPanPhysics.restoreViewportSession(
                    session = checkNotNull(pageTransitionTargetSession).copy(pageIndex = page),
                    layout = fittedPage,
                    entryDirection = if (page > activeHorizontalPage) {
                        HorizontalPageTransitionDirection.NEXT
                    } else {
                        HorizontalPageTransitionDirection.PREVIOUS
                    },
                )
            } else {
                com.m1racle.reader.pdf.HorizontalPageTransform(1f, HorizontalPanState())
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = ThreePageStripPhysics.slotTranslationPx(
                            role = slot.role,
                            viewportWidthPx = viewportSize.width.toFloat(),
                            stripOffsetPx = pageStripOffsetPx,
                        )
                    }
                    .clipToBounds()
                    .background(horizontalViewportBackground)
                    .pointerInput(
                        slot.stableId,
                        slot.role,
                        activeHorizontalPage,
                        fittedPage.fittedWidthPx,
                        fittedPage.fittedHeightPx,
                    ) {
                        // Any visible portion of the stable strip can interrupt a settle. All
                        // physical slots still feed the same controller and the same CURRENT page.
                        val page = activeHorizontalPage
                        fun pointerInViewport(localPosition: Offset): Offset = Offset(
                            x = ThreePageStripPhysics.pointerXInViewport(
                                localPointerX = localPosition.x,
                                role = slot.role,
                                viewportWidthPx = viewportSize.width.toFloat(),
                                stripOffsetPx = pageStripOffsetPx,
                            ),
                            y = localPosition.y,
                        )
                        awaitPdfGestureLifecycle(sharedGestureController) { down ->
                            cancelHorizontalMotion()
                            gestureTelemetryRing.clear()
                            horizontalPointerDown = true
                            HorizontalRealPathTelemetry.onPointerDown()
                            currentOnZoomGestureActive(true)
                            horizontalGestureGeneration += 1L
                            pageIndexChangeCountThisGesture = 0
                            slotRotationCountThisGesture = 0
                            settleStartCountThisGesture = 0
                            offsetRebaseCountThisGesture = 0
                            pageTransition = ThreePageStripPhysics.interrupt(
                                previous = pageTransition,
                                displayedOffsetPx = pageStripOffsetPx,
                                currentPage = page,
                                pageCount = state.pageCount,
                                generation = horizontalGestureGeneration,
                            )
                            if (!pageTransition.active) pageTransitionTargetSession = null
                            qaProbe?.beginPageStripGesture(
                                strip = pageStrip,
                                pageIndex = page,
                                offsetPx = pageStripOffsetPx,
                                viewportWidthPx = viewportSize.width.toFloat(),
                            )
                            HorizontalRealPathTelemetry.onPageStripGestureStart(
                                strip = pageStrip,
                                pageIndex = page,
                                offsetPx = pageStripOffsetPx,
                                viewportWidthPx = viewportSize.width.toFloat(),
                            )
                            qaProbe?.recordPageTransition(pageTransition, pageStripOffsetPx)
                            horizontalPan = if (
                                scale <= HorizontalZoomPanPhysics.PAGE_STRIP_SCALE_THRESHOLD
                            ) {
                                HorizontalPanState()
                            } else {
                                HorizontalZoomPanPhysics.clampToBounds(
                                    horizontalPan,
                                    panBounds(page, scale),
                                )
                            }
                            val downOrigin = transformOrigin(page)
                            val downViewportPosition = pointerInViewport(down.position)
                            sharedGestureController.beginGesture(
                                currentTransform = horizontalPan.asZoomPanTransform(scale),
                                initialPointers = listOf(
                                    ZoomPanPointer(
                                        down.id.value,
                                        ZoomPanPoint(
                                            downViewportPosition.x,
                                            downViewportPosition.y,
                                        ),
                                    ),
                                ),
                                transformOriginViewportPx = ZoomPanPoint(downOrigin.x, downOrigin.y),
                                boundsForScale = { targetScale ->
                                    panBounds(page, targetScale).asZoomPanBounds()
                                },
                                overscrollPolicy = horizontalOverscrollPolicy,
                            )
                            logHorizontalBounds(page, scale)
                            var velocityTracker = VelocityTracker().also {
                                it.addPosition(down.uptimeMillis, downViewportPosition)
                            }
                            var transformed = false
                            var singlePanUsed = false
                            var lastPanLogMs = -120L
                            var telemetryRawPan = Offset.Zero
                            var telemetryLegal = Offset.Zero
                            var telemetryResidual = Offset.Zero
                            var telemetryRawScaleFactor = 1f
                            var stripRawInputAbsPx = 0f
                            var stripDisplayedDeltaAbsPx = 0f
                            var stripReverseDeltaCount = 0
                            publishHorizontalTileSnapshot(page, pointerDown = true, rebaseFrame = true)
                            do {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                val origin = transformOrigin(page)
                                val controllerFrame = if (pressed.isNotEmpty()) {
                                    val pointers = pressed.map {
                                        val viewportPosition = pointerInViewport(it.position)
                                        ZoomPanPointer(
                                            id = it.id.value,
                                            positionPx = ZoomPanPoint(
                                                viewportPosition.x,
                                                viewportPosition.y,
                                            ),
                                        )
                                    }
                                    sharedGestureController.pointerFrame(
                                        pointers = pointers,
                                        transformOriginViewportPx = ZoomPanPoint(origin.x, origin.y),
                                        boundsForScale = { targetScale ->
                                            panBounds(page, targetScale).asZoomPanBounds()
                                        },
                                        overscrollPolicy = if (
                                            scale <= HorizontalZoomPanPhysics.PAGE_STRIP_SCALE_THRESHOLD
                                        ) AxisOverscrollPolicy.Disabled else horizontalOverscrollPolicy,
                                    )
                                } else null
                                if (pressed.isEmpty()) {
                                    // Freeze release velocity from the real UP/CANCEL sample. A held
                                    // stationary finger may not emit MOVE events, so omitting this
                                    // sample leaves VelocityTracker with stale drag velocity.
                                    event.changes.firstOrNull()?.let { releaseChange ->
                                        velocityTracker.addPosition(
                                            releaseChange.uptimeMillis,
                                            pointerInViewport(releaseChange.position),
                                        )
                                    }
                                }
                                if (pressed.isNotEmpty()) {
                                    val beforeScale = scale
                                    val beforePan = horizontalPan
                                    val frame = checkNotNull(controllerFrame)
                                    horizontalPinching = frame.snapshot.pointerEpoch?.mode ==
                                        ZoomPanPointerMode.TWO_POINTER_PINCH
                                    qaProbe?.recordFocalAnchorDrift(frame.focalAnchorDriftPx)
                                    if (frame.rebased) {
                                        telemetryRawPan = Offset.Zero
                                        telemetryLegal = Offset.Zero
                                        telemetryResidual = Offset.Zero
                                        telemetryRawScaleFactor = 1f
                                        qaProbe?.recordPointerRebase(
                                            beforePan,
                                            frame.snapshot.transform.asHorizontalPanState(),
                                        )
                                        publishHorizontalTileSnapshot(
                                            page,
                                            pointerDown = true,
                                            rebaseFrame = true,
                                        )
                                    }
                                    if (horizontalDebuggable) {
                                        Log.d(
                                            "M1racleReaderH1",
                                                "H1_OWNER scale=$scale pointerCount=${pressed.size} " +
                                                    "owner=${HorizontalZoomPanPhysics.owner(scale)} " +
                                                    "pageTransition=${pageTransition.phase} " +
                                                    "visualOwner=THREE_PAGE_STRIP",
                                        )
                                    }
                                    if (frame.rebased) {
                                        if (pressed.size == 1) {
                                            velocityTracker = VelocityTracker().also {
                                                val change = pressed.first()
                                                it.addPosition(
                                                    change.uptimeMillis,
                                                    pointerInViewport(change.position),
                                                )
                                            }
                                        }
                                        if (horizontalDebuggable) {
                                            Log.d(
                                                "M1racleReaderH1",
                                                "H1_POINTER_EPOCH_REBASE epochId=${frame.snapshot.pointerEpoch?.epochId} " +
                                                    "pointerIds=${frame.snapshot.pointerEpoch?.activePointerIds} " +
                                                    "mode=${frame.snapshot.pointerEpoch?.mode} scale=$scale",
                                            )
                                        }
                                    } else {
                                        if (pressed.size == 1) {
                                            val change = pressed.first()
                                            val rawPan = Offset(
                                                frame.rawCentroidDeltaPx.x,
                                                frame.rawCentroidDeltaPx.y,
                                            )
                                            val candidateScale = frame.snapshot.transform.scale
                                            val candidatePan = frame.snapshot.transform.asHorizontalPanState()
                                            val legalConsumed = Offset(
                                                frame.legalConsumedPx.x,
                                                frame.legalConsumedPx.y,
                                            )
                                            val residual = Offset(frame.residualPx.x, frame.residualPx.y)
                                            telemetryRawPan = rawPan
                                            telemetryLegal = legalConsumed
                                            telemetryResidual = residual
                                            telemetryRawScaleFactor = 1f
                                            qaProbe?.recordPanFrame(
                                                rawDeltaPx = ZoomPanPoint(rawPan.x, rawPan.y),
                                                legalConsumedPx = ZoomPanPoint(
                                                    legalConsumed.x,
                                                    legalConsumed.y,
                                                ),
                                                residualPx = ZoomPanPoint(residual.x, residual.y),
                                            )
                                            velocityTracker.addPosition(
                                                change.uptimeMillis,
                                                pointerInViewport(change.position),
                                            )
                                            val transitionBeforeFrame = pageTransition
                                            val stripOffsetBeforeFrame = pageStripOffsetPx
                                            val stripDrag = ThreePageStripPhysics.drag(
                                                transition = transitionBeforeFrame,
                                                displayedOffsetPx = pageStripOffsetPx,
                                                rawDeltaX = rawPan.x,
                                                rawDeltaY = rawPan.y,
                                                edgeResidualX = residual.x,
                                                currentPage = page,
                                                pageCount = state.pageCount,
                                                viewportWidthPx = viewportSize.width.toFloat(),
                                                previousBaseReady = page <= 0 ||
                                                    state.pageBitmaps[page - 1] != null,
                                                nextBaseReady = page >= state.pageCount - 1 ||
                                                    state.pageBitmaps[page + 1] != null,
                                            )
                                            val nextTransition = stripDrag.transition
                                            pageTransition = nextTransition
                                            pageStripOffsetPx = stripDrag.offsetPx
                                            val displayedStripDelta =
                                                pageStripOffsetPx - stripOffsetBeforeFrame
                                            if (transitionBeforeFrame.active || nextTransition.active) {
                                                stripRawInputAbsPx += abs(rawPan.x)
                                                stripDisplayedDeltaAbsPx += abs(displayedStripDelta)
                                                if (
                                                    abs(rawPan.x) > 0.01f &&
                                                    abs(displayedStripDelta) > 0.01f &&
                                                    rawPan.x * displayedStripDelta < 0f
                                                ) {
                                                    stripReverseDeltaCount += 1
                                                }
                                            }
                                            qaProbe?.recordPageStripFrame(
                                                rawDeltaX = rawPan.x,
                                                displayedDeltaX = displayedStripDelta,
                                                strip = pageStrip,
                                                transition = pageTransition,
                                                offsetPx = pageStripOffsetPx,
                                                viewportWidthPx = viewportSize.width.toFloat(),
                                            )
                                            HorizontalRealPathTelemetry.onPageStripFrame(
                                                rawDeltaX = rawPan.x,
                                                displayedDeltaX = displayedStripDelta,
                                                strip = pageStrip,
                                                transition = pageTransition,
                                                pageIndex = page,
                                                offsetPx = pageStripOffsetPx,
                                                viewportWidthPx = viewportSize.width.toFloat(),
                                            )
                                            qaProbe?.recordPageTransition(
                                                pageTransition,
                                                pageStripOffsetPx,
                                            )
                                            scale = candidateScale
                                            horizontalPan = if (
                                                transitionBeforeFrame.active || nextTransition.active
                                            ) {
                                                candidatePan.copy(
                                                    rawOverscrollX = 0f,
                                                    visualOverscrollX = 0f,
                                                )
                                            } else candidatePan
                                            if (transitionBeforeFrame.active || nextTransition.active) {
                                                sharedGestureController.replaceTransform(
                                                    horizontalPan.asZoomPanTransform(scale),
                                                )
                                            }
                                            if (
                                                nextTransition.active &&
                                                transitionBeforeFrame.targetPage != nextTransition.targetPage
                                            ) {
                                                pageTransitionTargetSession = horizontalSession(page).copy(
                                                    pageIndex = checkNotNull(nextTransition.targetPage),
                                                )
                                                currentOnEnsureAround(page)
                                                val targetBitmap = state.pageBitmaps[nextTransition.targetPage]
                                                qaProbe?.recordStripSurfaces(
                                                    previousBaseReady = page <= 0 ||
                                                        state.pageBitmaps[page - 1] != null,
                                                    currentBaseReady = state.pageImage != null ||
                                                        state.pageBitmaps[page] != null,
                                                    nextBaseReady = page >= state.pageCount - 1 ||
                                                        state.pageBitmaps[page + 1] != null,
                                                    visibleNeighborBitmapId = targetBitmap
                                                        ?.let(System::identityHashCode) ?: -1,
                                                )
                                                if (horizontalDebuggable) {
                                                    Log.d(
                                                        "M1racleReaderH15",
                                                        "H15_STRIP_PREVIEW_START pageIndex=$page " +
                                                            "eventTimeNanos=${System.nanoTime()} " +
                                                            "rawDeltaX=${rawPan.x} legalConsumedX=${legalConsumed.x} " +
                                                            "edgeResidualX=${residual.x} " +
                                                            "pageStripOffsetPx=$pageStripOffsetPx " +
                                                            "transitionProgress=${abs(pageStripOffsetPx) / viewportSize.width.toFloat().coerceAtLeast(1f)} " +
                                                            "targetPage=${nextTransition.targetPage} " +
                                                            "previousBaseReady=${page <= 0 || state.pageBitmaps[page - 1] != null} " +
                                                            "currentBaseReady=${state.pageImage != null || state.pageBitmaps[page] != null} " +
                                                            "nextBaseReady=${page >= state.pageCount - 1 || state.pageBitmaps[page + 1] != null} " +
                                                            "visibleNeighborBitmapId=${targetBitmap?.let(System::identityHashCode) ?: -1}",
                                                    )
                                                }
                                            }
                                            if (stripDrag.blockedByMissingBase && horizontalDebuggable) {
                                                currentOnEnsureAround(page)
                                                Log.e(
                                                    "M1racleReaderH15",
                                                    "H15_STRIP_BLOCKED_MISSING_BASE currentPage=$page " +
                                                        "rawDeltaX=${rawPan.x} residualX=${residual.x} " +
                                                        "previousReady=${page <= 0 || state.pageBitmaps[page - 1] != null} " +
                                                        "nextReady=${page >= state.pageCount - 1 || state.pageBitmaps[page + 1] != null}",
                                                )
                                            }
                                            singlePanUsed = true
                                            qaProbe?.recordTransform(scale, horizontalPan)
                                            publishHorizontalTileSnapshot(page, pointerDown = true)
                                            if (change.positionChanged()) change.consume()
                                            if (
                                                horizontalDebuggable &&
                                                change.uptimeMillis - lastPanLogMs >= 120L
                                            ) {
                                                lastPanLogMs = change.uptimeMillis
                                                Log.d(
                                                    "M1racleReaderH1",
                                                    "H1_PAN rawDelta=(${rawPan.x},${rawPan.y}) " +
                                                        "legalConsumed=(${legalConsumed.x},${legalConsumed.y}) " +
                                                        "residual=(${residual.x},${residual.y}) " +
                                                        "legalOffset=(${horizontalPan.legalOffsetX},${horizontalPan.legalOffsetY}) " +
                                                        "visualOverscroll=(${horizontalPan.visualOverscrollX},${horizontalPan.visualOverscrollY}) " +
                                                        "displayOffset=(${horizontalPan.displayOffsetX},${horizontalPan.displayOffsetY}) " +
                                                        "pageTransition=${pageTransition.phase} " +
                                                        "pageStripOffsetPx=$pageStripOffsetPx " +
                                                        "previousBaseReady=${page <= 0 || state.pageBitmaps[page - 1] != null} " +
                                                        "currentBaseReady=${state.pageImage != null || state.pageBitmaps[page] != null} " +
                                                        "nextBaseReady=${page >= state.pageCount - 1 || state.pageBitmaps[page + 1] != null}",
                                                )
                                            }
                                        } else {
                                            scale = frame.snapshot.transform.scale
                                            horizontalPan = frame.snapshot.transform.asHorizontalPanState()
                                            if (pageTransition.active && pageTransition.targetPage != null) {
                                                pageTransitionTargetSession = horizontalSession(page).copy(
                                                    pageIndex = checkNotNull(pageTransition.targetPage),
                                                )
                                            }
                                            telemetryRawPan = Offset.Zero
                                            telemetryLegal = Offset(
                                                horizontalPan.legalOffsetX - beforePan.legalOffsetX,
                                                horizontalPan.legalOffsetY - beforePan.legalOffsetY,
                                            )
                                            telemetryResidual = Offset.Zero
                                            telemetryRawScaleFactor = frame.rawScaleFactor
                                            transformed = transformed || abs(scale - beforeScale) > 0.0001f
                                            qaProbe?.recordTransform(scale, horizontalPan)
                                            publishHorizontalTileSnapshot(page, pointerDown = true)
                                            pressed.forEach { if (it.positionChanged()) it.consume() }
                                        }
                                    }
                                } else {
                                    horizontalPinching = false
                                }
                                if (horizontalDebuggable) {
                                    val eventUptime = event.changes.firstOrNull()?.uptimeMillis
                                        ?: SystemClock.uptimeMillis()
                                    gestureTelemetryRing.record(
                                        PdfGestureFrameSample(
                                            eventUptimeMillis = eventUptime,
                                            observedUptimeMillis = SystemClock.uptimeMillis(),
                                            rawDeltaPx = ZoomPanPoint(telemetryRawPan.x, telemetryRawPan.y),
                                            legalDeltaPx = ZoomPanPoint(telemetryLegal.x, telemetryLegal.y),
                                            residualPx = ZoomPanPoint(telemetryResidual.x, telemetryResidual.y),
                                            pageTransitionOffsetPx = pageStripOffsetPx,
                                        ),
                                    )
                                }
                                HorizontalRealPathTelemetry.onGestureFrame()
                            } while (event.changes.any { it.pressed })
                            horizontalPointerDown = false
                            HorizontalRealPathTelemetry.onPointerUp()
                            qaProbe?.recordPageStripPointerUp()
                            val gestureSummary = gestureTelemetryRing.summary()
                            qaProbe?.recordGestureSummary(gestureSummary)
                            if (horizontalDebuggable) {
                                Log.d(
                                    "M1racleReaderH15",
                                    "H15_GESTURE_SUMMARY frames=${gestureSummary.frameCount} " +
                                        "inputLatencyP50Ms=${gestureSummary.inputLatencyP50Ms} " +
                                        "inputLatencyP95Ms=${gestureSummary.inputLatencyP95Ms} " +
                                        "maxInputLatencyMs=${gestureSummary.maxInputLatencyMs} " +
                                        "dragGain=(${gestureSummary.legalDragGainX},${gestureSummary.legalDragGainY}) " +
                                        "pageStripDragGain=${if (stripRawInputAbsPx > 0f) stripDisplayedDeltaAbsPx / stripRawInputAbsPx else 1f} " +
                                        "reverseDeltaCount=$stripReverseDeltaCount " +
                                        "pageIndexChangeCount=$pageIndexChangeCountThisGesture " +
                                        "slotRotationCount=$slotRotationCountThisGesture " +
                                        "settleStartCount=$settleStartCountThisGesture " +
                                        "offsetRebaseCount=$offsetRebaseCountThisGesture " +
                                        "pagerProgrammaticScrollCount=0 " +
                                        "maxPageTransitionOffsetPx=${gestureSummary.maxPageTransitionOffsetPx}",
                                )
                            }
                            horizontalPinching = false
                            if (scale <= HorizontalZoomPanPhysics.PAGE_STRIP_SCALE_THRESHOLD) {
                                horizontalPan = HorizontalPanState()
                                sharedGestureController.replaceTransform(
                                    horizontalPan.asZoomPanTransform(scale),
                                )
                            }
                            currentOnZoomGestureActive(false)
                            publishHorizontalTileSnapshot(page, pointerDown = false)
                            logHorizontalBounds(page, scale)
                            if (transformed) onZoomScaleSettled(scale)
                            if (pageTransition.phase == ZoomedPageTransitionPhase.EDGE_PREVIEW) {
                                val velocity = velocityTracker.calculateVelocity()
                                settleZoomedPageTransition(page, velocity.x)
                            } else if (
                                horizontalPan.visualOverscrollX != 0f ||
                                horizontalPan.visualOverscrollY != 0f
                            ) {
                                startHorizontalSpring(page)
                            } else if (singlePanUsed) {
                                val velocity = velocityTracker.calculateVelocity()
                                startHorizontalFling(page, Offset(velocity.x, velocity.y))
                            } else {
                                commitHorizontalSession(page)
                            }
                        }
                    }
                    .pointerInput(slot.stableId, slot.role, activeHorizontalPage) {
                        val page = activeHorizontalPage
                        fun tapPositionInViewport(localPosition: Offset): Offset = Offset(
                            x = ThreePageStripPhysics.pointerXInViewport(
                                localPointerX = localPosition.x,
                                role = slot.role,
                                viewportWidthPx = viewportSize.width.toFloat(),
                                stripOffsetPx = pageStripOffsetPx,
                            ),
                            y = localPosition.y,
                        )
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var maxDistance = 0f
                            val downPosition = tapPositionInViewport(down.position)
                            var lastPosition = downPosition
                            var upTimeMs = down.uptimeMillis
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull()
                                if (change != null) {
                                    lastPosition = tapPositionInViewport(change.position)
                                    upTimeMs = change.uptimeMillis
                                    maxDistance = maxOf(
                                        maxDistance, (lastPosition - downPosition).getDistance(),
                                    )
                                }
                            } while (event.changes.any { it.pressed })
                            if (maxDistance > viewConfig.touchSlop) {
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
                                val oldScale = scale
                                horizontalPan = if (
                                    target <= HorizontalZoomPanPhysics.PAGE_STRIP_SCALE_THRESHOLD
                                ) {
                                    HorizontalPanState()
                                } else {
                                    val origin = transformOrigin(page)
                                    HorizontalZoomPanPhysics.rebaseLegalForPinch(
                                        baseState = HorizontalZoomPanPhysics.settle(horizontalPan),
                                        baseScale = oldScale,
                                        newScale = target,
                                        basePointerX = lastPosition.x,
                                        basePointerY = lastPosition.y,
                                        pointerX = lastPosition.x,
                                        pointerY = lastPosition.y,
                                        transformOriginX = origin.x,
                                        transformOriginY = origin.y,
                                        bounds = panBounds(page, target),
                                    )
                                }
                                scale = target
                                sharedGestureController.replaceTransform(
                                    horizontalPan.asZoomPanTransform(scale),
                                )
                                lastTapTimeMs = 0L
                                lastTapPosition = null
                                onZoomScaleSettled(target)
                                commitHorizontalSession(page)
                            } else {
                                lastTapTimeMs = now
                                lastTapPosition = lastPosition
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                when (ZoomPageOverlayMath.pageTap(lastPosition.x, w, false, scale)) {
                                    PageTap.PREVIOUS -> if (state.canGoPrevious) onPrevious() else Unit
                                    PageTap.NEXT -> if (state.canGoNext) onNext() else Unit
                                    PageTap.NONE -> onToggleChrome()
                                }
                            }
                        }
                    }
                    .padding(top = with(horizontalDensity) { fittedPage.contentTopPx.toDp() }),
                contentAlignment = Alignment.TopCenter,
            ) {
                when {
                    bmp != null -> {
                        Surface(
                            // Registered paper-sheet radius (matches the intentional paper-white exception).
                            shape = RoundedCornerShape(2.dp),
                            color = Color.White,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                scheme.outlineVariant,
                            ),
                            modifier = Modifier
                                .size(
                                    width = with(horizontalDensity) {
                                        fittedPage.fittedWidthPx.coerceAtLeast(1f).toDp()
                                    },
                                    height = with(horizontalDensity) {
                                        fittedPage.fittedHeightPx.coerceAtLeast(1f).toDp()
                                    },
                                )
                                 .graphicsLayer {
                                     transformOrigin = TransformOrigin(0.5f, 0f)
                                     val activeTransform = if (
                                         page == activeHorizontalPage && staticRenderedTransform == null
                                     ) {
                                         com.m1racle.reader.pdf.HorizontalPageTransform(scale, horizontalPan)
                                     } else {
                                         staticRenderedTransform ?:
                                             com.m1racle.reader.pdf.HorizontalPageTransform(1f, HorizontalPanState())
                                     }
                                     scaleX = activeTransform.scale
                                     scaleY = activeTransform.scale
                                     translationX = activeTransform.pan.displayOffsetX
                                     translationY = activeTransform.pan.displayOffsetY
                                 }
                                 .testTag("h1-horizontal-page-$page"),
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                Image(
                                    bitmap = bmp,
                                    contentDescription = stringResource(
                                        R.string.pdf_page_cd,
                                        page + 1,
                                        state.pageCount,
                                    ),
                                    contentScale = ContentScale.Fit,
                                    filterQuality = FilterQuality.Medium,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                if (horizontalTiles.isNotEmpty()) {
                                    Canvas(Modifier.fillMaxSize()) {
                                        horizontalTiles.forEach { tile ->
                                            val source = tile.contentRectPx
                                            val destination = tile.pageRect.roundedPixelRect()
                                            drawImage(
                                                image = tile.bitmap,
                                                srcOffset = IntOffset(source.left, source.top),
                                                srcSize = IntSize(
                                                    source.width.coerceAtLeast(1),
                                                    source.height.coerceAtLeast(1),
                                                ),
                                                dstOffset = IntOffset(destination.left, destination.top),
                                                dstSize = IntSize(
                                                    destination.width.coerceAtLeast(1),
                                                    destination.height.coerceAtLeast(1),
                                                ),
                                            )
                                        }
                                    }
                                }
                                // R26/R27 标注覆盖层：与页面图像共享局部坐标并随页面变换。
                                pageAnnotation(page)
                            }
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
        }

        // Semi-transparent bottom overlay controls (not a fixed Scaffold bottom bar).
        // Edge/center taps handled via detectTapGestures on the page (does not block pager swipe).
    }

}
