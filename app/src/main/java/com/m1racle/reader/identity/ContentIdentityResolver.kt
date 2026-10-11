package com.m1racle.reader.identity

import android.content.Context
import android.net.Uri
import com.m1racle.reader.data.local.DocumentDao
import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.share.ResolvedExternalMetadata
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible

/**
 * Resolves exact source-byte identity without committing a document or changing
 * any import/session state.
 *
 * A successful result owns an app-private staged source. Its caller must either
 * hand that file to a later commit operation or close/discard the result.
 */
class ContentIdentityResolver internal constructor(
    private val documentDao: DocumentDao,
    private val stagingDirectory: File,
    private val openSource: (Uri) -> InputStream?,
) {

    constructor(
        context: Context,
        documentDao: DocumentDao,
    ) : this(
        documentDao = documentDao,
        stagingDirectory = File(context.filesDir, STAGING_DIRECTORY_NAME),
        openSource = defaultOpenSource(context),
    )

    suspend fun resolve(input: ContentIdentityInput): ContentIdentityResolution {
        require(input.maximumBytes == null || input.maximumBytes >= 0L) {
            "maximumBytes must be null or non-negative"
        }

        val maximumBytes = input.maximumBytes
        if (maximumBytes != null && input.metadata.sizeBytesHint?.let { it > maximumBytes } == true) {
            return ContentIdentityResolution.SourceTooLarge
        }

        var staged: StagedSource? = null
        try {
            staged = runInterruptible(Dispatchers.IO) {
                stageSource(input.sourceUri, maximumBytes)
            }
            currentCoroutineContext().ensureActive()
            val existing = findExistingDocument(staged.contentSha256)
            currentCoroutineContext().ensureActive()

            val decision = if (existing != null) {
                ContentIdentityDecision.ExistingDocumentFound(existing)
            } else {
                ContentIdentityDecision.NoExistingDocument
            }
            return ContentIdentityResolution.Resolved(
                PreparedContentIdentity(
                    sourceUri = input.sourceUri,
                    metadata = input.metadata,
                    stagedFile = staged.file,
                    byteCount = staged.byteCount,
                    contentSha256 = staged.contentSha256,
                    decision = decision,
                ),
            )
        } catch (cancelled: CancellationException) {
            staged?.file?.delete()
            throw cancelled
        } catch (_: SourceTooLargeException) {
            staged?.file?.delete()
            return ContentIdentityResolution.SourceTooLarge
        } catch (error: Exception) {
            staged?.file?.delete()
            return ContentIdentityResolution.Failed(error)
        }
    }

    /**
     * The single indexed content-identity lookup boundary. Commit callers invoke
     * this again from their Room transaction because [resolve] returns only a
     * pre-commit candidate and another import may have won meanwhile.
     */
    internal suspend fun findExistingDocument(contentSha256: String): DocumentEntity? {
        require(CONTENT_SHA256.matches(contentSha256)) {
            "contentSha256 must be 64 lowercase hexadecimal characters"
        }
        return documentDao.getByContentSha256(contentSha256)
    }

    private fun stageSource(sourceUri: Uri, maximumBytes: Long?): StagedSource {
        if (!stagingDirectory.exists() && !stagingDirectory.mkdirs()) {
            throw IllegalStateException("Unable to create identity staging directory")
        }
        if (!stagingDirectory.isDirectory) {
            throw IllegalStateException("Identity staging path is not a directory")
        }

        val stagedFile = File.createTempFile(STAGING_FILE_PREFIX, STAGING_FILE_SUFFIX, stagingDirectory)
        var completed = false
        try {
            val digest = MessageDigest.getInstance(SHA_256)
            var byteCount = 0L
            val input = openSource(sourceUri)
                ?: throw IllegalStateException("Unable to open source stream")

            input.use { source ->
                FileOutputStream(stagedFile).use { destination ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        if (Thread.currentThread().isInterrupted) {
                            throw InterruptedException("Content identity staging interrupted")
                        }
                        val read = source.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue

                        byteCount += read
                        if (maximumBytes != null && byteCount > maximumBytes) {
                            throw SourceTooLargeException
                        }
                        digest.update(buffer, 0, read)
                        destination.write(buffer, 0, read)
                    }
                    destination.fd.sync()
                }
            }

            if (!stagedFile.isFile || stagedFile.length() != byteCount) {
                throw IllegalStateException("Staged source length mismatch")
            }

            completed = true
            return StagedSource(
                file = stagedFile,
                byteCount = byteCount,
                contentSha256 = digest.digest().toHex(),
            )
        } finally {
            if (!completed) stagedFile.delete()
        }
    }

    private data class StagedSource(
        val file: File,
        val byteCount: Long,
        val contentSha256: String,
    )

    private data object SourceTooLargeException : Exception()

    companion object {
        internal const val BUFFER_BYTES = 64 * 1024
        private const val SHA_256 = "SHA-256"
        private const val STAGING_DIRECTORY_NAME = "identity-staging"
        private const val STAGING_FILE_PREFIX = "identity-"
        private const val STAGING_FILE_SUFFIX = ".importing"
        private val CONTENT_SHA256 = Regex("^[0-9a-f]{64}$")

        /** Shared source-opening policy, also used by the one-time identity migrations. */
        internal fun defaultOpenSource(context: Context): (Uri) -> InputStream? = { uri ->
            when (uri.scheme?.lowercase(Locale.ROOT)) {
                "file" -> uri.path?.let(::File)?.takeIf(File::isFile)?.let(::FileInputStream)
                else -> context.contentResolver.openInputStream(uri)
            }
        }
    }
}

data class ContentIdentityInput(
    val sourceUri: Uri,
    val metadata: ResolvedExternalMetadata,
    /** Caller-owned intake policy. Null means no resolver-level byte cap. */
    val maximumBytes: Long? = null,
)

sealed interface ContentIdentityResolution {
    data class Resolved(val content: PreparedContentIdentity) : ContentIdentityResolution
    data object SourceTooLarge : ContentIdentityResolution
    data class Failed(val cause: Exception) : ContentIdentityResolution
}

sealed interface ContentIdentityDecision {
    /**
     * Exact content already exists, proven by the indexed content hash. The
     * commit layer must reuse the row without changing its id.
     */
    data class ExistingDocumentFound(val document: DocumentEntity) : ContentIdentityDecision

    /** No exact content owner exists; a later commit layer may create one. */
    data object NoExistingDocument : ContentIdentityDecision
}

class PreparedContentIdentity internal constructor(
    val sourceUri: Uri,
    val metadata: ResolvedExternalMetadata,
    val stagedFile: File,
    val byteCount: Long,
    val contentSha256: String,
    val decision: ContentIdentityDecision,
) : Closeable {
    override fun close() {
        stagedFile.delete()
    }
}

private fun ByteArray.toHex(): String = buildString(size * 2) {
    for (byte in this@toHex) {
        val value = byte.toInt() and 0xff
        append(HEX[value ushr 4])
        append(HEX[value and 0x0f])
    }
}

private const val HEX = "0123456789abcdef"
