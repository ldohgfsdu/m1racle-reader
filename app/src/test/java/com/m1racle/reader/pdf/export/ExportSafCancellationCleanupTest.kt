package com.m1racle.reader.pdf.export

import android.Manifest
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import androidx.test.core.app.ApplicationProvider
import com.m1racle.reader.data.local.TextbookOutlineNodeEntity
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * SAF destination lifecycle contract for both exporters.
 *
 * ACTION_CREATE_DOCUMENT creates the destination before an exporter starts.
 * Any cancellation before a successful final stream close must remove that
 * pre-created document best-effort and preserve CancellationException.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class ExportSafCancellationCleanupTest {

    /**
     * DocumentsContract.deleteDocument uses the DocumentsProvider call API,
     * not ContentProvider.delete. A real DocumentsProvider stub is required to
     * prove whether the production helper reaches deleteDocument.
     */
    private class StubDocumentsProvider : DocumentsProvider() {
        companion object {
            var sourceFile: File? = null
            var outputFile: File? = null
            var deleteCalls: Int = 0
            var failDelete: Boolean = false
            const val AUTHORITY = "c22saf.stub"
            const val SOURCE_DOCUMENT_ID = "source.pdf"
            const val OUTPUT_DOCUMENT_ID = "export_out.zip"
        }

        private fun fileFor(documentId: String): File = when (documentId) {
            SOURCE_DOCUMENT_ID -> checkNotNull(sourceFile)
            OUTPUT_DOCUMENT_ID -> checkNotNull(outputFile)
            else -> throw FileNotFoundException("unknown document: $documentId")
        }

        override fun onCreate() = true

        override fun queryRoots(projection: Array<out String>?): Cursor =
            MatrixCursor(
                arrayOf(
                    DocumentsContract.Root.COLUMN_ROOT_ID,
                    DocumentsContract.Root.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Root.COLUMN_TITLE,
                    DocumentsContract.Root.COLUMN_FLAGS,
                ),
            ).apply {
                addRow(arrayOf<Any>("root", SOURCE_DOCUMENT_ID, "C2.2 stub", 0))
            }

        override fun queryDocument(
            documentId: String,
            projection: Array<out String>?,
        ): Cursor = MatrixCursor(
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_FLAGS,
                DocumentsContract.Document.COLUMN_SIZE,
            ),
        ).apply {
            addRow(
                arrayOf<Any>(
                    documentId,
                    documentId,
                    if (documentId == SOURCE_DOCUMENT_ID) "application/pdf" else "application/zip",
                    DocumentsContract.Document.FLAG_SUPPORTS_DELETE,
                    fileFor(documentId).length(),
                ),
            )
        }

        override fun queryChildDocuments(
            parentDocumentId: String,
            projection: Array<out String>?,
            sortOrder: String?,
        ): Cursor = MatrixCursor(
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
        )

        override fun openDocument(
            documentId: String,
            mode: String,
            signal: CancellationSignal?,
        ): ParcelFileDescriptor = ParcelFileDescriptor.open(
            fileFor(documentId),
            if ("w" in mode) {
                ParcelFileDescriptor.MODE_READ_WRITE or
                    ParcelFileDescriptor.MODE_CREATE or
                    ParcelFileDescriptor.MODE_TRUNCATE
            } else {
                ParcelFileDescriptor.MODE_READ_ONLY
            },
        )

        override fun deleteDocument(documentId: String) {
            deleteCalls += 1
            if (failDelete) throw FileNotFoundException("provider refuses delete")
            if (documentId != OUTPUT_DOCUMENT_ID || outputFile?.delete() != true) {
                throw FileNotFoundException("backing document was not deleted")
            }
        }
    }

    private lateinit var app: android.content.Context
    private lateinit var resolver: android.content.ContentResolver
    private lateinit var source: File
    private lateinit var backing: File
    private val destUri: Uri
        get() = DocumentsContract.buildDocumentUri(
            StubDocumentsProvider.AUTHORITY,
            StubDocumentsProvider.OUTPUT_DOCUMENT_ID,
        )
    private val sourceUri: Uri
        get() = DocumentsContract.buildDocumentUri(
            StubDocumentsProvider.AUTHORITY,
            StubDocumentsProvider.SOURCE_DOCUMENT_ID,
        )

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        resolver = app.contentResolver
        source = File(app.cacheDir, "c22-source-${System.nanoTime()}.pdf").apply {
            PDDocument().use { document ->
                document.addPage(PDPage())
                document.save(this)
            }
        }
        backing = File(app.cacheDir, "c22-output-${System.nanoTime()}.zip")
        app.cacheDir.listFiles()
            ?.filter { it.name.startsWith("chapter_export_") || it.name.startsWith("pdf_export_") }
            ?.forEach { ChapterPackageExporter.cleanupDir(it) }
        StubDocumentsProvider.sourceFile = source
        StubDocumentsProvider.outputFile = backing
        StubDocumentsProvider.deleteCalls = 0
        StubDocumentsProvider.failDelete = false
        Robolectric.buildContentProvider(StubDocumentsProvider::class.java)
            .create(
                ProviderInfo().apply {
                    authority = StubDocumentsProvider.AUTHORITY
                    exported = true
                    grantUriPermissions = true
                    readPermission = Manifest.permission.MANAGE_DOCUMENTS
                    writePermission = Manifest.permission.MANAGE_DOCUMENTS
                },
            )
    }

    /** Cancels the token once at least [cancelAfterBytes] have been read. */
    private class CancelMidStream(
        private val data: ByteArray,
        private val cancelAfterBytes: Long,
        private val token: ExportCancellationToken,
    ) : InputStream() {
        private var pos = 0L

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (pos >= cancelAfterBytes) token.cancel()
            if (pos >= data.size) return -1
            val n = minOf(len.toLong(), data.size - pos).toInt()
            System.arraycopy(data, pos.toInt(), b, off, n)
            pos += n
            return n
        }

        override fun read(): Int = throw UnsupportedOperationException()
    }

    private fun copyToSafWithCancellationCleanup(
        source: InputStream,
        token: ExportCancellationToken,
    ) {
        resolver.openOutputStream(destUri, "w")!!.use { out ->
            try {
                copyWithExportCancellation(source, out, token)
            } catch (ce: CancellationException) {
                deleteSafDocumentBestEffort(resolver, destUri)
                throw ce
            }
        }
    }

    private fun exportPlan(): ExportRangePlan {
        val node = TextbookOutlineNodeEntity(
            id = "node",
            documentId = "document",
            parentId = null,
            type = "CHAPTER",
            title = "Chapter",
            startPageIndex = 0,
            endPageIndex = 0,
            sortOrder = 0,
            createdAt = 0,
            updatedAt = 0,
        )
        return ExportRangePlan(
            node = node,
            pathTitles = listOf(node.title),
            startPageIndex = 0,
            endPageIndex = 0,
            endSource = EndPageSource.USER_OVERRIDE,
            pageCount = 1,
            printPageStart = null,
            printPageEnd = null,
        )
    }

    private fun expectCancellation(block: suspend () -> Unit): CancellationException {
        try {
            runBlocking { block() }
            throw AssertionError("expected CancellationException")
        } catch (ce: CancellationException) {
            return ce
        }
    }

    private fun assertNoExportWorkDirs() {
        val remaining = app.cacheDir.listFiles()
            .orEmpty()
            .filter { it.name.startsWith("chapter_export_") || it.name.startsWith("pdf_export_") }
        assertTrue("temporary export directories remain: $remaining", remaining.isEmpty())
    }

    @Test
    fun safDocumentCreated_isInitiallyZeroBytes() {
        assertTrue(backing.createNewFile())

        assertTrue(backing.exists())
        assertEquals(0L, backing.length())
        assertEquals(0, StubDocumentsProvider.deleteCalls)
    }

    @Test
    fun zip_normalCompletion_keepsDocumentAndDoesNotDelete() = runBlocking {
        assertTrue(backing.createNewFile())
        val plan = exportPlan()

        val result = ChapterPackageExporter(app).export(
            sourceUri = sourceUri,
            documentId = "document",
            documentDisplayName = "source.pdf",
            plan = plan,
            allNodes = listOf(plan.node),
            printAnchors = emptyList(),
            outputUri = destUri,
            cancellation = ExportCancellationToken(),
            onState = {},
        )

        assertTrue(result is ChapterExportState.Completed)
        assertTrue(backing.exists())
        assertTrue(backing.length() > 0L)
        assertEquals(0, StubDocumentsProvider.deleteCalls)
        assertNoExportWorkDirs()
    }

    @Test
    fun annotatedPdf_normalCompletion_keepsDocumentAndDoesNotDelete() = runBlocking {
        assertTrue(backing.createNewFile())

        val result = PdfAnnotatedExporter(app).export(
            sourceUri = sourceUri,
            documentDisplayName = "source.pdf",
            plan = exportPlan(),
            outputUri = destUri,
            cancellation = ExportCancellationToken(),
            onState = {},
        )

        assertTrue(result is ChapterExportState.Completed)
        assertTrue(backing.exists())
        assertTrue(backing.length() > 0L)
        assertEquals(0, StubDocumentsProvider.deleteCalls)
        assertNoExportWorkDirs()
    }

    @Test
    fun zip_cancelAfterSafDocumentCreationBeforeWrite_deletesAndRethrowsCancellation() {
        assertTrue(backing.createNewFile())
        val token = ExportCancellationToken().apply { cancel() }
        val states = mutableListOf<ChapterExportState>()
        val plan = exportPlan()

        val cancellation = expectCancellation {
            ChapterPackageExporter(app).export(
                sourceUri = sourceUri,
                documentId = "document",
                documentDisplayName = "source.pdf",
                plan = plan,
                allNodes = listOf(plan.node),
                printAnchors = emptyList(),
                outputUri = destUri,
                cancellation = token,
                onState = states::add,
            )
        }

        assertEquals("Export cancelled", cancellation.message)
        assertEquals(1, StubDocumentsProvider.deleteCalls)
        assertFalse(backing.exists())
        assertEquals(ChapterExportState.Cancelled, states.last())
        assertNoExportWorkDirs()
    }

    @Test
    fun annotatedPdf_cancelAfterSafDocumentCreationBeforeWrite_deletesAndRethrowsCancellation() {
        assertTrue(backing.createNewFile())
        val token = ExportCancellationToken().apply { cancel() }
        val states = mutableListOf<ChapterExportState>()
        val plan = exportPlan()

        val cancellation = expectCancellation {
            PdfAnnotatedExporter(app).export(
                sourceUri = sourceUri,
                documentDisplayName = "source.pdf",
                plan = plan,
                outputUri = destUri,
                cancellation = token,
                onState = states::add,
            )
        }

        assertEquals("Export cancelled", cancellation.message)
        assertEquals(1, StubDocumentsProvider.deleteCalls)
        assertFalse(backing.exists())
        assertEquals(ChapterExportState.Cancelled, states.last())
        assertNoExportWorkDirs()
    }

    @Test
    fun zip_cancelBeforeWrite_deleteFailure_doesNotReplaceCancellation() {
        assertTrue(backing.createNewFile())
        StubDocumentsProvider.failDelete = true
        val token = ExportCancellationToken().apply { cancel() }
        val plan = exportPlan()

        val cancellation = expectCancellation {
            ChapterPackageExporter(app).export(
                sourceUri = sourceUri,
                documentId = "document",
                documentDisplayName = "source.pdf",
                plan = plan,
                allNodes = listOf(plan.node),
                printAnchors = emptyList(),
                outputUri = destUri,
                cancellation = token,
                onState = {},
            )
        }

        assertEquals("Export cancelled", cancellation.message)
        assertEquals(1, StubDocumentsProvider.deleteCalls)
        assertTrue(backing.exists())
        assertNoExportWorkDirs()
    }

    @Test
    fun cancelBeforeFirstWrite_afterSafStreamOpened_callsDeleteSuccessfully() {
        assertTrue(backing.createNewFile())
        val token = ExportCancellationToken().apply { cancel() }

        try {
            copyToSafWithCancellationCleanup(ByteArrayInputStream(ByteArray(1024)), token)
            fail("expected CancellationException")
        } catch (_: CancellationException) {
            // expected
        }

        assertEquals(1, StubDocumentsProvider.deleteCalls)
        assertFalse(backing.exists())
    }

    @Test
    fun cancelDuringWrite_callsDeleteAndRemovesPartialDocument() {
        assertTrue(backing.createNewFile())
        val payload = ByteArray(500_000) { (it % 253).toByte() }
        val token = ExportCancellationToken()
        val source = CancelMidStream(payload, EXPORT_COPY_CHUNK_BYTES.toLong(), token)

        try {
            copyToSafWithCancellationCleanup(source, token)
            fail("expected CancellationException")
        } catch (_: CancellationException) {
            // expected
        }

        assertEquals(1, StubDocumentsProvider.deleteCalls)
        assertFalse(backing.exists())
    }

    @Test
    fun cancelAfterStreamClose_keepsDocumentAndDoesNotDelete() {
        assertTrue(backing.createNewFile())
        val payload = ByteArray(150_000) { (it % 249).toByte() }
        val token = ExportCancellationToken()

        copyToSafWithCancellationCleanup(ByteArrayInputStream(payload), token)
        token.cancel()

        assertTrue(backing.exists())
        assertEquals(payload.size.toLong(), backing.length())
        assertEquals("post-close cancel is a completion race", 0, StubDocumentsProvider.deleteCalls)
    }

    @Test
    fun deleteSuccess_routesThroughDocumentsProvider_andRemovesDocument() {
        backing.writeBytes(ByteArray(1024))

        deleteSafDocumentBestEffort(resolver, destUri)

        assertEquals(1, StubDocumentsProvider.deleteCalls)
        assertFalse(backing.exists())
    }

    @Test
    fun deleteFailure_isSwallowed_andDocumentRemains() {
        backing.writeBytes(ByteArray(1024))
        StubDocumentsProvider.failDelete = true

        deleteSafDocumentBestEffort(resolver, destUri)

        assertEquals(1, StubDocumentsProvider.deleteCalls)
        assertTrue(backing.exists())
        assertEquals(1024L, backing.length())
    }
}
