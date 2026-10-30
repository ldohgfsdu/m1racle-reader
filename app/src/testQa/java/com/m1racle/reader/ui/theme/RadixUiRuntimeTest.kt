package com.m1racle.reader.ui.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.m1racle.reader.ui.components.ReaderOutlinedTextField
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
@Config(sdk = [34], qualifiers = "w360dp-h800dp-mdpi")
class RadixUiRuntimeTest {
    @get:Rule val rule = createComposeRule()

    @Test fun textContrastAndHalfPixelBordersSurviveThemeDensityAndFontScaleChanges() {
        val dark = mutableStateOf(false)
        val density = mutableStateOf(Density(1f))
        var physicalWidth = 0f
        var sizes = emptySet<Float>()
        var contrastPairs = emptyList<Pair<Color, Color>>()
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides density.value) {
                M1racleReaderTheme(darkTheme = dark.value) {
                    val scheme = MaterialTheme.colorScheme
                    val type = MaterialTheme.typography
                    val width = M1racleReaderTheme.hairline.value * LocalDensity.current.density
                    val link = M1racleReaderTheme.extra.link
                    val inheritedText = LocalContentColor.current
                    SideEffect {
                        physicalWidth = width
                        sizes = listOf(type.displayLarge, type.displayMedium, type.displaySmall,
                            type.headlineLarge, type.headlineMedium, type.headlineSmall,
                            type.titleLarge, type.titleMedium, type.titleSmall,
                            type.bodyLarge, type.bodyMedium, type.bodySmall,
                            type.labelLarge, type.labelMedium, type.labelSmall)
                            .map { it.fontSize.value }.toSet()
                        contrastPairs = listOf(scheme.onSurface to scheme.surface,
                            scheme.onSurfaceVariant to scheme.surfaceContainerLow,
                            scheme.onPrimary to scheme.primary,
                            scheme.error to scheme.errorContainer,
                            link to scheme.background, inheritedText to scheme.background)
                    }
                }
            }
        }
        for (night in listOf(false, true)) {
            for (scale in listOf(1f, 2f, 3.5f)) {
                rule.runOnIdle {
                    dark.value = night
                    density.value = Density(scale, fontScale = 1.3f)
                }
                rule.waitForIdle()
                assertEquals("physical border at density $scale", 0.5f, physicalWidth, 0.0001f)
                assertEquals(setOf(12f, 14f, 18f), sizes)
                contrastPairs.forEach { (text, background) ->
                    val ratio = (maxOf(text.luminance(), background.luminance()) + 0.05f) /
                        (minOf(text.luminance(), background.luminance()) + 0.05f)
                    assertTrue("text contrast $ratio in dark=$night", ratio >= 4.5f)
                }
            }
        }
    }

    @Test fun sharedFieldsKeepStringEditingAndTextFieldValueCursorSelection() {
        val query = mutableStateOf("")
        val draft = mutableStateOf(TextFieldValue("文档"))
        val invalid = mutableStateOf(false)
        rule.setContent {
            M1racleReaderTheme {
                Column(Modifier.width(320.dp)) {
                    ReaderOutlinedTextField(query.value, { query.value = it },
                        singleLine = true, modifier = Modifier.testTag("query"))
                    ReaderOutlinedTextField(draft.value, { draft.value = it },
                        isError = invalid.value, singleLine = true,
                        modifier = Modifier.testTag("draft"))
                }
            }
        }
        rule.onNodeWithTag("query").performTextReplacement("阅读")
        rule.runOnIdle { assertEquals("阅读", query.value) }
        rule.onNodeWithTag("draft").performTextInputSelection(TextRange(1))
        rule.onNodeWithTag("draft").performTextInput("新")
        rule.runOnIdle {
            assertEquals("文新档", draft.value.text)
            assertEquals(TextRange(2), draft.value.selection)
            invalid.value = true
        }
        rule.onNodeWithTag("draft").assertTextContains("文新档")
        rule.onNodeWithTag("draft").performTextInput("增")
        rule.runOnIdle { assertEquals("文新增档", draft.value.text) }
    }
}
