package com.m1racle.reader.ui.viewer

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.m1racle.reader.data.settings.ReaderSettings
import com.m1racle.reader.markdown.MarkdownBlock
import com.m1racle.reader.markdown.TableAlignment
import com.m1racle.reader.ui.annotation.TextSelection
import com.m1racle.reader.ui.material.GlassBackdropSource
import com.m1racle.reader.ui.reader.ReaderProgressWheel
import com.m1racle.reader.ui.theme.M1racleReaderTheme
import com.m1racle.reader.ui.viewer.components.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android text hit-testing and system capture/rendering; never touches the production library. */
@RunWith(AndroidJUnit4::class)
class MarkdownReadingBacklogInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<com.m1racle.reader.ui.epub.EpubQaHostActivity>()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun atomicDraftReplacementAndRollbackSurviveReopeningOnAndroid() {
        val directory = File(context.cacheDir, "draft-test-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        try {
            val first = com.m1racle.reader.documents.MarkdownDraftStore.Draft("base", "初稿🌟\r\n")
            val second = first.copy(text = "第二次保存\n完整内容")
            val store = com.m1racle.reader.documents.MarkdownDraftStore(directory, "book")
            store.write(first); store.write(second)
            assertEquals(second, com.m1racle.reader.documents.MarkdownDraftStore(directory, "book").read())
            val file = File(directory, "markdown-drafts").listFiles()!!.single()
            android.util.AtomicFile(file).let { atomic ->
                val stream = atomic.startWrite(); stream.write("incomplete".toByteArray()); atomic.failWrite(stream)
            }
            assertEquals(second, store.read())
            store.delete(); assertNull(store.read())
        } finally { directory.deleteRecursively() }
    }

    private fun select(node: SemanticsNodeInteraction, from: Int, to: Int) {
        val results = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        val layout = results.single()
        fun point(index: Int) = layout.getBoundingBox(index).let {
            androidx.compose.ui.geometry.Offset(it.left + it.width * 0.25f, it.center.y)
        }
        val start = point(from)
        val end = point(to)
        node.performTouchInput { down(start); moveTo(start, 650); moveTo(end, 180); up() }
        rule.waitForIdle()
    }

    @Test fun paragraphLongDragReturnsExactCharactersIncludingSurrogatePair() {
        val text = "Alpha 🌟 中文 Beta gamma"
        var selected: TextSelection.MarkdownSelection? = null
        rule.setContent { M1racleReaderTheme {
            CompositionLocalProvider(LocalMarkdownSelection provides MarkdownSelectionContext(7, text, emptyList()) { selected = it }) {
                InlineMarkdownText(text, ReaderSettings.Default, MaterialTheme.typography.bodyLarge,
                    Modifier.padding(24.dp).testTag("paragraph"))
            }
        } }
        select(rule.onNodeWithTag("paragraph"), 6, 10)
        rule.runOnIdle {
            val actual = requireNotNull(selected)
            assertEquals(7, actual.blockIndex)
            assertEquals(6, actual.charStart)
            assertEquals(11, actual.charEnd)
            assertEquals(text.substring(6, 11), actual.excerpt)
        }
    }

    @Test fun codeAndTableUseDocumentOffsetsRatherThanCellLocalOffsets() {
        var selected: TextSelection.MarkdownSelection? = null
        val code = "val answer = 42"
        val tablePlain = "Key Value Alpha Beta"
        rule.setContent { M1racleReaderTheme { Column(Modifier.padding(24.dp)) {
            CompositionLocalProvider(LocalMarkdownSelection provides MarkdownSelectionContext(3, code, emptyList()) { selected = it }) {
                CodeBlockView(MarkdownBlock.CodeBlock("kotlin", code), ReaderSettings.Default, {})
            }
            CompositionLocalProvider(LocalMarkdownSelection provides MarkdownSelectionContext(4, tablePlain, emptyList()) { selected = it }) {
                TableBlockView(MarkdownBlock.Table(listOf("Key", "Value"), listOf(listOf("Alpha", "Beta")),
                    listOf(TableAlignment.Start, TableAlignment.Start)), ReaderSettings.Default)
            }
        } } }
        select(rule.onNodeWithText(code, useUnmergedTree = true), 4, 9)
        rule.runOnIdle { assertEquals(3, selected!!.blockIndex); assertEquals("answer", selected!!.excerpt) }
        select(rule.onNodeWithText("Beta", useUnmergedTree = true), 0, 2)
        rule.runOnIdle {
            android.util.Log.i("ReaderSelectionQA", "table selection=$selected")
            assertEquals(4, selected!!.blockIndex); assertEquals(16, selected!!.charStart)
            assertEquals(19, selected!!.charEnd); assertEquals("Bet", selected!!.excerpt)
        }
    }

    @Test fun wheelCommitsAccessibleSeekAndTouchRotation() {
        var value by mutableFloatStateOf(0f)
        var committed = 0f
        rule.setContent { M1racleReaderTheme { ReaderProgressWheel(value, "阅读进度", { value = it }, { committed = value }) } }
        rule.onNodeWithContentDescription("阅读进度").performSemanticsAction(SemanticsActions.SetProgress) { it(0.72f) }
        rule.runOnIdle { assertEquals(.72f, committed, .0001f) }
        rule.onNodeWithContentDescription("阅读进度").performTouchInput { click(topCenter + androidx.compose.ui.geometry.Offset(0f, 10f)) }
        rule.runOnIdle { assertEquals(.5f, committed, .02f) }
    }

    @Test fun androidSystemScrollCaptureFindsReadingListCapturesMoreAndRestoresPosition() {
        lateinit var list: androidx.compose.foundation.lazy.LazyListState
        var capturing = false
        rule.setContent { M1racleReaderTheme {
            capturing = androidx.compose.ui.platform.LocalScrollCaptureInProgress.current
            list = rememberLazyListState(3, 17)
            LazyColumn(state = list, modifier = Modifier.width(300.dp).height(400.dp)
                .background(Color.White)) {
                items(60) { index -> Text("Paragraph $index", Modifier.fillMaxWidth().height(80.dp)) }
            }
        } }
        val targets = mutableListOf<android.view.ScrollCaptureTarget>()
        rule.runOnIdle {
            val root = rule.activity.window.decorView
            root.dispatchScrollCaptureSearch(android.graphics.Rect(0, 0, root.width, root.height),
                android.graphics.Point(0, 0), java.util.function.Consumer { targets.add(it) })
        }
        assertEquals("Android must discover the reading list", 1, targets.size)
        val target = targets.single()
        val callback = target.callback
        val bounds = requireNotNull(target.scrollBounds)
        assertTrue(bounds.width() > 0 && bounds.height() > 0)
        val reader = android.media.ImageReader.newInstance(bounds.width(), bounds.height(),
            android.graphics.PixelFormat.RGBA_8888, 8)
        val frameCount = java.util.concurrent.atomic.AtomicInteger()
        reader.setOnImageAvailableListener({ source ->
            source.acquireLatestImage()?.use { frameCount.incrementAndGet() }
        }, android.os.Handler(android.os.Looper.getMainLooper()))
        val session = android.view.ScrollCaptureSession(reader.surface, bounds, target.positionInWindow)
        var started = false
        try {
            rule.runOnIdle { callback.onScrollCaptureStart(session, android.os.CancellationSignal()) { started = true } }
            rule.waitUntil(5_000) { started && capturing }
            repeat(3) { index ->
                val result = java.util.concurrent.atomic.AtomicReference<android.graphics.Rect?>()
                rule.runOnIdle {
                    callback.onScrollCaptureImageRequest(session, android.os.CancellationSignal(),
                        android.graphics.Rect(0, index * bounds.height(), bounds.width(), (index + 1) * bounds.height()),
                        java.util.function.Consumer { result.set(it) })
                }
                rule.waitUntil(10_000) { result.get() != null }
                assertEquals(bounds.height(), result.get()!!.height())
            }
            rule.waitUntil(5_000) { frameCount.get() >= 3 }
            rule.runOnIdle { assertTrue("System capture must move beyond the initial screen", list.firstVisibleItemIndex > 3) }
        } finally {
            var ended = false
            rule.runOnIdle { callback.onScrollCaptureEnd { ended = true } }
            rule.waitUntil(10_000) { ended && !capturing }
            reader.close()
        }
        rule.runOnIdle {
            assertEquals(3, list.firstVisibleItemIndex)
            assertEquals(17, list.firstVisibleItemScrollOffset)
        }
    }

    @Test fun progressiveChromeShaderRendersOnDeviceWithoutRuntimeCompilationErrors() {
        rule.setContent { M1racleReaderTheme {
            val backdrop = rememberLayerBackdrop()
            Box(Modifier.fillMaxSize()) {
                GlassBackdropSource(backdrop, Modifier.fillMaxSize().background(Color.White)) {
                    Column { repeat(30) { Text("Readable paragraph $it", Modifier.fillMaxWidth().height(24.dp)) } }
                }
                Box(Modifier.fillMaxWidth().height(180.dp).readerGradient(backdrop).testTag("progressive-chrome"))
            }
        } }
        rule.waitForIdle()
        val image = rule.onNodeWithTag("progressive-chrome").captureToImage()
        assertTrue(image.width > 0 && image.height > 0)
    }
}
