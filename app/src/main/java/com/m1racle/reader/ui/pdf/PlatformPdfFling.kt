package com.m1racle.reader.ui.pdf

import android.content.Context
import android.os.SystemClock
import android.view.ViewConfiguration
import android.widget.OverScroller
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Result from Android's density/friction-aware spline fling. */
internal data class PlatformPdfFlingResult(
    val releaseVelocityPxPerSecond: Offset,
    /** Position emitted by Android's spline before document-bound clipping. */
    val splinePositionPx: Offset,
    /** Final position planned by OverScroller at fling start. */
    val plannedFinalPositionPx: Offset,
    /** Position actually committed to the visible transform after clipping. */
    val displayedPositionPx: Offset,
    val durationMs: Long,
    val interrupted: Boolean,
    val terminatedByBounds: Boolean,
)

/**
 * The one post-release motion implementation shared by both PDF viewport adapters.
 *
 * Pointer-down movement never passes through this class. [OverScroller] supplies Android's
 * platform spline, density and scroll friction; the adapter only clamps each produced delta to
 * its current document bounds.
 */
internal class PlatformPdfFlingRunner(context: Context) {
    private val appContext = context.applicationContext
    val minimumFlingVelocityPxPerSecond: Float =
        ViewConfiguration.get(appContext).scaledMinimumFlingVelocity.toFloat()

    suspend fun run(
        releaseVelocityPxPerSecond: Offset,
        isCurrent: () -> Boolean,
        applyDelta: (Offset) -> Offset,
    ): PlatformPdfFlingResult {
        val startedAt = SystemClock.uptimeMillis()
        val scalarSplineSpeed = hypot(
            releaseVelocityPxPerSecond.x.toDouble(),
            releaseVelocityPxPerSecond.y.toDouble(),
        ).toFloat()
        val direction = if (scalarSplineSpeed > 0f) {
            Offset(
                releaseVelocityPxPerSecond.x / scalarSplineSpeed,
                releaseVelocityPxPerSecond.y / scalarSplineSpeed,
            )
        } else Offset.Zero
        val scroller = OverScroller(appContext)
        // Drive one platform-list spline and project its scalar distance back onto the release
        // vector. Some vendor OverScroller builds apply materially different X/Y coefficients;
        // a scalar native-list curve keeps continuous and horizontal short strokes equivalent
        // without introducing a custom decay constant.
        scroller.fling(
            0,
            0,
            0,
            scalarSplineSpeed.roundToInt(),
            0,
            0,
            0,
            MAX_SPLINE_DISTANCE_PX,
        )
        val plannedFinalPosition = Offset(
            direction.x * scroller.finalY,
            direction.y * scroller.finalY,
        )
        var previousDistance = 0
        var splinePosition = Offset.Zero
        var displayedPosition = Offset.Zero
        var interrupted = false
        var terminatedByBounds = false
        while (!scroller.isFinished) {
            withFrameNanos { }
            if (!isCurrent()) {
                interrupted = true
                scroller.abortAnimation()
                break
            }
            if (!scroller.computeScrollOffset()) break
            val scalarDelta = scroller.currY - previousDistance
            val requested = Offset(
                direction.x * scalarDelta,
                direction.y * scalarDelta,
            )
            previousDistance = scroller.currY
            splinePosition += requested
            val applied = applyDelta(requested)
            displayedPosition += applied
            val activeX = abs(requested.x) >= MIN_FRAME_DELTA_PX
            val activeY = abs(requested.y) >= MIN_FRAME_DELTA_PX
            val allActiveAxesBlocked = (activeX || activeY) &&
                (!activeX || abs(applied.x) < MIN_FRAME_DELTA_PX) &&
                (!activeY || abs(applied.y) < MIN_FRAME_DELTA_PX)
            if (allActiveAxesBlocked) {
                terminatedByBounds = true
                scroller.abortAnimation()
                break
            }
        }
        return PlatformPdfFlingResult(
            releaseVelocityPxPerSecond = releaseVelocityPxPerSecond,
            splinePositionPx = splinePosition,
            plannedFinalPositionPx = plannedFinalPosition,
            displayedPositionPx = displayedPosition,
            durationMs = SystemClock.uptimeMillis() - startedAt,
            interrupted = interrupted,
            terminatedByBounds = terminatedByBounds,
        )
    }

    private companion object {
        const val MAX_SPLINE_DISTANCE_PX = 1_000_000
        const val MIN_FRAME_DELTA_PX = 0.01f
    }
}
