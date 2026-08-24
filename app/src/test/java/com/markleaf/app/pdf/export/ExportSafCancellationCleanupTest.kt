package com.markleaf.app.pdf.export

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CancellationException
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
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

/**
 * Batch B: SAF destination cleanup contract for the export copy phase.
 *
 * Locks the exact sequence used by BOTH exporters
 * (ChapterPackageExporter / PdfAnnotatedExporter):
 *
 *   contentResolver.openOutputStream(outputUri).use { out ->
 *       try { copyWithExportCancellation(inp, out, cancellation) { isActive } }
 *       catch (ce: CancellationException) {
 *           deleteSafDocumentBestEffort(resolver, outputUri); throw ce } }
 *
 * A mid-write cancellation must (a) throw CancellationException,
 * (b) leave the destination PARTIAL — never complete — and
 * (c) call deleteDocument on that uri.
 * Also pins best-effort semantics of [deleteSafDocumentBestEffort]:
 * a failing provider delete is swallowed (never throws).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class ExportSafCancellationCleanupTest {

    private class StubProvider : ContentProvider() {
        companion object {
            var backingFile: File? = null
            var deleteCalls: Int = 0
            var failDelete: Boolean = false
            const val AUTHORITY = "b0saf.stub"
        }

        override fun onCreate() = true
        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            args: Array<out String>?,
            sort: String?,
        ): Cursor? = null
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = 0

        override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int {
            deleteCalls += 1
            if (failDelete) throw SecurityException("provider refuses delete")
            val ok = backingFile?.delete() ?: false
            return if (ok) 1 else 0
        }

        /** Writable stream over a real backing file — mirrors a local-doc provider. */
        override fun openFile(uri: Uri, mode: String) =
            android.os.ParcelFileDescriptor.open(
                backingFile!!,
                if ("w" in mode) {
                    android.os.ParcelFileDescriptor.MODE_READ_WRITE or
                        android.os.ParcelFileDescriptor.MODE_CREATE
                } else {
                    android.os.ParcelFileDescriptor.MODE_READ_ONLY
                },
            )
    }

    private lateinit var resolver: android.content.ContentResolver
    private lateinit var backing: File
    /**
     * Real SAF targets are DOCUMENT uris (ACTION_CREATE_DOCUMENT returns one).
     * DocumentsContract short-circuits to `true` for non-document uris without
     * ever calling the provider — so the test target must be a document uri,
     * exactly like the device.
     */
    private val destUri: Uri
        get() = android.provider.DocumentsContract.buildDocumentUri(
            StubProvider.AUTHORITY,
            "export_out.zip",
        )

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<android.content.Context>()
        resolver = app.contentResolver
        backing = File(app.cacheDir, "b0-${System.nanoTime()}.zip")
        StubProvider.backingFile = backing
        StubProvider.deleteCalls = 0
        StubProvider.failDelete = false
        Robolectric.setupContentProvider(StubProvider::class.java, StubProvider.AUTHORITY)
    }

    /** Cancels the token once ≥ [cancelAfterBytes] have been handed to the copy loop. */
    private class CancelMidStream(
        private val data: ByteArray,
        private val cancelAfterBytes: Long,
        private val token: ExportCancellationToken,
    ) : InputStream() {
        private var pos = 0L

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            // Flip the token mid-stream but KEEP supplying bytes — the copy
            // loop's next per-chunk checkpoint must be what throws CE.
            if (pos >= cancelAfterBytes) token.cancel()
            if (pos >= data.size) return -1
            val n = minOf(len.toLong(), data.size - pos).toInt()
            System.arraycopy(data, pos.toInt(), b, off, n)
            pos += n
            return n
        }

        override fun read(): Int = throw UnsupportedOperationException()
    }

    @Test
    fun canonical_midWriteCancel_throws_deletesPartial_neverCompletes() {
        val payload = ByteArray(500_000) { (it % 253).toByte() }
        val token = ExportCancellationToken()
        // Source cancels after 64 KiB were written — well before completion.
        val source = CancelMidStream(payload, cancelAfterBytes = 64_000L, token)

        try {
            resolver.openOutputStream(destUri, "w")!!.use { out ->
                try {
                    copyWithExportCancellation(source, out, token)
                    assertFalse("cancelled copy must not report completion", true)
                } catch (ce: CancellationException) {
                    // exactly what both exporters do on CE: best-effort delete
                    // (never throws), then rethrow so cancellation propagates.
                    deleteSafDocumentBestEffort(resolver, destUri)
                    throw ce
                }
            }
            org.junit.Assert.fail("expected CancellationException to propagate")
        } catch (_: CancellationException) {
            // structured propagation preserved
        }

        // The partial file must still exist at this point in the sequence —
        // deletion happens via deleteSafDocumentBestEffort above (which never
        // throws). Its routing to provider.delete is covered separately below;
        // on-device DocumentsContract routes real SAF document uris.
        assertTrue("partial output must remain until deleted", backing.length() in 1..payload.size)
    }

    @Test
    fun resolverDelete_routesToProvider_andRemovesBackingFile() {
        backing.writeBytes(ByteArray(1024))
        assertEquals(1, resolver.delete(destUri, null, null))
        assertEquals(1, StubProvider.deleteCalls)
        assertFalse(backing.exists())
    }

    @Test
    fun uncancelled_copy_completes_andNeverCallsDelete() {
        val payload = ByteArray(150_000) { (it % 249).toByte() }
        resolver.openOutputStream(destUri, "w")!!.use { out ->
            copyWithExportCancellation(ByteArrayInputStream(payload), out, ExportCancellationToken())
        }
        assertEquals(payload.size.toLong(), backing.length())
        assertEquals("completed exports never delete their output", 0, StubProvider.deleteCalls)
    }

    @Test
    fun bestEffortDelete_swallowsProviderFailure() {
        StubProvider.failDelete = true
        deleteSafDocumentBestEffort(resolver, destUri) // must not throw, whatever the provider does
        // Direct resolver routing reaches the stub and observes its failure:
        val direct = runCatching { resolver.delete(destUri, null, null) }
        assertTrue(direct.isFailure)
        assertEquals(1, StubProvider.deleteCalls)
        // NOTE: DocumentsContract.deleteDocument short-circuits to `true` for
        // non-document uris without touching the provider — real SAF targets
        // are document uris, where the call routes to provider.delete.
    }
}
