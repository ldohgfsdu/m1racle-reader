package com.m1racle.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure policy tests for thumbnail cache sizing (no Android PdfRenderer).
 */
class PdfThumbnailCachePolicyTest {

    @Test
    fun lru_evictsAboveMaxEntries() {
        val max = 4
        val cache = object : LinkedHashMap<Int, String>(4, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, String>?): Boolean {
                return size > max
            }
        }
        repeat(6) { cache[it] = "p$it" }
        assertEquals(max, cache.size)
        // Eldest (0,1) should be gone with access-order default inserts.
        assertTrue(!cache.containsKey(0))
        assertTrue(!cache.containsKey(1))
        assertTrue(cache.containsKey(5))
    }

    @Test
    fun thumbBudget_isMuchSmallerThanFullPage() {
        assertTrue(PdfThumbnailLoader.THUMB_MAX_PIXELS < 200_000)
        assertTrue(PdfThumbnailLoader.THUMB_WIDTH_PX < PdfPageRenderer.MIN_EDGE_PX)
        assertTrue(PdfThumbnailLoader.DEFAULT_CACHE_ENTRIES <= 64)
        // Worst-case cache memory rough upper bound: entries * maxPixels * 4 bytes
        val worstBytes =
            PdfThumbnailLoader.DEFAULT_CACHE_ENTRIES.toLong() *
                PdfThumbnailLoader.THUMB_MAX_PIXELS * 4L
        assertTrue(worstBytes < 20L * 1024L * 1024L) // < 20MB theoretical ARGB
    }
}
