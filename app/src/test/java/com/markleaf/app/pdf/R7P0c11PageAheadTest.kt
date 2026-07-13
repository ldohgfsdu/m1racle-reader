package com.markleaf.app.pdf
import org.junit.Assert.*
import org.junit.Test

class R7P0c11PageAheadTest {
 private val g=ContinuousDocumentGeometry.create(4,1000f,mapOf(0 to 1f,1 to 1f,2 to 1f,3 to 1f),10f)
 private fun motion(v:Float,d:TileNavigationDirection,c:Float=.8f)=ViewportMotionEstimate(0f,v,0f,d,c,1,DocumentViewportRect(0f,800f,1000f,1600f))
 @Test fun `scroll velocity uses ema`(){var m=PdfViewportMotion.update(ViewportMotionEstimate(),DocumentViewportRect(0f,0f,100f,100f),1f,1f,1_000_000_000,false);m=PdfViewportMotion.update(m,DocumentViewportRect(0f,100f,100f,200f),1f,1f,1_100_000_000,false);assertEquals(350f,m.velocityYDocumentPxPerSec,.1f)}
 @Test fun `rebase frame skips velocity`(){val m=motion(500f,TileNavigationDirection.FORWARD);assertEquals(500f,PdfViewportMotion.update(m,DocumentViewportRect(0f,0f,1f,1f),1f,1f,2,true).velocityYDocumentPxPerSec,0f)}
 @Test fun `near and far predict two horizons`(){val c=PdfViewportMotion.corridor(DocumentViewportRect(0f,0f,100f,100f),motion(1000f,TileNavigationDirection.FORWARD));assertEquals(100f,c.near.top,.1f);assertEquals(240f,c.far.top,.1f)}
 @Test fun `direction reversal lowers confidence`(){var m=motion(500f,TileNavigationDirection.FORWARD,1f);m=PdfViewportMotion.update(m,DocumentViewportRect(0f,500f,1000f,1300f),1f,1f,101_000_001,false);assertEquals(.25f,m.confidence,0f)}
 @Test fun `down selects next page top band`(){val c=PrefetchCorridor(DocumentViewportRect(0f,700f,1000f,1500f),DocumentViewportRect(0f,800f,1000f,1600f),DocumentViewportRect(0f,1100f,1000f,1900f),TileNavigationDirection.FORWARD,.8f);val a=PdfTilePlanner.pageAhead(g,c,0)!!;assertEquals(1,a.pageIndex);assertEquals(0f,a.visibleRect.top,0f)}
 @Test fun `up selects previous page bottom band`(){val c=PrefetchCorridor(DocumentViewportRect(0f,1100f,1000f,1900f),DocumentViewportRect(0f,1000f,1000f,1800f),DocumentViewportRect(0f,500f,1000f,1300f),TileNavigationDirection.BACKWARD,.8f);val a=PdfTilePlanner.pageAhead(g,c,2)!!;assertEquals(1,a.pageIndex);assertEquals(a.pageHeight,a.visibleRect.bottom,0f)}
 @Test fun `page ahead preserves x anchor`(){val c=PrefetchCorridor(DocumentViewportRect(250f,700f,750f,1500f),DocumentViewportRect(250f,800f,750f,1600f),DocumentViewportRect(250f,1100f,750f,1900f),TileNavigationDirection.FORWARD,.8f);val a=PdfTilePlanner.pageAhead(g,c,0)!!;assertEquals(250f,a.visibleRect.left,0f);assertEquals(750f,a.visibleRect.right,0f)}
 @Test fun `page ahead requests band not full page`(){val c=PrefetchCorridor(DocumentViewportRect(0f,700f,500f,1100f),DocumentViewportRect(0f,800f,500f,1200f),DocumentViewportRect(0f,1000f,500f,1400f),TileNavigationDirection.FORWARD,.8f);val a=PdfTilePlanner.pageAhead(g,c,0)!!;assertTrue(a.visibleRect.height<a.pageHeight)}
 @Test fun `idle direction selects no page`(){val c=PrefetchCorridor(DocumentViewportRect(0f,0f,1f,1f),DocumentViewportRect(0f,0f,1f,1f),DocumentViewportRect(0f,0f,1f,1f),TileNavigationDirection.IDLE,1f);assertNull(PdfTilePlanner.pageAhead(g,c,0))}
 @Test fun `document generation invalidates page ahead keys`(){val v=PageTileViewport(1,1000f,1000f,PageLayoutRect(0f,0f,500f,400f));assertNotEquals(PdfTilePlanner.requests("d",1,v,1,1,null,null).first().key,PdfTilePlanner.requests("d",2,v,1,1,null,null).first().key)}
}
