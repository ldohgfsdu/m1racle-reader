package com.m1racle.reader.ui.hub

import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import com.m1racle.reader.ui.theme.M1racleMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DockMotionTest {
    @Test
    fun `drag stretch is symmetric in direction and disappears at rest`() {
        assertEquals(1f to 1f, dockLensScale(0f, 1f))
        assertEquals(1.38f to 1.44f, dockLensScale(1f, 0f))
        for (velocity in listOf(0.1f, 0.5f, 1f, 4f)) {
            val forward = dockLensScale(1f, velocity)
            val reverse = dockLensScale(1f, -velocity)
            assertEquals(forward, reverse)
            assertTrue(forward.first > 1.38f && forward.first <= 1.50f)
            assertTrue(forward.second < 1.44f && forward.second >= 1.35f)
        }
    }
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
    fun `selection spring settles at destination without an abrupt first frame`() {
        val animation = TargetBasedAnimation(M1racleMotion.dockSelection, Float.VectorConverter, 0f, 3f)
        assertEquals(0f, animation.getValueFromNanos(0), 0f)
        val firstFrame = animation.getValueFromNanos(16_000_000)
        assertTrue(firstFrame > 0f && firstFrame < 0.3f)
        assertTrue(animation.durationNanos < 1_000_000_000)
        assertEquals(3f, animation.getValueFromNanos(animation.durationNanos), 0.001f)
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
