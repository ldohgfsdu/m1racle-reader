package com.m1racle.reader.ui.material

import kotlin.math.roundToInt

/**
 * Motion contract for the settings stress backdrop.
 *
 * Compose [androidx.compose.foundation.ScrollState] stores an Int. The scroll
 * layout then places the child at that integer every frame, so a 0.25px step
 * alternately stalls and jumps. LayerBackdrop also writes new layout coordinates
 * on each of those placements. Blur of high-contrast text turns that jump into
 * a brightness flash.
 *
 * The preview keeps the source box size fixed and moves the strip with a float
 * graphics-layer translation. Capture size does not depend on the translation.
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
