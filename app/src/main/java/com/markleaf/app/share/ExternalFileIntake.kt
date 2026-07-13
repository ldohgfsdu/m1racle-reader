package com.markleaf.app.share

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.markleaf.app.data.local.DocumentType
import com.markleaf.app.data.repository.DocumentRepository
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Copy a shared/opened URI into app-private storage, then import via [DocumentRepository].
 * Does not rely on long-lived temporary content grants.
 */
object ExternalFileIntake {

    /** Hard cap to avoid huge share payloads. */
    const val MAX_BYTES: Long = 200L * 1024L * 1024L

    private val ALLOWED_MIMES = setOf(
        "application/pdf",
        "application/zip",
        "application/x-zip-compressed",
        "application/x-zip",
        "text/markdown",
        "text/x-markdown",
        "text/plain",
        "application/octet-stream",
    )

    sealed class Outcome {
        data class Success(val documentId: String, val isPdf: Boolean) : Outcome()
        data object Unsupported : Outcome()
        data object TooLarge : Outcome()
        data object Failed : Outcome()
        data object Cancelled : Outcome()
    }

    suspend fun importSharedUri(
        context: Context,
        repository: DocumentRepository,
        sourceUri: Uri,
        intentType: String?,
    ): Outcome {
        val meta = queryMeta(context, sourceUri)
        val displayName = meta.displayName
            ?: sourceUri.lastPathSegment?.substringAfterLast('/')
            ?: "shared"
        val mime = intentType?.takeIf { it.isNotBlank() }
            ?: context.contentResolver.getType(sourceUri)
            ?: meta.mimeHint

        if (!isAllowed(mime, displayName, sourceUri.lastPathSegment)) {
            return Outcome.Unsupported
        }
        if (meta.size != null && meta.size > MAX_BYTES) {
            return Outcome.TooLarge
        }

        val ext = guessExtension(displayName, mime)
        val localId = DocumentRepository.stableUriId(sourceUri.toString())
        val destDir = File(context.filesDir, "inbox/$localId")
        if (!destDir.exists()) destDir.mkdirs()
        val destFile = File(destDir, sanitizeFileName(displayName, ext))

        try {
            val input = openStream(context, sourceUri) ?: return Outcome.Failed
            input.use { stream ->
                var written = 0L
                val buf = ByteArray(64 * 1024)
                FileOutputStream(destFile).use { out ->
                    while (true) {
                        val n = stream.read(buf)
                        if (n < 0) break
                        written += n
                        if (written > MAX_BYTES) {
                            destFile.delete()
                            return Outcome.TooLarge
                        }
                        out.write(buf, 0, n)
                    }
                }
                if (written == 0L && !destFile.isFile) {
                    return Outcome.Failed
                }
            }
        } catch (_: SecurityException) {
            destFile.delete()
            return Outcome.Failed
        } catch (_: Exception) {
            destFile.delete()
            return Outcome.Failed
        }

        // Local private copy → same importDocument path as FAB (ZIP extracts to packages/).
        val localUri = Uri.fromFile(destFile)
        return when (val result = repository.importDocument(localUri)) {
            is com.markleaf.app.data.repository.ImportResult.Success -> {
                Outcome.Success(
                    documentId = result.document.id,
                    isPdf = result.document.isPdf,
                )
            }
            com.markleaf.app.data.repository.ImportResult.UnsupportedFormat -> {
                destFile.delete()
                Outcome.Unsupported
            }
            com.markleaf.app.data.repository.ImportResult.ReadFailed -> {
                destFile.delete()
                Outcome.Failed
            }
        }
    }

    fun isAllowed(mime: String?, displayName: String?, pathSegment: String?): Boolean {
        if (DocumentType.isAllowedImport(mime, displayName, pathSegment)) return true
        val m = mime?.lowercase(Locale.ROOT)?.trim().orEmpty()
        if (m in ALLOWED_MIMES) {
            // octet-stream only if extension is known
            if (m == "application/octet-stream") {
                return DocumentType.isMarkdownFileName(displayName) ||
                    DocumentType.hasPdfExtension(displayName) ||
                    DocumentType.isZipFileName(displayName) ||
                    DocumentType.isMarkdownFileName(pathSegment) ||
                    DocumentType.hasPdfExtension(pathSegment) ||
                    DocumentType.isZipFileName(pathSegment)
            }
            return true
        }
        return false
    }

    private fun openStream(context: Context, uri: Uri) =
        when (uri.scheme?.lowercase(Locale.ROOT)) {
            "file" -> {
                val p = uri.path ?: return null
                val f = File(p)
                if (f.isFile) f.inputStream() else null
            }
            else -> context.contentResolver.openInputStream(uri)
        }

    private data class Meta(val displayName: String?, val size: Long?, val mimeHint: String?)

    private fun queryMeta(context: Context, uri: Uri): Meta {
        var name: String? = null
        var size: Long? = null
        try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        } catch (_: Exception) {
            // ignore
        }
        return Meta(name, size, null)
    }

    private fun guessExtension(displayName: String, mime: String?): String {
        val lower = displayName.lowercase(Locale.ROOT)
        when {
            lower.endsWith(".pdf") -> return "pdf"
            lower.endsWith(".zip") -> return "zip"
            lower.endsWith(".md") || lower.endsWith(".markdown") -> return "md"
            lower.endsWith(".txt") -> return "txt"
        }
        return when (mime?.lowercase(Locale.ROOT)) {
            "application/pdf" -> "pdf"
            "application/zip", "application/x-zip-compressed", "application/x-zip" -> "zip"
            "text/markdown", "text/x-markdown" -> "md"
            "text/plain" -> "txt"
            else -> "bin"
        }
    }

    private fun sanitizeFileName(name: String, fallbackExt: String): String {
        val base = name.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("""[^\w.\-()（）\u4e00-\u9fff]+"""), "_")
            .take(120)
            .ifBlank { "file.$fallbackExt" }
        return if (base.contains('.')) base else "$base.$fallbackExt"
    }
}
