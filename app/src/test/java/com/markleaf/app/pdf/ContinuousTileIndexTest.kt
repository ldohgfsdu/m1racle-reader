package com.markleaf.app.pdf

import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import com.markleaf.app.ui.pdf.buildContinuousTileIndex
import com.markleaf.app.ui.pdf.ContinuousTileIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P1-7 hot-path index: overlay tiles are grouped and sorted once per
 * publication, so composition/measure passes read pre-built per-page lists.
 * The builder must apply exactly the old per-composition filter predicates
 * (document generation, page range, geometry generation, fitted width version)
 * and produce [PdfTileDisplay.forPage] ordering per page.
 */
class ContinuousTileIndexTest {

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

    private val geometry = ContinuousDocumentGeometry.create(
        pageCount = 3,
        basePageWidth = 1000f,
        aspects = mapOf(0 to 1f, 1 to 1f, 2 to 1f),
        pageGap = 20f,
    )

    private val geomGenFor0 = geometry.pageGeometryGeneration(0)
    private val fpwv = geometry.fittedPageWidthVersion

    private fun key(
        page: Int,
        docGen: Long = 5L,
        geomGen: Long = geometry.pageGeometryGeneration(page),
        fpwv: Int = this.fpwv,
        bucket: Int = 1,
        tileX: Int = 0,
        tileY: Int = 0,
    ) = PdfTileKey("doc", docGen, geomGen, page, 0, fpwv, bucket, tileX, tileY)

    private fun entry(k: PdfTileKey) = PdfTileEntry(
        k, bitmap, PageLayoutRect(0f, 0f, 8f, 8f), TilePixelRect(0, 0, 8, 8),
        renderedAtNanos = 1L, requestGeneration = 1L, byteSize = 40L,
    )

    private fun build(source: Collection<PdfTileEntry>, docGen: Long = 5L) =
        buildContinuousTileIndex(source, docGen, geometry, overlayEnabled = true)

    @Test
    fun `filters stale document generation`() {
        val index = build(listOf(entry(key(0)), entry(key(1, docGen = 4L))))
        assertEquals(listOf(key(0)), index.forPage(0).map { it.key })
        assertTrue(index.forPage(1).isEmpty())
        assertEquals(1, index.total)
    }

    @Test
    fun `filters out-of-range pages`() {
        val index = build(listOf(entry(key(0)), entry(key(7))))
        assertEquals(1, index.total)
        assertTrue(index.forPage(7).isEmpty())
    }

    @Test
    fun `filters mismatched geometry generation and fitted width version`() {
        val index = build(
            listOf(
                entry(key(0)),
                entry(key(1, geomGen = geomGenFor0 + 9L)),
                entry(key(2, fpwv = fpwv + 42)),
            ),
        )
        assertEquals(1, index.total)
        assertEquals(setOf(0), index.byPageKeys())
    }

    @Test
    fun `groups pages sorted by zoom bucket then y then x`() {
        val index = build(
            listOf(
                entry(key(0, bucket = 2, tileX = 1)),
                entry(key(0, bucket = 1, tileY = 1)),
                entry(key(0, bucket = 1)),
                entry(key(1, bucket = 1, tileX = 3)),
            ),
        )
        assertEquals(
            listOf(
                key(0, bucket = 1),
                key(0, bucket = 1, tileY = 1),
                key(0, bucket = 2, tileX = 1),
            ),
            index.forPage(0).map { it.key },
        )
        assertEquals(listOf(key(1, bucket = 1, tileX = 3)), index.forPage(1).map { it.key })
        assertEquals(4, index.total)
    }

    @Test
    fun `overlay disabled yields empty index regardless of source`() {
        val index = buildContinuousTileIndex(
            listOf(entry(key(0))),
            documentGeneration = 5L,
            geometry = geometry,
            overlayEnabled = false,
        )
        assertEquals(0, index.total)
        assertTrue(index.forPage(0).isEmpty())
    }

    private fun ContinuousTileIndex.byPageKeys(): Set<Int> =
        (0 until geometry.pageCount).filter { forPage(it).isNotEmpty() }.toSet()
}
