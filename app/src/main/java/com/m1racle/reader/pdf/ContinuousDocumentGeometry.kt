package com.m1racle.reader.pdf

import kotlin.math.max
import kotlin.math.min

/** Single product authority for the R7 continuous viewport zoom range. */
const val MAX_CONTINUOUS_SCALE = MAX_ZOOM_PAN_SCALE

/** One document-wide transform; no page owns zoom or pan. */
data class ContinuousDocumentState(
    val scale: Float = 1f,
    val scrollX: Float = 0f,
    val scrollY: Float = 0f,
    val gestureActive: Boolean = false,
)

/**
 * Immutable fit-width document geometry. Page coordinates are defined at scale=1;
 * [ContinuousDocumentState] transforms the entire document into viewport coordinates.
 */
class ContinuousDocumentGeometry private constructor(
    val pageCount: Int,
    val basePageWidth: Float,
    val pageGap: Float,
    private val pageTops: FloatArray,
    private val pageHeights: FloatArray,
    private val pageGeometryGenerations: LongArray,
    val baseDocumentHeight: Float,
) {
    /** Version of the fitted scale-1 page width used by both base bitmap and tile layout. */
    val fittedPageWidthVersion: Int = basePageWidth.toBits()

    fun pageTop(page: Int): Float = pageTops[page.coerceIn(0, pageCount - 1)]
    fun pageHeight(page: Int): Float = pageHeights[page.coerceIn(0, pageCount - 1)]
    fun pageGeometryGeneration(page: Int): Long =
        pageGeometryGenerations[page.coerceIn(0, pageCount - 1)]
    fun scaledDocumentHeight(scale: Float): Float = baseDocumentHeight * scale
    fun scaledDocumentWidth(scale: Float): Float = basePageWidth * scale

    fun maxScrollX(scale: Float, viewportWidth: Float): Float =
        max(0f, scaledDocumentWidth(scale) - viewportWidth)

    fun maxScrollY(scale: Float, viewportHeight: Float): Float =
        max(0f, scaledDocumentHeight(scale) - viewportHeight)

    /** Continuous adapter: document scroll is the negative of content translation. */
    fun zoomPanBounds(scale: Float, viewportWidth: Float, viewportHeight: Float): ZoomPanBounds =
        ZoomPanBounds(
            minX = if (scale <= MIN_SCALE) 0f else -maxScrollX(scale, viewportWidth),
            maxX = 0f,
            minY = -maxScrollY(scale, viewportHeight),
            maxY = 0f,
        )

    fun clamp(state: ContinuousDocumentState, viewportWidth: Float, viewportHeight: Float): ContinuousDocumentState {
        val scale = ZoomPanPhysics.clampScale(state.scale, ZoomPanPhysicsProfile.ScaleRange)
        val clamped = ZoomPanPhysics.clampToBounds(
            transform = state.toZoomPanTransform(scale),
            bounds = zoomPanBounds(scale, viewportWidth, viewportHeight),
        )
        return state.fromZoomPanTransform(clamped)
    }

    /** Preserve the document point beneath the pinch centroid across a scale change. */
    fun zoomAt(
        state: ContinuousDocumentState,
        newScale: Float,
        centroidX: Float,
        centroidY: Float,
        viewportWidth: Float,
        viewportHeight: Float,
    ): ContinuousDocumentState {
        val nextScale = ZoomPanPhysics.clampScale(newScale, ZoomPanPhysicsProfile.ScaleRange)
        val transformed = ZoomPanPhysics.applyScaleAtFocal(
            transform = state.toZoomPanTransform(),
            newScale = nextScale,
            focalViewportPx = ZoomPanPoint(centroidX, centroidY),
            transformOriginViewportPx = ZoomPanPoint.Zero,
            newBounds = zoomPanBounds(nextScale, viewportWidth, viewportHeight),
            scaleRange = ZoomPanPhysicsProfile.ScaleRange,
        )
        return state.fromZoomPanTransform(transformed)
    }

    /** Positive finger delta moves document content with the finger. */
    fun panBy(
        state: ContinuousDocumentState,
        fingerDx: Float,
        fingerDy: Float,
        viewportWidth: Float,
        viewportHeight: Float,
    ): ContinuousDocumentState {
        val scale = ZoomPanPhysics.clampScale(state.scale, ZoomPanPhysicsProfile.ScaleRange)
        val result = ZoomPanPhysics.applyPan(
            transform = state.toZoomPanTransform(scale),
            rawDeltaPx = ZoomPanPoint(fingerDx, fingerDy),
            bounds = zoomPanBounds(scale, viewportWidth, viewportHeight),
            policy = ZoomPanPhysicsProfile.ContinuousOverscrollPolicy,
        )
        return state.fromZoomPanTransform(result.transform)
    }

    /**
     * P0B1: Single-step focal-point-preserving transform.
     *
     * Uses the gesture-baseline [focalDocumentX/Y] (captured at gesture start or rebase)
     * and the current [centroidX/Y] to compute new scroll in one pass:
     *   newScroll = focalDocument * newScale - centroid
     * Then clamps once with new-scale boundaries.
     *
     * This eliminates the (Z-1)*P per-frame accumulated drift that [zoomAt] + [panBy]
     * produces when the centroid moves between frames.
     */
    fun zoomAndPanFromFocal(
        state: ContinuousDocumentState,
        newScale: Float,
        focalDocumentX: Float,
        focalDocumentY: Float,
        centroidX: Float,
        centroidY: Float,
        viewportWidth: Float,
        viewportHeight: Float,
    ): ContinuousDocumentState {
        val nextScale = ZoomPanPhysics.clampScale(newScale, ZoomPanPhysicsProfile.ScaleRange)
        val transformed = ZoomPanPhysics.applyScaleFromAnchor(
            transform = state.toZoomPanTransform(),
            newScale = nextScale,
            anchor = ZoomPanFocalAnchor(focalDocumentX, focalDocumentY),
            focalViewportPx = ZoomPanPoint(centroidX, centroidY),
            transformOriginViewportPx = ZoomPanPoint.Zero,
            newBounds = zoomPanBounds(nextScale, viewportWidth, viewportHeight),
            scaleRange = ZoomPanPhysicsProfile.ScaleRange,
        )
        return state.fromZoomPanTransform(transformed)
    }

    private fun ContinuousDocumentState.toZoomPanTransform(
        transformScale: Float = scale,
    ): ZoomPanTransform = ZoomPanTransform(
        scale = transformScale,
        legalOffsetPx = ZoomPanPoint(-scrollX, -scrollY),
    )

    private fun ContinuousDocumentState.fromZoomPanTransform(
        transform: ZoomPanTransform,
    ): ContinuousDocumentState = copy(
        scale = transform.scale,
        scrollX = -transform.legalOffsetPx.x,
        scrollY = -transform.legalOffsetPx.y,
    )

    /** Viewport center chooses page metadata/render priority only; it never drives scroll. */
    fun pageAtViewportCenter(state: ContinuousDocumentState, viewportHeight: Float): Int =
        pageAtDocumentY((state.scrollY + viewportHeight / 2f) / state.scale.coerceAtLeast(0.0001f))

    /** Page + fraction under the stable viewport-center anchor used for restore/persistence. */
    fun readingPositionAtViewportCenter(
        state: ContinuousDocumentState,
        viewportHeight: Float,
        source: ReadingPositionSource,
        generation: Long,
    ): ReadingPosition {
        val documentY = (state.scrollY + viewportHeight / 2f) / state.scale.coerceAtLeast(0.0001f)
        val page = pageAtDocumentY(documentY)
        val fraction = ((documentY - pageTop(page)) / pageHeight(page).coerceAtLeast(0.0001f))
            .coerceIn(0f, 1f)
        return ReadingPosition(page, fraction, source, generation)
    }

    fun viewportSessionAtAnchor(
        state: ContinuousDocumentState,
        viewportWidth: Float,
        viewportHeight: Float,
        generation: Long,
        viewportAnchorXFraction: Float = 0.5f,
        viewportAnchorYFraction: Float = 0.5f,
    ): PdfViewportSessionState {
        val safeScale = state.scale.coerceAtLeast(0.0001f)
        val requestedViewportY = viewportHeight * viewportAnchorYFraction
        val requestedDocumentY = (state.scrollY + requestedViewportY) / safeScale
        var page = pageAtDocumentY(requestedDocumentY)
        var resolvedViewportY = requestedViewportY
        val requestedInsidePage = requestedDocumentY >= pageTop(page) &&
            requestedDocumentY <= pageTop(page) + pageHeight(page)
        if (!requestedInsidePage && pageCount > 0) {
            // A continuous viewport can put its center inside an inter-page gap, while horizontal
            // mode must select exactly one page. Preserve the dominant visible page and a real
            // point on that page instead of coercing a negative next-page fraction to zero.
            val visibleDocumentTop = state.scrollY / safeScale
            val visibleDocumentBottom = (state.scrollY + viewportHeight) / safeScale
            val candidateStart = (pageAtDocumentY(visibleDocumentTop) - 1).coerceAtLeast(0)
            val candidateEnd = (pageAtDocumentY(visibleDocumentBottom) + 1)
                .coerceAtMost(pageCount - 1)
            var bestPage = page
            var bestVisibleHeightPx = -1f
            for (candidate in candidateStart..candidateEnd) {
                val topPx = pageTop(candidate) * safeScale - state.scrollY
                val bottomPx = (pageTop(candidate) + pageHeight(candidate)) * safeScale - state.scrollY
                val visibleHeightPx = (
                    min(viewportHeight, bottomPx) - max(0f, topPx)
                    ).coerceAtLeast(0f)
                if (visibleHeightPx > bestVisibleHeightPx + 0.001f ||
                    kotlin.math.abs(visibleHeightPx - bestVisibleHeightPx) <= 0.001f &&
                    candidate < bestPage
                ) {
                    bestPage = candidate
                    bestVisibleHeightPx = visibleHeightPx
                }
            }
            page = bestPage
            val pageTopPx = pageTop(page) * safeScale - state.scrollY
            val pageBottomPx = (pageTop(page) + pageHeight(page)) * safeScale - state.scrollY
            val visibleTopPx = max(0f, pageTopPx)
            val visibleBottomPx = min(viewportHeight, pageBottomPx)
            resolvedViewportY = if (visibleBottomPx >= visibleTopPx) {
                requestedViewportY.coerceIn(visibleTopPx, visibleBottomPx)
            } else {
                requestedViewportY
            }
        }
        val documentX = (state.scrollX + viewportWidth * viewportAnchorXFraction) / safeScale
        val documentY = (state.scrollY + resolvedViewportY) / safeScale
        return PdfViewportSessionState(
            readingMode = PdfReadingMode.VERTICAL_CONTINUOUS,
            pageIndex = page,
            pageAnchorXFraction = (documentX / basePageWidth.coerceAtLeast(1f)).coerceIn(0f, 1f),
            pageAnchorYFraction = ((documentY - pageTop(page)) / pageHeight(page).coerceAtLeast(1f)).coerceIn(0f, 1f),
            viewportAnchorXFraction = viewportAnchorXFraction,
            viewportAnchorYFraction = (resolvedViewportY / viewportHeight.coerceAtLeast(1f))
                .coerceIn(0f, 1f),
            sourceViewportWidthPx = viewportWidth.coerceAtLeast(1f),
            sourceViewportHeightPx = viewportHeight.coerceAtLeast(1f),
            scale = state.scale,
            generation = generation,
        )
    }

    fun restoreViewportSession(
        session: PdfViewportSessionState,
        state: ContinuousDocumentState,
        viewportWidth: Float,
        viewportHeight: Float,
    ): ContinuousDocumentState {
        val scale = session.scale.coerceIn(MIN_SCALE, MAX_CONTINUOUS_SCALE)
        val documentX = basePageWidth * session.pageAnchorXFraction
        val documentY = pageTop(session.pageIndex) + pageHeight(session.pageIndex) * session.pageAnchorYFraction
        return clamp(
            state.copy(
                scale = scale,
                scrollX = documentX * scale - viewportWidth * session.viewportAnchorXFraction,
                scrollY = documentY * scale - viewportHeight * session.viewportAnchorYFraction,
                gestureActive = false,
            ),
            viewportWidth,
            viewportHeight,
        )
    }

    /** True when any part of [page] is visible given the current viewport transform. */
    fun pageIntersectsViewport(
        state: ContinuousDocumentState,
        viewportHeight: Float,
        page: Int,
    ): Boolean {
        val scale = state.scale.coerceAtLeast(0.0001f)
        val visibleTop = state.scrollY / scale
        val visibleBottom = (state.scrollY + viewportHeight) / scale
        val pageTop = pageTop(page)
        val pageBottom = pageTop + pageHeight(page)
        return pageTop < visibleBottom && pageBottom > visibleTop
    }

    /** True when the anchor document-Y (from the restore target) is inside the visible viewport. */
    fun anchorIsInViewport(
        state: ContinuousDocumentState,
        viewportHeight: Float,
        targetPage: Int,
        intraPageFraction: Float,
    ): Boolean {
        val scale = state.scale.coerceAtLeast(0.0001f)
        val anchorY = pageTop(targetPage) + intraPageFraction * pageHeight(targetPage)
        val visibleTop = state.scrollY / scale
        val visibleBottom = (state.scrollY + viewportHeight) / scale
        return anchorY >= visibleTop && anchorY <= visibleBottom
    }

    /**
     * After page heights change (real aspect replacing the 1.414 placeholder),
     * keep the document point that was at the viewport top. Without this,
     * later pages shift under a stale scrollY and continuous scroll jumps.
     */
    fun remapStatePreservingViewportTop(
        old: ContinuousDocumentGeometry,
        state: ContinuousDocumentState,
        viewportWidth: Float,
        viewportHeight: Float,
    ): ContinuousDocumentState {
        if (pageCount <= 0 || old.pageCount != pageCount) {
            return clamp(state, viewportWidth, viewportHeight)
        }
        val scale = state.scale.coerceAtLeast(0.0001f)
        val documentYAtTop = state.scrollY / scale
        val page = old.pageAtDocumentY(documentYAtTop)
        val oldHeight = old.pageHeight(page).coerceAtLeast(0.0001f)
        val fraction = (documentYAtTop - old.pageTop(page)) / oldHeight
        val newDocumentY = pageTop(page) + fraction * pageHeight(page)
        val widthRatio = if (old.basePageWidth > 0.0001f) {
            basePageWidth / old.basePageWidth
        } else {
            1f
        }
        return clamp(
            state.copy(
                scrollX = state.scrollX * widthRatio,
                scrollY = newDocumentY * scale,
            ),
            viewportWidth,
            viewportHeight,
        )
    }

    fun pageAtDocumentY(documentY: Float): Int {
        if (pageCount == 0) return 0
        var low = 0
        var high = pageCount - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val top = pageTops[mid]
            val bottom = top + pageHeights[mid]
            when {
                documentY < top -> high = mid - 1
                documentY > bottom -> low = mid + 1
                else -> return mid
            }
        }
        return min(pageCount - 1, max(0, low))
    }

    /** Includes [prefetchPages] around the actual viewport, never the full document. */
    fun composedPages(state: ContinuousDocumentState, viewportHeight: Float, prefetchPages: Int = 2): IntRange {
        if (pageCount == 0) return IntRange.EMPTY
        val scale = state.scale.coerceAtLeast(0.0001f)
        val first = pageAtDocumentY(state.scrollY / scale)
        val last = pageAtDocumentY((state.scrollY + viewportHeight) / scale)
        return (first - prefetchPages).coerceAtLeast(0)..(last + prefetchPages).coerceAtMost(pageCount - 1)
    }

    /** Exact transformed page bounds captured with an R7 gesture snapshot. */
    fun pageBoundsInViewport(state: ContinuousDocumentState, page: Int): OverlayRect {
        val safePage = page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        val left = -state.scrollX
        val top = pageTop(safePage) * state.scale - state.scrollY
        return OverlayRect(
            left = left,
            top = top,
            width = basePageWidth * state.scale,
            height = pageHeight(safePage) * state.scale,
        )
    }

    companion object {
        const val MIN_SCALE = 1f

        fun create(pageCount: Int, basePageWidth: Float, aspects: Map<Int, Float>, pageGap: Float): ContinuousDocumentGeometry {
            val count = pageCount.coerceAtLeast(0)
            val tops = FloatArray(count)
            val heights = FloatArray(count)
            val geometryGenerations = LongArray(count)
            var cursor = 0f
            for (page in 0 until count) {
                tops[page] = cursor
                val aspect = (aspects[page] ?: DEFAULT_ASPECT).coerceIn(0.4f, 3f)
                val height = basePageWidth.coerceAtLeast(1f) * aspect
                heights[page] = height
                geometryGenerations[page] = pageGeometryGeneration(basePageWidth, height, rotation = 0)
                cursor += height
                if (page != count - 1) cursor += pageGap
            }
            return ContinuousDocumentGeometry(
                count,
                basePageWidth,
                pageGap,
                tops,
                heights,
                geometryGenerations,
                cursor,
            )
        }

        private const val DEFAULT_ASPECT = 1.414f
    }
}
