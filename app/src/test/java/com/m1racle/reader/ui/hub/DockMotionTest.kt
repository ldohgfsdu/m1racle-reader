package com.m1racle.reader.ui.hub
import com.m1racle.reader.ui.hub.liquidglass.shouldConsumeDragChange
import org.junit.Assert.*
import com.m1racle.reader.ui.hub.liquidglass.opticalDeformationX
import com.m1racle.reader.ui.hub.liquidglass.opticalDeformationY
import com.m1racle.reader.ui.hub.liquidglass.dragDeformationTarget
import org.junit.Test
class DockMotionTest {
    @Test fun `central action has no navigation destination and both sides map correctly`() {
        assertEquals(listOf(0, 1, 3, 4), (0..3).map(::dockSlotForDestination))
        assertNull(dockDestinationForSlot(2))
        assertEquals(listOf(0, 1, 2, 3), listOf(0, 1, 3, 4).map(::dockDestinationForSlot))
    }

    @Test fun `speed target is symmetric bounded and visible at ordinary drag speed`() {
        assertEquals(0f, dragDeformationTarget(0f), 0f)
        for (speed in listOf(0.01f, 0.5f, 2f, 10f, 1000f)) {
            assertEquals(dragDeformationTarget(speed), dragDeformationTarget(-speed), 0f)
            assertTrue(dragDeformationTarget(speed) in 0f..1f)
        }
        assertTrue(opticalDeformationX(dragDeformationTarget(3f)) > 1.15f)
        assertTrue(opticalDeformationY(dragDeformationTarget(3f)) < .95f)
    }

    @Test fun `signed shape rebound squashes width and expands height past rest`() {
        assertEquals(1f, opticalDeformationX(0f), 0f)
        assertEquals(1f, opticalDeformationY(0f), 0f)
        assertTrue(opticalDeformationX(-.2f) < 1f)
        assertTrue(opticalDeformationY(-.2f) > 1f)
        for (strain in listOf(-100f, -.4f, 0f, .5f, 1f, 100f)) {
            assertTrue(opticalDeformationX(strain) in .85f..1.46f)
            assertTrue(opticalDeformationY(strain) in .76f..1.08f)
        }
    }

    @Test fun `RiseDiary mask mirrors its physical position in RTL`() {
        val ltr=compactLensMaskBounds(400f,64f,4f,98f,1f,56f,1.4f,1.4f,true,lensWidth=104f)
        val rtl=compactLensMaskBounds(400f,64f,4f,98f,1f,56f,1.4f,1.4f,false,lensWidth=104f)
        assertEquals(400f-ltr.left-ltr.width,rtl.left,.001f)
        assertEquals(ltr.height,rtl.height,0f)
        assertEquals(4f + 98f * 1.5f, ltr.left + ltr.width / 2f, .001f)
        assertEquals(67f/48f, originalLiquidSelectionPressedScale(),0f)
    }
    @Test fun `drag consumes movement but honors an unconsumed down policy`() {
        assertFalse(shouldConsumeDragChange(false,true,true))
        assertFalse(shouldConsumeDragChange(true,false,false))
        assertTrue(shouldConsumeDragChange(true,false,true))
        assertTrue(shouldConsumeDragChange(true,true,false))
    }
}
