package com.markleaf.app.share

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
 * A3-3a provider matrix: pins the single [ProviderMetadataSource.collect]
 * probe behavior that replaced ExternalDocumentImporter.queryProviderMetadata
 * and DocumentRepository.queryMetadata.
 *
 * Covered channels per case: OpenableColumns query (name/size), getType vs
 * intentMime precedence, AssetFileDescriptor length, ParcelFileDescriptor
 * statSize, identity fields, per-probe exception isolation.
 *
 * No Room is touched (Robolectric SQLite backend unavailable on linux-aarch64).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class ProviderMetadataSourceMatrixTest {

    /**
     * Static-configured stub: each test sets the scenario then registers a
     * fresh instance under its own authority via Robolectric.setupContentProvider.
     */
    class StubProvider : ContentProvider() {
        companion object {
            var row: List<Any?>? = listOf("stub.pdf", 42L)
            var type: String? = "application/pdf"
            var failQuery: Boolean = false
            var backingFile: File? = null
        }

        override fun onCreate() = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            args: Array<out String>?,
            sort: String?,
        ): Cursor {
            if (failQuery) throw IllegalStateException("provider query exploded")
            return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
                row?.let { addRow(it) }
            }
        }

        override fun getType(uri: Uri): String? = type
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = 0
        override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = 0

        /** Backed by a real temp file so AFD/PFD size channels are deterministic. */
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            val f = backingFile ?: error("no backing file configured")
            return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
        }
    }

    private lateinit var resolver: android.content.ContentResolver
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<android.content.Context>()
        resolver = app.contentResolver
        tempDir = app.cacheDir.resolve("pms-matrix-${System.nanoTime()}").apply { mkdirs() }
        val backing = File(tempDir, "backing.bin").apply { writeBytes(ByteArray(77)) }
        StubProvider.row = listOf("stub.pdf", 42L)
        StubProvider.type = "application/pdf"
        StubProvider.failQuery = false
        StubProvider.backingFile = backing
    }

    private fun registerStub(authority: String) {
        Robolectric.setupContentProvider(StubProvider::class.java, authority)
    }

    private fun contentUri(authority: String, segment: String = "doc123") =
        Uri.parse("content://$authority/root/$segment")

    @Test
    fun fullRowWithTypeAndDescriptorChannels() {
        registerStub("full")
        val pm = ProviderMetadataSource.collect(resolver, contentUri("full"), intentMime = null)

        assertEquals("stub.pdf", pm.displayName)
        assertEquals(42L, pm.size)
        assertEquals("application/pdf", pm.mimeType)
        assertEquals("content", pm.scheme)
        assertEquals("full", pm.authority)
        assertEquals("doc123", pm.lastPathSegment)
        // openFile-backed descriptor: both size channels reflect the backing file.
        assertEquals(77L, pm.assetFileDescriptorLength)
        assertEquals(77L, pm.parcelFileDescriptorStatSize)
    }

    @Test
    fun nullSizeCellAndMissingNameYieldNullsButKeepOtherChannels() {
        registerStub("partial")
        StubProvider.row = listOf(null, null)
        val pm = ProviderMetadataSource.collect(resolver, contentUri("partial"))

        assertNull(pm.displayName)
        assertNull(pm.size)
        assertEquals("application/pdf", pm.mimeType)
        assertEquals(77L, pm.assetFileDescriptorLength)
    }

    @Test
    fun emptyCursorYieldsNoNameNoSize() {
        registerStub("empty")
        StubProvider.row = null
        val pm = ProviderMetadataSource.collect(resolver, contentUri("empty"))
        assertNull(pm.displayName)
        assertNull(pm.size)
        assertEquals("application/pdf", pm.mimeType)
    }

    @Test
    fun nonBlankIntentMimeWinsOverProviderType() {
        registerStub("mime1")
        StubProvider.type = "application/octet-stream"
        val pm = ProviderMetadataSource.collect(resolver, contentUri("mime1"), intentMime = "text/markdown")
        assertEquals("text/markdown", pm.mimeType)
    }

    @Test
    fun blankIntentMimeFallsBackToProviderType() {
        registerStub("mime2")
        StubProvider.type = "application/octet-stream"
        val pm = ProviderMetadataSource.collect(resolver, contentUri("mime2"), intentMime = "   ")
        assertEquals("application/octet-stream", pm.mimeType)
    }

    @Test
    fun nullIntentMimeFallsBackToProviderType() {
        registerStub("mime3")
        StubProvider.type = "text/plain"
        val pm = ProviderMetadataSource.collect(resolver, contentUri("mime3"), intentMime = null)
        assertEquals("text/plain", pm.mimeType)
    }

    @Test
    fun queryExplosionIsIsolatedToNameAndSize() {
        registerStub("boom")
        StubProvider.failQuery = true
        val pm = ProviderMetadataSource.collect(resolver, contentUri("boom"))

        assertNull(pm.displayName)
        assertNull(pm.size)
        assertEquals("application/pdf", pm.mimeType)
        assertEquals(77L, pm.assetFileDescriptorLength)
        assertEquals(77L, pm.parcelFileDescriptorStatSize)
    }

    @Test
    fun fileUriIdentityFieldsCollectedWithoutFabricatedDisplayName() {
        val f = File(tempDir, "local 教材.md").apply { writeText("# hi") }
        val uri = Uri.fromFile(f)

        val pm = ProviderMetadataSource.collect(resolver, uri, intentMime = null)

        assertEquals("file", pm.scheme)
        // Uri.fromFile carries an EMPTY-STRING authority on Android, not null.
        assertEquals("", pm.authority)
        assertTrue(pm.lastPathSegment!!.endsWith("local 教材.md"))
        // The collector never invents names/sizes from the filesystem — the
        // file:// enrichment stays an explicit repository-side step.
        assertNull(pm.displayName)
        assertNull(pm.size)
        assertEquals(f.length(), pm.assetFileDescriptorLength ?: pm.parcelFileDescriptorStatSize)
    }

    @Test
    fun unknownSchemeKeepsIdentityAndNullsEverythingElse() {
        val uri = Uri.parse("myapp://host/seg/name.bin")
        val pm = ProviderMetadataSource.collect(resolver, uri, intentMime = null)

        assertEquals("myapp", pm.scheme)
        assertEquals("host", pm.authority)
        // Android lastPathSegment is the FINAL segment only.
        assertEquals("name.bin", pm.lastPathSegment)
        assertNull(pm.displayName)
        assertNull(pm.size)
        assertNull(pm.mimeType)
        assertNull(pm.assetFileDescriptorLength)
        assertNull(pm.parcelFileDescriptorStatSize)
    }
}
