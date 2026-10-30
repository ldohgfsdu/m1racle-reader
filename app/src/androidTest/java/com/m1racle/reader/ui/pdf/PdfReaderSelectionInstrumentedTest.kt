package com.m1racle.reader.ui.pdf

import android.app.Application
import android.content.ClipboardManager
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m1racle.reader.data.anchor.HighlightStyle
import com.m1racle.reader.data.local.AppDatabase
import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.data.local.HighlightEntity
import com.m1racle.reader.data.local.NoteEntity
import com.m1racle.reader.data.repository.DocumentRepository
import com.m1racle.reader.data.repository.ReadingRepository
import com.m1racle.reader.data.repository.RoomRepositoryTransactionRunner
import com.m1racle.reader.data.repository.SearchRepository
import com.m1racle.reader.pdf.ContinuousEntryPhase
import com.m1racle.reader.pdf.PdfReadingMode
import com.m1racle.reader.pdf.PdfReadingPrefs
import com.m1racle.reader.pdf.PdfSelectablePage
import com.m1racle.reader.pdf.PdfTopBarMode
import com.m1racle.reader.ui.reader.ReaderHighlightController
import com.m1racle.reader.ui.reader.ReaderNoteController
import com.m1racle.reader.ui.theme.M1racleReaderTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * Production PdfViewerScreen integration for text selection and reader chrome.
 *
 * The fixture uses a real three-page PDF, real PdfRenderer/PDFBox extraction,
 * real Room, real controllers, and the production Compose screen. Touch points
 * are derived from the glyph rectangles returned by the production extractor
 * and mapped through the page semantics bounds.
 */
@RunWith(AndroidJUnit4::class)
class PdfReaderSelectionInstrumentedTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun freshSessionsAreImmersiveAndBodyTapsToggleChromeInBothModes() {
        withFixture { fixture ->
            fixture.show()
            fixture.awaitReady()

            fixture.assertFreshSessionChrome()
            fixture.waitForStatusBarHidden()

            fixture.jitteredTap("r7-continuous-viewport")
            fixture.assertChromeVisible()

            // The production initial lock uses SystemClock; wait past it before
            // exercising the pre-existing continuous-scroll visibility path.
            val scrollReadyAt = SystemClock.uptimeMillis() + 2_700L
            while (SystemClock.uptimeMillis() < scrollReadyAt) {
                Thread.sleep(50L)
            }
            rule.onNodeWithTag("r7-continuous-viewport").performTouchInput {
                swipe(
                    start = Offset(width * 0.5f, height * 0.82f),
                    end = Offset(width * 0.5f, height * 0.18f),
                    durationMillis = 350,
                )
            }
            fixture.awaitChromeHidden()

            fixture.tap("r7-continuous-viewport")
            fixture.assertChromeVisible()

            fixture.viewModel().setReadingMode(PdfReadingMode.HORIZONTAL_PAGED)
            fixture.awaitReady(PdfReadingMode.HORIZONTAL_PAGED)

            fixture.assertFreshSessionBarMode()
            fixture.tap("h1-horizontal-viewport")
            fixture.assertChromeHidden()
            fixture.tap("h1-horizontal-viewport")
            fixture.assertChromeVisible()
            fixture.tap("h1-horizontal-viewport")
            fixture.assertChromeHidden()
        }
    }

    @Test
    fun realGlyphDragCopiesAndPersistsTheConfirmedPartialHighlightAcrossReopen() {
        withFixture { fixture ->
            fixture.show()
            fixture.awaitReady()

            val target = fixture.selectableTarget()
            fixture.longPressDrag(target)
            fixture.assertSelectionActions()

            rule.onNodeWithTag("pdf-selection-action-copy").performClick()
            rule.waitUntil(5_000) { rule.activity.window.decorView.hasWindowFocus() }
            val copied = requireNotNull(
                rule.activity.getSystemService(ClipboardManager::class.java),
            )
                .primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(rule.activity)
                ?.toString()
            assertEquals(target.expectedExcerpt, copied)
            rule.onNodeWithTag("pdf-selection-actions").assertDoesNotExist()
            fixture.assertChromeHidden()

            fixture.longPressDrag(target)
            rule.onNodeWithTag("pdf-selection-action-highlight").performClick()
            rule.onNodeWithText("所选文字").assertExists()
            rule.onNodeWithText("绿色").performClick()
            rule.onNodeWithText("高亮").performClick()

            val stored = fixture.awaitHighlight()
            assertEquals(target.expectedExcerpt, stored.excerpt)
            assertEquals(HighlightStyle.Green.name, stored.style)
            assertEquals(0, stored.location)
            fixture.assertChromeHidden()

            fixture.reopen()
            fixture.awaitReady()
            fixture.assertFreshSessionChrome()
            fixture.openAnnotations()
            fixture.awaitText(target.expectedExcerpt)
            rule.onNodeWithText(target.expectedExcerpt).assertExists()
        }
    }

    @Test
    fun realGlyphSelectionOpensExistingNoteEditorAndPersistsAcrossReopen() {
        withFixture { fixture ->
            fixture.show()
            fixture.awaitReady()

            fixture.longPressDrag(fixture.selectableTarget())
            fixture.assertSelectionActions()
            rule.onNodeWithTag("pdf-selection-action-note").performClick()

            rule.onNodeWithText("批注：第 1 页").assertExists()
            fixture.assertChromeHidden()
            rule.onNode(hasSetTextAction()).performTextInput("真实字形批注")
            rule.onNodeWithText("保存").assertIsEnabled().performClick()

            val stored = fixture.awaitNote()
            assertEquals("真实字形批注", stored.content)
            assertEquals(0, stored.location)
            assertFalse(stored.anchor.isNullOrBlank())

            fixture.reopen()
            fixture.awaitReady()
            fixture.openAnnotations()
            fixture.awaitText("真实字形批注")
            rule.onNodeWithText("真实字形批注").assertExists().performClick()

            rule.onNodeWithText("批注：第 1 页").assertExists()
            rule.onNodeWithText("真实字形批注").assertExists()
        }
    }

    @Test
    fun clearingASelectionConsumesTheTapBeforeChromeCanToggle() {
        withFixture { fixture ->
            fixture.show()
            fixture.awaitReady()
            fixture.longPressDrag(fixture.selectableTarget())
            fixture.assertSelectionActions()
            rule.onNodeWithContentDescription("PDF page 2 of 3", useUnmergedTree = true)
                .assertIsDisplayed().performTouchInput { click(center) }
            rule.waitForIdle()
            rule.onNodeWithTag("pdf-selection-actions").assertDoesNotExist()
            fixture.assertChromeHidden()
            fixture.tap("r7-continuous-viewport")
            fixture.assertChromeVisible()
        }
    }

    private fun withFixture(block: (Fixture) -> Unit) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertEquals("com.m1racle.reader.qa", app.packageName)
        val fixture = Fixture(app)
        try {
            block(fixture)
        } finally {
            fixture.close()
        }
    }

    private data class GlyphTarget(
        val page: PdfSelectablePage,
        val start: PdfSelectablePage.Hit,
        val end: PdfSelectablePage.Hit,
        val expectedExcerpt: String,
    )

    private inner class Fixture(private val app: Application) {
        private val documentId = "pdf-selection-ui-${UUID.randomUUID()}"
        private val pdfFile = File(app.cacheDir, "$documentId.pdf")
        private val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val searchRepository = SearchRepository(db.searchIndexDao())
        private val documentRepository = DocumentRepository(
            app,
            db,
            db.documentDao(),
            db.pdfPrintPageAnchorDao(),
            db.textbookOutlineNodeDao(),
            searchRepository,
        )
        private val readingRepository = ReadingRepository(
            db.bookmarkDao(),
            db.favoriteDocumentDao(),
            db.highlightDao(),
            db.noteDao(),
            searchRepository,
            RoomRepositoryTransactionRunner(db),
        )
        private val prefs = PdfReadingPrefs(app)
        private val store = ViewModelStore()
        private var model by mutableStateOf<PdfViewerViewModel?>(null)

        init {
            createPdf()
            runBlocking {
                db.documentDao().upsert(
                    DocumentEntity(
                        id = documentId,
                        displayName = "Selection integration.pdf",
                        uri = Uri.fromFile(pdfFile).toString(),
                        mimeType = "application/pdf",
                        fileSize = pdfFile.length(),
                        createdAt = 0L,
                        lastOpenedAt = 0L,
                        lastScrollIndex = 0,
                        lastScrollOffset = 0,
                        readingProgress = 0f,
                        title = null,
                        headingCount = 0,
                        documentType = "PDF",
                        pageCount = 3,
                    ),
                )
            }
            prefs.setMode(documentId, PdfReadingMode.VERTICAL_CONTINUOUS)
        }

        fun show() {
            rule.setContent {
                val current = model
                if (current != null) {
                    val highlightController = remember(current) {
                        ReaderHighlightController(readingRepository, documentId)
                    }
                    val noteController = remember(current) {
                        ReaderNoteController(readingRepository, documentId)
                    }
                    M1racleReaderTheme {
                        PdfViewerScreen(
                            viewModel = current,
                            onBack = {},
                            highlightController = highlightController,
                            noteController = noteController,
                        )
                    }
                }
            }
            rule.runOnIdle {
                model = createViewModel()
            }
        }

        fun reopen() {
            rule.runOnIdle { model = null }
            rule.waitForIdle()
            rule.runOnIdle {
                store.clear()
                model = createViewModel()
            }
        }

        fun viewModel(): PdfViewerViewModel = checkNotNull(model)

        fun awaitReady(expectedMode: PdfReadingMode = PdfReadingMode.VERTICAL_CONTINUOUS) {
            rule.waitUntil(20_000) {
                val ready = model?.uiState?.value as? PdfViewerUiState.Ready
                val pageReady = when (ready?.readingMode) {
                    PdfReadingMode.VERTICAL_CONTINUOUS ->
                        ready.pageBitmaps[ready.pageIndex] != null &&
                            ready.continuousEntryState.phase == ContinuousEntryPhase.READY
                    PdfReadingMode.HORIZONTAL_PAGED ->
                        ready.pageImage != null || ready.pageBitmaps[ready.pageIndex] != null
                    null -> false
                }
                ready != null &&
                    ready.readingMode == expectedMode &&
                    ready.pageCount == 3 &&
                    ready.pageAspects[ready.pageIndex] != null &&
                    pageReady
            }
            rule.runOnIdle {
                val ready = viewModel().uiState.value as PdfViewerUiState.Ready
                assertEquals(expectedMode, ready.readingMode)
                assertEquals(3, ready.pageCount)
                assertNotNull(ready.pageAspects[ready.pageIndex])
            }
            rule.waitForIdle()
        }

        fun assertFreshSessionChrome() {
            assertFreshSessionBarMode()
            assertChromeHidden()
        }

        fun assertFreshSessionBarMode() {
            assertEquals(PdfTopBarMode.IMMERSIVE, viewModel().topBarMode.value)
        }

        fun waitForStatusBarHidden() {
            val decor = rule.activity.window.decorView
            rule.waitUntil(5_000) {
                val insets = ViewCompat.getRootWindowInsets(decor)
                insets != null &&
                    !insets.isVisible(WindowInsetsCompat.Type.statusBars())
            }
        }

        fun tap(tag: String) {
            rule.onNodeWithTag(tag).assertIsDisplayed().performTouchInput { click(center) }
            rule.waitForIdle()
        }

        fun jitteredTap(tag: String) {
            rule.onNodeWithTag(tag).assertIsDisplayed().performTouchInput {
                down(center)
                moveTo(center + Offset(1f, 1f), 30L)
                up()
            }
            rule.waitForIdle()
        }

        fun assertChromeVisible() {
            rule.onNodeWithContentDescription("返回").assertIsDisplayed()
            rule.onNodeWithContentDescription("更多").assertIsDisplayed()
            rule.onNodeWithTag("pdf-location-capsule").assertIsDisplayed()
        }

        fun assertChromeHidden() {
            rule.onNodeWithContentDescription("返回").assertDoesNotExist()
            rule.onNodeWithContentDescription("更多").assertDoesNotExist()
            rule.onNodeWithTag("pdf-location-capsule").assertDoesNotExist()
        }

        fun awaitChromeHidden() {
            rule.waitUntil(5_000) {
                rule.onAllNodesWithContentDescription("返回")
                    .fetchSemanticsNodes()
                    .isEmpty()
            }
            assertChromeHidden()
        }

        fun selectableTarget(): GlyphTarget {
            val page = runBlocking { viewModel().selectableText(0) }
            assertTrue("Android PdfDocument text must be extractable", page.lines.isNotEmpty())
            assertTrue(page.unavailableReason == null)
            val lineIndex = page.lines.indexOfFirst { line ->
                line.glyphs.count { it.text.isNotBlank() } >= 4
            }
            assertTrue("Need a selectable line with glyph rectangles", lineIndex >= 0)
            val glyphs = page.lines[lineIndex].glyphs
            val startGlyph = glyphs.indexOfFirst { it.text.isNotBlank() }
            val endGlyph = (startGlyph + 4 until glyphs.size)
                .firstOrNull { glyphs[it].text.isNotBlank() }
                ?: error("Need at least two selectable glyphs in the chosen line")
            val start = PdfSelectablePage.Hit(lineIndex, startGlyph)
            val end = PdfSelectablePage.Hit(lineIndex, endGlyph)
            val expected = page.selection(start, end).excerpt
            assertTrue("Selection must contain actual glyph text", expected.isNotBlank())
            val wholePageText = page.lines.joinToString("\n") { line ->
                line.glyphs.joinToString("") { it.text }
            }
            assertTrue("Selection must be a partial range", expected.length < wholePageText.length)

            // The page annotation owns its own extraction effect. The mutex
            // guarantees extraction order; this yields its state assignment
            // before the test injects a physical long-press stream.
            Thread.sleep(100L)
            rule.waitForIdle()
            return GlyphTarget(page, start, end, expected)
        }

        fun longPressDrag(target: GlyphTarget) {
            val pageNode = rule.onNodeWithContentDescription(
                "PDF page 1 of 3",
                useUnmergedTree = true,
            ).assertIsDisplayed()
            val pageBounds = pageNode.fetchSemanticsNode().boundsInRoot
            check(pageBounds.width > 0f && pageBounds.height > 0f)
            val startRect = target.page.lines[target.start.line]
                .glyphs[target.start.glyph].rect
            val endRect = target.page.lines[target.end.line]
                .glyphs[target.end.glyph].rect

            pageNode.performTouchInput {
                down(
                    Offset(
                        x = (startRect.x + startRect.width / 2f) * pageBounds.width,
                        y = (startRect.y + startRect.height / 2f) * pageBounds.height,
                    ),
                )
                advanceEventTime(800L)
                moveTo(
                    Offset(
                        x = (endRect.x + endRect.width / 2f) * pageBounds.width,
                        y = (endRect.y + endRect.height / 2f) * pageBounds.height,
                    ),
                )
                advanceEventTime(60L)
                up()
            }
            rule.waitUntil(5_000) {
                rule.onAllNodesWithTag("pdf-selection-actions")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
        }

        fun assertSelectionActions() {
            rule.onNodeWithTag("pdf-selection-handle-anchor").assertExists()
            rule.onNodeWithTag("pdf-selection-handle-focus").assertExists()
            rule.onNodeWithTag("pdf-selection-actions").assertExists()
            rule.onNodeWithText("所选文字").assertDoesNotExist()
            rule.onNodeWithText("复制").assertExists()
            rule.onNodeWithText("高亮").assertExists()
            rule.onNodeWithText("批注").assertExists()
        }

        fun awaitHighlight(): HighlightEntity {
            rule.waitUntil(10_000) {
                runBlocking { db.highlightDao().listForDocument(documentId) }.isNotEmpty()
            }
            return runBlocking { db.highlightDao().listForDocument(documentId) }.single()
        }

        fun awaitNote(): NoteEntity {
            rule.waitUntil(10_000) {
                runBlocking { db.noteDao().listForDocument(documentId) }.isNotEmpty()
            }
            return runBlocking { db.noteDao().listForDocument(documentId) }.single()
        }

        fun openAnnotations() {
            tap("r7-continuous-viewport")
            assertChromeVisible()
            rule.onNodeWithContentDescription("更多").performClick()
            rule.onNodeWithText("标注列表").assertExists().performClick()
            awaitText("标注")
            rule.onNodeWithText("标注").assertExists()
        }

        fun awaitText(text: String) {
            rule.waitUntil(10_000) {
                rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
            }
        }

        fun close() {
            try {
                rule.runOnIdle { model = null }
                rule.waitForIdle()
            } finally {
                rule.runOnIdle { store.clear() }
                runBlocking {
                    applicationScope.coroutineContext[kotlinx.coroutines.Job]?.children?.toList()?.forEach { it.join() }
                }
                applicationScope.cancel()
                db.close()
                app.getSharedPreferences("pdf_reading_prefs", Application.MODE_PRIVATE)
                    .edit()
                    .remove(PdfReadingPrefs.modeKey(documentId))
                    .remove(PdfReadingPrefs.buttonsKey(documentId))
                    .commit()
                pdfFile.delete()
            }
        }

        private fun createViewModel(): PdfViewerViewModel =
            ViewModelProvider(
                store,
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        PdfViewerViewModel(
                            app,
                            documentId,
                            documentRepository,
                            applicationScope,
                        ) as T
                },
            )[PdfViewerViewModel::class.java]

        private fun createPdf() {
            val pdf = PdfDocument()
            try {
                val pageText = listOf(
                    listOf(
                        "Selection proof: real glyphs stay here.",
                        "A second line keeps note anchors visible.",
                    ),
                    listOf(
                        "Page two stores a distinct selectable sentence.",
                        "Its bounds prove the extractor uses real PDF text.",
                    ),
                    listOf(
                        "Page three closes the unique three page fixture.",
                        "Every line is ordinary horizontal text.",
                    ),
                )
                pageText.forEachIndexed { index, lines ->
                    val page = pdf.startPage(
                        PdfDocument.PageInfo.Builder(612, 792, index + 1).create(),
                    )
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.BLACK
                        textSize = 24f
                    }
                    lines.forEachIndexed { lineIndex, text ->
                        page.canvas.drawText(text, 54f, 110f + lineIndex * 42f, paint)
                    }
                    pdf.finishPage(page)
                }
                pdfFile.outputStream().use(pdf::writeTo)
            } finally {
                pdf.close()
            }
        }
    }
}
