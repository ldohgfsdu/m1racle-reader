package com.m1racle.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuousViewportPolicyTest {
    private fun pages(primary: Int, direction: ContinuousScrollDirection, phase: ContinuousScrollPhase) =
        ContinuousViewportPolicy.previewPages(setOf(primary), primary, 20, direction, phase)

    @Test fun idle_primaryPlusTwo() = assertEquals((3..7).toSet(), pages(5, ContinuousScrollDirection.IDLE, ContinuousScrollPhase.IDLE))
    @Test fun forwardDragging_sixAheadTwoBehind() = assertEquals((3..11).toSet(), pages(5, ContinuousScrollDirection.FORWARD, ContinuousScrollPhase.DRAGGING))
    @Test fun backwardDragging_reverses() = assertEquals((0..7).toSet(), pages(5, ContinuousScrollDirection.BACKWARD, ContinuousScrollPhase.DRAGGING))
    @Test fun forwardFling_tenAheadThreeBehind() = assertEquals((2..15).toSet(), pages(5, ContinuousScrollDirection.FORWARD, ContinuousScrollPhase.FLINGING))
    @Test fun backwardFling_reverses() = assertEquals((0..13).toSet(), pages(10, ContinuousScrollDirection.BACKWARD, ContinuousScrollPhase.FLINGING))
    @Test fun clampsAtDocumentEdgesAndKeepsVisible() {
        val result = ContinuousViewportPolicy.previewPages(setOf(0, 1), 0, 3, ContinuousScrollDirection.BACKWARD, ContinuousScrollPhase.FLINGING)
        assertTrue(result.all { it in 0..2 })
        assertTrue(result.containsAll(setOf(0, 1)))
    }
    @Test fun boundedSetNeverExceedsCapPlusVisible() {
        val visible = setOf(40, 41, 42)
        val s = ContinuousViewportSnapshot(visible, 41, ContinuousScrollDirection.FORWARD, ContinuousScrollPhase.FLINGING)
        val result = ContinuousViewportPolicy.boundedPreviewPages(s, 100)
        assertTrue(result.containsAll(visible))
        assertTrue(result.size <= ContinuousViewportPolicy.MAX_RETAINED_PAGES + visible.size)
    }
    @Test fun requestOrder_visibleThenForwardNearest() {
        val s = ContinuousViewportSnapshot(setOf(5), 5, ContinuousScrollDirection.FORWARD, ContinuousScrollPhase.FLINGING)
        val order = ContinuousViewportPolicy.previewRequestOrder(s, 20)
        assertEquals(5, order.first())
        assertTrue(order.indexOf(6) < order.indexOf(4))
    }
}
