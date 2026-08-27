package com.markleaf.app.ui.pdf

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.markleaf.app.ui.material.GlassMaterialRole
import com.markleaf.app.ui.material.GlassMaterialEngine
import com.markleaf.app.ui.material.glassMutedContentAlpha
import com.markleaf.app.ui.material.glassMaterial
import com.markleaf.app.ui.material.glassPressedContentOverlayAlpha
import kotlinx.coroutines.launch

/**
 * Reader More — Main parent card + anchored nested child (ref screenshots).
 *
 *  ① INLINE: 阅读方式 / 阅读界面 expand inside MAIN only.
 *  ② NESTED: 设置教材页码 / 教材目录 / 页面缩略图 / 导出章节 open a foreground
 *     child from that row’s boundsInRoot. MAIN stays unmoved with full alpha;
 *     child covers Main’s lower portion. Close reverse-morphs to the same row.
 *     Single glass token for Main/child/circles; no Popup/BottomSheet/dim.
 */
/**
 * Single exclusive menu state (r26.1). Never BasicExpanded + AdvancedOpen together.
 *
 * Preparing* = collapse Basic → wait for Main six-row geometry → then AdvancedOpen.
 */
enum class ReaderMoreState {
    CLOSED,
    MAIN,
    /** Basic: 阅读方式 */
    INLINE_READING_MODE,
    /** Basic: 阅读界面 */
    INLINE_READING_UI,
    /** Phase-1: collapse Basic, restore Main six-row before Child morph. */
    PREPARING_CHILD_PAGE_MAPPING,
    PREPARING_CHILD_TOC,
    PREPARING_CHILD_THUMBNAILS,
    PREPARING_CHILD_EXPORT,
    /** Advanced open (Child morph running or open). */
    CHILD_PAGE_MAPPING,
    CHILD_TOC,
    CHILD_THUMBNAILS,
    CHILD_EXPORT,
}

/** Basic inline expansions (阅读方式 / 阅读界面). */
fun ReaderMoreState.isInline(): Boolean =
    this == ReaderMoreState.INLINE_READING_MODE || this == ReaderMoreState.INLINE_READING_UI

/** Two-phase prep before Advanced open. */
fun ReaderMoreState.isPreparing(): Boolean = when (this) {
    ReaderMoreState.PREPARING_CHILD_PAGE_MAPPING,
    ReaderMoreState.PREPARING_CHILD_TOC,
    ReaderMoreState.PREPARING_CHILD_THUMBNAILS,
    ReaderMoreState.PREPARING_CHILD_EXPORT -> true
    else -> false
}

/** Nested Child card open (or reverse-morphing). */
fun ReaderMoreState.isChild(): Boolean = when (this) {
    ReaderMoreState.CHILD_PAGE_MAPPING,
    ReaderMoreState.CHILD_TOC,
    ReaderMoreState.CHILD_THUMBNAILS,
    ReaderMoreState.CHILD_EXPORT -> true
    else -> false
}

/** Map PREPARING_* → CHILD_* target. */
fun ReaderMoreState.preparingTarget(): ReaderMoreState? = when (this) {
    ReaderMoreState.PREPARING_CHILD_PAGE_MAPPING -> ReaderMoreState.CHILD_PAGE_MAPPING
    ReaderMoreState.PREPARING_CHILD_TOC -> ReaderMoreState.CHILD_TOC
    ReaderMoreState.PREPARING_CHILD_THUMBNAILS -> ReaderMoreState.CHILD_THUMBNAILS
    ReaderMoreState.PREPARING_CHILD_EXPORT -> ReaderMoreState.CHILD_EXPORT
    else -> null
}

/** CHILD_* → PREPARING_* for two-phase open from Basic. */
fun ReaderMoreState.toPreparing(): ReaderMoreState = when (this) {
    ReaderMoreState.CHILD_PAGE_MAPPING -> ReaderMoreState.PREPARING_CHILD_PAGE_MAPPING
    ReaderMoreState.CHILD_TOC -> ReaderMoreState.PREPARING_CHILD_TOC
    ReaderMoreState.CHILD_THUMBNAILS -> ReaderMoreState.PREPARING_CHILD_THUMBNAILS
    ReaderMoreState.CHILD_EXPORT -> ReaderMoreState.PREPARING_CHILD_EXPORT
    else -> this
}

/**
 * Pure state transitions. Advanced open from Basic always goes PREPARING first.
 */
object ReaderMoreStateMachine {

    fun onToggleOpen(current: ReaderMoreState): ReaderMoreState = when (current) {
        ReaderMoreState.CLOSED -> ReaderMoreState.MAIN
        else -> current
    }

    fun onSectionToggle(current: ReaderMoreState, section: ReaderMoreState): ReaderMoreState {
        require(section.isInline()) { "section must be INLINE_*" }
        return when {
            // Advanced open: host must close Child first (not jump to inline here).
            current.isChild() || current.isPreparing() -> current
            current == ReaderMoreState.MAIN -> section
            current.isInline() && current == section -> ReaderMoreState.MAIN
            current.isInline() -> section
            else -> section
        }
    }

    fun onOptionSelected(current: ReaderMoreState): ReaderMoreState = ReaderMoreState.MAIN

    /**
     * Open advanced: from Basic → PREPARING (collapse first);
     * from Main → CHILD immediately (canonical bounds available).
     */
    fun onOpenChild(current: ReaderMoreState, child: ReaderMoreState): ReaderMoreState {
        require(child.isChild()) { "child must be CHILD_*" }
        return when {
            current.isInline() -> child.toPreparing()
            current.isPreparing() -> current // already preparing
            current.isChild() -> child // switch advanced (host may remorph)
            else -> child
        }
    }

    /** Preparing layout ready → Advanced open. */
    fun onPreparingReady(current: ReaderMoreState): ReaderMoreState =
        current.preparingTarget() ?: current

    fun onChildCollapse(current: ReaderMoreState): ReaderMoreState = ReaderMoreState.MAIN

    fun onDismiss(current: ReaderMoreState): ReaderMoreState = ReaderMoreState.CLOSED
}

// ── Geometry & tokens (r26.1: Parent ~220dp compact) ─────────────────────
internal val MoreCircleSize = 44.dp
/** Parent width preferred 220dp (band 212–224). */
internal val MoreMenuWidth = 220.dp
internal val MoreMenuCornerClosed = 22.dp
internal val MoreMenuCorner = 28.dp
internal val MenuRowHeight = 52.dp
internal val MenuOptionRowHeight = 48.dp
internal val MenuIconSize = 24.dp
internal val MenuIconColWidth = 24.dp
internal val MenuIconGap = 14.dp
internal val MenuRowHPad = 18.dp
internal val MenuVerticalPad = 3.dp
internal val OptionIndent = MenuRowHPad + MenuIconColWidth + MenuIconGap

/** Level-1 label: 16–17sp Medium/Semibold (not pure black bold print look). */
internal val Level1FontSize = 16.sp
internal val Level1FontWeight = FontWeight.SemiBold
/** Level-2 / secondary: 13–14sp Regular/Medium. */
internal val Level2FontSize = 13.sp
internal val Level2FontWeight = FontWeight.Normal

// ── Motion ────────────────────────────────────────────────────────────────
private const val MorphCollapseMs = 170
/** Emphasized decelerate / FastOutSlowIn-class. */
internal val MoreMorphEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * R22.2 open-morph spring: the card grows out of the More button anchor
 * (top-right pinned) with one damped spring; close stays a short deterministic
 * tween so the host's morph-handoff window is exact.
 */
internal val MoreMorphSpring = SpringSpec<Float>(
    dampingRatio = 0.8f,
    stiffness = 450f,
)
/** Inline expansion/collapse duration (single authoritative progress p). */
private const val InlineProgressMs = 175

/**
 * One entry of the morphing "more" card (level-1 row). [closeOnClick] gates
 * the generic close-after-tap: task entries keep the card alive and grow the
 * Advanced Card instead.
 */
data class PdfChromeMenuItem(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val closeOnClick: Boolean = true,
)

/** Check-marked option inside an inline-expandable group. */
data class PdfChromeMenuOption(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

/** Level-1 row that carries an inline expansion (kind must be INLINE_*). */
data class PdfChromeSubmenuEntry(
    val kind: ReaderMoreState,
    val icon: ImageVector,
    val label: String,
)

/** Row index inside the parent content (vertical pad + index × row height). */
internal fun parentRowTop(rowIndex: Int): Dp = MenuVerticalPad + MenuRowHeight * rowIndex

/**
 * The four task entries live at parent rows 2..5 (阅读方式/阅读界面 are 0/1).
 * Each CHILD_* state owns EXACTLY ONE row — reverse morph returns there only.
 */
internal fun childRowIndex(state: ReaderMoreState): Int = when (state) {
    ReaderMoreState.CHILD_PAGE_MAPPING -> 2
    ReaderMoreState.CHILD_TOC -> 3
    ReaderMoreState.CHILD_THUMBNAILS -> 4
    ReaderMoreState.CHILD_EXPORT -> 5
    else -> -1
}

/**
 * R25.2 pure geometry: the parent card's real content height under a single
 * authoritative progress per section. Each section contributes
 * `measuredExpandedHeight × p`; the card bottom follows this value every
 * frame — no separate size animation, no white tail.
 *
 * Invariants (tested):
 *  - p=0 → exactly baseHeight (no empty expanded space while children hidden);
 *  - p monotonically 0→1 ⇒ height monotonically baseHeight→baseHeight+Σh,
 *    with NO jump (siblings Y continuous);
 *  - two sections switching A→B stay continuous (no blank pause).
 */
internal fun inlineCardHeightPx(
    baseHeightPx: Float,
    sections: List<Pair<Float, Float>>,
): Float = baseHeightPx + sections.fold(0f) { acc, (h, p) -> acc + h * p.coerceIn(0f, 1f) }

/**
 * THE Reader more control + full-screen overlay host (same Compose root space).
 * MAIN never de-emphasizes; nested child uses AnchoredNestedPopover for all
 * four CHILD_* states with real bodies.
 */
@Composable
internal fun ReaderMoreHost(
    state: ReaderMoreState,
    closing: Boolean,
    childClosing: Boolean,
    contentDescription: String?,
    onToggleOpen: () -> Unit,
    onCloseRequest: () -> Unit,
    onChildCollapsed: () -> Unit,
    onSectionToggle: (ReaderMoreState) -> Unit,
    submenuEntries: List<PdfChromeSubmenuEntry>,
    plainMenuItems: List<PdfChromeMenuItem>,
    readingModeOptions: List<PdfChromeMenuOption>,
    topBarModeOptions: List<PdfChromeMenuOption>,
    nestedData: ReaderAdvancedData? = null,
    nestedMaxHeightDp: Dp = 520.dp,
    /** Root-space safe viewport bottom. */
    viewportBottomRoot: Float = Float.POSITIVE_INFINITY,
    /**
     * Called when PREPARING_* has a stable Main six-row source bounds and can
     * promote to CHILD_* (r26.1 two-phase Basic→Advanced).
     */
    onPreparingLayoutReady: (ReaderMoreState) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val active = state != ReaderMoreState.CLOSED
    val density = LocalDensity.current
    val circlePx = with(density) { MoreCircleSize.toPx() }
    val menuWidthPx = with(density) { MoreMenuWidth.toPx() }
    // Expose preparing callback without unstable remember of lambda identity issues
    // (parent passes stable lambda).
    val cardWidth = remember { Animatable(circlePx) }
    val cardHeight = remember { Animatable(circlePx) }

    // ── Unique coordinate system: ALL rects via boundsInRoot() ──────────────
    var hostOriginRoot by remember { mutableStateOf(Rect.Zero) }
    var mainCardRectRoot by remember { mutableStateOf(Rect.Zero) }
    var rowRectsRoot by remember { mutableStateOf<Map<Int, Rect>>(emptyMap()) }
    /** Canonical Main six-row bounds — only updated when layout is Main (no Basic expand). */
    var canonicalMainBounds by remember { mutableStateOf<Map<Int, Rect>>(emptyMap()) }
    var frozenSourceRoot by remember { mutableStateOf<Rect?>(null) }
    var frozenParentRoot by remember { mutableStateOf<Rect?>(null) }
    var frozenChildState by remember { mutableStateOf<ReaderMoreState?>(null) }
    val nestedActive =
        (state.isChild() || childClosing) &&
            frozenSourceRoot != null &&
            frozenParentRoot != null &&
            frozenParentRoot != Rect.Zero &&
            frozenChildState != null &&
            nestedData != null

    // ── Inline section progress (MAIN card) ─────────────────────────────────
    val readingModeProgress = remember { Animatable(0f) }
    val topBarProgress = remember { Animatable(0f) }
    var readingModeExpandedHeightPx by remember { mutableFloatStateOf(0f) }
    var topBarExpandedHeightPx by remember { mutableFloatStateOf(0f) }
    val totalRowCount = submenuEntries.size + plainMenuItems.size
    val baseHeightPx = with(density) {
        (MenuVerticalPad * 2 + MenuRowHeight * totalRowCount).toPx()
    }
    val sixRowLayout =
        readingModeProgress.value == 0f &&
            topBarProgress.value == 0f &&
            !state.isInline()

    // Preparing / Child: force Basic collapsed so Parent is standard six-row.
    LaunchedEffect(state) {
        if (state.isPreparing() || state.isChild()) {
            readingModeProgress.snapTo(0f)
            topBarProgress.snapTo(0f)
        }
    }

    // Update canonical Main bounds only when truly Main six-row (not Basic/Preparing/Child).
    LaunchedEffect(state, rowRectsRoot, mainCardRectRoot, sixRowLayout) {
        if (state == ReaderMoreState.MAIN && sixRowLayout && mainCardRectRoot.width > 0f) {
            val next = mutableMapOf<Int, Rect>()
            for (i in 2..5) {
                rowRectsRoot[i]?.let { next[i] = it }
            }
            if (next.size == 4) {
                canonicalMainBounds = next
            }
        }
    }

    // Two-phase: PREPARING → wait for six-row layout → freeze canonical bounds → CHILD.
    LaunchedEffect(
        state, childClosing, closing, rowRectsRoot, mainCardRectRoot,
        readingModeProgress.value, topBarProgress.value, canonicalMainBounds,
    ) {
        when {
            closing && !childClosing -> {
                frozenSourceRoot = null
                frozenParentRoot = null
                frozenChildState = null
            }
            state.isPreparing() && sixRowLayout -> {
                val target = state.preparingTarget() ?: return@LaunchedEffect
                val rowIdx = childRowIndex(target)
                // Prefer canonical Main bounds; fall back to current six-row measure.
                val row = canonicalMainBounds[rowIdx] ?: rowRectsRoot[rowIdx]
                if (row != null && mainCardRectRoot.width > 0f &&
                    kotlin.math.abs(mainCardRectRoot.height - baseHeightPx) < 8f
                ) {
                    // Wait one frame so Compose finishes measure after collapse.
                    kotlinx.coroutines.delay(1)
                    val row2 = canonicalMainBounds[rowIdx] ?: rowRectsRoot[rowIdx] ?: row
                    frozenSourceRoot = row2
                    frozenParentRoot = mainCardRectRoot
                    frozenChildState = target
                    onPreparingLayoutReady(target)
                }
            }
            state.isChild() -> {
                if (frozenChildState != state || frozenSourceRoot == null) {
                    val rowIdx = childRowIndex(state)
                    val row = canonicalMainBounds[rowIdx] ?: rowRectsRoot[rowIdx]
                    if (row != null && mainCardRectRoot.width > 0f) {
                        frozenSourceRoot = row
                        frozenParentRoot = mainCardRectRoot
                        frozenChildState = state
                    }
                }
            }
            !state.isChild() && !state.isPreparing() && !childClosing -> {
                frozenSourceRoot = null
                frozenParentRoot = null
                frozenChildState = null
            }
        }
    }

    val showMainSurface = active || closing

    // Inline expand. Preparing/Child keep progress at 0 (no dual expansion).
    LaunchedEffect(state, nestedActive) {
        when (state) {
            ReaderMoreState.INLINE_READING_MODE -> {
                if (nestedActive || state.isPreparing()) return@LaunchedEffect
                launch {
                    if (topBarProgress.value > 0f) {
                        topBarProgress.animateTo(0f, tween(InlineProgressMs, easing = MoreMorphEasing))
                    }
                }
                launch {
                    readingModeProgress.snapTo(0f)
                    readingModeProgress.animateTo(1f, tween(InlineProgressMs, easing = MoreMorphEasing))
                }
            }
            ReaderMoreState.INLINE_READING_UI -> {
                if (nestedActive || state.isPreparing()) return@LaunchedEffect
                launch {
                    if (readingModeProgress.value > 0f) {
                        readingModeProgress.animateTo(0f, tween(InlineProgressMs, easing = MoreMorphEasing))
                    }
                }
                launch {
                    topBarProgress.snapTo(0f)
                    topBarProgress.animateTo(1f, tween(InlineProgressMs, easing = MoreMorphEasing))
                }
            }
            else -> {
                if (state.isChild() || state.isPreparing() || nestedActive) {
                    readingModeProgress.snapTo(0f)
                    topBarProgress.snapTo(0f)
                } else {
                    launch { readingModeProgress.animateTo(0f, tween(InlineProgressMs, easing = MoreMorphEasing)) }
                    launch { topBarProgress.animateTo(0f, tween(InlineProgressMs, easing = MoreMorphEasing)) }
                }
            }
        }
    }

    // ── MAIN container size (circle ↔ menu card) ────────────────────────────
    LaunchedEffect(active, closing) {
        if (active && !closing) {
            cardWidth.animateTo(menuWidthPx, MoreMorphSpring)
        }
    }
    LaunchedEffect(
        readingModeProgress.value, topBarProgress.value,
        readingModeExpandedHeightPx, topBarExpandedHeightPx,
        active, closing, state, nestedActive,
    ) {
        if (closing) {
            cardHeight.animateTo(circlePx, tween(MorphCollapseMs, easing = MoreMorphEasing))
        } else if (active) {
            // Preparing/Child: lock Parent to standard six-row height.
            val target = if (state.isChild() || state.isPreparing() || nestedActive) {
                baseHeightPx
            } else {
                inlineCardHeightPx(
                    baseHeightPx = baseHeightPx,
                    sections = listOf(
                        readingModeExpandedHeightPx to readingModeProgress.value,
                        topBarExpandedHeightPx to topBarProgress.value,
                    ),
                )
            }
            if (cardHeight.value <= circlePx * 1.05f) {
                cardHeight.animateTo(target, MoreMorphSpring)
            } else {
                cardHeight.snapTo(target)
            }
        }
    }
    LaunchedEffect(closing) {
        if (closing) {
            launch {
                cardWidth.animateTo(circlePx, tween(MorphCollapseMs, easing = MoreMorphEasing))
            }
            launch {
                readingModeProgress.snapTo(0f)
                topBarProgress.snapTo(0f)
            }
        }
    }

    // ── R22.2 single-clock spatial morph ─────────────────────────────────────
    // One expandFraction (derived from the spring-driven card width) owns the
    // corner radius, content fade-in, icon fade-out and the content's slight
    // scale from the top-right anchor. No independent alpha clocks: opening
    // reads as "the button grows into the card", closing as the exact reverse,
    // with the anchor (top-right) invariant throughout.
    val expandFraction = ((cardWidth.value - circlePx) / (menuWidthPx - circlePx))
        .coerceIn(0f, 1f)
    val shape = RoundedCornerShape(
        MoreMenuCornerClosed + (MoreMenuCorner - MoreMenuCornerClosed) * expandFraction,
    )

    val contentAlpha = (expandFraction * 1.6f).coerceAtMost(1f)
    val iconAlpha = (1f - expandFraction * 2.5f).coerceAtLeast(0f)
    val contentScale = 0.94f + 0.06f * expandFraction

    var lastTop by remember { mutableFloatStateOf(Float.NaN) }
    var lastRight by remember { mutableFloatStateOf(Float.NaN) }
    fun logBounds(tag: String, coords: LayoutCoordinates) {
        if (!com.markleaf.app.BuildConfig.READER_PERFORMANCE_EXPERIMENTS_ENABLED) return
        val bounds = coords.boundsInWindow()
        if (lastTop.isNaN() || kotlin.math.abs(bounds.top - lastTop) > 0.5f ||
            kotlin.math.abs(bounds.right - lastRight) > 0.5f
        ) {
            Log.i(
                "MORE_SURFACE",
                "state=$tag top=${"%.1f".format(bounds.top)} right=${"%.1f".format(bounds.right)} " +
                    "w=${"%.1f".format(bounds.width)} h=${"%.1f".format(bounds.height)}",
            )
            lastTop = bounds.top
            lastRight = bounds.right
        }
    }
    val stateTag = when {
        closing -> "closing"
        nestedActive -> "anchored_child"
        state.isInline() -> "inline"
        state == ReaderMoreState.MAIN -> "expanded_level1"
        else -> "collapsed"
    }

    // ReaderMoreOverlayHost: full-screen Box in the same Compose tree as MAIN.
    // No Popup / Dialog / second window. Coordinate space = boundsInRoot.
    Box(
        modifier = modifier
            .then(if (active || closing || nestedActive) Modifier.fillMaxSize() else Modifier)
            .onGloballyPositioned { coords ->
                hostOriginRoot = coords.boundsInRoot()
            },
    ) {
        if (!active && !closing) {
            MoreCircleButton(
                contentDescription = contentDescription,
                onClick = onToggleOpen,
                modifier = Modifier
                    .size(MoreCircleSize)
                    .align(Alignment.TopEnd),
            )
        } else {
            // ── MAIN — never de-emphasized, never moved under nested popover ─
            if (showMainSurface) {
                Box(
                    modifier = Modifier
                        .size(
                            width = with(density) { cardWidth.value.toDp() },
                            height = with(density) { cardHeight.value.toDp() },
                        )
                        .align(Alignment.TopEnd)
                        .glassMaterial(
                            role = GlassMaterialRole.Menu,
                            shape = shape,
                        )
                        .onGloballyPositioned { coords ->
                            mainCardRectRoot = coords.boundsInRoot()
                            logBounds(stateTag, coords)
                        },
                ) {
                    Box(
                        modifier = Modifier
                            .width(MoreMenuWidth)
                            .align(Alignment.TopEnd)
                            .graphicsLayer {
                                alpha = contentAlpha
                                scaleX = contentScale
                                scaleY = contentScale
                                // Grow from / shrink into the More button anchor.
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f)
                            },
                    ) {
                        MainMenuContent(
                            state = state,
                            onSectionToggle = onSectionToggle,
                            onCloseRequest = onCloseRequest,
                            submenuEntries = submenuEntries,
                            plainMenuItems = plainMenuItems,
                            readingModeOptions = readingModeOptions,
                            topBarModeOptions = topBarModeOptions,
                            readingModeProgress = readingModeProgress.value,
                            topBarProgress = topBarProgress.value,
                            readingModeExpandedHeightPx = readingModeExpandedHeightPx,
                            topBarExpandedHeightPx = topBarExpandedHeightPx,
                            onReadingModeHeightChanged = { readingModeExpandedHeightPx = it },
                            onTopBarHeightChanged = { topBarExpandedHeightPx = it },
                            onRowBoundsRoot = { rowIndex, rect ->
                                rowRectsRoot = rowRectsRoot + (rowIndex to rect)
                            },
                        )
                        Box(
                            modifier = Modifier
                                .size(MoreCircleSize)
                                .align(Alignment.TopEnd)
                                .graphicsLayer { alpha = iconAlpha },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = null,
                                modifier = Modifier.size(MenuIconSize),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    // Block MAIN interaction while nested TOC is open (occlusion).
                    if (nestedActive) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .pointerInput(Unit) {
                                    detectTapGestures(onTap = {})
                                },
                        )
                    }
                }
            }

            // ── Anchored nested child (all four advanced entries) ────────────
            // nestedActive already requires non-null frozen source/parent/child/data.
            if (nestedActive && hostOriginRoot != Rect.Zero) {
                AnchoredNestedPopover(
                    state = frozenChildState!!,
                    data = nestedData!!,
                    parentRectRoot = frozenParentRoot!!,
                    sourceRectRoot = frozenSourceRoot!!,
                    hostOriginRoot = hostOriginRoot,
                    viewportBottomRoot = viewportBottomRoot,
                    maxHeightDp = nestedMaxHeightDp,
                    closing = childClosing || closing,
                    onCollapsed = onChildCollapsed,
                    onCloseRequest = onCloseRequest,
                )
            }
        }
    }
}

/**
 * The collapsed three-dot circle: a fixed 44dp click host completely separate
 * from the morphing geometry host. Own interaction source, NO Material ripple
 * (neutral bounded press feedback only — the ripple ring must never ride the
 * container).
 */
@Composable
private fun MoreCircleButton(
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = modifier
            .glassMaterial(
                role = GlassMaterialRole.Control,
                shape = CircleShape,
                pressed = pressed,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .semantics {
                this.contentDescription = contentDescription.orEmpty()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.MoreVert,
            contentDescription = null,
            modifier = Modifier.size(MenuIconSize),
            tint = scheme.onSurface,
        )
    }
}

/**
 * MAIN menu content: six level-1 rows. Lightweight sections expand INLINE
 * inside this same card. Child option text left edge aligns with the MAIN
 * title column. Every row reports boundsInRoot() continuously for nested
 * popover targeting (unique coordinate system).
 */
@Composable
private fun MainMenuContent(
    state: ReaderMoreState,
    onSectionToggle: (ReaderMoreState) -> Unit,
    onCloseRequest: () -> Unit,
    submenuEntries: List<PdfChromeSubmenuEntry>,
    plainMenuItems: List<PdfChromeMenuItem>,
    readingModeOptions: List<PdfChromeMenuOption>,
    topBarModeOptions: List<PdfChromeMenuOption>,
    readingModeProgress: Float,
    topBarProgress: Float,
    readingModeExpandedHeightPx: Float,
    topBarExpandedHeightPx: Float,
    onReadingModeHeightChanged: (Float) -> Unit,
    onTopBarHeightChanged: (Float) -> Unit,
    onRowBoundsRoot: (rowIndex: Int, bounds: Rect) -> Unit,
) {
    val readingEntry = submenuEntries.firstOrNull { it.kind == ReaderMoreState.INLINE_READING_MODE }
    val topBarEntry = submenuEntries.firstOrNull { it.kind == ReaderMoreState.INLINE_READING_UI }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = MenuVerticalPad),
    ) {
        if (readingEntry != null) {
            PdfChromeMenuRow(
                icon = readingEntry.icon,
                label = readingEntry.label,
                chevron = true,
                chevronRotation = readingModeProgress * 90f,
                onClick = { onSectionToggle(ReaderMoreState.INLINE_READING_MODE) },
            )
        }
        if (readingEntry != null) {
            InlineExpandableSection(
                expanded = state == ReaderMoreState.INLINE_READING_MODE,
                expandedHeightPx = readingModeExpandedHeightPx,
                progress = readingModeProgress,
                onMeasuredHeightChanged = onReadingModeHeightChanged,
            ) {
                readingModeOptions.forEach { opt ->
                    PdfChromeMenuRow(
                        icon = null,
                        label = opt.label,
                        checked = opt.selected,
                        chevron = false,
                        indentLabel = true,
                        rowHeight = MenuOptionRowHeight,
                        labelSize = Level2FontSize,
                        labelWeight = Level2FontWeight,
                        onClick = {
                            opt.onClick()
                            onSectionToggle(ReaderMoreState.INLINE_READING_MODE)
                        },
                    )
                }
            }
        }
        if (topBarEntry != null) {
            PdfChromeMenuRow(
                icon = topBarEntry.icon,
                label = topBarEntry.label,
                chevron = true,
                chevronRotation = topBarProgress * 90f,
                onClick = { onSectionToggle(ReaderMoreState.INLINE_READING_UI) },
            )
        }
        if (topBarEntry != null) {
            InlineExpandableSection(
                expanded = state == ReaderMoreState.INLINE_READING_UI,
                expandedHeightPx = topBarExpandedHeightPx,
                progress = topBarProgress,
                onMeasuredHeightChanged = onTopBarHeightChanged,
            ) {
                topBarModeOptions.forEach { opt ->
                    PdfChromeMenuRow(
                        icon = null,
                        label = opt.label,
                        checked = opt.selected,
                        chevron = false,
                        indentLabel = true,
                        rowHeight = MenuOptionRowHeight,
                        labelSize = Level2FontSize,
                        labelWeight = Level2FontWeight,
                        onClick = {
                            opt.onClick()
                            onSectionToggle(ReaderMoreState.INLINE_READING_UI)
                        },
                    )
                }
            }
        }
        // Task rows: continuous boundsInRoot for anchored nested targeting.
        // r26 §6 single-instance: while THIS row's child is open, the row keeps
        // its layout placeholder but icon/title alpha → 0 (child header owns
        // the only visible instance).
        plainMenuItems.forEachIndexed { i, item ->
            val rowIndex = 2 + i
            val childStateForRow = when (rowIndex) {
                2 -> ReaderMoreState.CHILD_PAGE_MAPPING
                3 -> ReaderMoreState.CHILD_TOC
                4 -> ReaderMoreState.CHILD_THUMBNAILS
                5 -> ReaderMoreState.CHILD_EXPORT
                else -> null
            }
            val owns = childStateForRow != null && (
                state == childStateForRow ||
                    state.preparingTarget() == childStateForRow ||
                    (state.isChild() && childRowIndex(state) == rowIndex)
                )
            PdfChromeMenuRow(
                icon = item.icon,
                label = item.label,
                chevron = false,
                // Keep 52dp layout slot; hide visual while Child header owns the row.
                contentAlpha = if (owns) 0f else 1f,
                onClick = {
                    item.onClick()
                    if (item.closeOnClick) onCloseRequest()
                },
                modifier = Modifier.onGloballyPositioned { coords ->
                    onRowBoundsRoot(rowIndex, coords.boundsInRoot())
                },
            )
        }
    }
}

/**
 * R25.2: inline expandable region driven by a single authoritative progress
 * p∈[0, 1]. The region's layout height = measuredExpandedHeight × p; children
 * stay composed (clipped by region bounds) while expanded or collapsing.
 * When fully collapsed (progress==0 && !expanded), children are removed from
 * composition — preventing invisible nodes from occupying layout/semantics.
 */
@Composable
private fun InlineExpandableSection(
    expanded: Boolean,
    expandedHeightPx: Float,
    progress: Float,
    onMeasuredHeightChanged: (Float) -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val regionHeight = with(density) { (expandedHeightPx * progress).toDp() }
    // Compose children only when expanded or mid-collapse (progress > 0).
    // This keeps invisible options out of the semantics tree when fully
    // collapsed while preserving composition during the collapse animation.
    val shouldCompose = expanded || progress > 0f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(regionHeight)
            .clipToBounds(),
    ) {
        if (shouldCompose) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .reportNaturalHeight { onMeasuredHeightChanged(it.toFloat()) },
            ) {
                content()
            }
        }
    }
}

/**
 * Reports the content's NATURAL height via an UNBOUNDED intrinsic query, then
 * lays the content out normally with the incoming (clamped) constraints.
 * Breaks the morph feedback loop (the card's animated size never feeds back
 * into the target). Used by the parent's inline sections (fixed, non-lazy
 * content); the Foreground Advanced Card measures its body via
 * onGloballyPositioned against a FIXED-size reference column instead.
 */
internal fun Modifier.reportNaturalHeight(onNaturalHeight: (Int) -> Unit): Modifier =
    layout { measurable, constraints ->
        val naturalHeight = measurable.minIntrinsicHeight(constraints.maxWidth)
        onNaturalHeight(naturalHeight)
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

/**
 * One compact row: 52dp level-1 / 48dp option rows, 22dp icon + 12dp gap,
 * level-1 label 14sp Normal / level-2 13sp Normal, optional chevron
 * (rotates `>` → `⌄` while expanded) / small check. Row-local restrained
 * highlight only — no Material ripple, no selected background.
 *
 * @param chevronRotation explicit rotation angle (degrees) for the chevron,
 *   driven by the caller's progress p (R25.2). When null, falls back to the
 *   boolean [chevronExpanded] with its own animated transition.
 */
@Composable
internal fun PdfChromeMenuRow(
    icon: ImageVector?,
    label: String,
    checked: Boolean = false,
    chevron: Boolean = false,
    chevronExpanded: Boolean = false,
    chevronRotation: Float? = null,
    indentLabel: Boolean = false,
    rowHeight: Dp = MenuRowHeight,
    labelSize: TextUnit = Level1FontSize,
    labelWeight: FontWeight = Level1FontWeight,
    /** 0f hides icon/title/chevron but keeps the row as a layout placeholder
     *  (r26 §6 single-instance rule: the ChildCard header takes over). */
    contentAlpha: Float = 1f,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val animatedRotation by animateFloatAsState(
        targetValue = if (chevronExpanded) 90f else 0f,
        animationSpec = tween(InlineProgressMs, easing = MoreMorphEasing),
        label = "menuChevron",
    )
    val rotation = chevronRotation ?: animatedRotation
    val rowInteraction = remember { MutableInteractionSource() }
    val rowPressed by rowInteraction.collectIsPressedAsState()
    val pressedOverlay = glassPressedContentOverlayAlpha(GlassMaterialRole.Menu)
    val rowHighlight by animateFloatAsState(
        targetValue = if (rowPressed) pressedOverlay else 0f,
        animationSpec = GlassMaterialEngine.PressSpring,
        label = "menuRowPress",
    )
    val muted = glassMutedContentAlpha(GlassMaterialRole.Menu)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(rowHeight)
            .background(
                scheme.onSurface.copy(alpha = rowHighlight),
                RoundedCornerShape(10.dp),
            )
            .clickable(
                interactionSource = rowInteraction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = MenuRowHPad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .width(MenuIconColWidth)
                    .graphicsLayer { alpha = contentAlpha },
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(MenuIconSize),
                    tint = scheme.onSurface.copy(alpha = muted),
                )
            }
            Spacer(modifier = Modifier.width(MenuIconGap))
        } else if (indentLabel) {
            Spacer(modifier = Modifier.width(OptionIndent))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = labelSize,
                fontWeight = labelWeight,
            ),
            // Level-2 uses muted secondary; level-1 stays full onSurface Medium/SemiBold.
            color = if (indentLabel) scheme.onSurface.copy(alpha = muted + 0.12f) else scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .graphicsLayer { alpha = contentAlpha },
        )
        if (checked) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = scheme.primary,
            )
        }
        if (chevron) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .rotate(rotation)
                    .graphicsLayer { alpha = contentAlpha },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = scheme.onSurface.copy(alpha = muted),
                )
            }
        }
    }
}
