package com.markleaf.app.ui.viewer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic coverage for thresholded top-bar show/hide and nestedScroll observe-only policy.
 */
class ReaderTopBarScrollStateTest {

    private var nowMs = 1_000_000L

    private fun state(
        hide: Float = 32f,
        show: Float = 88f,
        jitter: Float = 12f,
        cooldownMs: Long = 0L,
    ) = ReaderTopBarScrollState(
        hideThresholdPx = hide,
        showThresholdPx = show,
        jitterIgnorePx = jitter,
        cooldownMs = cooldownMs,
        uptimeMillis = { nowMs },
    )

    @Test
    fun startsVisible() {
        assertTrue(state().visible)
    }

    @Test
    fun belowHideThreshold_stateUnchanged() {
        val s = state(hide = 32f)
        s.onUserDragDelta(-20f)
        assertTrue(s.visible)
    }

    @Test
    fun dragDownPastThreshold_hidesOnce() {
        val s = state()
        s.onUserDragDelta(-20f)
        assertTrue(s.visible)
        s.onUserDragDelta(-20f)
        assertFalse(s.visible)
        // Further continuous down deltas must not re-toggle or thrash.
        s.onUserDragDelta(-40f)
        s.onUserDragDelta(-40f)
        assertFalse(s.visible)
    }

    @Test
    fun continuousDownDeltas_doNotRestartHideAfterFlip() {
        val s = state(hide = 30f, cooldownMs = 0L)
        s.onUserDragDelta(-30f)
        assertFalse(s.visible)
        // Same direction after hide: still hidden, single flip only.
        repeat(20) { s.onUserDragDelta(-15f) }
        assertFalse(s.visible)
    }

    @Test
    fun slowDownDrag_accumulatesSubJitterDeltasAndHides() {
        val s = state(hide = 32f, jitter = 12f)
        repeat(3) { s.onUserDragDelta(-8f) }
        assertTrue(s.visible)
        s.onUserDragDelta(-8f)
        assertFalse(s.visible)
    }

    @Test
    fun slowUpDrag_accumulatesSubJitterDeltasAndShows() {
        val s = state(hide = 32f, show = 88f, jitter = 12f)
        repeat(4) { s.onUserDragDelta(-8f) }
        assertFalse(s.visible)
        repeat(10) { s.onUserDragDelta(8f) }
        assertFalse(s.visible)
        s.onUserDragDelta(8f)
        assertTrue(s.visible)
    }

    @Test
    fun reversalBelowJitter_doesNotClearHideProgress() {
        val s = state(hide = 30f, jitter = 12f)
        s.onUserDragDelta(-20f)
        s.onUserDragDelta(5f)
        s.onUserDragDelta(-10f)
        assertFalse(s.visible)
    }

    @Test
    fun accumulatedReversalAtJitter_clearsHideProgress() {
        val s = state(hide = 30f, jitter = 12f)
        s.onUserDragDelta(-20f)
        s.onUserDragDelta(6f)
        s.onUserDragDelta(6f)
        s.onUserDragDelta(-20f)
        assertTrue(s.visible)
        s.onUserDragDelta(-10f)
        assertFalse(s.visible)
    }

    @Test
    fun dragUpPastThreshold_showsOnceAfterHide() {
        val s = state()
        s.onUserDragDelta(-40f)
        assertFalse(s.visible)
        s.onUserDragDelta(50f)
        assertFalse(s.visible)
        s.onUserDragDelta(50f)
        assertTrue(s.visible)
        // Further continuous up deltas must not re-toggle.
        s.onUserDragDelta(50f)
        assertTrue(s.visible)
    }

    @Test
    fun reverseWhileAccumulatingUp_clearsShowCounter() {
        val s = state(hide = 30f, show = 40f)
        s.onUserDragDelta(-30f)
        assertFalse(s.visible)
        s.onUserDragDelta(30f) // accumulate toward show, not enough
        assertFalse(s.visible)
        s.onUserDragDelta(-20f) // reverse down → clear up accumulator
        assertFalse(s.visible)
        s.onUserDragDelta(30f) // must re-accumulate from zero
        assertFalse(s.visible)
        s.onUserDragDelta(20f)
        assertTrue(s.visible)
    }

    @Test
    fun forceShow_restoresVisibleAndClearsAccumulators() {
        val s = state()
        s.onUserDragDelta(-40f)
        assertFalse(s.visible)
        s.forceShow()
        assertTrue(s.visible)
        // After forceShow, need full threshold again to hide.
        s.onUserDragDelta(-20f)
        assertTrue(s.visible)
    }

    @Test
    fun forceShow_whenAlreadyVisible_stillClearsAccumulators() {
        val s = state(hide = 30f)
        s.onUserDragDelta(-20f) // partial hide accumulate
        s.forceShow()
        s.onUserDragDelta(-20f)
        assertTrue(s.visible) // partial was cleared
        s.onUserDragDelta(-30f)
        assertFalse(s.visible)
    }

    @Test
    fun reset_restoresVisibleAndClearsAll() {
        val s = state()
        s.onUserDragDelta(-40f)
        assertFalse(s.visible)
        s.reset()
        assertTrue(s.visible)
        // Fresh document: full hide threshold required again.
        s.onUserDragDelta(-20f)
        assertTrue(s.visible)
        s.onUserDragDelta(-20f)
        assertFalse(s.visible)
    }

    @Test
    fun cooldown_blocksImmediateRetrigger() {
        val s = state(hide = 30f, show = 30f, cooldownMs = 250L)
        s.onUserDragDelta(-30f)
        assertFalse(s.visible)
        nowMs += 100L
        s.onUserDragDelta(30f)
        assertFalse(s.visible) // still in cooldown
        nowMs += 200L
        s.onUserDragDelta(30f)
        assertTrue(s.visible)
    }

    @Test
    fun nonFiniteDelta_ignored() {
        val s = state()
        s.onUserDragDelta(Float.NaN)
        s.onUserDragDelta(Float.POSITIVE_INFINITY)
        assertTrue(s.visible)
    }

    @Test
    fun nestedScroll_userInput_doesNotConsumeOffset() {
        val s = state(hide = 30f)
        val conn = s.asNestedScrollConnection()
        val result = conn.onPostScroll(
            consumed = Offset(0f, -30f),
            available = Offset.Zero,
            source = NestedScrollSource.UserInput,
        )
        assertEquals(Offset.Zero, result)
        assertFalse(s.visible)
    }

    @Test
    fun nestedScroll_nonUserInput_ignored() {
        val s = state(hide = 10f)
        val conn = s.asNestedScrollConnection()
        // Fling / side-effect sources must not toggle.
        val result = conn.onPostScroll(
            consumed = Offset(0f, -100f),
            available = Offset.Zero,
            source = NestedScrollSource.SideEffect,
        )
        assertEquals(Offset.Zero, result)
        assertTrue(s.visible)
    }

    @Test
    fun nestedScroll_usesAvailableWhenConsumedZero() {
        val s = state(hide = 30f)
        val conn = s.asNestedScrollConnection()
        conn.onPostScroll(
            consumed = Offset.Zero,
            available = Offset(0f, -30f),
            source = NestedScrollSource.UserInput,
        )
        assertFalse(s.visible)
    }
}
