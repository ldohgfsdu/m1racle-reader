package com.m1racle.reader.packageimport

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

/**
 * Safe textbook ZIP extract + entrypoint resolution + package-relative image paths.
 */
object ZipPackageImporter {

    private val ENTRY_CANDIDATES = listOf(
        "index.md",
        "README.md",
        "readme.md",
        "main.md",
    )

    private val IMAGE_EXTS = setOf("png", "jpg", "jpeg", "webp")

    /** Hard caps against zip bombs / resource exhaustion (host-testable). */
    const val MAX_ENTRY_COUNT: Int = 5_000
    const val MAX_TOTAL_UNCOMPRESSED_BYTES: Long = 200L * 1024L * 1024L // 200 MiB
    const val MAX_SINGLE_ENTRY_BYTES: Long = 80L * 1024L * 1024L // 80 MiB
    const val MAX_MARKDOWN_BYTES: Int = 32 * 1024 * 1024
    const val MAX_MANIFEST_BYTES: Int = 64 * 1024
    internal const val MAX_TITLE_PREFIX_CHARS: Int = 16 * 1024

    data class ExtractResult(
        val packageRoot: File,
        val entryFile: File,
        val entryRelativePath: String,
    )

    /**
     * Extract [zipStream] into [destDir] (created/cleared by caller as needed).
     * @throws SecurityException on Zip Slip or size-limit violation
     * @throws IllegalArgumentException if no Markdown entry
     */
    fun extract(zipStream: InputStream, destDir: File): ExtractResult {
        if (destDir.exists()) {
            destDir.deleteRecursively()
        }
        destDir.mkdirs()
        val rootCanon = destDir.canonicalFile
        var entryCount = 0
        var totalBytes = 0L

        ZipInputStream(zipStream.buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name.replace('\\', '/')
                entryCount++
                if (entryCount > MAX_ENTRY_COUNT) {
                    throw SecurityException("ZIP entry count exceeds limit ($MAX_ENTRY_COUNT)")
                }
                val remaining = minOf(MAX_SINGLE_ENTRY_BYTES, MAX_TOTAL_UNCOMPRESSED_BYTES - totalBytes)
                if (name.startsWith("__MACOSX/") || name.endsWith(".DS_Store")) {
                    // closeEntry drains/inflates data too. Consume it through the
                    // same limits as stored files before advancing the stream.
                    totalBytes += copyEntry(zis, null, remaining)
                    zis.closeEntry()
                    entry = zis.nextEntry
                    continue
                }
                val cleaned = name.trimStart('/')
                if (cleaned.isEmpty() || cleaned.contains("..")) {
                    throw SecurityException("Zip Slip rejected: $name")
                }
                val outFile = File(destDir, cleaned).canonicalFile
                if (!isUnder(outFile, rootCanon) && outFile.path != rootCanon.path) {
                    throw SecurityException("Zip Slip rejected: $name")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                    totalBytes += copyEntry(zis, null, remaining)
                } else {
                    outFile.parentFile?.mkdirs()
                    totalBytes += copyWithLimit(zis, outFile, remaining)
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val entry = resolveEntrypoint(destDir)
            ?: throw IllegalArgumentException("ZIP 中未找到 Markdown 入口文件")
        require(entry.length() <= MAX_MARKDOWN_BYTES) { "Markdown 入口超过 32 MiB 上限" }
        val rel = entry.relativeTo(destDir).path.replace('\\', '/')
        return ExtractResult(packageRoot = destDir.canonicalFile, entryFile = entry.canonicalFile, entryRelativePath = rel)
    }

    /**
     * Copy stream to [outFile] aborting if more than [maxBytes] are written.
     * @return bytes written
     */
    internal fun copyWithLimit(input: InputStream, outFile: File, maxBytes: Long): Long =
        outFile.outputStream().use { copyEntry(input, it, maxBytes) }

    private fun copyEntry(input: InputStream, output: OutputStream?, maxBytes: Long): Long {
        var written = 0L
        val buf = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            written += n
            if (written > maxBytes) {
                throw SecurityException("ZIP entry or total exceeds size limit ($maxBytes)")
            }
            output?.write(buf, 0, n)
        }
        return written
    }

    fun resolveEntrypoint(packageRoot: File): File? {
        val root = packageRoot.canonicalFile
        // 1) package-manifest.json "entrypoint"
        readManifestEntrypoint(root)?.let { return it }
        // 2) well-known names
        for (c in ENTRY_CANDIDATES) {
            val f = File(root, c)
            if (f.isFile && f.canRead()) return f.canonicalFile
        }
        // 3) shallowest Markdown
        val mds = root.walkTopDown()
            .filter { it.isFile && isMarkdownFile(it) && it.canRead() }
            .sortedBy { it.toRelativeString(root).count { ch -> ch == '/' || ch == '\\' } }
            .toList()
        return mds.firstOrNull()?.canonicalFile
    }

    /**
     * Read "entrypoint" from package-manifest.json without a JSON library.
     * Path must stay under [packageRoot], be readable md/markdown.
     */
    fun readManifestEntrypoint(packageRoot: File): File? {
        val manifest = File(packageRoot, "package-manifest.json")
        if (!manifest.isFile || !manifest.canRead()) return null
        require(manifest.length() <= MAX_MANIFEST_BYTES) { "教材包 manifest 超过 64 KiB 上限" }
        val text = try {
            manifest.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            return null
        }
        val match = Regex(""""entrypoint"\s*:\s*"([^"]+)"""").find(text) ?: return null
        val raw = match.groupValues[1].trim().replace('\\', '/')
        if (raw.isEmpty() || raw.contains("..") || raw.startsWith("/")) return null
        val candidate = File(packageRoot, raw)
        return try {
            val root = packageRoot.canonicalFile
            val canon = candidate.canonicalFile
            if (!isUnder(canon, root) && canon.path != root.path) return null
            if (!canon.isFile || !canon.canRead()) return null
            if (!isMarkdownFile(canon)) return null
            canon
        } catch (_: Exception) {
            null
        }
    }

    private fun isMarkdownFile(file: File): Boolean {
        val ext = file.extension.lowercase()
        return ext == "md" || ext == "markdown"
    }

    fun isZipFileName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        return name.lowercase().endsWith(".zip")
    }

    /**
     * Nearest ancestor containing package-manifest.json, else entry's parent.
     */
    fun findPackageRoot(entryFile: File): File {
        var dir: File? = entryFile.canonicalFile.parentFile
        while (dir != null) {
            if (File(dir, "package-manifest.json").isFile) {
                return dir.canonicalFile
            }
            dir = dir.parentFile
        }
        return entryFile.canonicalFile.parentFile
            ?: entryFile.canonicalFile
    }

    /**
     * Resolve package-relative image path against the Markdown entry file.
     * Accepts `assets/a.png` and `./assets/a.png`. Rejects traversal and schemes.
     */
    fun resolvePackageImage(
        entryFile: File,
        relativePath: String,
        packageRoot: File? = null,
    ): File? {
        val decoded = try {
            URLDecoder.decode(relativePath.trim(), StandardCharsets.UTF_8.name())
        } catch (_: Exception) {
            relativePath.trim()
        }.replace('\\', '/')

        var rel = decoded
        while (rel.startsWith("./")) {
            rel = rel.removePrefix("./")
        }
        if (rel.isEmpty()) return null
        // Reject absolute, schemes, parent traversal
        if (rel.startsWith("/")) return null
        val lower = rel.lowercase()
        if (lower.startsWith("file:") || lower.startsWith("content:") ||
            lower.startsWith("http:") || lower.startsWith("https:") ||
            lower.startsWith("data:") || lower.startsWith("javascript:")
        ) {
            return null
        }
        if (rel.contains("://")) return null
        if (rel.split('/').any { it == ".." || it.isEmpty() && rel != rel.trim('/') }) {
            // empty segment from // — allow single empty only if not traversal
        }
        if (rel.split('/').any { it == ".." }) return null

        val entryCanon = entryFile.canonicalFile
        val baseDir = entryCanon.parentFile?.canonicalFile ?: return null
        val root = (packageRoot?.canonicalFile) ?: findPackageRoot(entryCanon)
        val target = File(baseDir, rel).canonicalFile

        if (!target.isFile || !target.canRead()) return null
        if (!isUnder(target, root)) return null
        val ext = target.extension.lowercase()
        if (ext !in IMAGE_EXTS) return null
        return target
    }

    /** @deprecated use [resolvePackageImage] */
    fun resolveRelativeFile(documentFile: File, relativePath: String): File? =
        resolvePackageImage(documentFile, relativePath, null)

    fun isUnder(child: File, root: File): Boolean {
        val c = child.canonicalFile.path
        val r = root.canonicalFile.path
        return c == r || c.startsWith(r + File.separator)
    }

    /** Sum sizes of regular files under package root. */
    fun packageTotalSize(packageRoot: File): Long {
        if (!packageRoot.isDirectory) return 0L
        return packageRoot.walkTopDown()
            .filter { it.isFile }
            .map { it.length() }
            .sum()
    }

    /** First ATX H1 plain text, or null. */
    fun extractFirstH1(markdownFile: File): String? = markdownFile.reader(Charsets.UTF_8).use { reader ->
        val prefix = CharArray(MAX_TITLE_PREFIX_CHARS)
        var count = 0
        while (count < prefix.size) {
            val n = reader.read(prefix, count, prefix.size - count)
            if (n < 0) break
            count += n
        }
        extractFirstH1(String(prefix, 0, count))
    }

    fun extractFirstH1(markdown: String): String? {
        for (line in markdown.lineSequence()) {
            val t = line.trim()
            if (t.startsWith("# ") && !t.startsWith("##")) {
                return t.removePrefix("#").trim().ifBlank { null }
            }
        }
        return null
    }
}
