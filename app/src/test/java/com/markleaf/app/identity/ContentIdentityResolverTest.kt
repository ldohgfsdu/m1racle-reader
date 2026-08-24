package com.markleaf.app.identity

import android.net.Uri
import com.markleaf.app.data.local.DocumentDao
import com.markleaf.app.data.local.DocumentEntity
import com.markleaf.app.share.ResolvedExternalMetadata
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.LockSupport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
class ContentIdentityResolverTest {

    private lateinit var stagingDirectory: java.io.File

    @Before
    fun setUp() {
        stagingDirectory = Files.createTempDirectory("markleaf-identity-resolver-").toFile()
    }

    @After
    fun tearDown() {
        stagingDirectory.deleteRecursively()
    }

    @Test
    fun sameBytesDifferentUri_resolveToSameContentHash() = runBlocking {
        val bytes = "same exact content".toByteArray()
        val firstUri = Uri.parse("content://provider-a/docs/one")
        val secondUri = Uri.parse("content://provider-b/shared/two")
        val sources = mapOf(firstUri.toString() to bytes, secondUri.toString() to bytes)
        val resolver = resolver(openSource = { uri -> ByteArrayInputStream(sources.getValue(uri.toString())) })

        val first = resolver.resolve(input(firstUri, "one.md", bytes.size.toLong())).resolved()
        val second = resolver.resolve(input(secondUri, "two.md", bytes.size.toLong())).resolved()
        try {
            assertEquals(first.contentSha256, second.contentSha256)
            assertEquals(sha256(bytes), first.contentSha256)
            assertTrue(first.decision is ContentIdentityDecision.NoExistingDocument)
            assertTrue(second.decision is ContentIdentityDecision.NoExistingDocument)
            assertFalse(first.stagedFile == second.stagedFile)
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun differentBytesSameFilename_resolveToDifferentContentHashes() = runBlocking {
        val firstBytes = "version one".toByteArray()
        val secondBytes = "version two".toByteArray()
        val firstUri = Uri.parse("content://provider/docs/a")
        val secondUri = Uri.parse("content://provider/docs/b")
        val sources = mapOf(firstUri.toString() to firstBytes, secondUri.toString() to secondBytes)
        val resolver = resolver(openSource = { uri -> ByteArrayInputStream(sources.getValue(uri.toString())) })

        val first = resolver.resolve(input(firstUri, "same-name.md", firstBytes.size.toLong())).resolved()
        val second = resolver.resolve(input(secondUri, "same-name.md", secondBytes.size.toLong())).resolved()
        try {
            assertFalse(first.contentSha256 == second.contentSha256)
            assertEquals("same-name.md", first.metadata.displayName)
            assertEquals("same-name.md", second.metadata.displayName)
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun sameHashExistingDocument_returnsExistingDocumentFound() = runBlocking {
        val bytes = "already in library".toByteArray()
        val existing = document("existing", sha256(bytes))
        val resolver = resolver(
            dao = ResolverDocumentDao(listOf(existing)),
            openSource = { ByteArrayInputStream(bytes) },
        )

        val resolved = resolver.resolve(
            input(Uri.parse("content://provider/another-uri"), "existing.md", bytes.size.toLong()),
        ).resolved()
        try {
            val decision = resolved.decision as ContentIdentityDecision.ExistingDocumentFound
            assertSame(existing, decision.document)
        } finally {
            resolved.close()
        }
    }

    @Test
    fun nullHashDocument_doesNotMatchIncomingContent() = runBlocking {
        val bytes = "legacy content".toByteArray()
        val resolver = resolver(
            dao = ResolverDocumentDao(listOf(document("legacy-null", null))),
            openSource = { ByteArrayInputStream(bytes) },
        )

        val resolved = resolver.resolve(
            input(Uri.parse("content://provider/legacy"), "legacy.md", bytes.size.toLong()),
        ).resolved()
        try {
            assertTrue(resolved.decision is ContentIdentityDecision.NoExistingDocument)
        } finally {
            resolved.close()
        }
    }

    @Test
    fun interruptedHashing_returnsFailureAndRemovesPartialStage() = runBlocking {
        val resolver = resolver(openSource = { FailingInputStream(bytesBeforeFailure = 96 * 1024) })

        val result = resolver.resolve(
            input(Uri.parse("content://provider/interrupted"), "broken.pdf", null),
        )

        assertTrue(result is ContentIdentityResolution.Failed)
        assertTrue((result as ContentIdentityResolution.Failed).cause is IOException)
        assertTrue(stagingDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun coroutineCancellation_interruptsHashingAndRemovesPartialStage() = runBlocking {
        val stream = BlockingInterruptibleInputStream()
        val resolver = resolver(openSource = { stream })
        val task = async(Dispatchers.Default) {
            resolver.resolve(input(Uri.parse("content://provider/cancel"), "cancel.pdf", null))
        }

        assertTrue("hashing started", stream.started.await(5, TimeUnit.SECONDS))
        task.cancel()
        val error = runCatching { task.await() }.exceptionOrNull()

        assertTrue(error is CancellationException)
        assertTrue(stagingDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun largeStream_isHashedWithBoundedReadBuffer() = runBlocking {
        val size = 16L * 1024L * 1024L
        val stream = GeneratedByteInputStream(size, 0x5a)
        val resolver = resolver(openSource = { stream })

        val resolved = resolver.resolve(
            input(Uri.parse("content://provider/large"), "large.pdf", size, maximumBytes = size),
        ).resolved()
        try {
            assertEquals(size, resolved.byteCount)
            assertEquals(size, resolved.stagedFile.length())
            assertEquals(sha256OfRepeatedByte(size, 0x5a), resolved.contentSha256)
            assertTrue(stream.maximumRequestedBytes <= ContentIdentityResolver.BUFFER_BYTES)
            assertTrue(resolved.decision is ContentIdentityDecision.NoExistingDocument)
        } finally {
            resolved.close()
        }
        assertTrue(stagingDirectory.listFiles().orEmpty().isEmpty())
    }

    private fun resolver(
        dao: DocumentDao = ResolverDocumentDao(),
        openSource: (Uri) -> InputStream?,
    ): ContentIdentityResolver = ContentIdentityResolver(
        documentDao = dao,
        stagingDirectory = stagingDirectory,
        openSource = openSource,
    )

    private fun input(
        uri: Uri,
        displayName: String,
        sizeBytes: Long?,
        maximumBytes: Long? = null,
    ): ContentIdentityInput = ContentIdentityInput(
        sourceUri = uri,
        metadata = ResolvedExternalMetadata(
            displayName = displayName,
            sizeBytesHint = sizeBytes,
            mimeType = if (displayName.endsWith(".pdf")) "application/pdf" else "text/markdown",
            nameSource = ResolvedExternalMetadata.NameSource.DISPLAY_NAME,
            sizeSource = sizeBytes?.let { ResolvedExternalMetadata.SizeSource.OPENABLE_SIZE },
        ),
        maximumBytes = maximumBytes,
    )

    private fun ContentIdentityResolution.resolved(): PreparedContentIdentity {
        assertTrue("expected resolved identity, got $this", this is ContentIdentityResolution.Resolved)
        return (this as ContentIdentityResolution.Resolved).content
    }

    private fun document(id: String, contentSha256: String?): DocumentEntity = DocumentEntity(
        id = id,
        displayName = "$id.md",
        uri = "content://library/$id",
        mimeType = "text/markdown",
        fileSize = 10L,
        lastOpenedAt = 1L,
        createdAt = 1L,
        lastScrollIndex = 0,
        lastScrollOffset = 0,
        readingProgress = 0f,
        title = null,
        headingCount = 0,
        contentSha256 = contentSha256,
    )

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun sha256OfRepeatedByte(size: Long, value: Int): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024) { value.toByte() }
        var remaining = size
        while (remaining > 0) {
            val count = minOf(remaining, buffer.size.toLong()).toInt()
            digest.update(buffer, 0, count)
            remaining -= count
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

private class ResolverDocumentDao(
    initial: List<DocumentEntity> = emptyList(),
) : DocumentDao {
    private val rows = initial.associateByTo(linkedMapOf()) { it.id }
    private val recent = MutableStateFlow(rows.values.toList())

    override fun observeRecentDocuments(): Flow<List<DocumentEntity>> = recent
    override suspend fun getById(id: String): DocumentEntity? = rows[id]
    override suspend fun getByContentSha256(contentSha256: String): DocumentEntity? =
        rows.values.find { it.contentSha256 == contentSha256 }
    override suspend fun getAll(): List<DocumentEntity> = rows.values.toList()
    override suspend fun upsert(document: DocumentEntity) {
        rows[document.id] = document
        recent.value = rows.values.toList()
    }
    override suspend fun deleteById(id: String) {
        rows.remove(id)
        recent.value = rows.values.toList()
    }
    override suspend fun updateLastOpenedAt(id: String, time: Long) = Unit
    override suspend fun updateParseMetadata(id: String, title: String?, headingCount: Int) = Unit
    override suspend fun updateReadingPosition(
        id: String,
        lastScrollIndex: Int,
        lastScrollOffset: Int,
        readingProgress: Float,
    ) = Unit
    override suspend fun updatePdfReadingPosition(
        id: String,
        pageIndex: Int,
        intraPageFractionEncoded: Int,
        pageCount: Int,
        readingProgress: Float,
    ) = Unit
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
        rows.clear()
        recent.value = emptyList()
    }
}

private class FailingInputStream(
    private var bytesBeforeFailure: Int,
) : InputStream() {
    override fun read(): Int = throw UnsupportedOperationException()

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (bytesBeforeFailure <= 0) throw IOException("simulated interrupted source")
        val count = minOf(length, bytesBeforeFailure)
        java.util.Arrays.fill(buffer, offset, offset + count, 0x31.toByte())
        bytesBeforeFailure -= count
        return count
    }
}

private class BlockingInterruptibleInputStream : InputStream() {
    val started = CountDownLatch(1)

    override fun read(): Int = throw UnsupportedOperationException()

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        started.countDown()
        while (!Thread.currentThread().isInterrupted) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5))
        }
        throw InterruptedIOException("cancelled")
    }
}

private class GeneratedByteInputStream(
    totalBytes: Long,
    private val value: Int,
) : InputStream() {
    private var remaining = totalBytes
    var maximumRequestedBytes: Int = 0
        private set

    override fun read(): Int = if (remaining-- > 0) value else -1

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (remaining <= 0) return -1
        maximumRequestedBytes = maxOf(maximumRequestedBytes, length)
        val count = minOf(remaining, length.toLong()).toInt()
        java.util.Arrays.fill(buffer, offset, offset + count, value.toByte())
        remaining -= count
        return count
    }
}
