package com.markleaf.app.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.markleaf.app.data.local.DocumentDao
import com.markleaf.app.data.local.DocumentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.util.Locale

/**
 * Data access for recent-read documents.
 * Delete only removes history rows — never the on-disk source file.
 */
class DocumentRepository(
    private val context: Context,
    private val documentDao: DocumentDao,
) {

    fun observeRecentDocuments(): Flow<List<DocumentEntity>> =
        documentDao.observeRecentDocuments()

    suspend fun getById(id: String): DocumentEntity? =
        documentDao.getById(id)

    suspend fun getDocumentById(id: String): DocumentEntity? =
        withContext(Dispatchers.IO) {
            documentDao.getById(id)
        }

    suspend fun upsert(document: DocumentEntity) {
        documentDao.upsert(document)
    }

    suspend fun deleteById(id: String) {
        documentDao.deleteById(id)
    }

    /** Bump lastOpenedAt so Home list reorders by recent open. */
    suspend fun touchLastOpenedAt(id: String) {
        withContext(Dispatchers.IO) {
            documentDao.updateLastOpenedAt(id, System.currentTimeMillis())
        }
    }

    /**
     * Persist title (first H1 when present) and TOC heading count after parse.
     */
    suspend fun updateParseMetadata(id: String, title: String?, headingCount: Int) {
        withContext(Dispatchers.IO) {
            documentDao.updateParseMetadata(
                id = id,
                title = title,
                headingCount = headingCount,
            )
        }
    }

    /**
     * Persist LazyColumn scroll position and reading progress (0f–1f).
     */
    suspend fun updateReadingPosition(
        id: String,
        lastScrollIndex: Int,
        lastScrollOffset: Int,
        readingProgress: Float,
    ) {
        withContext(Dispatchers.IO) {
            documentDao.updateReadingPosition(
                id = id,
                lastScrollIndex = lastScrollIndex.coerceAtLeast(0),
                lastScrollOffset = lastScrollOffset.coerceAtLeast(0),
                readingProgress = readingProgress.coerceIn(0f, 1f),
            )
        }
    }

    /**
     * Read plain text from a SAF URI for preview.
     * Caps at [MAX_PREVIEW_BYTES] so huge files do not fill memory.
     * Runs on [Dispatchers.IO]; never throws to callers.
     */
    suspend fun readDocumentText(uriString: String): Result<DocumentTextPreview> =
        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(uriString)
                val input = context.contentResolver.openInputStream(uri)
                    ?: return@withContext Result.failure(DocumentReadException.NotFound)

                input.use { stream ->
                    val buffer = ByteArray(MAX_PREVIEW_BYTES + 1)
                    var total = 0
                    while (total < buffer.size) {
                        val read = stream.read(buffer, total, buffer.size - total)
                        if (read < 0) break
                        total += read
                    }
                    val truncated = total > MAX_PREVIEW_BYTES
                    val length = minOf(total, MAX_PREVIEW_BYTES)
                    val text = String(buffer, 0, length, Charsets.UTF_8)
                    Result.success(
                        DocumentTextPreview(
                            text = text,
                            isTruncated = truncated,
                        ),
                    )
                }
            } catch (_: SecurityException) {
                Result.failure(DocumentReadException.PermissionDenied)
            } catch (_: FileNotFoundException) {
                Result.failure(DocumentReadException.NotFound)
            } catch (e: Exception) {
                Result.failure(DocumentReadException.Failed(e))
            }
        }

    /**
     * Import a document via SAF URI: persist read permission, validate extension,
     * read metadata, and upsert into Room. Runs on [Dispatchers.IO].
     */
    suspend fun importDocument(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        try {
            takeReadPermission(uri)

            val metadata = queryMetadata(uri)
            val displayName = metadata.displayName
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: "未命名文档"

            if (!isAllowedFileName(displayName) && !isAllowedFileName(uri.lastPathSegment)) {
                return@withContext ImportResult.UnsupportedFormat
            }

            // Verify the URI is readable without loading full content.
            context.contentResolver.openInputStream(uri)?.use { /* probe only */ }
                ?: return@withContext ImportResult.ReadFailed

            val mimeType = context.contentResolver.getType(uri)
            val uriString = uri.toString()
            val id = stableUriId(uriString)
            val now = System.currentTimeMillis()
            val existing = documentDao.getById(id)

            val entity = if (existing != null) {
                // Same file again: refresh open time + metadata; keep progress/title.
                existing.copy(
                    displayName = displayName,
                    uri = uriString,
                    mimeType = mimeType,
                    fileSize = metadata.size,
                    lastOpenedAt = now,
                )
            } else {
                DocumentEntity(
                    id = id,
                    displayName = displayName,
                    uri = uriString,
                    mimeType = mimeType,
                    fileSize = metadata.size,
                    lastOpenedAt = now,
                    createdAt = now,
                    lastScrollIndex = 0,
                    lastScrollOffset = 0,
                    readingProgress = 0f,
                    title = null,
                    headingCount = 0,
                )
            }

            documentDao.upsert(entity)
            ImportResult.Success(entity)
        } catch (_: Exception) {
            ImportResult.ReadFailed
        }
    }

    private fun takeReadPermission(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // Provider may not support persistable grants; continue if stream is readable.
        }
    }

    private fun queryMetadata(uri: Uri): FileMetadata {
        var displayName: String? = null
        var size: Long? = null

        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    displayName = cursor.getString(nameIndex)
                }
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }

        return FileMetadata(displayName = displayName, size = size)
    }

    private data class FileMetadata(
        val displayName: String?,
        val size: Long?,
    )

    companion object {
        /** Preview cap: 200KB of raw bytes. */
        const val MAX_PREVIEW_BYTES: Int = 200 * 1024

        private val ALLOWED_EXTENSIONS = listOf(".md", ".markdown", ".txt")

        fun isAllowedFileName(name: String?): Boolean {
            if (name.isNullOrBlank()) return false
            val lower = name.lowercase(Locale.ROOT)
            return ALLOWED_EXTENSIONS.any { lower.endsWith(it) }
        }

        fun stableUriId(uriString: String): String {
            val digest = MessageDigest
                .getInstance("SHA-256")
                .digest(uriString.toByteArray(Charsets.UTF_8))
            return digest.joinToString(separator = "") { byte ->
                "%02x".format(byte)
            }
        }
    }
}

sealed class ImportResult {
    data class Success(val document: DocumentEntity) : ImportResult()
    data object UnsupportedFormat : ImportResult()
    data object ReadFailed : ImportResult()
}

data class DocumentTextPreview(
    val text: String,
    val isTruncated: Boolean,
)

sealed class DocumentReadException : Exception() {
    data object NotFound : DocumentReadException()
    data object PermissionDenied : DocumentReadException()
    data class Failed(val causeError: Throwable? = null) : DocumentReadException() {
        override val cause: Throwable?
            get() = causeError
    }
}
