package com.markleaf.app.ui.viewer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlin.math.abs

/**
 * Thresholded TopAppBar show/hide — NestedScrollSource.UserInput drag only.
 *
 * - Drag down ≥ hideThreshold → hide
 * - Continuous drag up ≥ showThreshold (~88dp) → show
 * - Reversing down while accumulating up clears up counter
 * - |dy| < jitterIgnore ignored (bounce / noise)
 * - 250ms cooldown after each state flip
 * - Fling / postFling / SideEffect never show or hide
 */
class ReaderTopBarScrollState(
    private val hideThresholdPx: Float,
    private val showThresholdPx: Float,
    private val jitterIgnorePx: Float,
    private val cooldownMs: Long = 250L,
) {
    var visible by mutableStateOf(true)
        private set

    private var accumulatedDownScroll = 0f
    private var accumulatedUpScroll = 0f
    private var lastToggleUptimeMs = 0L

    fun forceShow() {
        if (!visible) {
            visible = true
            lastToggleUptimeMs = android.os.SystemClock.uptimeMillis()
        }
        accumulatedDownScroll = 0f
        accumulatedUpScroll = 0f
    }

    fun onUserDragDelta(dy: Float) {
        if (!dy.isFinite()) return
        if (abs(dy) < jitterIgnorePx) return

        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastToggleUptimeMs < cooldownMs) return

        if (dy < 0f) {
            accumulatedUpScroll = 0f
            if (visible) {
                accumulatedDownScroll += -dy
                if (accumulatedDownScroll >= hideThresholdPx) {
                    visible = false
                    accumulatedDownScroll = 0f
                    lastToggleUptimeMs = now
                }
            }
        } else {
            accumulatedDownScroll = 0f
            if (!visible) {
                accumulatedUpScroll += dy
                if (accumulatedUpScroll >= showThresholdPx) {
                    visible = true
                    accumulatedUpScroll = 0f
                    lastToggleUptimeMs = now
                }
            }
        }
    }

    fun asNestedScrollConnection(): NestedScrollConnection =
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput) {
                    return Offset.Zero
                }
                val dy = if (consumed.y != 0f) consumed.y else available.y
                onUserDragDelta(dy)
                return Offset.Zero
            }
        }
}
