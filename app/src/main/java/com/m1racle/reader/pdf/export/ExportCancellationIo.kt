package com.m1racle.reader.pdf.export

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.CancellationException
import java.io.InputStream
import java.io.OutputStream

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

/**
 * Best-effort removal of a partially written SAF destination so a cancelled
 * export does not leave a stale file behind. Never throws: providers may not
 * support delete for arbitrary stream URIs, in which case a partial (never
 * complete) file may remain.
 */
internal fun deleteSafDocumentBestEffort(resolver: ContentResolver, uri: Uri) {
    runCatching {
        DocumentsContract.deleteDocument(resolver, uri)
    }
}
