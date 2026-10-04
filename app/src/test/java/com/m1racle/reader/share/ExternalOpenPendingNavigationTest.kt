package com.m1racle.reader.share

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.room.Room
import com.m1racle.reader.data.local.AppDatabase
import com.m1racle.reader.data.local.DocumentDao
import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.data.local.DocumentType
import com.m1racle.reader.data.local.PdfPrintPageAnchorDao
import com.m1racle.reader.data.local.PdfPrintPageAnchorEntity
import com.m1racle.reader.data.local.TextbookOutlineNodeDao
import com.m1racle.reader.data.local.TextbookOutlineNodeEntity
import com.m1racle.reader.data.repository.DocumentRepository
import com.m1racle.reader.data.repository.ResolvedImportRequest
import com.m1racle.reader.identity.ContentIdentityDecision
import com.m1racle.reader.identity.PreparedContentIdentity
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

/**
 * P0-2 regression: navigation from an external open must survive the no-subscriber
 * window of an Activity rotation. Navigation is published via a replay-latest
 * [ExternalOpenCoordinator.pendingNavigation] StateFlow (never the fire-and-forget
 * effects SharedFlow, replay=0) and acked by the UI with the request id.
 *
 * Runs the REAL [ExternalOpenCoordinator] (two-phase prepare/commit state machine)
 * with a fake importer that controls prepare/commit outcomes and in-memory fake
 * DAOs (Robolectric's SQLite backends are unavailable on linux-aarch64, so no Room
 * query may run); navigation publish + ack semantics are the unit under test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class ExternalOpenPendingNavigationTest {

    private lateinit var documentDao: FakeDocumentDao
    private lateinit var repository: DocumentRepository
    private lateinit var importer: FakeImporter
    private lateinit var coordinator: ExternalOpenCoordinator
    private lateinit var context: Application

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        documentDao = FakeDocumentDao()
        // Never-opened lazy RoomDatabase: all DAO traffic is intercepted by the fakes.
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = DocumentRepository(
            context = context,
            db = db,
            documentDao = documentDao,
            pdfPrintPageAnchorDao = FakePdfPrintPageAnchorDao(),
            textbookOutlineNodeDao = FakeTextbookOutlineNodeDao(),
            searchRepository = com.m1racle.reader.testing.testSearchRepository(),
        )
        importer = FakeImporter(context, repository)
        coordinator = ExternalOpenCoordinator(context, repository, importer)
    }

    private fun viewIntent(
        uri: String = "content://provider/doc/1",
        mime: String = "text/markdown",
    ): Intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), mime)

    private fun pdfContent(id: String, activeDocumentId: String? = null) =
        preparedContent(id, DocumentType.PDF, activeDocumentId)

    private fun epubContent(id: String, activeDocumentId: String? = null) =
        preparedContent(id, DocumentType.EPUB, activeDocumentId)

    private fun newContent(id: String, activeDocumentId: String? = null) =
        preparedContent(id, DocumentType.MARKDOWN, activeDocumentId)

    private fun preparedContent(
        id: String,
        type: DocumentType,
        activeDocumentId: String?,
    ): PreparedOpen {
        val extension = when (type) {
            DocumentType.PDF -> "pdf"
            DocumentType.EPUB -> "epub"
            else -> "md"
        }
        val mime = when (type) {
            DocumentType.PDF -> "application/pdf"
            DocumentType.EPUB -> "application/epub+zip"
            else -> "text/markdown"
        }
        val stage = File.createTempFile("stage-$id", ".$extension").apply {
            writeBytes(ByteArray(10))
        }
        val uri = Uri.parse("content://provider/$id.$extension")
        val resolved = ResolvedExternalMetadata(
            displayName = "$id.$extension",
            sizeBytesHint = 10L,
            mimeType = mime,
            nameSource = ResolvedExternalMetadata.NameSource.DISPLAY_NAME,
            sizeSource = ResolvedExternalMetadata.SizeSource.OPENABLE_SIZE,
        )
        return PreparedOpen(
            importRequest = ResolvedImportRequest(
                content = PreparedContentIdentity(
                    sourceUri = uri,
                    metadata = resolved,
                    stagedFile = stage,
                    byteCount = 10L,
                    contentSha256 = "a".repeat(64),
                    decision = ContentIdentityDecision.NoExistingDocument,
                ),
                canonicalExternalKey = "doc:provider/$id",
                documentType = type,
            ),
            activeDocumentId = activeDocumentId,
        )
    }

    private fun doc(id: String) = DocumentEntity(
        id = id,
        displayName = "$id.md",
        uri = "content://provider/$id",
        mimeType = "text/markdown",
        fileSize = 10L,
        lastOpenedAt = 1L,
        createdAt = 1L,
        lastScrollIndex = 0,
        lastScrollOffset = 0,
        readingProgress = 0f,
        title = null,
        headingCount = 0,
    )

    /**
     * The P0-2 bug contract: a request published with NO subscriber must still be
     * readable later (replay-latest). The old SharedFlow(replay=0) path silently
     * dropped it in this window.
     */
    @Test
    fun content_pdf_view_intent_reaches_importer_prepare() = runTest {
        val uri = "content://provider/docs/sample.pdf"
        importer.probeCard = DocumentIdentityCard(
            documentId = null,
            displayName = "sample.pdf",
            sizeBytes = 10L,
            sizeUnknownUntilOpen = false,
            isPdf = true,
        )
        importer.prepareResult = PrepareOutcome.Success(pdfContent("pdf-1"))
        importer.commitResult = CommitOutcome.Success(
            documentId = "pdf-1",
            documentType = DocumentType.PDF,
            displayName = "sample.pdf",
            sizeBytes = 10L,
            contentSha256 = "sha-pdf-1",
            reusedExisting = false,
            bringToFrontOnly = false,
        )

        coordinator.onIncomingIntent(viewIntent(uri = uri, mime = "application/pdf"))

        assertEquals(1, importer.prepareCalls)
        assertEquals(Uri.parse(uri), importer.lastPrepareUri)
        assertEquals("application/pdf", importer.lastPrepareType)
        val req = coordinator.pendingNavigation.value
        assertNotNull(req)
        assertEquals("pdf-1", req!!.documentId)
        assertTrue(req.isPdf)
        assertFalse(req.bringToFrontOnly)
        assertEquals(
            coordinator.makeEventId(Intent.ACTION_VIEW, Uri.parse(uri), "application/pdf").value,
            req.requestId,
        )
    }

    @Test
    fun commit_epub_carries_document_type_for_epub_route() = runTest {
        importer.prepareResult = PrepareOutcome.Success(epubContent("book-1"))
        importer.commitResult = CommitOutcome.Success(
            documentId = "book-1",
            documentType = DocumentType.EPUB,
            displayName = "book-1.epub",
            sizeBytes = 10L,
            contentSha256 = "sha-book-1",
            reusedExisting = false,
            bringToFrontOnly = false,
        )

        coordinator.onIncomingIntent(
            viewIntent(uri = "content://provider/book-1.epub", mime = "application/epub+zip"),
        )

        val req = coordinator.pendingNavigation.value
        assertNotNull(req)
        assertEquals("book-1", req!!.documentId)
        assertEquals(DocumentType.EPUB, req.documentType)
        assertFalse(req.isPdf)
    }

    @Test
    fun commit_success_holds_request_even_with_no_subscriber() = runTest {        importer.prepareResult = PrepareOutcome.Success(newContent("doc-b"))
        importer.commitResult =
            CommitOutcome.Success("doc-b", DocumentType.MARKDOWN, "doc-b.md", 10L, "sha-doc-b", true, false)

        coordinator.onIncomingIntent(viewIntent())

        val req = coordinator.pendingNavigation.value
        assertNotNull("navigation request must survive without any subscriber", req)
        assertEquals("doc-b", req!!.documentId)
        assertFalse(req.isPdf)
        assertFalse(req.bringToFrontOnly)
        assertEquals(
            "requestId ties to the delivery event id",
            coordinator.makeEventId(Intent.ACTION_VIEW, viewIntent().data, "text/markdown").value,
            req.requestId,
        )
    }

    @Test
    fun ack_clears_matching_request_and_is_idempotent() = runTest {
        importer.prepareResult = PrepareOutcome.Success(newContent("doc-b"))
        importer.commitResult =
            CommitOutcome.Success("doc-b", DocumentType.MARKDOWN, "doc-b.md", 10L, "sha-doc-b", true, false)
        coordinator.onIncomingIntent(viewIntent())
        val requestId = coordinator.pendingNavigation.value!!.requestId

        coordinator.ackNavigation(requestId)
        assertNull(coordinator.pendingNavigation.value)

        coordinator.ackNavigation(requestId)
        assertNull("double ack must be a no-op", coordinator.pendingNavigation.value)
    }

    @Test
    fun stale_ack_does_not_clear_newer_request() = runTest {
        importer.prepareResult = PrepareOutcome.Success(newContent("doc-b"))
        importer.commitResult =
            CommitOutcome.Success("doc-b", DocumentType.MARKDOWN, "doc-b.md", 10L, "sha-doc-b", true, false)

        // Delivery A: commit success -> request A pending.
        coordinator.onIncomingIntent(viewIntent(uri = "content://provider/a"))
        val reqA = coordinator.pendingNavigation.value!!
        coordinator.ackNavigation(reqA.requestId)
        assertNull(coordinator.pendingNavigation.value)

        // Delivery B: commit success -> request B replaces the slot (last-wins).
        coordinator.onIncomingIntent(viewIntent(uri = "content://provider/b"))
        val reqB = coordinator.pendingNavigation.value!!
        assertTrue(reqB.requestId != reqA.requestId)

        // A slow/stale ack for A must NOT clear B.
        coordinator.ackNavigation(reqA.requestId)
        assertEquals(
            "stale ack must not clear a newer request",
            reqB,
            coordinator.pendingNavigation.value,
        )

        coordinator.ackNavigation(reqB.requestId)
        assertNull(coordinator.pendingNavigation.value)
    }

    @Test
    fun quick_bring_to_front_path_sets_request() = runTest {
        documentDao.upsert(doc("doc-a"))
        coordinator.onViewerOpened("doc-a", "sess-1")
        importer.probeCard =
            DocumentIdentityCard("doc-a", "doc-a.md", 10L, sizeUnknownUntilOpen = false, isPdf = false)
        importer.prepareResult = PrepareOutcome.Success(newContent("doc-a", activeDocumentId = "doc-a"))
        importer.commitResult = CommitOutcome.Success(
            "doc-a", DocumentType.MARKDOWN, "doc-a.md", 10L, "a".repeat(64), true, true,
        )

        coordinator.onIncomingIntent(viewIntent())

        assertEquals("alias candidate must still hash/prepare", 1, importer.prepareCalls)
        val req = coordinator.pendingNavigation.value
        assertNotNull(req)
        assertEquals("doc-a", req!!.documentId)
        assertTrue(req.bringToFrontOnly)
    }

    @Test
    fun current_active_prepare_path_sets_request() = runTest {
        importer.prepareResult = PrepareOutcome.Success(newContent("doc-a", activeDocumentId = "doc-a"))
        importer.commitResult = CommitOutcome.Success(
            "doc-a", DocumentType.MARKDOWN, "doc-a.md", 10L, "a".repeat(64), true, true,
        )

        coordinator.onIncomingIntent(viewIntent())

        val req = coordinator.pendingNavigation.value
        assertNotNull(req)
        assertEquals("doc-a", req!!.documentId)
        assertTrue("already-current open must not reset route state", req.bringToFrontOnly)
    }

    @Test
    fun failed_prepare_leaves_no_request_and_toasts() = runTest {
        val effects = mutableListOf<ExternalOpenUiEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            coordinator.effects.toList(effects)
        }
        importer.prepareResult = PrepareOutcome.Failed("import failed")

        coordinator.onIncomingIntent(viewIntent())
        advanceUntilIdle()

        assertNull(coordinator.pendingNavigation.value)
        assertEquals(1, effects.size)
        assertTrue(effects[0] is ExternalOpenUiEffect.Toast)
    }

    @Test
    fun unsupported_delivery_toasts_without_request() = runTest {
        val effects = mutableListOf<ExternalOpenUiEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            coordinator.effects.toList(effects)
        }
        importer.probeCard =
            DocumentIdentityCard(null, "mystery", null, sizeUnknownUntilOpen = true, isPdf = false)

        coordinator.onIncomingIntent(viewIntent(mime = "application/octet-stream"))
        advanceUntilIdle()

        assertNull(coordinator.pendingNavigation.value)
        assertEquals(1, effects.size)
        assertTrue(effects[0] is ExternalOpenUiEffect.ToastUnsupported)
    }

    @Test
    fun textOnlySend_isRejectedWithExplicitFileGuidance() = runTest {
        val effects = mutableListOf<ExternalOpenUiEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            coordinator.effects.toList(effects)
        }
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "# shared text")

        coordinator.onIncomingIntent(intent)
        advanceUntilIdle()

        assertEquals(listOf(ExternalOpenUiEffect.ToastTextRequiresFile), effects)
        assertNull(coordinator.pendingNavigation.value)
    }

    @Test
    fun concurrent_second_pdf_view_waits_on_mutex_then_runs_after_first() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        importer.probeCard = DocumentIdentityCard(
            documentId = null,
            displayName = "first.pdf",
            sizeBytes = 10L,
            sizeUnknownUntilOpen = false,
            isPdf = true,
        )
        importer.prepareBlock = { gate.await() }
        importer.prepareResult = PrepareOutcome.Success(pdfContent("pdf-first"))
        importer.commitResult = CommitOutcome.Success(
            "pdf-first", DocumentType.PDF, "first.pdf", 10L, "sha-first", false, false,
        )

        val first = launch {
            coordinator.onIncomingIntent(
                viewIntent(uri = "content://provider/first.pdf", mime = "application/pdf"),
            )
        }
        advanceUntilIdle()
        assertEquals(1, importer.prepareCalls)
        assertEquals(Uri.parse("content://provider/first.pdf"), importer.lastPrepareUri)

        val second = launch {
            coordinator.onIncomingIntent(
                viewIntent(uri = "content://provider/second.pdf", mime = "application/pdf"),
            )
        }
        advanceUntilIdle()
        assertEquals(
            "mutex still held by first prepare, so second VIEW cannot enter yet",
            1,
            importer.prepareCalls,
        )

        gate.complete(Unit)
        first.join()
        second.join()
        assertEquals(
            "_busy drop is unreachable across mutex; second VIEW is serialized after first",
            2,
            importer.prepareCalls,
        )
        assertEquals(Uri.parse("content://provider/second.pdf"), importer.lastPrepareUri)
    }

    @Test
    fun freshProcessRestore_handlesLaunchIntentWithReplayDisabled() = runTest {
        importer.prepareResult = PrepareOutcome.Success(newContent("restored-doc"))
        importer.commitResult = CommitOutcome.Success(
            "restored-doc", DocumentType.MARKDOWN, "restored-doc.md", 10L, "sha-restored", true, false,
        )

        coordinator.onIncomingIntent(
            viewIntent(uri = "content://provider/restored"),
            allowActivityRecreateReplay = false,
        )

        assertEquals("restored-doc", coordinator.pendingNavigation.value?.documentId)
    }

    @Test
    fun sameProcessActivityRecreate_doesNotReplayHandledIntent() = runTest {
        val intent = viewIntent(uri = "content://provider/rotation")
        importer.prepareResult = PrepareOutcome.Success(newContent("rotation-doc"))
        importer.commitResult = CommitOutcome.Success(
            "rotation-doc", DocumentType.MARKDOWN, "rotation-doc.md", 10L, "sha-rotation", true, false,
        )
        coordinator.onIncomingIntent(intent)
        val requestId = coordinator.pendingNavigation.value!!.requestId
        coordinator.ackNavigation(requestId)

        coordinator.onIncomingIntent(intent, allowActivityRecreateReplay = false)

        assertNull(coordinator.pendingNavigation.value)
    }

    @Test
    fun roomFailureAfterResolverStage_removesOperationOwnedStage() = runTest {
        val failingImporter = ExternalDocumentImporter(
            context = context,
            repository = repository,
            forceRoomUpsertFailure = true,
        )
        val prepared = newContent("rollback")
        val staged = prepared.importRequest.content.stagedFile

        val outcome = failingImporter.commitPrepared(prepared)

        assertTrue(outcome is CommitOutcome.Failed)
        assertFalse(staged.exists())
    }
}

/** Controllable importer: keeps the coordinator's two-phase flow real, controls outcomes. */
private class FakeImporter(
    context: Context,
    repository: DocumentRepository,
) : ExternalDocumentImporter(context, repository) {

    var probeCard: DocumentIdentityCard =
        DocumentIdentityCard(null, "doc.md", 10L, sizeUnknownUntilOpen = false, isPdf = false)

    var prepareResult: PrepareOutcome = PrepareOutcome.Failed("prepare not configured")
    var commitResult: CommitOutcome = CommitOutcome.Failed("commit not configured")
    var discardedCount = 0
    var prepareCalls = 0
    var lastPrepareUri: Uri? = null
    var lastPrepareType: String? = null
    var prepareBlock: (suspend () -> Unit)? = null

    override suspend fun probeIncomingCard(sourceUri: Uri, intentType: String?): DocumentIdentityCard =
        probeCard

    override suspend fun prepare(
        sourceUri: Uri,
        intentType: String?,
        grantFlags: Int,
        activeDocumentId: String?,
    ): PrepareOutcome {
        prepareCalls += 1
        lastPrepareUri = sourceUri
        lastPrepareType = intentType
        prepareBlock?.invoke()
        return prepareResult
    }

    override suspend fun commitPrepared(prepared: PreparedOpen): CommitOutcome = commitResult

    override fun discardPrepared(prepared: PreparedOpen?) {
        discardedCount++
    }
}

/** In-memory [DocumentDao]: only the coordinator-facing rows matter; loud on misuse. */
private class FakeDocumentDao : DocumentDao {
    private val rows = linkedMapOf<String, DocumentEntity>()
    private val recent = MutableStateFlow<List<DocumentEntity>>(emptyList())

    override fun observeRecentDocuments(): Flow<List<DocumentEntity>> = recent

    override suspend fun getById(id: String): DocumentEntity? = rows[id]

    override suspend fun getByContentSha256(contentSha256: String): DocumentEntity? =
        rows.values.find { it.contentSha256 == contentSha256 }

    override suspend fun getAll(): List<DocumentEntity> = rows.values.toList()

    override suspend fun upsert(document: DocumentEntity) {
        rows[document.id] = document
        recent.value = rows.values.toList()
    }

    override suspend fun insertDocument(document: DocumentEntity): Long {
        check(document.id !in rows)
        rows[document.id] = document
        recent.value = rows.values.toList()
        return rows.size.toLong()
    }

    override suspend fun updateDocument(document: DocumentEntity) {
        if (document.id in rows) rows[document.id] = document
        recent.value = rows.values.toList()
    }

    override suspend fun assignContentSha256IfNull(
        id: String,
        contentSha256: String,
        lastOpenedAt: Long,
    ): Int {
        val existing = rows[id] ?: return 0
        if (existing.contentSha256 != null) return 0
        rows[id] = existing.copy(
            contentSha256 = contentSha256,
            lastOpenedAt = lastOpenedAt,
        )
        recent.value = rows.values.toList()
        return 1
    }

    override suspend fun deleteById(id: String) {
        rows.remove(id)
        recent.value = rows.values.toList()
    }

    override suspend fun updateLastOpenedAt(id: String, time: Long) {
        rows[id]?.let { rows[id] = it.copy(lastOpenedAt = time) }
    }

    override suspend fun updateParseMetadata(id: String, title: String?, headingCount: Int) {
        rows[id]?.let { rows[id] = it.copy(title = title, headingCount = headingCount) }
    }

    override suspend fun updateReadingPosition(
        id: String,
        lastScrollIndex: Int,
        lastScrollOffset: Int,
        readingProgress: Float,
    ) {
        rows[id]?.let {
            rows[id] = it.copy(
                lastScrollIndex = lastScrollIndex,
                lastScrollOffset = lastScrollOffset,
                readingProgress = readingProgress,
            )
        }
    }

    override suspend fun updateEpubReadingPosition(
        id: String,
        lastScrollIndex: Int,
        lastScrollOffset: Int,
        readingProgress: Float,
        readingAnchor: String,
    ) = Unit
    override suspend fun updatePdfReadingPosition(
        id: String,
        pageIndex: Int,
        intraPageFractionEncoded: Int,
        pageCount: Int,
        readingProgress: Float,
    ) {
        rows[id]?.let {
            rows[id] = it.copy(
                lastScrollIndex = pageIndex,
                lastScrollOffset = intraPageFractionEncoded,
                pageCount = pageCount,
                readingProgress = readingProgress,
            )
        }
    }

    override suspend fun updatePdfViewportSession(
        id: String,
        version: Int,
        readingMode: String,
        pageIndex: Int,
        pageAnchorXFraction: Float,
        pageAnchorYFraction: Float,
        viewportAnchorXFraction: Float,
        viewportAnchorYFraction: Float,
        scale: Float,
        generation: Long,
    ) {
        rows[id]?.let {
            rows[id] = it.copy(
                pdfViewportSessionVersion = version,
                pdfViewportReadingMode = readingMode,
                pdfViewportPageIndex = pageIndex,
                pdfPageAnchorXFraction = pageAnchorXFraction,
                pdfPageAnchorYFraction = pageAnchorYFraction,
                pdfViewportAnchorXFraction = viewportAnchorXFraction,
                pdfViewportAnchorYFraction = viewportAnchorYFraction,
                pdfViewportScale = scale,
                pdfViewportGeneration = generation,
            )
        }
    }

    override suspend fun deleteAll() {
        rows.clear()
        recent.value = emptyList()
    }
}

/** Unused by these tests; any real call must fail loudly, not silently. */
private class FakePdfPrintPageAnchorDao : PdfPrintPageAnchorDao {
    override suspend fun listForDocument(documentId: String): List<PdfPrintPageAnchorEntity> =
        error("unused in ExternalOpenPendingNavigationTest")

    override suspend fun upsert(anchor: PdfPrintPageAnchorEntity) =
        error("unused in ExternalOpenPendingNavigationTest")

    override suspend fun delete(documentId: String, pdfPageIndex: Int) =
        error("unused in ExternalOpenPendingNavigationTest")

    override suspend fun deleteAllForDocument(documentId: String) =
        error("unused in ExternalOpenPendingNavigationTest")
}

/** Unused by these tests; any real call must fail loudly, not silently. */
private class FakeTextbookOutlineNodeDao : TextbookOutlineNodeDao {
    override suspend fun listForDocument(documentId: String): List<TextbookOutlineNodeEntity> =
        error("unused in ExternalOpenPendingNavigationTest")

    override suspend fun getById(id: String): TextbookOutlineNodeEntity? =
        error("unused in ExternalOpenPendingNavigationTest")

    override suspend fun upsert(node: TextbookOutlineNodeEntity) =
        error("unused in ExternalOpenPendingNavigationTest")

    override suspend fun update(node: TextbookOutlineNodeEntity) =
        error("unused in ExternalOpenPendingNavigationTest")

    override suspend fun deleteById(id: String) =
        error("unused in ExternalOpenPendingNavigationTest")

    override suspend fun deleteAllForDocument(documentId: String) =
        error("unused in ExternalOpenPendingNavigationTest")

    override suspend fun listChildren(documentId: String, parentId: String): List<TextbookOutlineNodeEntity> =
        error("unused in ExternalOpenPendingNavigationTest")

    override suspend fun listRoots(documentId: String): List<TextbookOutlineNodeEntity> =
        error("unused in ExternalOpenPendingNavigationTest")
}
