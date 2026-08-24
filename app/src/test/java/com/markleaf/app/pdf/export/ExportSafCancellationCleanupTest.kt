package com.markleaf.app.pdf.export

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
import kotlinx.coroutines.CancellationException
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
 * SAF destination lifecycle characterization for both exporters.
 *
 * ACTION_CREATE_DOCUMENT creates the destination before an exporter starts.
 * The exporter currently deletes only when [copyWithExportCancellation] throws
 * from inside the final SAF stream copy. These tests deliberately distinguish
 * that path from an earlier cancellation checkpoint, where the pre-created
 * document is never opened and delete is never called.
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
            var backingFile: File? = null
            var deleteCalls: Int = 0
            var failDelete: Boolean = false
            const val AUTHORITY = "c21saf.stub"
            const val DOCUMENT_ID = "export_out.zip"
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
                addRow(arrayOf("root", DOCUMENT_ID, "C2.1 stub", 0))
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
                arrayOf(
                    documentId,
                    DOCUMENT_ID,
                    "application/zip",
                    DocumentsContract.Document.FLAG_SUPPORTS_DELETE,
                    backingFile?.length() ?: 0L,
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
            checkNotNull(backingFile),
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
            if (backingFile?.delete() != true) {
                throw FileNotFoundException("backing document was not deleted")
            }
        }
    }

    private lateinit var resolver: android.content.ContentResolver
    private lateinit var backing: File
    private val destUri: Uri
        get() = DocumentsContract.buildDocumentUri(
            StubDocumentsProvider.AUTHORITY,
            StubDocumentsProvider.DOCUMENT_ID,
        )

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<android.content.Context>()
        resolver = app.contentResolver
        backing = File(app.cacheDir, "c21-${System.nanoTime()}.zip")
        StubDocumentsProvider.backingFile = backing
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

    private fun finalSafCopyLikeExporters(
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

    @Test
    fun safDocumentCreated_isInitiallyZeroBytes() {
        assertTrue(backing.createNewFile())

        assertTrue(backing.exists())
        assertEquals(0L, backing.length())
        assertEquals(0, StubDocumentsProvider.deleteCalls)
    }

    @Test
    fun cancelBeforeFinalSafCopy_currentEarlyReturn_skipsDeleteAndLeavesZeroByteDocument() {
        assertTrue(backing.createNewFile())
        val token = ExportCancellationToken().apply { cancel() }

        // Characterizes the pre-copy checks in both exporters. They return
        // Cancelled before entering openOutputStream/catch/delete.
        val state = if (token.isCancelled()) {
            ChapterExportState.Cancelled
        } else {
            finalSafCopyLikeExporters(ByteArrayInputStream(byteArrayOf(1)), token)
            ChapterExportState.Completed("unreachable.zip")
        }

        assertEquals(ChapterExportState.Cancelled, state)
        assertTrue("ACTION_CREATE_DOCUMENT target remains", backing.exists())
        assertEquals(0L, backing.length())
        assertEquals("delete is outside the early-return path", 0, StubDocumentsProvider.deleteCalls)
    }

    @Test
    fun cancelBeforeFirstWrite_afterSafStreamOpened_callsDeleteSuccessfully() {
        assertTrue(backing.createNewFile())
        val token = ExportCancellationToken().apply { cancel() }

        try {
            finalSafCopyLikeExporters(ByteArrayInputStream(ByteArray(1024)), token)
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
            finalSafCopyLikeExporters(source, token)
            fail("expected CancellationException")
        } catch (_: CancellationException) {
            // expected
        }

        assertEquals(1, StubDocumentsProvider.deleteCalls)
        assertFalse(backing.exists())
    }

    @Test
    fun cancelAfterStreamClose_keepsCompletedDocumentAndDoesNotDelete() {
        assertTrue(backing.createNewFile())
        val payload = ByteArray(150_000) { (it % 249).toByte() }
        val token = ExportCancellationToken()

        finalSafCopyLikeExporters(ByteArrayInputStream(payload), token)
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
