package com.m1racle.reader.ui.home

import android.graphics.Insets
import android.view.View
import android.view.WindowInsets
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.m1racle.reader.ui.theme.M1racleReaderTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

/** A reader returns while statusBars is still hidden and the native reveal is in flight. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
class HomeReturnInsetsHostTest {
    @get:Rule val rule = createComposeRule()

    @Test fun homeHeaderReservesTheStatusAreaThroughoutNativeBarReveal() {
        lateinit var view: View
        val statusHeight = (32 * rule.density.density).toInt()
        fun dispatch(visibleHeight: Int) {
            val insets = WindowInsets.Builder()
                .setInsets(WindowInsets.Type.statusBars(), Insets.of(0, visibleHeight, 0, 0))
                .setInsetsIgnoringVisibility(WindowInsets.Type.statusBars(), Insets.of(0, statusHeight, 0, 0))
                .setVisible(WindowInsets.Type.statusBars(), visibleHeight > 0)
                .build()
            view.dispatchApplyWindowInsets(insets)
        }
        rule.setContent {
            view = LocalView.current
            M1racleReaderTheme {
                HomeContent(HomeUiState(ready = true, isEmpty = true), false,
                    onImport = {}, onOpenSearch = {}, onOpen = {}, onDelete = {})
            }
            SideEffect { dispatch(0) }
        }
        rule.waitForIdle()
        for (visibleHeight in listOf(0, statusHeight / 4, statusHeight / 2, statusHeight)) {
            rule.runOnIdle { dispatch(visibleHeight) }
            rule.waitForIdle()
            assertEquals("header moves while native status visibility changes ($visibleHeight)",
                statusHeight.toFloat(), rule.onNodeWithTag("home-header").fetchSemanticsNode().boundsInRoot.top, 0f)
        }
    }
}
