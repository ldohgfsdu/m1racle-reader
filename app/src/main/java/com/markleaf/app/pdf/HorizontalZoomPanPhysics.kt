package com.markleaf.app.pdf
import kotlin.math.*

enum class HorizontalGestureOwner { PAGER, INNER_PAN }
data class HorizontalPanState(val contentX:Float=0f,val contentY:Float=0f,val overscrollX:Float=0f,val overscrollY:Float=0f,val edgeAccumulation:Float=0f,val handedOff:Boolean=false)
data class HorizontalPanResult(val state:HorizontalPanState,val visualX:Float,val visualY:Float,val navigateDirection:Int=0)
object HorizontalZoomPanPhysics {
 const val MAX_FLING_VELOCITY=4200f
 fun owner(scale:Float)=if(scale<=1.01f)HorizontalGestureOwner.PAGER else HorizontalGestureOwner.INNER_PAN
 fun resistance(raw:Float,limit:Float)=sign(raw)*limit*(1f-exp(-abs(raw)/limit.coerceAtLeast(1f)))
 fun pan(s:HorizontalPanState,dx:Float,dy:Float,maxX:Float,maxY:Float,limit:Float,handoffThreshold:Float,pinching:Boolean=false,rebased:Boolean=false):HorizontalPanResult{
  if(pinching||rebased)return HorizontalPanResult(s,s.contentX+resistance(s.overscrollX,limit),s.contentY+resistance(s.overscrollY,limit))
  val rawX=s.contentX+s.overscrollX+dx;val rawY=s.contentY+s.overscrollY+dy;val cx=rawX.coerceIn(-maxX,maxX);val cy=rawY.coerceIn(-maxY,maxY)
  val ox=rawX-cx;val oy=rawY-cy;val horizontal=abs(dx)>abs(dy)*1.2f
  val accum=if(horizontal&&ox!=0f) s.edgeAccumulation+abs(dx) else 0f
  val handoff=!s.handedOff&&accum>=handoffThreshold
  val direction=if(handoff) if(ox<0)-1 else 1 else 0
  val next=HorizontalPanState(cx,cy,ox,oy,accum,s.handedOff||handoff)
  return HorizontalPanResult(next,cx+resistance(ox,limit),cy+resistance(oy,limit),direction)
 }
 fun settle(s:HorizontalPanState)=s.copy(overscrollX=0f,overscrollY=0f,edgeAccumulation=0f,handedOff=false)
 fun clampVelocity(v:Float)=v.coerceIn(-MAX_FLING_VELOCITY,MAX_FLING_VELOCITY)
}
