package com.m1racle.reader.pdf.export

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.InputStream
import java.io.OutputStream

/**
 * Output seam used by [PdfSplitExporter].
 *
 * Production always uses a DocumentsProvider directory returned by
 * ACTION_OPEN_DOCUMENT_TREE. Instrumentation tests may inject a file-backed
 * target so create, readback and cancellation cleanup exercise the same code.
 */
internal interface PdfSplitOutputTarget {
    fun createPdf(directoryUri: Uri, displayName: String): Uri?

    fun openInput(uri: Uri): InputStream?

    fun openOutput(uri: Uri): OutputStream?

    fun delete(uri: Uri): Boolean
}

internal class AndroidSafSplitOutputTarget(
    context: Context,
) : PdfSplitOutputTarget {
    private val resolver: ContentResolver = context.applicationContext.contentResolver

    override fun createPdf(directoryUri: Uri, displayName: String): Uri? {
        val treeDocumentId = runCatching {
            DocumentsContract.getTreeDocumentId(directoryUri)
        }.getOrNull() ?: return null
        val directoryDocumentUri = DocumentsContract.buildDocumentUriUsingTree(
            directoryUri,
            treeDocumentId,
        )
        return DocumentsContract.createDocument(
            resolver,
            directoryDocumentUri,
            "application/pdf",
            displayName,
        )
    }

    override fun openInput(uri: Uri): InputStream? = resolver.openInputStream(uri)

    override fun openOutput(uri: Uri): OutputStream? =
        resolver.openOutputStream(uri, "w")

    override fun delete(uri: Uri): Boolean =
        runCatching {
            DocumentsContract.deleteDocument(resolver, uri)
        }.getOrDefault(false)
}
