package com.m1racle.reader.ui.pdf

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m1racle.reader.pdf.ContinuousGestureSnapshotPayload
import com.m1racle.reader.pdf.ContinuousScrollPhase
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression for fit-width pages whose short page boxes make four or more pages
 * actually visible at once. The display mirror must keep every visible bitmap;
 * the former anchor-plus/minus-one window painted short pages white.
 */
@RunWith(AndroidJUnit4::class)
class PdfContinuousShortPageRetentionInstrumentedTest {

    @get:Rule
    val rule = createAndroidComposeRule<PdfMotionAspectHarnessActivity>()

    @Test
    fun fitWidthShortPagesKeepEveryActuallyVisibleBitmapBeforeAndAfterScroll() {
        rule.activity.enableFitWidthShortPageWindow()
        rule.waitUntil(10_000) {
            captureVisibleState()?.let { state ->
                state.actualVisible.size >= 4 && state.allActuallyVisiblePagesHaveBitmaps()
            } == true
        }
        val before = requireNotNull(captureVisibleState())
        assertTrue("fit-width fixture must expose at least four actual pages", before.actualVisible.size >= 4)

        val node = rule.onNodeWithTag("r7-continuous-viewport")
        val stepPx = with(rule.density) { 40.dp.toPx() }
        node.performTouchInput { down(center) }
        repeat(8) {
            node.performTouchInput { moveBy(Offset(0f, -stepPx)) }
            rule.waitForIdle()
        }
        node.performTouchInput {
            advanceEventTime(200)
            up()
        }

        rule.waitUntil(10_000) {
            rule.activity.latestPhase() == ContinuousScrollPhase.IDLE &&
                captureVisibleState()?.let { state ->
                    state.actualVisible != before.actualVisible &&
                        state.actualVisible.size >= 4 &&
                        state.allActuallyVisiblePagesHaveBitmaps()
                } == true
        }
        val after = requireNotNull(captureVisibleState())
        assertNotEquals(
            "the scroll must cross enough short pages to change the actual visible set",
            before.actualVisible,
            after.actualVisible,
        )
        assertTrue("the scrolled viewport must still contain at least four short pages", after.actualVisible.size >= 4)
    }

    private fun captureVisibleState(): VisibleState? {
        val payload = rule.activity.capturePayload() ?: return null
        val actual = payload.geometry.composedPages(
            payload.documentState,
            payload.viewportHeight.toFloat(),
            prefetchPages = 0,
        ).toSet()
        return VisibleState(payload, actual)
    }

    private data class VisibleState(
        val payload: ContinuousGestureSnapshotPayload,
        val actualVisible: Set<Int>,
    ) {
        fun allActuallyVisiblePagesHaveBitmaps(): Boolean =
            actualVisible.all { page ->
                payload.displayedBitmapIds[page]?.let { it != 0 } == true
            }
    }
}
