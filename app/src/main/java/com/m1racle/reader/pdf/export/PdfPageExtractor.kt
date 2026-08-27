package com.m1racle.reader.pdf.export

import android.util.Log
import com.m1racle.reader.pdf.PdfBoxRuntime
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import kotlin.coroutines.coroutineContext

/**
 * Extract a contiguous page range into a new PDF via PDFBox page import
 * (content streams preserved — not rasterized screenshots).
 *
 * Uses temp-file memory mode so large source PDFs are not fully heap-loaded.
 * Outline detection shares [PdfBoxRuntime] init; display still uses PdfRenderer.
 */
object PdfPageExtractor {

    private const val TAG = "PdfPageExtractor"

    fun ensureInit(context: android.content.Context) {
        PdfBoxRuntime.ensureInit(context)
    }

    sealed class ExtractResult {
        data class Success(val pageCount: Int) : ExtractResult()
        data class Failed(val message: String) : ExtractResult()
        data object Cancelled : ExtractResult()
    }

    /**
     * @param startIndex0 inclusive 0-based
     * @param endIndex0 inclusive 0-based
     * @param onProgress (current 1-based, total)
     */
    suspend fun extractPageRange(
        context: android.content.Context,
        sourceStream: InputStream,
        outputFile: File,
        startIndex0: Int,
        endIndex0: Int,
        onProgress: suspend (current: Int, total: Int) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false },
    ): ExtractResult = withContext(Dispatchers.IO) {
        if (!PdfBoxRuntime.ensureInit(context)) {
            return@withContext ExtractResult.Failed("PDF 组件初始化失败")
        }
        if (startIndex0 < 0 || endIndex0 < startIndex0) {
            return@withContext ExtractResult.Failed("非法页面范围")
        }
        val total = endIndex0 - startIndex0 + 1
        // Remove partial output on cancel/failure
        fun cleanupPartial() {
            try {
                if (outputFile.exists()) outputFile.delete()
            } catch (_: Throwable) {
            }
        }
        try {
            val mem = PdfBoxRuntime.memorySetting()
            sourceStream.use { input ->
                PDDocument.load(input, mem).use { source ->
                    val srcPages = source.numberOfPages
                    if (endIndex0 >= srcPages) {
                        return@withContext ExtractResult.Failed(
                            "结束页超出源文档 (共 $srcPages 页)",
                        )
                    }
                    PDDocument().use { dest ->
                        for (i in 0 until total) {
                            coroutineContext.ensureActive()
                            if (isCancelled()) {
                                cleanupPartial()
                                return@withContext ExtractResult.Cancelled
                            }
                            val srcIndex = startIndex0 + i
                            try {
                                val page = source.getPage(srcIndex)
                                dest.importPage(page)
                            } catch (t: Throwable) {
                                Log.e(TAG, "importPage failed at $srcIndex", t)
                                cleanupPartial()
                                return@withContext ExtractResult.Failed(
                                    "复制第 ${srcIndex + 1} 页失败: ${t.message ?: t.javaClass.simpleName}",
                                )
                            }
                            onProgress(i + 1, total)
                        }
                        if (isCancelled()) {
                            cleanupPartial()
                            return@withContext ExtractResult.Cancelled
                        }
                        dest.save(outputFile)
                    }
                }
            }
            // Verify page count
            try {
                FileInputStream(outputFile).use { verifyIn ->
                    PDDocument.load(verifyIn, PdfBoxRuntime.memorySetting()).use { v ->
                        if (v.numberOfPages != total) {
                            cleanupPartial()
                            return@withContext ExtractResult.Failed(
                                "导出 PDF 页数不符: 期望 $total, 实际 ${v.numberOfPages}",
                            )
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "verify open failed", t)
                cleanupPartial()
                return@withContext ExtractResult.Failed("导出 PDF 无法重新打开校验")
            }
            ExtractResult.Success(total)
        } catch (e: CancellationException) {
            cleanupPartial()
            ExtractResult.Cancelled
        } catch (t: Throwable) {
            Log.e(TAG, "extract failed", t)
            cleanupPartial()
            ExtractResult.Failed(
                when (t) {
                    is OutOfMemoryError -> "内存不足，无法导出该范围"
                    is SecurityException -> "文件访问权限已失效"
                    else -> t.message?.takeIf { it.isNotBlank() } ?: "页面提取失败"
                },
            )
        }
    }
}
