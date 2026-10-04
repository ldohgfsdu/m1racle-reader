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
import com.m1racle.reader.data.local.PdfPrintPageAnchorDao
import com.m1racle.reader.data.local.PdfPrintPageAnchorEntity
import com.m1racle.reader.data.local.TextbookOutlineNodeDao
import com.m1racle.reader.data.local.TextbookOutlineNodeEntity
import com.m1racle.reader.data.repository.DocumentRepository
import com.m1racle.reader.data.repository.ImportResult
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

/**
 * Import policy batch 3: the 200 MB source-import limit applies to EVERY
 * intake entry. Both entries share [ExternalDocumentImporter.MAX_BYTES] as the
 * single boundary constant; zip EXTRACTION caps (entry count / total / single)
 * are separate limits and stay untouched inside ZipPackageImporter.
 *
 * Required boundaries (identical verdicts on both paths):
 *   199 MiB        → accepted
 *   200 MiB exact  → accepted (cap is inclusive)
 *   200 MiB + 1    → rejected (TooLarge / SourceTooLarge)
 *   no size hint   → rejection happens on the STREAM side (copy loop on Path B,
 *                    bounded drain on Path A), fed here by a zero-allocation
 *                    fake stream of MAX+1 logical bytes.
 *
 * Error-type unification: Path B surfaces PrepareOutcome.TooLarge, Path A
 * surfaces ImportResult.SourceTooLarge — distinct layer types, one semantic,
 * one shared constant. UI presentation is intentionally frozen (constraint).
 *
 * No Room query ever runs; DAOs are fakes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class ImportSizeLimitBoundaryTest {

    private lateinit var context: android.content.Context
    private lateinit var resolver: android.content.ContentResolver
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        resolver = context.contentResolver
        tempDir = context.cacheDir.resolve("b3-${System.nanoTime()}").apply { mkdirs() }
        // Companion state is shared by Robolectric's sandbox across test cases.
        // Each boundary must start with a readable descriptor and plain-text MIME.
        StubContentProvider.row = null
        StubContentProvider.type = "text/plain"
        StubContentProvider.serveDescriptors = true
        StubContentProvider.backingDir = tempDir
    }

    /** Yields [totalBytes] logical bytes in 64 KiB chunks — nothing allocated. */
    private class FakeSizedStream(totalBytes: Long) : FilterInputStream(NullStream()) {
        private var remaining: Long = totalBytes
        override fun read(): Int = if (read(ByteArray(1)) < 0) -1 else 0

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0) return -1
            val n = minOf(len.toLong(), remaining, 64L * 1024L).toInt()
            remaining -= n
            return n
        }

        private class NullStream : InputStream() {
            override fun read(): Int = -1
        }
    }

    private class StubContentProvider : ContentProvider() {
        companion object {
            var row: List<Any?>? = null
            var type: String? = "text/plain"
            var serveDescriptors: Boolean = true
            var backingDir: File? = null
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

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            if (!serveDescriptors) throw java.io.FileNotFoundException("descriptors disabled")
            val f = File.createTempFile("b3backing", ".bin", backingDir).apply { writeBytes(ByteArray(8)) }
            return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
        }
    }

    private fun stubUri(authority: String): Uri = Uri.parse("content://$authority/root/doc")

    private fun registerStub(authority: String) {
        StubContentProvider.backingDir = tempDir
        Robolectric.setupContentProvider(StubContentProvider::class.java, authority)
    }

    private fun installRepository(): DocumentRepository {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        return DocumentRepository(
            context = context,
            db = db,
            documentDao = B3RecordingDocumentDao(),
            pdfPrintPageAnchorDao = B3UnusedPdfPrintPageAnchorDao(),
            textbookOutlineNodeDao = B3UnusedTextbookOutlineNodeDao(),
            searchRepository = com.m1racle.reader.testing.testSearchRepository(),
            documentAliasDao = com.m1racle.reader.testing.TestDocumentAliasDao(),
            importTransactionRunner = com.m1racle.reader.testing.ImmediateRepositoryTransactionRunner(),
        )
    }

    private fun importerWithFakeStream(
        repository: DocumentRepository,
        totalBytes: Long,
    ): ExternalDocumentImporter =
        ExternalDocumentImporter(
            context = context,
            repository = repository,
            openStreamOverride = { _ -> FakeSizedStream(totalBytes) },
        )

    private fun cap(): Long = ExternalDocumentImporter.MAX_BYTES

    // ── hint-present boundaries ──────────────────────────────────────────────

    @Test
    fun hint_199MiB_acceptedByBothPaths() = runBlocking {
        registerStub("b199")
        StubContentProvider.row = listOf("big.md", 199L * 1024L * 1024L)
        val uri = stubUri("b199")

        val pathA = installRepository().importDocument(uri)
        assertEquals(8L, (pathA as ImportResult.Success).document.fileSize)

        val pathB = importerWithFakeStream(installRepository(), totalBytes = 64L * 1024L)
            .prepare(uri, intentType = null)
        assertTrue(pathB is PrepareOutcome.Success)
    }

    @Test
    fun hint_exactlyCap_acceptedByBothPaths_capIsInclusive() = runBlocking {
        registerStub("b200")
        StubContentProvider.row = listOf("big.md", cap())
        val uri = stubUri("b200")

        val pathA = installRepository().importDocument(uri)
        assertEquals(8L, (pathA as ImportResult.Success).document.fileSize)

        val pathB = importerWithFakeStream(installRepository(), totalBytes = 64L * 1024L)
            .prepare(uri, intentType = null)
        assertTrue(pathB is PrepareOutcome.Success)
    }

    @Test
    fun hint_capPlusOne_rejectedByBothPaths() = runBlocking {
        registerStub("b201")
        StubContentProvider.row = listOf("big.md", cap() + 1L)
        val uri = stubUri("b201")

        val pathA = installRepository().importDocument(uri)
        assertEquals(ImportResult.SourceTooLarge, pathA)

        val pathB = importerWithFakeStream(installRepository(), totalBytes = 64L * 1024L)
            .prepare(uri, intentType = null)
        assertEquals(PrepareOutcome.TooLarge, pathB)
    }

    // ── no-hint boundaries: enforcement moves to the stream side ────────────

    @Test
    fun noHint_streamBeyondCap_rejectedOnBothPaths_viaCopyAndDrain() = runBlocking {
        registerStub("bn1")
        StubContentProvider.row = listOf("big.md", null) // provider hides SIZE…
        StubContentProvider.serveDescriptors = false // …and exposes no AFD/PFD hint
        val uri = stubUri("bn1")
        // Robolectric: feed the oversized logical stream to Path A's reader.
        shadowOf(resolver).registerInputStream(uri, FakeSizedStream(cap() + 1L))

        val pathA = installRepository().importDocument(uri)
        assertEquals(ImportResult.SourceTooLarge, pathA)

        // Path B: prepare's copy loop hits the same wall through its own stream.
        val pathB = importerWithFakeStream(installRepository(), totalBytes = cap() + 1L)
            .prepare(uri, intentType = null)
        assertEquals(PrepareOutcome.TooLarge, pathB)
    }

    @Test
    fun noHint_streamWithinCap_acceptedOnBothPaths() = runBlocking {
        registerStub("bn2")
        // Test the generic 200 MiB envelope with PDF. Markdown has a stricter
        // 32 MiB readable-text limit, covered independently below.
        StubContentProvider.row = listOf("big.pdf", null)
        StubContentProvider.type = "application/pdf"
        StubContentProvider.serveDescriptors = false
        val uri = stubUri("bn2")
        shadowOf(resolver).registerInputStream(uri, FakeSizedStream(cap())) // exact cap drains clean

        val pathA = installRepository().importDocument(uri)
        assertTrue("Path A accepts a cap-sized hint-less stream", pathA is ImportResult.Success)

        val importer = importerWithFakeStream(installRepository(), totalBytes = cap())
        val pathB = importer.prepare(uri, intentType = null)
        assertTrue(pathB is PrepareOutcome.Success)
        importer.discardPrepared((pathB as PrepareOutcome.Success).prepared)
    }

    @Test
    fun markdownAboveReadableLimitIsRejectedAtCommit() = runBlocking {
        registerStub("md-limit")
        StubContentProvider.row = listOf("big.md", null)
        StubContentProvider.type = "text/plain"
        StubContentProvider.serveDescriptors = false
        val uri = stubUri("md-limit")
        shadowOf(resolver).registerInputStream(uri,
            FakeSizedStream(DocumentRepository.MAX_MARKDOWN_BYTES.toLong() + 1))
        assertEquals(ImportResult.UnsupportedFormat, installRepository().importDocument(uri))
        val importer = importerWithFakeStream(installRepository(), DocumentRepository.MAX_MARKDOWN_BYTES.toLong() + 1)
        val prepared = importer.prepare(uri, intentType = null) as PrepareOutcome.Success
        assertTrue(importer.commitPrepared(prepared.prepared) is com.m1racle.reader.share.CommitOutcome.Failed)
    }

    // ── shared constant sanity ───────────────────────────────────────────────

    @Test
    fun boundaryConstant_isExactly200MiB() {
        assertEquals(200L * 1024L * 1024L, ExternalDocumentImporter.MAX_BYTES)
    }
}

/** Records upserts; everything else mirrors an empty library. */
private class B3RecordingDocumentDao : DocumentDao {
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
        upserts.find { it.id == id }?.let { upserts[upserts.indexOf(it)] = it.copy(lastOpenedAt = time) }
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
            upserts[upserts.indexOf(it)] = it.copy(pageCount = pageCount, readingProgress = readingProgress)
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
    ) = Unit

    override suspend fun deleteAll() {
        upserts.clear()
        recent.value = emptyList()
    }
}

private class B3UnusedPdfPrintPageAnchorDao : PdfPrintPageAnchorDao {
    override suspend fun listForDocument(documentId: String): List<PdfPrintPageAnchorEntity> =
        error("unused in ImportSizeLimitBoundaryTest")

    override suspend fun upsert(anchor: PdfPrintPageAnchorEntity) =
        error("unused in ImportSizeLimitBoundaryTest")

    override suspend fun delete(documentId: String, pdfPageIndex: Int) =
        error("unused in ImportSizeLimitBoundaryTest")

    override suspend fun deleteAllForDocument(documentId: String) =
        error("unused in ImportSizeLimitBoundaryTest")
}

private class B3UnusedTextbookOutlineNodeDao : TextbookOutlineNodeDao {
    override suspend fun listForDocument(documentId: String): List<TextbookOutlineNodeEntity> =
        error("unused in ImportSizeLimitBoundaryTest")

    override suspend fun getById(id: String): TextbookOutlineNodeEntity? =
        error("unused in ImportSizeLimitBoundaryTest")

    override suspend fun upsert(node: TextbookOutlineNodeEntity) =
        error("unused in ImportSizeLimitBoundaryTest")

    override suspend fun update(node: TextbookOutlineNodeEntity) =
        error("unused in ImportSizeLimitBoundaryTest")

    override suspend fun deleteById(id: String) =
        error("unused in ImportSizeLimitBoundaryTest")

    override suspend fun deleteAllForDocument(documentId: String) =
        error("unused in ImportSizeLimitBoundaryTest")

    override suspend fun listChildren(documentId: String, parentId: String): List<TextbookOutlineNodeEntity> =
        error("unused in ImportSizeLimitBoundaryTest")

    override suspend fun listRoots(documentId: String): List<TextbookOutlineNodeEntity> =
        error("unused in ImportSizeLimitBoundaryTest")
}
