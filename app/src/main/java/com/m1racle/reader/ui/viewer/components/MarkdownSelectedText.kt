package com.m1racle.reader.ui.viewer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.m1racle.reader.design.RadixColors
import com.m1racle.reader.markdown.InlineMarkdownParser
import com.m1racle.reader.ui.annotation.readerHighlightColor
import com.m1racle.reader.ui.components.readerHairlineBorder
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

private enum class MarkdownSelectionHandle {
    Start,
    End,
}

private data class MarkdownHandleGeometry(
    val start: Offset,
    val end: Offset,
)

/**
 * Text that keeps a character selection alive after long-press release.
 *
 * Persisted and transient ranges are painted here. Persistent character ranges use the
 * block-canvas offsets already produced by search/annotation extraction, so list items,
 * table cells and code blocks retain their document locations.
 */
@Composable
internal fun MarkdownSelectableText(
    searched: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    annotationOffset: Int = LocalMarkdownInlineOffset.current,
    softWrap: Boolean = true,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    onLinkClick: ((String) -> Unit)? = null,
) {
    val selectionContext = LocalMarkdownSelection.current
    val fallbackSession = remember { MarkdownSelectionSession() }
    val session = LocalMarkdownSelectionSession.current ?: fallbackSession
    val owner = MarkdownSelectionOwner(
        block = selectionContext?.block ?: -1,
        inlineOffset = annotationOffset,
    )
    val endpoints = session.endpoints?.takeIf { it.owner == owner }
    val activeRange = endpoints?.let {
        markdownSelectionRange(
            text = searched.text,
            anchorOffset = it.anchor,
            dragOffset = it.focus,
        )
    }
    val annotated = remember(
        searched,
        selectionContext?.highlights,
        selectionContext?.plainText,
        annotationOffset,
        activeRange,
    ) {
        buildMarkdownDisplayText(
            searched = searched,
            selectionContext = selectionContext,
            annotationOffset = annotationOffset,
            activeRange = activeRange,
        )
    }

    var textLayout by remember(searched.text) {
        mutableStateOf<TextLayoutResult?>(null)
    }
    var boundsInWindow by remember { mutableStateOf(Rect.Zero) }
    val latestLayout by rememberUpdatedState(textLayout)
    val latestContext by rememberUpdatedState(selectionContext)
    val latestAnnotated by rememberUpdatedState(annotated)
    val latestOnLinkClick by rememberUpdatedState(onLinkClick)
    val searchForLinks = onLinkClick != null &&
        annotated.getStringAnnotations(
            tag = InlineMarkdownParser.LINK_ANNOTATION_TAG,
            start = 0,
            end = annotated.length,
        ).isNotEmpty()

    val textPointerModifier = if (searchForLinks || selectionContext != null) {
        Modifier.pointerInput(
            searched.text,
            selectionContext?.block,
            selectionContext?.plainText,
            annotationOffset,
            owner,
            session,
        ) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val reachedLongPress = withTimeoutOrNull(
                    viewConfiguration.longPressTimeoutMillis,
                ) {
                    var lastPosition = down.position
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull
                        if (event.changes.count { it.pressed } > 1) return@withTimeoutOrNull
                        lastPosition = change.position
                        if (!change.pressed) {
                            if (session.isActive) {
                                session.clear()
                                change.consume()
                                return@withTimeoutOrNull
                            }
                            val layout = latestLayout
                            val link = layout?.let {
                                latestAnnotated.getStringAnnotations(
                                    tag = InlineMarkdownParser.LINK_ANNOTATION_TAG,
                                    start = markdownCharacterOffsetAt(
                                        layout = it,
                                        position = lastPosition,
                                        text = latestAnnotated.text,
                                    ),
                                    end = markdownCharacterOffsetAt(
                                        layout = it,
                                        position = lastPosition,
                                        text = latestAnnotated.text,
                                    ),
                                ).firstOrNull()
                            }
                            if (link != null) {
                                change.consume()
                                session.clear()
                                latestOnLinkClick?.invoke(link.item)
                            }
                            return@withTimeoutOrNull
                        }
                        if (
                            change.isConsumed ||
                            (lastPosition - down.position).getDistance() >
                            viewConfiguration.touchSlop
                        ) {
                            return@withTimeoutOrNull
                        }
                    }
                } == null
                if (!reachedLongPress) return@awaitEachGesture
                val context = latestContext ?: return@awaitEachGesture
                if (context.plainText.isEmpty() || searched.isEmpty()) {
                    return@awaitEachGesture
                }
                val layout = latestLayout ?: return@awaitEachGesture

                // The long press offset itself is a complete one-character selection.
                // It is committed before waiting for lift, so a long-press-and-release
                // gesture does not disappear as a canceled drag.
                down.consume()
                val previous = session.endpoints
                session.begin(
                    owner = owner,
                    offset = markdownCharacterOffsetAt(
                        layout = layout,
                        position = down.position,
                        text = searched.text,
                    ),
                )

                var released = false
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (event.changes.count { it.pressed } > 1) break
                        if (!change.pressed) {
                            change.consume()
                            released = true
                            break
                        }
                        session.moveFocus(
                            owner = owner,
                            offset = markdownCharacterOffsetAt(
                                layout = layout,
                                position = change.position,
                                text = searched.text,
                            ),
                        )
                        change.consume()
                    }
                } finally {
                    if (!released && session.endpoints?.owner == owner) session.restore(previous)
                }
            }
        }
    } else {
        Modifier
    }

    val actionSelection = activeRange?.let { selectionContext?.selectionFor(it, annotationOffset) }

    Box(
        modifier = modifier.onGloballyPositioned { coordinates ->
            boundsInWindow = coordinates.boundsInWindow()
        },
    ) {
        val layoutCallback: (TextLayoutResult) -> Unit = { result ->
            textLayout = result
            onTextLayout?.invoke(result)
        }
        Text(
            text = annotated,
            style = style,
            modifier = Modifier.fillMaxWidth().then(textPointerModifier),
            softWrap = softWrap,
            overflow = overflow,
            maxLines = maxLines,
            onTextLayout = layoutCallback,
        )

        val layout = textLayout
        if (activeRange != null && layout != null && selectionContext != null) {
            MarkdownSelectionHandles(
                owner = owner,
                activeRange = activeRange,
                layout = layout,
                session = session,
            )
            if (actionSelection != null && boundsInWindow != Rect.Zero) {
                val selectionBounds = markdownSelectionBounds(layout, activeRange)
                    ?.translate(boundsInWindow.left, boundsInWindow.top)
                if (selectionBounds != null) {
                    MarkdownSelectionActionPopup(
                        selectionBounds = selectionBounds,
                        selectionContext = selectionContext,
                        selection = actionSelection,
                        onClear = session::clear,
                    )
                }
            }
        }
    }
}

private fun buildMarkdownDisplayText(
    searched: AnnotatedString,
    selectionContext: MarkdownSelectionContext?,
    annotationOffset: Int,
    activeRange: IntRange?,
): AnnotatedString {
    val highlights = selectionContext?.highlights.orEmpty()
    if (highlights.isEmpty() && activeRange == null) return searched

    val builder = AnnotatedString.Builder(searched)
    highlights.forEach { highlight ->
        val anchor =
            com.m1racle.reader.data.anchor.AnchorCodec.decode(highlight.anchor)
                as? com.m1racle.reader.data.anchor.DocumentAnchor.MarkdownBlock
                ?: return@forEach
        val start = ((anchor.charStart ?: 0) - annotationOffset).coerceIn(0, searched.length)
        val end = ((anchor.charEnd ?: selectionContext?.plainText?.length ?: (searched.length + annotationOffset)) - annotationOffset).coerceIn(0, searched.length)
        if (end <= start) return@forEach

        val kind = com.m1racle.reader.data.anchor.HighlightStyle.fromStorage(highlight.style)
        val span = when (kind) {
            com.m1racle.reader.data.anchor.HighlightStyle.Underline ->
                SpanStyle(
                    textDecoration = TextDecoration.Underline,
                    color = readerHighlightColor(kind),
                )
            com.m1racle.reader.data.anchor.HighlightStyle.Strike ->
                SpanStyle(
                    textDecoration = TextDecoration.LineThrough,
                    color = readerHighlightColor(kind),
                )
            else -> SpanStyle(background = readerHighlightColor(kind))
        }
        builder.addStyle(span, start, end)
    }
    activeRange?.takeUnless { it.isEmpty() }?.let { range ->
        builder.addStyle(
            SpanStyle(background = RadixColors.Blue9.copy(alpha = 0.40f)),
            range.first,
            range.last + 1,
        )
    }
    return builder.toAnnotatedString()
}

@Composable
private fun BoxScope.MarkdownSelectionHandles(
    owner: MarkdownSelectionOwner,
    activeRange: IntRange,
    layout: TextLayoutResult,
    session: MarkdownSelectionSession,
) {
    val density = LocalDensity.current
    val latestLayout by rememberUpdatedState(layout)
    val handleHitTarget = 48.dp
    val handleSize = 18.dp
    val geometry = markdownHandleGeometry(layout, activeRange)
    val latestGeometry by rememberUpdatedState(geometry)
    val halfHitTargetPx = with(density) { handleHitTarget.toPx() / 2f }

    Box(
        modifier = Modifier
            .matchParentSize()
    ) {
        for (handle in MarkdownSelectionHandle.entries) {
            val start = handle == MarkdownSelectionHandle.Start
            val gesture = Modifier.pointerInput(owner, session, handle) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val previous = session.endpoints?.takeIf { it.owner == owner } ?: return@awaitEachGesture
                    val moveAnchor = if (start) previous.anchor <= previous.focus else previous.anchor > previous.focus
                    down.consume()
                    var released = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (event.changes.count { it.pressed } > 1) break
                            val currentCenter = if (start) latestGeometry.start else latestGeometry.end
                            val position = currentCenter - Offset(halfHitTargetPx, halfHitTargetPx) + change.position
                            val currentLayout = latestLayout
                            val offset = markdownCharacterOffsetAt(currentLayout, position, currentLayout.layoutInput.text.text)
                            if (moveAnchor) session.moveAnchor(owner, offset) else session.moveFocus(owner, offset)
                            change.consume()
                            if (!change.pressed) { released = true; break }
                        }
                    } finally {
                        if (!released && session.endpoints?.owner == owner) session.restore(previous)
                    }
                }
            }
            MarkdownSelectionHandleView(
                center = if (start) geometry.start else geometry.end,
                halfHitTargetPx = halfHitTargetPx,
                hitTarget = handleHitTarget,
                handleSize = handleSize,
                tag = if (start) "markdown-selection-handle-start" else "markdown-selection-handle-end",
                description = if (start) "调整选区起点" else "调整选区终点",
                gesture = gesture,
            )
        }
    }
}

@Composable
private fun MarkdownSelectionHandleView(
    center: Offset,
    halfHitTargetPx: Float,
    hitTarget: androidx.compose.ui.unit.Dp,
    handleSize: androidx.compose.ui.unit.Dp,
    tag: String,
    description: String,
    gesture: Modifier,
) {
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = (center.x - halfHitTargetPx).roundToInt(),
                    y = (center.y - halfHitTargetPx).roundToInt(),
                )
            }
            .size(hitTarget)
            .then(gesture)
            .testTag(tag)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(handleSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun MarkdownSelectionActionPopup(
    selectionBounds: Rect,
    selectionContext: MarkdownSelectionContext,
    selection: com.m1racle.reader.ui.annotation.TextSelection.MarkdownSelection,
    onClear: () -> Unit,
) {
    val density = LocalDensity.current
    val clipboard = LocalClipboardManager.current
    val marginPx = with(density) { 8.dp.roundToPx() }
    val gapPx = with(density) { 8.dp.roundToPx() }
    val positionProvider = remember(selectionBounds, marginPx, gapPx) {
        MarkdownSelectionPopupPositionProvider(
            selectionBounds = selectionBounds,
            marginPx = marginPx,
            gapPx = gapPx,
        )
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onClear,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            modifier = Modifier.readerHairlineBorder(
                MaterialTheme.colorScheme.outline,
                RoundedCornerShape(12.dp),
            ),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 6.dp,
        ) {
            Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(selection.excerpt.orEmpty()))
                        onClear()
                    },
                    modifier = Modifier.testTag("markdown-selection-copy"),
                ) {
                    Text("复制")
                }
                if (selectionContext.onHighlight != null) {
                    TextButton(
                        onClick = {
                            onClear()
                            selectionContext.onHighlight(selection)
                        },
                        modifier = Modifier.testTag("markdown-selection-highlight"),
                    ) {
                        Text("高亮")
                    }
                }
                if (selectionContext.onNote != null) {
                    TextButton(
                        onClick = {
                            onClear()
                            selectionContext.onNote(selection)
                        },
                        modifier = Modifier.testTag("markdown-selection-note"),
                    ) {
                        Text("批注")
                    }
                }
            }
        }
    }
}

private class MarkdownSelectionPopupPositionProvider(
    private val selectionBounds: Rect,
    private val marginPx: Int,
    private val gapPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = maxOf(
            marginPx,
            windowSize.width - popupContentSize.width - marginPx,
        )
        val centeredX =
            (selectionBounds.center.x.roundToInt() - popupContentSize.width / 2)
                .coerceIn(marginPx, maxX)
        val aboveY = selectionBounds.top.roundToInt() - gapPx - popupContentSize.height
        val maxY = maxOf(
            marginPx,
            windowSize.height - popupContentSize.height - marginPx,
        )
        val y = if (aboveY >= marginPx) {
            aboveY
        } else {
            (selectionBounds.bottom.roundToInt() + gapPx).coerceIn(marginPx, maxY)
        }
        return IntOffset(centeredX, y)
    }
}

private fun markdownCharacterOffsetAt(
    layout: TextLayoutResult,
    position: Offset,
    text: String,
): Int {
    if (text.isEmpty()) return 0

    fun characterStart(rawOffset: Int): Int {
        val offset = rawOffset.coerceIn(0, text.lastIndex)
        return if (
            offset > 0 &&
            Character.isLowSurrogate(text[offset]) &&
            Character.isHighSurrogate(text[offset - 1])
        ) {
            offset - 1
        } else {
            offset
        }
    }

    val nearestCursor = layout.getOffsetForPosition(position)
    val candidates = listOf(
        characterStart(nearestCursor),
        characterStart(nearestCursor - 1),
    ).distinct()
    return candidates.minBy { index ->
        val bounds = layout.getBoundingBox(index)
        val dx = when {
            position.x < bounds.left -> bounds.left - position.x
            position.x > bounds.right -> position.x - bounds.right
            else -> 0f
        }
        val dy = when {
            position.y < bounds.top -> bounds.top - position.y
            position.y > bounds.bottom -> position.y - bounds.bottom
            else -> 0f
        }
        dx * dx + dy * dy
    }
}

private fun markdownHandleGeometry(
    layout: TextLayoutResult,
    range: IntRange,
): MarkdownHandleGeometry {
    val startBounds = layout.getBoundingBox(range.first)
    val endBounds = layout.getBoundingBox(range.last)
    return MarkdownHandleGeometry(
        start = Offset(startBounds.left, startBounds.top),
        end = Offset(endBounds.right, endBounds.bottom),
    )
}

private fun markdownSelectionBounds(
    layout: TextLayoutResult,
    range: IntRange,
): Rect? {
    if (range.isEmpty()) return null
    val start = layout.getBoundingBox(range.first)
    val end = layout.getBoundingBox(range.last)
    if (start == Rect.Zero && end == Rect.Zero) return null
    return Rect(
        left = minOf(start.left, end.left),
        top = minOf(start.top, end.top),
        right = maxOf(start.right, end.right),
        bottom = maxOf(start.bottom, end.bottom),
    )
}
