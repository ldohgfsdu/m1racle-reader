package com.m1racle.reader.pdf

/**
 * R25 closure: the legacy zoom-lock overlay math (ContinuousZoomSession /
 * ZoomLockSession / hitTestPage / targetScale / anchoredOffset / clampOffset /
 * fitCenter / replaceBitmap) was deleted together with the legacy
 * PdfVerticalZoomLockArea path — the continuous viewport owns its own
 * transform math. Only the horizontal tap-zone mapping, the one-shot pager
 * request policy and the shared viewport-coordinate rect remain here.
 */

/** Rect in viewport coordinates (used by the continuous document geometry). */
data class OverlayRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

/** Horizontal-mode page-tap zones and pager request policy. */
enum class PageTap { NONE, PREVIOUS, NEXT }

object ZoomPageOverlayMath {
    const val EXIT_SCALE = 1.01f

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
