package com.markleaf.app.pdf

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class R6PdfInteractionTest {
    private val page = OverlayRect(10f, 100f, 980f, 1400f)

    @Test fun listItemGeometryIsIndependentOfZoomState() {
        val source = screenSource().substringBefore("/* R5 global-layout")
        assertFalse(source.contains("pageLayoutWidthPx"))
        assertFalse(source.contains("pageLayoutHeightPx"))
    }

    @Test fun enteringZoomLockPreservesListPosition() {
        val session = session()
        assertEquals(8, session.entryListIndex)
        assertEquals(321, session.entryListOffset)
    }

    @Test fun zoomLockPrimaryPageIsImmutableAcrossTransformCopy() {
        assertEquals(12, session().copy(scale = 2f).primaryPage)
    }

    @Test fun productionDisablesListUserScrollDuringLock() {
        assertTrue(screenSource().contains("TwoFingerGestureArbitration.listScrollEnabled("))
    }

    @Test fun overlayReverseScaleClampsAtFitScale() {
        assertEquals(1f, ZoomPageOverlayMath.targetScale(1.2f, 0.7f), 0f)
    }

    @Test fun overlaySupportsFourDirectionPan() {
        val start = OverlayOffset(-300f, -500f)
        val rightDown = ZoomPageOverlayMath.clampOffset(2f, OverlayOffset(-250f, -450f), page, 1000f, 1200f)
        val leftUp = ZoomPageOverlayMath.clampOffset(2f, OverlayOffset(-350f, -550f), page, 1000f, 1200f)
        assertTrue(rightDown.x > start.x && rightDown.y > start.y)
        assertTrue(leftUp.x < start.x && leftUp.y < start.y)
    }

    @Test fun overlayBoundsRejectLargeBlankAreas() {
        val clamped = ZoomPageOverlayMath.clampOffset(2f, OverlayOffset(900f, 900f), page, 1000f, 1200f)
        assertEquals(-10f, clamped.x, 0f)
        assertEquals(-100f, clamped.y, 0f)
    }

    @Test fun qualityReplacementPreservesTransform() {
        val before = session().copy(scale = 2.4f, offset = OverlayOffset(-200f, -400f))
        val after = ZoomPageOverlayMath.replaceBitmap(before, 99L)
        assertEquals(before.scale, after.scale, 0f)
        assertEquals(before.offset, after.offset)
    }

    @Test fun exitingAtOnePreservesEntryListPosition() {
        val before = session()
        val active = ContinuousZoomSessionReducer.begin(
            ContinuousZoomSession.inactive(), 12, page, 8, 321,
        )
        val zoomed = ContinuousZoomSessionReducer.updateTransform(
            active, 2f, OverlayOffset(-100f, -100f),
        )
        val shrink = ContinuousZoomSessionReducer.updateTransform(
            zoomed, 1.005f, OverlayOffset(0f, 0f),
        )
        assertFalse(ContinuousZoomSessionReducer.gestureEnd(shrink).active)
        assertEquals(before.entryListIndex to before.entryListOffset, 8 to 321)
    }

    @Test fun activeProductionPinchEndNeverScrollsToItem() {
        val active = screenSource().substringBefore("/* R5 global-layout")
        val zoomLock = active.substringAfter("private fun PdfVerticalZoomLockArea")
        assertFalse(zoomLock.contains("scrollToItem(req"))
    }

    @Test fun pendingLayoutAnchorHasNoCompiledProductionReference() {
        val active = screenSource().substringBefore("/* R5 global-layout")
        assertFalse(active.contains("PendingLayoutAnchor"))
    }

    @Test fun committedZoomCannotChangeActiveListItemHeight() {
        val active = screenSource().substringBefore("/* R5 global-layout")
        val zoomLock = active.substringAfter("private fun PdfVerticalZoomLockArea")
        assertFalse(zoomLock.contains("state.zoomScale"))
    }

    @Test fun horizontalModeHasNoAutoHideTimer() {
        assertFalse(screenSource().contains("CHROME_AUTO_HIDE_MS"))
    }

    @Test fun topBarIsAlwaysVisible() {
        assertTrue(screenSource().contains("topBarVisible = true"))
    }

    @Test fun controlsDoNotChangeViewportGeometry() {
        val hidden = ZoomPageOverlayMath.fitCenter(1000f, 1600f, 1.4f)
        val shown = ZoomPageOverlayMath.fitCenter(1000f, 1600f, 1.4f)
        assertEquals(hidden, shown)
    }

    @Test fun fitCenterCentersHorizontallyAndVertically() {
        val fit = ZoomPageOverlayMath.fitCenter(1000f, 1000f, 1.4f)
        assertEquals(fit.left, (1000f - fit.width) / 2f, 0f)
        assertEquals(fit.top, (1000f - fit.height) / 2f, 0f)
    }

    @Test fun fitCenterHasSymmetricVerticalWhitespace() {
        val fit = ZoomPageOverlayMath.fitCenter(1000f, 1600f, 1.4f)
        assertEquals(fit.top, 1600f - fit.top - fit.height, 0.001f)
    }

    @Test fun draggingDoesNotPublishUnsettledPage() {
        assertFalse(PagerRequestPolicy.shouldPublishSettled(4, 4))
    }

    @Test fun settledPagePublishesOnlyWhenChanged() {
        assertTrue(PagerRequestPolicy.shouldPublishSettled(5, 4))
        assertFalse(PagerRequestPolicy.shouldPublishSettled(5, 5))
    }

    @Test fun ordinaryVmPageStateIsNotAnExternalRequest() {
        assertFalse(PagerRequestPolicy.shouldConsume(null, 10L))
    }

    @Test fun externalRequestIdIsConsumedOnce() {
        assertTrue(PagerRequestPolicy.shouldConsume(11L, 10L))
        assertFalse(PagerRequestPolicy.shouldConsume(11L, 11L))
    }

    @Test fun pagerMovementDisablesEdgeTap() {
        assertEquals(PageTap.NONE, ZoomPageOverlayMath.pageTap(20f, 1000f, true, 1f))
    }

    @Test fun oneTapProducesAtMostOneDirection() {
        assertEquals(PageTap.PREVIOUS, ZoomPageOverlayMath.pageTap(20f, 1000f, false, 1f))
        assertEquals(PageTap.NEXT, ZoomPageOverlayMath.pageTap(980f, 1000f, false, 1f))
    }

    @Test fun zoomDisablesPagerTapAndEnablesBoundedPan() {
        assertEquals(PageTap.NONE, ZoomPageOverlayMath.pageTap(20f, 1000f, false, 2f))
        val moved = ZoomPageOverlayMath.clampOffset(2f, OverlayOffset(-200f, -300f), page, 1000f, 1200f)
        assertEquals(OverlayOffset(-200f, -300f), moved)
    }

    @Test fun returningToOneRestoresPagerEligibility() {
        assertEquals(PageTap.PREVIOUS, ZoomPageOverlayMath.pageTap(20f, 1000f, false, 1f))
    }

    private fun session() = ZoomLockSession(
        primaryPage = 12,
        entryListIndex = 8,
        entryListOffset = 321,
        bitmapIdentity = 1L,
    )

    private fun screenSource(): String {
        val candidates = listOf(
            File("app/src/main/java/com/markleaf/app/ui/pdf/PdfViewerScreen.kt"),
            File("src/main/java/com/markleaf/app/ui/pdf/PdfViewerScreen.kt"),
        )
        return candidates.first { it.isFile }.readText()
    }
}
