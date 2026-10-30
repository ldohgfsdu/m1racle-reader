package com.m1racle.reader.packageimport

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Rebuild only packages whose original ZIP was not retained by older imports. */
internal suspend fun rebuildMarkdownPackage(root: File, entry: File, output: File) {
    val canonicalRoot = root.canonicalFile
    require(canonicalRoot.isDirectory && ZipPackageImporter.isUnder(entry.canonicalFile, canonicalRoot)) {
        "教材包资源目录不可用"
    }
    require(!ZipPackageImporter.isUnder(output.canonicalFile, canonicalRoot))
    var count = 0
    var total = 0L
    try {
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            val buffer = ByteArray(64 * 1024)
            canonicalRoot.walkTopDown().forEach { file ->
                currentCoroutineContext().ensureActive()
                if (file == canonicalRoot) return@forEach
                require(!Files.isSymbolicLink(file.toPath())) { "教材包包含链接资源，未导出" }
                require(ZipPackageImporter.isUnder(file.canonicalFile, canonicalRoot)) { "教材包包含越界资源" }
                require(file.isFile || file.isDirectory) { "教材包包含不可读取的资源" }
                // Parents are recreated by extraction. Only empty directories need entries,
                // otherwise implicit parents could push a previously valid ZIP over its limit.
                if (file.isDirectory && !file.list().isNullOrEmpty()) return@forEach
                require(++count <= ZipPackageImporter.MAX_ENTRY_COUNT) { "教材包文件数量超限" }
                val relative = file.relativeTo(canonicalRoot).invariantSeparatorsPath
                zip.putNextEntry(ZipEntry(relative + if (file.isDirectory) "/" else ""))
                if (file.isFile) {
                    var bytes = 0L
                    file.inputStream().use { input ->
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            bytes += read
                            total += read
                            require(bytes <= ZipPackageImporter.MAX_SINGLE_ENTRY_BYTES &&
                                total <= ZipPackageImporter.MAX_TOTAL_UNCOMPRESSED_BYTES) { "教材包资源体积超限" }
                            zip.write(buffer, 0, read)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
    } catch (failure: Exception) {
        output.delete()
        throw failure
    }
}
