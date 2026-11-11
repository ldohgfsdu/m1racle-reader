package com.m1racle.reader.ui.pdf

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m1racle.reader.core.model.PdfViewportSessionState
import com.m1racle.reader.pdf.ContinuousScrollPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Regression for a logical page count and a previously visited aspect arriving
 * after continuous movement has started. Existing page coordinates stay frozen
 * until the pointer epoch ends, then the top reading line is remapped once.
 */
@RunWith(AndroidJUnit4::class)
class PdfLatePageCountInstrumentedTest {

    @get:Rule
    val rule = createAndroidComposeRule<PdfLatePageCountHarnessActivity>()

    @Test
    fun latePageCountDuringDragDoesNotJumpAndKeepsTopReadingLine() {
        jumpAndAwaitPrimaryPage(1)
        rule.activity.armLatePageCountDuringNextMotion()

        val node = rule.onNodeWithTag("r7-continuous-viewport")
        val stepPx = with(rule.density) { 8.dp.toPx() }
        node.performTouchInput { down(center) }
        repeat(5) {
            node.performTouchInput { moveBy(Offset(0f, -stepPx)) }
            rule.waitForIdle()
        }
        rule.waitUntil(10_000) {
            rule.activity.appliedMotionPhase() == ContinuousScrollPhase.DRAGGING
        }
        repeat(5) {
            node.performTouchInput { moveBy(Offset(0f, -stepPx)) }
            rule.waitForIdle()
        }

        val frames = rule.activity.snapshots()
        val injectedFrame = frames.indexOfFirst { it.pageCount == 8 }
        assertTrue("late page count must be observable in a motion frame", injectedFrame >= 0)
        assertTrue(
            "the pointer epoch must keep moving after the late page count",
            frames.drop(injectedFrame + 1).count {
                it.snapshot.phase == ContinuousScrollPhase.DRAGGING &&
                    it.snapshot.contentAnchorDeltaY != 0f
            } >= 3,
        )
        assertContinuousUpwardMotion(frames, injectedFrame)
        assertEquals(8, rule.activity.currentPageCount())
        assertEquals(
            "geometry must stay frozen on the pre-commit aspect",
            1.414f,
            requireNotNull(rule.activity.capturedPageAspect(0)),
            0.000001f,
        )

        val motionFinalAnchor = rule.runOnIdle {
            requireNotNull(rule.activity.captureTopAnchor())
        }
        node.performTouchInput {
            advanceEventTime(200)
            up()
        }

        rule.waitUntil(10_000) {
            rule.activity.capturedDocumentState()?.gestureActive == false &&
                rule.activity.latestPhase() == ContinuousScrollPhase.IDLE &&
                rule.activity.capturedPageAspect(0)?.let {
                    abs(it - 0.55f) <= 0.000001f
                } == true
        }
        rule.waitForIdle()
        val committedAnchor = rule.runOnIdle {
            requireNotNull(rule.activity.captureTopAnchor())
        }

        assertSameReadingLine(motionFinalAnchor, committedAnchor)
    }

    private fun jumpAndAwaitPrimaryPage(pageIndex: Int) {
        rule.activity.requestPageJump(pageIndex)
        rule.waitUntil(10_000) {
            rule.activity.snapshots().any {
                it.snapshot.primaryPage == pageIndex &&
                    it.snapshot.phase == ContinuousScrollPhase.IDLE
            }
        }
        rule.waitForIdle()
    }

    private fun assertContinuousUpwardMotion(
        frames: List<PdfLatePageCountHarnessActivity.TimedSnapshot>,
        injectedFrame: Int,
    ) {
        var previousScreenY: Float? = null
        frames.withIndex().forEach { (index, timed) ->
            val snapshot = timed.snapshot
            if (snapshot.phase != ContinuousScrollPhase.DRAGGING || snapshot.primaryPage != 1) {
                previousScreenY = null
                return@forEach
            }
            if (snapshot.contentAnchorDeltaY == 0f) return@forEach
            if (index > injectedFrame) {
                assertTrue(
                    "late page count reversed upward motion at frame $index",
                    snapshot.contentAnchorDeltaY <= 0f,
                )
            }
            previousScreenY?.let { previous ->
                assertTrue(
                    "same-page screen top moved backward at frame $index: " +
                        "$previous -> ${snapshot.contentAnchorScreenY}",
                    snapshot.contentAnchorScreenY <= previous,
                )
            }
            previousScreenY = snapshot.contentAnchorScreenY
        }
    }

    private fun assertSameReadingLine(
        motionFinal: PdfViewportSessionState,
        committed: PdfViewportSessionState,
    ) {
        assertEquals(motionFinal.pageIndex, committed.pageIndex)
        assertEquals(0f, committed.viewportAnchorYFraction, 0f)
        assertEquals(
            motionFinal.pageAnchorYFraction,
            committed.pageAnchorYFraction,
            0.0005f,
        )
    }
}
