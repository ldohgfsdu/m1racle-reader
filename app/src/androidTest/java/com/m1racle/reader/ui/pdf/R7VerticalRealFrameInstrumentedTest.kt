package com.m1racle.reader.ui.pdf

import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.m1racle.reader.pdf.ContinuousScrollPhase
import java.io.File
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G1-3: vertical continuous drag measured on real device frames.
 *
 * The horizontal three-page strip already proves 1:1 tracking for the paged
 * transition (R7H15ThreePageStripInstrumentedTest: `pageStripDragGainX` in
 * 0.97..1.03, `reverseDeltaCount == 0`). Vertical continuous scrolling uses a
 * completely different path — document scrollY instead of strip offset — so this
 * suite measures it independently rather than inferring it from the strip.
 *
 * Method: every frame production actually publishes to the ViewModel is recorded
 * by the QA harness together with the uptime clock. The injected pointer path is
 * recorded by [H1QaMotionEventTrace] with the same clock, so each published frame
 * is paired with the exact finger displacement that preceded it. Gain is then
 * Σ|contentAnchorDeltaY| / Σ|Δy_in| over one uninterrupted same-page run.
 *
 * Sign convention (verified against ContinuousDocumentViewport:1054-1083 and
 * PdfScrollJumpInstrumentedTest:307-311): dragging the finger up makes
 * `contentAnchorDeltaY` negative. Content therefore shares the sign of the finger
 * movement, i.e. correct tracking is `Δy_in * contentAnchorDeltaY > 0`.
 * The handoff note (`Δy_in * (−contentAnchorDeltaY) < 0`) inverts this and would
 * flag every correct frame; see the G1 status report.
 *
 * Emulator only. No physical device was attached, so nothing here is a phone
 * verdict.
 */
@RunWith(AndroidJUnit4::class)
class R7VerticalRealFrameInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @After
    fun restoreHarnessDefaults() {
        H1QaContinuousHarnessActivity.oneToOneScaleOverride = false
    }

    @Test
    fun REAL_VERTICAL_DRAG_TRACKS_FINGER_ONE_TO_ONE_WITH_NO_REVERSE_FRAME() {
        // The production collector suppresses frames while `gestureActive && scale > 1.001`,
        // so the 2.5x tile-test default cannot produce a measurable 1:1 drag.
        H1QaContinuousHarnessActivity.oneToOneScaleOverride = true
        assertEquals("com.m1racle.reader.qa", instrumentation.targetContext.packageName)
        ActivityScenario.launch(H1QaContinuousHarnessActivity::class.java).use { scenario ->
            lateinit var activity: H1QaContinuousHarnessActivity
            scenario.onActivity { activity = it }
            waitUntil { activity.contentBoundsOnScreen().width() > 0 }
            waitUntil(3_000L) { activity.hasWindowFocus() }
            instrumentation.waitForIdleSync()

            val bounds = activity.contentBoundsOnScreen()
            val height = bounds.height().toFloat()
            val trace = H1QaMotionEventTrace(instrumentation, bounds)
            activity.clearViewportFrames()
            trace.clearInjectedFrames()

            // 40 samples, 16 ms apart, ~0.28 viewport heights of travel: short enough
            // to stay inside one page, long enough to yield >= 30 directional frames.
            val startY = 0.62f
            val endY = 0.34f
            val frames = 40
            var window: List<H1QaViewportFrame> = emptyList()
            trace.singleDragSampledHold(
                startX = 0.5f,
                startY = startY,
                endX = 0.5f,
                endY = endY,
                frames = frames,
                sampleFrames = emptySet(),
                onSample = { _, _ -> },
                beforeRelease = {
                    // Pointer is still down; let the main thread drain the last emissions
                    // so the measured window ends at the last injected sample.
                    SystemClock.sleep(250L)
                    window = activity.viewportFrames()
                },
            )

            val injected = trace.injectedFrames()
            assertTrue("no pointer samples recorded", injected.size >= frames)
            val measured = measure(window, injected, height)
            assertTrue("no viewport frame aligned with an injected sample", measured.isNotEmpty())

            // ── One uninterrupted same-page run ────────────────────────────
            val runs = mutableListOf<MutableList<Measured>>()
            measured.forEach { frame ->
                val current = runs.lastOrNull()
                if (current != null && current.last().primaryPage == frame.primaryPage) {
                    current += frame
                } else {
                    runs += mutableListOf(frame)
                }
            }
            val run = runs.maxByOrNull { it.size }
                ?: throw AssertionError("no directional run found; measured=$measured")
            assertTrue(
                "directionalFrames=${run.size}, expected >= 30; runs=${runs.map { it.size }}",
                run.size >= 30,
            )

            // No IDLE may appear inside the measured drag span.
            val spanStart = run.first().index
            val spanEnd = run.last().index
            val idleInside = window.withIndex().count { (index, frame) ->
                index in spanStart..spanEnd && frame.snapshot.phase == ContinuousScrollPhase.IDLE
            }
            assertEquals("IDLE frames inside the drag span", 0, idleInside)

            // ── Cumulative 1:1 gain, ±3 % ───────────────────────────────────
            val totalAnchor = run.sumOf { abs(it.anchorDeltaY.toDouble()) }
            val totalInput = run.sumOf { abs(it.deltaInPx.toDouble()) }
            val gain = totalAnchor / totalInput
            assertTrue(
                "cumulative vertical drag gain=$gain must stay within 0.97..1.03 " +
                    "(anchor=$totalAnchor px, input=$totalInput px, frames=${run.size})",
                gain in 0.97..1.03,
            )

            // ── No reversed displacement on any measured frame ──────────────
            val reversed = run.filter { it.deltaInPx * it.anchorDeltaY < 0f }
            assertTrue(
                "content moved against the finger on ${reversed.size} frames: " +
                    reversed.take(5).joinToString { "dy=${it.deltaInPx} anchor=${it.anchorDeltaY}" },
                reversed.isEmpty(),
            )

            // ── Per-frame gain ─────────────────────────────────────────────
            // A single frame is dominated by touch resampling and 0.5 px motion
            // quantization, so the per-frame bound is the cumulative tolerance plus
            // one pixel expressed as a fraction of the frame's own input travel.
            val perFrameViolations = run.filter { it.deltaInPx >= MIN_PER_FRAME_INPUT_PX }
                .filter { frame ->
                    abs(abs(frame.anchorDeltaY / frame.deltaInPx) - 1f) > 0.03f + 1f / frame.deltaInPx
                }
            assertTrue(
                "per-frame gain out of bounds on ${perFrameViolations.size} frames: " +
                    perFrameViolations.take(5).joinToString {
                        "dy=${it.deltaInPx} anchor=${it.anchorDeltaY}"
                    },
                perFrameViolations.isEmpty(),
            )

            // ── Frame continuity ───────────────────────────────────────────
            val nanos = window.map { it.observedAtNanos }
            assertTrue(
                "observedAtNanos must strictly increase",
                nanos.zipWithNext().all { (a, b) -> b > a },
            )
            val intervalsMs = run.zipWithNext().map { (a, b) -> (b.uptimeMs - a.uptimeMs) }
            val sorted = intervalsMs.sorted()
            val p50 = sorted.getOrElse(sorted.size / 2) { 0L }
            val p95 = sorted.getOrElse((sorted.size * 95) / 100) { 0L }
            val max = sorted.lastOrNull() ?: 0L
            assertTrue(
                "frame interval p95=${p95}ms exceeds 4 display frames; intervals=$intervalsMs",
                p95 <= MAX_INTERVAL_P95_MS,
            )

            emitEvidence(
                buildString {
                    appendLine("R7_VERTICAL_REAL_FRAME heightPx=$height frames=${run.size} runs=${runs.map { it.size }}")
                    appendLine("R7_VERTICAL_REAL_FRAME gain=$gain anchorPx=$totalAnchor inputPx=$totalInput")
                    appendLine("R7_VERTICAL_REAL_FRAME reversed=0 perFrameViolations=0")
                    appendLine("R7_VERTICAL_REAL_FRAME intervalMs p50=$p50 p95=$p95 max=$max")
                    run.forEach { frame ->
                        appendLine(
                            "frame=${frame.index} dy=${frame.deltaInPx} anchor=${frame.anchorDeltaY} " +
                                "page=${frame.primaryPage} phase=${frame.phase} uptimeMs=${frame.uptimeMs}",
                        )
                    }
                },
            )
        }
    }

    // ── measurement ────────────────────────────────────────────────────────

    private data class Measured(
        val index: Int,
        val uptimeMs: Long,
        val primaryPage: Int,
        val phase: ContinuousScrollPhase,
        val deltaInPx: Float,
        val anchorDeltaY: Float,
    )

    /**
     * Pairs each published frame with the finger travel that preceded it.
     *
     * A frame whose `contentAnchorDeltaY` is exactly 0 is a re-baseline frame
     * (page identity, scale or viewport changed) or a no-motion emission; it is
     * dropped instead of being scored as a 0-gain frame.
     */
    private fun measure(
        frames: List<H1QaViewportFrame>,
        injected: List<H1InjectedPointerFrame>,
        heightPx: Float,
    ): List<Measured> {
        if (injected.isEmpty()) return emptyList()
        val out = mutableListOf<Measured>()
        var previousNormY: Float? = null
        frames.forEachIndexed { index, frame ->
            val normY = injectionNormAt(injected, frame.observedAtUptimeMs) ?: return@forEachIndexed
            val previous = previousNormY
            previousNormY = normY
            if (previous == null) return@forEachIndexed
            val snapshot = frame.snapshot
            if (snapshot.phase != ContinuousScrollPhase.DRAGGING) return@forEachIndexed
            if (snapshot.contentAnchorDeltaY == 0f) return@forEachIndexed
            val deltaInPx = (normY - previous) * heightPx
            if (abs(deltaInPx) < 1f) return@forEachIndexed
            out += Measured(
                index = index,
                uptimeMs = frame.observedAtUptimeMs,
                primaryPage = snapshot.primaryPage,
                phase = snapshot.phase,
                deltaInPx = deltaInPx,
                anchorDeltaY = snapshot.contentAnchorDeltaY,
            )
        }
        return out
    }

    /** Last injected pointer position at or before [uptimeMs]. */
    private fun injectionNormAt(
        injected: List<H1InjectedPointerFrame>,
        uptimeMs: Long,
    ): Float? {
        var value: Float? = null
        for (frame in injected) {
            if (frame.actionMasked == MotionEvent.ACTION_UP) break
            if (frame.eventTimeMs > uptimeMs) break
            value = frame.normY
        }
        return value
    }

    private fun emitEvidence(line: String) {
        File(instrumentation.targetContext.filesDir, "r7-vertical-real-frame-evidence.txt")
            .appendText("$line\n")
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

    private companion object {
        /** Below this a single frame cannot carry a 3 % measurement. */
        const val MIN_PER_FRAME_INPUT_PX = 8f

        /** Four 60 Hz display frames: continuity, not jitter, is what this bounds. */
        const val MAX_INTERVAL_P95_MS = 64L
    }
}
