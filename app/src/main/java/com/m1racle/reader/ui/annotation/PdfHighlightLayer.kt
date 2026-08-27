package com.m1racle.reader.ui.annotation

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

/**
 * R26.1/R26.2 PDF 高亮层：挂在页面渲染 Box 内（局部坐标系 = 当前渲染页面）。
 *
 * - 长按拖选创建（awaitLongPressOrCancellation 在第二指针加入或越 slop 时取消，
 *   pinch/滚动零干扰）；拖拽实时预览；抬起结算。
 * - R26.2 点击已存高亮 → 选中态（描边加强 + 右上角 ✕ 角标）；✕ 命中回调删除；
 *   点空白处取消选中。非高亮区域的点按不消费——阅读体验（chrome 点按）不受影响。
 */
@Composable
fun PdfHighlightLayer(
    pageIndex: Int,
    items: List<AnnotationOverlayItem>,
    selectionEnabled: Boolean,
    onSelectCompleted: (TextSelection.PdfSelection) -> Unit,
    modifier: Modifier = Modifier,
    /** R26.2 选中态删除入口；null = 不显示 ✕。参数 (itemId, pageIndex)。 */
    onDeleteRequested: ((itemId: Long, page: Int) -> Unit)? = null,
    /** R27 选中态批注入口；null = 不显示 ✎。参数 (itemId, pageIndex)。 */
    onNoteRequested: ((item: AnnotationOverlayItem, page: Int) -> Unit)? = null,
) {
    var canvasSize by remember(pageIndex) { mutableStateOf(IntSize.Zero) }
    var dragRect by remember(pageIndex) { mutableStateOf<Pair<Offset, Offset>?>(null) }
    var selectedId by remember(pageIndex) { mutableStateOf<Long?>(null) }

    // 项集变化时清理失效选中。
    LaunchedEffect(items) {
        if (selectedId != null && items.none { it.id == selectedId }) selectedId = null
    }

    val badgeRadiusPx = 44f

    fun rectOf(item: AnnotationOverlayItem) = item.pdfRectNormalized()

    val gestureModifier = Modifier.pointerInput(
        pageIndex,
        selectionEnabled,
        items,
        onDeleteRequested,
        onNoteRequested,
    ) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val downPos = down.position
            val longPress = awaitLongPressOrCancellation(down.id)
            if (longPress == null) {
                // 未成长按：跟踪到抬手，判定是「点按」还是「滚动」。
                // 滚动（越 slop）→ 完全不干预；点按 → 高亮命中测试，未命中不消费。
                var movedMax = 0f
                var released: androidx.compose.ui.input.pointer.PointerInputChange? = null
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    movedMax = maxOf(movedMax, (change.position - downPos).getDistance())
                    if (!change.pressed) {
                        released = change
                        break
                    }
                }
                val up = released ?: return@awaitEachGesture
                if (movedMax > viewConfiguration.touchSlop) return@awaitEachGesture // 滚动：放行

                val pos = up.position
                if (selectedId != null) {
                    val badgeTarget = items.firstOrNull { it.id == selectedId }
                        ?.let { item -> rectOf(item)?.let { item to it } }
                    if (badgeTarget != null) {
                        val (item, r) = badgeTarget
                        // ✎ 批注优先于 ✕ 删除。
                        if (onNoteRequested != null && PdfHighlightGeometry.noteBadgeHit(
                                r, pos.x, pos.y, size.width.toFloat(), size.height.toFloat(), badgeRadiusPx,
                            )
                        ) {
                            up.consume()
                            selectedId = null
                            onNoteRequested(item, pageIndex)
                            return@awaitEachGesture
                        }
                        if (onDeleteRequested != null && PdfHighlightGeometry.deleteBadgeHit(
                                r, pos.x, pos.y, size.width.toFloat(), size.height.toFloat(), badgeRadiusPx,
                            )
                        ) {
                            up.consume()
                            selectedId = null
                            onDeleteRequested(item.id, pageIndex)
                            return@awaitEachGesture
                        }
                    }
                }
                val hit = PdfHighlightGeometry.hitTest(
                    itemsInDrawOrder = items.mapNotNull { item ->
                        rectOf(item)?.let { item.id to it }
                    },
                    x = pos.x,
                    y = pos.y,
                    widthPx = size.width.toFloat(),
                    heightPx = size.height.toFloat(),
                )
                if (hit != null) {
                    up.consume()
                    selectedId = hit
                } else if (selectedId != null) {
                    selectedId = null // 点空白取消选中，不消费事件
                }
                return@awaitEachGesture
            }
            // ── 长按成立：矩形拖选 ──
            selectedId = null
            var start = longPress.position
            var current = start
            dragRect = start to current
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    current = change.position
                    dragRect = start to current
                    change.consume()
                }
            } finally {
                dragRect = null
                val sizeW = canvasSize.width.toFloat()
                val sizeH = canvasSize.height.toFloat()
                val selection = if (sizeW > 0f && sizeH > 0f) {
                    PdfHighlightGeometry.selectionOrNull(
                        startX = start.x,
                        startY = start.y,
                        endX = current.x,
                        endY = current.y,
                        pageWidthPx = sizeW,
                        pageHeightPx = sizeH,
                        page = pageIndex,
                    )
                } else {
                    null
                }
                if (selection != null && PdfHighlightGeometry.meetsAreaGate(selection)) {
                    onSelectCompleted(selection)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
            .then(gestureModifier)
            .drawWithContent {
                drawContent()
                val w = size.width
                val h = size.height
                items.forEach { item ->
                    item.pdfRectNormalized()?.let { r ->
                        val isSelected = item.id == selectedId
                        val arm = badgeRadiusPx * 0.45f
                        drawRoundRect(
                            color = if (isSelected) HighlightFillSelected else HighlightFill,
                            topLeft = Offset(r.x * w, r.y * h),
                            size = Size(r.width * w, r.height * h),
                            cornerRadius = CornerRadius(6f, 6f),
                        )
                        drawRoundRect(
                            color = if (isSelected) Color(0xFFF57C00) else HighlightEdge,
                            topLeft = Offset(r.x * w, r.y * h),
                            size = Size(r.width * w, r.height * h),
                            cornerRadius = CornerRadius(6f, 6f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = if (isSelected) 3.5f else 2f,
                            ),
                        )
                        if (isSelected && onNoteRequested != null) {
                            // 左上角 ✎ 批注角标。
                            val cx = r.x * w + badgeRadiusPx
                            val cy = r.y * h - badgeRadiusPx * 0.6f
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
                        if (isSelected && onDeleteRequested != null) {
                            // 右上角 ✕ 角标（与 deleteBadgeHit 同心同半径）。
                            val cx = (r.x + r.width) * w - badgeRadiusPx
                            val cy = r.y * h - badgeRadiusPx * 0.6f
                            drawCircle(color = Color.White, radius = badgeRadiusPx, center = Offset(cx, cy))
                            drawCircle(color = Color(0xFFE53935), radius = badgeRadiusPx, center = Offset(cx, cy))
                            val arm = badgeRadiusPx * 0.45f
                            drawLine(Color.White, Offset(cx - arm, cy - arm), Offset(cx + arm, cy + arm), 4f)
                            drawLine(Color.White, Offset(cx - arm, cy + arm), Offset(cx + arm, cy - arm), 4f)
                        }
                    }
                }
                dragRect?.let { (a, b) ->
                    drawRect(
                        color = SelectionPreview,
                        topLeft = Offset(minOf(a.x, b.x), minOf(a.y, b.y)),
                        size = Size(kotlin.math.abs(a.x - b.x), kotlin.math.abs(a.y - b.y)),
                    )
                }
            },
    )
}

private val HighlightFill = Color(0x55FFD54F)
private val HighlightFillSelected = Color(0x77FFB300)
private val HighlightEdge = Color(0xCCF9A825)
private val SelectionPreview = Color(0x334FC3F7)
