package com.m1racle.reader.ui.hub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DockMotionTest {
    @Test
    fun `drag is bounded and commits the nearest destination once`() {
        val preview = DockDragPreview()
        preview.moveTo(0f)
        preview.moveTo(1.7f)
        assertEquals(HubDestination.BOOKMARKS, preview.finish())
        assertNull(preview.finish())
        preview.moveTo(-20f)
        assertEquals(0f, preview.position!!, 0f)
        preview.moveTo(100f)
        assertEquals(HubDestination.SETTINGS, preview.finish())
    }

    @Test
    fun `cancel clears only preview and emits no navigation`() {
        val preview = DockDragPreview()
        preview.moveTo(2f)
        preview.moveTo(0.4f)
        preview.cancel()
        assertNull(preview.position)
        assertNull(preview.finish())
    }

    @Test
    fun `capsule press scale matches official 78 over 56 and stretches with velocity`() {
        assertEquals(78f / 56f, DockCapsulePressedScale, 0f)
        assertEquals(1.2f, DockContentPressedScale, 0f)
        val rest = dockCapsuleScales(1f, 1f, 0f)
        assertEquals(1f, rest.first, 0f)
        assertEquals(1f, rest.second, 0f)
        val pressed = dockCapsuleScales(DockCapsulePressedScale, DockCapsulePressedScale, 0f)
        assertEquals(DockCapsulePressedScale, pressed.first, 1e-6f)
        assertEquals(DockCapsulePressedScale, pressed.second, 1e-6f)
        val right = dockCapsuleScales(1f, 1f, 10f)
        assertTrue(right.first > 1f)
        assertTrue(right.second < 1f)
        val left = dockCapsuleScales(1f, 1f, -10f)
        assertTrue(left.first < 1f)
        assertTrue(left.second > 1f)
        val clamped = dockCapsuleScales(1f, 1f, 1000f)
        val expectedX = 1f / (1f - 0.2f)
        val expectedY = 1f * (1f - 0.2f)
        assertEquals(expectedX, clamped.first, 1e-6f)
        assertEquals(expectedY, clamped.second, 1e-6f)
    }

    @Test
    fun `rtl mirrors touch and indicator positions with the same mapping`() {
        for (logical in listOf(0f, 0.5f, 1f, 2.25f, 3f)) {
            assertEquals(logical, dockPhysicalPosition(logical, false), 0f)
            assertEquals(3f - logical, dockPhysicalPosition(logical, true), 0f)
            assertEquals(logical, dockPhysicalPosition(dockPhysicalPosition(logical, true), true), 0f)
        }
    }
}
