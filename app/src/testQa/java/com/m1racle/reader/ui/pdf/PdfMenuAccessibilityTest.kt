package com.m1racle.reader.ui.pdf

import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.m1racle.reader.ui.theme.M1racleReaderTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34])
class PdfMenuAccessibilityTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `textbook page action is fully readable at double font scale`() {
        var clicks = 0
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                M1racleReaderTheme {
                    PdfChromeMenuRow(Icons.Outlined.Tag, "设置教材页码", onClick = { clicks++ },
                        modifier = Modifier.width(220.dp))
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText("设置教材页码").performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            assertTrue(it(layouts))
        }
        assertTrue(layouts.isNotEmpty())
        assertFalse("The full action label must fit without ellipsis", layouts.single().hasVisualOverflow)
        rule.onNodeWithText("设置教材页码").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun `reading option exposes its selected state`() {
        rule.setContent {
            M1racleReaderTheme {
                PdfChromeMenuRow(null, "上下连续", checked = true, onClick = {},
                    modifier = Modifier.width(220.dp))
            }
        }
        rule.onNodeWithText("上下连续").assertIsSelected()
    }

    @Test fun `expanded section exposes its state instead of only rotating a chevron`() {
        rule.setContent {
            M1racleReaderTheme {
                PdfChromeMenuRow(null, "阅读方式", chevron = true, chevronExpanded = true,
                    onClick = {}, modifier = Modifier.width(220.dp))
            }
        }
        rule.onNodeWithText("阅读方式").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已展开"))
    }
}
