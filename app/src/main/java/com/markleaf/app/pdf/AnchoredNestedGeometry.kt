package com.markleaf.app.pdf

import kotlin.math.max
import kotlin.math.min

/**
 * Downward foreground child geometry (root / boundsInRoot only).
 *
 * Target = full secondary **card** (not a horizontal pill):
 *  - right = parentRect.right (Main right fixed)
 *  - top prefers sourceRect.top
 *  - left expands 80–120dp past the parent left (r26 §4), clamped to
 *    [minLeftPx] ≈ 16dp from the screen left edge — the back circle sits in
 *    the top strip and never shares the child's vertical band, so the child
 *    may legally reach the 16dp margin (r26: "最终距屏幕左边至少 16dp")
 *  - height ≫ source row (clamped to viewport)
 *
 * Morph is a **full LTRB continuous interpolation** (r26 §4/§7):
 *  - p drives every edge linearly source → target (the card grows in height
 *    AND widens leftward together — never a 52dp-tall horizontal bar, because
 *    the container is a real card at every frame);
 *  - right edge stays pinned (source right == parent right)
 *  - p=0 → exactly the source row rect
 */
data class AnchoredNestedInput(
    val parentRectLeft: Float,
    val parentRectTop: Float,
    val parentRectRight: Float,
    val parentRectBottom: Float,
    val sourceRectLeft: Float,
    val sourceRectTop: Float,
    val sourceRectRight: Float,
    val sourceRectBottom: Float,
    /** Screen-space minimum left edge for the fully-open child (≈16dp px). */
    val minLeftPx: Float,
    val viewportBottom: Float,
    val leftExpandPx: Float,
    val contentHeightPx: Float,
    val bottomMarginPx: Float,
    val minHeightPx: Float = 52f,
)

data class AnchoredNestedTarget(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/**
 * Fully-open target: a tall card from the source row, right-aligned to Main.
 * [sourceRectLeft]/[sourceRectRight] define morph origin; target left expands
 * mildly for content width (never a row-height-only strip).
 */
fun computeAnchoredNestedTarget(input: AnchoredNestedInput): AnchoredNestedTarget {
    val childRight = input.parentRectRight
    val preferredLeft = input.parentRectLeft - input.leftExpandPx
    val childLeft = max(input.minLeftPx, preferredLeft)
    val sourceH = (input.sourceRectBottom - input.sourceRectTop).coerceAtLeast(1f)
    // Fully-open card must be clearly taller than a single row (anti-pill gate).
    val minCardH = max(input.minHeightPx, sourceH * 3.5f)
    val availableFromTop = input.viewportBottom - input.bottomMarginPx - input.sourceRectTop
    val height = min(
        input.contentHeightPx.coerceAtLeast(minCardH),
        availableFromTop.coerceAtLeast(minCardH),
    ).coerceAtLeast(minCardH)
    val maxTop = input.viewportBottom - input.bottomMarginPx - height
    val childTop = input.sourceRectTop.coerceIn(
        minOf(0f, maxTop),
        maxOf(0f, maxTop),
    )
    val childBottom = min(childTop + height, input.viewportBottom - input.bottomMarginPx)
        .coerceAtLeast(childTop + minCardH)
    return AnchoredNestedTarget(
        left = childLeft,
        top = childTop,
        right = childRight,
        bottom = childBottom,
    )
}

/**
 * Full LTRB continuous morph. Invariants (tested):
 *  - p=0 → exactly source L/T/R/B (row shape)
 *  - p=1 → exactly target (tall card)
 *  - each edge is a continuous linear interpolation source→target (no jump,
 *    no off-track bounds, no bottom-only / horizontal-bar phase)
 *  - open/close share trajectory (same p → same rect)
 *  - fully-open height ≫ source height (not a horizontal capsule)
 */
fun anchoredRevealRect(
    sourceLeft: Float,
    sourceTop: Float,
    sourceRight: Float,
    sourceBottom: Float,
    targetLeft: Float,
    targetTop: Float,
    targetRight: Float,
    targetBottom: Float,
    p: Float,
): AnchoredNestedTarget {
    val t = p.coerceIn(0f, 1f)
    return AnchoredNestedTarget(
        left = sourceLeft + (targetLeft - sourceLeft) * t,
        top = sourceTop + (targetTop - sourceTop) * t,
        right = sourceRight + (targetRight - sourceRight) * t,
        bottom = sourceBottom + (targetBottom - sourceBottom) * t,
    )
}

// ── Scroll-start helpers (shared by outline list + thumbnail grid) ────────

/** Row index of the current page inside a two-column thumbnail grid. */
fun thumbnailGridRow(currentPageIndex: Int): Int = currentPageIndex / 2

/**
 * First visible row so the current page lands around the upper-third of the
 * viewport with a couple of rows of context above it.
 */
fun thumbnailInitialRow(currentPageIndex: Int): Int =
    max(0, thumbnailGridRow(currentPageIndex) - 2)

/**
 * First visible row for the outline list: the current node's render index
 * minus a few rows of context, so it lands near the upper-third.
 */
fun outlineInitialRow(renderIndex: Int): Int = max(0, renderIndex - 3)
