package com.markleaf.app.pdf

object ContinuousZoomHandoff {
    fun effectiveCommitted(committed: Float, pending: Float?): Float = pending ?: committed

    fun displayedScale(committed: Float, pending: Float?, gestureScale: Float): Float =
        PdfRenderQuality.clampZoom(effectiveCommitted(committed, pending) * gestureScale)

    fun target(committed: Float, pending: Float?, gestureScale: Float): Float =
        displayedScale(committed, pending, gestureScale)

    fun confirmed(committed: Float, pending: Float?, epsilon: Float = 0.001f): Boolean =
        pending != null && kotlin.math.abs(committed - pending) < epsilon
}
