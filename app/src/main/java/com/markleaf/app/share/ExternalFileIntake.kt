package com.markleaf.app.share

import com.markleaf.app.data.local.DocumentType
import java.util.Locale

/**
 * Import acceptance gate for external content (mime / extension rules only).
 *
 * A3-3a: the copy-based intake path (`importSharedUri` and its private
 * metadata probe, stream copy, name sanitizer and Outcome type) was deleted —
 * it had zero production callers; Home SAF import goes through
 * [com.markleaf.app.data.repository.DocumentRepository.importDocument] and
 * external VIEW/SEND goes through [ExternalDocumentImporter].
 *
 * Acceptance policy itself is intentionally untouched: [isAllowed],
 * [ALLOWED_MIMES] and [MAX_BYTES] keep their exact semantics.
 */
object ExternalFileIntake {

    /** Hard cap shared with the two-phase importer's copy loop. */
    const val MAX_BYTES: Long = 200L * 1024L * 1024L

    private val ALLOWED_MIMES = setOf(
        "application/pdf",
        "application/x-pdf",
        "application/zip",
        "application/x-zip-compressed",
        "application/x-zip",
        "text/markdown",
        "text/x-markdown",
        "text/plain",
        "application/octet-stream",
    )

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
}
