package com.m1racle.reader.ui.annotation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the two selection thresholds that deliberately deviate from the platform.
 *
 * These values are a product decision, not physics: the arm window must be
 * tighter than the 8dp scroll slop, and the lift dead zone must be larger than
 * it would be at zero. A change here changes how the reader feels on device, so
 * it should be a deliberate edit, not a drive-by.
 */
class PdfSelectionGestureTuningTest {

    @Test
    fun armWindowIsStricterThanThePlatformScrollSlop() {
        // ViewConfiguration.touchSlop is 8dp on every Android version we support.
        val platformSlopDp = 8f
        assertTrue(
            "arm slop ${PdfSelectionGestureTuning.ARM_SLOP_DP}dp must stay below ${platformSlopDp}dp",
            PdfSelectionGestureTuning.ARM_SLOP_DP < platformSlopDp,
        )
    }

    @Test
    fun liftDeadZoneExceedsTheArmWindow() {
        assertTrue(
            "the finger must clear the arm box before the selection follows it",
            PdfSelectionGestureTuning.LIFT_DEAD_ZONE_DP > PdfSelectionGestureTuning.ARM_SLOP_DP,
        )
    }

    @Test
    fun thresholdsKeepTheirTunedValues() {
        assertEquals(4f, PdfSelectionGestureTuning.ARM_SLOP_DP, 0f)
        assertEquals(6f, PdfSelectionGestureTuning.LIFT_DEAD_ZONE_DP, 0f)
    }
}
