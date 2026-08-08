package com.markleaf.app.pdf

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PDF **metadata / outline** reader (bookmarks only).
 *
 * Does **not** render pages — [PdfPageRenderer] remains the sole bitmap path.
 * Chapter/page export still uses PDFBox via [com.markleaf.app.pdf.export.PdfPageExtractor].
 *
 * Perf notes:
 * - Builds a page→index map once (O(pages)) instead of indexOf per bookmark.
 * - Unresolved destinations fall back to the previous bookmark page (not always 0).
 * - Failures never kill the reader process.
 */
object PdfEmbeddedOutlineParser {

    private const val TAG = "PdfEmbeddedOutline"

    sealed class ParseResult {
        data class Success(
            val entries: List<EmbeddedOutlineEntry>,
            /** Bookmarks whose PDF destination could not be resolved. */
            val unresolvedDestinations: Int = 0,
        ) : ParseResult() {
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
                if (!PdfBoxRuntime.ensureInit(context)) {
                    return@withContext ParseResult.Failed("PDF 组件初始化失败")
                }
                val uri = Uri.parse(uriString)
                val stream = when (uri.scheme?.lowercase(java.util.Locale.ROOT)) {
                    "file" -> {
                        val path = uri.path
                        if (path.isNullOrEmpty()) {
                            null
                        } else {
                            val f = java.io.File(path)
                            if (f.isFile) f.inputStream() else null
                        }
                    }
                    else -> context.contentResolver.openInputStream(uri)
                } ?: return@withContext ParseResult.Failed("无法打开文件")

                stream.use { input ->
                    val loadSetting = PdfBoxRuntime.memorySetting()
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

                        // Page→index once by *stable* COS dictionary identity
                        // (PDPage wrappers are re-created on every access and
                        // must never be used as keys; see OutlinePageIndexResolver).
                        val resolver = OutlinePageIndexResolver(doc)

                        val entries = ArrayList<EmbeddedOutlineEntry>(256)
                        var order = 0
                        var unresolved = 0
                        var lastResolvedPage = 0

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

                                val resolved = resolver.resolvePageIndex(item)
                                val pageIndex = if (resolved >= 0) {
                                    lastResolvedPage = resolved
                                    resolved
                                } else {
                                    unresolved++
                                    // Prefer previous bookmark page over hard-coding 0 for every miss.
                                    lastResolvedPage.coerceIn(0, pageCount - 1)
                                }.coerceIn(0, pageCount - 1)

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

                        decideOutcome(entries, unresolved)
                    }
                }
            } catch (t: Throwable) {
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

    /**
     * Failure policy for outline parsing (kept explicit & testable):
     * - A single bad bookmark NEVER drops the whole outline: it degrades to
     *   the previously resolved page and is counted as unresolved.
     * - Only when the majority of bookmarks cannot be resolved
     *   (unresolved * 2 >= entries.size) is the outline treated as untrusted
     *   and surfaced to the user as a failure instead of silently wrong data.
     */
    internal fun decideOutcome(entries: List<EmbeddedOutlineEntry>, unresolved: Int): ParseResult = when {
        entries.isEmpty() -> ParseResult.None
        unresolved > 0 && unresolved * 2 >= entries.size ->
            ParseResult.Failed(
                "目录页码解析失败过多（$unresolved/${entries.size}），请检查 PDF 书签",
            )
        else -> ParseResult.Success(
            entries = entries,
            unresolvedDestinations = unresolved,
        )
    }
}
