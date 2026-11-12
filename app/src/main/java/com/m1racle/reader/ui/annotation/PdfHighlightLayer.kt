package com.m1racle.reader.ui.annotation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.m1racle.reader.design.RadixColors
import com.m1racle.reader.pdf.PdfSelectablePage
import com.m1racle.reader.pdf.PdfTextSelectionRange
import kotlin.math.roundToInt

/**
 * Page text selection and highlight interaction with a document-owned selection.
 *
 * Host document gestures may consume events in the Initial pass. Those consumed flags do not
 * cancel this layer: movement before the long-press timeout is still treated as ordinary reading,
 * while acquiring a text selection switches ownership through [onSelectionGestureActiveChanged].
 *
 * Releasing a long press keeps the selection and exposes copy/highlight/note actions.
 * [onSelectCompleted] is called only when the user explicitly chooses highlight.
 */
@Composable
fun PdfHighlightLayer(
    pageIndex: Int,
    items: List<AnnotationOverlayItem>,
    selectionEnabled: Boolean,
    textPage: PdfSelectablePage? = null,
    /** True while this page's text extraction is still running. */
    textLoading: Boolean = false,
    onSelectionUnavailable: () -> Unit = {},
    onSelectCompleted: (TextSelection.PdfSelection) -> Unit,
    modifier: Modifier = Modifier,
    onDeleteRequested: ((itemId: Long, page: Int) -> Unit)? = null,
    onNoteRequested: ((item: AnnotationOverlayItem, page: Int) -> Unit)? = null,
    /**
     * True after a long press or handle press takes ownership of the current pointer stream;
     * false on release, cancellation, second finger, or disposal. Reading hosts should stop their
     * own pan/page detector for that interval.
     */
    onSelectionGestureActiveChanged: (Boolean) -> Unit = {},
    /** Optional direct path into the existing note editor; null hides the note action. */
    onSelectionNoteRequested: ((TextSelection.PdfSelection) -> Unit)? = null,
    selectionSession: PdfTextSelectionSession = remember { PdfTextSelectionSession() },
    /** GPU scale the viewport applies to the whole page layer this overlay sits in. */
    pageScale: Float = 1f,
) {
    val currentComplete by rememberUpdatedState(onSelectCompleted)
    val currentTextPage by rememberUpdatedState(textPage)
    val currentTextLoading by rememberUpdatedState(textLoading)
    val currentUnavailable by rememberUpdatedState(onSelectionUnavailable)
    val currentDelete by rememberUpdatedState(onDeleteRequested)
    val currentNote by rememberUpdatedState(onNoteRequested)
    val currentGestureActive by rememberUpdatedState(onSelectionGestureActiveChanged)
    val currentSelectionNote by rememberUpdatedState(onSelectionNoteRequested)
    val clipboard = LocalClipboardManager.current

    val selectionRange by remember(pageIndex, selectionSession) { selectionSession.pageState(pageIndex) }
    var selectedId by remember(pageIndex) { mutableStateOf<Long?>(null) }
    var gestureOwned by remember(pageIndex) { mutableStateOf(false) }
    val actionsVisible by remember(pageIndex, selectionSession) {
        derivedStateOf { selectionRange != null && !gestureOwned }
    }
    var layerSize by remember(pageIndex) { mutableStateOf(IntSize.Zero) }
    var actionBarSize by remember(pageIndex) { mutableStateOf(IntSize.Zero) }
    // A long press that lands before this page's text extraction finishes is
    // remembered here and completed once the text arrives, so the user never has
    // to long-press twice.
    var pendingPress by remember(pageIndex) { mutableStateOf<Offset?>(null) }

    LaunchedEffect(textPage, pendingPress) {
        val press = pendingPress ?: return@LaunchedEffect
        val page = textPage ?: return@LaunchedEffect
        pendingPress = null
        if (layerSize.width <= 0 || layerSize.height <= 0) return@LaunchedEffect
        if (page.lines.isEmpty()) {
            currentUnavailable()
            return@LaunchedEffect
        }
        val hit = page.hit(
            press.x / layerSize.width,
            press.y / layerSize.height,
            tolerance = page.height * 0.006f,
        ) ?: return@LaunchedEffect
        selectedId = null
        selectionSession.setSelection(pageIndex, PdfTextSelectionRange(hit, hit))
    }

    BackHandler(enabled = selectionRange != null) {
        selectionSession.clear()
    }

    DisposableEffect(pageIndex, selectionSession) {
        onDispose {
            if (gestureOwned) currentGestureActive(false)
            selectionSession.clearPage(pageIndex)
        }
    }

    LaunchedEffect(items) {
        if (selectedId != null && items.none { it.id == selectedId }) selectedId = null
    }

    val density = LocalDensity.current
    val badgeRadiusPx = with(density) { 18.dp.toPx() }
    val handleRadiusPx = with(density) { 9.dp.toPx() }
    val handleHitRadiusPx = with(density) { 24.dp.toPx() }
    val actionGapPx = with(density) { 8.dp.toPx() }
    val actionMarginPx = with(density) { 8.dp.toPx() }
    // The action bar is a screen-fixed control. Its dp sizes are converted into
    // page-local equivalents so the viewport's GPU scale cannot enlarge it.
    val actionMetrics = PdfTextSelectionGeometry.fixedScreenActionBarMetrics(
        barWidthPx = actionBarSize.width.toFloat(),
        barHeightPx = actionBarSize.height.toFloat(),
        gapPx = actionGapPx,
        marginPx = actionMarginPx,
        pageScale = pageScale,
    )
    // Pointer-input lambdas capture values; keep the gesture stream alive on
    // scale changes while still reading the latest metrics.
    val currentActionMetrics by rememberUpdatedState(actionMetrics)

    fun rectOf(item: AnnotationOverlayItem) = item.pdfRectsNormalized().firstOrNull()

    fun uiSelection(range: PdfTextSelectionRange, page: PdfSelectablePage): TextSelection.PdfSelection? {
        val valid = page.validRange(range.anchor, range.focus) ?: return null
        val selected = page.selection(valid)
        return TextSelection.PdfSelection(
            page = selected.anchor.page,
            rects = selected.anchor.rects,
            excerpt = selected.excerpt,
        ).takeIf { it.isValid() }
    }

    val currentTextSelection = selectionRange?.let { range ->
        textPage?.let { page -> uiSelection(range, page) }
    }
    val actionPlacement = if (actionsVisible && currentTextSelection != null &&
        textPage != null && layerSize != IntSize.Zero && actionBarSize != IntSize.Zero
    ) {
        PdfTextSelectionGeometry.actionBarPlacement(
            selectionRects = currentTextSelection.rects,
            widthPx = layerSize.width.toFloat(),
            heightPx = layerSize.height.toFloat(),
            barWidthPx = actionMetrics.barWidthPx,
            barHeightPx = actionMetrics.barHeightPx,
            gapPx = actionMetrics.gapPx,
            marginPx = actionMetrics.marginPx,
        )
    } else {
        null
    }

    val gestureModifier = Modifier.pointerInput(
        pageIndex,
        items,
        selectionEnabled,
        density,
        selectionSession,
    ) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val page = currentTextPage
            val widthPx = size.width.toFloat()
            val heightPx = size.height.toFloat()

            fun setOwnership(owned: Boolean) {
                if (gestureOwned == owned) return
                gestureOwned = owned
                currentGestureActive(owned)
            }

            fun keepSelection(range: PdfTextSelectionRange, owner: PdfSelectablePage) {
                if (uiSelection(range, owner) == null) return
                selectionSession.setSelection(pageIndex, range)
                selectedId = null
            }

            // Action buttons are children above this gesture layer and must receive their own taps.
            val actionRect = if (actionsVisible && actionBarSize != IntSize.Zero && page != null) {
                val range = selectionRange
                val selected = range?.let { uiSelection(it, page) }
                selected?.let { selection ->
                    PdfTextSelectionGeometry.actionBarPlacement(
                        selectionRects = selection.rects,
                        widthPx = widthPx,
                        heightPx = heightPx,
                        barWidthPx = currentActionMetrics.barWidthPx,
                        barHeightPx = currentActionMetrics.barHeightPx,
                        gapPx = currentActionMetrics.gapPx,
                        marginPx = currentActionMetrics.marginPx,
                    )
                }
            } else {
                null
            }
            if (actionRect != null &&
                down.position.x >= actionRect.x &&
                down.position.x <= actionRect.x + currentActionMetrics.barWidthPx &&
                down.position.y >= actionRect.y &&
                down.position.y <= actionRect.y + currentActionMetrics.barHeightPx
            ) {
                return@awaitEachGesture
            }

            // A visible handle owns the pointer immediately, even before the long-press timeout.
            val handleDown = if (selectionEnabled && page != null) {
                selectionRange?.let { range ->
                    PdfTextSelectionGeometry.hitHandle(
                        page = page,
                        range = range,
                        x = down.position.x,
                        y = down.position.y,
                        widthPx = widthPx,
                        heightPx = heightPx,
                        visualRadiusPx = handleRadiusPx,
                        hitRadiusPx = handleHitRadiusPx,
                    )
                }
            } else {
                null
            }
            if (handleDown != null && page != null && selectionRange != null) {
                val previous = selectionRange
                var active = selectionRange!!
                var cancelled = false
                setOwnership(true)
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || event.changes.count { it.pressed } > 1) {
                            cancelled = true
                            break
                        }
                        val hit = page.hit(
                            change.position.x / widthPx,
                            change.position.y / heightPx,
                            tolerance = -1f,
                        )
                        if (hit != null) {
                            val next = when (handleDown) {
                                PdfSelectionHandle.ANCHOR -> active.copy(anchor = hit)
                                PdfSelectionHandle.FOCUS -> active.copy(focus = hit)
                            }
                            active = next
                            selectionSession.setSelection(pageIndex, next)
                        }
                        if (!change.pressed) {
                            keepSelection(active, page)
                            change.consume()
                            break
                        }
                        change.consume()
                    }
                } finally {
                    if (cancelled) {
                        selectionSession.restoreAfterCancellation(pageIndex, active, previous)
                    }
                    setOwnership(false)
                }
                return@awaitEachGesture
            }

            var cancelled = false
            val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                var released: androidx.compose.ui.input.pointer.PointerInputChange? = null
                while (released == null && !cancelled) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || event.changes.count { it.pressed } > 1 ||
                        (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                    ) {
                        cancelled = true
                    } else if (!change.pressed) {
                        released = change
                    }
                }
                released
            }
            if (cancelled) return@awaitEachGesture

            if (up != null) {
                val pos = up.position
                val target = items.firstOrNull { it.id == selectedId }
                val rect = target?.let { rectOf(it) }
                if (target != null && rect != null && currentNote != null &&
                    PdfHighlightGeometry.noteBadgeHit(
                        rect, pos.x, pos.y, widthPx, heightPx, badgeRadiusPx,
                    )
                ) {
                    up.consume()
                    selectedId = null
                    selectionSession.clear()
                    currentNote?.invoke(target, pageIndex)
                } else if (target != null && rect != null && currentDelete != null &&
                    PdfHighlightGeometry.deleteBadgeHit(
                        rect, pos.x, pos.y, widthPx, heightPx, badgeRadiusPx,
                    )
                ) {
                    up.consume()
                    selectedId = null
                    selectionSession.clear()
                    currentDelete?.invoke(target.id, pageIndex)
                } else {
                    val hit = PdfHighlightGeometry.hitTest(
                        items.flatMap { item -> item.pdfRectsNormalized().map { item.id to it } },
                        pos.x, pos.y, widthPx, heightPx,
                    )
                    selectedId = hit
                    if (hit != null) {
                        selectionSession.clear()
                        up.consume()
                    } else if (selectionSession.hasSelection() || actionsVisible) {
                        selectionSession.clear()
                        up.consume()
                    }
                }
                return@awaitEachGesture
            }

            if (!selectionEnabled) return@awaitEachGesture
            if (page == null && currentTextLoading) {
                // Text extraction is still running: remember the press and finish
                // the selection when the text arrives instead of dropping it.
                pendingPress = down.position
                return@awaitEachGesture
            }
            if (page == null || page.lines.isEmpty()) {
                currentUnavailable()
                return@awaitEachGesture
            }
            if (widthPx <= 0f || heightPx <= 0f) return@awaitEachGesture
            val start = page.hit(
                down.position.x / widthPx,
                down.position.y / heightPx,
                tolerance = page.height * 0.006f,
            ) ?: return@awaitEachGesture

            val previous = selectionRange
            var active = PdfTextSelectionRange(start, start)
            var kept = false
            var gestureCancelled = false
            selectionSession.setSelection(pageIndex, active)
            selectedId = null
            setOwnership(true)
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || event.changes.count { it.pressed } > 1) {
                        gestureCancelled = true
                        break
                    }
                    val hit = page.hit(
                        change.position.x / widthPx,
                        change.position.y / heightPx,
                        tolerance = -1f,
                    )
                    if (hit != null) {
                        active = active.copy(focus = hit)
                        selectionSession.setSelection(pageIndex, active)
                    }
                    if (!change.pressed) {
                        change.consume()
                        keepSelection(active, page)
                        kept = true
                        break
                    }
                    change.consume()
                }
            } finally {
                if (!kept || gestureCancelled) {
                    selectionSession.restoreAfterCancellation(pageIndex, active, previous)
                }
                setOwnership(false)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { layerSize = it },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(gestureModifier)
                .drawWithContent {
                    drawContent()
                    val w = size.width
                    val h = size.height
                    items.forEach { item ->
                        item.pdfRectsNormalized().forEachIndexed { rectIndex, rect ->
                            val isSelected = item.id == selectedId
                            val arm = badgeRadiusPx * 0.45f
                            val style = item.style
                            val color = readerHighlightColor(style)
                            if (style == com.m1racle.reader.data.anchor.HighlightStyle.Underline ||
                                style == com.m1racle.reader.data.anchor.HighlightStyle.Strike
                            ) {
                                val y = (rect.y + rect.height *
                                    if (style == com.m1racle.reader.data.anchor.HighlightStyle.Underline) .95f else .5f
                                ) * h
                                drawLine(
                                    color,
                                    Offset(rect.x * w, y),
                                    Offset((rect.x + rect.width) * w, y),
                                    strokeWidth = (h * .0015f).coerceAtLeast(1f),
                                )
                            } else {
                                drawRoundRect(
                                    color = if (isSelected) color.copy(alpha = .55f) else color,
                                    topLeft = Offset(rect.x * w, rect.y * h),
                                    size = Size(rect.width * w, rect.height * h),
                                    cornerRadius = CornerRadius(1f, 1f),
                                    blendMode = BlendMode.SrcOver,
                                )
                            }
                            if (isSelected && rectIndex == 0 && currentNote != null) {
                                val (cx, cy) = PdfHighlightGeometry.badgeCenter(
                                    rect, w, h, badgeRadiusPx, false,
                                )
                                drawCircle(RadixColors.White, badgeRadiusPx, Offset(cx, cy))
                                drawCircle(RadixColors.Blue9, badgeRadiusPx, Offset(cx, cy))
                                drawLine(
                                    RadixColors.White,
                                    Offset(cx - arm, cy + arm),
                                    Offset(cx + arm * 0.4f, cy - arm),
                                    strokeWidth = 4f,
                                )
                                drawCircle(
                                    RadixColors.White,
                                    radius = 2.5f,
                                    center = Offset(cx - arm * 1.1f, cy + arm * 1.1f),
                                )
                            }
                            if (isSelected && rectIndex == 0 && currentDelete != null) {
                                val (cx, cy) = PdfHighlightGeometry.badgeCenter(
                                    rect, w, h, badgeRadiusPx, true,
                                )
                                drawCircle(RadixColors.White, badgeRadiusPx, Offset(cx, cy))
                                drawCircle(RadixColors.Red9, badgeRadiusPx, Offset(cx, cy))
                                drawLine(
                                    RadixColors.White,
                                    Offset(cx - arm, cy - arm),
                                    Offset(cx + arm, cy + arm),
                                    4f,
                                )
                                drawLine(
                                    RadixColors.White,
                                    Offset(cx - arm, cy + arm),
                                    Offset(cx + arm, cy - arm),
                                    4f,
                                )
                            }
                        }
                    }

                    val activeRange = selectionRange
                    val activePage = textPage
                    if (activeRange != null && activePage != null) {
                        activePage.selection(activeRange).anchor.rects.forEach { rect ->
                            drawRect(
                                color = SelectionPreview,
                                topLeft = Offset(rect.x * w, rect.y * h),
                                size = Size(rect.width * w, rect.height * h),
                                blendMode = BlendMode.Multiply,
                            )
                        }
                        PdfTextSelectionGeometry.handles(
                            page = activePage,
                            range = activeRange,
                            widthPx = w,
                            heightPx = h,
                            visualRadiusPx = handleRadiusPx,
                        ).forEach { handle ->
                            val center = Offset(handle.x, handle.y)
                            drawCircle(RadixColors.White, handleRadiusPx, center)
                            drawCircle(
                                RadixColors.Blue9,
                                radius = handleRadiusPx * 0.64f,
                                center = center,
                            )
                        }
                    }
                },
        )

        if (actionsVisible && currentTextSelection != null) {
            val placement = actionPlacement
            SelectionActionBar(
                modifier = Modifier
                    .onSizeChanged { actionBarSize = it }
                    .offset {
                        if (placement != null) {
                            IntOffset(placement.x.roundToInt(), placement.y.roundToInt())
                        } else {
                            // Keep the first measuring pass out of the page's hit area.
                            IntOffset(-10_000, -10_000)
                        }
                    }
                    // Undo the viewport's page scale for this control only. The
                    // offset above stays in page-local coordinates so the bar keeps
                    // following the selection, while its content stays screen-sized.
                    .graphicsLayer {
                        scaleX = actionMetrics.inverseScale
                        scaleY = actionMetrics.inverseScale
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                    .alpha(if (placement != null) 1f else 0f),
                hasNoteAction = currentSelectionNote != null,
                onCopy = {
                    clipboard.setText(AnnotatedString(currentTextSelection.excerpt.orEmpty()))
                    selectionSession.clear()
                },
                onHighlight = {
                    selectionSession.clear()
                    currentComplete(currentTextSelection)
                },
                onNote = {
                    selectionSession.clear()
                    currentSelectionNote?.invoke(currentTextSelection)
                },
            )
        }

        val activeRange = selectionRange
        val activePage = textPage
        if (activeRange != null && activePage != null) {
            PdfTextSelectionGeometry.handles(
                page = activePage,
                range = activeRange,
                widthPx = layerSize.width.toFloat(),
                heightPx = layerSize.height.toFloat(),
                visualRadiusPx = handleRadiusPx,
            ).forEach { handle ->
                SelectionHandleSemantics(handle, handleHitRadiusPx)
            }
        }
    }
}

@Composable
private fun SelectionActionBar(
    modifier: Modifier,
    hasNoteAction: Boolean,
    onCopy: () -> Unit,
    onHighlight: () -> Unit,
    onNote: () -> Unit,
) {
    Surface(
        modifier = modifier
            .semantics { contentDescription = "文字选区操作" }
            .testTag("pdf-selection-actions"),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
    ) {
        Row {
            SelectionActionButton(
                label = "复制",
                onAction = onCopy,
                testTag = "pdf-selection-action-copy",
            )
            SelectionActionButton(
                label = "高亮",
                onAction = onHighlight,
                testTag = "pdf-selection-action-highlight",
            )
            if (hasNoteAction) {
                SelectionActionButton(
                    label = "批注",
                    onAction = onNote,
                    testTag = "pdf-selection-action-note",
                )
            }
        }
    }
}

@Composable
private fun SelectionActionButton(
    label: String,
    onAction: () -> Unit,
    testTag: String,
) {
    val currentClick by rememberUpdatedState(onAction)
    Box(
        modifier = Modifier
            .height(40.dp)
            .semantics {
                contentDescription = label
                role = Role.Button
                onClick(label = label) {
                    currentClick()
                    true
                }
            }
            .testTag(testTag)
            .pointerInput(label) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var cancelled = false
                    while (!cancelled) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || event.changes.count { it.pressed } > 1 ||
                            (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                        ) {
                            cancelled = true
                        } else if (!change.pressed) {
                            change.consume()
                            currentClick()
                            break
                        } else {
                            change.consume()
                        }
                    }
                }
            }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label)
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.SelectionHandleSemantics(
    handle: PdfSelectionHandlePoint,
    hitRadiusPx: Float,
) {
    val density = LocalDensity.current
    val diameter = with(density) { (hitRadiusPx * 2f).toDp() }
    val half = hitRadiusPx
    val label = when (handle.handle) {
        PdfSelectionHandle.ANCHOR -> "调整选区起点"
        PdfSelectionHandle.FOCUS -> "调整选区终点"
    }
    val tag = when (handle.handle) {
        PdfSelectionHandle.ANCHOR -> "pdf-selection-handle-anchor"
        PdfSelectionHandle.FOCUS -> "pdf-selection-handle-focus"
    }
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = (handle.x - half).roundToInt(),
                    y = (handle.y - half).roundToInt(),
                )
            }
            .size(diameter)
            .semantics { contentDescription = label }
            .testTag(tag),
    )
}

private val SelectionPreview = RadixColors.Blue9.copy(alpha = 0.20f)
