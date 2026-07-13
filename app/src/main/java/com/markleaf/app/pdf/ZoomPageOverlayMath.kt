package com.markleaf.app.pdf

import kotlin.math.max
import kotlin.math.min

data class OverlayRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

data class OverlayOffset(val x: Float, val y: Float)

/**
 * Immutable snapshot captured at pinch start for hit-testing.
 * [bounds] are in viewport coordinates (same space as [calculateCentroid]).
 */
data class VisiblePageTarget(
    val pageIndex: Int,
    val bounds: OverlayRect,
)

data class ZoomLockSession(
    val primaryPage: Int,
    val entryListIndex: Int,
    val entryListOffset: Int,
    val scale: Float = 1f,
    val offset: OverlayOffset = OverlayOffset(0f, 0f),
    val bitmapIdentity: Long,
)

data class ContinuousZoomSession(
    val active: Boolean,
    val lockedPage: Int?,
    val scale: Float,
    val offset: OverlayOffset,
    val lockedBounds: OverlayRect,
    val gestureActive: Boolean,
    val exitRequested: Boolean,
    val sessionId: Long,
    val entryListIndex: Int,
    val entryListOffset: Int,
) {
    companion object {
        fun inactive(sessionId: Long = 0L) = ContinuousZoomSession(
            active = false,
            lockedPage = null,
            scale = 1f,
            offset = OverlayOffset(0f, 0f),
            lockedBounds = OverlayRect(0f, 0f, 0f, 0f),
            gestureActive = false,
            exitRequested = false,
            sessionId = sessionId,
            entryListIndex = 0,
            entryListOffset = 0,
        )
    }
}

object ContinuousZoomSessionReducer {
    fun begin(
        previous: ContinuousZoomSession,
        page: Int,
        bounds: OverlayRect,
        listIndex: Int,
        listOffset: Int,
    ): ContinuousZoomSession = if (previous.active) previous.copy(gestureActive = true) else
        ContinuousZoomSession(
            active = true,
            lockedPage = page,
            scale = 1f,
            offset = OverlayOffset(0f, 0f),
            lockedBounds = bounds,
            gestureActive = true,
            exitRequested = false,
            sessionId = previous.sessionId + 1L,
            entryListIndex = listIndex,
            entryListOffset = listOffset,
        )

    fun updateTransform(
        session: ContinuousZoomSession,
        scale: Float,
        offset: OverlayOffset,
    ): ContinuousZoomSession {
        if (!session.active) return session
        val clampedScale = scale.coerceIn(1f, ZoomPageOverlayMath.MAX_SCALE)
        val explicitlyShrunkToFit = session.scale > ZoomPageOverlayMath.EXIT_SCALE &&
            clampedScale <= ZoomPageOverlayMath.EXIT_SCALE
        return session.copy(
            scale = clampedScale,
            offset = offset,
            exitRequested = session.exitRequested || explicitlyShrunkToFit,
        )
    }

    /** Finger release persists transform; only an explicit shrink request exits. */
    fun gestureEnd(session: ContinuousZoomSession): ContinuousZoomSession =
        if (session.exitRequested) exit(session) else session.copy(gestureActive = false)

    fun exit(session: ContinuousZoomSession): ContinuousZoomSession =
        ContinuousZoomSession.inactive(session.sessionId)

    fun bottomContentVisible(session: ContinuousZoomSession, page: Int): Boolean =
        !session.active || session.lockedPage != page

    fun overlayCount(session: ContinuousZoomSession): Int = if (session.active) 1 else 0
}

enum class PageTap { NONE, PREVIOUS, NEXT }

object ZoomPageOverlayMath {
    const val ACTIVE_MIN_SCALE = 1f
    const val EXIT_SCALE = 1.01f
    const val MAX_SCALE = 3f

    fun targetScale(current: Float, zoomChange: Float): Float =
        (current * zoomChange).coerceIn(ACTIVE_MIN_SCALE, MAX_SCALE)

    fun anchoredOffset(
        oldScale: Float,
        newScale: Float,
        oldOffset: OverlayOffset,
        centroidX: Float,
        centroidY: Float,
        page: OverlayRect,
        panX: Float = 0f,
        panY: Float = 0f,
    ): OverlayOffset {
        val ratio = newScale / oldScale.coerceAtLeast(0.01f)
        return OverlayOffset(
            x = centroidX - page.left -
                (centroidX - page.left - oldOffset.x) * ratio + panX,
            y = centroidY - page.top -
                (centroidY - page.top - oldOffset.y) * ratio + panY,
        )
    }

    fun clampOffset(
        scale: Float,
        desired: OverlayOffset,
        page: OverlayRect,
        viewportWidth: Float,
        viewportHeight: Float,
    ): OverlayOffset = OverlayOffset(
        x = clampAxis(desired.x, page.left, page.width * scale, viewportWidth),
        y = clampAxis(desired.y, page.top, page.height * scale, viewportHeight),
    )

    private fun clampAxis(offset: Float, origin: Float, scaledSize: Float, viewportSize: Float): Float {
        if (scaledSize <= viewportSize) {
            val centered = (viewportSize - scaledSize) / 2f - origin
            return offset.coerceIn(min(0f, centered), max(0f, centered))
        }
        return offset.coerceIn(viewportSize - origin - scaledSize, -origin)
    }

    /**
     * Hit-test a ReadingViewport-local centroid against ReadingViewport-local page bounds.
     * Gaps use the nearest visible page; null means no valid bounds snapshot exists yet.
     */
    fun hitTestPage(
        centroidX: Float,
        centroidY: Float,
        candidates: List<VisiblePageTarget>,
    ): Int? {
        if (candidates.isEmpty()) return null
        val exact = candidates.firstOrNull { target ->
            centroidX >= target.bounds.left && centroidX <= target.bounds.left + target.bounds.width &&
                centroidY >= target.bounds.top && centroidY <= target.bounds.top + target.bounds.height
        }
        if (exact != null) return exact.pageIndex
        return candidates.minByOrNull { target ->
            val centerX = target.bounds.left + target.bounds.width / 2f
            val centerY = target.bounds.top + target.bounds.height / 2f
            val dx = centroidX - centerX
            val dy = centroidY - centerY
            dx * dx + dy * dy
        }?.pageIndex
    }

    /** Compatibility helper for vertical-only tests/callers. */
    fun hitTestPage(centroidY: Float, candidates: List<VisiblePageTarget>): Int? =
        hitTestPage(0f, centroidY, candidates)

    fun replaceBitmap(session: ZoomLockSession, bitmapIdentity: Long): ZoomLockSession =
        session.copy(bitmapIdentity = bitmapIdentity)

    fun fitCenter(viewportWidth: Float, viewportHeight: Float, aspectHeightWidth: Float): OverlayRect {
        val contentHeightAtFullWidth = viewportWidth * aspectHeightWidth
        val width = if (contentHeightAtFullWidth <= viewportHeight) viewportWidth
        else viewportHeight / aspectHeightWidth
        val height = width * aspectHeightWidth
        return OverlayRect(
            left = (viewportWidth - width) / 2f,
            top = (viewportHeight - height) / 2f,
            width = width,
            height = height,
        )
    }

    fun pageTap(x: Float, width: Float, pagerMoving: Boolean, scale: Float): PageTap {
        if (pagerMoving || scale > EXIT_SCALE || width <= 0f) return PageTap.NONE
        return when {
            x < width * 0.14f -> PageTap.PREVIOUS
            x > width * 0.86f -> PageTap.NEXT
            else -> PageTap.NONE
        }
    }
}

object PagerRequestPolicy {
    fun shouldConsume(requestId: Long?, consumedRequestId: Long?): Boolean =
        requestId != null && requestId != consumedRequestId

    fun shouldPublishSettled(settledPage: Int, viewModelPage: Int): Boolean =
        settledPage != viewModelPage
}
