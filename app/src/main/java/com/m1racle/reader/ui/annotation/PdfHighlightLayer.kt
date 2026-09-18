package com.m1racle.reader.ui.annotation

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.runtime.rememberUpdatedState
import com.m1racle.reader.pdf.PdfSelectablePage

/** Page-local text selection and per-line highlight overlay. Only a normal pointer release commits. */
@Composable
fun PdfHighlightLayer(
    pageIndex: Int,
    items: List<AnnotationOverlayItem>,
    selectionEnabled: Boolean,
    textPage: PdfSelectablePage? = null,
    onSelectionUnavailable: () -> Unit = {},
    onSelectCompleted: (TextSelection.PdfSelection) -> Unit,
    modifier: Modifier = Modifier,
    /** R26.2 选中态删除入口；null = 不显示 ✕。参数 (itemId, pageIndex)。 */
    onDeleteRequested: ((itemId: Long, page: Int) -> Unit)? = null,
    /** R27 选中态批注入口；null = 不显示 ✎。参数 (itemId, pageIndex)。 */
    onNoteRequested: ((item: AnnotationOverlayItem, page: Int) -> Unit)? = null,
) {
    var preview by remember(pageIndex) { mutableStateOf<TextSelection.PdfSelection?>(null) }
    val complete by rememberUpdatedState(onSelectCompleted)
    var selectedId by remember(pageIndex) { mutableStateOf<Long?>(null) }

    // 项集变化时清理失效选中。
    LaunchedEffect(items) {
        if (selectedId != null && items.none { it.id == selectedId }) selectedId = null
    }

    val badgeRadiusPx = with(LocalDensity.current) { 18.dp.toPx() }

    fun rectOf(item: AnnotationOverlayItem) = item.pdfRectsNormalized().firstOrNull()

    val gestureModifier = Modifier.pointerInput(pageIndex, items, selectionEnabled, textPage, onDeleteRequested, onNoteRequested) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var cancelled = false
            val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                var released: androidx.compose.ui.input.pointer.PointerInputChange? = null
                while (released == null && !cancelled) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || change.isConsumed || event.changes.count { it.pressed } > 1 ||
                        (change.position-down.position).getDistance() > viewConfiguration.touchSlop) cancelled = true
                    else if (!change.pressed) released = change
                }
                released
            }
            if (cancelled) return@awaitEachGesture
            if (up != null) {
                val pos = up.position
                val target = items.firstOrNull { it.id == selectedId }
                val r = target?.let { rectOf(it) }
                if (target != null && r != null && onNoteRequested != null && PdfHighlightGeometry.noteBadgeHit(
                    r,pos.x,pos.y,size.width.toFloat(),size.height.toFloat(),badgeRadiusPx)) {
                    up.consume(); selectedId = null; onNoteRequested(target,pageIndex)
                } else if (target != null && r != null && onDeleteRequested != null && PdfHighlightGeometry.deleteBadgeHit(
                    r,pos.x,pos.y,size.width.toFloat(),size.height.toFloat(),badgeRadiusPx)) {
                    up.consume(); selectedId = null; onDeleteRequested(target.id,pageIndex)
                } else {
                    val hit = PdfHighlightGeometry.hitTest(
                        items.flatMap { item -> item.pdfRectsNormalized().map { item.id to it } },
                        pos.x,pos.y,size.width.toFloat(),size.height.toFloat(),
                    )
                    selectedId = hit
                    if (hit != null) up.consume()
                }
                return@awaitEachGesture
            }
            if (!selectionEnabled) return@awaitEachGesture
            if (textPage == null || textPage.lines.isEmpty()) {
                onSelectionUnavailable(); return@awaitEachGesture
            }
            if (size.width <= 0 || size.height <= 0) return@awaitEachGesture
            val start = textPage.hit(down.position.x/size.width, down.position.y/size.height,
                tolerance = textPage.height * 0.006f) ?: return@awaitEachGesture
            selectedId = null
            preview = textPage.selection(start,start).let { TextSelection.PdfSelection(it.anchor.page,it.anchor.rects,it.excerpt) }
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.count { it.pressed } > 1) break
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.isConsumed) break
                    val end = textPage.hit(change.position.x/size.width,change.position.y/size.height,tolerance = -1f)
                    if (end != null) preview = textPage.selection(start,end).let { TextSelection.PdfSelection(it.anchor.page,it.anchor.rects,it.excerpt) }
                    if (!change.pressed) {
                        change.consume()
                        preview?.takeIf { it.isValid() }?.let { complete(it) }
                        break
                    }
                    change.consume()
                }
            } finally { preview = null }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(gestureModifier)
            .drawWithContent {
                drawContent()
                val w = size.width
                val h = size.height
                items.forEach { item ->
                    item.pdfRectsNormalized().forEachIndexed { rectIndex, r ->
                        val isSelected = item.id == selectedId
                        val arm = badgeRadiusPx * 0.45f
                        drawRoundRect(
                            color = if (isSelected) HighlightFillSelected else HighlightFill,
                            topLeft = Offset(r.x * w, r.y * h),
                            size = Size(r.width * w, r.height * h),
                            cornerRadius = CornerRadius(1f, 1f),
                            blendMode = BlendMode.Multiply,
                        )
                        if (isSelected && rectIndex == 0 && onNoteRequested != null) {
                            // 左上角 ✎ 批注角标。
                            val (cx,cy) = PdfHighlightGeometry.badgeCenter(r,w,h,badgeRadiusPx,false)
                            drawCircle(color = Color.White, radius = badgeRadiusPx, center = Offset(cx, cy))
                            drawCircle(color = Color(0xFF1976D2), radius = badgeRadiusPx, center = Offset(cx, cy))
                            // 简化笔形：斜杆 + 底点。
                            drawLine(
                                Color.White,
                                Offset(cx - arm, cy + arm),
                                Offset(cx + arm * 0.4f, cy - arm),
                                strokeWidth = 4f,
                            )
                            drawCircle(Color.White, radius = 2.5f, center = Offset(cx - arm * 1.1f, cy + arm * 1.1f))
                        }
                        if (isSelected && rectIndex == 0 && onDeleteRequested != null) {
                            // 右上角 ✕ 角标（与 deleteBadgeHit 同心同半径）。
                            val (cx,cy) = PdfHighlightGeometry.badgeCenter(r,w,h,badgeRadiusPx,true)
                            drawCircle(color = Color.White, radius = badgeRadiusPx, center = Offset(cx, cy))
                            drawCircle(color = Color(0xFFE53935), radius = badgeRadiusPx, center = Offset(cx, cy))
                            val arm = badgeRadiusPx * 0.45f
                            drawLine(Color.White, Offset(cx - arm, cy - arm), Offset(cx + arm, cy + arm), 4f)
                            drawLine(Color.White, Offset(cx - arm, cy + arm), Offset(cx + arm, cy - arm), 4f)
                        }
                    }
                }
                preview?.rects?.forEach { r ->
                    drawRect(
                        color = SelectionPreview,
                        topLeft = Offset(r.x * w, r.y * h),
                        size = Size(r.width * w, r.height * h),
                        blendMode = BlendMode.Multiply,
                    )
                }
            },
    )
}

private val HighlightFill = Color(0x55FFD54F)
private val HighlightFillSelected = Color(0x77FFB300)
private val SelectionPreview = Color(0x334FC3F7)
