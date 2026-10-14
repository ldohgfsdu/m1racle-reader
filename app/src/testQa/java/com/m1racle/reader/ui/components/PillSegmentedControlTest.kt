package com.m1racle.reader.ui.components

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.performSemanticsAction
import com.m1racle.reader.ui.theme.M1racleReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "w411dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
class PillSegmentedControlTest {
    @get:Rule val rule = createComposeRule()

    private fun assertCompleteLibraryGroup(width: Int) {
        rule.setContent {
            M1racleReaderTheme {
                PillSegmentedControl(listOf("全部", "PDF", "Markdown", "EPUB"), 0, {},
                    Modifier.width(width.dp).testTag("library-filter"))
            }
        }
        val viewport = rule.onNodeWithTag("library-filter").fetchSemanticsNode().boundsInRoot
        val bounds = listOf("全部", "PDF", "Markdown", "EPUB").map {
            rule.onNodeWithText(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        }
        assertTrue(bounds.last().right <= viewport.right)
        bounds.forEach {
            assertEquals(bounds.first().width, it.width, 0.1f)
            assertTrue(it.height >= 48f)
        }
        val scroll = rule.onNodeWithTag("segment-track").fetchSemanticsNode()
            .config[SemanticsProperties.HorizontalScrollAxisRange]
        assertEquals(0f, scroll.maxValue(), 0.1f)
    }

    @Test fun libraryShowsAllFourChoicesWithoutClippingAtNormalPhoneWidth() {
        assertCompleteLibraryGroup(340)
    }

    @Test fun smallPhoneWrapsLabelsAndKeepsBothEndsInsideThePage() {
        assertCompleteLibraryGroup(272)
    }

    @Test fun phoneAtItsCurrentFontScaleKeepsMarkdownOnOneLine() {
        rule.setContent {
            M1racleReaderTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1.1f)) {
                    PillSegmentedControl(listOf("全部", "PDF", "Markdown", "EPUB"), 0, {},
                        Modifier.width(327.dp))
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText("Markdown")
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.single().lineCount)
        val layout = layouts.single()
        assertTrue("width=${layout.didOverflowWidth}, height=${layout.didOverflowHeight}, " +
            "size=${layout.size}, line=${layout.getLineRight(0)}", !layout.hasVisualOverflow)
        rule.onNodeWithText("EPUB").assertIsDisplayed()
    }

    private fun dragContent(rtl: Boolean = false) {
        rule.setContent {
            M1racleReaderTheme {
                CompositionLocalProvider(LocalLayoutDirection provides
                    if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    var selected by remember { mutableIntStateOf(0) }
                    PillSegmentedControl(listOf("小", "默认", "大"), selected, { selected = it },
                        Modifier.width(300.dp).testTag("segments"))
                }
            }
        }
    }

    @Test fun dragCommitsTheReleaseTargetRatherThanThePressedChoice() {
        dragContent()
        val first = rule.onNodeWithText("小").fetchSemanticsNode().boundsInRoot.center
        val last = rule.onNodeWithText("大").fetchSemanticsNode().boundsInRoot.center
        val origin = rule.onNodeWithTag("segments").fetchSemanticsNode().boundsInRoot.topLeft
        rule.onNodeWithTag("segments").performTouchInput {
            down(first - origin)
            moveTo(last - origin, 300)
        }
        rule.onNodeWithText("小").assertIsSelected()
        rule.onNodeWithTag("segments").performTouchInput { up() }
        rule.onNodeWithText("大").assertIsSelected()
        rule.onNodeWithText("小").assertIsNotSelected()
    }

    @Test fun cancellingAcrossSeveralChoicesDoesNotCommitThePreview() {
        dragContent()
        val first = rule.onNodeWithText("小").fetchSemanticsNode().boundsInRoot.center
        val last = rule.onNodeWithText("大").fetchSemanticsNode().boundsInRoot.center
        val origin = rule.onNodeWithTag("segments").fetchSemanticsNode().boundsInRoot.topLeft
        rule.onNodeWithTag("segments").performTouchInput {
            down(first - origin)
            moveTo(last - origin, 300)
            cancel()
        }
        rule.onNodeWithText("小").assertIsSelected()
        rule.onNodeWithText("大").assertIsNotSelected()
    }

    @Test fun rightToLeftDragUsesLogicalChoiceOrder() {
        dragContent(rtl = true)
        val first = rule.onNodeWithText("小").fetchSemanticsNode().boundsInRoot.center
        val last = rule.onNodeWithText("大").fetchSemanticsNode().boundsInRoot.center
        val origin = rule.onNodeWithTag("segments").fetchSemanticsNode().boundsInRoot.topLeft
        rule.onNodeWithTag("segments").performTouchInput {
            down(first - origin)
            moveTo(last - origin, 300)
            up()
        }
        rule.onNodeWithText("大").assertIsSelected()
    }

    @Test fun overflowingTrackScrollsNormallyAndSwitchesAfterHolding() {
        rule.setContent {
            M1racleReaderTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                    var selected by remember { mutableIntStateOf(0) }
                    PillSegmentedControl(listOf("全部", "PDF", "Markdown", "EPUB"), selected,
                        { selected = it }, Modifier.width(240.dp).testTag("segments"))
                }
            }
        }
        rule.onNodeWithTag("segments").performTouchInput { swipeLeft(durationMillis = 250) }
        rule.onNodeWithText("全部").assertIsSelected()
        rule.onNodeWithText("全部").performScrollTo()
        val first = rule.onNodeWithText("全部").fetchSemanticsNode().boundsInRoot.center
        val next = rule.onNodeWithText("PDF").fetchSemanticsNode().boundsInRoot.center
        val origin = rule.onNodeWithTag("segments").fetchSemanticsNode().boundsInRoot.topLeft
        rule.onNodeWithTag("segments").performTouchInput { down(first - origin) }
        rule.mainClock.advanceTimeBy(700)
        rule.onNodeWithTag("segments").performTouchInput {
            moveTo(next - origin, 300)
            up()
        }
        rule.onNodeWithText("PDF").assertIsSelected()
    }

    @Test fun compactChoicesKeepTheTouchAreaWithoutStretchingAcrossThePage() {
        rule.setContent {
            M1racleReaderTheme {
                PillSegmentedControl(listOf("小", "默认", "大"), 1, {}, Modifier.width(340.dp))
            }
        }
        val first = rule.onNodeWithText("小").fetchSemanticsNode().boundsInRoot
        val last = rule.onNodeWithText("大").fetchSemanticsNode().boundsInRoot
        assertEquals(48f, first.height, 0.1f)
        assertTrue(last.right - first.left < 240f)
    }

    @Test fun pressPreviewsWithoutCommittingAndCancellationKeepsTheOriginalChoice() {
        rule.setContent {
            M1racleReaderTheme {
                var selected by remember { mutableIntStateOf(0) }
                PillSegmentedControl(listOf("全部", "PDF"), selected, { selected = it })
            }
        }
        rule.onNodeWithText("PDF").performTouchInput { down(center); advanceEventTime(200) }
        rule.onNodeWithText("全部").assertIsSelected()
        rule.onNodeWithText("PDF").assertIsNotSelected()
        rule.onNodeWithText("PDF").performTouchInput { cancel() }
        rule.onNodeWithText("全部").assertIsSelected()
        rule.onNodeWithText("PDF").performClick().assertIsSelected()
    }

    @Test fun selectionMovesExclusivelyToTheTappedChoice() {
        rule.setContent {
            M1racleReaderTheme {
                var selected by remember { mutableIntStateOf(0) }
                PillSegmentedControl(listOf("全部", "PDF", "Markdown", "EPUB"), selected,
                    { selected = it }, Modifier.width(340.dp))
            }
        }
        rule.onNodeWithText("全部").assertIsSelected()
        rule.onNodeWithText("Markdown").performClick().assertIsSelected()
        rule.onNodeWithText("全部").assertIsNotSelected()
        rule.onNodeWithText("PDF").assertIsNotSelected()
        rule.onNodeWithText("EPUB").assertIsNotSelected()
    }

    @Test fun narrowControlAtDoubleFontScaleKeepsTheLastChoiceReachable() {
        rule.setContent {
            M1racleReaderTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                    var selected by remember { mutableIntStateOf(0) }
                    PillSegmentedControl(listOf("全部", "PDF", "Markdown", "EPUB"), selected,
                        { selected = it }, Modifier.width(240.dp))
                }
            }
        }
        rule.onNodeWithText("EPUB").performScrollTo().assertIsDisplayed()
            .performClick().assertIsSelected()
        rule.onNodeWithText("全部").assertIsNotSelected()
    }
}
