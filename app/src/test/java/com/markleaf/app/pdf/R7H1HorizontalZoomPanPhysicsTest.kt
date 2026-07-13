package com.markleaf.app.pdf
import org.junit.Assert.*
import org.junit.Test
class R7H1HorizontalZoomPanPhysicsTest {
 @Test fun `one x pager owns drag`(){assertEquals(HorizontalGestureOwner.PAGER,HorizontalZoomPanPhysics.owner(1f))}
 @Test fun `zoomed inner pan owns drag`(){assertEquals(HorizontalGestureOwner.INNER_PAN,HorizontalZoomPanPhysics.owner(2f))}
 @Test fun `in range follows one to one`(){val r=HorizontalZoomPanPhysics.pan(HorizontalPanState(),20f,10f,100f,100f,50f,40f);assertEquals(20f,r.visualX,.01f);assertEquals(10f,r.visualY,.01f)}
 @Test fun `only overflow is resisted`(){val r=HorizontalZoomPanPhysics.pan(HorizontalPanState(95f),20f,0f,100f,100f,50f,40f);assertEquals(100f,r.state.contentX,0f);assertTrue(r.visualX in 100f..115f)}
 @Test fun `resistance saturates`(){assertTrue(HorizontalZoomPanPhysics.resistance(1000f,50f)<=50f)}
 @Test fun `edge does not immediately navigate`(){assertEquals(0,HorizontalZoomPanPhysics.pan(HorizontalPanState(100f),10f,0f,100f,100f,50f,40f).navigateDirection)}
 @Test fun `threshold hands off once`(){val a=HorizontalZoomPanPhysics.pan(HorizontalPanState(100f),45f,0f,100f,100f,50f,40f);assertEquals(1,a.navigateDirection);assertEquals(0,HorizontalZoomPanPhysics.pan(a.state,20f,0f,100f,100f,50f,40f).navigateDirection)}
 @Test fun `vertical intent never hands off`(){assertEquals(0,HorizontalZoomPanPhysics.pan(HorizontalPanState(100f),50f,100f,100f,100f,50f,40f).navigateDirection)}
 @Test fun `rebase consumes no delta`(){val s=HorizontalPanState(20f,30f);assertEquals(s,HorizontalZoomPanPhysics.pan(s,100f,0f,100f,100f,50f,40f,rebased=true).state)}
 @Test fun `pinching never hands off`(){assertEquals(0,HorizontalZoomPanPhysics.pan(HorizontalPanState(100f),100f,0f,100f,100f,50f,40f,pinching=true).navigateDirection)}
 @Test fun `settle removes overscroll`(){assertEquals(0f,HorizontalZoomPanPhysics.settle(HorizontalPanState(0f,0f,20f,10f,30f)).overscrollX,0f)}
 @Test fun `fling velocity clamps symmetrically`(){assertEquals(4200f,HorizontalZoomPanPhysics.clampVelocity(9000f),0f);assertEquals(-4200f,HorizontalZoomPanPhysics.clampVelocity(-9000f),0f)}
 @Test fun `left right resistance symmetric`(){assertEquals(-HorizontalZoomPanPhysics.resistance(100f,50f),HorizontalZoomPanPhysics.resistance(-100f,50f),.001f)}
}
