package com.markleaf.app.pdf

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Independent PDF **metadata / outline** reader.
 * Does **not** render pages — [PdfPageRenderer] remains the sole bitmap path.
 *
 * Failures never propagate as process-killing Errors: large textbooks may OOM
 * if fully loaded; we use temp-file memory mode and catch [Throwable].
 */
object PdfEmbeddedOutlineParser {

    private const val TAG = "PdfEmbeddedOutline"
    private val pdfBoxReady = AtomicBoolean(false)

    fun ensureInit(context: Context) {
        if (pdfBoxReady.compareAndSet(false, true)) {
            try {
                PDFBoxResourceLoader.init(context.applicationContext)
            } catch (t: Throwable) {
                Log.w(TAG, "PDFBoxResourceLoader.init failed", t)
                pdfBoxReady.set(false)
            }
        }
    }

    sealed class ParseResult {
        data class Success(val entries: List<EmbeddedOutlineEntry>) : ParseResult() {
            val nodeCount: Int get() = entries.size
            val isEmpty: Boolean get() = entries.isEmpty()
        }

        data object None : ParseResult()
        data class Failed(val message: String) : ParseResult()
    }

    /**
     * Parse document outline from a SAF content URI. Never writes the PDF.
     */
    suspend fun parse(context: Context, uriString: String): ParseResult =
        withContext(Dispatchers.IO) {
            try {
                ensureInit(context)
                val uri = Uri.parse(uriString)
                val stream = when (uri.scheme?.lowercase(java.util.Locale.ROOT)) {
                    "file" -> {
                        val path = uri.path
                        if (path.isNullOrEmpty()) null
                        else {
                            val f = java.io.File(path)
                            if (f.isFile) f.inputStream() else null
                        }
                    }
                    else -> context.contentResolver.openInputStream(uri)
                } ?: return@withContext ParseResult.Failed("无法打开文件")
                stream.use { input ->
                    // Prefer temp-file backed memory for large medical PDFs (avoids heap OOM).
                    val loadSetting = try {
                        MemoryUsageSetting.setupTempFileOnly()
                    } catch (_: Throwable) {
                        MemoryUsageSetting.setupMainMemoryOnly()
                    }
                    PDDocument.load(input, loadSetting).use { doc ->
                        val outline: PDDocumentOutline? = try {
                            doc.documentCatalog?.documentOutline
                        } catch (t: Throwable) {
                            Log.w(TAG, "documentOutline access failed", t)
                            null
                        }
                        val first = try {
                            outline?.firstChild
                        } catch (_: Throwable) {
                            null
                        }
                        if (outline == null || first == null) {
                            return@withContext ParseResult.None
                        }
                        val pageCount = try {
                            doc.numberOfPages
                        } catch (_: Throwable) {
                            0
                        }
                        if (pageCount <= 0) {
                            return@withContext ParseResult.None
                        }
                        val entries = ArrayList<EmbeddedOutlineEntry>(256)
                        var order = 0
                        fun walk(node: PDOutlineNode, depth: Int, parentOrder: Int) {
                            if (depth > 32) return
                            var item: PDOutlineItem? = try {
                                node.firstChild
                            } catch (_: Throwable) {
                                null
                            }
                            var guard = 0
                            while (item != null && guard++ < 50_000) {
                                val myOrder = order++
                                val title = try {
                                    item.title?.trim().orEmpty()
                                } catch (_: Throwable) {
                                    ""
                                }.ifBlank { "未命名" }
                                val pageIndex = resolvePageIndex(doc, item)
                                    .coerceIn(0, pageCount - 1)
                                entries.add(
                                    EmbeddedOutlineEntry(
                                        title = title,
                                        pageIndex = pageIndex,
                                        depth = depth,
                                        order = myOrder,
                                        parentOrder = parentOrder,
                                    ),
                                )
                                try {
                                    walk(item, depth + 1, myOrder)
                                } catch (t: Throwable) {
                                    Log.w(TAG, "outline child walk failed at $title", t)
                                }
                                item = try {
                                    item.nextSibling
                                } catch (_: Throwable) {
                                    null
                                }
                            }
                        }
                        try {
                            walk(outline, 0, -1)
                        } catch (t: Throwable) {
                            Log.w(TAG, "outline walk failed", t)
                        }
                        if (entries.isEmpty()) {
                            ParseResult.None
                        } else {
                            ParseResult.Success(entries)
                        }
                    }
                }
            } catch (t: Throwable) {
                // Includes OutOfMemoryError — do not crash the reader.
                Log.e(TAG, "parse failed", t)
                ParseResult.Failed(
                    when (t) {
                        is SecurityException -> "文件访问权限已失效"
                        is OutOfMemoryError -> "PDF 过大，目录解析内存不足"
                        else -> t.message?.takeIf { it.isNotBlank() } ?: "无法解析 PDF 目录"
                    },
                )
            }
        }

    private fun resolvePageIndex(doc: PDDocument, item: PDOutlineItem): Int {
        return try {
            val page = item.findDestinationPage(doc) ?: return 0
            val idx = doc.pages.indexOf(page)
            if (idx >= 0) idx else 0
        } catch (_: Throwable) {
            0
        }
    }
}
