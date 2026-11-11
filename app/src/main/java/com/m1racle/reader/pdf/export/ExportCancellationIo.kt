package com.m1racle.reader.pdf.export

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.CancellationException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

internal const val EXPORT_COPY_CHUNK_BYTES: Int = 64 * 1024

/**
 * Cancellation checkpoint for blocking export phases (zip assembly, stream
 * copy) where coroutine suspension never happens on its own. Throws
 * [CancellationException]; exporters rethrow after local cleanup so structured
 * cancellation semantics hold.
 */
internal fun throwIfExportCancelled(
    cancellation: ExportCancellationToken,
    jobActive: () -> Boolean = { true },
) {
    if (cancellation.isCancelled() || !jobActive()) {
        throw CancellationException("Export cancelled")
    }
}

/**
 * Chunked stream copy with a cancellation checkpoint per chunk. On cancel the
 * destination is left partial — never complete — and the caller owns
 * best-effort destination cleanup before rethrowing.
 */
internal fun copyWithExportCancellation(
    input: InputStream,
    output: OutputStream,
    cancellation: ExportCancellationToken,
    jobActive: () -> Boolean = { true },
) {
    val buf = ByteArray(EXPORT_COPY_CHUNK_BYTES)
    while (true) {
        throwIfExportCancelled(cancellation, jobActive)
        val n = input.read(buf)
        if (n < 0) break
        output.write(buf, 0, n)
    }
}

/** Hash a stream in fixed-size chunks without materializing it in memory. */
internal fun sha256WithExportCancellation(
    input: InputStream,
    cancellation: ExportCancellationToken,
    jobActive: () -> Boolean = { true },
): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buf = ByteArray(EXPORT_COPY_CHUNK_BYTES)
    while (true) {
        throwIfExportCancelled(cancellation, jobActive)
        val n = input.read(buf)
        if (n < 0) break
        digest.update(buf, 0, n)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/**
 * Best-effort removal of incomplete SAF output. Never throws. Returns true
 * when the provider reports deletion, so export state can tell the user when a
 * partial file could not be removed.
 */
internal fun deleteSafDocumentBestEffort(resolver: ContentResolver, uri: Uri): Boolean =
    runCatching {
        DocumentsContract.deleteDocument(resolver, uri)
    }.getOrDefault(false)

/** Job-local SAF destination ownership. Cleanup is idempotent. */
internal class SafExportDestination(
    private val resolver: ContentResolver,
    private val uri: Uri,
) {
    var committed: Boolean = false
        private set

    private var cleaned: Boolean = false

    fun markCommitted() {
        committed = true
    }

    /** Returns true when no incomplete destination remains. */
    fun cleanupIfUncommitted(): Boolean {
        if (committed || cleaned) return true
        cleaned = deleteSafDocumentBestEffort(resolver, uri)
        return cleaned
    }
}
