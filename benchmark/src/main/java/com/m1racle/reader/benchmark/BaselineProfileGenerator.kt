package com.m1racle.reader.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates `app/src/main/baseline-prof.txt` for M1racle Reader.
 *
 * Reader has shipped without a baseline profile, so every cold start re-decides
 * AOT/JIT state for the same startup classes. This generator walks the paths a
 * cold start actually takes — process start, Compose host, home list — so ART can
 * pre-compile them.
 *
 * Run on a device or emulator:
 * `./gradlew :benchmark:connectedBenchmarkAndroidTest --tests "*BaselineProfileGenerator*"`
 *
 * The profile lands in the benchmark module's additional outputs; copy it to
 * `app/src/main/baseline-prof.txt` and commit. AGP packages it as
 * `assets/dexopt/baseline.prof`, and `androidx.profileinstaller` installs it.
 *
 * Requires the target to be profileable: `app/src/main/AndroidManifest.xml`
 * declares `<profileable android:shell="true" />`, which is why the non-debuggable
 * benchmark variant can be profiled. Do not run this against the QA variant — it
 * is debuggable and produces a profile that does not match production.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    private val packageName: String
        get() = "com.m1racle.reader.benchmark"

    @Test
    fun startupAndHome() {
        rule.collect(packageName = packageName, maxIterations = 12) {
            pressHome()
            startActivityAndWait()

            val device = this.device
            device.wait(Until.hasObject(By.pkg(packageName).depth(0)), 5_000)
            device.waitForIdle(1_000)

            // The home list is the first surface a cold start renders; walking it
            // captures the item layout and text measurement paths too.
            val root = device.findObject(By.pkg(packageName).depth(0)) ?: return@collect
            repeat(3) {
                root.fling(Direction.DOWN, 4_000)
                device.waitForIdle(400)
            }
            repeat(1) {
                root.fling(Direction.UP, 3_000)
                device.waitForIdle(300)
            }
        }
    }
}
