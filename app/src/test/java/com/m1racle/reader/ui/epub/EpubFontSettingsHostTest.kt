package com.m1racle.reader.ui.epub

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.m1racle.reader.data.settings.EpubFontFamily
import com.m1racle.reader.data.settings.ReaderSettings
import com.m1racle.reader.ui.theme.M1racleReaderTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EpubFontSettingsHostTest {
    @get:Rule val rule = createComposeRule()

    @Test fun fontChoicesKeepTheVersion43DisplayControlsAndPreferences() {
        val initial = ReaderSettings(fontScale = 1.2f, lineHeightScale = 1.7f)
        val settings = mutableStateOf(initial)
        rule.setContent {
            M1racleReaderTheme {
                Column(Modifier.fillMaxSize()) {
                    EpubReadingSettings(settings.value, {}, {}, {}, {},
                        onFontFamily = { settings.value = settings.value.copy(epubFontFamily = it) })
                }
            }
        }
        rule.onNodeWithText("宋体").performClick()
        rule.runOnIdle {
            assertEquals(EpubFontFamily.Serif, settings.value.epubFontFamily)
            assertEquals(initial.fontScale, settings.value.fontScale, 0f)
            assertEquals(initial.lineHeightScale, settings.value.lineHeightScale, 0f)
        }
        rule.onNodeWithTag("epub-line-slider").assertIsEnabled()
        rule.onAllNodesWithTag("epub-layout-style").assertCountEquals(0)
        rule.onNodeWithText("系统").performClick()
        rule.runOnIdle { assertEquals(EpubFontFamily.Sans, settings.value.epubFontFamily) }
        rule.onNode(hasText("默认") and hasAnyAncestor(hasTestTag("epub-font-family"))).performClick()
        rule.runOnIdle { assertEquals(EpubFontFamily.Default, settings.value.epubFontFamily) }
    }
}
