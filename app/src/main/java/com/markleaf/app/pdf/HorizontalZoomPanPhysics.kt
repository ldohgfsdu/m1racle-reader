package com.markleaf.app.pdf

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class HorizontalGestureOwner { PAGE_STRIP, INNER_PAN }

enum class HorizontalPageTransitionDirection { PREVIOUS, NEXT }

enum class ZoomedPageTransitionPhase { IDLE, EDGE_PREVIEW, COMMITTING, CANCELLING, SETTLED }

/** The unscaled page rect fitted, horizontally centered and top-aligned in the viewport. */
data class HorizontalPageLayout(
    val viewportWidthPx: Float,
    val viewportHeightPx: Float,
    val fittedWidthPx: Float,
    val fittedHeightPx: Float,
    /** Top edge of the unscaled page inside the horizontal reading viewport. */
    val contentTopPx: Float = 0f,
)

/** Horizontal layout adapter view of shared legal translation bounds. */
data class HorizontalPanBounds(
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float,
) {
    val maxPanX: Float get() = maxX
    val maxPanY: Float get() = max(abs(minY), abs(maxY))

    fun asZoomPanBounds() = ZoomPanBounds(minX, maxX, minY, maxY)
}

/** Compatibility state exposed to the horizontal Compose adapter. All math lives in shared core. */
data class HorizontalPanState(
    val legalOffsetX: Float = 0f,
    val legalOffsetY: Float = 0f,
    val rawOverscrollX: Float = 0f,
    val visualOverscrollX: Float = 0f,
    val rawOverscrollY: Float = 0f,
    val visualOverscrollY: Float = 0f,
) {
    val displayOffsetX: Float get() = legalOffsetX + visualOverscrollX
    val displayOffsetY: Float get() = legalOffsetY + visualOverscrollY

    fun asZoomPanTransform(scale: Float): ZoomPanTransform = ZoomPanTransform(
        scale = scale,
        legalOffsetPx = ZoomPanPoint(legalOffsetX, legalOffsetY),
        rawOverscrollPx = ZoomPanPoint(rawOverscrollX, rawOverscrollY),
        visualOverscrollPx = ZoomPanPoint(visualOverscrollX, visualOverscrollY),
    )
}

data class HorizontalPageTransform(
    val scale: Float,
    val pan: HorizontalPanState,
)

data class HorizontalPanResult(
    val state: HorizontalPanState,
    val legalConsumedX: Float,
    val legalConsumedY: Float,
    val residualX: Float,
    val residualY: Float,
)

/**
 * Horizontal-only layout/Pager adapter.
 *
 * Focal math, legal/residual consumption, rubber mapping, clamp and settle are delegated to
 * [ZoomPanPhysics]; this object no longer owns a second H1 physics implementation.
 */
object HorizontalZoomPanPhysics {
    const val PAGE_STRIP_SCALE_THRESHOLD = 1.01f
    const val DEFAULT_PAGE_ASPECT = 1.414f
    fun owner(scale: Float): HorizontalGestureOwner =
        if (scale <= PAGE_STRIP_SCALE_THRESHOLD) HorizontalGestureOwner.PAGE_STRIP
        else HorizontalGestureOwner.INNER_PAN

    /** Fits a page with height/width [pageAspect] into the actual measured viewport. */
    fun fitPage(
        viewportWidthPx: Float,
        viewportHeightPx: Float,
        pageAspect: Float,
        contentMarginPx: Float,
        horizontalMarginPx: Float = contentMarginPx,
    ): HorizontalPageLayout {
        val width = viewportWidthPx.coerceAtLeast(0f)
        val height = viewportHeightPx.coerceAtLeast(0f)
        val margin = contentMarginPx.coerceAtLeast(0f)
        val horizontalMargin = horizontalMarginPx.coerceAtLeast(0f)
        val availableWidth = (width - horizontalMargin * 2f).coerceAtLeast(0f)
        val availableHeight = (height - margin * 2f).coerceAtLeast(0f)
        val aspect = pageAspect.takeIf { it.isFinite() && it > 0f } ?: DEFAULT_PAGE_ASPECT
        if (availableWidth == 0f || availableHeight == 0f) {
            return HorizontalPageLayout(width, height, 0f, 0f, margin)
        }
        val heightAtFullWidth = availableWidth * aspect
        val fittedWidth: Float
        val fittedHeight: Float
        if (heightAtFullWidth <= availableHeight) {
            fittedWidth = availableWidth
            fittedHeight = heightAtFullWidth
        } else {
            fittedHeight = availableHeight
            fittedWidth = availableHeight / aspect
        }
        // Single-page paging centers the fitted page in the reading viewport:
        // when the fitted page is shorter than the available height, its top
        // sits at the vertical middle (equal margins above and below) instead
        // of being glued to the top. When the page fills the height, it stays
        // at the reading margin and the zoomed pan bounds take over.
        val top = margin + ((availableHeight - fittedHeight) / 2f).coerceAtLeast(0f)
        return HorizontalPageLayout(width, height, fittedWidth, fittedHeight, top)
    }

    /**
     * Computes bounds from the fitted page itself, not from the outer viewport.
     *
     * At 1x the fitted page already owns its intentional reading margins. During a focal pinch,
     * every point inside that fitted rect must therefore be able to remain under the fingers from
     * the very first scale frame. Subtracting the whole viewport delayed legal motion until the
     * scaled page crossed the viewport edge, which produced the visible "move away, then return"
     * detour at the beginning of a pinch.
     */
    fun bounds(layout: HorizontalPageLayout, scale: Float): HorizontalPanBounds {
        if (scale <= 1f) return HorizontalPanBounds(0f, 0f, 0f, 0f)
        val safeScale = scale.coerceAtLeast(1f)
        val scaleGrowth = (safeScale - 1f).coerceAtLeast(0f)
        val maxX = layout.fittedWidthPx * scaleGrowth / 2f
        val minY = -layout.fittedHeightPx * scaleGrowth
        return HorizontalPanBounds(-maxX, maxX, minY, 0f)
    }

    fun rebaseLegalForPinch(
        baseState: HorizontalPanState,
        baseScale: Float,
        newScale: Float,
        basePointerX: Float,
        basePointerY: Float,
        pointerX: Float,
        pointerY: Float,
        transformOriginX: Float,
        transformOriginY: Float,
        bounds: HorizontalPanBounds,
    ): HorizontalPanState {
        if (newScale <= PAGE_STRIP_SCALE_THRESHOLD) return HorizontalPanState()
        val baseTransform = baseState.asZoomPanTransform(baseScale)
        val origin = ZoomPanPoint(transformOriginX, transformOriginY)
        val anchor = ZoomPanPhysics.captureFocalAnchor(
            baseTransform,
            ZoomPanPoint(basePointerX, basePointerY),
            origin,
        )
        return ZoomPanPhysics.applyScaleFromAnchor(
            transform = baseTransform,
            newScale = newScale,
            anchor = anchor,
            focalViewportPx = ZoomPanPoint(pointerX, pointerY),
            transformOriginViewportPx = origin,
            newBounds = bounds.asZoomPanBounds(),
            scaleRange = ZoomPanPhysicsProfile.ScaleRange,
        ).asHorizontalPanState()
    }

    fun pan(
        state: HorizontalPanState,
        deltaX: Float,
        deltaY: Float,
        bounds: HorizontalPanBounds,
        policy: AxisOverscrollPolicy,
        scale: Float = 1f,
        rebased: Boolean = false,
    ): HorizontalPanResult {
        if (rebased) return HorizontalPanResult(state, 0f, 0f, 0f, 0f)
        val result = ZoomPanPhysics.applyPan(
            transform = state.asZoomPanTransform(scale),
            rawDeltaPx = ZoomPanPoint(deltaX, deltaY),
            bounds = bounds.asZoomPanBounds(),
            policy = policy,
        )
        return HorizontalPanResult(
            state = result.transform.asHorizontalPanState(),
            legalConsumedX = result.legalConsumedPx.x,
            legalConsumedY = result.legalConsumedPx.y,
            residualX = result.residualPx.x,
            residualY = result.residualPx.y,
        )
    }

    /** Temporary source-compatible adapter for existing H1 tests/instrumentation. */
    fun pan(
        state: HorizontalPanState,
        deltaX: Float,
        deltaY: Float,
        bounds: HorizontalPanBounds,
        maxOverscrollPx: Float,
        rebased: Boolean = false,
    ): HorizontalPanResult = pan(
        state = state,
        deltaX = deltaX,
        deltaY = deltaY,
        bounds = bounds,
        policy = AxisOverscrollPolicy(
            horizontalEnabled = maxOverscrollPx > 0f,
            verticalEnabled = false,
            maxDistancePx = maxOverscrollPx,
        ),
        rebased = rebased,
    )

    fun withVisualOverscroll(
        state: HorizontalPanState,
        visualOverscrollX: Float,
        maxOverscrollPx: Float,
    ): HorizontalPanState = ZoomPanPhysics.withVisualOverscroll(
        transform = state.asZoomPanTransform(scale = 1f),
        visualOverscrollPx = ZoomPanPoint(visualOverscrollX, 0f),
        policy = AxisOverscrollPolicy(true, false, maxOverscrollPx),
    ).asHorizontalPanState()

    fun withVisualOverscroll(
        state: HorizontalPanState,
        visualOverscrollPx: ZoomPanPoint,
        policy: AxisOverscrollPolicy,
    ): HorizontalPanState = ZoomPanPhysics.withVisualOverscroll(
        transform = state.asZoomPanTransform(scale = 1f),
        visualOverscrollPx = visualOverscrollPx,
        policy = policy,
    ).asHorizontalPanState()

    fun clampToBounds(
        state: HorizontalPanState,
        bounds: HorizontalPanBounds,
    ): HorizontalPanState = ZoomPanPhysics.clampToBounds(
        state.asZoomPanTransform(scale = 1f),
        bounds.asZoomPanBounds(),
    ).asHorizontalPanState()

    fun settle(state: HorizontalPanState): HorizontalPanState =
        ZoomPanPhysics.springTarget(state.asZoomPanTransform(scale = 1f)).asHorizontalPanState()

    fun rubberBand(rawOverscrollPx: Float, limitPx: Float): Float =
        ZoomPanPhysics.rubberBand(rawOverscrollPx, limitPx)

    fun viewportSession(
        pageIndex: Int,
        layout: HorizontalPageLayout,
        scale: Float,
        pan: HorizontalPanState,
        generation: Long,
        viewportAnchorXFraction: Float = 0.5f,
        viewportAnchorYFraction: Float = 0.5f,
        phase: PdfGesturePhase = PdfGesturePhase.IDLE,
        pointerEpoch: Long? = null,
    ): PdfViewportSessionState {
        val safeScale = scale.coerceAtLeast(0.0001f)
        val requestedViewportX = layout.viewportWidthPx * viewportAnchorXFraction
        val requestedViewportY = layout.viewportHeightPx * viewportAnchorYFraction
        val pageLeft = layout.viewportWidthPx / 2f - layout.fittedWidthPx * safeScale / 2f +
            pan.legalOffsetX
        val pageRight = pageLeft + layout.fittedWidthPx * safeScale
        val pageTop = layout.contentTopPx + pan.legalOffsetY
        val pageBottom = pageTop + layout.fittedHeightPx * safeScale
        val visibleLeft = max(0f, pageLeft)
        val visibleRight = min(layout.viewportWidthPx, pageRight)
        val visibleTop = max(0f, pageTop)
        val visibleBottom = min(layout.viewportHeightPx, pageBottom)
        val viewportX = if (visibleRight >= visibleLeft) {
            requestedViewportX.coerceIn(visibleLeft, visibleRight)
        } else {
            requestedViewportX
        }
        val viewportY = if (visibleBottom >= visibleTop) {
            requestedViewportY.coerceIn(visibleTop, visibleBottom)
        } else {
            requestedViewportY
        }
        val contentX = (
            viewportX - layout.viewportWidthPx / 2f - pan.legalOffsetX
            ) / safeScale + layout.fittedWidthPx / 2f
        val contentY = (
            viewportY - layout.contentTopPx - pan.legalOffsetY
            ) / safeScale
        return PdfViewportSessionState(
            readingMode = PdfReadingMode.HORIZONTAL_PAGED,
            pageIndex = pageIndex,
            pageAnchorXFraction = (contentX / layout.fittedWidthPx.coerceAtLeast(1f)).coerceIn(0f, 1f),
            pageAnchorYFraction = (contentY / layout.fittedHeightPx.coerceAtLeast(1f)).coerceIn(0f, 1f),
            viewportAnchorXFraction = (viewportX / layout.viewportWidthPx.coerceAtLeast(1f))
                .coerceIn(0f, 1f),
            viewportAnchorYFraction = (viewportY / layout.viewportHeightPx.coerceAtLeast(1f))
                .coerceIn(0f, 1f),
            sourceViewportWidthPx = layout.viewportWidthPx.coerceAtLeast(1f),
            sourceViewportHeightPx = layout.viewportHeightPx.coerceAtLeast(1f),
            scale = scale,
            generation = generation,
            legalOffsetX = pan.legalOffsetX,
            legalOffsetY = pan.legalOffsetY,
            visualOverscrollX = pan.visualOverscrollX,
            visualOverscrollY = pan.visualOverscrollY,
            interactionPhase = phase,
            pointerEpoch = pointerEpoch,
        )
    }

    fun restoreViewportSession(
        session: PdfViewportSessionState,
        layout: HorizontalPageLayout,
        entryDirection: HorizontalPageTransitionDirection? = null,
    ): HorizontalPageTransform {
        val scale = ZoomPanPhysics.clampScale(session.scale, ZoomPanPhysicsProfile.ScaleRange)
        if (scale <= PAGE_STRIP_SCALE_THRESHOLD) return HorizontalPageTransform(scale, HorizontalPanState())
        val bounds = bounds(layout, scale)
        val viewportX = layout.viewportWidthPx * session.viewportAnchorXFraction
        val viewportY = layout.viewportHeightPx * session.viewportAnchorYFraction
        val desiredX = viewportX - layout.viewportWidthPx / 2f -
            (session.pageAnchorXFraction * layout.fittedWidthPx - layout.fittedWidthPx / 2f) * scale
        val desiredY = viewportY - layout.contentTopPx -
            session.pageAnchorYFraction * layout.fittedHeightPx * scale
        val entryX = when (entryDirection) {
            HorizontalPageTransitionDirection.NEXT -> bounds.maxX
            HorizontalPageTransitionDirection.PREVIOUS -> bounds.minX
            null -> desiredX.coerceIn(bounds.minX, bounds.maxX)
        }
        return HorizontalPageTransform(
            scale = scale,
            pan = HorizontalPanState(
                legalOffsetX = entryX,
                legalOffsetY = desiredY.coerceIn(bounds.minY, bounds.maxY),
            ),
        )
    }

    /** Cancelled edge preview keeps scale/legal position and clears display-only edge pull. */
    fun cancelledHandoffTransform(
        scale: Float,
        pan: HorizontalPanState,
    ): HorizontalPageTransform = HorizontalPageTransform(scale, settle(pan))

}

fun ZoomPanTransform.asHorizontalPanState(): HorizontalPanState = HorizontalPanState(
    legalOffsetX = legalOffsetPx.x,
    legalOffsetY = legalOffsetPx.y,
    rawOverscrollX = rawOverscrollPx.x,
    visualOverscrollX = visualOverscrollPx.x,
    rawOverscrollY = rawOverscrollPx.y,
    visualOverscrollY = visualOverscrollPx.y,
)
