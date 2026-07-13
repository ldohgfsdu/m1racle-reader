package com.markleaf.app.pdf

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class R63TwoFingerGestureArbitrationTest {

    @Test fun `one pointer remains possible single pointer`() {
        assertEquals(
            TwoFingerGesturePhase.POSSIBLE_SINGLE_POINTER,
            TwoFingerGestureArbitration.onSinglePointer(TwoFingerGesturePhase.IDLE),
        )
        assertFalse(TwoFingerGestureArbitration.consumesChanges(TwoFingerGesturePhase.POSSIBLE_SINGLE_POINTER))
    }

    @Test fun `second pointer captures without zoom threshold`() {
        assertEquals(
            TwoFingerGesturePhase.TWO_FINGER_CAPTURED,
            TwoFingerGestureArbitration.onSecondPointer(),
        )
    }

    @Test fun `second pointer captures before or after the first pointer crosses slop`() {
        assertTrue(TwoFingerGestureArbitration.shouldCapture(2))
    }

    @Test fun `second pointer interval of fifty one hundred fifty and three hundred ms captures`() {
        listOf(50, 150, 300).forEach { delayMs ->
            assertTrue("delayMs=$delayMs", TwoFingerGestureArbitration.shouldCapture(2))
        }
    }

    @Test fun `capture immediately disables list before any session exists`() {
        assertFalse(TwoFingerGestureArbitration.listScrollEnabled(true, false))
        assertTrue(TwoFingerGestureArbitration.listScrollEnabled(false, false))
    }

    @Test fun `active session also disables list`() {
        assertFalse(TwoFingerGestureArbitration.listScrollEnabled(false, true))
    }

    @Test fun `captured and session states consume all movement`() {
        assertTrue(TwoFingerGestureArbitration.consumesChanges(TwoFingerGesturePhase.TWO_FINGER_CAPTURED))
        assertTrue(TwoFingerGestureArbitration.consumesChanges(TwoFingerGesturePhase.ZOOM_SESSION_ACTIVE))
    }

    @Test fun `hit miss has exactly one retry`() {
        assertTrue(TwoFingerGestureArbitration.canRetryHit(0))
        assertTrue(TwoFingerGestureArbitration.canRetryHit(1))
        assertFalse(TwoFingerGestureArbitration.canRetryHit(2))
    }

    @Test fun `viewport hit test uses both centroid axes`() {
        val candidates = listOf(
            VisiblePageTarget(4, OverlayRect(100f, 0f, 500f, 900f)),
            VisiblePageTarget(5, OverlayRect(100f, 910f, 500f, 900f)),
        )
        assertEquals(4, ZoomPageOverlayMath.hitTestPage(300f, 450f, candidates))
        assertEquals(5, ZoomPageOverlayMath.hitTestPage(300f, 1200f, candidates))
    }

    @Test fun `empty bounds is a hit miss`() {
        assertNull(ZoomPageOverlayMath.hitTestPage(100f, 100f, emptyList()))
    }

    @Test fun `root detector is installed before LazyColumn and overlay`() {
        val source = continuousSource()
        val detector = source.substringAfter(".pointerInput(docId) {")
            .substringBefore(".pointerInput(docId) {\n                detectTapGestures")
        assertTrue(detector.contains("PointerEventPass.Initial"))
        assertTrue(source.indexOf(".pointerInput(docId) {") < source.indexOf("LazyColumn("))
        assertTrue(source.indexOf(".pointerInput(docId) {") < source.indexOf("val page = lockedPage"))
    }

    @Test fun `continuous page items have no transform detector`() {
        val items = continuousSource().substringAfter("LazyColumn(")
            .substringBefore("val page = lockedPage")
        assertFalse(items.contains("pointerInput("))
        assertFalse(items.contains("detectTransformGestures"))
    }

    @Test fun `root detector key is stable document identity`() {
        val detector = rootDetector()
        assertTrue(detector.contains(".pointerInput(docId)"))
        assertFalse(detector.substringBefore("awaitEachGesture").contains("pageIndex"))
        assertFalse(detector.substringBefore("awaitEachGesture").contains("bitmap"))
        assertFalse(detector.substringBefore("awaitEachGesture").contains("quality"))
    }

    @Test fun `capture stops fling with prevent user input priority`() {
        assertTrue(rootDetector().contains("listState.stopScroll(MutatePriority.PreventUserInput)"))
    }

    @Test fun `capture consumes second down and every active change`() {
        val detector = rootDetector()
        assertTrue(detector.contains("it.pressed || it.previousPressed"))
        assertTrue(detector.contains("it.consume()"))
    }

    @Test fun `capture happens before session hit test and transform update`() {
        val detector = rootDetector()
        assertTrue(detector.indexOf("capturedThisGesture = true") < detector.indexOf("attemptSessionCreate(centroid)"))
        assertTrue(detector.indexOf("consumeCapturedChanges(event)") < detector.indexOf("attemptSessionCreate(centroid)"))
    }

    @Test fun `hit miss remains captured and logs miss`() {
        val detector = rootDetector()
        assertTrue(detector.contains("val result = if (hitPage == null) \"MISS\" else \"HIT\""))
        assertTrue(detector.contains("if (zoomSession.active || !TwoFingerGestureArbitration.canRetryHit"))
        assertFalse(detector.contains("dispatchRawDelta"))
        assertFalse(detector.contains("animateScrollBy"))
    }

    @Test fun `release keeps session and does not emit list velocity`() {
        val detector = rootDetector()
        assertTrue(detector.contains("ContinuousZoomSessionReducer.gestureEnd(zoomSession)"))
        assertTrue(detector.contains("RELEASE instanceId="))
        assertFalse(detector.contains("VelocityTracker"))
    }

    @Test fun `page bounds are converted to viewport local coordinates`() {
        val source = continuousSource()
        assertTrue(source.contains("viewport.localPositionOf(coordinates, Offset.Zero)"))
        assertTrue(source.contains("coordinateSpace=ReadingViewportLocal"))
        assertFalse(source.contains("boundsInRoot"))
    }

    @Test fun `session active freezes list while root handles single pan`() {
        val source = continuousSource()
        assertTrue(source.contains("TwoFingerGestureArbitration.listScrollEnabled("))
        assertTrue(rootDetector().contains("pressed.size == 1 && zoomSession.lockedPage != null"))
    }

    @Test fun `one gesture creates at most one session`() {
        val detector = rootDetector()
        assertTrue(detector.contains("if (zoomSession.active || !TwoFingerGestureArbitration.canRetryHit"))
        assertTrue(detector.contains("if (enterLock(hitPage, overlayBounds))"))
    }

    @Test fun `root detector covers fingers on different page items`() {
        val source = continuousSource()
        assertTrue(source.indexOf(".pointerInput(docId) {") < source.indexOf("LazyColumn("))
        assertFalse(rootDetector().contains("items(\n                count"))
    }

    @Test fun `root detector is independent of page gaps and overlay composition`() {
        val detector = rootDetector()
        assertTrue(detector.contains("val pressed = event.changes.filter { it.pressed }"))
        assertFalse(detector.contains("bitmap != null"))
        assertFalse(detector.contains("lockedBounds.width"))
    }

    @Test fun `capture and release compare the exact list snapshot`() {
        val detector = rootDetector()
        assertTrue(detector.contains("captureListIndex = listState.firstVisibleItemIndex"))
        assertTrue(detector.contains("captureListOffset = listState.firstVisibleItemScrollOffset"))
        assertTrue(detector.contains("listMovedDuringGesture=\$moved"))
    }

    @Test fun `second down sets capture before consume stop hit and transform`() {
        val capture = rootDetector().substringAfter("if (!capturedThisGesture) {")
        assertTrue(capture.indexOf("twoFingerCaptured = true") < capture.indexOf("consumeCapturedChanges(event)"))
        assertTrue(capture.indexOf("consumeCapturedChanges(event)") < capture.indexOf("listState.stopScroll"))
        assertTrue(capture.indexOf("listState.stopScroll") < capture.indexOf("attemptSessionCreate(centroid)"))
    }

    @Test fun `detector lifecycle logs expose accidental restart`() {
        val detector = rootDetector()
        assertTrue(detector.contains("DETECTOR_START instanceId="))
        assertTrue(detector.contains("DETECTOR_CANCEL instanceId="))
        assertTrue(continuousSource().contains("DETECTOR_DISPOSE instanceId="))
        assertTrue(detector.contains("FIRST_DOWN instanceId="))
        assertTrue(detector.contains("SECOND_DOWN instanceId="))
        assertTrue(detector.contains("CAPTURE instanceId="))
    }

    private fun rootDetector(): String {
        val source = continuousSource()
        return source.substring(
            source.indexOf(".pointerInput(docId) {"),
            source.indexOf(
                ".pointerInput(docId) {\n                detectTapGestures",
                source.indexOf(".pointerInput(docId) {") + 1,
            ),
        )
    }

    private fun continuousSource(): String {
        val candidates = listOf(
            File("app/src/main/java/com/markleaf/app/ui/pdf/PdfViewerScreen.kt"),
            File("src/main/java/com/markleaf/app/ui/pdf/PdfViewerScreen.kt"),
        )
        return candidates.first { it.isFile }.readText()
            .substringAfter("private fun PdfVerticalZoomLockArea")
            .substringBefore("private data class ViewportRaw")
    }
}
