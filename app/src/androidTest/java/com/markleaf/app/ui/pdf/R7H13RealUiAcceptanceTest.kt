package com.markleaf.app.ui.pdf

import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real QA MainActivity + repository + PdfRenderer + horizontal viewport validation. */
@RunWith(AndroidJUnit4::class)
class R7H13RealUiAcceptanceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Before
    fun requireQaTarget() {
        check(instrumentation.targetContext.packageName == "com.markleaf.app.qa")
    }

    @Test
    fun realHorizontalTileAndPageAheadAdvanceBeforeActionUp() {
        shell("logcat -c")
        val component = "com.markleaf.app.qa/com.markleaf.app.MainActivity"
        shell(
            "am start --display 0 -W -a android.intent.action.VIEW " +
                "-d ${H1QaPdfFixtureProvider.URI} -t application/pdf -f 1 -n $component",
        )
        SystemClock.sleep(2_000L)
        val display = checkNotNull(
            instrumentation.targetContext.getSystemService(DisplayManager::class.java).getDisplay(0),
        )
        val metrics = instrumentation.targetContext.createDisplayContext(display).resources.displayMetrics
        shell("input -d 0 tap ${(metrics.widthPixels * .54f).toInt()} ${(metrics.heightPixels * .09f).toInt()}")
        SystemClock.sleep(500L)
        shell("input -d 0 tap ${(metrics.widthPixels * .50f).toInt()} ${(metrics.heightPixels * .91f).toInt()}")
        SystemClock.sleep(1_500L)
        val viewport = Rect(0, (metrics.heightPixels * .15f).toInt(), metrics.widthPixels, (metrics.heightPixels * .91f).toInt())
        instrumentation.targetContext.contentResolver.call(
            H1QaPdfFixtureProvider.URI,
            "resetHorizontalTelemetry",
            null,
            null,
        )
        H1QaMotionEventTrace(instrumentation, viewport, displayId = 0).pinchHoldThreeSeconds {
            val metricsUri = H1QaPdfFixtureProvider.URI.buildUpon().appendPath("metrics").build()
            val metrics = checkNotNull(instrumentation.targetContext.contentResolver.query(
                metricsUri,
                null,
                null,
                null,
                null,
            )).use { cursor ->
                check(cursor.moveToFirst())
                IntArray(5) { cursor.getInt(it) }
            }
            assertTrue("real horizontal gesture telemetry missing", metrics[0] > 0)
            assertTrue("tile request must happen before ACTION_UP", metrics[1] > 0)
            assertTrue("tile ready must happen before ACTION_UP", metrics[2] > 0)
            assertTrue("tile swap must happen before ACTION_UP", metrics[3] > 0)
            assertTrue("page-ahead must be requested before ACTION_UP", metrics[4] > 0)
        }
    }

    private fun shell(command: String): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }
}
