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
    /** Local reflowable EPUB (limited reading). Navigates to the EPUB reader. */
    EPUB,
    ;

    companion object {
        const val MIME_PDF = "application/pdf"
        const val MIME_EPUB = "application/epub+zip"

        fun fromStorage(value: String?): DocumentType {
            return when (value?.uppercase(Locale.ROOT)) {
                PDF.name -> PDF
                MARKDOWN_PACKAGE.name -> MARKDOWN_PACKAGE
                EPUB.name -> EPUB
                else -> MARKDOWN
            }
        }

        /**
         * The only import gate. Both live entries — DocumentRepository.importDocument
         * (FAB/SAF) and ExternalDocumentImporter.prepare (VIEW/SEND) — call this and
         * nothing else.
         *
         * PDF only. A name ending in .pdf is admitted under any MIME, because
         * providers often mislabel real PDFs. A name ending in anything else
         * (.epub, .md, .txt, .zip, .jpg, …) is rejected even when the MIME says
         * application/pdf, so a contradictory extension cannot slip through.
         * Extensionless names are admitted only when the MIME itself is PDF.
         *
         * Markdown, plain text, ZIP packages and EPUB are not imported. Existing
         * rows keep their stored documentType; this function does not rewrite or
         * delete them.
         */
        fun isAllowedImport(mimeType: String?, displayName: String?, pathSegment: String? = null): Boolean {
            if (hasNonPdfExtension(displayName) || hasNonPdfExtension(pathSegment)) return false
            return resolve(mimeType, displayName, pathSegment) == PDF
        }

        /** True when [name] has an extension and that extension is not pdf. */
        internal fun hasNonPdfExtension(name: String?): Boolean {
            if (name.isNullOrBlank()) return false
            val trimmed = name.trim()
            val dot = trimmed.lastIndexOf('.')
            if (dot <= 0 || dot == trimmed.length - 1) return false
            return !trimmed.endsWith(".pdf", ignoreCase = true)
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
            // third-party ebook containers (EPUB is supported; the rest are rejected)
            "mobi", "azw3",
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
         * PDF only when mime or extension clearly indicates PDF;
         * EPUB only when mime or extension clearly indicates EPUB.
         * Non-PDF never becomes PDF by accident.
         */
        fun resolve(mimeType: String?, displayName: String?, pathSegment: String? = null): DocumentType {
            val mime = mimeType?.lowercase(Locale.ROOT)?.trim().orEmpty()
            if (mime == MIME_PDF || mime == "application/x-pdf") {
                return PDF
            }
            if (mime == MIME_EPUB) {
                return EPUB
            }
            if (hasPdfExtension(displayName) || hasPdfExtension(pathSegment)) {
                // Provider MIME is frequently generic or wrong. A concrete .pdf
                // display/path name is stronger file identity than text/plain.
                return PDF
            }
            if (isEpubFileName(displayName) || isEpubFileName(pathSegment)) {
                return EPUB
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

        fun isEpubFileName(name: String?): Boolean {
            if (name.isNullOrBlank()) return false
            return name.lowercase(Locale.ROOT).endsWith(".epub")
        }
    }
}
