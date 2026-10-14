package com.m1racle.reader.ui.pdf

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.InspectableValue
import androidx.compose.ui.platform.isDebugInspectorInfoEnabled
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.m1racle.reader.ui.theme.M1racleReaderTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@ConscryptMode(ConscryptMode.Mode.OFF)
class PdfMenuHighlightTest {
    @get:Rule val rule = createComposeRule()
    private var clicks = 0

    @Before fun enableModifierInspection() { isDebugInspectorInfoEnabled = true }
    @After fun resetModifierInspection() { isDebugInspectorInfoEnabled = false }

    private fun showMenu() {
        rule.setContent {
            M1racleReaderTheme {
                Box(Modifier.padding(60.dp)) {
                    PdfMenuElasticPress(Modifier.testTag("gesture")) { transform, _ ->
                        Column(Modifier.width(240.dp).graphicsLayer(transform)) {
                            PdfChromeMenuRow(icon = null, label = "First", onClick = { clicks++ })
                            PdfChromeMenuRow(icon = null, label = "Second", onClick = { clicks++ })
                        }
                    }
                }
            }
        }
    }

    // Inspect the real background paint installed by the production row, not a
    // duplicate hover flag or a test-only semantic. GPU appearance is device QA.
    private fun SemanticsNodeInteraction.backgroundAlpha(): Float =
        fetchSemanticsNode().layoutInfo.getModifierInfo()
            .mapNotNull { it.modifier as? InspectableValue }
            .first { it.nameFallback == "background" }
            .inspectableElements.first { it.name == "color" }
            .value.let { (it as Color).alpha }

    private fun holdFirst() {
        val gesture = rule.onNodeWithTag("gesture")
        gesture.performTouchInput { down(Offset(center.x, 26.dp.toPx())) }
        rule.mainClock.advanceTimeBy(700)
        gesture.performTouchInput { advanceEventTime(700) }
        rule.waitForIdle()
    }

    @Test fun crossingARowUpdatesHighlightWithoutWaitingForASpring() {
        showMenu()
        holdFirst()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("gesture").performTouchInput { moveBy(Offset(0f, 52.dp.toPx())) }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        assertEquals(0f, rule.onNodeWithText("First").backgroundAlpha(), 0f)
        assertTrue(rule.onNodeWithText("Second").backgroundAlpha() >= 0.09f)
        rule.onNodeWithTag("gesture").performTouchInput { cancel() }
        rule.mainClock.autoAdvance = true
    }

    @Test fun heldHighlightFollowsPointerThenClearsOnCancel() {
        showMenu()
        holdFirst()
        val first = rule.onNodeWithText("First")
        val second = rule.onNodeWithText("Second")
        assertTrue(first.backgroundAlpha() >= 0.09f)
        assertEquals(0f, second.backgroundAlpha(), 0f)
        rule.onNodeWithTag("gesture").performTouchInput { moveBy(Offset(0f, 52.dp.toPx())) }
        rule.waitForIdle()
        assertEquals(0f, first.backgroundAlpha(), 0f)
        assertTrue(second.backgroundAlpha() >= 0.09f)
        rule.onNodeWithTag("gesture").performTouchInput { cancel() }
        rule.waitForIdle()
        assertEquals(0f, first.backgroundAlpha(), 0f)
        assertEquals(0f, second.backgroundAlpha(), 0f)
        assertEquals(0, clicks)
    }

    @Test fun leavingCardClearsHighlightAndReleaseDoesNotActivateARow() {
        showMenu()
        holdFirst()
        rule.onNodeWithTag("gesture").performTouchInput { moveBy(Offset(-300.dp.toPx(), 0f)) }
        rule.waitForIdle()
        assertEquals(0f, rule.onNodeWithText("First").backgroundAlpha(), 0f)
        assertEquals(0f, rule.onNodeWithText("Second").backgroundAlpha(), 0f)
        rule.onNodeWithTag("gesture").performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(0, clicks)
    }
}
