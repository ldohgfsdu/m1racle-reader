package com.m1racle.reader.ui.hub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `rtl mirrors touch and indicator positions with the same mapping`() {
        for (logical in listOf(0f, 0.5f, 1f, 2.25f, 3f)) {
            assertEquals(logical, dockPhysicalPosition(logical, false), 0f)
            assertEquals(3f - logical, dockPhysicalPosition(logical, true), 0f)
            assertEquals(logical, dockPhysicalPosition(dockPhysicalPosition(logical, true), true), 0f)
        }
    }
}
