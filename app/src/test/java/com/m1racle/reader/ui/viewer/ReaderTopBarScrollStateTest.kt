package com.m1racle.reader.ui.viewer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic coverage for continuous top-chrome collapse and nestedScroll observe-only policy.
 */
class ReaderTopBarScrollStateTest {

    private fun state(
        maxHide: Float = 100f,
        jitter: Float = 12f,
        flingThreshold: Float = 900f,
        settleFraction: Float = 0.28f,
    ) = ReaderTopBarScrollState(
        maxHiddenOffsetPx = maxHide,
        jitterIgnorePx = jitter,
        flingVelocityThresholdPxPerSec = flingThreshold,
        settleHideFraction = settleFraction,
    )

    @Test
    fun startsFullyShown() {
        val s = state()
        assertEquals(0f, s.hiddenOffsetPx, 0.001f)
        assertEquals(0f, s.collapseFraction, 0.001f)
        assertTrue(s.visible)
    }

    @Test
    fun slowUpDrag_increasesHiddenOffsetPixelSync() {
        val s = state(maxHide = 100f)
        s.onUserDragDelta(-10f)
        assertEquals(10f, s.hiddenOffsetPx, 0.001f)
        s.onUserDragDelta(-7f)
        assertEquals(17f, s.hiddenOffsetPx, 0.001f)
        assertFalse(s.visible)
    }

    @Test
    fun slowDownDrag_decreasesHiddenOffsetPixelSync() {
        val s = state(maxHide = 100f)
        s.onUserDragDelta(-40f)
        s.onUserDragDelta(15f)
        assertEquals(25f, s.hiddenOffsetPx, 0.001f)
        s.onUserDragDelta(30f)
        assertEquals(0f, s.hiddenOffsetPx, 0.001f)
        assertTrue(s.visible)
    }

    @Test
    fun neverBelowZero() {
        val s = state(maxHide = 50f)
        s.onUserDragDelta(20f)
        s.onUserDragDelta(40f)
        assertEquals(0f, s.hiddenOffsetPx, 0.001f)
    }

    @Test
    fun neverAboveMaxHiddenOffset() {
        val s = state(maxHide = 50f)
        s.onUserDragDelta(-80f)
        assertEquals(50f, s.hiddenOffsetPx, 0.001f)
        s.onUserDragDelta(-20f)
        assertEquals(50f, s.hiddenOffsetPx, 0.001f)
    }

    @Test
    fun reverseJitter_doesNotImmediatelyFlipDirection() {
        val s = state(maxHide = 100f, jitter = 12f)
        s.onUserDragDelta(-30f)
        assertEquals(30f, s.hiddenOffsetPx, 0.001f)
        // Small reverse (show) below jitter — chrome must not move yet.
        s.onUserDragDelta(5f)
        s.onUserDragDelta(5f)
        assertEquals(30f, s.hiddenOffsetPx, 0.001f)
    }

    @Test
    fun reverseBeyondJitter_switchesDirection() {
        val s = state(maxHide = 100f, jitter = 12f)
        s.onUserDragDelta(-30f)
        s.onUserDragDelta(6f)
        s.onUserDragDelta(6f) // reverse noise hits 12 → apply this delta
        assertEquals(24f, s.hiddenOffsetPx, 0.001f)
        s.onUserDragDelta(10f)
        assertEquals(14f, s.hiddenOffsetPx, 0.001f)
    }

    @Test
    fun forceShow_zerosOffsetAndNoise() {
        val s = state(maxHide = 100f, jitter = 12f)
        s.onUserDragDelta(-40f)
        s.onUserDragDelta(5f) // noise
        s.forceShow()
        assertEquals(0f, s.hiddenOffsetPx, 0.001f)
        assertTrue(s.visible)
        // After forceShow, a small reverse does not inherit old noise.
        s.onUserDragDelta(-10f)
        assertEquals(10f, s.hiddenOffsetPx, 0.001f)
    }

    @Test
    fun reset_zerosOffsetAndAllGestureState() {
        val s = state(maxHide = 80f)
        s.onUserDragDelta(-50f)
        s.recordFlingVelocity(-1200f)
        s.reset()
        assertEquals(0f, s.hiddenOffsetPx, 0.001f)
        assertEquals(0f, s.consumePendingFlingVelocity(), 0.001f)
        s.onUserDragDelta(-20f)
        assertEquals(20f, s.hiddenOffsetPx, 0.001f)
    }

    @Test
    fun updateMaxHiddenOffset_clampsCurrentOffset() {
        val s = state(maxHide = 100f)
        s.onUserDragDelta(-90f)
        assertEquals(90f, s.hiddenOffsetPx, 0.001f)
        s.updateMaxHiddenOffsetPx(40f)
        assertEquals(40f, s.maxHiddenOffsetPx, 0.001f)
        assertEquals(40f, s.hiddenOffsetPx, 0.001f)
    }

    @Test
    fun settle_strongUpFling_hidesFully() {
        val s = state(maxHide = 100f, flingThreshold = 1200f)
        s.onUserDragDelta(-20f)
        assertEquals(100f, s.resolveSettleTarget(-1500f), 0.001f)
    }

    @Test
    fun settle_strongDownFling_showsFully() {
        val s = state(maxHide = 100f, flingThreshold = 1200f)
        s.onUserDragDelta(-80f)
        assertEquals(0f, s.resolveSettleTarget(1500f), 0.001f)
    }

    @Test
    fun settle_lowSpeed_usesFractionThreshold() {
        val s = state(maxHide = 100f, settleFraction = 0.28f)
        s.onUserDragDelta(-60f)
        assertEquals(100f, s.resolveSettleTarget(0f), 0.001f)
        s.snapTo(20f)
        assertEquals(0f, s.resolveSettleTarget(0f), 0.001f)
    }

    @Test
    fun settle_weakUpFling_finishesHideOnceCollapseStarted() {
        val s = state(maxHide = 100f, flingThreshold = 900f)
        s.onUserDragDelta(-25f)
        assertEquals(100f, s.resolveSettleTarget(-200f), 0.001f)
    }

    @Test
    fun forceShow_afterPartialCollapse_restoresFully() {
        val s = state(maxHide = 100f)
        s.onUserDragDelta(-55f)
        s.forceShow()
        assertEquals(0f, s.hiddenOffsetPx, 0.001f)
        assertEquals(0f, s.collapseFraction, 0.001f)
    }

    @Test
    fun reset_documentSwitch_startsFullyShown() {
        val s = state(maxHide = 120f)
        s.onUserDragDelta(-100f)
        s.reset()
        assertTrue(s.visible)
        assertEquals(0f, s.hiddenOffsetPx, 0.001f)
    }

    @Test
    fun nonFiniteDelta_ignored() {
        val s = state()
        s.onUserDragDelta(Float.NaN)
        s.onUserDragDelta(Float.POSITIVE_INFINITY)
        assertEquals(0f, s.hiddenOffsetPx, 0.001f)
        assertTrue(s.visible)
    }

    @Test
    fun nestedScroll_userInput_doesNotConsumeOffset() {
        val s = state(maxHide = 100f)
        val conn = s.asNestedScrollConnection()
        val result = conn.onPostScroll(
            consumed = Offset(0f, -30f),
            available = Offset.Zero,
            source = NestedScrollSource.UserInput,
        )
        assertEquals(Offset.Zero, result)
        assertEquals(30f, s.hiddenOffsetPx, 0.001f)
    }

    @Test
    fun nestedScroll_flingInertia_hidesChrome() {
        val s = state(maxHide = 100f)
        val conn = s.asNestedScrollConnection()
        val result = conn.onPostScroll(
            consumed = Offset(0f, -40f),
            available = Offset.Zero,
            source = NestedScrollSource.SideEffect,
        )
        assertEquals(Offset.Zero, result)
        assertEquals(40f, s.hiddenOffsetPx, 0.001f)
    }

    @Test
    fun onScrollIdle_clearsReverseJitter() {
        val s = state(maxHide = 100f, jitter = 12f)
        s.onUserDragDelta(-30f)
        s.onUserDragDelta(5f)
        s.onScrollIdle()
        s.onUserDragDelta(5f)
        assertEquals(25f, s.hiddenOffsetPx, 0.001f)
    }

    @Test
    fun nestedScroll_usesAvailableWhenConsumedZero() {
        val s = state(maxHide = 100f)
        val conn = s.asNestedScrollConnection()
        conn.onPostScroll(
            consumed = Offset.Zero,
            available = Offset(0f, -25f),
            source = NestedScrollSource.UserInput,
        )
        assertEquals(25f, s.hiddenOffsetPx, 0.001f)
    }

    @Test
    fun nestedScroll_postFling_recordsVelocityWithoutConsuming() = runBlocking {
        val s = state(maxHide = 100f, flingThreshold = 1200f)
        s.onUserDragDelta(-40f)
        val conn = s.asNestedScrollConnection()
        val result = conn.onPostFling(
            consumed = Velocity.Zero,
            available = Velocity(0f, -1500f),
        )
        assertEquals(Velocity.Zero, result)
        assertEquals(100f, s.resolveSettleTarget(s.consumePendingFlingVelocity()), 0.001f)
    }

    @Test
    fun sameDirection_continuesWithoutJitterGate() {
        val s = state(maxHide = 100f, jitter = 12f)
        repeat(8) { s.onUserDragDelta(-5f) }
        assertEquals(40f, s.hiddenOffsetPx, 0.001f)
    }

    @Test
    fun collapseFraction_tracksOffset() {
        val s = state(maxHide = 80f)
        s.onUserDragDelta(-40f)
        assertEquals(0.5f, s.collapseFraction, 0.001f)
    }
}
