package com.markleaf.app.data.local

import java.util.Locale

/**
 * High-level document kind for library list + navigation.
 * Stored as TEXT in Room; Markdown is the default for pre-migration rows.
 */
enum class DocumentType {
    MARKDOWN,
    PDF,
    /** ZIP textbook package (entry Markdown under packages/). Navigates as Markdown. */
    MARKDOWN_PACKAGE,
    ;

    companion object {
        const val MIME_PDF = "application/pdf"

        fun fromStorage(value: String?): DocumentType {
            return when (value?.uppercase(Locale.ROOT)) {
                PDF.name -> PDF
                MARKDOWN_PACKAGE.name -> MARKDOWN_PACKAGE
                else -> MARKDOWN
            }
        }

        /**
         * Resolve type from SAF mime and/or file name.
         * PDF only when mime or extension clearly indicates PDF.
         * Non-PDF never becomes PDF by accident.
         */
        fun resolve(mimeType: String?, displayName: String?, pathSegment: String? = null): DocumentType {
            val mime = mimeType?.lowercase(Locale.ROOT)?.trim().orEmpty()
            if (mime == MIME_PDF || mime == "application/x-pdf") {
                return PDF
            }
            if (hasPdfExtension(displayName) || hasPdfExtension(pathSegment)) {
                // Some providers return octet-stream / empty mime for PDFs.
                if (mime.isEmpty() ||
                    mime == "application/octet-stream" ||
                    mime.startsWith("application/")
                ) {
                    return PDF
                }
            }
            return MARKDOWN
        }

        fun isAllowedImport(mimeType: String?, displayName: String?, pathSegment: String? = null): Boolean {
            if (isMarkdownFileName(displayName) || isMarkdownFileName(pathSegment)) {
                return true
            }
            if (isZipFileName(displayName) || isZipFileName(pathSegment) || isZipMime(mimeType)) {
                return true
            }
            if (resolve(mimeType, displayName, pathSegment) == PDF) {
                return true
            }
            // text/* without known extension still rejected (existing strict import).
            return false
        }

        fun isZipFileName(name: String?): Boolean {
            if (name.isNullOrBlank()) return false
            return name.lowercase(Locale.ROOT).endsWith(".zip")
        }

        fun isZipMime(mimeType: String?): Boolean {
            val mime = mimeType?.lowercase(Locale.ROOT)?.trim().orEmpty()
            return mime == "application/zip" ||
                mime == "application/x-zip-compressed" ||
                mime == "application/x-zip"
        }

        fun isMarkdownFileName(name: String?): Boolean {
            if (name.isNullOrBlank()) return false
            val lower = name.lowercase(Locale.ROOT)
            return lower.endsWith(".md") ||
                lower.endsWith(".markdown") ||
                lower.endsWith(".txt")
        }

        fun hasPdfExtension(name: String?): Boolean {
            if (name.isNullOrBlank()) return false
            return name.lowercase(Locale.ROOT).endsWith(".pdf")
        }
    }
}
