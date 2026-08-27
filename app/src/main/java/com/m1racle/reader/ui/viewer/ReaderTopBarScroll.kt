package com.m1racle.reader.ui.viewer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs

/**
 * Continuous TopChrome collapse driven by NestedScrollSource.UserInput drag only.
 *
 * Design goals (R9 chrome recovery + X-style immersive collapse):
 * - Observe scroll; never consume body deltas (always return [Offset.Zero] / zero velocity).
 * - Same-direction user deltas move [hiddenOffsetPx] 1:1 (pixel-sync with the finger).
 * - Small reverse deltas accumulate as reverse noise; only after [jitterIgnorePx] does direction
 *   switch and start moving the chrome the other way (R9 reverse-jitter semantics).
 * - Fling / postFling never move chrome mid-stream; velocity is recorded for settle only.
 * - UI-layer only: no ViewModel / DB / SavedState writes.
 *
 * Drag conventions (Compose nested scroll):
 * - Content moves up (user scrolls down the document) → negative [dy] → hide chrome
 * - Content moves down (user scrolls up the document) → positive [dy] → show chrome
 */
class ReaderTopBarScrollState(
    maxHiddenOffsetPx: Float,
    private val jitterIgnorePx: Float,
    /**
     * Absolute fling velocity (px/s) above which settle prefers fully open/closed by direction
     * instead of collapse fraction.
     */
    /**
     * Higher than a low X-like flick so medium releases prefer fraction settle over
     * hard velocity snap (reduces "magnet" feel while keeping decisive flings).
     */
    private val flingVelocityThresholdPxPerSec: Float = 900f,
    /** Fraction at which low-speed settle prefers fully hidden. */
    private val settleHideFraction: Float = 0.28f,
) {
    /**
     * Total chrome height that must leave the screen: status bar inset + top bar (+ divider).
     * Updated when density / orientation / insets change.
     */
    var maxHiddenOffsetPx by mutableFloatStateOf(maxHiddenOffsetPx.coerceAtLeast(0f))
        private set

    /**
     * Continuous hide distance in px. `0` = fully shown; [maxHiddenOffsetPx] = fully hidden.
     */
    var hiddenOffsetPx by mutableFloatStateOf(0f)
        private set

    val collapseFraction: Float
        get() {
            val max = maxHiddenOffsetPx
            if (max <= 0f) return 0f
            return (hiddenOffsetPx / max).coerceIn(0f, 1f)
        }

    /**
     * Fully shown chrome. Kept for popup dismiss / forceShow call sites that only need a boolean.
     * Continuous motion uses [hiddenOffsetPx] / [collapseFraction].
     */
    val visible: Boolean
        get() = hiddenOffsetPx <= 0.5f

    /** +1 last committed motion hides chrome; -1 shows; 0 none yet this gesture chain. */
    private var committedSign = 0
    private var reverseNoisePx = 0f

    /** Last observed fling velocity Y (content direction); consumed by settle. */
    private var pendingFlingVelocityY = 0f

    fun updateMaxHiddenOffsetPx(maxPx: Float) {
        val clampedMax = maxPx.coerceAtLeast(0f)
        if (clampedMax == maxHiddenOffsetPx) {
            hiddenOffsetPx = hiddenOffsetPx.coerceIn(0f, clampedMax)
            return
        }
        maxHiddenOffsetPx = clampedMax
        hiddenOffsetPx = hiddenOffsetPx.coerceIn(0f, clampedMax)
    }

    /**
     * Force the chrome fully shown and clear accumulators / fling residue.
     * Used at document top (settled), search mode, TOC/anchor jumps.
     */
    fun forceShow() {
        hiddenOffsetPx = 0f
        clearGestureNoise()
        pendingFlingVelocityY = 0f
    }

    /**
     * Reset to initial fully-shown state (e.g. document switch).
     */
    fun reset() {
        hiddenOffsetPx = 0f
        clearGestureNoise()
        pendingFlingVelocityY = 0f
    }

    /**
     * Snap chrome to an absolute offset (settle animation frames / tests).
     */
    fun snapTo(offsetPx: Float) {
        hiddenOffsetPx = offsetPx.coerceIn(0f, maxHiddenOffsetPx)
    }

    /**
     * Apply one user-drag delta in pixels (Compose nested-scroll Y).
     */
    fun onUserDragDelta(dy: Float) {
        if (!dy.isFinite() || dy == 0f || maxHiddenOffsetPx <= 0f) return

        // wantSign +1: hide more; -1: show more
        val wantSign = if (dy < 0f) 1 else -1
        val amount = abs(dy)

        if (committedSign == 0 || wantSign == committedSign) {
            committedSign = wantSign
            reverseNoisePx = 0f
            applySignedDelta(wantSign, amount)
            return
        }

        // Reverse against committed direction: tolerate small jitter without flipping chrome.
        reverseNoisePx += amount
        if (reverseNoisePx >= jitterIgnorePx) {
            committedSign = wantSign
            reverseNoisePx = 0f
            applySignedDelta(wantSign, amount)
        }
    }

    /**
     * Record fling velocity for settle (does not move chrome).
     * [velocityY] uses Compose nested-scroll convention (content motion).
     */
    fun recordFlingVelocity(velocityY: Float) {
        if (velocityY.isFinite()) {
            pendingFlingVelocityY = velocityY
        }
    }

    fun consumePendingFlingVelocity(): Float {
        val v = pendingFlingVelocityY
        pendingFlingVelocityY = 0f
        return v
    }

    /** Call when the list reports idle, before settle — drop reverse-jitter residue. */
    fun onScrollIdle() {
        clearGestureNoise()
    }

    /**
     * Target offset after finger-up / fling end.
     *
     * - Strong content-up fling (velocityY negative, large) → fully hide
     * - Strong content-down fling → fully show
     * - Weak up-fling that already started collapsing → finish hiding
     * - Low speed → fraction threshold
     */
    fun resolveSettleTarget(velocityY: Float = consumePendingFlingVelocity()): Float {
        val max = maxHiddenOffsetPx
        if (max <= 0f) return 0f
        if (!velocityY.isFinite()) {
            return if (collapseFraction >= settleHideFraction) max else 0f
        }
        if (velocityY <= -flingVelocityThresholdPxPerSec) return max
        if (velocityY >= flingVelocityThresholdPxPerSec) return 0f
        if (velocityY < 0f && collapseFraction >= 0.18f) return max
        if (velocityY > 0f) return 0f
        return if (collapseFraction >= settleHideFraction) max else 0f
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
                // UserInput = finger; SideEffect = fling/inertia. Ignoring inertia left
                // the bar half-open after a flick, then settle snapped it back to shown.
                if (source != NestedScrollSource.UserInput &&
                    source != NestedScrollSource.SideEffect
                ) {
                    return Offset.Zero
                }
                // Prefer consumed (list actually moved); fall back to available for edge overscroll.
                val dy = if (consumed.y != 0f) consumed.y else available.y
                onUserDragDelta(dy)
                return Offset.Zero
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity {
                val vy = when {
                    available.y != 0f -> available.y
                    else -> consumed.y
                }
                recordFlingVelocity(vy)
                return Velocity.Zero
            }
        }

    private fun applySignedDelta(sign: Int, amount: Float) {
        if (sign > 0) {
            hiddenOffsetPx = (hiddenOffsetPx + amount).coerceIn(0f, maxHiddenOffsetPx)
        } else {
            hiddenOffsetPx = (hiddenOffsetPx - amount).coerceIn(0f, maxHiddenOffsetPx)
        }
    }

    private fun clearGestureNoise() {
        committedSign = 0
        reverseNoisePx = 0f
    }
}
