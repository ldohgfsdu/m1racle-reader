package com.m1racle.reader.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Physical-device Macrobenchmark for M1racleReader reader scroll jank.
 *
 * Prerequisites:
 * 1. Install target: `./gradlew :app:assembleBenchmark` (or release)
 * 2. On device, import `docs/sample-stress.md` (or sample-large.md) once
 * 3. Open that document so it appears under 最近文件
 * 4. Run:
 *    `./gradlew :benchmark:connectedBenchmarkAndroidTest`
 *
 * Metrics: [FrameTimingMetric] (frameDurationCpuMs, frameOverrunMs) + startup.
 */
@RunWith(AndroidJUnit4::class)
class ReaderScrollBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val packageName: String
        get() = "com.m1racle.reader.benchmark"

    @Test
    fun coldStartup() {
        benchmarkRule.measureRepeated(
            packageName = packageName,
            metrics = listOf(StartupTimingMetric()),
            iterations = 5,
            startupMode = StartupMode.COLD,
            compilationMode = CompilationMode.DEFAULT,
        ) {
            pressHome()
            startActivityAndWait()
        }
    }

    /**
     * Opens first recent document (if present) and flings the reader LazyColumn.
     * When no document is imported, the test still starts home and records frames
     * for the empty list path (setup reminder in device log).
     */
    @Test
    fun openDocumentAndFlingScroll() {
        benchmarkRule.measureRepeated(
            packageName = packageName,
            metrics = listOf(FrameTimingMetric()),
            iterations = 5,
            startupMode = StartupMode.WARM,
            compilationMode = CompilationMode.DEFAULT,
        ) {
            pressHome()
            startActivityAndWait()

            val device = this.device
            device.wait(Until.findObject(By.clickable(true)), 3_000)
            // UiAutomator: open first substantial list row below top chrome (recent file).
            val candidates = device.findObjects(By.clickable(true))
            val target = candidates.firstOrNull { obj ->
                val b = obj.visibleBounds
                b.width() > 200 && b.height() in 48..240 && b.top > 120
            }
            target?.click()
            device.waitForIdle(1_000)

            // Fling the main content area repeatedly (paragraphs → code → tables → images).
            val screen = device.findObject(By.pkg(packageName).depth(0))
            if (screen != null) {
                repeat(6) {
                    screen.fling(Direction.DOWN, 8_000)
                    device.waitForIdle(400)
                }
                repeat(2) {
                    screen.fling(Direction.UP, 6_000)
                    device.waitForIdle(300)
                }
            }
        }
    }
}
