package com.m1racle.reader.ui.hub

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m1racle.reader.ui.hub.glasslab.GlassLabScreen
import com.m1racle.reader.ui.theme.M1racleReaderTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Text focus + IME run on Android; Robolectric's dialog idle loop cannot model this flow. */
@RunWith(AndroidJUnit4::class)
class GlassLabInputInstrumentedTest {
    @get:Rule val rule=createComposeRule()
    @Before fun prepare(){ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("glass-workbench-v1",Context.MODE_PRIVATE).edit().clear().commit()}

    @Test fun exactInputRejectsOutOfRangeAndUpdatesTheSamePreviewState() {
        rule.setContent {M1racleReaderTheme {GlassLabScreen({})}}
        val slider=rule.onNodeWithContentDescription("Width")
        slider.performScrollTo().performSemanticsAction(SemanticsActions.SetProgress){it(250f)}
        assertEquals(250f,slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current,0f)
        rule.onNodeWithText("250").performClick()
        rule.onNode(hasSetTextAction()).performTextReplacement("900")
        rule.onNodeWithText("应用").assertIsNotEnabled()
        rule.onNode(hasSetTextAction()).performTextReplacement("210")
        rule.onNodeWithText("应用").performClick()
        assertEquals(210f,slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current,0f)
        rule.onNodeWithContentDescription("重置参数与位置").performClick()
        assertEquals(180f,slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current,0f)
    }
}
