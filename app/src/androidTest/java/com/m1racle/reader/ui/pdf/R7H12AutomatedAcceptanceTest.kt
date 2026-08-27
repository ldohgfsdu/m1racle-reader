package com.m1racle.reader.ui.pdf

import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** R7 H1.2 QA-only production-composable acceptance driven by real injected MotionEvents. */
@RunWith(AndroidJUnit4::class)
class R7H12AutomatedAcceptanceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val pendingMetricEvidence = mutableListOf<String>()

    @Before
    fun requireQaTarget() {
        assertEquals("com.m1racle.reader.qa", instrumentation.targetContext.packageName)
    }

    @Test
    fun h1FourEdgeOverscroll() = horizontalScenario("H1_FOUR_EDGE_OVERSCROLL") { activity, trace ->
        trace.pinch1To2Point5()
        waitUntil { activity.interactionSnapshot().scale in 2.35f..2.65f }
        val page = activity.interactionSnapshot().pageIndex

        // Left X edge then a light outward pull below the handoff threshold.
        trace.zoomedEdgeDragLeft()
        waitForSpring(activity)
        trace.singleSwipe(0.55f, 0.45f, 0.50f, 0.45f, frames = 12)
        waitForSpring(activity)

        // Traverse to the right X edge without one large edge residual, then lightly pull out.
        trace.zoomedEdgeDragRight()
        trace.singleSwipe(0.15f, 0.45f, 0.80f, 0.45f)
        // Vertical-dominant final approach guarantees the right edge without arming Pager.
        trace.singleSwipe(0.15f, 0.30f, 0.35f, 0.58f, frames = 18)
        waitForSpring(activity)
        trace.singleSwipe(0.45f, 0.45f, 0.50f, 0.45f, frames = 12)
        waitForSpring(activity)

        // Top Y edge then bottom Y edge. Vertical residual can never arm Pager.
        trace.singleSwipe(0.50f, 0.78f, 0.50f, 0.18f, frames = 32)
        waitForSpring(activity)
        trace.singleSwipe(0.50f, 0.18f, 0.50f, 0.82f, frames = 32)
        trace.singleSwipe(0.50f, 0.18f, 0.50f, 0.82f, frames = 32)
        waitForSpring(activity)

        val result = activity.interactionSnapshot()
        val density = activity.densityValue()
        assertTrue(result.minVisualOverscrollXPx < -1f)
        assertTrue(result.maxPositiveVisualOverscrollXPx > 1f)
        assertTrue(result.minVisualOverscrollYPx < -1f)
        assertTrue(result.maxPositiveVisualOverscrollYPx > 1f)
        assertTrue(result.maxVisualOverscrollXPx / density <= 24.5f)
        assertTrue(result.maxVisualOverscrollYPx / density <= 20.5f)
        assertTrue(result.legalDragGainX in 0.98f..1.02f)
        assertTrue(result.legalDragGainY in 0.98f..1.02f)
        assertTrue(result.pan.legalOffsetX.isFinite() && result.pan.legalOffsetY.isFinite())
        assertEquals(0f, result.pan.visualOverscrollX, 0.75f)
        assertEquals(0f, result.pan.visualOverscrollY, 0.75f)
        assertEquals(page, result.pageIndex)
        recordQaEvidence(
            "H1_QA_METRICS scenario=H1_FOUR_EDGE_OVERSCROLL " +
                "legalDragGainX=${result.legalDragGainX} legalDragGainY=${result.legalDragGainY} " +
                "maxVisualOverscrollXDp=${result.maxVisualOverscrollXPx / density} " +
                "maxVisualOverscrollYDp=${result.maxVisualOverscrollYPx / density} " +
                "springDurationMs=${result.springDurationMs} pageChangeCount=${result.pageChangeCount}",
        )
    }

    @Test
    fun h1ZoomedHorizontalHandoff() = horizontalScenario("H1_ZOOMED_HORIZONTAL_HANDOFF") { activity, trace ->
        trace.pinch1To2Point5()
        waitUntil { activity.interactionSnapshot().scale > 2.3f }
        waitUntil { (activity.viewportSessionSnapshot()?.scale ?: 0f) > 2.3f }
        val startPage = activity.interactionSnapshot().pageIndex
        val outgoingAnchorY = checkNotNull(activity.viewportSessionSnapshot()).pageAnchorYFraction
        trace.zoomedEdgeDragLeft()
        waitForSpring(activity)

        trace.cancelledEdgeHandoff()
        waitForSpring(activity)
        assertEquals(startPage, activity.interactionSnapshot().pageIndex)

        trace.zoomedPageHandoff()
        waitUntil(3_000L) { activity.interactionSnapshot().pageIndex == startPage + 1 }
        val result = activity.interactionSnapshot()
        assertEquals(1, result.pageChangeCount)
        assertEquals(startPage + 1, result.pageIndex)
        assertTrue(result.handoffAccumulatedPx / activity.densityValue() >= 36f)
        assertTrue(result.outgoingScaleBeforeCommit > 2.3f)
        assertEquals(result.outgoingScaleBeforeCommit, result.outgoingScaleAfterCommit, 0.001f)
        assertEquals(result.outgoingScaleBeforeCommit, result.targetScaleAfterCommit, 0.001f)
        assertEquals(result.outgoingScaleBeforeCommit, result.scale, 0.001f)
        assertTrue(result.pan.legalOffsetX.isFinite())
        assertTrue(result.pan.legalOffsetY.isFinite())
        val targetSession = checkNotNull(activity.viewportSessionSnapshot())
        assertEquals(result.scale, targetSession.scale, 0.001f)
        assertEquals(outgoingAnchorY, targetSession.pageAnchorYFraction, 0.01f)
        recordQaEvidence(
            "H1_QA_METRICS scenario=H1_ZOOMED_HORIZONTAL_HANDOFF " +
                "handoffAccumulatedDp=${result.handoffAccumulatedPx / activity.densityValue()} " +
                "pageChangeCount=${result.pageChangeCount} " +
                "outgoingScaleBeforeCommit=${result.outgoingScaleBeforeCommit} " +
                "outgoingScaleAfterCommit=${result.outgoingScaleAfterCommit} " +
                "targetScaleAfterCommit=${result.targetScaleAfterCommit}",
        )
    }

    @Test
    fun h1ReturnPageKeepsSessionScale() = horizontalScenario("H1_RETURN_PAGE_SCALE_CONTINUITY") { activity, trace ->
        trace.pinch1To2Point5()
        waitUntil { activity.interactionSnapshot().scale > 2.3f }
        val originalPage = activity.interactionSnapshot().pageIndex
        trace.zoomedEdgeDragLeft()
        waitForSpring(activity)
        trace.zoomedPageHandoff()
        waitUntil(3_000L) { activity.interactionSnapshot().pageIndex == originalPage + 1 }

        trace.singleSwipe(0.18f, 0.45f, 0.86f, 0.45f, frames = 30, accelerating = true)
        waitUntil(3_000L) { activity.interactionSnapshot().pageIndex == originalPage }
        val returned = activity.interactionSnapshot()
        assertEquals(2, returned.pageChangeCount)
        assertTrue(returned.scale > 2.3f)
        assertEquals(returned.outgoingScaleBeforeCommit, returned.scale, 0.001f)
        assertTrue(returned.pan.legalOffsetX.isFinite())
        assertTrue(returned.pan.legalOffsetY.isFinite())
        recordQaEvidence(
            "H1_QA_METRICS scenario=H1_RETURN_PAGE_SCALE_CONTINUITY pageChangeCount=${returned.pageChangeCount} " +
                "returnPageScale=${returned.scale}",
        )
    }

    @Test
    fun h1VerticalNoNavigation() = horizontalScenario("H1_VERTICAL_NO_NAVIGATION") { activity, trace ->
        trace.pinch1To2Point5()
        waitUntil { activity.interactionSnapshot().scale > 2.3f }
        val page = activity.interactionSnapshot().pageIndex
        trace.singleSwipe(0.50f, 0.78f, 0.50f, 0.18f, frames = 36)
        waitForSpring(activity)
        trace.singleSwipe(0.50f, 0.18f, 0.50f, 0.82f, frames = 36)
        trace.singleSwipe(0.50f, 0.18f, 0.50f, 0.82f, frames = 36)
        waitForSpring(activity)
        val result = activity.interactionSnapshot()
        assertTrue(result.maxVisualOverscrollYPx > 1f)
        assertEquals(0f, result.pan.visualOverscrollY, 0.75f)
        assertEquals(page, result.pageIndex)
        assertEquals(0, result.pageChangeCount)
        assertFalse(result.handoffPhase == com.m1racle.reader.pdf.ZoomedPageTransitionPhase.EDGE_PREVIEW)
        recordQaEvidence(
            "H1_QA_METRICS scenario=H1_VERTICAL_NO_NAVIGATION " +
                "maxVisualOverscrollYDp=${result.maxVisualOverscrollYPx / activity.densityValue()} " +
                "pageChangeCount=${result.pageChangeCount} handoffPhase=${result.handoffPhase}",
        )
    }

    @Test
    fun h1PointerTwoOneTwo() = horizontalScenario("H1_POINTER_2_1_2") { activity, trace ->
        trace.pinchMovingCentroid()
        trace.pointer2To1To2()
        waitUntil { activity.interactionSnapshot().scale > 1.5f }
        val result = activity.interactionSnapshot()
        assertTrue(result.pointerTransitionMaxJumpPx / activity.densityValue() <= 1f)
        assertTrue(result.maxFocalAnchorDriftPx / activity.densityValue() <= 2f)
        assertTrue(result.scale.isFinite())
        assertTrue(result.pan.legalOffsetX.isFinite() && result.pan.legalOffsetY.isFinite())
        assertEquals(H1QaHarnessActivity.INITIAL_PAGE, result.pageIndex)
        recordQaEvidence(
            "H1_QA_METRICS scenario=H1_POINTER_2_1_2 " +
                "pointerTransitionMaxJumpDp=${result.pointerTransitionMaxJumpPx / activity.densityValue()} " +
                "focalAnchorDriftDp=${result.maxFocalAnchorDriftPx / activity.densityValue()} " +
                "scale=${result.scale} pageChangeCount=${result.pageChangeCount}",
        )
    }

    @Test
    fun realSharedGestureParity() {
        var continuousGainX = 0f
        var continuousGainY = 0f
        continuousScenario("REAL_SHARED_GESTURE_PARITY_CONTINUOUS") { activity, trace ->
            val before = activity.transformSnapshot()
            val bounds = activity.contentBoundsOnScreen()
            trace.singleDragWithStationaryTail(0.50f, 0.52f, 0.56f, 0.48f)
            waitUntil { activity.transformSnapshot() != before }
            val after = activity.transformSnapshot()
            continuousGainX = abs(after.scrollX - before.scrollX) / (bounds.width() * 0.06f)
            continuousGainY = abs(after.scrollY - before.scrollY) / (bounds.height() * 0.04f)
        }

        var horizontalGainX = 0f
        var horizontalGainY = 0f
        horizontalScenario("REAL_SHARED_GESTURE_PARITY_HORIZONTAL") { activity, trace ->
            trace.pinch1To2Point5()
            waitUntil { activity.interactionSnapshot().scale > 2.3f }
            trace.singleDragWithStationaryTail(0.50f, 0.52f, 0.56f, 0.48f)
            val snapshot = activity.interactionSnapshot()
            horizontalGainX = snapshot.legalDragGainX
            horizontalGainY = snapshot.legalDragGainY
            assertTrue(snapshot.maxFocalAnchorDriftPx / activity.densityValue() <= 2f)
        }

        assertTrue(abs(continuousGainX - horizontalGainX) <= 0.05f)
        assertTrue(abs(continuousGainY - horizontalGainY) <= 0.05f)
        recordQaEvidence(
            "H1_QA_METRICS scenario=REAL_SHARED_GESTURE_PARITY " +
                "continuousGainX=$continuousGainX horizontalGainX=$horizontalGainX " +
                "continuousGainY=$continuousGainY horizontalGainY=$horizontalGainY",
        )
    }

    @Test
    fun h1BottomBackground() = horizontalScenario("H1_BOTTOM_BACKGROUND") { activity, _ ->
        waitUntil { activity.interactionSnapshot().fittedPageHeightPx > 0f }
        val result = activity.interactionSnapshot()
        val density = activity.densityValue()
        val bounds = activity.contentBoundsOnScreen()
        assertTrue(result.pageTopPx / density <= 12f)
        assertEquals(bounds.width().toFloat(), result.viewportWidthPx, 2f)
        assertEquals(bounds.height().toFloat(), result.viewportHeightPx, 2f)
        assertEquals(1.3833f, result.fittedPageHeightPx / result.fittedPageWidthPx, 0.01f)

        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        val pageBottom = bounds.top + result.pageTopPx + result.fittedPageHeightPx
        val sampleY = (pageBottom + 32f * density)
            .toInt()
            .coerceAtMost(bounds.bottom - (96f * density).toInt())
        val sampleX = bounds.centerX()
        val pixel = screenshot.getPixel(sampleX, sampleY)
        assertTrue(Color.red(pixel) >= 248)
        assertTrue(Color.green(pixel) >= 248)
        assertTrue(Color.blue(pixel) >= 248)
        recordQaEvidence(
            "H1_QA_METRICS scenario=H1_BOTTOM_BACKGROUND topGapDp=${result.pageTopPx / density} " +
                "viewport=${result.viewportWidthPx}x${result.viewportHeightPx} " +
                "page=${result.fittedPageWidthPx}x${result.fittedPageHeightPx} " +
                "unusedRegionArgb=${Integer.toHexString(pixel)} classification=PAGE_WHITE",
        )
    }

    @Test
    fun p0c14Regression() {
        val started = SystemClock.elapsedRealtime()
        val intent = Intent().setComponent(
            ComponentName(
                instrumentation.targetContext.packageName,
                H1QaContinuousHarnessActivity::class.java.name,
            ),
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        H1QaContinuousHarnessActivity.currentInstance?.let { stale ->
            instrumentation.runOnMainSync { stale.finish() }
            waitUntil { H1QaContinuousHarnessActivity.currentInstance == null }
        }
        launchFromQaShell(intent)
        waitUntil { H1QaContinuousHarnessActivity.currentInstance != null }
        val activity = checkNotNull(H1QaContinuousHarnessActivity.currentInstance)
        try {
            waitUntil { activity.contentBoundsOnScreen().width() > 0 }
            waitUntil(3_000L) { activity.hasWindowFocus() }
            val trace = H1QaMotionEventTrace(instrumentation, activity.contentBoundsOnScreen())
            trace.singleSwipe(0.50f, 0.72f, 0.50f, 0.28f, frames = 36)
            waitUntil(3_000L) { activity.tileMetrics().pointerDownSwaps > 0 }
            val result = activity.tileMetrics()
            assertTrue(result.pointerDownRequests > 0)
            assertTrue(result.pointerDownSwaps > 0)
            assertTrue(result.coverageIncreasedBeforeRelease)
            assertTrue(result.coverageBeforeRelease > 0f)
            assertTrue(result.transformUnchangedBySwap)
            recordQaEvidence(
                "H1_QA_METRICS scenario=P0C1_4_REGRESSION " +
                    "pointerDownRequests=${result.pointerDownRequests} " +
                    "pointerDownSwaps=${result.pointerDownSwaps} " +
                    "visibleCoverageBeforeRelease=${result.coverageBeforeRelease} " +
                    "coverageIncreased=${result.coverageIncreasedBeforeRelease} " +
                    "transformUnchanged=${result.transformUnchangedBySwap}",
            )
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            waitUntil { H1QaContinuousHarnessActivity.currentInstance == null }
        }
        val durationMs = SystemClock.elapsedRealtime() - started
        Log.i(TAG, "H1_QA_SCENARIO name=P0C1_4_REGRESSION durationMs=$durationMs")
        recordQaEvidence("H1_QA_DURATION scenario=P0C1_4_REGRESSION durationMs=$durationMs")
    }

    private fun horizontalScenario(
        name: String,
        block: (H1QaHarnessActivity, H1QaMotionEventTrace) -> Unit,
    ) {
        val started = SystemClock.elapsedRealtime()
        val intent = Intent().setComponent(
            ComponentName(
                instrumentation.targetContext.packageName,
                H1QaHarnessActivity::class.java.name,
            ),
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        H1QaHarnessActivity.currentInstance?.let { stale ->
            instrumentation.runOnMainSync { stale.finish() }
            waitUntil { H1QaHarnessActivity.currentInstance == null }
        }
        launchFromQaShell(intent)
        waitUntil { H1QaHarnessActivity.currentInstance != null }
        val activity = checkNotNull(H1QaHarnessActivity.currentInstance)
        try {
            waitUntil { activity.contentBoundsOnScreen().width() > 0 }
            waitUntil(3_000L) { activity.hasWindowFocus() }
            SystemClock.sleep(180L)
            val trace = H1QaMotionEventTrace(instrumentation, activity.contentBoundsOnScreen())
            block(activity, trace)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            waitUntil { H1QaHarnessActivity.currentInstance == null }
        }
        val durationMs = SystemClock.elapsedRealtime() - started
        Log.i(TAG, "H1_QA_SCENARIO name=$name durationMs=$durationMs")
        recordQaEvidence("H1_QA_DURATION scenario=$name durationMs=$durationMs")
    }

    private fun continuousScenario(
        name: String,
        block: (H1QaContinuousHarnessActivity, H1QaMotionEventTrace) -> Unit,
    ) {
        val started = SystemClock.elapsedRealtime()
        val intent = Intent().setComponent(
            ComponentName(
                instrumentation.targetContext.packageName,
                H1QaContinuousHarnessActivity::class.java.name,
            ),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        H1QaContinuousHarnessActivity.currentInstance?.let { stale ->
            instrumentation.runOnMainSync { stale.finish() }
            waitUntil { H1QaContinuousHarnessActivity.currentInstance == null }
        }
        launchFromQaShell(intent)
        waitUntil { H1QaContinuousHarnessActivity.currentInstance != null }
        val activity = checkNotNull(H1QaContinuousHarnessActivity.currentInstance)
        try {
            waitUntil { activity.contentBoundsOnScreen().width() > 0 }
            waitUntil(3_000L) { activity.hasWindowFocus() }
            val trace = H1QaMotionEventTrace(instrumentation, activity.contentBoundsOnScreen())
            block(activity, trace)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            waitUntil { H1QaContinuousHarnessActivity.currentInstance == null }
        }
        recordQaEvidence(
            "H1_QA_DURATION scenario=$name durationMs=${SystemClock.elapsedRealtime() - started}",
        )
    }

    private fun waitForSpring(activity: H1QaHarnessActivity) {
        waitUntil(2_000L) {
            val pan = activity.interactionSnapshot().pan
            abs(pan.visualOverscrollX) <= 0.75f && abs(pan.visualOverscrollY) <= 0.75f
        }
    }

    private fun waitUntil(timeoutMs: Long = 2_000L, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (!condition()) {
            if (SystemClock.elapsedRealtime() >= deadline) {
                throw AssertionError("Condition not reached within ${timeoutMs}ms")
            }
            SystemClock.sleep(20L)
        }
    }

    private fun launchFromQaShell(intent: Intent) {
        val component = checkNotNull(intent.component)
        val command = "am start --display 0 -W -n ${component.packageName}/${component.className}"
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command),
        ).bufferedReader().use { reader ->
            val output = reader.readText()
            check(output.contains("Status: ok")) { "QA activity launch failed: $output" }
        }
    }

    private fun recordQaEvidence(message: String) {
        if (message.startsWith("H1_QA_DURATION")) {
            pendingMetricEvidence.forEach { Log.i(TAG, it) }
            pendingMetricEvidence.clear()
            Log.i(TAG, message)
        } else {
            pendingMetricEvidence += message
        }
        kotlin.io.println(message)
    }

    companion object {
        private const val TAG = "M1racleReaderH1Qa"
    }
}
