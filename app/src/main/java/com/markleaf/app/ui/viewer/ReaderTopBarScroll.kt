package com.markleaf.app.ui.viewer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

/**
 * Thresholded TopAppBar show/hide driven by NestedScrollSource.UserInput drag only.
 *
 * Design goals (R9 markdown chrome recovery):
 * - Observe scroll; never consume body deltas (always return [Offset.Zero]).
 * - Hysteresis: hide and show use separate thresholds so small reversals do not flicker.
 * - Small accumulated reversals below [jitterIgnorePx] do not reset progress.
 * - Cooldown after each flip suppresses animation/task thrash during fling bounce.
 * - Fling / postFling never toggle (UserInput only).
 * - UI-layer only: no ViewModel / DB / SavedState writes.
 *
 * Drag conventions (Compose nested scroll):
 * - Content moves up (user scrolls down the document) → negative [dy]
 * - Content moves down (user scrolls up the document) → positive [dy]
 */
class ReaderTopBarScrollState(
    private val hideThresholdPx: Float,
    private val showThresholdPx: Float,
    private val jitterIgnorePx: Float,
    private val cooldownMs: Long = 250L,
    /** Injected for unit tests; production uses [android.os.SystemClock.uptimeMillis]. */
    private val uptimeMillis: () -> Long = { android.os.SystemClock.uptimeMillis() },
) {
    var visible by mutableStateOf(true)
        private set

    private var accumulatedDownScroll = 0f
    private var accumulatedUpScroll = 0f
    private var accumulatedReverseScroll = 0f
    private var lastToggleUptimeMs = 0L

    /**
     * Force the bar visible and clear accumulators.
     * Used at document top (settled), search mode, TOC/anchor jumps.
     */
    fun forceShow() {
        if (!visible) {
            visible = true
            lastToggleUptimeMs = uptimeMillis()
        }
        accumulatedDownScroll = 0f
        accumulatedUpScroll = 0f
        accumulatedReverseScroll = 0f
    }

    /**
     * Reset to initial visible state (e.g. document switch).
     * Always clears accumulators and cooldown so the next document does not
     * inherit a hidden bar or mid-gesture counters.
     */
    fun reset() {
        visible = true
        accumulatedDownScroll = 0f
        accumulatedUpScroll = 0f
        accumulatedReverseScroll = 0f
        lastToggleUptimeMs = 0L
    }

    /**
     * Apply one user-drag delta in pixels (Compose nested-scroll Y).
     * Idempotent after threshold: further same-direction deltas do not re-toggle.
     */
    fun onUserDragDelta(dy: Float) {
        if (!dy.isFinite() || dy == 0f) return

        val now = uptimeMillis()
        if (now - lastToggleUptimeMs < cooldownMs) return

        if (visible) {
            if (dy < 0f) {
                // Every same-direction delta counts, including slow 120 Hz drags.
                accumulatedReverseScroll = 0f
                accumulatedDownScroll += -dy
                if (accumulatedDownScroll >= hideThresholdPx) {
                    visible = false
                    accumulatedDownScroll = 0f
                    accumulatedUpScroll = 0f
                    accumulatedReverseScroll = 0f
                    lastToggleUptimeMs = now
                }
            } else {
                // Do not let a tiny hand tremor erase meaningful hide progress.
                accumulatedReverseScroll += dy
                if (accumulatedReverseScroll >= jitterIgnorePx) {
                    accumulatedDownScroll = 0f
                    accumulatedReverseScroll = 0f
                }
            }
        } else {
            if (dy > 0f) {
                // Every same-direction delta counts, including slow 120 Hz drags.
                accumulatedReverseScroll = 0f
                accumulatedUpScroll += dy
                if (accumulatedUpScroll >= showThresholdPx) {
                    visible = true
                    accumulatedDownScroll = 0f
                    accumulatedUpScroll = 0f
                    accumulatedReverseScroll = 0f
                    lastToggleUptimeMs = now
                }
            } else {
                // Do not let a tiny hand tremor erase meaningful show progress.
                accumulatedReverseScroll += -dy
                if (accumulatedReverseScroll >= jitterIgnorePx) {
                    accumulatedUpScroll = 0f
                    accumulatedReverseScroll = 0f
                }
            }
        }
    }

    /**
     * Nested scroll connection that **observes only**.
     * Never consumes scroll distance — LazyColumn fling/inertia stay unchanged.
     */
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
                // Prefer consumed (list actually moved); fall back to available for edge overscroll.
                val dy = if (consumed.y != 0f) consumed.y else available.y
                onUserDragDelta(dy)
                return Offset.Zero
            }
        }
}
