package com.m1racle.reader.ui.annotation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the two selection thresholds that are ours rather than the platform's.
 *
 * The arm window itself uses ViewConfiguration.touchSlop, the platform long-press
 * timeout and AOSP's cancel set; only the stillness window and the lift dead zone
 * are product decisions.
 */
class PdfSelectionGestureTuningTest {

    @Test
    fun stillnessWindowFitsInsideTheLongPressTimeout() {
        // ViewConfiguration.getLongPressTimeout() is 500ms on every Android version
        // we support. The window has to be shorter than that or no press could pass.
        val longPressTimeoutMs = 500L
        assertTrue(
            "ARM_STILL_MS ${PdfSelectionGestureTuning.ARM_STILL_MS} must be < $longPressTimeoutMs",
            PdfSelectionGestureTuning.ARM_STILL_MS < longPressTimeoutMs,
        )
        assertTrue(PdfSelectionGestureTuning.ARM_STILL_MS > 0L)
    }

    @Test
    fun liftDeadZoneIsSmallEnoughToFeelLikeFollowingTheFinger() {
        // The platform slop is 8dp. The dead zone stays under it so the selection
        // starts following before a scroll would have started, and stays above zero
        // so jitter cannot move it.
        val platformSlopDp = 8f
        assertTrue(
            "LIFT_DEAD_ZONE_DP ${PdfSelectionGestureTuning.LIFT_DEAD_ZONE_DP} must be < $platformSlopDp",
            PdfSelectionGestureTuning.LIFT_DEAD_ZONE_DP < platformSlopDp,
        )
        assertTrue(PdfSelectionGestureTuning.LIFT_DEAD_ZONE_DP > 0f)
    }

    @Test
    fun thresholdsKeepTheirTunedValues() {
        assertEquals(6f, PdfSelectionGestureTuning.LIFT_DEAD_ZONE_DP, 0f)
        assertEquals(120L, PdfSelectionGestureTuning.ARM_STILL_MS)
    }
}
