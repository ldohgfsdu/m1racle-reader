package com.markleaf.app.ui.pdf

import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.markleaf.app.R
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** H1.4 real QA MainActivity paths. Component harness tests remain separate characterization. */
@RunWith(AndroidJUnit4::class)
class R7H14RealMainActivityAcceptanceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Before
    fun requireIsolatedQaTarget() {
        assertEquals("com.markleaf.app.qa", instrumentation.targetContext.packageName)
    }

    @Test
    fun realModeSwitchContinuity() {
        launchFixture()
        selectMode(horizontal = false)
        shell("logcat -c")
        trace().pinch1To2Point5(movingCentroid = true)
        SystemClock.sleep(700L)

        selectMode(horizontal = true)
        SystemClock.sleep(700L)
        selectMode(horizontal = false)
        SystemClock.sleep(900L)

        val logs = h14Logs()
        val requests = MODE_REQUEST.findAll(logs).toList()
        assertTrue("both real mode-switch requests must be observed: $logs", requests.size >= 2)
        requests.takeLast(2).forEach { match ->
            assertEquals(match.groupValues[3], match.groupValues[4])
            assertEquals(match.groupValues[5].toFloat(), match.groupValues[6].toFloat(), 0.001f)
            assertTrue(match.groupValues[6].toFloat() > 1.05f)
        }
        val applies = MODE_APPLY.findAll(logs).toList()
        assertTrue("both adapters must apply the same non-default session: $logs", applies.size >= 2)
        val lastTwo = applies.takeLast(2)
        assertTrue(lastTwo.any { it.groupValues[1] == "HORIZONTAL_PAGED" })
        assertTrue(lastTwo.any { it.groupValues[1] == "VERTICAL_CONTINUOUS" })
        assertTrue(lastTwo.all { it.groupValues[3].toFloat() > 1.05f })
        assertTrue(abs(lastTwo[0].groupValues[3].toFloat() - lastTwo[1].groupValues[3].toFloat()) <= 0.001f)
        Log.i(TAG, "H14_REAL_MODE_SWITCH ${lastTwo.joinToString { it.value }}")
    }

    @Test
    fun realZoomedPageScaleContinuity() {
        launchFixture()
        selectMode(horizontal = true)
        shell("logcat -c")
        val trace = trace()
        trace.pinch1To2Point5(movingCentroid = true)
        SystemClock.sleep(450L)
        trace.zoomedEdgeDragLeft()
        SystemClock.sleep(350L)
        trace.zoomedPageHandoff()
        SystemClock.sleep(1_400L)

        val logs = h14Logs()
        val commit = PAGE_COMMIT.findAll(logs).lastOrNull()
            ?: throw AssertionError("real PAGE_COMMIT evidence missing: $logs")
        assertTrue(commit.groupValues[2].toInt() != commit.groupValues[1].toInt())
        assertEquals(commit.groupValues[4].toFloat(), commit.groupValues[5].toFloat(), 0.001f)
        assertTrue(commit.groupValues[5].toFloat() > 1.05f)
        assertEquals(commit.groupValues[6].toFloat(), commit.groupValues[7].toFloat(), 0.01f)
        Log.i(TAG, "H14_REAL_PAGE_CONTINUITY ${commit.value}")
    }

    private fun launchFixture() {
        val component = "com.markleaf.app.qa/com.markleaf.app.MainActivity"
        val output = shell(
            "am start --display 0 -W -a android.intent.action.VIEW " +
                "-d ${H1QaPdfFixtureProvider.URI} -t application/pdf -f 1 -n $component",
        )
        assertTrue("real QA reader launch failed: $output", output.contains("Status: ok"))
        waitForNode(instrumentation.targetContext.getString(R.string.pdf_reading_mode_open))
    }

    private fun selectMode(horizontal: Boolean) {
        clickNode(instrumentation.targetContext.getString(R.string.pdf_reading_mode_open))
        val label = instrumentation.targetContext.getString(
            if (horizontal) R.string.pdf_reading_mode_horizontal else R.string.pdf_reading_mode_vertical,
        )
        clickNode(label)
        SystemClock.sleep(350L)
    }

    private fun trace(): H1QaMotionEventTrace {
        val display = checkNotNull(
            instrumentation.targetContext.getSystemService(DisplayManager::class.java).getDisplay(0),
        )
        val metrics = instrumentation.targetContext.createDisplayContext(display).resources.displayMetrics
        return H1QaMotionEventTrace(
            instrumentation,
            Rect(
                0,
                (metrics.heightPixels * 0.15f).toInt(),
                metrics.widthPixels,
                (metrics.heightPixels * 0.90f).toInt(),
            ),
            displayId = 0,
        )
    }

    private fun clickNode(label: String) {
        var node = waitForNode(label)
        while (!node.isClickable && node.parent != null) node = node.parent
        assertTrue("node is not clickable: $label", node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }

    private fun waitForNode(label: String, timeoutMs: Long = 8_000L): AccessibilityNodeInfo {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            findNode(instrumentation.uiAutomation.rootInActiveWindow, label)?.let { return it }
            SystemClock.sleep(80L)
        }
        throw AssertionError("Accessibility node not found: $label")
    }

    private fun findNode(root: AccessibilityNodeInfo?, label: String): AccessibilityNodeInfo? {
        if (root == null) return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.text?.toString() == label || node.contentDescription?.toString() == label) return node
            repeat(node.childCount) { index -> node.getChild(index)?.let(queue::addLast) }
        }
        return null
    }

    private fun h14Logs(): String = shell("logcat -d -s MarkLeafH14:D *:S")

    private fun shell(command: String): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }

    companion object {
        private const val TAG = "MarkLeafH14Qa"
        private val FLOAT = "([-+0-9.Ee]+)"
        private val MODE_REQUEST = Regex(
            "H14_MODE_SWITCH_REQUEST from=(\\S+) to=(\\S+) pageBefore=(\\d+) pageAfter=(\\d+) " +
                "scaleBefore=$FLOAT scaleAfter=$FLOAT",
        )
        private val MODE_APPLY = Regex(
            "H14_MODE_APPLY mode=(\\S+) page=(\\d+) scale=$FLOAT anchorY=$FLOAT",
        )
        private val PAGE_COMMIT = Regex(
            "H14_PAGE_COMMIT outgoingPage=(\\d+) targetPage=(\\d+) direction=(\\S+) " +
                "scaleBefore=$FLOAT scaleAfter=$FLOAT anchorYBefore=$FLOAT anchorYAfter=$FLOAT",
        )
    }
}
