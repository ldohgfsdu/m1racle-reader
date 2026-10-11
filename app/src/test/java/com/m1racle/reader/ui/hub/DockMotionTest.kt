package com.m1racle.reader.ui.hub

import org.junit.Assert.*
import org.junit.Test

class DockMotionTest {
    @Test fun `central action has no navigation destination and both sides map correctly`() {
        assertEquals(listOf(0, 1, 3, 4), (0..3).map(::dockSlotForDestination))
        assertNull(dockDestinationForSlot(2))
        assertEquals(listOf(0, 1, 2, 3), listOf(0, 1, 3, 4).map(::dockDestinationForSlot))
    }
    @Test fun `upstream signed velocity deforms both directions and remains bounded`() {
        assertEquals(0f, dockLensStretch(0f), 0f)
        for (speed in listOf(.1f, 3f, 1000f)) {
            assertEquals(-dockLensStretch(speed), dockLensStretch(-speed), 0f)
            assertTrue(dockLensStretch(speed) in 0f..1f)
        }
        assertTrue(dockLensScaleX(dockLensStretch(20f)) > 1.15f)
        assertTrue(dockLensScaleY(dockLensStretch(20f)) < .95f)
    }
    @Test fun `shape settles at rest and allows bounded spring rebound`() {
        assertEquals(1f, dockLensScaleX(0f), 0f)
        assertEquals(1f, dockLensScaleY(0f), 0f)
        assertTrue(dockLensScaleX(-.2f) < 1f)
        assertTrue(dockLensScaleY(-.2f) > 1f)
        for (strain in listOf(-100f, -.2f, 0f, 1f, 100f)) {
            assertTrue(dockLensScaleX(strain) in (1f / 1.2f)..1.251f)
            assertTrue(dockLensScaleY(strain) in .8f..1.2f)
        }
    }
    @Test fun `reference pickup lifts lens above rail with bounded rebound`() {
        assertEquals(1f, dockLensPickupX(0f), 0f)
        assertEquals(1f, dockLensPickupY(0f), 0f)
        assertEquals(78f / 56f, dockLensPickupX(1f), .001f)
        assertEquals(48f * 78f / 56f, 48f * dockLensPickupY(1f), .001f)
        assertTrue(dockLensPickupY(-.2f) < 1f)
        assertEquals(dockLensPickupY(1.3f), dockLensPickupY(100f), 0f)
        assertEquals(dockLensPickupX(-.3f), dockLensPickupX(-100f), 0f)
    }
    @Test fun `upstream panel follow is directional bounded and returns to zero`() {
        assertEquals(0f, dockPanelOffset(0f, 400f, 4f), 0f)
        assertTrue(dockPanelOffset(120f, 400f, 4f) in 0f..4f)
        assertEquals(-dockPanelOffset(120f, 400f, 4f), dockPanelOffset(-120f, 400f, 4f), 0f)
        assertEquals(4f, dockPanelOffset(900f, 400f, 4f), 0f)
        assertEquals(0f, dockPanelOffset(90f, 0f, 4f), 0f)
    }
    @Test fun `physical pointer mapping mirrors in RTL and clamps the edges`() {
        for (slot in 0..4) {
            val x = slot * 80f + 40f
            assertEquals(slot, dockSlotAt(x, 400f, false))
            assertEquals(4 - slot, dockSlotAt(x, 400f, true))
        }
        assertEquals(0, dockSlotAt(-900f, 400f, false))
        assertEquals(4, dockSlotAt(900f, 400f, false))
        assertEquals(0, dockSlotAt(10f, 0f, false))
    }
}

