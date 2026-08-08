package com.markleaf.app.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.graphics.Matrix
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Local-only PDF page renderer backed by [PdfRenderer].
 * - No network
 * - Renders off the main thread
 * - Serializes open-page (PdfRenderer allows only one page open at a time)
 * - Caps bitmap long edge to limit memory
 * - Caller must [close] when done; FDs are not held after close
 */
class PdfPageRenderer(
    private val context: Context,
) : AutoCloseable {

    private val mutex = Mutex()
    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null

    val pageCount: Int
        get() = renderer?.pageCount ?: 0

    val isOpen: Boolean
        get() = renderer != null

    sealed class OpenResult {
        data class Success(val pageCount: Int) : OpenResult()
        data object NotFound : OpenResult()
        data object PermissionDenied : OpenResult()
        data class Failed(val message: String) : OpenResult()
    }

    sealed class RenderResult {
        data class Success(val image: ImageBitmap, val widthPx: Int, val heightPx: Int) : RenderResult()
        data object Closed : RenderResult()
        data object InvalidPage : RenderResult()
        data object OutOfMemory : RenderResult()
        data class Failed(val message: String) : RenderResult()
    }

    sealed class TileRenderResult {
        data class Success(val image: ImageBitmap, val widthPx: Int, val heightPx: Int, val byteSize: Long) : TileRenderResult()
        data object Closed : TileRenderResult()
        data object InvalidPage : TileRenderResult()
        data object OutOfMemory : TileRenderResult()
        data class Failed(val message: String) : TileRenderResult()
    }

    suspend fun renderTile(
        request: PdfTileRequest,
        pageLayoutSize: PageLayoutSize,
    ): TileRenderResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val pdf = renderer ?: return@withLock TileRenderResult.Closed
            if (request.key.pageIndex !in 0 until pdf.pageCount) {
                return@withLock TileRenderResult.InvalidPage
            }
            try {
                pdf.openPage(request.key.pageIndex).use { page ->
                    val transform = PdfTileRenderMath.create(
                        request = request,
                        layoutSize = pageLayoutSize,
                        pdfSize = PdfPageSize(page.width.coerceAtLeast(1), page.height.coerceAtLeast(1)),
                    )
                    val matrix = Matrix().apply { setValues(transform.matrixValues) }
                    BitmapAllocationGuard.allocateOr(
                        allocate = {
                            Bitmap.createBitmap(
                                transform.bitmapWidthPx,
                                transform.bitmapHeightPx,
                                Bitmap.Config.ARGB_8888,
                            )
                        },
                        onOutOfMemory = { TileRenderResult.OutOfMemory },
                        onRelease = { it.recycle() },
                        body = { bitmap ->
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            TileRenderResult.Success(
                                bitmap.asImageBitmap(),
                                transform.bitmapWidthPx,
                                transform.bitmapHeightPx,
                                bitmap.allocationByteCount.toLong(),
                            )
                        },
                    )
                }
            } catch (e: Exception) {
                TileRenderResult.Failed(e.message ?: "tile render failed")
            }
        }
    }

    suspend fun open(uriString: String): OpenResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            closeLocked()
            try {
                val uri = Uri.parse(uriString)
                val descriptor = openDescriptor(uri)
                    ?: return@withLock OpenResult.NotFound
                // Assign pfd before PdfRenderer construction so closeLocked() always
                // releases the FD if the renderer ctor throws (failed-open path).
                pfd = descriptor
                val pdf = try {
                    PdfRenderer(descriptor)
                } catch (e: Exception) {
                    closeLocked()
                    throw e
                }
                renderer = pdf
                val count = pdf.pageCount
                if (count <= 0) {
                    closeLocked()
                    return@withLock OpenResult.Failed("PDF 没有可显示的页面")
                }
                OpenResult.Success(count)
            } catch (_: SecurityException) {
                closeLocked()
                OpenResult.PermissionDenied
            } catch (_: FileNotFoundException) {
                closeLocked()
                OpenResult.NotFound
            } catch (_: IOException) {
                closeLocked()
                OpenResult.Failed("无法打开 PDF 文件")
            } catch (e: Exception) {
                closeLocked()
                OpenResult.Failed(e.message?.takeIf { it.isNotBlank() } ?: "无法打开 PDF 文件")
            }
        }
    }

    private fun openDescriptor(uri: Uri): ParcelFileDescriptor? {
        return when (uri.scheme?.lowercase(Locale.ROOT)) {
            "file" -> {
                val path = uri.path ?: return null
                val file = File(path)
                if (!file.isFile) return null
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }
            else -> context.contentResolver.openFileDescriptor(uri, "r")
        }
    }

    /**
     * Render [pageIndex] (0-based) scaled to fit [targetWidthPx] while capping the long edge.
     *
     * @param minEdgePx lower bound for width (use a small value for thumbnails).
     * @param maxEdgePx upper bound for long edge.
     * @param maxPixels hard pixel budget (defaults to full-page budget).
     */
    /**
     * PDF page size in points (for aspect ratio). Does not allocate a bitmap.
     */
    suspend fun pageSizePts(pageIndex: Int): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val pdf = renderer ?: return@withLock null
            if (pageIndex < 0 || pageIndex >= pdf.pageCount) return@withLock null
            try {
                pdf.openPage(pageIndex).use { page ->
                    page.width.coerceAtLeast(1) to page.height.coerceAtLeast(1)
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun renderPage(
        pageIndex: Int,
        targetWidthPx: Int,
        maxEdgePx: Int = MAX_EDGE_PX,
        minEdgePx: Int = MIN_EDGE_PX,
        maxPixels: Long = MAX_PIXELS,
    ): RenderResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val pdf = renderer ?: return@withLock RenderResult.Closed
            if (pageIndex < 0 || pageIndex >= pdf.pageCount) {
                return@withLock RenderResult.InvalidPage
            }
            try {
                pdf.openPage(pageIndex).use { page ->
                    val pageW = page.width.coerceAtLeast(1)
                    val pageH = page.height.coerceAtLeast(1)
                    val lo = minEdgePx.coerceAtLeast(1)
                    val hi = maxEdgePx.coerceAtLeast(lo)
                    // targetWidthPx is always pixels (never dp). Cap width at MAX_WIDTH_PX.
                    val desiredW = targetWidthPx
                        .coerceAtMost(MAX_WIDTH_PX)
                        .coerceIn(lo, hi)
                    var scale = desiredW.toFloat() / pageW.toFloat()
                    var outW = (pageW * scale).roundToInt().coerceAtLeast(1)
                    var outH = (pageH * scale).roundToInt().coerceAtLeast(1)
                    val longEdge = max(outW, outH)
                    if (longEdge > maxEdgePx) {
                        val down = maxEdgePx.toFloat() / longEdge.toFloat()
                        outW = max(1, (outW * down).roundToInt())
                        outH = max(1, (outH * down).roundToInt())
                    }
                    val pixels = outW.toLong() * outH.toLong()
                    if (pixels > maxPixels) {
                        val factor = kotlin.math.sqrt(maxPixels.toDouble() / pixels.toDouble())
                        outW = max(1, (outW * factor).roundToInt())
                        outH = max(1, (outH * factor).roundToInt())
                    }
                    BitmapAllocationGuard.allocateOr(
                        allocate = { Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888) },
                        onOutOfMemory = { RenderResult.OutOfMemory },
                        onRelease = { it.recycle() },
                        body = { bitmap ->
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            RenderResult.Success(
                                image = bitmap.asImageBitmap(),
                                widthPx = outW,
                                heightPx = outH,
                            )
                        },
                    )
                }
            } catch (e: Exception) {
                RenderResult.Failed(e.message?.takeIf { it.isNotBlank() } ?: "页面渲染失败")
            }
        }
    }

    override fun close() {
        // Best-effort sync close; safe if already closed.
        try {
            kotlinx.coroutines.runBlocking {
                mutex.withLock { closeLocked() }
            }
        } catch (_: Exception) {
            closeLocked()
        }
    }

    suspend fun closeAsync() = withContext(Dispatchers.IO) {
        mutex.withLock { closeLocked() }
    }

    private fun closeLocked() {
        try {
            renderer?.close()
        } catch (_: Exception) {
            // ignore
        }
        renderer = null
        try {
            pfd?.close()
        } catch (_: Exception) {
            // ignore
        }
        pfd = null
    }

    companion object {
        /** Long-edge cap (px). Continuous path caps width at 3072. */
        const val MAX_EDGE_PX = 6144
        const val MIN_EDGE_PX = 320
        /** ~14MP budget — enough for 3072×~4340 portrait; continuous keeps ≤1 HIGH. */
        const val MAX_PIXELS = 14L * 1024L * 1024L
        const val MAX_WIDTH_PX = PdfRenderQuality.MAX_CONTINUOUS_WIDTH_PX
    }
}
