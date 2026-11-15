package com.m1racle.reader.share

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.m1racle.reader.data.local.AppDatabase
import com.m1racle.reader.data.local.DocumentDao
import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.data.local.DocumentType
import com.m1racle.reader.data.local.PdfPrintPageAnchorDao
import com.m1racle.reader.data.local.PdfPrintPageAnchorEntity
import com.m1racle.reader.data.local.TextbookOutlineNodeDao
import com.m1racle.reader.data.local.TextbookOutlineNodeEntity
import com.m1racle.reader.data.repository.DocumentRepository
import com.m1racle.reader.data.repository.ImportResult
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

/**
 * Import acceptance parity golden — policy UNIFIED state (import policy batch 2).
 *
 * History: the batch-1 version of this file pinned the pre-unification split
 * (Path A extension-driven gate vs Path B ExternalFileIntake whitelist + 200MB
 * cap) with six gate-level diff cells; that baseline lives in git history.
 * Batch 2 removed the whitelist and the redundant repository branches, making
 * `DocumentType.isAllowedImport` the single authority for both entries, and
 * this file now pins the UNIFIED verdicts:
 *
 * - Path A = FAB/SAF entry (`DocumentRepository.importDocument`) gates with
 *   `DocumentType.isAllowedImport(mime,dn,ps)` only (no size cap — unchanged).
 * - Path B = VIEW/SEND entry (`ExternalOpenCoordinator` +
 *   `ExternalDocumentImporter.prepare`) gates with the same function
 *   (plus its 200 MB source-size cap in prepare — unchanged).
 *
 * PDF-only admission (2026-11-15). Both entries still share
 * DocumentType.isAllowedImport, and the verdicts are now:
 *   - a .pdf name is accepted under every mime;
 *   - an extensionless name is accepted only when the mime itself is PDF;
 *   - any other extension (.md, .zip, .jpg, .epub, …) is rejected even when
 *     the mime says application/pdf;
 *   - both paths still agree on every cell.
 *
 * Entry-level E2E pairs keep proving: extensionless text normalizes to
 * "<segment>.txt" via the shared resolve layer and is accepted by BOTH paths;
 * wrong-extension text is now rejected by BOTH paths (was B-only accept);
 * the 200 MB source cap is unified across both entries as of policy batch 3
 * (boundary matrix lives in ImportSizeLimitBoundaryTest; zip extraction caps
 * stay independent).
 *
 * No Room query ever runs (linux-aarch64 has no Robolectric SQLite backend):
 * DAOs are fakes; the RoomDatabase instance is built but never queried.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class ImportAcceptanceParityGoldenTest {

    // ── Matrix axes ──────────────────────────────────────────────────────────

    /** Fixed mime order for the golden rows below. */
    private val mimes = listOf(
        "application/pdf",
        "application/x-pdf",
        "application/zip",
        "application/x-zip-compressed",
        "application/x-zip",
        "text/markdown",
        "text/x-markdown",
        "text/plain",
        "application/octet-stream",
        "image/jpeg",
        "text/html",
        "application/msword",
        null,
    )

    /**
     * Path A entry verdict: the exact gate expression inside
     * DocumentRepository.importDocument (single authority, batch 2).
     */
    private fun pathA(mime: String?, name: String): Boolean =
        DocumentType.isAllowedImport(mime, name, name)

    /**
     * Path B entry verdict: the exact gate expression shared by
     * ExternalOpenCoordinator and ExternalDocumentImporter.prepare.
     */
    private fun pathB(mime: String?, name: String): Boolean =
        DocumentType.isAllowedImport(mime, name, name)

    // ── PART 1: golden matrix ────────────────────────────────────────────────

    private fun assertRow(name: String, expectedA: String, expectedB: String) {
        assertEquals("row length", mimes.size, expectedA.length)
        assertEquals("row length", mimes.size, expectedB.length)
        mimes.forEachIndexed { i, mime ->
            val a = pathA(mime, name)
            val b = pathB(mime, name)
            assertEquals(
                "PathA[$mime,$name]",
                expectedA[i] == 'Y',
                a,
            )
            assertEquals(
                "PathB[$mime,$name]",
                expectedB[i] == 'Y',
                b,
            )
        }
    }

    @Test
    fun golden_documentExtensionNames_acceptedByBothPathsForEveryMime() {
        // Only a .pdf name is extension-admitted, and it ignores mime.
        assertRow("doc.pdf", expectedA = "YYYYYYYYYYYYY", expectedB = "YYYYYYYYYYYYY")
        assertRow("DOC.PDF", expectedA = "YYYYYYYYYYYYY", expectedB = "YYYYYYYYYYYYY")
        // Former document extensions are no longer admitted under any mime.
        assertRow("note.md", expectedA = "NNNNNNNNNNNNN", expectedB = "NNNNNNNNNNNNN")
        assertRow("pack.zip", expectedA = "NNNNNNNNNNNNN", expectedB = "NNNNNNNNNNNNN")
        assertRow("book.epub", expectedA = "NNNNNNNNNNNNN", expectedB = "NNNNNNNNNNNNN")
    }

    @Test
    fun golden_extensionlessName_textFamilyAcceptedByFallback_bothPathsIdentical() {
        // No text fallback remains. An extensionless name enters only when the
        // mime itself is PDF (indexes 0 and 1); every other mime is rejected.
        assertRow("README", expectedA = "YYNNNNNNNNNNN", expectedB = "YYNNNNNNNNNNN")
    }

    @Test
    fun golden_wrongExtensionName_rejectedOnBothPaths() {
        // A non-pdf extension loses even to an application/pdf mime.
        assertRow("photo.jpg", expectedA = "NNNNNNNNNNNNN", expectedB = "NNNNNNNNNNNNN")
    }

    @Test
    fun parity_diffCells_empty_afterUnification() {
        val diffCells = mutableListOf<String>()
        for (name in listOf("doc.pdf", "note.md", "pack.zip", "README", "photo.jpg", "DOC.PDF")) {
            for (mime in mimes) {
                if (pathA(mime, name) != pathB(mime, name)) {
                    diffCells += "$mime|$name"
                }
            }
        }
        assertEquals(emptyList<String>(), diffCells)
    }

    // ── PART 2: unified authority semantics (fallback + denylist) ────────────

    @Test
    fun authority_documentExtensionNames_acceptedForEveryMime() {
        val pdfNames = listOf("a.pdf", "教材.PDF", "my.file.pdf", "sp ace.pdf", "a.tar.pdf")
        for (name in pdfNames) {
            for (mime in mimes) {
                assertTrue("isAllowedImport($mime,'$name')", DocumentType.isAllowedImport(mime, name, name))
            }
        }
        val rejected = listOf("a.md", "a.markdown", "a.txt", "a.zip", "a.epub", "a.MD", "a.Zip")
        for (name in rejected) {
            for (mime in mimes) {
                assertFalse(
                    "isAllowedImport($mime,'$name') must reject a non-pdf extension",
                    DocumentType.isAllowedImport(mime, name, name),
                )
            }
        }
    }

    @Test
    fun authority_textFallback_matrix() {
        // Text never enters, including the old "text/* + no extension" fallback.
        assertFalse(DocumentType.isAllowedImport("text/plain", "README", "README"))
        assertFalse(DocumentType.isAllowedImport("text/markdown", "notes123", "notes123"))
        assertFalse(DocumentType.isAllowedImport("text/html", "page1", "page1"))
        assertFalse(DocumentType.isAllowedImport("text/plain", "a.md", null))
        assertFalse(DocumentType.isAllowedImport("text/plain", "a.jpg.md", null))

        // A pdf mime does not override a contradictory extension.
        assertFalse(DocumentType.isAllowedImport("application/pdf", "book.epub", "book.epub"))
        assertFalse(DocumentType.isAllowedImport("application/pdf", "notes.md", null))
        assertFalse(DocumentType.isAllowedImport("application/x-pdf", "pack.zip", "pack.zip"))
        // The path segment counts too: a pdf display name cannot hide an epub path.
        assertFalse(DocumentType.isAllowedImport("application/pdf", "book.pdf", "book.epub"))

        // Extensionless + pdf mime is the one non-named admission.
        assertTrue(DocumentType.isAllowedImport("application/pdf", "README", "README"))
        assertTrue(DocumentType.isAllowedImport("application/pdf", "scan.pdf", null))

        assertFalse(DocumentType.isAllowedImport("application/octet-stream", "mystery", "mystery"))
        assertFalse(DocumentType.isAllowedImport("image/jpeg", "x.png", null))
        assertFalse(DocumentType.isAllowedImport(null, "README2", "README2"))
    }

    @Test
    fun authority_controlGroup_denylistClosesMojibakeHole_unknownExtensionsEnterAsText() {
        val named = listOf("a.jpg", "photo.jpeg", "教材.docx", "A.JPG", "clip.mp4", "a.tar.gz", "a.mdown")
        for (name in named) {
            assertFalse(
                "isAllowedImport must reject '$name'",
                DocumentType.isAllowedImport("text/plain", name, name),
            )
            assertFalse(
                "a pdf mime must not override '$name'",
                DocumentType.isAllowedImport("application/pdf", name, name),
            )
        }
        for (name in listOf("a.mdown", "README2", "教材（第二版）")) {
            assertFalse(DocumentType.isAllowedImport(null, name, name))
        }
        assertFalse(DocumentType.isAllowedImport("text/plain", ".md", ".md"))
        assertTrue(DocumentType.hasNonDocumentExtension(".jpg"))
        assertFalse(DocumentType.hasNonDocumentExtension(".md"))
        assertFalse(DocumentType.hasNonDocumentExtension("noext"))
        assertFalse(DocumentType.hasNonDocumentExtension("trailing."))
        assertFalse(DocumentType.hasNonDocumentExtension(""))
        assertFalse(DocumentType.hasNonDocumentExtension(null))
    }

    // ── PART 3: entry-level E2E spot checks of the three pinned divergences ──

    private lateinit var context: android.content.Context
    private lateinit var resolver: android.content.ContentResolver
    private lateinit var tempDir: File
    private lateinit var repository: DocumentRepository
    private lateinit var fakeDao: RecordingDocumentDao

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        resolver = context.contentResolver
        tempDir = context.cacheDir.resolve("t0-${System.nanoTime()}").apply { mkdirs() }
    }

    private fun installRepository(): DocumentRepository {
        fakeDao = RecordingDocumentDao()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        return DocumentRepository(
            context = context,
            db = db,
            documentDao = fakeDao,
            pdfPrintPageAnchorDao = UnusedPdfPrintPageAnchorDao(),
            textbookOutlineNodeDao = UnusedTextbookOutlineNodeDao(),
            searchRepository = com.m1racle.reader.testing.testSearchRepository(),
            documentAliasDao = com.m1racle.reader.testing.TestDocumentAliasDao(),
            importTransactionRunner = com.m1racle.reader.testing.ImmediateRepositoryTransactionRunner(),
        )
    }

    private class StubContentProvider : ContentProvider() {
        companion object {
            var row: List<Any?>? = null
            var type: String? = null
            var backingFile: File? = null
        }

        override fun onCreate() = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            args: Array<out String>?,
            sort: String?,
        ): Cursor = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
            row?.let { addRow(it) }
        }

        override fun getType(uri: Uri): String? = type
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = 0
        override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = 0

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
            ParcelFileDescriptor.open(backingFile!!, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun stubUri(authority: String, segment: String): Uri =
        Uri.parse("content://$authority/root/$segment")

    private fun registerStub(authority: String) {
        val backing = File(tempDir, "backing-$authority.bin").apply { writeBytes(ByteArray(16)) }
        StubContentProvider.backingFile = backing
        Robolectric.setupContentProvider(StubContentProvider::class.java, authority)
    }

    private fun importerWithTinyStream(repository: DocumentRepository): ExternalDocumentImporter =
        ExternalDocumentImporter(
            context = context,
            repository = repository,
            openStreamOverride = { _ -> ByteArrayInputStream("# tiny".toByteArray()) },
        )

    /** Extensionless text is no longer imported by either entry. */
    @Test
    fun d1_bothEntries_acceptExtensionlessTextViaSharedNameNormalization() = runBlocking {
        registerStub("d1a")
        StubContentProvider.row = null
        StubContentProvider.type = "text/plain"
        val uri = stubUri("d1a", "notes123")

        val pathA = installRepository().importDocument(uri)
        assertEquals(ImportResult.UnsupportedFormat, pathA)
        assertEquals(0, fakeDao.upserts.size)

        val pathB = importerWithTinyStream(installRepository()).prepare(uri, intentType = null)
        assertEquals(PrepareOutcome.Unsupported, pathB)
    }

    /** D2 pair: text/plain + wrong (image) extension. */
    @Test
    fun d2_pathA_rejectsWrongExtensionTextToday() = runBlocking {
        registerStub("d2a")
        StubContentProvider.row = listOf("photo.jpg", 5L)
        StubContentProvider.type = "text/plain"
        val repo = installRepository()

        val result = repo.importDocument(stubUri("d2a", "doc"))

        assertEquals(ImportResult.UnsupportedFormat, result)
        assertEquals(0, fakeDao.upserts.size)
    }

    @Test
    fun d2_pathB_rejectsWrongExtensionTextAfterUnification() = runBlocking {
        registerStub("d2b")
        StubContentProvider.row = listOf("photo.jpg", 5L)
        StubContentProvider.type = "text/plain"
        val repo = installRepository()

        // Pre-unification this entry staged a MARKDOWN-typed mojibake document;
        // the unified authority's denylist closes that hole on both paths.
        val outcome = importerWithTinyStream(repo)
            .prepare(stubUri("d2b", "doc"), intentType = null)

        assertEquals(PrepareOutcome.Unsupported, outcome)
    }

    /** Non-PDF is rejected at the gate, before any size check or copy. */
    @Test
    fun d3_pathA_rejectsHugeSourceAfterBatch3() = runBlocking {
        registerStub("d3a")
        val huge = 300L * 1024L * 1024L
        StubContentProvider.row = listOf("big.md", huge)
        StubContentProvider.type = "text/plain"
        val repo = installRepository()

        val result = repo.importDocument(stubUri("d3a", "doc"))

        assertEquals(ImportResult.UnsupportedFormat, result)
        assertEquals(0, fakeDao.upserts.size)
    }

    @Test
    fun d3_pathB_rejectsHugeSourceWithTooLargeToday() = runBlocking {
        registerStub("d3b")
        val huge = 300L * 1024L * 1024L
        StubContentProvider.row = listOf("big.md", huge)
        StubContentProvider.type = "text/plain"
        val repo = installRepository()

        val outcome = importerWithTinyStream(repo).prepare(stubUri("d3b", "doc"), intentType = null)

        assertEquals(PrepareOutcome.Unsupported, outcome)
        assertEquals(0, fakeDao.upserts.size)
    }

    @Test
    fun d3_pathB_boundary_exactlyMaxBytesPassesHintStageAndCopies() = runBlocking {
        registerStub("d3c")
        StubContentProvider.row = listOf("big.md", ExternalDocumentImporter.MAX_BYTES)
        StubContentProvider.type = "text/plain"
        val repo = installRepository()

        val outcome = importerWithTinyStream(repo).prepare(stubUri("d3c", "doc"), intentType = null)

        assertEquals(PrepareOutcome.Unsupported, outcome)
        assertEquals(0, fakeDao.upserts.size)
    }
}

/** Records upserts; everything else mirrors an empty library. */
private class RecordingDocumentDao : DocumentDao {
    val upserts = mutableListOf<DocumentEntity>()
    private val recent = MutableStateFlow<List<DocumentEntity>>(emptyList())

    override fun observeRecentDocuments(): Flow<List<DocumentEntity>> = recent
    override suspend fun getById(id: String): DocumentEntity? = upserts.find { it.id == id }
    override suspend fun getByContentSha256(contentSha256: String): DocumentEntity? =
        upserts.find { it.contentSha256 == contentSha256 }
    override suspend fun getAll(): List<DocumentEntity> = upserts.toList()
    override suspend fun upsert(document: DocumentEntity) {
        upserts.removeAll { it.id == document.id }
        upserts.add(document)
        recent.value = upserts.toList()
    }

    override suspend fun insertDocument(document: DocumentEntity): Long {
        check(upserts.none { it.id == document.id || it.contentSha256 == document.contentSha256 })
        upserts.add(document)
        recent.value = upserts.toList()
        return upserts.size.toLong()
    }

    override suspend fun updateDocument(document: DocumentEntity) {
        val index = upserts.indexOfFirst { it.id == document.id }
        if (index >= 0) upserts[index] = document
        recent.value = upserts.toList()
    }

    override suspend fun assignContentSha256IfNull(
        id: String,
        contentSha256: String,
        lastOpenedAt: Long,
    ): Int {
        val index = upserts.indexOfFirst { it.id == id && it.contentSha256 == null }
        if (index < 0) return 0
        upserts[index] = upserts[index].copy(
            contentSha256 = contentSha256,
            lastOpenedAt = lastOpenedAt,
        )
        recent.value = upserts.toList()
        return 1
    }

    override suspend fun deleteById(id: String) {
        upserts.removeAll { it.id == id }
        recent.value = upserts.toList()
    }

    override suspend fun updateLastOpenedAt(id: String, time: Long) {
        upserts.find { it.id == id }?.let {
            upserts[upserts.indexOf(it)] = it.copy(lastOpenedAt = time)
        }
    }

    override suspend fun updateParseMetadata(id: String, title: String?, headingCount: Int) {
        upserts.find { it.id == id }?.let {
            upserts[upserts.indexOf(it)] = it.copy(title = title, headingCount = headingCount)
        }
    }

    override suspend fun updateReadingPosition(
        id: String,
        lastScrollIndex: Int,
        lastScrollOffset: Int,
        readingProgress: Float,
    ) {
        upserts.find { it.id == id }?.let {
            upserts[upserts.indexOf(it)] = it.copy(
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
        readingAnchor: String?,
    ) = Unit
    override suspend fun updatePdfReadingPosition(
        id: String,
        pageIndex: Int,
        intraPageFractionEncoded: Int,
        pageCount: Int,
        readingProgress: Float,
    ) {
        upserts.find { it.id == id }?.let {
            upserts[upserts.indexOf(it)] = it.copy(
                pdfViewportPageIndex = pageIndex,
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
        // not exercised by these tests
    }

    override suspend fun deleteAll() {
        upserts.clear()
        recent.value = emptyList()
    }
}

private class UnusedPdfPrintPageAnchorDao : PdfPrintPageAnchorDao {
    override suspend fun listForDocument(documentId: String): List<PdfPrintPageAnchorEntity> =
        error("unused in ImportAcceptanceParityGoldenTest")

    override suspend fun upsert(anchor: PdfPrintPageAnchorEntity) =
        error("unused in ImportAcceptanceParityGoldenTest")

    override suspend fun delete(documentId: String, pdfPageIndex: Int) =
        error("unused in ImportAcceptanceParityGoldenTest")

    override suspend fun deleteAllForDocument(documentId: String) =
        error("unused in ImportAcceptanceParityGoldenTest")
}

private class UnusedTextbookOutlineNodeDao : TextbookOutlineNodeDao {
    override suspend fun listForDocument(documentId: String): List<TextbookOutlineNodeEntity> =
        error("unused in ImportAcceptanceParityGoldenTest")

    override suspend fun getById(id: String): TextbookOutlineNodeEntity? =
        error("unused in ImportAcceptanceParityGoldenTest")

    override suspend fun upsert(node: TextbookOutlineNodeEntity) =
        error("unused in ImportAcceptanceParityGoldenTest")

    override suspend fun update(node: TextbookOutlineNodeEntity) =
        error("unused in ImportAcceptanceParityGoldenTest")

    override suspend fun deleteById(id: String) =
        error("unused in ImportAcceptanceParityGoldenTest")

    override suspend fun deleteAllForDocument(documentId: String) =
        error("unused in ImportAcceptanceParityGoldenTest")

    override suspend fun listChildren(documentId: String, parentId: String): List<TextbookOutlineNodeEntity> =
        error("unused in ImportAcceptanceParityGoldenTest")

    override suspend fun listRoots(documentId: String): List<TextbookOutlineNodeEntity> =
        error("unused in ImportAcceptanceParityGoldenTest")
}
