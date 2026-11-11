package com.m1racle.reader.ui.pdf

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m1racle.reader.data.local.AppDatabase
import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.data.repository.DocumentRepository
import com.m1racle.reader.data.repository.SearchRepository
import com.m1racle.reader.pdf.PageLayoutRect
import com.m1racle.reader.pdf.ContinuousEntryState
import com.m1racle.reader.pdf.ContinuousEntryPhase
import com.m1racle.reader.core.model.PdfReadingMode
import com.m1racle.reader.pdf.PdfReadingPrefs
import com.m1racle.reader.pdf.PdfTileEntry
import com.m1racle.reader.pdf.PdfTileKey
import com.m1racle.reader.core.model.QualityTier
import com.m1racle.reader.pdf.TilePixelRect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class PdfFirstFrameInstrumentedTest {
    @get:Rule val rule = createComposeRule()

    @Test fun firstReadyAlreadyContainsTheCompleteRestoredPageOnEveryReopen() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertEquals("com.m1racle.reader.qa", app.packageName)
        val id = "first-frame-${UUID.randomUUID()}"
        val file = File(app.cacheDir, "$id.pdf")
        val pdf = PdfDocument()
        try {
            repeat(3) { index ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(400, 600, index + 1).create())
                page.canvas.drawColor(if (index == 1) android.graphics.Color.RED else android.graphics.Color.BLUE)
                page.canvas.drawRect(200f, 300f, 400f, 600f, Paint().apply { color = android.graphics.Color.GREEN })
                pdf.finishPage(page)
            }
            file.outputStream().use(pdf::writeTo)
        } finally { pdf.close() }
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = ViewModelStore()
        val repository = DocumentRepository(app, db, db.documentDao(), db.pdfPrintPageAnchorDao(),
            db.textbookOutlineNodeDao(), SearchRepository(db.searchIndexDao()))
        val prefs = PdfReadingPrefs(app)
        try {
            runBlocking {
                db.documentDao().upsert(DocumentEntity(
                    id = id, displayName = "First frame.pdf", uri = Uri.fromFile(file).toString(),
                    mimeType = "application/pdf", fileSize = file.length(), createdAt = 0, lastOpenedAt = 0,
                    lastScrollIndex = 1, lastScrollOffset = 0, readingProgress = .5f, title = null,
                    headingCount = 0, documentType = "PDF", pageCount = 3,
                ))
            }
            for (mode in PdfReadingMode.entries) {
                prefs.setMode(id, mode)
                repeat(3) { attempt ->
                    val entryPage = if (attempt == 1) 2 else 1
                    runBlocking {
                        val entity = requireNotNull(db.documentDao().getById(id))
                        db.documentDao().upsert(entity.copy(
                            lastScrollIndex = 1,
                            pdfViewportSessionVersion = if (attempt == 1) 1 else null,
                            pdfViewportReadingMode = mode.toStorage(),
                            pdfViewportPageIndex = entryPage,
                            pdfPageAnchorXFraction = .5f, pdfPageAnchorYFraction = .5f,
                            pdfViewportAnchorXFraction = .5f, pdfViewportAnchorYFraction = .5f,
                            pdfViewportScale = 2f, pdfViewportGeneration = 1,
                        ))
                    }
                    val first = AtomicReference<PdfViewerUiState>()
                    rule.runOnIdle {
                        val model = ViewModelProvider(store, object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                                PdfViewerViewModel(app, id, repository, scope) as T
                        })[PdfViewerViewModel::class.java]
                        // Subscribe before open/render IO returns; do not wait for a later good frame.
                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                            first.set(model.uiState.first { it !is PdfViewerUiState.Loading })
                        }
                    }
                    rule.waitUntil(15_000) { first.get() != null }
                    val ready = first.get() as? PdfViewerUiState.Ready
                        ?: error("Initial render failed: ${first.get()}")
                    assertEquals(mode, ready.readingMode)
                    assertEquals(entryPage, ready.pageIndex)
                    assertFalse(ready.isRendering)
                    assertNotNull("first Ready must not expose an empty page", ready.pageBitmaps[entryPage])
                    assertEquals(1.5f, ready.pageAspects[entryPage]!!, 0f)
                    val pixels = ready.pageBitmaps.getValue(entryPage).toPixelMap()
                    assertEquals(if (entryPage == 1) android.graphics.Color.RED else android.graphics.Color.BLUE,
                        pixels[pixels.width / 4, pixels.height / 4].toArgb())
                    assertEquals(android.graphics.Color.GREEN, pixels[pixels.width * 3 / 4, pixels.height * 3 / 4].toArgb())
                    rule.runOnIdle { store.clear() }
                    runBlocking { scope.coroutineContext[kotlinx.coroutines.Job]?.children?.toList()?.forEach { it.join() } }
                }
            }
        } finally {
            rule.runOnIdle { store.clear() }
            runBlocking { scope.coroutineContext[kotlinx.coroutines.Job]?.children?.toList()?.forEach { it.join() } }
            scope.cancel()
            db.close()
            app.getSharedPreferences("pdf_reading_prefs", Application.MODE_PRIVATE).edit()
                .remove(PdfReadingPrefs.modeKey(id)).remove(PdfReadingPrefs.buttonsKey(id)).commit()
            file.delete()
        }
    }

    @Test fun detailTilesNeverAppearAsIsolatedBlocksBeforeTheBasePage() {
        fun image(color: Int) = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
            .apply { eraseColor(color) }.asImageBitmap()
        val tileImage = image(android.graphics.Color.RED)
        var base by mutableStateOf<ImageBitmap?>(null)
        val tile = PdfTileEntry(
            key = PdfTileKey("first-frame", 1, 1, 0, 0, 100, 0, 0, 0),
            bitmap = tileImage, pageRect = PageLayoutRect(0f, 0f, 100f, 100f),
            contentRectPx = TilePixelRect(0, 0, 100, 100), renderedAtNanos = 0,
            requestGeneration = 1, byteSize = 40_000,
        )
        rule.setContent {
            MaterialTheme {
                Box(Modifier.size(200.dp).testTag("page")) {
                    DocumentPage("first-frame", 0, 1, base, 0, QualityTier.SCROLL_PREVIEW,
                        false, {}, {}, false, listOf(tile), true, 1f, false)
                }
            }
        }
        rule.waitForIdle()
        val before = rule.onNodeWithTag("page").captureToImage().toPixelMap()
        assertEquals("partial tile must stay hidden without a complete base", android.graphics.Color.WHITE,
            before[25, 25].toArgb())
        rule.runOnIdle { base = image(android.graphics.Color.BLUE) }
        rule.waitForIdle()
        val after = rule.onNodeWithTag("page").captureToImage().toPixelMap()
        assertEquals("detail tile must still refine the base", android.graphics.Color.RED, after[25, 25].toArgb())
        assertEquals("unrefined areas keep their complete base", android.graphics.Color.BLUE,
            after[after.width - 25, after.height - 25].toArgb())
    }

    @Test fun firstViewportWaitsForTheVisibleNeighborBase() = verifyVisibleNeighbor(false)

    @Test fun aVisibleNeighborFailureExposesRetryInsteadOfHoldingLoadingForever() = verifyVisibleNeighbor(true)

    private fun verifyVisibleNeighbor(fail: Boolean) {
        val image = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
            .apply { eraseColor(android.graphics.Color.RED) }.asImageBitmap()
        var state by mutableStateOf(PdfViewerUiState.Ready(
            document = DocumentEntity(id = "neighbor", displayName = "Neighbor.pdf", uri = "qa://neighbor",
                mimeType = "application/pdf", fileSize = 0, lastOpenedAt = 0, createdAt = 0,
                lastScrollIndex = 0, lastScrollOffset = 0, readingProgress = 0f, title = null,
                headingCount = 0, documentType = "PDF", pageCount = 2),
            pageIndex = 0, pageCount = 2, pageImage = image, isRendering = false,
            pageBitmaps = mapOf(0 to image), pageAspects = mapOf(0 to 1f, 1 to 1f),
            continuousEntryState = ContinuousEntryState(phase = ContinuousEntryPhase.READY),
        ))
        var reported = false
        rule.setContent {
            MaterialTheme {
                ContinuousDocumentViewport(
                    state = state, pageJump = null, onPageJumpConsumed = {}, onViewportWidthPx = {},
                    onContinuousGeometryReady = {}, onContinuousEntryRestoreApplied = { _, _ -> },
                    onDocumentStateSettled = { _, _ -> }, onZoomGestureActive = {}, onPageSettled = {},
                    onViewportChanged = {}, onEnsureAround = {}, onRetryPage = {},
                    onInitialFrameReady = { reported = true }, modifier = Modifier.size(200.dp, 380.dp),
                )
            }
        }
        rule.waitForIdle()
        rule.runOnIdle {
            assertFalse("primary ready is insufficient while a visible neighbor is missing", reported)
            state = if (fail) state.copy(pageRenderErrors = mapOf(1 to "Synthetic render failure"))
                else state.copy(pageBitmaps = mapOf(0 to image, 1 to image))
        }
        rule.waitUntil(5_000) { reported }
        if (fail) {
            rule.onNodeWithText("Retry").assertExists()
        }
    }
}
