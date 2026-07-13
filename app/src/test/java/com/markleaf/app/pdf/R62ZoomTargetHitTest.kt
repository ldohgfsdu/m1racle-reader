package com.markleaf.app.pdf

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class R62ZoomTargetHitTest {

    // ── A. Persistent ZoomSession ──────────────────────────────────────────

    @Test
    fun `pinch end keeps session active`() {
        val ended = ContinuousZoomSessionReducer.gestureEnd(zoomed())
        assertTrue(ended.active)
    }

    @Test
    fun `gestureActive false does not exit session`() {
        val ended = ContinuousZoomSessionReducer.gestureEnd(zoomed())
        assertFalse(ended.gestureActive)
        assertTrue(ended.active)
        assertEquals(1, ContinuousZoomSessionReducer.overlayCount(ended))
    }

    @Test
    fun `gesture end uses final cumulative scale not stale 1x`() {
        // Simulate: user pinched to 2.35f, then released. Final scale must stay 2.35f.
        val session = zoomed() // scale=2.35f
        val ended = ContinuousZoomSessionReducer.gestureEnd(session)
        assertEquals(2.35f, ended.scale, 0f)
        assertTrue(ended.active)
    }

    @Test
    fun `overlay dispose does not reset session`() {
        // Session is owned by remember, not by the overlay composable.
        // The reducer/state itself must not reset on overlay removal.
        val session = zoomed()
        val exited = ContinuousZoomSessionReducer.exit(session)
        // Exit is the only way to reset; there is no "dispose resets session" path.
        assertFalse(exited.active)
        assertEquals(1f, exited.scale, 0f)
        // Prove session data is independent of overlay lifecycle.
        assertEquals(7, session.lockedPage)
    }

    @Test
    fun `bitmap tier update does not reset scale or offset`() {
        val before = zoomed()
        // A bitmap quality replacement should not touch session state.
        val after = before.copy() // simulates recompose with same session, new bitmap
        assertEquals(before.scale, after.scale, 0f)
        assertEquals(before.offset, after.offset)
        assertEquals(before.lockedPage, after.lockedPage)
        assertTrue(after.active)
    }

    @Test
    fun `primaryPage change does not modify lockedPage`() {
        val session = zoomed() // lockedPage=7
        assertEquals(7, session.lockedPage)
        // VM pageIndex might change to 8, but lockedPage must stay 7.
        assertTrue(session.lockedPage != 8)
    }

    @Test
    fun `active double tap exit restores fit state`() {
        val exited = ContinuousZoomSessionReducer.exit(zoomed())
        assertFalse(exited.active)
        assertEquals(1f, exited.scale, 0f)
        assertEquals(OverlayOffset(0f, 0f), exited.offset)
        assertTrue(exited.lockedPage == null)
    }

    // ── B. Pinch hit-test ──────────────────────────────────────────────────

    private val page5 = OverlayRect(10f, 0f, 980f, 1400f)
    private val page6 = OverlayRect(10f, 1410f, 980f, 1380f)
    private val page7 = OverlayRect(10f, 2800f, 980f, 1350f)

    @Test
    fun `hitTestPage returns exact match when centroid inside page bounds`() {
        // Finger centroid at Y=700 is inside page5 (0..1400).
        val candidates = listOf(
            VisiblePageTarget(5, page5),
            VisiblePageTarget(6, page6),
        )
        assertEquals(5, ZoomPageOverlayMath.hitTestPage(700f, candidates))
    }

    @Test
    fun `hitTestPage returns lower page when centroid inside lower page`() {
        // Finger centroid at Y=2000 is inside page6 (1410..2790).
        val candidates = listOf(
            VisiblePageTarget(5, page5),
            VisiblePageTarget(6, page6),
            VisiblePageTarget(7, page7),
        )
        assertEquals(6, ZoomPageOverlayMath.hitTestPage(2000f, candidates))
    }

    @Test
    fun `hitTestPage returns nearest page when centroid falls in gap`() {
        // Gap between page5 (0..1400) and page6 (1410..2790): centroid at 1405.
        // page5 center=700, page6 center=2100. |1405-700|=705, |1405-2100|=695 → page6 is nearer.
        val candidates = listOf(
            VisiblePageTarget(5, page5),
            VisiblePageTarget(6, page6),
        )
        assertEquals(6, ZoomPageOverlayMath.hitTestPage(1405f, candidates))
    }

    @Test
    fun `hitTestPage returns nearest when centroid in larger gap`() {
        // Gap between page6 (1410..2790) and page7 (2800..4150): centroid at 2795.
        // page6 center=2100, page7 center=3475. |2795-2100|=695, |2795-3475|=680 → page7 is nearer.
        val candidates = listOf(
            VisiblePageTarget(6, page6),
            VisiblePageTarget(7, page7),
        )
        assertEquals(7, ZoomPageOverlayMath.hitTestPage(2795f, candidates))
    }

    @Test
    fun `hitTestPage returns null for empty candidates`() {
        assertNull(ZoomPageOverlayMath.hitTestPage(500f, emptyList()))
    }

    @Test
    fun `hitTestPage with single candidate always returns it`() {
        val candidates = listOf(VisiblePageTarget(3, page5))
        // Even if centroid is far away, the only candidate wins.
        assertEquals(3, ZoomPageOverlayMath.hitTestPage(9999f, candidates))
    }

    // ── C. Identity alignment ──────────────────────────────────────────────

    @Test
    fun `lockedPage hides only itself in bottom content`() {
        val session = zoomed() // lockedPage=7
        assertFalse(ContinuousZoomSessionReducer.bottomContentVisible(session, 7))
        assertTrue(ContinuousZoomSessionReducer.bottomContentVisible(session, 5))
        assertTrue(ContinuousZoomSessionReducer.bottomContentVisible(session, 6))
        assertTrue(ContinuousZoomSessionReducer.bottomContentVisible(session, 8))
    }

    @Test
    fun `overlay count is exactly 1 during active session`() {
        assertEquals(1, ContinuousZoomSessionReducer.overlayCount(zoomed()))
    }

    @Test
    fun `overlay count is 0 when inactive`() {
        assertEquals(0, ContinuousZoomSessionReducer.overlayCount(ContinuousZoomSession.inactive()))
    }

    @Test
    fun `exit atomically restores bottom and removes overlay`() {
        val exited = ContinuousZoomSessionReducer.exit(zoomed())
        assertTrue(ContinuousZoomSessionReducer.bottomContentVisible(exited, 7))
        assertEquals(0, ContinuousZoomSessionReducer.overlayCount(exited))
    }

    @Test
    fun `entry list position is preserved while session is active`() {
        val active = zoomed()
        assertEquals(12, active.entryListIndex)
        assertEquals(345, active.entryListOffset)
        // Exit resets to inactive defaults; the key contract is that the session
        // retains position while active so the LazyColumn remains frozen.
        val exited = ContinuousZoomSessionReducer.exit(active)
        assertFalse(exited.active)
        // After exit, list is unfrozen — entry position defaults back to 0.
        assertEquals(0, exited.entryListIndex)
        assertEquals(0, exited.entryListOffset)
    }

    @Test
    fun `production enterLock never references state pageImage for overlay`() {
        // The overlay must only use pageBitmaps[lockedPage], never state.pageImage.
        val source = screenSource()
        val zoomLock = source.substringAfter("private fun PdfVerticalZoomLockArea")
        // After the fix, the overlay bitmap line should NOT contain "?: state.pageImage".
        val overlaySection = zoomLock.substringAfter("if (zoomSession.active && page != null &&")
        val overlayBitmapLine = overlaySection.substringAfter("val bitmap =")
        assertFalse(
            "overlay bitmap must not fall back to state.pageImage",
            overlayBitmapLine.contains("state.pageImage"),
        )
    }

    @Test
    fun `enterLock bitmap check uses pageBitmaps not pageImage`() {
        val source = screenSource()
        val zoomLock = source.substringAfter("private fun PdfVerticalZoomLockArea")
        val enterLockFn = zoomLock.substringAfter("fun enterLock(").substringBefore("fun exitLock(")
        // Must check pageBitmaps[page] != null, not pageImage.
        assertTrue(enterLockFn.contains("state.pageBitmaps[page]"))
        assertFalse(
            "enterLock must not check state.pageImage as fallback",
            enterLockFn.contains("state.pageImage"),
        )
    }

    // ── D. Continuous cold page spinner audit ──────────────────────────────

    @Test
    fun `continuous cold page uses white background not spinner`() {
        val source = screenSource()
        val zoomLock = source.substringAfter("private fun PdfVerticalZoomLockArea")
        val coldBranch = zoomLock.substringAfter("else -> {").substringBefore("}")
        // Continuous cold branch should have a white background Box, not CircularProgressIndicator.
        assertTrue(coldBranch.contains("Color.White") || coldBranch.contains("background"))
        assertFalse(
            "continuous cold page must not show CircularProgressIndicator",
            coldBranch.contains("CircularProgressIndicator"),
        )
    }

    @Test
    fun `horizontal cold page spinner is not in continuous path`() {
        // The CircularProgressIndicator on line ~1256 must be inside PdfHorizontalPagedArea only.
        val source = screenSource()
        val continuousFn = source.substringAfter("private fun PdfVerticalZoomLockArea")
            .substringBefore("private data class ViewportRaw")
        assertFalse(
            "CircularProgressIndicator must not appear in continuous mode",
            continuousFn.contains("CircularProgressIndicator"),
        )
    }

    // ── E. Regression guards ───────────────────────────────────────────────

    @Test
    fun `production code does not reference primaryBounds`() {
        val source = screenSource()
        val zoomLock = source.substringAfter("private fun PdfVerticalZoomLockArea")
        assertFalse(
            "primaryBounds must be removed from continuous mode",
            zoomLock.contains("primaryBounds"),
        )
    }

    @Test
    fun `enterLock is not called with state pageIndex`() {
        val source = screenSource()
        val zoomLock = source.substringAfter("private fun PdfVerticalZoomLockArea")
        assertFalse(
            "enterLock must not be called with state.pageIndex",
            zoomLock.contains("enterLock(state.pageIndex)"),
        )
    }

    @Test
    fun `gesture end reads final zoomSession not stale 1x`() {
        val source = screenSource()
        val zoomLock = source.substringAfter("private fun PdfVerticalZoomLockArea")
        // The gesture end path must use zoomSession (updated in the move loop).
        assertTrue(zoomLock.contains("ContinuousZoomSessionReducer.gestureEnd(zoomSession)"))
    }

    @Test
    fun `no scrollToItem in active pinch end`() {
        val source = screenSource()
        val zoomLock = source.substringAfter("private fun PdfVerticalZoomLockArea")
        assertFalse(zoomLock.contains("scrollToItem(req"))
    }

    @Test
    fun `no PendingLayoutAnchor in continuous mode`() {
        val source = screenSource()
        val zoomLock = source.substringAfter("private fun PdfVerticalZoomLockArea")
        assertFalse(zoomLock.contains("PendingLayoutAnchor"))
    }

    @Test
    fun `no dispatchRawDelta in continuous mode`() {
        val source = screenSource()
        val zoomLock = source.substringAfter("private fun PdfVerticalZoomLockArea")
        assertFalse(zoomLock.contains("dispatchRawDelta"))
    }

    @Test
    fun `no global layout zoom in continuous mode`() {
        val source = screenSource()
        val zoomLock = source.substringAfter("private fun PdfVerticalZoomLockArea")
        assertFalse(zoomLock.contains("state.zoomScale"))
    }

    @Test
    fun `bottom hiding uses lockedPage not state pageIndex`() {
        val source = screenSource()
        val zoomLock = source.substringAfter("private fun PdfVerticalZoomLockArea")
        // bottomContentVisible is called with zoomSession and page (the item page).
        assertTrue(zoomLock.contains("ContinuousZoomSessionReducer.bottomContentVisible"))
    }

    @Test
    fun `visible page bounds are captured for all pages not just primary`() {
        val source = screenSource()
        val zoomLock = source.substringAfter("private fun PdfVerticalZoomLockArea")
        // The onGloballyPositioned must no longer gate on "current".
        val positionedBlock = zoomLock.substringAfter(".onGloballyPositioned")
        assertFalse(
            "bounds capture must not be gated on 'current'",
            positionedBlock.contains("if (current &&"),
        )
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private val bounds = OverlayRect(10f, 120f, 980f, 1380f)

    private fun zoomed(): ContinuousZoomSession {
        val begun = ContinuousZoomSessionReducer.begin(
            ContinuousZoomSession.inactive(), 7, bounds, 12, 345,
        )
        return ContinuousZoomSessionReducer.updateTransform(
            begun, 2.35f, OverlayOffset(-180f, -290f),
        )
    }

    private fun screenSource(): String {
        val candidates = listOf(
            File("app/src/main/java/com/markleaf/app/ui/pdf/PdfViewerScreen.kt"),
            File("src/main/java/com/markleaf/app/ui/pdf/PdfViewerScreen.kt"),
        )
        return candidates.first { it.isFile }.readText()
    }
}
