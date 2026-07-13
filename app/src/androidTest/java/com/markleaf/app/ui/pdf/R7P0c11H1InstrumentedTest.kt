package com.markleaf.app.ui.pdf
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.markleaf.app.pdf.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class R7P0c11H1InstrumentedTest {
 @Test fun pageAheadBandIsPreparedBeforeIntersection() {
  val g=ContinuousDocumentGeometry.create(3,1000f,mapOf(0 to 1f,1 to 1f,2 to 1f),10f)
  val c=PrefetchCorridor(DocumentViewportRect(0f,500f,1000f,900f),DocumentViewportRect(0f,650f,1000f,1050f),DocumentViewportRect(0f,900f,1000f,1300f),TileNavigationDirection.FORWARD,.9f)
  val ahead=PdfTilePlanner.pageAhead(g,c,0)!!
  assertEquals(1,ahead.pageIndex);assertEquals(0f,ahead.visibleRect.top,0f)
 }
 @Test fun zoomedEdgePanRequiresThresholdAndHandsOffOnce() {
  var s=HorizontalPanState(contentX=100f)
  val first=HorizontalZoomPanPhysics.pan(s,12f,0f,100f,100f,50f,40f);assertEquals(0,first.navigateDirection)
  s=first.state
  val second=HorizontalZoomPanPhysics.pan(s,30f,0f,100f,100f,50f,40f);assertEquals(1,second.navigateDirection)
  assertEquals(0,HorizontalZoomPanPhysics.pan(second.state,30f,0f,100f,100f,50f,40f).navigateDirection)
 }
}
