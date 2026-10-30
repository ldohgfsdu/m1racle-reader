package com.m1racle.reader.ui.pdf

import android.app.Application
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.junit4.createComposeRule
import com.m1racle.reader.data.local.AppDatabase
import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.data.repository.DocumentRepository
import com.m1racle.reader.data.repository.SearchRepository
import com.m1racle.reader.pdf.ContinuousEntryPhase
import com.m1racle.reader.pdf.ContinuousGestureCoordinator
import com.m1racle.reader.pdf.PdfReadingMode
import com.m1racle.reader.pdf.PdfReadingPositionCodec
import com.m1racle.reader.pdf.PdfReadingPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Real PdfRenderer -> ViewModel/cache -> production geometry -> persisted/reopened anchor. */
@RunWith(AndroidJUnit4::class)
class PdfViewerRenderIntegrationInstrumentedTest {
    @get:Rule val rule = createComposeRule()

    @Test fun renderedSourceAspectsPreserveTheReadingAnchorThroughResizeAndReopen() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertEquals("com.m1racle.reader.qa", app.packageName)
        val id = "render-integration-${UUID.randomUUID()}"
        val file = File(app.cacheDir, "$id.pdf")
        val dimensions = listOf(613 to 797, 997 to 431, 431 to 997)
        val pdf = PdfDocument()
        try {
            dimensions.forEachIndexed { index, (width, height) ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, index + 1).create())
                page.canvas.drawText("Synthetic page ${index + 1}", 20f, 40f, Paint().apply { textSize = 18f })
                pdf.finishPage(page)
            }
            file.outputStream().use(pdf::writeTo)
        } finally { pdf.close() }
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = ViewModelStore()
        var model by mutableStateOf<PdfViewerViewModel?>(null)
        var width by mutableStateOf(260.dp)
        var height by mutableStateOf(420.dp)
        var availableWidth: Dp? = null
        var coordinator = ContinuousGestureCoordinator { }
        val repository = DocumentRepository(app, db, db.documentDao(), db.pdfPrintPageAnchorDao(),
            db.textbookOutlineNodeDao(), SearchRepository(db.searchIndexDao()))
        val prefs = PdfReadingPrefs(app)
        prefs.setMode(id, PdfReadingMode.VERTICAL_CONTINUOUS)
        runBlocking {
            db.documentDao().upsert(DocumentEntity(
                id = id, displayName = "Synthetic integration.pdf", uri = Uri.fromFile(file).toString(),
                mimeType = "application/pdf", fileSize = file.length(), createdAt = 0, lastOpenedAt = 0,
                lastScrollIndex = 1, lastScrollOffset = PdfReadingPositionCodec.encodeFraction(0.5f),
                readingProgress = 0.5f, title = null, headingCount = 0, documentType = "PDF", pageCount = 3,
            ))
        }
        fun createModel(): PdfViewerViewModel = ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PdfViewerViewModel(app, id, repository, applicationScope) as T
        })[PdfViewerViewModel::class.java]
        fun awaitRenderedAnchor() {
            rule.waitUntil(10_000) {
                val ready = model?.uiState?.value as? PdfViewerUiState.Ready
                ready?.continuousEntryState?.phase == ContinuousEntryPhase.READY &&
                    ready.pageBitmaps[1] != null && ready.pageAspects[1] == 431f / 997f &&
                    coordinator.snapshots.capture()?.payload?.geometry?.pageHeight(1)?.let { it > 1f } == true
            }
            rule.runOnIdle {
                val ready = model!!.uiState.value as PdfViewerUiState.Ready
                val frame = requireNotNull(coordinator.snapshots.capture()).payload
                val anchor = frame.geometry.viewportSessionAtAnchor(frame.documentState,
                    frame.viewportWidth.toFloat(), frame.viewportHeight.toFloat(), 0L)
                assertEquals(1, ready.pageIndex)
                assertEquals(431f / 997f, ready.pageAspects[1]!!, 0f)
                assertNotNull(ready.pageBitmaps[1])
                assertEquals(1, anchor.pageIndex)
                assertEquals(0.5f, anchor.pageAnchorYFraction, 0.005f)
                assertTrue(ready.pageBitmaps[1]!!.width > 0)
            }
        }
        try {
            rule.setContent {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    availableWidth = maxWidth
                    val current = model
                    if (current != null) {
                        val state by current.uiState.collectAsState()
                        val ready = state as? PdfViewerUiState.Ready
                        if (ready != null) MaterialTheme {
                            ContinuousDocumentViewport(
                                state = ready, pageJump = null, onPageJumpConsumed = current::onContinuousPageJumpConsumed,
                                onViewportWidthPx = current::onViewportWidth,
                                onContinuousGeometryReady = current::onContinuousGeometryReady,
                                onContinuousEntryRestoreApplied = current::onContinuousEntryRestoreApplied,
                                onDocumentStateSettled = current::commitContinuousDocumentState,
                                onZoomGestureActive = current::setZoomGestureActive,
                                onPageSettled = current::onVerticalPageSettled,
                                onViewportChanged = current::onContinuousViewportChanged,
                                onEnsureAround = current::ensurePagesAround, onRetryPage = current::ensurePagesAround,
                                onViewportSessionSettled = current::commitViewportSession,
                                onViewportSessionPreview = current::previewViewportSession,
                                gestureCoordinatorOverride = coordinator, modifier = Modifier.width(width).height(height),
                            )
                        }
                    }
                }
            }
            rule.runOnIdle { model = createModel() }
            awaitRenderedAnchor()
            val expandedWidth = minOf(380.dp, requireNotNull(availableWidth))
            assertTrue("fixture must exercise a wider viewport", expandedWidth > 260.dp)
            rule.runOnIdle { width = expandedWidth; height = 210.dp }
            rule.waitUntil(5_000) {
                coordinator.snapshots.capture()?.payload?.viewportWidth == with(rule.density) { width.roundToPx() }
            }
            awaitRenderedAnchor()
            rule.runOnIdle { model!!.flushProgress() }
            rule.waitUntil(5_000) {
                runBlocking { db.documentDao().getById(id) }?.let {
                    it.lastScrollIndex == 1 &&
                        kotlin.math.abs(PdfReadingPositionCodec.decodeFraction(it.lastScrollOffset) - 0.5f) < 0.005f
                } == true
            }
            rule.runOnIdle { model = null }
            rule.waitForIdle()
            rule.runOnIdle { store.clear(); coordinator = ContinuousGestureCoordinator { }; model = createModel() }
            awaitRenderedAnchor()
        } finally {
            rule.runOnIdle { model = null }
            rule.waitForIdle()
            rule.runOnIdle { store.clear() }
            runBlocking { applicationScope.coroutineContext[kotlinx.coroutines.Job]?.children?.toList()?.forEach { it.join() } }
            applicationScope.cancel()
            db.close()
            app.getSharedPreferences("pdf_reading_prefs", Application.MODE_PRIVATE).edit()
                .remove(PdfReadingPrefs.modeKey(id)).remove(PdfReadingPrefs.buttonsKey(id)).commit()
            file.delete()
        }
    }
}
