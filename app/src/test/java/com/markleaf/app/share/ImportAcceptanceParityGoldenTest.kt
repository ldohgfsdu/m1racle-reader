package com.markleaf.app.share

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.markleaf.app.data.local.AppDatabase
import com.markleaf.app.data.local.DocumentDao
import com.markleaf.app.data.local.DocumentEntity
import com.markleaf.app.data.local.DocumentType
import com.markleaf.app.data.local.PdfPrintPageAnchorDao
import com.markleaf.app.data.local.PdfPrintPageAnchorEntity
import com.markleaf.app.data.local.TextbookOutlineNodeDao
import com.markleaf.app.data.local.TextbookOutlineNodeEntity
import com.markleaf.app.data.repository.DocumentRepository
import com.markleaf.app.data.repository.ImportResult
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
 * Unified semantics over the raw-name matrix below:
 *   - document extensions (.md/.markdown/.txt/.zip/.pdf, case-insensitive)
 *     accepted under every mime;
 *   - text-family mime + no extension accepted as plain text;
 *   - text-family mime + obviously non-document extension REJECTED (the old
 *     Path-B-only mojibake hole is closed);
 *   - everything else rejected identically on both paths.
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
        // Extension-driven acceptance ignores mime entirely on both paths today.
        assertRow(
            "doc.pdf",
            expectedA = "YYYYYYYYYYYYY",
            expectedB = "YYYYYYYYYYYYY",
        )
        assertRow(
            "DOC.PDF", // case-insensitive extension match
            expectedA = "YYYYYYYYYYYYY",
            expectedB = "YYYYYYYYYYYYY",
        )
        assertRow(
            "note.md",
            expectedA = "YYYYYYYYYYYYY",
            expectedB = "YYYYYYYYYYYYY",
        )
        assertRow(
            "pack.zip",
            expectedA = "YYYYYYYYYYYYY",
            expectedB = "YYYYYYYYYYYYY",
        )
    }

    @Test
    fun golden_extensionlessName_textFamilyAcceptedByFallback_bothPathsIdentical() {
        // Unified fallback: ANY text-family mime accepts extensionless names as
        // plain text on BOTH paths (literal text/* reading — includes text/html).
        // Index: 0-4 pdf/xpdf/zip/zxc/zx = Y; 5-7 tmk/txm/txp = Y (fallback);
        // 8 octet-stream = N (lost whitelist carve-out); 9 jpeg mime = N;
        // 10 TEXT/HTML = Y (fallback); 11 msword = N; 12 null = N.
        assertRow(
            "README",
            expectedA = "YYYYYYYYNNYNN",
            expectedB = "YYYYYYYYNNYNN",
        )
    }

    @Test
    fun golden_wrongExtensionName_rejectedOnBothPaths() {
        // The old Path-B-only mojibake hole is closed: a text-family mime with
        // an obviously non-document extension is rejected by the single authority.
        assertRow(
            "photo.jpg",
            expectedA = "YYYYYNNNNNNNN",
            expectedB = "YYYYYNNNNNNNN",
        )
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
        val extensions = listOf(
            "md", "markdown", "txt", "zip", "pdf", // canonical
            "MD", "PDF", "Zip", "mD", "TxT", // case variants
        )
        val prefixes = listOf("a", "教材", "my.file", "sp ace")
        val positives = prefixes.flatMap { p -> extensions.map { e -> "$p.$e" } } +
            listOf(
                "a.tar.md", // compound, still endsWith .md
                "教材第二版.TXT",
            )
        val allMimes: List<String?> = mimes
        for (name in positives) {
            for (mime in allMimes) {
                assertTrue(
                    "isAllowedImport($mime,'$name','$name')",
                    DocumentType.isAllowedImport(mime, name, name),
                )
            }
        }
    }

    @Test
    fun authority_textFallback_matrix() {
        // text/* + no extension → accepted as plain text.
        assertTrue(DocumentType.isAllowedImport("text/plain", "README", "README"))
        assertTrue(DocumentType.isAllowedImport("text/markdown", "notes123", "notes123"))
        // Literal text/* reading: every text-family mime qualifies.
        assertTrue(DocumentType.isAllowedImport("text/html", "page1", "page1"))
        assertTrue(DocumentType.isAllowedImport("text/whatever-custom", "x", "x"))

        // text/* + document extension → accepted by the primary branch.
        assertTrue(DocumentType.isAllowedImport("text/plain", "a.md", null))

        // text/* + obviously non-document extension → rejected (mojibake guard).
        assertFalse(DocumentType.isAllowedImport("text/plain", "photo.jpg", "doc"))
        assertFalse(DocumentType.isAllowedImport("text/markdown", "clip.png", null))
        assertFalse(DocumentType.isAllowedImport("text/html", "movie.mp4", "doc"))
        assertFalse(DocumentType.isAllowedImport("text/plain", "教材.docx", null))
        // Denylist is case-insensitive and uses the LAST extension only.
        assertFalse(DocumentType.isAllowedImport("text/plain", "A.JPG", null))
        assertFalse(DocumentType.isAllowedImport("text/plain", "a.tar.gz", null))
        assertTrue(DocumentType.isAllowedImport("text/plain", "a.jpg.md", null)) // .md wins

        // Non-text mimes never reach the fallback.
        assertFalse(DocumentType.isAllowedImport("application/octet-stream", "mystery", "mystery"))
        assertFalse(DocumentType.isAllowedImport("image/jpeg", "x.png", null))
        assertFalse(DocumentType.isAllowedImport(null, "README2", "README2"))
    }

    @Test
    fun authority_controlGroup_denylistClosesMojibakeHole_unknownExtensionsEnterAsText() {
        // 1) Obviously-non-document extensions are rejected even with a text mime.
        val denylisted = listOf(
            "a.jpg", "photo.jpeg", "教材.docx", "A.JPG", "clip.mp4",
            "a.tar.gz", // last extension wins
        )
        for (name in denylisted) {
            assertFalse(
                "isAllowedImport must reject '$name' under text/plain",
                DocumentType.isAllowedImport("text/plain", name, name),
            )
        }

        // 2) Unknown extensions are NOT obviously non-document — the denylist is
        // deliberately closed, so they enter as readable text (e.g. .mdown is a
        // historic markdown alias).
        assertTrue(DocumentType.isAllowedImport("text/plain", "a.mdown", "a.mdown"))
        assertTrue(DocumentType.isAllowedImport("text/markdown", "notes.zipx", "notes.zipx"))

        // 3) Without any text mime, extensionless/unknown names stay rejected.
        for (name in listOf("a.mdown", "README2", "教材（第二版）")) {
            assertFalse(
                "isAllowedImport must reject '$name' (no mime)",
                DocumentType.isAllowedImport(null, name, name),
            )
        }
        // Blank names classify as extensionless at gate level; entry-level
        // resolve guarantees a usable name, so they are unreachable in practice.
        // A bare-dot hidden file named exactly ".md" DOES end with ".md" —
        // accepted on both paths; pinned as current behavior, not a bug.
        assertTrue(DocumentType.isAllowedImport("text/plain", ".md", ".md"))
        // Denylist helper edges: extension-only dotfiles classify by their ext;
        // blank/null and stem-less trailing dots do not count as extensions.
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

    /**
     * D1 correction (executable): extensionless text/plain content resolves to
     * "<segment>.txt" on BOTH entries before gating — both accept. The gate-row
     * divergence for raw "README"-style names never survives the shared resolve
     * layer, so a unification batch must NOT expect an A/B flip here.
     */
    @Test
    fun d1_bothEntries_acceptExtensionlessTextViaSharedNameNormalization() = runBlocking {
        registerStub("d1a")
        StubContentProvider.row = null // no DISPLAY_NAME/SIZE columns
        StubContentProvider.type = "text/plain"
        val uri = stubUri("d1a", "notes123")

        val pathA = installRepository().importDocument(uri)
        val imported = (pathA as ImportResult.Success).document
        assertEquals("notes123.txt", imported.displayName)
        assertEquals("MARKDOWN", imported.documentType)

        val pathB = importerWithTinyStream(installRepository())
            .prepare(uri, intentType = null)
        val preparedB = (pathB as PrepareOutcome.Success).prepared as PreparedOpen.NewContent
        assertEquals("notes123.txt.importing", preparedB.stagedFile.name)
        assertEquals("MARKDOWN", preparedB.documentTypeName)
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

    /** D3 pair: >200 MB source over a .md name — unified in policy batch 3. */
    @Test
    fun d3_pathA_rejectsHugeSourceAfterBatch3() = runBlocking {
        registerStub("d3a")
        val huge = 300L * 1024L * 1024L
        StubContentProvider.row = listOf("big.md", huge)
        StubContentProvider.type = "text/plain"
        val repo = installRepository()

        // Pre-batch-3 Path A imported this without any cap; the unified source
        // limit now rejects it exactly like the VIEW/SEND entry.
        val result = repo.importDocument(stubUri("d3a", "doc"))

        assertEquals(ImportResult.SourceTooLarge, result)
        assertEquals(0, fakeDao.upserts.size)
    }

    @Test
    fun d3_pathB_rejectsHugeSourceWithTooLargeToday() = runBlocking {
        registerStub("d3b")
        val huge = 300L * 1024L * 1024L
        StubContentProvider.row = listOf("big.md", huge)
        StubContentProvider.type = "text/plain"
        val repo = installRepository()

        val outcome = importerWithTinyStream(repo)
            .prepare(stubUri("d3b", "doc"), intentType = null)

        assertEquals(PrepareOutcome.TooLarge, outcome)
    }

    @Test
    fun d3_pathB_boundary_exactlyMaxBytesPassesHintStageAndCopies() = runBlocking {
        registerStub("d3c")
        StubContentProvider.row =
            listOf("big.md", ExternalDocumentImporter.MAX_BYTES) // == cap, not >
        StubContentProvider.type = "text/plain"
        val repo = installRepository()

        val outcome = importerWithTinyStream(repo)
            .prepare(stubUri("d3c", "doc"), intentType = null)

        val prepared = (outcome as PrepareOutcome.Success).prepared as PreparedOpen.NewContent
        assertTrue(prepared.stagedFile.isFile)
        // Copied byte count wins as final size authority (resolveFinalSizeBytes).
        assertEquals("# tiny".toByteArray().size.toLong(), prepared.sizeBytes)
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
