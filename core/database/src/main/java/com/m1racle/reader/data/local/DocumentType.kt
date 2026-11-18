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
    /** Historical only: the EPUB reader was retired. Stored rows keep this value. */
    EPUB,
    ;

    companion object {
        const val MIME_PDF = "application/pdf"

        /**
         * The MIME of a retired reading format. It is not a type this app can open,
         * but it still carries a claim: a file announced as EPUB is not a PDF even
         * when its name says `.pdf`. See [isAllowedImport] rule 2.
         */
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
         * PDF only:
         * 1. A name whose extension is not .pdf (.epub, .md, .txt, .zip, .jpg, …) is
         *    refused even when the MIME says application/pdf, so a contradictory
         *    extension cannot slip through.
         * 2. A name ending in .pdf is admitted under any MIME except
         *    application/epub+zip. That MIME names another reading format, so a .pdf
         *    name plus an EPUB MIME is two explicit claims that disagree and neither
         *    is trusted. A generic container MIME (application/zip,
         *    application/octet-stream) makes no claim about format, so a mislabelled
         *    PDF still enters.
         * 3. A name with no extension — including a trailing-dot name like "book." —
         *    is admitted only when the MIME itself is PDF.
         *
         * Admission is not verification: the PDF open and parse path decides whether
         * the bytes are readable.
         *
         * Markdown, plain text, ZIP packages and EPUB are not imported. Existing
         * rows keep their stored documentType; this function does not rewrite or
         * delete them.
         */
        fun isAllowedImport(mimeType: String?, displayName: String?, pathSegment: String? = null): Boolean {
            if (isRetiredEbookMime(mimeType)) return false
            if (hasNonPdfExtension(displayName) || hasNonPdfExtension(pathSegment)) return false
            return resolve(mimeType, displayName, pathSegment) == PDF
        }

        /**
         * Rule 2 of [isAllowedImport], kept separate from [resolve] on purpose:
         * resolve() answers "which type is this", and after the EPUB retirement no
         * answer of its can be EPUB. Refusing a contradictory EPUB claim is an
         * admission rule, so it lives in the gate.
         */
        internal fun isRetiredEbookMime(mimeType: String?): Boolean {
            val mime = mimeType?.lowercase(Locale.ROOT)?.trim().orEmpty()
            return mime == MIME_EPUB
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
            // third-party ebook containers (no ebook format is read any more)
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
         *
         * PDF only when mime or extension clearly indicates PDF. Everything else
         * resolves to [MARKDOWN]. The return value is a classification, never an
         * admission: [isAllowedImport] applies the extra rules, including refusing
         * a `.pdf` name announced under an EPUB MIME — that pair resolves to PDF
         * here and is still refused there. Historical EPUB and Markdown rows never
         * travel through this function; their type comes from [fromStorage].
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
