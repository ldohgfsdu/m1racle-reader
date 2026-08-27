package com.m1racle.reader.ui.pdf

import android.content.ComponentName
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.m1racle.reader.pdf.ZoomedPageTransitionPhase
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class R7H15ThreePageStripInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun REAL_1X_HOLD_NO_TWITCH() = scenario { activity, trace ->
        val original = activity.interactionSnapshot()
        val originalPage = original.pageIndex
        val width = activity.contentBoundsOnScreen().width().toFloat()
        val path = linearPath(0.82f, 0.18f, 30) +
            listOf(0.20f, 0.17f, 0.21f, 0.18f, 0.20f, 0.18f)
        var lastPointerX = 0.82f
        var lastDisplayedOffset = original.pageTransitionOffsetPx
        var directionalFrames = 0
        var heldSlots: List<Int>? = null
        trace.singleDragHoldWithPath(
            startX = 0.82f,
            startY = 0.46f,
            pathX = path,
            onFrame = { _, pointerX ->
                val live = activity.interactionSnapshot()
                val inputDelta = (pointerX - lastPointerX) * width
                val displayedDelta = live.pageTransitionOffsetPx - lastDisplayedOffset
                assertTrue(live.pointerDown)
                assertEquals(originalPage, live.pageIndex)
                assertEquals(0, live.pageIndexChangeCount)
                assertEquals(0, live.slotRotationCount)
                assertEquals(0, live.settleStartCount)
                assertEquals(0, live.offsetRebaseCount)
                assertEquals(0, live.pagerProgrammaticScrollCount)
                assertTrue(!live.settleActive)
                val liveSlots = listOf(live.previousSlotId, live.currentSlotId, live.nextSlotId)
                heldSlots?.let { assertEquals(it, liveSlots) } ?: run { heldSlots = liveSlots }
                if (abs(inputDelta) > 1f && abs(displayedDelta) > 1f) {
                    assertTrue(
                        "display delta $displayedDelta must follow input delta $inputDelta",
                        inputDelta * displayedDelta > 0f,
                    )
                    directionalFrames += 1
                }
                lastPointerX = pointerX
                lastDisplayedOffset = live.pageTransitionOffsetPx
            },
            beforeRelease = {
                val held = activity.interactionSnapshot()
                assertTrue(held.pointerDown)
                assertEquals(originalPage, held.pageIndex)
                assertEquals(0, held.pageIndexChangeCount)
                assertEquals(0, held.slotRotationCount)
                assertEquals(0, held.settleStartCount)
                assertEquals(0, held.offsetRebaseCount)
                assertEquals(0, held.pagerProgrammaticScrollCount)
                assertTrue(!held.settleActive)
                assertTrue(held.nextBaseReady)
                assertTrue(held.visibleNeighborBitmapId != -1)
                assertTrue(abs(held.pageTransitionOffsetPx) > width * 0.5f)
                assertTrue(held.pageStripDragGainX in 0.97f..1.03f)
                assertEquals(0, held.reverseDeltaCount)
                assertTrue(directionalFrames >= 30)
            },
        )
        waitUntil(3_000L) { activity.interactionSnapshot().pageIndex == originalPage + 1 }
        val settled = activity.interactionSnapshot()
        assertEquals(1, settled.pageIndexChangeCount)
        assertEquals(1, settled.slotRotationCount)
        assertEquals(1, settled.settleStartCount)
        assertEquals(1, settled.offsetRebaseCount)
        assertTrue(settled.slotRotationVisualJumpPx <= 1f)
        assertEquals(0f, settled.pageTransitionOffsetPx, 0f)
    }

    @Test
    fun REAL_1X_DRAG_BACK_BEFORE_RELEASE() = scenario { activity, trace ->
        val originalPage = activity.interactionSnapshot().pageIndex
        val path = linearPath(0.82f, 0.16f, 30) + linearPath(0.16f, 0.55f, 18)
        trace.singleDragHoldWithPath(
            startX = 0.82f,
            startY = 0.46f,
            pathX = path,
            beforeRelease = {
                val held = activity.interactionSnapshot()
                assertEquals(originalPage, held.pageIndex)
                assertEquals(0, held.slotRotationCount)
                assertEquals(0, held.settleStartCount)
                assertEquals(0, held.offsetRebaseCount)
                assertTrue(held.pointerDown)
            },
        )
        waitUntil(3_000L) {
            val live = activity.interactionSnapshot()
            live.settleStartCount == 1 && abs(live.pageTransitionOffsetPx) <= 1f
        }
        val cancelled = activity.interactionSnapshot()
        assertEquals(originalPage, cancelled.pageIndex)
        assertEquals(0, cancelled.pageIndexChangeCount)
        assertEquals(0, cancelled.slotRotationCount)
        assertEquals(1, cancelled.settleStartCount)
        assertEquals(0, cancelled.offsetRebaseCount)
        assertTrue(abs(cancelled.stripSettleFinalPx) <= 1f)
    }

    @Test
    fun REAL_ONE_GESTURE_ONE_PAGE() = scenario { activity, trace ->
        val originalPage = activity.interactionSnapshot().pageIndex
        val width = activity.contentBoundsOnScreen().width().toFloat()
        trace.singleDragHoldWithPath(
            startX = 0.90f,
            startY = 0.46f,
            pathX = linearPath(0.90f, -0.70f, 48),
            allowOutsideViewport = true,
            beforeRelease = {
                val held = activity.interactionSnapshot()
                assertEquals(originalPage, held.pageIndex)
                assertEquals(0, held.pageIndexChangeCount)
                assertEquals(0, held.slotRotationCount)
                assertEquals(0, held.settleStartCount)
                assertTrue(abs(abs(held.pageTransitionOffsetPx) - width) <= 1f)
                assertTrue(held.nextBaseReady)
            },
        )
        waitUntil(3_000L) { activity.interactionSnapshot().pageIndex != originalPage }
        val committed = activity.interactionSnapshot()
        assertEquals(originalPage + 1, committed.pageIndex)
        assertEquals(1, committed.pageIndexChangeCount)
        assertEquals(1, committed.slotRotationCount)
        assertEquals(1, committed.settleStartCount)
        assertEquals(1, committed.offsetRebaseCount)
        assertEquals(0f, committed.pageTransitionOffsetPx, 0f)
    }

    @Test
    fun realThreePageStripPreviewAtOneXKeepsPageIndexAndReadyNeighbour() = scenario { activity, trace ->
        val originalPage = activity.interactionSnapshot().pageIndex
        trace.singleDragHold(0.78f, 0.46f, 0.48f, 0.46f, frames = 24) {
            val live = activity.interactionSnapshot()
            assertEquals(originalPage, live.pageIndex)
            assertEquals(ZoomedPageTransitionPhase.EDGE_PREVIEW, live.pageTransitionPhase)
            assertTrue(live.pageTransitionOffsetPx < 0f)
            assertTrue(live.currentBaseReady)
            assertTrue(live.nextBaseReady)
            assertTrue(live.visibleNeighborBitmapId != -1)
            emitEvidence(
                "H15_QA_PREVIEW page=${live.pageIndex} offset=${live.pageTransitionOffsetPx} " +
                    "nextBaseReady=${live.nextBaseReady} bitmapId=${live.visibleNeighborBitmapId}",
            )
        }
    }

    @Test
    fun realPageStripCancelReturnsToExactZero() = scenario { activity, trace ->
        val originalPage = activity.interactionSnapshot().pageIndex
        trace.singleDragWithStationaryTail(
            startX = 0.58f,
            startY = 0.46f,
            endX = 0.52f,
            endY = 0.46f,
            frames = 12,
            stationaryFrames = 12,
        )
        waitUntil { activity.interactionSnapshot().pageTransitionPhase == ZoomedPageTransitionPhase.CANCELLING ||
            abs(activity.interactionSnapshot().pageTransitionOffsetPx) <= 1f }
        waitUntil { abs(activity.interactionSnapshot().pageTransitionOffsetPx) <= 1f }
        val settled = activity.interactionSnapshot()
        assertEquals("cancel snapshot=$settled", originalPage, settled.pageIndex)
        assertEquals(0f, settled.stripSettleTargetPx, 0f)
        assertTrue(abs(settled.stripSettleFinalPx) <= 1f)
        emitEvidence(
            "H15_QA_CANCEL target=${settled.stripSettleTargetPx} " +
                "final=${settled.stripSettleFinalPx} rebase=${settled.pageTransitionOffsetPx}",
        )
    }

    @Test
    fun realPageStripCommitChangesPageOnlyAfterExactSettleAndRebase() = scenario { activity, trace ->
        val originalPage = activity.interactionSnapshot().pageIndex
        trace.singleSwipe(0.84f, 0.46f, 0.08f, 0.46f, frames = 36, accelerating = true)
        waitUntil(3_000L) { activity.interactionSnapshot().pageIndex == originalPage + 1 }
        val committed = activity.interactionSnapshot()
        assertEquals(originalPage + 1, committed.pageIndex)
        assertEquals(1, committed.pageChangeCount)
        assertTrue(abs(abs(committed.stripSettleFinalPx) - activity.contentBoundsOnScreen().width()) <= 1f)
        assertEquals(0f, committed.pageTransitionOffsetPx, 0f)
        assertTrue(committed.pageIndexChangeTimeNanos > 0L)
        emitEvidence(
            "H15_QA_NEXT_COMMIT page=${committed.pageIndex} target=${committed.stripSettleTargetPx} " +
                "final=${committed.stripSettleFinalPx} rebase=${committed.pageTransitionOffsetPx} " +
                "latencyMs=${committed.inputLatencyP50Ms}/${committed.inputLatencyP95Ms}/${committed.maxInputLatencyMs}",
        )
    }

    @Test
    fun realPreviousPageCommitUsesSameStripAndExactRebase() = scenario { activity, trace ->
        val originalPage = activity.interactionSnapshot().pageIndex
        trace.singleSwipe(0.16f, 0.46f, 0.92f, 0.46f, frames = 36, accelerating = true)
        waitUntil(3_000L) { activity.interactionSnapshot().pageIndex == originalPage - 1 }
        val committed = activity.interactionSnapshot()
        assertEquals(originalPage - 1, committed.pageIndex)
        assertEquals(1, committed.pageChangeCount)
        assertTrue(abs(committed.stripSettleFinalPx - activity.contentBoundsOnScreen().width()) <= 1f)
        assertEquals(0f, committed.pageTransitionOffsetPx, 0f)
        emitEvidence(
            "H15_QA_PREVIOUS_COMMIT page=${committed.pageIndex} target=${committed.stripSettleTargetPx} " +
                "final=${committed.stripSettleFinalPx} rebase=${committed.pageTransitionOffsetPx}",
        )
    }

    @Test
    fun realZoomedStripPreviewKeepsScaleAndTargetBase() = scenario { activity, trace ->
        trace.pinch1To2Point5()
        waitUntil { activity.interactionSnapshot().scale > 2.3f }
        trace.zoomedEdgeDragLeft()
        SystemClock.sleep(250L)
        val originalPage = activity.interactionSnapshot().pageIndex
        val originalScale = activity.interactionSnapshot().scale
        trace.singleDragHold(0.72f, 0.46f, 0.42f, 0.46f, frames = 24) {
            val live = activity.interactionSnapshot()
            assertEquals(originalPage, live.pageIndex)
            assertEquals(originalScale, live.scale, 0.01f)
            assertTrue(live.nextBaseReady)
            emitEvidence(
                "H15_QA_ZOOM_PREVIEW page=${live.pageIndex} scale=${live.scale} " +
                    "offset=${live.pageTransitionOffsetPx} nextBaseReady=${live.nextBaseReady}",
            )
        }
    }

    private fun emitEvidence(line: String) {
        File(instrumentation.targetContext.filesDir, "h15-three-page-strip-evidence.txt")
            .appendText("$line\n")
    }

    private fun linearPath(start: Float, end: Float, frames: Int): List<Float> =
        (1..frames).map { frame -> start + (end - start) * (frame / frames.toFloat()) }

    private fun scenario(block: (H1QaHarnessActivity, H1QaMotionEventTrace) -> Unit) {
        assertEquals("com.m1racle.reader.qa", instrumentation.targetContext.packageName)
        H1QaHarnessActivity.currentInstance?.let { stale ->
            instrumentation.runOnMainSync { stale.finish() }
            waitUntil { H1QaHarnessActivity.currentInstance == null }
        }
        val component = ComponentName(
            instrumentation.targetContext.packageName,
            H1QaHarnessActivity::class.java.name,
        )
        val output = ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(
                "am start --display 0 -W -n ${component.packageName}/${component.className}",
            ),
        ).bufferedReader().use { it.readText() }
        check(output.contains("Status: ok")) { "QA activity launch failed: $output" }
        waitUntil { H1QaHarnessActivity.currentInstance != null }
        val activity = checkNotNull(H1QaHarnessActivity.currentInstance)
        try {
            waitUntil { activity.contentBoundsOnScreen().width() > 0 }
            waitUntil(3_000L) { activity.hasWindowFocus() }
            SystemClock.sleep(180L)
            block(activity, H1QaMotionEventTrace(instrumentation, activity.contentBoundsOnScreen()))
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            waitUntil { H1QaHarnessActivity.currentInstance == null }
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
}
