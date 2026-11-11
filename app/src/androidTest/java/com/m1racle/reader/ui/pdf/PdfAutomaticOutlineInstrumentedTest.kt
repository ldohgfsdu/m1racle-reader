package com.m1racle.reader.ui.pdf

import android.app.Application
import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m1racle.reader.data.local.AppDatabase
import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.data.local.TextbookOutlineNodeEntity
import com.m1racle.reader.data.repository.DocumentRepository
import com.m1racle.reader.data.repository.SearchRepository
import com.m1racle.reader.pdf.PdfBoxRuntime
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PdfAutomaticOutlineInstrumentedTest {
    @get:Rule val rule = createComposeRule()

    @Test fun firstOpenSilentlyMergesPersistsAndReopensWithoutDuplicates() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertEquals("com.m1racle.reader.qa", app.packageName)
        val id = "auto-outline-${UUID.randomUUID()}"
        val file = File(app.cacheDir, "$id.pdf")
        assertTrue(PdfBoxRuntime.ensureInit(app))
        PDDocument().use { pdf ->
            repeat(40) { pdf.addPage(PDPage()) }
            val outline = PDDocumentOutline()
            pdf.documentCatalog.documentOutline = outline
            repeat(40) { index ->
                outline.addLast(PDOutlineItem().apply {
                    title = "Chapter $index"
                    setDestination(pdf.getPage(index))
                })
            }
            pdf.save(file)
        }
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = ViewModelStore()
        val repository = DocumentRepository(app, db, db.documentDao(), db.pdfPrintPageAnchorDao(),
            db.textbookOutlineNodeDao(), SearchRepository(db.searchIndexDao()))
        try {
            runBlocking {
                db.documentDao().upsert(DocumentEntity(
                    id = id, displayName = "Automatic outline.pdf", uri = Uri.fromFile(file).toString(),
                    mimeType = "application/pdf", fileSize = file.length(), createdAt = 0, lastOpenedAt = 0,
                    lastScrollIndex = 7, lastScrollOffset = 0, readingProgress = .2f, title = null,
                    headingCount = 0, documentType = "PDF", pageCount = 40,
                ))
                db.textbookOutlineNodeDao().upsert(TextbookOutlineNodeEntity(
                    id = "manual-$id", documentId = id, parentId = null, type = "CHAPTER",
                    title = "My chapter", startPageIndex = 0, endPageIndex = 39,
                    sortOrder = 0, createdAt = 0, updatedAt = 0,
                ))
            }
            var originalIds: Set<String>? = null
            repeat(2) {
                lateinit var model: PdfViewerViewModel
                rule.runOnIdle {
                    model = ViewModelProvider(store, object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T =
                            PdfViewerViewModel(app, id, repository, scope) as T
                    })[PdfViewerViewModel::class.java]
                }
                rule.waitUntil(20_000) {
                    (model.uiState.value as? PdfViewerUiState.Ready)?.outlineNodes?.size == 41 &&
                        !model.outlineLoading.value
                }
                val ready = model.uiState.value as PdfViewerUiState.Ready
                assertEquals(7, ready.pageIndex)
                assertNull("first open must not ask to import", model.embeddedOutlineOffer.value)
                assertNull("successful automatic import stays quiet", model.importMessage.value)
                val nodes = runBlocking { repository.listOutlineNodes(id) }
                assertEquals(41, nodes.size)
                assertEquals("My chapter", nodes.single { it.id == "manual-$id" }.title)
                val ids = nodes.map { it.id }.toSet()
                if (originalIds != null) assertEquals(originalIds, ids)
                originalIds = ids
                assertEquals((0..39).toList(), nodes.filter { it.importKey != null }
                    .sortedBy { it.sortOrder }.map { it.startPageIndex })
                rule.runOnIdle { store.clear() }
            }
        } finally {
            rule.runOnIdle { store.clear() }
            scope.cancel()
            db.close()
            file.delete()
        }
    }
}
