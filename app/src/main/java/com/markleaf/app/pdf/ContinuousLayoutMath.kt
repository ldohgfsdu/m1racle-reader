package com.markleaf.app.pdf

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure geometry for R5-P0 continuous-mode document-layout scaling.
 *
 * Model: the committed zoom is applied to the **LazyColumn item layout** (page width/height
 * grow with zoom), so vertical navigation is native LazyList scroll. The GPU `graphicsLayer`
 * only adds a transient pinch scale (relative to the committed layout) and a horizontal
 * [panX]; there is no persistent vertical page offset and no manual list fling injection.
 *
 * All functions are side-effect free and unit-tested (see R5ContinuousScrollTest).
 */
enum class AxisLock { NONE, HORIZONTAL, VERTICAL }

object ContinuousLayoutMath {

    /** Document-layout page width (px) for a committed zoom. */
    fun pageLayoutWidthPx(viewportWidthPx: Int, committedScale: Float): Int =
        (viewportWidthPx.coerceAtLeast(1) * committedScale.coerceAtLeast(1f)).roundToInt()

    /** Document-layout page height (px) = width × aspect (h/w). */
    fun pageLayoutHeightPx(viewportWidthPx: Int, committedScale: Float, aspectHw: Float): Int =
        (pageLayoutWidthPx(viewportWidthPx, committedScale) * aspectHw.coerceIn(0.4f, 3f))
            .roundToInt()

    /**
     * Axis arbitration with touch slop. Once a dominant axis locks, it stays locked for the
     * gesture (R5: "手势一旦锁定，不在同一 gesture 中反复切换轴").
     */
    fun resolveAxis(dx: Float, dy: Float, touchSlop: Float, current: AxisLock): AxisLock {
        if (current != AxisLock.NONE) return current
        if (abs(dx) < touchSlop && abs(dy) < touchSlop) return AxisLock.NONE
        return if (abs(dx) > abs(dy)) AxisLock.HORIZONTAL else AxisLock.VERTICAL
    }

    /**
     * Horizontal pan clamp: when content is wider than the viewport it may only shift left
     * (negative), never expose empty space on the left. At 1× the pan is always 0.
     */
    fun clampHorizontalOffset(panX: Float, contentWidthPx: Int, viewportWidthPx: Int): Float {
        if (contentWidthPx <= viewportWidthPx) return 0f
        val overflow = (contentWidthPx - viewportWidthPx).toFloat()
        return panX.coerceIn(-overflow, 0f)
    }

    /** Horizontal pan after committing a scale: cleared at 1×, preserved otherwise. */
    fun offsetXAfterCommit(scale: Float, currentPanX: Float): Float =
        if (scale <= 1.01f) 0f else currentPanX

    /**
     * Total LazyList scroll offset (px, NEW committed layout) that keeps the content point
     * under the pinch centroid fixed on screen after committing a new committed scale.
     *
     * Derivation: before commit the screen Y of the anchored content point equals the centroid
     * because the GPU transform is anchored. After commit the layout is scaled by
     * newCommitted/oldCommitted, so the same content point's new layout Y is
     * `(centroidY - gesturePanY)` and the desired total scroll keeps it at `centroidY`, i.e.
     * `newTotal = oldTotal - gesturePanY`.
     */
    fun anchorTotalScrollCorrection(oldTotalScroll: Float, gesturePanY: Float): Float =
        oldTotalScroll - gesturePanY

    /**
     * Normalized vertical position (0..1) of the content point under the centroid, given the
     * pinch GPU transform (relative scale + vertical translate) and the page layout geometry.
     */
    fun contentNormY(
        centroidY: Float,
        totalScroll: Float,
        gestureScale: Float,
        gesturePanY: Float,
        pageTopPx: Float,
        pageHeightPx: Float,
    ): Float {
        if (pageHeightPx <= 0f) return 0f
        val layoutY = (centroidY - gesturePanY) / gestureScale.coerceAtLeast(0.01f) + totalScroll
        return ((layoutY - pageTopPx) / pageHeightPx).coerceIn(0f, 1f)
    }

    /**
     * `scrollToItem` offset (px) that positions the page top so the content point at
     * [normalizedY] lands exactly under [centroidY].
     */
    fun anchorScrollOffsetForContent(
        centroidY: Float,
        normalizedY: Float,
        pageHeightPx: Int,
    ): Int = (centroidY - normalizedY * pageHeightPx).roundToInt()
}
