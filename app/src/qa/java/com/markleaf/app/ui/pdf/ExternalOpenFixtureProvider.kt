package com.markleaf.app.ui.pdf

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

/**
 * QA-only ContentProvider fixtures for external-open acceptance.
 * Supports optional omission of SIZE column via query parameter nosize=1.
 */
class ExternalOpenFixtureProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val ctx = context ?: return null
        val file = materialize(ctx, uri) ?: return null
        val omitSize = uri.getQueryParameter("nosize") == "1"
        val cols = if (omitSize) {
            arrayOf(OpenableColumns.DISPLAY_NAME)
        } else {
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        }
        return MatrixCursor(cols).apply {
            if (omitSize) {
                addRow(arrayOf(file.name))
            } else {
                addRow(arrayOf(file.name, file.length()))
            }
        }
    }

    override fun getType(uri: Uri): String = "application/pdf"

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        if (uri.getQueryParameter("fail") == "1") {
            throw java.io.FileNotFoundException("QA intentional mid-read failure fixture")
        }
        val ctx = context ?: return null
        val file = materialize(ctx, uri) ?: return null
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        // When nosize=1, hide length so callers must use stream byte count.
        val length = if (uri.getQueryParameter("nosize") == "1") {
            AssetFileDescriptor.UNKNOWN_LENGTH
        } else {
            file.length()
        }
        return AssetFileDescriptor(pfd, 0, length)
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        if (uri.getQueryParameter("fail") == "1") {
            throw java.io.FileNotFoundException("QA intentional mid-read failure fixture")
        }
        val ctx = context ?: return null
        val file = materialize(ctx, uri) ?: return null
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        const val AUTHORITY = "com.markleaf.app.qa.externalopen"
        fun uriFor(
            fileName: String,
            omitSize: Boolean = false,
            failOpen: Boolean = false,
        ): Uri {
            val b = Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath(fileName)
            if (omitSize) b.appendQueryParameter("nosize", "1")
            if (failOpen) b.appendQueryParameter("fail", "1")
            return b.build()
        }

        private fun materialize(ctx: Context, uri: Uri): File? {
            val name = uri.lastPathSegment ?: return null
            val dir = File(ctx.cacheDir, "external-open-fixtures").also { it.mkdirs() }
            val out = File(dir, name)
            if (out.isFile && out.length() > 0) return out
            // Minimal valid-ish PDF bytes (same structure for A/B/C size differentiation via padding)
            val base = (
                "%PDF-1.1\n" +
                    "1 0 obj<<>>endobj\n" +
                    "2 0 obj<< /Length 44 >>stream\n" +
                    "BT /F1 12 Tf 100 700 Td (MarkLeaf) Tj ET\n" +
                    "endstream\nendobj\n" +
                    "3 0 obj<< /Type /Page /Parent 4 0 R /Contents 2 0 R >>endobj\n" +
                    "4 0 obj<< /Type /Pages /Kids [3 0 R] /Count 1 >>endobj\n" +
                    "5 0 obj<< /Type /Catalog /Pages 4 0 R >>endobj\n" +
                    "trailer<< /Root 5 0 R >>\n" +
                    "%%EOF\n"
                ).toByteArray(Charsets.US_ASCII)
            val pad = when {
                name.contains("D1") -> ByteArray(300) { 0x44 }
                name.contains("D2") -> ByteArray(900) { 0x45 }
                name.contains("A") || name.contains("甲") -> ByteArray(2048) { 0x41 }
                name.contains("B") || name.contains("乙") -> ByteArray(512) { 0x42 }
                name.contains("C") || name.contains("丙") -> ByteArray(2048) { 0x41 } // same as A content pad
                else -> ByteArray(128) { 0x20 }
            }
            FileOutputStream(out).use {
                it.write(base)
                it.write(pad)
            }
            return out
        }
    }
}
