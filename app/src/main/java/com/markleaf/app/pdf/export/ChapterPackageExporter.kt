package com.markleaf.app.pdf.export

import android.content.Context
import android.net.Uri
import android.util.Log
import com.markleaf.app.data.local.PdfPrintPageAnchorEntity
import com.markleaf.app.data.local.TextbookOutlineNodeEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext

sealed class ChapterExportState {
    data object Preparing : ChapterExportState()
    data class CopyingPages(val current: Int, val total: Int) : ChapterExportState()
    data object BuildingMetadata : ChapterExportState()
    data object WritingArchive : ChapterExportState()
    data class Completed(val displayName: String) : ChapterExportState()
    data object Cancelled : ChapterExportState()
    data class Failed(val message: String) : ChapterExportState()
}

/**
 * Builds medlearn-source-chapter ZIP packages offline.
 * Source PDF is opened read-only; original file is never modified.
 */
class ChapterPackageExporter(
    private val context: Context,
) {
    private val appContext = context.applicationContext

    suspend fun export(
        sourceUri: Uri,
        documentId: String,
        documentDisplayName: String,
        plan: ExportRangePlan,
        allNodes: List<TextbookOutlineNodeEntity>,
        printAnchors: List<PdfPrintPageAnchorEntity>,
        outputUri: Uri,
        cancellation: ExportCancellationToken,
        onState: (ChapterExportState) -> Unit,
    ): ChapterExportState = withContext(Dispatchers.IO) {
        val workDir = File(appContext.cacheDir, "chapter_export_${System.currentTimeMillis()}")
        var destinationCommitted = false
        try {
            onState(ChapterExportState.Preparing)
            if (!workDir.mkdirs()) {
                return@withContext fail("无法创建临时目录", workDir, onState)
            }
            throwIfExportCancelled(cancellation) { coroutineContext.isActive }
            val err = ExportRangeResolver.validateRange(
                plan.startPageIndex,
                plan.endPageIndex,
                // loose upper bound; real check in extractor
                plan.endPageIndex + 1,
            )
            // re-validate with open document later

            val sourcePdf = File(workDir, "source.pdf")
            val manifestFile = File(workDir, "manifest.json")
            val outlineFile = File(workDir, "outline.json")
            val pageMapFile = File(workDir, "page_map.json")
            val checksumsFile = File(workDir, "checksums.json")

            // Hash source while streaming pages from content resolver
            val sourceSha = MessageDigest.getInstance("SHA-256")
            val input = appContext.contentResolver.openInputStream(sourceUri)
                ?: return@withContext fail("无法打开源 PDF", workDir, onState)

            // Copy source stream to temp for random-access extraction? PDFBox needs full stream.
            // Use temp file of source only if needed — PDDocument.load from stream once.
            // We pass stream to extractor; also compute sha of original bytes via tee.
            val sourceTemp = File(workDir, "_source_full.tmp")
            try {
                input.use { src ->
                    FileOutputStream(sourceTemp).use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            throwIfExportCancelled(cancellation) { coroutineContext.isActive }
                            val n = src.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            sourceSha.update(buf, 0, n)
                        }
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                return@withContext fail(
                    t.message ?: "读取源 PDF 失败",
                    workDir,
                    onState,
                )
            }
            val sourceShaHex = sourceSha.digest().toHex()

            throwIfExportCancelled(cancellation) { coroutineContext.isActive }

            val extract = FileInputStream(sourceTemp).use { srcIn ->
                PdfPageExtractor.extractPageRange(
                    context = appContext,
                    sourceStream = srcIn,
                    outputFile = sourcePdf,
                    startIndex0 = plan.startPageIndex,
                    endIndex0 = plan.endPageIndex,
                    onProgress = { cur, tot ->
                        onState(ChapterExportState.CopyingPages(cur, tot))
                    },
                    isCancelled = { cancellation.isCancelled() || !coroutineContext.isActive },
                )
            }

            when (extract) {
                is PdfPageExtractor.ExtractResult.Cancelled ->
                    throw CancellationException("Export cancelled")
                is PdfPageExtractor.ExtractResult.Failed ->
                    return@withContext fail(extract.message, workDir, onState)
                is PdfPageExtractor.ExtractResult.Success -> {
                    if (extract.pageCount != plan.pageCount) {
                        return@withContext fail(
                            "页数不一致: plan=${plan.pageCount}, pdf=${extract.pageCount}",
                            workDir,
                            onState,
                        )
                    }
                }
            }

            // Drop full source temp ASAP (do not pack into ZIP)
            sourceTemp.delete()

            throwIfExportCancelled(cancellation) { coroutineContext.isActive }
            onState(ChapterExportState.BuildingMetadata)

            val iso = isoNow()
            val outlineJson = ChapterPackageMetadata.outlineJson(plan, allNodes)
            val pageMapJson = ChapterPackageMetadata.pageMapJson(plan, printAnchors)
            val manifestJson = ChapterPackageMetadata.manifestJson(
                documentId = documentId,
                documentDisplayName = documentDisplayName,
                sourceSha256 = sourceShaHex,
                plan = plan,
                createdAtIso = iso,
            )

            // Validate outline pages in range
            if (!validateOutlineJsonPages(outlineJson, plan.pageCount)) {
                return@withContext fail("outline.json 页码超出导出范围", workDir, onState)
            }

            manifestFile.writeText(manifestJson, Charsets.UTF_8)
            outlineFile.writeText(outlineJson, Charsets.UTF_8)
            pageMapFile.writeText(pageMapJson, Charsets.UTF_8)

            // Spec: checksums.json covers the other four package files only
            // (not itself — self-hash would be invalid after write).
            val hashes = linkedMapOf(
                "source.pdf" to sha256File(sourcePdf),
                "manifest.json" to sha256File(manifestFile),
                "outline.json" to sha256File(outlineFile),
                "page_map.json" to sha256File(pageMapFile),
            )
            checksumsFile.writeText(
                ChapterPackageMetadata.checksumsJson(hashes),
                Charsets.UTF_8,
            )

            throwIfExportCancelled(cancellation) { coroutineContext.isActive }
            onState(ChapterExportState.WritingArchive)

            val zipTmp = File(workDir, "package.zip")
            zipFiles(
                zipTmp,
                listOf(
                    sourcePdf to "source.pdf",
                    manifestFile to "manifest.json",
                    outlineFile to "outline.json",
                    pageMapFile to "page_map.json",
                    checksumsFile to "checksums.json",
                ),
                cancellation,
            ) { coroutineContext.isActive }

            throwIfExportCancelled(cancellation) { coroutineContext.isActive }

            // A successful stream close commits the destination. Any cancellation
            // before this boundary is handled by the outer cleanup path.
            appContext.contentResolver.openOutputStream(outputUri)?.use { out ->
                FileInputStream(zipTmp).use { inp ->
                    copyWithExportCancellation(inp, out, cancellation) { coroutineContext.isActive }
                }
            } ?: return@withContext fail("无法写入所选位置", workDir, onState)
            destinationCommitted = true

            val done = ChapterExportState.Completed(
                displayName = ExportRangeResolver.defaultZipName(
                    documentDisplayName,
                    plan.pathTitles,
                ),
            )
            onState(done)
            cleanupDir(workDir)
            done
        } catch (ce: CancellationException) {
            if (!destinationCommitted) {
                deleteSafDocumentBestEffort(appContext.contentResolver, outputUri)
            }
            cancel(workDir, onState)
            throw ce
        } catch (t: Throwable) {
            Log.e("ChapterExport", "export failed", t)
            fail(t.message ?: "导出失败", workDir, onState)
        }
    }

    private fun fail(
        msg: String,
        workDir: File,
        onState: (ChapterExportState) -> Unit,
    ): ChapterExportState {
        cleanupDir(workDir)
        val s = ChapterExportState.Failed(msg)
        onState(s)
        return s
    }

    private fun cancel(
        workDir: File,
        onState: (ChapterExportState) -> Unit,
    ): ChapterExportState {
        cleanupDir(workDir)
        onState(ChapterExportState.Cancelled)
        return ChapterExportState.Cancelled
    }

    companion object {
        private const val TAG = "ChapterExport"

        fun cleanupDir(dir: File?) {
            if (dir == null || !dir.exists()) return
            dir.listFiles()?.forEach { f ->
                try {
                    if (f.isDirectory) cleanupDir(f) else f.delete()
                } catch (_: Exception) {
                }
            }
            try {
                dir.delete()
            } catch (_: Exception) {
            }
        }

        fun sha256File(file: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { inp ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = inp.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                }
            }
            return md.digest().toHex()
        }

        fun ByteArray.toHex(): String =
            joinToString("") { b -> "%02x".format(b) }

        fun isoNow(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            return sdf.format(Date())
        }

        fun zipFiles(
            zipFile: File,
            entries: List<Pair<File, String>>,
            cancellation: ExportCancellationToken? = null,
            jobActive: () -> Boolean = { true },
        ) {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                for ((file, name) in entries) {
                    // Fixed names only — reject path traversal
                    require(!name.contains("..") && !name.contains('/') && !name.contains('\\')) {
                        "illegal zip entry name"
                    }
                    if (cancellation != null) throwIfExportCancelled(cancellation, jobActive)
                    zos.putNextEntry(ZipEntry(name))
                    FileInputStream(file).use { inp ->
                        if (cancellation == null) {
                            inp.copyTo(zos)
                        } else {
                            copyWithExportCancellation(inp, zos, cancellation, jobActive)
                        }
                    }
                    zos.closeEntry()
                }
            }
        }

        fun validateOutlineJsonPages(outlineJson: String, exportPageCount: Int): Boolean {
            // Lightweight check without org.json: every exported_pdf_page value in range.
            val regex = Regex(""""exported_pdf_page(?:_end)?":\s*(\d+)""")
            val nums = regex.findAll(outlineJson).map { it.groupValues[1].toInt() }.toList()
            if (nums.isEmpty()) return false
            return nums.all { it in 1..exportPageCount }
        }
    }
}
