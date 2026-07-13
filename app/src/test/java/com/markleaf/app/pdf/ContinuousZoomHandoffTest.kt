package com.markleaf.app.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuousZoomHandoffTest {
    @Test fun pendingBridgeHasNoOldCommittedFrame() {
        val target = ContinuousZoomHandoff.target(1f, null, 2.5f)
        assertEquals(2.5f, ContinuousZoomHandoff.displayedScale(1f, target, 1f))
    }
    @Test fun confirmationClearsWithoutChangingDisplay() {
        val before = ContinuousZoomHandoff.displayedScale(1f, 2.5f, 1f)
        assertTrue(ContinuousZoomHandoff.confirmed(2.5f, 2.5f))
        assertEquals(before, ContinuousZoomHandoff.displayedScale(2.5f, null, 1f))
    }
    @Test fun newGestureUsesPendingAsBaseAndClamps() {
        assertEquals(3f, ContinuousZoomHandoff.target(1f, 2.5f, 2f))
        assertEquals(1f, ContinuousZoomHandoff.target(2f, null, 0.1f))
    }

    @Test fun reversePinchUsesSamePendingCommitPath() {
        assertEquals(1.5f, ContinuousZoomHandoff.target(3f, null, 0.5f))
        assertEquals(1.25f, ContinuousZoomHandoff.target(3f, 2.5f, 0.5f))
    }
}
