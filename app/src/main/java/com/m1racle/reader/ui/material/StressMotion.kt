package com.m1racle.reader.ui.material

import kotlin.math.roundToInt

/**
 * Motion contract for the settings stress backdrop.
 *
 * Compose scroll places the child with placeRelativeWithLayer, so the moving
 * strip is its own graphics layer. A graphicsLayer translation is the same kind
 * of layer. LayerBackdrop records that layer by drawing it; the recorded copy
 * is the previous frame's texture, which flashes as the strip moves.
 *
 * The preview therefore translates inside the backdrop's own draw, with no
 * extra graphics layer. Capture size does not depend on the translation.
 */
object StressMotion {
    const val PageCount = 3
    const val PageWidthDp = 320f

    fun captureSizePx(viewportWidthPx: Int, viewportHeightPx: Int): Pair<Int, Int> =
        viewportWidthPx to viewportHeightPx

    /** Negative so the strip travels left as [travelPx] increases. Not rounded. */
    fun translationX(travelPx: Float): Float = -travelPx

    fun maxTravelPx(contentWidthPx: Float, viewportWidthPx: Float): Float =
        (contentWidthPx - viewportWidthPx).coerceAtLeast(0f)

    /** What the old ScrollState path did to the same travel. */
    fun quantizedScrollPx(travelPx: Float): Int = travelPx.roundToInt()

    fun frames(
        steps: Int,
        stepPx: Float,
        viewportWidthPx: Int,
        viewportHeightPx: Int,
        blurPx: Float,
        sourceId: Int,
        effectId: Int,
    ): List<StressFrame> {
        val size = captureSizePx(viewportWidthPx, viewportHeightPx)
        return List(steps) { index ->
            val travel = index * stepPx
            StressFrame(
                frameIndex = index,
                sourceWidth = size.first,
                sourceHeight = size.second,
                captureWidth = size.first,
                captureHeight = size.second,
                translationX = translationX(travel),
                blurPx = blurPx,
                sourceId = sourceId,
                effectId = effectId,
            )
        }
    }
}

data class StressFrame(
    val frameIndex: Int,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val captureWidth: Int,
    val captureHeight: Int,
    val translationX: Float,
    val blurPx: Float,
    val sourceId: Int,
    val effectId: Int,
)
