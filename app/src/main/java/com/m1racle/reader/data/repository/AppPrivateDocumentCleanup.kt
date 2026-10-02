package com.m1racle.reader.data.repository

import java.io.File
import java.net.URI

/**
 * Deletes a document only when its canonical path is strictly below
 * `files/library`. User-owned file/content URIs are never deletion candidates.
 */
internal fun deleteAppPrivateLibraryCopy(filesDir: File, uriString: String): Boolean {
    val uri = runCatching { URI(uriString) }.getOrNull() ?: return false
    if (!uri.scheme.equals("file", ignoreCase = true)) return false

    val libraryRoot = runCatching { File(filesDir, "library").canonicalFile }
        .getOrNull() ?: return false
    val candidate = runCatching { File(uri).canonicalFile }
        .getOrNull() ?: return false
    if (candidate == libraryRoot || !candidate.toPath().startsWith(libraryRoot.toPath())) {
        return false
    }
    if (!candidate.isFile || !candidate.delete()) return false

    var parent = candidate.parentFile
    while (parent != null && parent != libraryRoot) {
        if (!parent.delete()) break
        parent = parent.parentFile
    }
    return true
}

/** Extracted Markdown/EPUB resources belong to the deleted library identity. */
internal fun deleteDocumentExtracts(filesDir: File, documentId: String) {
    for (kind in listOf("packages", "epubs")) {
        val root = File(filesDir, kind).canonicalFile
        val directory = File(root, documentId).canonicalFile
        require(directory.parentFile == root) { "Invalid document id" }
        if (directory.exists()) check(directory.deleteRecursively()) { "Unable to delete document resources" }
    }
}
