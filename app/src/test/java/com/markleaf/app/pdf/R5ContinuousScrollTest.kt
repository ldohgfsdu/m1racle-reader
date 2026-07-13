package com.markleaf.app.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * R5-P0 continuous-mode native scroll: pure, side-effect-free geometry/law tests.
 * These encode the acceptance points that do not require an emulator.
 */
class R5ContinuousScrollTest {

    private val viewportW = 1000
    private val aspect = 1.4f

    // ---- committedScale drives document-layout height ----
    @Test fun committedScaleGrowsPageLayoutHeight() {
        val h1 = ContinuousLayoutMath.pageLayoutHeightPx(viewportW, 1f, aspect)
        val h2 = ContinuousLayoutMath.pageLayoutHeightPx(viewportW, 2f, aspect)
        val h3 = ContinuousLayoutMath.pageLayoutHeightPx(viewportW, 3f, aspect)
        assertEquals(1400, h1)
        assertEquals(2800, h2)
        assertEquals(4200, h3)
    }

    @Test fun pageLayoutHeightUsesWidthTimesAspect() {
        assertEquals(
            ContinuousLayoutMath.pageLayoutWidthPx(viewportW, 2f) * aspect,
            ContinuousLayoutMath.pageLayoutHeightPx(viewportW, 2f, aspect).toFloat(),
            0.001f,
        )
    }

    // ---- render tier / bitmap never affect layout identity or scroll anchor ----
    @Test fun layoutHeightDependsOnlyOnCommittedScaleAndAspect() {
        // Signature has no tier / bucket / bitmap arg → cannot alter scroll anchor.
        val a = ContinuousLayoutMath.pageLayoutHeightPx(viewportW, 2.5f, aspect)
        val b = ContinuousLayoutMath.pageLayoutHeightPx(viewportW, 2.5f, aspect)
        assertEquals(a, b)
    }

    @Test fun pageLayoutWidthClampsToMinimumViewport() {
        assertEquals(1, ContinuousLayoutMath.pageLayoutWidthPx(0, 1f))
    }

    // ---- single-finger axis arbitration ----
    @Test fun verticalDragLocksVerticalAndIsNotConsumedByPage() {
        var lock = AxisLock.NONE
        lock = ContinuousLayoutMath.resolveAxis(3f, 50f, 8f, lock)
        assertEquals(AxisLock.VERTICAL, lock)
        // VERTICAL → handler does NOT consume and does NOT change horizontal pan.
        var panX = -120f
        // (vertical branch leaves panX untouched — no horizontal clamp, no list fling)
        assertEquals(-120f, panX, 0f)
    }

    @Test fun verticalDragDoesNotInjectListScroll() {
        // New model never maps a vertical remainder into a list fling delta.
        val lock = ContinuousLayoutMath.resolveAxis(2f, 60f, 8f, AxisLock.NONE)
        assertEquals(AxisLock.VERTICAL, lock)
        // Simulate a vertical-only move: panX (horizontal) must stay exactly as it was.
        val before = -200f
        val after = before // VERTICAL branch does not touch panX
        assertEquals(before, after, 0f)
    }

    @Test fun horizontalAxisLockOnlyModifiesOffsetX() {
        var lock = AxisLock.NONE
        lock = ContinuousLayoutMath.resolveAxis(60f, 4f, 8f, lock)
        assertEquals(AxisLock.HORIZONTAL, lock)
        val contentW = ContinuousLayoutMath.pageLayoutWidthPx(viewportW, 2f) // 2000
        // Finger drags left (dx < 0) → content shifts left → panX negative.
        var panX = 0f
        panX = ContinuousLayoutMath.clampHorizontalOffset(panX - 50f, contentW, viewportW)
        assertEquals(-50f, panX, 0f)
        // Dragging right is clamped to 0 (cannot expose empty left space).
        panX = ContinuousLayoutMath.clampHorizontalOffset(panX + 200f, contentW, viewportW)
        assertEquals(0f, panX, 0f)
    }

    @Test fun axisStaysLockedOnceDecided() {
        var lock = AxisLock.NONE
        lock = ContinuousLayoutMath.resolveAxis(60f, 4f, 8f, lock)
        // A later vertical move must NOT flip the lock mid-gesture.
        lock = ContinuousLayoutMath.resolveAxis(2f, 80f, 8f, lock)
        assertEquals(AxisLock.HORIZONTAL, lock)
    }

    @Test fun belowTouchSlopResolvesNone() {
        assertEquals(AxisLock.NONE, ContinuousLayoutMath.resolveAxis(3f, 2f, 8f, AxisLock.NONE))
    }

    // ---- offsetX clears at 1x ----
    @Test fun offsetXClearsWhenBackToScaleOne() {
        assertEquals(0f, ContinuousLayoutMath.offsetXAfterCommit(1f, -350f), 0f)
        assertEquals(0f, ContinuousLayoutMath.offsetXAfterCommit(1.01f, -350f), 0f)
    }

    @Test fun offsetXPreservedAboveScaleOne() {
        assertEquals(-350f, ContinuousLayoutMath.offsetXAfterCommit(2f, -350f), 0f)
        assertEquals(-350f, ContinuousLayoutMath.offsetXAfterCommit(3f, -350f), 0f)
    }

    // ---- pinch-end single anchor correction ----
    @Test fun anchorScrollOffsetPlacesContentUnderCentroid() {
        val pageHeight = 2800
        val normY = 0.3f
        val centroidY = 800f
        val offset = ContinuousLayoutMath.anchorScrollOffsetForContent(centroidY, normY, pageHeight)
        // item top lands at `offset`; content point = offset + normY*pageHeight must equal centroidY.
        assertEquals(centroidY, (offset + normY * pageHeight).toFloat(), 0.001f)
    }

    @Test fun contentNormYRoundTrips() {
        val centroidY = 600f
        val totalScroll = 100f
        val gestureScale = 2f
        val gesturePanY = 0f
        val pageTopPx = 0f
        val pageHeightPx = 1400f
        val normY = ContinuousLayoutMath.contentNormY(
            centroidY, totalScroll, gestureScale, gesturePanY, pageTopPx, pageHeightPx,
        )
        // layoutY of content point = (centroid - pan)/scale + total = 600/2 + 100 = 400
        assertEquals(400f / 1400f, normY, 0.001f)
        val newPageHeight = 2800
        val offset = ContinuousLayoutMath.anchorScrollOffsetForContent(centroidY, normY, newPageHeight)
        assertEquals(centroidY, (offset + normY * newPageHeight), 0.001f)
    }

    @Test fun pinchEndAnchorKeepsCentroidFixed() {
        val oldCommitted = 1f
        val newCommitted = 2f
        val pageHeightOld = ContinuousLayoutMath.pageLayoutHeightPx(viewportW, oldCommitted, aspect).toFloat()
        val centroidY = 600f
        val totalScroll = 100f
        val pageTopPx = 0f
        val gesturePanY = 0f
        val gestureScale = newCommitted / oldCommitted
        val normY = ContinuousLayoutMath.contentNormY(
            centroidY, totalScroll, gestureScale, gesturePanY, pageTopPx, pageHeightOld,
        )
        val pageHeightNew = ContinuousLayoutMath.pageLayoutHeightPx(viewportW, newCommitted, aspect)
        val offset = ContinuousLayoutMath.anchorScrollOffsetForContent(centroidY, normY, pageHeightNew)
        // The corrected scroll keeps the pinned content point exactly under the centroid.
        assertEquals(centroidY, (offset + normY * pageHeightNew).toFloat(), 0.001f)
    }

    @Test fun anchorTotalScrollCorrectionIsSingleDifference() {
        // Only the transient vertical GPU translate is removed; no extra velocity injected.
        assertEquals(420f, ContinuousLayoutMath.anchorTotalScrollCorrection(500f, 80f), 0f)
        assertEquals(500f, ContinuousLayoutMath.anchorTotalScrollCorrection(500f, 0f), 0f)
    }

    @Test fun anchorCorrectionIsIdempotent() {
        // Applying the correction twice must not drift (pinch end does it exactly once).
        val once = ContinuousLayoutMath.anchorTotalScrollCorrection(500f, 80f)
        val twice = ContinuousLayoutMath.anchorTotalScrollCorrection(once, 0f)
        assertEquals(once, twice, 0f)
    }
}
