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
 * T0 behavior baseline for the import acceptance policy split (policy decision
 * batch 1 — tests only; production policy untouched on this HEAD).
 *
 * Pins TODAY'S verdicts of the two live import entries so the later unification
 * batch changes exactly what was decided and nothing else:
 *
 * - Path A = FAB/SAF entry: `DocumentRepository.importDocument` gates with
 *   `DocumentType.isAllowedImport(mime,dn,ps) || isAllowedFileName(dn) || isAllowedFileName(ps)`
 *   (extension-driven; NO mime whitelist; NO source size cap).
 * - Path B = external VIEW/SEND entry: `ExternalOpenCoordinator` +
 *   `ExternalDocumentImporter.prepare` gate with
 *   `ExternalFileIntake.isAllowed` = isAllowedImport ∨ ALLOWED_MIMES whitelist,
 *   plus a 200 MB source-size cap in prepare.
 *
 * Gate-level divergences pinned here (raw-name inputs; note that both real
 * entries run ExternalUriMetadata.resolve BEFORE gating, whose ensureExtension
 * normalizes extensionless text names — see the E2E tests for entry truth):
 *   DG1 text-family mime + no extension  → gate B accepts, gate A rejects
 *   DG2 text/plain + wrong extension     → B accepts, A rejects
 *
 * ENTRY-LEVEL divergences proven by the E2E pairs below (the ones a policy
 * unification actually changes):
 *   D2 text/plain + wrong extension ("photo.jpg") → A rejects / B imports a
 *       MARKDOWN-typed mojibake document
 *   D3 source size: >200MB over ".md" → A imports (no cap) / B TooLarge
 *
 * NOT an entry-level divergence despite the gate rows: extensionless
 * text/plain content resolves to "<segment>.txt" on BOTH paths before the
 * gate, so both accept it. The earlier review's D1 was a gate-abstract
 * artifact; pinned here as an executable correction.
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
     * Gate-level Path A verdict: the exact disjunction inside
     * DocumentRepository.importDocument (DocumentType.isAllowedImport OR the two
     * isAllowedFileName branches). Uses production functions only.
     */
    private fun pathA(mime: String?, name: String): Boolean =
        DocumentType.isAllowedImport(mime, name, name) ||
            DocumentRepository.isAllowedFileName(name)

    /** Gate-level Path B verdict: ExternalFileIntake.isAllowed verbatim. */
    private fun pathB(mime: String?, name: String): Boolean =
        ExternalFileIntake.isAllowed(mime, name, name)

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
    fun golden_extensionlessName_whitelistMimesDiverge() {
        // D1: text/markdown|x-markdown|plain pass the whitelist without any
        // document extension → Path B accepts where Path A rejects. octet-stream
        // requires a known extension → both reject. pdf/zip mimes accept both.
        assertRow(
            "README",
            expectedA = "YYYYYNNNNNNNN",
            expectedB = "YYYYYYYYNNNNN",
        )
    }

    @Test
    fun golden_wrongExtensionName_unconditionalTextWhitelistDiverges() {
        // D2: the text-family whitelist branches are unconditional — even an image
        // extension passes Path B while Path A rejects.
        assertRow(
            "photo.jpg",
            expectedA = "YYYYYNNNNNNNN",
            expectedB = "YYYYYYYYNNNNN",
        )
    }

    @Test
    fun golden_diffCells_areExactlyTheSixKnownPolicyDriftCells() {
        val diffCells = mutableListOf<String>()
        for (name in listOf("doc.pdf", "note.md", "pack.zip", "README", "photo.jpg", "DOC.PDF")) {
            for (mime in mimes) {
                if (pathA(mime, name) != pathB(mime, name)) {
                    diffCells += "$mime|$name"
                }
            }
        }
        assertEquals(
            listOf(
                "text/markdown|README",
                "text/markdown|photo.jpg",
                "text/plain|README",
                "text/plain|photo.jpg",
                "text/x-markdown|README",
                "text/x-markdown|photo.jpg",
            ),
            diffCells.sorted(),
        )
    }

    // ── PART 2: isAllowedFileName ⊆ isAllowedImport proof ────────────────────

    @Test
    fun implication_everyNameAcceptedByIsAllowedFileNameIsAcceptedByIsAllowedImport_forEveryMime() {
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
            assertTrue(
                "control: isAllowedFileName must accept '$name' (guards vacuity)",
                DocumentRepository.isAllowedFileName(name),
            )
            for (mime in allMimes) {
                assertTrue(
                    "isAllowedFileName('$name') ⇒ isAllowedImport($mime,'$name','$name')",
                    DocumentType.isAllowedImport(mime, name, name),
                )
            }
        }
    }

    @Test
    fun implication_controlGroup_rejectedNamesStayRejected() {
        val negatives = listOf(
            "", " ", // blank / whitespace-only
            "a.jpg", "a.docx", "a.mdown", "a.zipx", "a.pdff", "a.tar.gz",
            "教材（第二版）", // no extension
        )
        for (name in negatives) {
            assertFalse(
                "control: isAllowedFileName must reject '$name'",
                DocumentRepository.isAllowedFileName(name),
            )
        }
        assertFalse(DocumentRepository.isAllowedFileName(null))
        // A bare-dot hidden file named exactly ".md" DOES end with ".md" —
        // accepted today on both paths; pinned as current behavior, not a bug.
        assertTrue(DocumentRepository.isAllowedFileName(".md"))
        assertTrue(DocumentType.isAllowedImport("text/plain", ".md", ".md"))
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
    fun d2_pathB_importsWrongExtensionTextAsMojibakeMarkdownToday() = runBlocking {
        registerStub("d2b")
        StubContentProvider.row = listOf("photo.jpg", 5L)
        StubContentProvider.type = "text/plain"
        val repo = installRepository()

        val outcome = importerWithTinyStream(repo)
            .prepare(stubUri("d2b", "doc"), intentType = null)

        // The unconditional text-family whitelist lets it through: a JPEG named
        // file is staged as a MARKDOWN document — the mojibake-doc defect the
        // policy batch must eliminate.
        val prepared = (outcome as PrepareOutcome.Success).prepared as PreparedOpen.NewContent
        assertEquals("photo.jpg.importing", prepared.stagedFile.name)
        assertEquals("MARKDOWN", prepared.documentTypeName)
    }

    /** D3 pair: >200 MB source over a .md name. */
    @Test
    fun d3_pathA_importsHugeSourceWithoutCapToday() = runBlocking {
        registerStub("d3a")
        val huge = 300L * 1024L * 1024L
        StubContentProvider.row = listOf("big.md", huge)
        StubContentProvider.type = "text/plain"
        val repo = installRepository()

        val result = repo.importDocument(stubUri("d3a", "doc"))

        val entity = (result as ImportResult.Success).document
        assertEquals(huge, entity.fileSize)
        assertEquals("big.md", entity.displayName)
        assertEquals(1, fakeDao.upserts.size)
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
