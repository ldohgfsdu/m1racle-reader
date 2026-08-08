package com.markleaf.app.pdf

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * On-demand low-res PDF thumbnails with in-memory LRU.
 *
 * Uses a **separate** [PdfPageRenderer] so the main reader can keep its own
 * [PdfRenderer] open (Android allows one open page per renderer instance).
 *
 * Never pre-renders the full document — callers request only visible indices.
 */
class PdfThumbnailLoader(
    context: Context,
    private val maxCacheEntries: Int = DEFAULT_CACHE_ENTRIES,
) : AutoCloseable {

    private val appContext = context.applicationContext
    private val renderer = PdfPageRenderer(appContext)
    private val cacheMutex = Mutex()

    /** Access-order LRU of pageIndex → bitmap. */
    private val cache = object : LinkedHashMap<Int, ImageBitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ImageBitmap>?): Boolean {
            return size > maxCacheEntries
        }
    }

    /** Pages that failed once; cleared on [invalidate] / [retry]. */
    private val failed = mutableSetOf<Int>()

    private var openUri: String? = null

    sealed class ThumbResult {
        data class Ready(val image: ImageBitmap) : ThumbResult()
        data object Loading : ThumbResult() // not used by load; UI local
        data class Failed(val message: String) : ThumbResult()
        data object Closed : ThumbResult()
    }

    val isOpen: Boolean get() = renderer.isOpen

    suspend fun open(uriString: String): PdfPageRenderer.OpenResult {
        val result = renderer.open(uriString)
        if (result is PdfPageRenderer.OpenResult.Success) {
            openUri = uriString
            cacheMutex.withLock {
                cache.clear()
                failed.clear()
            }
        }
        return result
    }

    /**
     * Return cached thumb or render at thumbnail resolution.
     * Cooperative cancel: if the coroutine is cancelled mid-wait, result is discarded.
     */
    suspend fun load(pageIndex: Int): ThumbResult = withContext(Dispatchers.IO) {
        if (!renderer.isOpen) return@withContext ThumbResult.Closed
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
            return@withContext ThumbResult.Failed("页码无效")
        }

        cacheMutex.withLock {
            cache[pageIndex]?.let { return@withContext ThumbResult.Ready(it) }
            if (pageIndex in failed) {
                return@withContext ThumbResult.Failed("缩略图加载失败")
            }
        }

        coroutineContext.ensureActive()
        val rendered = renderer.renderPage(
            pageIndex = pageIndex,
            targetWidthPx = THUMB_WIDTH_PX,
            maxEdgePx = THUMB_MAX_EDGE_PX,
            minEdgePx = THUMB_MIN_EDGE_PX,
            maxPixels = THUMB_MAX_PIXELS,
        )
        coroutineContext.ensureActive()

        when (rendered) {
            is PdfPageRenderer.RenderResult.Success -> {
                cacheMutex.withLock {
                    cache[pageIndex] = rendered.image
                    failed.remove(pageIndex)
                }
                ThumbResult.Ready(rendered.image)
            }
            PdfPageRenderer.RenderResult.Closed -> ThumbResult.Closed
            PdfPageRenderer.RenderResult.InvalidPage -> ThumbResult.Failed("页码无效")
            PdfPageRenderer.RenderResult.OutOfMemory -> ThumbResult.Failed("内存不足")
            is PdfPageRenderer.RenderResult.Failed -> {
                cacheMutex.withLock { failed.add(pageIndex) }
                ThumbResult.Failed(rendered.message)
            }
        }
    }

    /** Drop one page so the next [load] re-renders (retry failed cells). */
    suspend fun invalidate(pageIndex: Int) {
        cacheMutex.withLock {
            cache.remove(pageIndex)
            failed.remove(pageIndex)
        }
    }

    suspend fun clearCache() {
        cacheMutex.withLock {
            cache.clear()
            failed.clear()
        }
    }

    /** Snapshot of cache size for diagnostics / tests. */
    suspend fun cacheSize(): Int = cacheMutex.withLock { cache.size }

    override fun close() {
        try {
            renderer.close()
        } catch (_: Exception) {
            // ignore
        }
        openUri = null
        try {
            kotlinx.coroutines.runBlocking {
                cacheMutex.withLock {
                    cache.clear()
                    failed.clear()
                }
            }
        } catch (_: Exception) {
            cache.clear()
            failed.clear()
        }
    }

    suspend fun closeAsync() {
        renderer.closeAsync()
        openUri = null
        cacheMutex.withLock {
            cache.clear()
            failed.clear()
        }
    }

    companion object {
        const val DEFAULT_CACHE_ENTRIES = 36
        const val THUMB_WIDTH_PX = 144
        const val THUMB_MIN_EDGE_PX = 64
        const val THUMB_MAX_EDGE_PX = 200
        const val THUMB_MAX_PIXELS = 80_000L // ~144x200-ish
    }
}
