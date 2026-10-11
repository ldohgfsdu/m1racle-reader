package com.m1racle.reader.ui.pdf

import android.graphics.Point
import android.graphics.Rect
import android.media.ImageReader
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.view.ScrollCaptureSession
import android.view.ScrollCaptureTarget
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the production custom PDF viewport through Android's real scroll-capture protocol. */
@RunWith(AndroidJUnit4::class)
class PdfSystemScrollCaptureInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<H1QaContinuousHarnessActivity>()

    @Test fun systemCapturesMultiplePdfScreensWithoutSavingTemporaryPosition() {
        rule.waitForIdle()
        val node = rule.onNodeWithTag("r7-continuous-viewport")
        fun scrollY() = node.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        val originalY = scrollY()
        val originalSaved = rule.activity.transformSnapshot()
        val targets = mutableListOf<ScrollCaptureTarget>()
        rule.runOnIdle {
            val root = rule.activity.window.decorView
            root.dispatchScrollCaptureSearch(Rect(0, 0, root.width, root.height), Point(), Consumer { targets.add(it) })
        }
        assertEquals("System must discover the actual PDF viewport", 1, targets.size)
        val target = targets.single()
        val bounds = requireNotNull(target.scrollBounds)
        val frames = AtomicInteger()
        val reader = ImageReader.newInstance(bounds.width(), bounds.height(), android.graphics.PixelFormat.RGBA_8888, 8)
        reader.setOnImageAvailableListener({ it.acquireLatestImage()?.use { frames.incrementAndGet() } }, Handler(Looper.getMainLooper()))
        val session = ScrollCaptureSession(reader.surface, bounds, target.positionInWindow)
        val callback = target.callback
        var started = false
        try {
            rule.runOnIdle { callback.onScrollCaptureStart(session, CancellationSignal()) { started = true } }
            rule.waitUntil(5_000) { started }
            rule.waitForIdle()
            repeat(3) { index ->
                val result = AtomicReference<Rect?>()
                rule.runOnIdle { callback.onScrollCaptureImageRequest(session, CancellationSignal(),
                    Rect(0, index * bounds.height(), bounds.width(), (index + 1) * bounds.height()), Consumer { result.set(it) }) }
                rule.waitUntil(10_000) { result.get() != null }
                assertEquals(bounds.height(), result.get()!!.height())
            }
            rule.waitUntil(5_000) { frames.get() >= 3 }
            assertTrue(scrollY() > originalY + bounds.height())
            assertEquals("Capture must not publish a temporary saved viewport", originalSaved, rule.activity.transformSnapshot())
        } finally {
            var ended = false
            rule.runOnIdle { callback.onScrollCaptureEnd { ended = true } }
            rule.waitUntil(10_000) { ended }
            reader.close()
        }
        assertEquals(originalY, scrollY(), .01f)
        assertEquals(originalSaved, rule.activity.transformSnapshot())
    }
}
