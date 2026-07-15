package com.markleaf.app.markdown

import android.content.Context
import com.markleaf.app.data.local.DocumentEntity
import com.markleaf.app.data.local.DocumentType
import com.markleaf.app.packageimport.ZipPackageImporter
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Explicit document base location for Markdown asset resolution (V1).
 * Must not collapse every source into a bare String path without capability meaning.
 *
 * URI values are stored as strings so pure resolution stays JVM-unit-testable
 * (android.net.Uri is not mocked under local unit tests).
 */
sealed class MarkdownDocumentBase {
    /** App-private package or library tree with a known filesystem root. */
    data class AppOwnedDirectory(
        val documentUri: String,
        val entryFile: File,
        val rootDir: File,
    ) : MarkdownDocumentBase()

    /** Readable file:// document whose parent (or package root) is the sandbox. */
    data class FileDirectory(
        val documentUri: String,
        val entryFile: File,
        val rootDir: File,
    ) : MarkdownDocumentBase()

    /**
     * SAF tree (or equivalent) with a verified accessible root.
     * V1 production does not invent tree grants; tests may construct this with a real root.
     */
    data class SafTreeDirectory(
        val documentUri: String,
        val treeUri: String,
        val rootDir: File,
    ) : MarkdownDocumentBase()

    /** Single-document content:// (or similar) grant — no directory capability. */
    data class SingleContentDocument(
        val documentUri: String,
    ) : MarkdownDocumentBase()

    data object Unknown : MarkdownDocumentBase()
}

sealed class MarkdownAssetResult {
    /** Absolute resource reference — do not rewrite. [uri] is the original string. */
    data class Absolute(val uri: String) : MarkdownAssetResult()

    /** Relative reference resolved to a loadable URI string (typically file://…). */
    data class Resolved(val uri: String) : MarkdownAssetResult()

    /** Capability or IO prevents access; UI should show controlled placeholder. */
    data class Inaccessible(val reason: MarkdownAssetFailure) : MarkdownAssetResult()

    /** Malformed reference or policy rejection. */
    data class Invalid(val reason: MarkdownAssetFailure) : MarkdownAssetResult()
}

enum class MarkdownAssetFailure {
    EMPTY,
    UNSUPPORTED_SCHEME,
    NOT_A_FILE,
    NOT_AN_IMAGE,
    OUTSIDE_ROOT,
    NOT_FOUND,
    SINGLE_FILE_GRANT,
    UNKNOWN_BASE,
    INVALID_PATH,
}

/**
 * Single entry for Markdown image/asset reference resolution.
 * Pure relative/absolute classification does not require Android services;
 * [fromDocument] needs [Context] only to classify app-owned paths.
 */
object MarkdownAssetResolver {

    private val IMAGE_EXTS = setOf("png", "jpg", "jpeg", "webp", "gif")

    private val ABSOLUTE_SCHEMES = setOf(
        "http",
        "https",
        "data",
        "file",
        "content",
        "android.resource",
        "asset",
    )

    /**
     * Build base location from a library document. No schema fields required.
     * Never fabricates sibling content:// URIs.
     */
    fun fromDocument(context: Context, document: DocumentEntity): MarkdownDocumentBase {
        return fromDocumentUri(
            context = context,
            documentUri = document.uri,
            documentType = document.type,
        )
    }

    fun fromDocumentUri(
        context: Context,
        documentUri: String?,
        documentType: DocumentType? = null,
    ): MarkdownDocumentBase {
        val raw = documentUri?.trim().orEmpty()
        if (raw.isEmpty()) return MarkdownDocumentBase.Unknown

        val scheme = extractScheme(raw).orEmpty()
        return when (scheme) {
            "file" -> classifyFileDocument(context, raw, documentType)
            "content" -> MarkdownDocumentBase.SingleContentDocument(raw)
            else -> MarkdownDocumentBase.Unknown
        }
    }

    private fun classifyFileDocument(
        context: Context,
        documentUri: String,
        documentType: DocumentType?,
    ): MarkdownDocumentBase {
        val path = filePathFromDocumentUri(documentUri)
            ?: return MarkdownDocumentBase.Unknown
        val entry = try {
            File(path).canonicalFile
        } catch (_: Exception) {
            return MarkdownDocumentBase.Unknown
        }
        val root = try {
            when (documentType) {
                DocumentType.MARKDOWN_PACKAGE -> ZipPackageImporter.findPackageRoot(entry)
                else -> detectAppOwnedRoot(context, entry) ?: ZipPackageImporter.findPackageRoot(entry)
            }.canonicalFile
        } catch (_: Exception) {
            entry.parentFile?.canonicalFile ?: return MarkdownDocumentBase.Unknown
        }

        val filesDir = try {
            context.applicationContext.filesDir.canonicalFile
        } catch (_: Exception) {
            null
        }
        val underApp = filesDir != null && ZipPackageImporter.isUnder(root, filesDir)
        return if (underApp) {
            MarkdownDocumentBase.AppOwnedDirectory(documentUri, entry, root)
        } else {
            MarkdownDocumentBase.FileDirectory(documentUri, entry, root)
        }
    }

    /**
     * packages/{id} or library/{sha} content directories under filesDir.
     */
    private fun detectAppOwnedRoot(context: Context, entry: File): File? {
        val filesDir = try {
            context.applicationContext.filesDir.canonicalFile
        } catch (_: Exception) {
            return null
        }
        val packages = File(filesDir, "packages")
        val library = File(filesDir, "library")
        val inbox = File(filesDir, "inbox")
        val entryCanon = try {
            entry.canonicalFile
        } catch (_: Exception) {
            return null
        }
        for (container in listOf(packages, library, inbox)) {
            if (!container.isDirectory) continue
            if (!ZipPackageImporter.isUnder(entryCanon, container)) continue
            if (container.name == "packages") {
                return ZipPackageImporter.findPackageRoot(entryCanon)
            }
            var dir = entryCanon.parentFile
            while (dir != null) {
                val parent = dir.parentFile
                if (parent != null && parent.canonicalFile == container.canonicalFile) {
                    return dir.canonicalFile
                }
                if (dir.canonicalFile == container.canonicalFile) break
                dir = parent
            }
        }
        return null
    }

    fun resolve(base: MarkdownDocumentBase, rawReference: String): MarkdownAssetResult {
        val trimmed = rawReference.trim()
        if (trimmed.isEmpty()) {
            return MarkdownAssetResult.Invalid(MarkdownAssetFailure.EMPTY)
        }

        absoluteUriStringOrNull(trimmed)?.let { abs ->
            return MarkdownAssetResult.Absolute(abs)
        }

        // Protocol-relative (//host/path) must never be treated as a relative filesystem path.
        if (trimmed.startsWith("//")) {
            return MarkdownAssetResult.Invalid(MarkdownAssetFailure.UNSUPPORTED_SCHEME)
        }

        // Unknown / dangerous schemes (javascript:, ftp://, …) → Invalid, never load.
        val scheme = extractScheme(trimmed)
        if (scheme != null && scheme !in ABSOLUTE_SCHEMES) {
            return MarkdownAssetResult.Invalid(MarkdownAssetFailure.UNSUPPORTED_SCHEME)
        }

        // Relative reference — capability gate first.
        when (base) {
            is MarkdownDocumentBase.SingleContentDocument ->
                return MarkdownAssetResult.Inaccessible(MarkdownAssetFailure.SINGLE_FILE_GRANT)
            MarkdownDocumentBase.Unknown ->
                return MarkdownAssetResult.Inaccessible(MarkdownAssetFailure.UNKNOWN_BASE)
            else -> Unit
        }

        val segments = normalizeRelativeSegments(trimmed)
            ?: return MarkdownAssetResult.Invalid(MarkdownAssetFailure.INVALID_PATH)

        val (entryFile, rootDir) = when (base) {
            is MarkdownDocumentBase.AppOwnedDirectory -> base.entryFile to base.rootDir
            is MarkdownDocumentBase.FileDirectory -> base.entryFile to base.rootDir
            is MarkdownDocumentBase.SafTreeDirectory -> null to base.rootDir
            else -> return MarkdownAssetResult.Inaccessible(MarkdownAssetFailure.UNKNOWN_BASE)
        }

        val startDir = when (base) {
            is MarkdownDocumentBase.AppOwnedDirectory,
            is MarkdownDocumentBase.FileDirectory,
            -> {
                val entry = entryFile
                    ?: return MarkdownAssetResult.Invalid(MarkdownAssetFailure.INVALID_PATH)
                try {
                    entry.canonicalFile.parentFile?.canonicalFile
                } catch (_: Exception) {
                    null
                } ?: return MarkdownAssetResult.Invalid(MarkdownAssetFailure.INVALID_PATH)
            }
            is MarkdownDocumentBase.SafTreeDirectory -> {
                try {
                    base.rootDir.canonicalFile
                } catch (_: Exception) {
                    return MarkdownAssetResult.Invalid(MarkdownAssetFailure.INVALID_PATH)
                }
            }
            else -> return MarkdownAssetResult.Inaccessible(MarkdownAssetFailure.UNKNOWN_BASE)
        }

        val target = try {
            resolveSegments(startDir, segments)
        } catch (_: Exception) {
            return MarkdownAssetResult.Invalid(MarkdownAssetFailure.INVALID_PATH)
        } ?: return MarkdownAssetResult.Invalid(MarkdownAssetFailure.INVALID_PATH)

        val rootCanon = try {
            rootDir.canonicalFile
        } catch (_: Exception) {
            return MarkdownAssetResult.Invalid(MarkdownAssetFailure.INVALID_PATH)
        }

        if (!ZipPackageImporter.isUnder(target, rootCanon)) {
            return MarkdownAssetResult.Invalid(MarkdownAssetFailure.OUTSIDE_ROOT)
        }
        if (!target.exists()) {
            return MarkdownAssetResult.Inaccessible(MarkdownAssetFailure.NOT_FOUND)
        }
        if (!target.isFile) {
            return MarkdownAssetResult.Invalid(MarkdownAssetFailure.NOT_A_FILE)
        }
        if (!target.canRead()) {
            return MarkdownAssetResult.Inaccessible(MarkdownAssetFailure.NOT_FOUND)
        }
        val ext = target.extension.lowercase(Locale.ROOT)
        if (ext !in IMAGE_EXTS) {
            return MarkdownAssetResult.Invalid(MarkdownAssetFailure.NOT_AN_IMAGE)
        }

        return MarkdownAssetResult.Resolved(fileToUriString(target))
    }

    /**
     * Absolute schemes must pass through unchanged.
     * Returns null when [raw] should be treated as relative.
     */
    fun absoluteUriStringOrNull(raw: String): String? {
        val t = raw.trim()
        if (t.isEmpty()) return null

        if (t.lowercase(Locale.ROOT).startsWith("asset://")) {
            return t
        }
        if (t.lowercase(Locale.ROOT).startsWith("data:")) {
            return t
        }

        val scheme = extractScheme(t) ?: return null
        if (scheme !in ABSOLUTE_SCHEMES) {
            return null
        }
        return t
    }

    private fun extractScheme(raw: String): String? {
        val colon = raw.indexOf(':')
        if (colon <= 0) return null
        val candidate = raw.substring(0, colon)
        if (!candidate.all { it.isLetterOrDigit() || it == '+' || it == '.' || it == '-' }) {
            return null
        }
        // Avoid treating single-letter drive-like prefixes as schemes
        if (candidate.length == 1 && candidate[0].isLetter()) return null
        return candidate.lowercase(Locale.ROOT)
    }

    /**
     * Single URL-decode pass, normalize `.` / `..`, reject empty / schemes / absolute.
     * @return path segments or null if invalid
     */
    fun normalizeRelativeSegments(raw: String): List<String>? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        if (absoluteUriStringOrNull(trimmed) != null) return null
        if (trimmed.startsWith("//")) return null

        val scheme = extractScheme(trimmed)
        if (scheme != null && scheme !in ABSOLUTE_SCHEMES) {
            return null
        }
        if (scheme != null && scheme in ABSOLUTE_SCHEMES) {
            return null
        }

        // One percent-decode only (does not treat '+' as space).
        // Strip query/fragment so they never participate in filesystem location.
        val onceDecoded = percentDecodeOnce(trimmed).replace('\\', '/')
        val pathOnly = onceDecoded
            .substringBefore('#')
            .substringBefore('?')
            .trim()
        if (pathOnly.isEmpty()) return null
        if (pathOnly.startsWith("/")) return null
        if (pathOnly.contains("://")) return null
        if (pathOnly.startsWith("//")) return null

        val parts = pathOnly.split('/')
        val out = ArrayList<String>(parts.size)
        for (p in parts) {
            when {
                p.isEmpty() || p == "." -> Unit
                p == ".." -> {
                    if (out.isEmpty()) {
                        out.add("..")
                    } else if (out.last() == "..") {
                        out.add("..")
                    } else {
                        out.removeAt(out.lastIndex)
                    }
                }
                else -> {
                    // Reject NUL and residual encoded separators that would re-split after decode.
                    if (p.contains('\u0000')) return null
                    out.add(p)
                }
            }
        }
        if (out.isEmpty()) return null
        return out
    }

    /**
     * Single-pass percent-decoding for path references.
     * Unlike [java.net.URLDecoder], does **not** convert `+` to space (form-encoding).
     * Leaves invalid `%` sequences unchanged. Does not decode twice.
     */
    fun percentDecodeOnce(raw: String): String {
        if (!raw.contains('%')) return raw
        val bytes = ByteArrayOutputStream(raw.length)
        var i = 0
        val s = raw
        while (i < s.length) {
            val c = s[i]
            if (c == '%' && i + 2 < s.length) {
                val hi = Character.digit(s[i + 1], 16)
                val lo = Character.digit(s[i + 2], 16)
                if (hi >= 0 && lo >= 0) {
                    bytes.write((hi shl 4) + lo)
                    i += 3
                    continue
                }
            }
            // Preserve as UTF-8 bytes of the code unit sequence already in the string.
            val utf = c.toString().toByteArray(StandardCharsets.UTF_8)
            bytes.write(utf, 0, utf.size)
            i++
        }
        return String(bytes.toByteArray(), StandardCharsets.UTF_8)
    }

    private fun resolveSegments(baseDir: File, segments: List<String>): File? {
        var current = baseDir.canonicalFile
        for (seg in segments) {
            current = File(current, seg)
        }
        return try {
            current.canonicalFile
        } catch (_: Exception) {
            null
        }
    }

    /**
     * file:// URI string without android.net.Uri (JVM-safe).
     * Uses [File.toURI] so reserved characters in filenames (space, `%`, …) are
     * percent-encoded once; [filePathFromDocumentUri] applies a single decode.
     */
    fun fileToUriString(file: File): String {
        val f = try {
            file.canonicalFile
        } catch (_: Exception) {
            file.absoluteFile
        }
        return f.toURI().toString()
    }

    /**
     * Extract filesystem path from a file:// document URI string (JVM-safe).
     */
    fun filePathFromDocumentUri(documentUri: String): String? {
        val t = documentUri.trim()
        if (!t.lowercase(Locale.ROOT).startsWith("file:")) return null
        var p = when {
            t.startsWith("file://") -> t.removePrefix("file://")
            t.startsWith("file:") -> t.removePrefix("file:")
            else -> return null
        }
        p = percentDecodeOnce(p)
        if (p.isEmpty()) return null
        return p
    }

    fun userMessage(result: MarkdownAssetResult): String {
        return when (result) {
            is MarkdownAssetResult.Absolute,
            is MarkdownAssetResult.Resolved,
            -> ""
            is MarkdownAssetResult.Inaccessible -> when (result.reason) {
                MarkdownAssetFailure.SINGLE_FILE_GRANT ->
                    "无法访问相对图片\n当前仅授权了 Markdown 文件，未授权图片所在目录"
                MarkdownAssetFailure.UNKNOWN_BASE ->
                    "无法访问相对图片"
                MarkdownAssetFailure.NOT_FOUND ->
                    "无法访问相对图片"
                else -> "无法访问相对图片"
            }
            is MarkdownAssetResult.Invalid -> when (result.reason) {
                MarkdownAssetFailure.OUTSIDE_ROOT -> "无法访问相对图片"
                MarkdownAssetFailure.NOT_AN_IMAGE -> "无法访问相对图片"
                MarkdownAssetFailure.EMPTY -> "图片地址为空"
                else -> "无法访问相对图片"
            }
        }
    }
}
