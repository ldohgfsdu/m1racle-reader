package com.m1racle.reader.ui.hub

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import com.m1racle.reader.ui.theme.M1racleReaderTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "w411dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@ConscryptMode(ConscryptMode.Mode.OFF)
class BackgroundPositionSliderTest {
    @get:Rule val rule = createComposeRule()

    private fun show(direction: LayoutDirection = LayoutDirection.Ltr) {
        rule.setContent {
            M1racleReaderTheme {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    var value by remember { mutableFloatStateOf(0.5f) }
                    BackgroundPositionSlider(value, { value = it }, "移动背景")
                }
            }
        }
    }

    @Test fun accessibleAdjustmentUpdatesTheExactValueAndKeepsTouchTarget() {
        show()
        val slider = rule.onNodeWithContentDescription("移动背景")
        assertTrue(slider.fetchSemanticsNode().boundsInRoot.height >= 48f)
        for (value in listOf(0f, 0.73f, 1f)) {
            slider.performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
            rule.onNodeWithText("${(value * 100).toInt()}%").assertIsDisplayed()
            assertEquals(value, slider.fetchSemanticsNode()
                .config[SemanticsProperties.ProgressBarRangeInfo].current, 0.001f)
        }
    }

    @Test fun draggingUpdatesContinuouslyBeforeRelease() {
        show()
        val slider = rule.onNodeWithContentDescription("移动背景")
        slider.performTouchInput { down(center); moveTo(centerRight - androidx.compose.ui.geometry.Offset(24f, 0f)) }
        assertTrue(slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current > 0.8f)
        slider.performTouchInput { up() }
    }

    @Test fun rtlMinimumIsOnTheLeadingRightSide() {
        show(LayoutDirection.Rtl)
        val slider = rule.onNodeWithContentDescription("移动背景")
        slider.performTouchInput { click(centerRight - androidx.compose.ui.geometry.Offset(16f, 0f)) }
        assertTrue(slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current < 0.1f)
    }
}
