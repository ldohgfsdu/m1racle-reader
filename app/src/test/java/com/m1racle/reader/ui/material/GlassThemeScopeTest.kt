package com.m1racle.reader.ui.material

import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.m1racle.reader.ui.theme.M1racleReaderTheme
import com.m1racle.reader.ui.theme.LocalReaderDarkTheme
import org.junit.Assert.assertEquals
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
@Config(sdk = [34], qualifiers = "night")
class GlassThemeScopeTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun lightPaperChromeOverridesDarkSystemAndRestoresOuterTheme() {
        val themes = mutableListOf<Boolean>()
        val muted = mutableListOf<Float>()
        rule.setContent {
            M1racleReaderTheme(darkTheme = true) {
                val before = LocalReaderDarkTheme.current
                M1racleReaderTheme(darkTheme = false) {
                    val light = LocalReaderDarkTheme.current
                    val alpha = glassMutedContentAlpha(GlassMaterialRole.Control)
                    SideEffect { themes.add(light); muted.add(alpha) }
                }
                val after = LocalReaderDarkTheme.current
                val alpha = glassMutedContentAlpha(GlassMaterialRole.Control)
                SideEffect { themes.add(before); themes.add(after); muted.add(alpha) }
            }
        }
        rule.runOnIdle {
            assertEquals(listOf(false, true, true), themes)
            assertEquals(listOf(
                GlassMaterialEngine.resolve(GlassMaterialRole.Control, false).contentMutedAlpha,
                GlassMaterialEngine.resolve(GlassMaterialRole.Control, true).contentMutedAlpha,
            ), muted)
        }
    }
}
