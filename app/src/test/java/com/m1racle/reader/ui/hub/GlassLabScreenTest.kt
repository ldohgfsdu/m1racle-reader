package com.m1racle.reader.ui.hub

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.m1racle.reader.ui.hub.glasslab.GlassLabScreen
import com.m1racle.reader.ui.theme.M1racleReaderTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[31],qualifiers="w411dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@ConscryptMode(ConscryptMode.Mode.OFF)
class GlassLabScreenTest {
    @get:Rule val rule=createComposeRule()
    @Before fun prepare(){ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("glass-workbench-v1",Context.MODE_PRIVATE).edit().clear().commit()}

    @Test fun accessibleSliderAndResetUpdatePreviewState() {
        rule.setContent { M1racleReaderTheme {GlassLabScreen({})} }
        val slider=rule.onNodeWithContentDescription("Width")
        slider.performScrollTo().performSemanticsAction(SemanticsActions.SetProgress){it(250f)}
        assertEquals(250f,slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current,0f)
        rule.onNodeWithContentDescription("重置参数与位置").performClick()
        assertEquals(180f,slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current,0f)
    }

    @Test fun presetChangesRenderedMaterialInputsAndOlderApiExplainsLimits() {
        rule.setContent { M1racleReaderTheme {GlassLabScreen({})} }
        rule.onNodeWithText("Clear").performClick()
        val blur=rule.onNodeWithContentDescription("Blur")
        blur.performScrollTo()
        assertEquals(1f,blur.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current,0f)
        rule.onNodeWithText("Regular").performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick){it()}
        blur.performScrollTo()
        assertEquals(12f,blur.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current,0f)
        rule.onNodeWithText("分屏法线").assertIsNotEnabled()
    }

    @Test fun settingsEntryOpensChild() {
        var opened=false
        rule.setContent {M1racleReaderTheme {HubSettingsScreen({}, {opened=true})}}
        rule.onNodeWithText("液态玻璃工作台").performScrollTo().performClick()
        assertTrue(opened)
    }
}
