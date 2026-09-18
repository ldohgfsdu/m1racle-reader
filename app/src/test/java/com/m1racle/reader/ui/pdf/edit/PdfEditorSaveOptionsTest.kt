package com.m1racle.reader.ui.pdf.edit

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.m1racle.reader.ui.theme.M1racleReaderTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class PdfEditorSaveOptionsTest {
    @get:Rule val rule=createComposeRule()
    @Test fun locallySavedChangesCanStillBeWrittenBackWithoutAnotherEdit() {
        var writes=0
        rule.setContent { M1racleReaderTheme {
            PdfEditorSaveOptions(PdfEditorState(busy=false,dirty=false,sourceNeedsUpdate=true),{}, { writes++ },{}, {})
        } }
        rule.onNodeWithText("书库里的修改尚未写回源文件。").assertExists()
        rule.onNodeWithText("覆盖源文件并保存").assertIsEnabled().performClick()
        assertEquals(1,writes)
    }
    @Test fun recoveryBackupIsAvailableEvenWhenThereAreNoNewEdits() {
        var exports=0
        rule.setContent { M1racleReaderTheme {
            PdfEditorSaveOptions(PdfEditorState(busy=false,dirty=false,recoveryAvailable=true),{}, {},{}, { exports++ })
        } }
        rule.onNodeWithText("导出原文件备份").assertIsEnabled().performClick()
        assertEquals(1,exports)
    }
}
