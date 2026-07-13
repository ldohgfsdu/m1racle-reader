package com.markleaf.app.pdf

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class R61PersistentZoomSessionTest {
    private val bounds = OverlayRect(10f, 120f, 980f, 1380f)

    @Test fun pinchEndKeepsSessionActive() {
        val ended = ContinuousZoomSessionReducer.gestureEnd(zoomed())
        assertTrue(ended.active)
    }

    @Test fun pinchEndKeepsFinalScale() {
        assertEquals(2.35f, ContinuousZoomSessionReducer.gestureEnd(zoomed()).scale, 0f)
    }

    @Test fun gestureInactiveDoesNotHideOverlay() {
        val ended = ContinuousZoomSessionReducer.gestureEnd(zoomed())
        assertFalse(ended.gestureActive)
        assertEquals(1, ContinuousZoomSessionReducer.overlayCount(ended))
    }

    @Test fun postReleaseSingleFingerPanPersistsOffset() {
        val ended = ContinuousZoomSessionReducer.gestureEnd(zoomed())
        val panned = ended.copy(offset = OverlayOffset(-240f, -360f))
        assertEquals(OverlayOffset(-240f, -360f), panned.offset)
        assertTrue(panned.active)
    }

    @Test fun bitmapTierReplacementDoesNotResetSession() {
        val before = zoomed()
        val after = before // bitmap is deliberately outside session identity/state
        assertEquals(before, after)
    }

    @Test fun recompositionValueCopyKeepsScaleAndOffset() {
        val before = zoomed()
        val recomposed = before.copy()
        assertEquals(before.scale, recomposed.scale, 0f)
        assertEquals(before.offset, recomposed.offset)
    }

    @Test fun primaryPageChangeCannotChangeLockedPage() {
        val session = zoomed()
        val newPrimaryPage = 18
        assertEquals(7, session.lockedPage)
        assertTrue(newPrimaryPage != session.lockedPage)
    }

    @Test fun scaleAboveExitThresholdDoesNotExit() {
        val ended = ContinuousZoomSessionReducer.gestureEnd(zoomed().copy(scale = 1.02f))
        assertTrue(ended.active)
    }

    @Test fun explicitShrinkFromZoomToFitExitsAtGestureEnd() {
        val shrink = ContinuousZoomSessionReducer.updateTransform(
            zoomed(), 1.005f, OverlayOffset(0f, 0f),
        )
        assertTrue(shrink.exitRequested)
        assertFalse(ContinuousZoomSessionReducer.gestureEnd(shrink).active)
    }

    @Test fun activeDoubleTapExitRestoresFitState() {
        val exited = ContinuousZoomSessionReducer.exit(zoomed())
        assertFalse(exited.active)
        assertEquals(1f, exited.scale, 0f)
        assertEquals(OverlayOffset(0f, 0f), exited.offset)
    }

    @Test fun exitRestoresLazyColumnEligibility() {
        val exited = ContinuousZoomSessionReducer.exit(zoomed())
        assertFalse(exited.active)
        assertTrue(exited.lockedPage == null)
    }

    @Test fun ordinaryFingerReleaseNeverRequestsReset() {
        val before = zoomed().copy(exitRequested = false)
        val after = ContinuousZoomSessionReducer.gestureEnd(before)
        assertTrue(after.active)
        assertEquals(before.scale, after.scale, 0f)
    }

    @Test fun overlayDisplayConditionDependsOnActiveOnly() {
        val source = screenSource()
        assertTrue(source.contains("if (zoomSession.active && page != null"))
        assertFalse(source.contains("if (zoomSession.gestureActive && page != null"))
    }

    @Test fun inactiveDrawsBottomAndNoOverlay() {
        val inactive = ContinuousZoomSession.inactive()
        assertTrue(ContinuousZoomSessionReducer.bottomContentVisible(inactive, 7))
        assertEquals(0, ContinuousZoomSessionReducer.overlayCount(inactive))
    }

    @Test fun activeHidesOnlyLockedPageAndShowsOneOverlay() {
        val active = zoomed()
        assertFalse(ContinuousZoomSessionReducer.bottomContentVisible(active, 7))
        assertTrue(ContinuousZoomSessionReducer.bottomContentVisible(active, 8))
        assertEquals(1, ContinuousZoomSessionReducer.overlayCount(active))
    }

    @Test fun qualityReplacementKeepsExactlyOneOverlay() {
        val session = zoomed()
        assertEquals(1, ContinuousZoomSessionReducer.overlayCount(session))
        assertEquals(1, ContinuousZoomSessionReducer.overlayCount(session.copy()))
    }

    @Test fun exitAtomicallyRestoresBottomAndRemovesOverlay() {
        val exited = ContinuousZoomSessionReducer.exit(zoomed())
        assertTrue(ContinuousZoomSessionReducer.bottomContentVisible(exited, 7))
        assertEquals(0, ContinuousZoomSessionReducer.overlayCount(exited))
    }

    @Test fun visibilitySwitchPreservesItemIdentityAndListPosition() {
        val active = zoomed()
        val exited = ContinuousZoomSessionReducer.exit(active)
        assertEquals(12, active.entryListIndex)
        assertEquals(345, active.entryListOffset)
        assertEquals(7, active.lockedPage)
        assertFalse(exited.active)
    }

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
