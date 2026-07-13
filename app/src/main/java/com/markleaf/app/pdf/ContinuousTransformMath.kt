package com.markleaf.app.pdf

import kotlin.math.abs
import kotlin.math.max

data class TransformPoint(val x: Float, val y: Float)
data class TransformSize(val width: Float, val height: Float)
data class TransformOffset(val x: Float, val y: Float)
data class PanConsumption(val offset: TransformOffset, val remainder: TransformOffset)

object ContinuousTransformMath {
    fun targetScale(baseScale: Float, accumulatedGestureScale: Float): Float =
        PdfRenderQuality.clampZoom(baseScale * accumulatedGestureScale)

    fun anchoredOffset(
        oldScale: Float,
        newScale: Float,
        oldOffset: TransformOffset,
        centroid: TransformPoint,
        pan: TransformOffset = TransformOffset(0f, 0f),
    ): TransformOffset {
        if (oldScale <= 0f) return oldOffset
        val ratio = newScale / oldScale
        return TransformOffset(
            x = centroid.x - (centroid.x - oldOffset.x) * ratio + pan.x,
            y = centroid.y - (centroid.y - oldOffset.y) * ratio + pan.y,
        )
    }

    fun clampOffset(
        offset: TransformOffset,
        scale: Float,
        viewport: TransformSize,
        content: TransformSize = viewport,
    ): TransformOffset {
        if (scale <= 1.001f) return TransformOffset(0f, 0f)
        val overflowX = max(0f, content.width * scale - viewport.width)
        val overflowY = max(0f, content.height * scale - viewport.height)
        return TransformOffset(
            offset.x.coerceIn(-overflowX, 0f),
            offset.y.coerceIn(-overflowY, 0f),
        )
    }

    fun consumePan(
        current: TransformOffset,
        delta: TransformOffset,
        scale: Float,
        viewport: TransformSize,
        content: TransformSize = viewport,
    ): PanConsumption {
        val desired = TransformOffset(current.x + delta.x, current.y + delta.y)
        val clamped = clampOffset(desired, scale, viewport, content)
        val consumed = TransformOffset(clamped.x - current.x, clamped.y - current.y)
        return PanConsumption(
            offset = clamped,
            remainder = TransformOffset(delta.x - consumed.x, delta.y - consumed.y),
        )
    }

    /** Convert unconsumed screen-space finger dy into LazyColumn's unscaled scroll delta. */
    fun listScrollDelta(remainderY: Float, scale: Float): Float =
        -remainderY / scale.coerceAtLeast(1f)

    fun approximatelySame(a: TransformOffset, b: TransformOffset, epsilon: Float = 0.5f): Boolean =
        abs(a.x - b.x) <= epsilon && abs(a.y - b.y) <= epsilon
}
