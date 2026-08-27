package com.m1racle.reader.pdf

import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0-1 eviction mirror: published overlay snapshots must drop LRU-evicted keys
 * (sequence bump, remaining bitmaps untouched) and must be a no-op when the
 * keys do not match — otherwise display state would resurrect freed tiles.
 */
class TileOverlayPurgeTest {

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

    private fun entry(pageIndex: Int): PdfTileEntry = PdfTileEntry(
        PdfTileKey("doc", 1L, 1L, pageIndex, 0, 0, 1, 0, 0),
        bitmap,
        PageLayoutRect(0f, 0f, 8f, 8f),
        TilePixelRect(0, 0, 8, 8),
        renderedAtNanos = 1L,
        requestGeneration = 1L,
        byteSize = 40L,
    )

    @Test
    fun `purge removes only evicted keys and bumps sequence`() {
        val kept = entry(1)
        val snapshot = TileOverlaySnapshot(
            sequence = 7L,
            entries = mapOf(entryKey(0) to entry(0), entryKey(1) to kept),
            publishedAtNanos = 5L,
        )

        val purged = snapshot.purgedWithout(setOf(entryKey(0)))

        assertNotNull(purged)
        assertEquals(8L, purged!!.sequence)
        assertEquals(setOf(entryKey(1)), purged.entries.keys)
        assertSame(bitmap, purged.entries.getValue(entryKey(1)).bitmap)
    }

    @Test
    fun `purge with no matching keys is a no-op`() {
        val snapshot = TileOverlaySnapshot(sequence = 3L, entries = mapOf(entryKey(2) to entry(2)))
        assertNull(snapshot.purgedWithout(emptySet()))
        assertNull(snapshot.purgedWithout(setOf(entryKey(9))))
    }

    @Test
    fun `purging every key empties the overlay`() {
        val snapshot = TileOverlaySnapshot(sequence = 1L, entries = mapOf(entryKey(0) to entry(0)))
        val purged = snapshot.purgedWithout(setOf(entryKey(0)))

        assertNotNull(purged)
        assertTrue(purged!!.entries.isEmpty())
        assertEquals(2L, purged.sequence)
    }

    private fun entryKey(page: Int): PdfTileKey =
        PdfTileKey("doc", 1L, 1L, page, 0, 0, 1, 0, 0)
}
