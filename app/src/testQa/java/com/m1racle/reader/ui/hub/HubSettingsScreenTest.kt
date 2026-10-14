package com.m1racle.reader.ui.hub

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.m1racle.reader.ui.theme.M1racleReaderTheme
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
class HubSettingsScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun cancelledWorkbenchHasNoEntryInQaHost() {
        rule.setContent { M1racleReaderTheme { HubSettingsScreen(onNavigateHub = {}) } }
        rule.onNodeWithText("液态玻璃工作台").assertDoesNotExist()
        rule.onNodeWithText("材质对比").performScrollTo().assertExists()
    }

    @Test
    fun documentToolsRemainActionable() {
        var opened = false
        rule.setContent {
            M1racleReaderTheme {
                HubSettingsScreen(onNavigateHub = {}, onOpenDocumentTools = { opened = true })
            }
        }
        rule.onNodeWithText("文件与PDF工具").performScrollTo().performClick()
        assertTrue(opened)
    }
}
