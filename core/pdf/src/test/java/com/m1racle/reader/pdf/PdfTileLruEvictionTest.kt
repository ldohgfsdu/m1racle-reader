package com.m1racle.reader.pdf

import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0-1 regression: tile LRU must retain REAL eviction capability.
 *
 * The old caller pinned every published overlay key, so the victim scan
 * (`firstOrNull { it.key !in pinned } ?: break`) never found a candidate and
 * the cache grew unbounded. These tests pin the Lru-level contract the fix
 * relies on: only explicitly pinned (queued/in-flight) keys are protected;
 * stale displayed keys are evictable; trimTo reports what it removed.
 */
class PdfTileLruEvictionTest {

    private val bitmap: ImageBitmap = object : ImageBitmap {
        override val width: Int = 8
        override val height: Int = 8
        override val colorSpace = ColorSpaces.Srgb
        override val hasAlpha: Boolean = false
        override val config: ImageBitmapConfig = ImageBitmapConfig.Argb8888
        override fun readPixels(
            buffer: IntArray,
            startX: Int,
            startY: Int,
            width: Int,
            height: Int,
            bufferOffset: Int,
            stride: Int,
        ) = Unit
        override fun prepareToDraw() = Unit
    }

    private fun entry(pageIndex: Int, byteSize: Long = 40L): PdfTileEntry = PdfTileEntry(
        PdfTileKey("doc", 1L, 1L, pageIndex, 0, 0, 1, 0, 0),
        bitmap,
        PageLayoutRect(0f, 0f, 8f, 8f),
        TilePixelRect(0, 0, 8, 8),
        renderedAtNanos = 1L,
        requestGeneration = 1L,
        byteSize = byteSize,
    )

    private fun lru(maxBytes: Long) = PdfTileLru(maxBytes)

    @Test
    fun `put evicts oldest unpinned entries until under budget`() {
        val cache = lru(100)
        val a = entry(0)
        val b = entry(1)
        val c = entry(2)
        cache.put(a, emptySet())
        cache.put(b, emptySet())
        val evicted = cache.put(c, emptySet())

        // Single-step eviction: stop as soon as bytes are back under budget.
        assertEquals(listOf(a), evicted)
        assertEquals(80L, cache.bytes)
        assertEquals(setOf(b.key, c.key), cache.snapshot().keys)
    }

    @Test
    fun `explicitly pinned active keys survive eviction`() {
        val cache = lru(100)
        val a = entry(0)
        val b = entry(1)
        cache.put(a, emptySet())
        cache.put(b, emptySet())

        val c = entry(2)
        val evicted = cache.put(c, pinned = setOf(a.key))

        assertEquals(listOf(b), evicted)
        assertEquals(setOf(a.key, c.key), cache.snapshot().keys)
    }

    @Test
    fun `stale displayed keys are evictable once they are no longer pinned`() {
        // Regression shape: x/y were published to an overlay snapshot earlier
        // ("displayed"), nothing queues them anymore. Under the old pin-everything
        // policy this put could not evict anything; now the budget holds.
        val cache = lru(100)
        val x = entry(0)
        val y = entry(1)
        cache.put(x, emptySet())
        cache.put(y, emptySet())

        val z = entry(2)
        val evicted = cache.put(z, pinned = emptySet())

        assertEquals(listOf(x), evicted)
        assertEquals(80L, cache.bytes)
    }

    @Test
    fun `get refreshes recency so recently read tiles outlive older ones`() {
        val cache = lru(120)
        val a = entry(0)
        val b = entry(1)
        val c = entry(2)
        cache.put(a, emptySet())
        cache.put(b, emptySet())
        cache.put(c, emptySet())
        cache.get(a.key)

        val d = entry(3)
        val evicted = cache.put(d, pinned = emptySet())

        assertEquals(listOf(b), evicted)
        assertEquals(setOf(a.key, c.key, d.key), cache.snapshot().keys)
    }

    @Test
    fun `all-pinned put keeps entries but documents no eviction`() {
        val cache = lru(50)
        val a = entry(0)
        val b = entry(1)
        assertEquals(emptyList<PdfTileEntry>(), cache.put(a, pinned = setOf(a.key)))
        assertEquals(emptyList<PdfTileEntry>(), cache.put(b, pinned = setOf(a.key, b.key)))
        // Budget exceeded while everything is pinned: no victim exists by design.
        assertEquals(setOf(a.key, b.key), cache.snapshot().keys)
    }

    @Test
    fun `trimTo returns evicted entries oldest-first and reaches budget`() {
        val cache = lru(200)
        val a = entry(0)
        val b = entry(1)
        val c = entry(2)
        cache.put(a, emptySet())
        cache.put(b, emptySet())
        cache.put(c, emptySet())

        val evicted = cache.trimTo(50)

        assertEquals(listOf(a, b), evicted)
        assertEquals(40L, cache.bytes)
        assertEquals(setOf(c.key), cache.snapshot().keys)
    }

    @Test
    fun `keySet is a live unmodifiable view with zero copy`() {
        val cache = lru(200)
        val a = entry(0)
        cache.put(a, emptySet())

        val view = cache.keySet()
        assertEquals(setOf(a.key), view)
        val b = entry(1)
        cache.put(b, emptySet())
        assertTrue("live view must reflect later mutation", b.key in view)

        try {
            (view as MutableSet).clear()
            throw IllegalStateException("expected UnsupportedOperationException")
        } catch (_: UnsupportedOperationException) {
            // expected — callers must never mutate the cache through the view
        }
    }

    @Test
    fun `re-putting an existing key replaces without double counting`() {
        val cache = lru(100)
        cache.put(entry(0), emptySet())
        cache.put(entry(0), emptySet())
        assertEquals(40L, cache.bytes)
    }

}
