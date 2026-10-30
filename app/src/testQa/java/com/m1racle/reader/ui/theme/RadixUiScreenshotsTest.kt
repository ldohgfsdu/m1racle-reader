package com.m1racle.reader.ui.theme

import com.m1racle.reader.design.RadixColors

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.m1racle.reader.ui.components.readerHairlineBorder
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.data.settings.ReaderSettings
import com.m1racle.reader.ui.epub.EpubReadingSettings
import com.m1racle.reader.ui.home.HomeContent
import com.m1racle.reader.ui.home.HomeUiState
import com.m1racle.reader.ui.home.homeUiState
import com.m1racle.reader.ui.hub.HubSettingsScreen
import com.m1racle.reader.ui.material.GlassCapability
import com.m1racle.reader.ui.material.LocalGlassCapability
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

/** Native Android Canvas evidence from the QA JVM host, with solid glass fallback. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-mdpi")
class RadixUiScreenshotsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun captureLightAndDarkNarrowLayouts() {
        val dark = mutableStateOf(false)
        val page = mutableStateOf("home")
        lateinit var view: View
        val documents = (1..3).map {
            DocumentEntity(id = "$it", displayName = listOf("阅读与记录.pdf", "设计笔记.md", "日常阅读.epub")[it - 1],
                uri = "content://fixture/$it", mimeType = "application/pdf", fileSize = 1024L,
                lastOpenedAt = 1L, createdAt = 1L, lastScrollIndex = 0, lastScrollOffset = 0,
                readingProgress = 0.37f, title = null, headingCount = 0,
                documentType = listOf("PDF", "MARKDOWN", "EPUB")[it - 1])
        }
        rule.setContent {
            M1racleReaderTheme(darkTheme = dark.value) {
                CompositionLocalProvider(LocalGlassCapability provides GlassCapability.SOLID_FALLBACK) {
                    view = LocalView.current
                    Box(Modifier.size(360.dp, 740.dp).background(MaterialTheme.colorScheme.background)
                        .testTag("screen")) {
                        when (page.value) {
                            "home" -> HomeContent(homeUiState(documents, emptySet()),
                                false, {}, {}, {}, {}, statusTop = 0.dp, navBottom = 0.dp)
                            "qa-settings" -> HubSettingsScreen(onNavigateHub = {}, onOpenDocumentTools = {})
                            else -> EpubReadingSettings(ReaderSettings(), {}, {}, {}, {})
                        }
                    }
                }
            }
        }
        val directory = File(System.getenv("READER_UI_EVIDENCE") ?: "build/reports/ui-radix").apply { mkdirs() }
        for (night in listOf(false, true)) {
            for (screen in listOf("home", "qa-settings", "reading-settings")) {
                rule.runOnIdle { dark.value = night; page.value = screen }
                rule.waitForIdle()
                val bounds = rule.onNodeWithTag("screen").fetchSemanticsNode().boundsInRoot
                rule.runOnIdle {
                    val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(full))
                    val bitmap = Bitmap.createBitmap(full, bounds.left.toInt(), bounds.top.toInt(),
                        bounds.width.toInt(), bounds.height.toInt())
                    val file = File(directory, "$screen-${if (night) "dark" else "light"}.png")
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    assertTrue("capture contains rendered pixels", file.length() > 2_000)
                    if (bitmap !== full) bitmap.recycle()
                    full.recycle()
                }
            }
        }
    }
    @Test fun borderRasterizationStaysHalfAPixelAcrossDisplayDensities() {
        val density = mutableStateOf(Density(1f))
        lateinit var view: View
        rule.setContent {
            M1racleReaderTheme {
                CompositionLocalProvider(LocalDensity provides density.value) {
                    view = LocalView.current
                    Row(Modifier.padding(8.dp)) {
                        Box(Modifier.size(40.dp).background(RadixColors.White)
                            .border(M1racleReaderTheme.hairline, RadixColors.Black).testTag("rounded-up"))
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.size(40.dp).background(RadixColors.White)
                            .readerHairlineBorder(RadixColors.Black).testTag("half-pixel"))
                    }
                }
            }
        }
        for (scale in listOf(1f, 2f, 3.5f)) {
            rule.runOnIdle { density.value = Density(scale) }
            rule.waitForIdle()
            val reference = rule.onNodeWithTag("rounded-up").fetchSemanticsNode().boundsInRoot
            val half = rule.onNodeWithTag("half-pixel").fetchSemanticsNode().boundsInRoot
            rule.runOnIdle {
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                fun coverage(bounds: androidx.compose.ui.geometry.Rect): Float = (0..2).sumOf { offset ->
                    (255 - android.graphics.Color.red(bitmap.getPixel(bounds.center.x.toInt(),
                        bounds.top.toInt() + offset))).toDouble() / 255.0
                }.toFloat()
                assertEquals("Foundation rounds up at density $scale", 1f, coverage(reference), 0.01f)
                assertEquals("actual inset stroke at density $scale", 0.5f, coverage(half), 0.01f)
                bitmap.recycle()
            }
        }
    }

}
