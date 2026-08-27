package com.markleaf.app

import android.app.Application
import com.markleaf.app.data.local.AppDatabase
import com.markleaf.app.data.repository.DocumentRepository
import com.markleaf.app.data.repository.ReadingRepository
import com.markleaf.app.data.repository.SearchRepository
import com.markleaf.app.data.settings.ReaderSettingsRepository
import com.markleaf.app.share.ExternalOpenCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MarkLeafApplication : Application() {

    /**
     * Process-lifetime scope for work that must outlive a ViewModel's onCleared.
     * - SupervisorJob: a child failure does not cancel siblings.
     * - Dispatchers.IO: close/flush operations acquire renderer/Room mutexes.
     * - NOT GlobalScope: owned by the Application so test code can substitute it.
     *
     * P0A lifecycle hardening: PdfViewerViewModel.onCleared uses this scope to
     * issue idempotent renderer.closeAsync()/thumbnailLoader.closeAsync() so
     * the main thread never runBlocks a mutex wait, and viewModelScope
     * cancellation (which AndroidX performs BEFORE onCleared — see
     * ViewModel.clear$lifecycle_viewmodel_release bytecode) cannot drop the
     * final close.
     */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy {
        AppDatabase.getInstance(this)
    }

    val documentRepository: DocumentRepository by lazy {
        DocumentRepository(
            context = this,
            db = database,
            documentDao = database.documentDao(),
            pdfPrintPageAnchorDao = database.pdfPrintPageAnchorDao(),
            textbookOutlineNodeDao = database.textbookOutlineNodeDao(),
            searchRepository = searchRepository,
        )
    }

    val readerSettingsRepository: ReaderSettingsRepository by lazy {
        ReaderSettingsRepository(this)
    }

    /** H2.1 Reading Library 门面（书签/收藏；阅读位置权威在 DocumentRepository）。 */
    val readingRepository: ReadingRepository by lazy {
        ReadingRepository(
            bookmarkDao = database.bookmarkDao(),
            favoriteDao = database.favoriteDocumentDao(),
            highlightDao = database.highlightDao(),
            noteDao = database.noteDao(),
            searchRepository = searchRepository,
        )
    }

    /** R29 local-search data boundary; no UI surface is introduced here. */
    val searchRepository: SearchRepository by lazy {
        SearchRepository(database.searchIndexDao())
    }

    val externalOpenCoordinator: ExternalOpenCoordinator by lazy {
        ExternalOpenCoordinator(
            app = this,
            repository = documentRepository,
        )
    }
}
