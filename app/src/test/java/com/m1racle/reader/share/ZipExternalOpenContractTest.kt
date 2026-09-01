package com.m1racle.reader.share

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
 * Batch B-0 contract: a zip delivered over VIEW/SEND must enter the library as
 * a MARKDOWN_PACKAGE whose uri points at the extracted entry markdown — exactly
 * like the FAB/SAF zip path — and must NEVER be committed as a plain MARKDOWN
 * row pointing at the binary archive.
 *
 * The manifest advertises application/zip (+x-zip-compressed) for VIEW and
 * SEND; pre-B-0 runtime committed `documentType=MARKDOWN, uri=…zip`, which the
 * navigation graph then routes into the markdown text reader.
 *
 * Regression guards pin the untouched neighbours: pdf/txt via VIEW stay PDF /
 * MARKDOWN; the FAB zip route keeps producing MARKDOWN_PACKAGE; a corrupt zip
 * fails cleanly with no extracted-package or staged-copy residue.
 *
 * No Room query ever runs; DAOs are fakes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class ZipExternalOpenContractTest {

    private lateinit var context: android.content.Context
    private lateinit var resolver: android.content.ContentResolver

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        resolver = context.contentResolver
    }

    // ── fixture helpers ──────────────────────────────────────────────────────

    /** Minimal but real textbook zip: one entry markdown + one asset. */
    private fun textbookZipBytes(mdBody: String = "# 包标题\n\n正文"): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("教材.md"))
            zip.write(mdBody.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("assets/pic.txt"))
            zip.write("asset".toByteArray())
            zip.closeEntry()
        }
        return bytes.toByteArray()
    }

    private class StubContentProvider : ContentProvider() {
        companion object {
            var displayName: String? = null
            var mimeType: String? = null
        }

        override fun onCreate() = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            args: Array<out String>?,
            sort: String?,
        ): Cursor = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
            displayName?.let { addRow(listOf(it, 64L)) }
        }

        override fun getType(uri: Uri): String? = mimeType
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = 0
        override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = 0
    }

    private fun stubZipUri(authority: String, name: String): Uri =
        Uri.parse("content://$authority/root/$name")

    private fun registerStub(authority: String, name: String, mime: String) {
        StubContentProvider.displayName = name
        StubContentProvider.mimeType = mime
        Robolectric.setupContentProvider(StubContentProvider::class.java, authority)
    }

    private fun installRepository(): Pair<DocumentRepository, ZbRecordingDocumentDao> {
        val dao = ZbRecordingDocumentDao()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val repo = DocumentRepository(
            context = context,
            db = db,
            documentDao = dao,
            pdfPrintPageAnchorDao = ZbUnusedPdfPrintPageAnchorDao(),
            textbookOutlineNodeDao = ZbUnusedTextbookOutlineNodeDao(),
            searchRepository = com.m1racle.reader.testing.testSearchRepository(),
            documentAliasDao = com.m1racle.reader.testing.TestDocumentAliasDao(),
            importTransactionRunner = com.m1racle.reader.testing.ImmediateImportTransactionRunner(),
        )
        return repo to dao
    }

    private fun zipImporter(
        repository: DocumentRepository,
        payload: ByteArray,
    ): ExternalDocumentImporter =
        ExternalDocumentImporter(
            context = context,
            repository = repository,
            openStreamOverride = { _ -> ByteArrayInputStream(payload) },
        )

    /** prepare + commit; returns commit outcome (mirrors the coordinator handoff). */
    private fun importViaPathB(
        repository: DocumentRepository,
        payload: ByteArray,
        uri: Uri,
        mime: String,
    ): CommitOutcome = runBlocking {
        val importer = zipImporter(repository, payload)
        val prep = importer.prepare(uri, mime)
        val prepared = (prep as PrepareOutcome.Success).prepared
        importer.commitPrepared(prepared)
    }

    // ── RED baseline: zip over VIEW/SEND ─────────────────────────────────────

    @Test
    fun zipViewIntent_commitsAsMarkdownPackage_pointingAtExtractedEntryMd() = runBlocking {
        registerStub("zb1", "甲教材.zip", "application/zip")
        val repo = installRepository()

        val commit = importViaPathB(
            repo.first,
            textbookZipBytes(),
            stubZipUri("zb1", "doc"),
            "application/zip",
        )
        assertTrue("commit failed: $commit", commit is CommitOutcome.Success)

        val row = daoRow(repo.second)
        assertEquals(DocumentType.MARKDOWN_PACKAGE.name, row.documentType)
        assertFalse(
            "uri must point at extracted entry md, not the binary archive",
            row.uri.endsWith(".zip"),
        )
        assertTrue(row.uri.endsWith(".md"))
        assertEquals("甲教材", row.displayName)
    }

    @Test
    fun zipSendIntent_neverCommitsAsPlainMarkdown() = runBlocking {
        registerStub("zb2", "乙讲义.zip", "application/x-zip-compressed")
        val (repo, dao) = installRepository()

        val commit = importViaPathB(
            repo,
            textbookZipBytes(),
            stubZipUri("zb2", "doc"),
            "application/x-zip-compressed",
        )
        assertTrue(commit is CommitOutcome.Success)

        val row = daoRow(dao)
        assertNotEquals(DocumentType.MARKDOWN.name, row.documentType)
        assertEquals(DocumentType.MARKDOWN_PACKAGE.name, row.documentType)
        // Entry body must be readable text at the recorded uri (markdown reader opens it).
        val entryText = File(Uri.parse(row.uri).path!!).readText(Charsets.UTF_8)
        assertTrue(entryText.contains("包标题"))
    }

    @Test
    fun corruptZip_failsCleanly_withoutResidue() = runBlocking {
        registerStub("zb3", "坏包.zip", "application/zip")
        val (repo, dao) = installRepository()

        val garbage = "definitely not a zip".toByteArray()
        val commit = importViaPathB(repo, garbage, stubZipUri("zb3", "doc"), "application/zip")

        assertTrue(commit is CommitOutcome.Failed)
        assertTrue(dao.upserts.isEmpty())
        // No extracted package tree may survive a failed intake.
        assertEquals(0, File(context.filesDir, "packages").listFiles()?.size ?: 0)
        // And no resolver-owned .importing source lingers.
        val staging = File(context.filesDir, "identity-staging")
        val leftovers = staging.walkTopDown().filter { it.isFile }.toList()
        assertTrue("staged zip leaked: $leftovers", leftovers.isEmpty())
    }

    // ── regression guards (green before AND after B-0) ───────────────────────

    @Test
    fun pdfViewIntent_staysPdf() = runBlocking {
        registerStub("zbp", "样张.pdf", "application/pdf")
        val (repo, dao) = installRepository()

        val minimalPdf = "%PDF-1.4\n%%EOF".toByteArray()
        val commit = importViaPathB(repo, minimalPdf, stubZipUri("zbp", "doc"), "application/pdf")

        assertTrue(commit is CommitOutcome.Success)
        val row = daoRow(dao)
        assertEquals(DocumentType.PDF.name, row.documentType)
        assertTrue(row.uri.endsWith(".pdf"))
    }

    @Test
    fun txtViewIntent_staysMarkdown() = runBlocking {
        registerStub("zbt", "笔记.txt", "text/plain")
        val (repo, dao) = installRepository()

        val commit = importViaPathB(
            repo,
            "# 笔记\n正文".toByteArray(),
            stubZipUri("zbt", "doc"),
            "text/plain",
        )

        assertTrue(commit is CommitOutcome.Success)
        val row = daoRow(dao)
        assertEquals(DocumentType.MARKDOWN.name, row.documentType)
        assertTrue(row.uri.endsWith(".txt"))
    }

    @Test
    fun fabZipRoute_stillProducesMarkdownPackage() = runBlocking {
        registerStub("zbf", "丙包.zip", "application/zip")
        val (repo, _) = installRepository()
        val uri = stubZipUri("zbf", "doc")
        shadowOf(resolver).registerInputStream(uri, ByteArrayInputStream(textbookZipBytes()))

        val result = repo.importDocument(uri)

        val imported = (result as ImportResult.Success).document
        assertEquals(DocumentType.MARKDOWN_PACKAGE.name, imported.documentType)
        assertTrue(imported.uri.endsWith(".md"))
    }
}

private fun daoRow(dao: ZbRecordingDocumentDao): DocumentEntity =
    requireNotNull(dao.upserts.firstOrNull()) { "no document row committed" }

/** Records upserts; everything else mirrors an empty library. */
private class ZbRecordingDocumentDao : DocumentDao {
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

private class ZbUnusedPdfPrintPageAnchorDao : PdfPrintPageAnchorDao {
    override suspend fun listForDocument(documentId: String): List<PdfPrintPageAnchorEntity> =
        error("unused in ZipExternalOpenContractTest")

    override suspend fun upsert(anchor: PdfPrintPageAnchorEntity) =
        error("unused in ZipExternalOpenContractTest")

    override suspend fun delete(documentId: String, pdfPageIndex: Int) =
        error("unused in ZipExternalOpenContractTest")

    override suspend fun deleteAllForDocument(documentId: String) =
        error("unused in ZipExternalOpenContractTest")
}

private class ZbUnusedTextbookOutlineNodeDao : TextbookOutlineNodeDao {
    override suspend fun listForDocument(documentId: String): List<TextbookOutlineNodeEntity> =
        error("unused in ZipExternalOpenContractTest")

    override suspend fun getById(id: String): TextbookOutlineNodeEntity? =
        error("unused in ZipExternalOpenContractTest")

    override suspend fun upsert(node: TextbookOutlineNodeEntity) =
        error("unused in ZipExternalOpenContractTest")

    override suspend fun update(node: TextbookOutlineNodeEntity) =
        error("unused in ZipExternalOpenContractTest")

    override suspend fun deleteById(id: String) =
        error("unused in ZipExternalOpenContractTest")

    override suspend fun deleteAllForDocument(documentId: String) =
        error("unused in ZipExternalOpenContractTest")

    override suspend fun listChildren(documentId: String, parentId: String): List<TextbookOutlineNodeEntity> =
        error("unused in ZipExternalOpenContractTest")

    override suspend fun listRoots(documentId: String): List<TextbookOutlineNodeEntity> =
        error("unused in ZipExternalOpenContractTest")
}
