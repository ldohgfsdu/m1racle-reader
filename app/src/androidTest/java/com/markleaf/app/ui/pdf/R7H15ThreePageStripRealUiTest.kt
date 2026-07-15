package com.markleaf.app.ui.pdf

import android.graphics.Bitmap
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.markleaf.app.R
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real QA MainActivity + repository + PdfRenderer evidence for the visible three-page strip. */
@RunWith(AndroidJUnit4::class)
class R7H15ThreePageStripRealUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Before
    fun requireIsolatedQaTarget() {
        assertEquals("com.markleaf.app.qa", instrumentation.targetContext.packageName)
    }

    @Test
    fun REAL_1X_HOLD_NO_TWITCH_REAL_MAIN_ACTIVITY() {
        val trace = prepareMiddlePage()
        shell("logcat -c")
        val path = linearPath(0.82f, 0.18f, 30) +
            listOf(0.20f, 0.17f, 0.21f, 0.18f, 0.20f, 0.18f)
        trace.singleDragHoldWithPath(
            startX = 0.82f,
            startY = 0.46f,
            pathX = path,
            beforeRelease = {
                val lifecycle = h15Logs()
                assertTrue(!lifecycle.contains("H15_STRIP_RELEASE"))
                assertTrue(!lifecycle.contains("H15_PAGE_TRANSITION_COMMIT"))
                assertTrue(!lifecycle.contains("H15_PAGE_TRANSITION_CANCEL"))
                assertTrue(findNode("2 / 3") != null)

                val held = pageStripMetrics()
                assertEquals(1.0, held.getValue("pointerDown"), 0.0)
                assertTrue(held.getValue("pageStripFrames") >= 30.0)
                assertTrue(held.getValue("pageStripGain") in 0.97..1.03)
                assertEquals(0.0, held.getValue("pageStripReverseDeltaCount"), 0.0)
                assertEquals(
                    held.getValue("pageIndexAtPointerDown"),
                    held.getValue("pageIndexDuringPointerDown"),
                    0.0,
                )
                assertTrue(held.getValue("previousSlotId") >= 0.0)
                assertTrue(held.getValue("currentSlotId") >= 0.0)
                assertTrue(held.getValue("nextSlotId") >= 0.0)
                assertTrue(abs(held.getValue("displayedCurrentPageX")) > 600.0)
                assertTrue(held.getValue("displayedNeighborPageX").isFinite())
                assertEquals(0.0, held.getValue("slotRotationCount"), 0.0)
                assertEquals(0.0, held.getValue("settleActive"), 0.0)
                assertEquals(0.0, held.getValue("settleStartCount"), 0.0)
                assertEquals(0.0, held.getValue("offsetRebaseCount"), 0.0)
                assertEquals(0.0, held.getValue("pagerProgrammaticScrollCount"), 0.0)
            },
        )
        waitUntil(3_000L) { h15Logs().contains("H15_PAGE_TRANSITION_COMMIT") }
        val logs = h15Logs()
        assertTrue(logs.contains("pageIndexChangeCount=1"))
        assertTrue(logs.contains("slotRotationCount=1"))
        assertTrue(logs.contains("settleStartCount=1"))
        assertTrue(logs.contains("offsetRebaseCount=1"))
        assertTrue(logs.contains("slotRotationVisualJumpPx=0.0"))
    }

    @Test
    fun realOneXPreviewFramesContainNeighbourSurfaceBeforePageCommit() {
        val trace = prepareMiddlePage()
        shell("logcat -c")
        val evidenceDir = checkNotNull(instrumentation.targetContext.getExternalFilesDir(null))
        val sampled = mutableListOf<Pair<Int, Int>>()
        trace.singleDragSampledHold(
            startX = 0.78f,
            startY = 0.46f,
            endX = 0.42f,
            endY = 0.46f,
            frames = 24,
            sampleFrames = setOf(8, 16, 24),
            onSample = { frame, expectedOffsetFraction ->
                val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
                val file = File(evidenceDir, "h15-three-page-preview-frame-$frame.png")
                FileOutputStream(file).use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
                val darkPixels = neighbourBandDarkPixels(screenshot, abs(expectedOffsetFraction))
                sampled += frame to darkPixels
                screenshot.recycle()
                assertTrue(
                    "frame=$frame must show real neighbour detail instead of a uniform canvas; dark=$darkPixels",
                    darkPixels > 20,
                )
            },
            beforeRelease = {
                val liveLogs = h15Logs()
                assertTrue(liveLogs.contains("H15_STRIP_PREVIEW_START pageIndex=1"))
                assertTrue(liveLogs.contains("targetBaseReady=true") || liveLogs.contains("nextBaseReady=true"))
                assertTrue(!liveLogs.contains("visibleNeighborBitmapId=-1"))
                assertTrue(!liveLogs.contains("H15_PAGE_TRANSITION_COMMIT"))
            },
        )
        waitUntil(3_000L) { h15Logs().contains("H15_PAGE_TRANSITION_COMMIT") }
        val commit = COMMIT.findAll(h15Logs()).lastOrNull()
            ?: throw AssertionError("H15 commit telemetry missing: ${h15Logs()}")
        assertEquals(commit.groupValues[1].toFloat(), commit.groupValues[2].toFloat(), 1f)
        assertEquals(0f, commit.groupValues[3].toFloat(), 0f)
        assertTrue(sampled.size == 3 && sampled.all { it.second > 20 })
    }

    @Test
    fun realZoomedPreviewKeepsTargetBaseReadyAndScaleAcrossCommit() {
        val trace = prepareMiddlePage()
        trace.pinch1To2Point5(movingCentroid = true)
        SystemClock.sleep(450L)
        trace.zoomedEdgeDragLeft()
        SystemClock.sleep(350L)
        shell("logcat -c")
        trace.zoomedPageHandoff()
        waitUntil(3_000L) { h15Logs().contains("H15_PAGE_TRANSITION_COMMIT") }
        val logs = h15Logs()
        val release = RELEASE.findAll(logs).lastOrNull()
            ?: throw AssertionError("H15 release telemetry missing: $logs")
        val commit = SCALE_COMMIT.findAll(logs).lastOrNull()
            ?: throw AssertionError("H15 scale commit telemetry missing: $logs")
        assertEquals("true", release.groupValues[1])
        assertTrue(release.groupValues[2].toInt() != -1)
        assertTrue(commit.groupValues[1].toFloat() > 1.05f)
        assertEquals(commit.groupValues[1].toFloat(), commit.groupValues[2].toFloat(), 0.001f)
    }

    private fun prepareMiddlePage(): H1QaMotionEventTrace {
        val component = "com.markleaf.app.qa/com.markleaf.app.MainActivity"
        val output = shell(
            "am start --display 0 -W -a android.intent.action.VIEW " +
                "-d ${H1QaPdfFixtureProvider.URI} -t application/pdf -f 1 -n $component",
        )
        assertTrue("real QA reader launch failed: $output", output.contains("Status: ok"))
        waitForNode(instrumentation.targetContext.getString(R.string.pdf_reading_mode_open))
        if (findNode(instrumentation.targetContext.getString(R.string.pdf_next_page)) == null) {
            clickNode(instrumentation.targetContext.getString(R.string.pdf_reading_mode_open))
            clickNode(instrumentation.targetContext.getString(R.string.pdf_reading_mode_horizontal))
        }
        SystemClock.sleep(500L)
        repeat(2) {
            clickNodeIfEnabled(instrumentation.targetContext.getString(R.string.pdf_previous_page))
            SystemClock.sleep(250L)
        }
        clickNode(instrumentation.targetContext.getString(R.string.pdf_next_page))
        waitForNode("2 / 3")
        // Page chrome updates before PdfRenderer completes. Warm the real NEXT surface through a
        // settled QA-only navigation, then return to page 2; the three-slot window retains page 3.
        waitForNode(instrumentation.targetContext.getString(R.string.pdf_page_cd, 2, 3))
        clickNode(instrumentation.targetContext.getString(R.string.pdf_next_page))
        waitForNode("3 / 3")
        waitForNode(instrumentation.targetContext.getString(R.string.pdf_page_cd, 3, 3))
        clickNode(instrumentation.targetContext.getString(R.string.pdf_previous_page))
        waitForNode("2 / 3")
        waitForNode(instrumentation.targetContext.getString(R.string.pdf_page_cd, 2, 3))

        val display = checkNotNull(
            instrumentation.targetContext.getSystemService(DisplayManager::class.java).getDisplay(0),
        )
        val metrics = instrumentation.targetContext.createDisplayContext(display).resources.displayMetrics
        val trace = H1QaMotionEventTrace(
            instrumentation = instrumentation,
            viewport = Rect(
                0,
                (metrics.heightPixels * 0.15f).toInt(),
                metrics.widthPixels,
                (metrics.heightPixels * 0.90f).toInt(),
            ),
            displayId = 0,
        )
        trace.pinchToOne()
        SystemClock.sleep(350L)
        return trace
    }

    private fun neighbourBandDarkPixels(bitmap: Bitmap, expectedOffsetFraction: Float): Int {
        val width = bitmap.width
        val height = bitmap.height
        val visibleWidth = (width * expectedOffsetFraction).toInt().coerceAtLeast(1)
        val left = (width - visibleWidth + width * 0.03f).toInt().coerceIn(0, width - 1)
        val right = (width - width * 0.02f).toInt().coerceIn(left + 1, width)
        val top = (height * 0.22f).toInt()
        val bottom = (height * 0.82f).toInt()
        var dark = 0
        for (y in top until bottom step 4) {
            for (x in left until right step 4) {
                val pixel = bitmap.getPixel(x, y)
                val sum = android.graphics.Color.red(pixel) +
                    android.graphics.Color.green(pixel) + android.graphics.Color.blue(pixel)
                if (sum < 660) dark++
            }
        }
        return dark
    }

    private fun h15Logs(): String = shell("logcat -d -s MarkLeafH15:D '*:S'")

    private fun linearPath(start: Float, end: Float, frames: Int): List<Float> =
        (1..frames).map { frame -> start + (end - start) * (frame / frames.toFloat()) }

    private fun pageStripMetrics(): Map<String, Double> {
        val metricsUri = H1QaPdfFixtureProvider.URI.buildUpon().appendPath("metrics").build()
        return checkNotNull(
            instrumentation.targetContext.contentResolver.query(
                metricsUri,
                null,
                null,
                null,
                null,
            ),
        ).use { cursor ->
            check(cursor.moveToFirst())
            (0 until cursor.columnCount).associate { index ->
                cursor.getColumnName(index) to cursor.getDouble(index)
            }
        }
    }

    private fun clickNode(label: String) = clickNode(waitForNode(label))

    private fun clickNodeIfEnabled(label: String) {
        var clickable: AccessibilityNodeInfo? = findNode(label) ?: return
        while (clickable != null && !clickable.isClickable) clickable = clickable.parent
        if (clickable?.isEnabled == true) {
            clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    private fun clickNode(node: AccessibilityNodeInfo) {
        var clickable: AccessibilityNodeInfo? = node
        while (clickable != null && !clickable.isClickable) clickable = clickable.parent
        checkNotNull(clickable) { "node is not clickable: ${node.text ?: node.contentDescription}" }
            .performAction(AccessibilityNodeInfo.ACTION_CLICK)
        SystemClock.sleep(250L)
    }

    private fun waitForNode(label: String): AccessibilityNodeInfo {
        val deadline = SystemClock.elapsedRealtime() + 5_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            findNode(label)?.let { return it }
            SystemClock.sleep(80L)
        }
        throw AssertionError("Accessibility node not found: $label")
    }

    private fun findNode(label: String): AccessibilityNodeInfo? {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue += root
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.text?.toString() == label || node.contentDescription?.toString() == label) {
                return node
            }
            repeat(node.childCount) { index -> node.getChild(index)?.let(queue::addLast) }
        }
        return null
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (!condition()) {
            if (SystemClock.elapsedRealtime() >= deadline) {
                throw AssertionError("Condition not reached within ${timeoutMs}ms")
            }
            SystemClock.sleep(50L)
        }
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command),
    ).bufferedReader().use { it.readText() }

    companion object {
        private val COMMIT = Regex(
            "settleTargetPx=(-?[0-9.]+) settleFinalPx=(-?[0-9.]+) rebaseFinalPx=(-?[0-9.]+)",
        )
        private val RELEASE = Regex(
            "targetBaseReady=(true|false) visibleNeighborBitmapId=(-?[0-9]+)",
        )
        private val SCALE_COMMIT = Regex("scaleBefore=([0-9.]+) scaleAfter=([0-9.]+)")
    }
}
