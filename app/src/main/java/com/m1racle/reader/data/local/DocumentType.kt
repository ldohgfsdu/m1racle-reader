package com.m1racle.reader.data.local

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
         * THE single import-acceptance authority (policy unification, import
         * policy batch 2). Both live entries — DocumentRepository.importDocument
         * (FAB/SAF) and ExternalOpenCoordinator / ExternalDocumentImporter.prepare
         * (VIEW/SEND) — gate through this function and nothing else.
         *
         * Accepted content, in order:
         * 1. Markdown/text extension (.md/.markdown/.txt) on either name;
         * 2. ZIP package (extension on either name or zip mime);
         * 3. PDF (pdf mime or .pdf extension via [resolve]);
         * 4. Text-mime fallback ([isTextMimeFallback]): any provider mime in the
         *    text family with no obviously-non-document extension enters as
         *    readable text — extensionless names are normalized to ".txt"
         *    downstream by ExternalUriMetadata.ensureExtension.
         *
         * Everything else is rejected; there is deliberately no generic mime
         * whitelist any more (the former ExternalFileIntake.ALLOWED_MIMES path).
         */
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
            return isTextMimeFallback(mimeType, displayName, pathSegment)
        }

        /**
         * Text-mime fallback sub-rule of [isAllowedImport]:
         * - text-family mime + document extension → already accepted by branch 1;
         * - text-family mime + no extension at all → accepted (plain text);
         * - text-family mime + an obviously non-document extension
         *   (media/archive/office binaries, see [NON_DOCUMENT_EXTENSIONS]) →
         *   rejected so e.g. a JPEG shared with a text mime can never enter the
         *   library as a mojibake document.
         */
        internal fun isTextMimeFallback(
            mimeType: String?,
            displayName: String?,
            pathSegment: String?,
        ): Boolean {
            val mime = mimeType?.lowercase(Locale.ROOT)?.trim().orEmpty()
            if (!mime.startsWith("text/")) return false
            return !hasNonDocumentExtension(displayName) && !hasNonDocumentExtension(pathSegment)
        }

        /** Extensions that mark content as definitely not reader-readable text. */
        private val NON_DOCUMENT_EXTENSIONS = setOf(
            // raster / vector images
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif", "tiff", "ico", "psd",
            // audio / video
            "mp4", "mov", "avi", "mkv", "webm", "flv", "wmv",
            "mp3", "wav", "ogg", "flac", "m4a", "aac", "wma",
            // archives / disk / executables
            "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "dmg", "apk", "exe", "msi", "bin",
            // office suites
            "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp",
            // third-party ebook containers
            "epub", "mobi", "azw3",
        )

        fun hasNonDocumentExtension(name: String?): Boolean {
            if (name.isNullOrBlank()) return false
            val trimmed = name.trim()
            val dot = trimmed.lastIndexOf('.')
            if (dot < 0 || dot == trimmed.length - 1) return false
            val ext = trimmed.substring(dot + 1).lowercase(Locale.ROOT)
            return ext in NON_DOCUMENT_EXTENSIONS
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
                // Provider MIME is frequently generic or wrong. A concrete .pdf
                // display/path name is stronger file identity than text/plain.
                return PDF
            }
            return MARKDOWN
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
