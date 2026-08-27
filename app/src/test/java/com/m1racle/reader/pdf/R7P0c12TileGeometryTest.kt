package com.m1racle.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class R7P0c12TileGeometryTest {
    private val scales = PdfZoomBuckets.scales.toList()

    @Test fun `matrix maps requested pdf origin to exact bleed origin`() {
        val pageSizes = listOf(
            PageLayoutSize(997.25f, 1410.35f) to PdfPageSize(595, 842),
            PageLayoutSize(1001.5f, 707.75f) to PdfPageSize(842, 595),
            // Swapped PDF dimensions model a rotated page as exposed by PdfRenderer.
            PageLayoutSize(913.75f, 1292.5f) to PdfPageSize(842, 595),
        )
        for ((layout, pdf) in pageSizes) {
            for ((bucket, scale) in scales.withIndex()) {
                val view = PageTileViewport(
                    pageIndex = 0,
                    pageWidth = layout.width,
                    pageHeight = layout.height,
                    visibleRect = PageLayoutRect(
                        layout.width * .35f,
                        layout.height * .35f,
                        layout.width * .75f,
                        layout.height * .75f,
                    ),
                    rotation = if (pdf.width > pdf.height && layout.height > layout.width) 90 else 0,
                )
                val request = PdfTilePlanner.requests("doc", 1, view, bucket, 1, null, null)
                    .first { it.key.tileX > 0 || it.key.tileY > 0 }
                val transform = PdfTileRenderMath.create(request, layout, pdf)
                val matrix = transform.matrixValues
                val bitmapX = transform.pdfPageRect.left * matrix[0] + matrix[2]
                val bitmapY = transform.pdfPageRect.top * matrix[4] + matrix[5]
                assertEquals("bucket=$bucket scale=$scale", request.bleedPx.left.toFloat(), bitmapX, .001f)
                assertEquals("bucket=$bucket scale=$scale", request.bleedPx.top.toFloat(), bitmapY, .001f)
            }
        }
    }

    @Test fun `content dimensions use round and bitmap dimensions add explicit bleed`() {
        for ((bucket, scale) in scales.withIndex()) {
            val view = PageTileViewport(
                0,
                1000.3f,
                1414.7f,
                PageLayoutRect(900f, 1300f, 1000.3f, 1414.7f),
            )
            val edge = PdfTilePlanner.requests("doc", 1, view, bucket, 1, null, null)
                .maxBy { it.key.tileX + it.key.tileY }
            assertEquals((edge.pageRect.width * scale).roundToInt(), edge.contentWidthPx)
            assertEquals((edge.pageRect.height * scale).roundToInt(), edge.contentHeightPx)
            assertEquals(edge.contentWidthPx + 4, edge.outputWidth)
            assertEquals(edge.contentHeightPx + 4, edge.outputHeight)
            assertEquals(edge.contentWidthPx, edge.contentRectPx.width)
            assertEquals(edge.contentHeightPx, edge.contentRectPx.height)
        }
    }

    @Test fun `rounding logical edges produces contiguous canvas destinations`() {
        val scale = 2.8f
        val logicalTile = PdfTilePlanner.TILE_PX / scale
        val first = PageLayoutRect(0f, 0f, logicalTile, 100f).roundedPixelRect()
        val second = PageLayoutRect(logicalTile, 0f, logicalTile * 2f, 100f).roundedPixelRect()
        val third = PageLayoutRect(logicalTile * 2f, 0f, logicalTile * 3f, 100f).roundedPixelRect()
        assertEquals(first.right, second.left)
        assertEquals(second.right, third.left)
    }

    @Test fun `geometry identity changes with fitted size or rotation`() {
        val baseline = pageGeometryGeneration(1080f, 1527.12f, 0)
        assertNotEquals(baseline, pageGeometryGeneration(1081f, 1528.534f, 0))
        assertNotEquals(baseline, pageGeometryGeneration(1080f, 1528f, 0))
        assertNotEquals(baseline, pageGeometryGeneration(1080f, 1527.12f, 90))
    }

    @Test fun `old default aspect and real aspect cannot share cache key`() {
        val provisional = PageTileViewport(
            29,
            1080f,
            1080f * 1.414f,
            PageLayoutRect(0f, 0f, 500f, 500f),
        )
        val actual = provisional.copy(
            pageHeight = 1080f * (842f / 595f),
            geometryGeneration = pageGeometryGeneration(1080f, 1080f * (842f / 595f), 0),
        )
        val oldKey = PdfTilePlanner.requests("doc", 4, provisional, 2, 1, null, null).first().key
        val newKey = PdfTilePlanner.requests("doc", 4, actual, 2, 2, null, null).first().key
        assertNotEquals(oldKey, newKey)
    }

    @Test fun `synthetic whole page equals composition after cropping one and two pixel bleed`() {
        val cases = listOf(
            Triple(613, 877, 0),
            Triple(877, 613, 0),
            Triple(613, 877, 90),
        )
        for ((pageWidth, pageHeight, rotation) in cases) {
            for ((bucket, scale) in scales.withIndex()) {
                for (bleed in listOf(1, 2)) {
                    assertSyntheticComposition(pageWidth, pageHeight, rotation, bucket, scale, bleed)
                }
            }
        }
    }

    private fun assertSyntheticComposition(
        pageWidth: Int,
        pageHeight: Int,
        rotation: Int,
        bucket: Int,
        scale: Float,
        bleed: Int,
    ) {
        val fittedWidth = 389.5f
        val fittedHeight = fittedWidth * pageHeight / pageWidth
        val outputWidth = (fittedWidth * scale).roundToInt()
        val outputHeight = (fittedHeight * scale).roundToInt()
        val reference = IntArray(outputWidth * outputHeight) { index ->
            pattern(index % outputWidth, index / outputWidth, outputWidth, outputHeight)
        }
        val composed = IntArray(reference.size) { SENTINEL }
        val view = PageTileViewport(
            pageIndex = 0,
            pageWidth = fittedWidth,
            pageHeight = fittedHeight,
            visibleRect = PageLayoutRect(0f, 0f, fittedWidth, fittedHeight),
            rotation = rotation,
        )
        for (planned in PdfTilePlanner.requests("doc", 1, view, bucket, 1, null, null)) {
            val request = planned.copy(bleedPx = TilePixelInsets(bleed, bleed, bleed, bleed))
            val tile = IntArray(request.outputWidth * request.outputHeight) { SENTINEL }
            val destinationLeft = (request.pageRect.left * scale).roundToInt()
            val destinationTop = (request.pageRect.top * scale).roundToInt()
            for (contentY in 0 until request.contentHeightPx) {
                val pageY = destinationTop + contentY
                if (pageY !in 0 until outputHeight) continue
                for (contentX in 0 until request.contentWidthPx) {
                    val pageX = destinationLeft + contentX
                    if (pageX !in 0 until outputWidth) continue
                    val tileX = request.contentRectPx.left + contentX
                    val tileY = request.contentRectPx.top + contentY
                    tile[tileY * request.outputWidth + tileX] = reference[pageY * outputWidth + pageX]
                }
            }
            val source = request.contentRectPx
            for (sourceY in source.top until source.bottom) {
                val pageY = destinationTop + sourceY - source.top
                if (pageY !in 0 until outputHeight) continue
                for (sourceX in source.left until source.right) {
                    val pageX = destinationLeft + sourceX - source.left
                    if (pageX !in 0 until outputWidth) continue
                    composed[pageY * outputWidth + pageX] = tile[sourceY * request.outputWidth + sourceX]
                }
            }
        }
        var differingPixels = 0
        for (index in reference.indices) if (reference[index] != composed[index]) differingPixels++
        assertEquals(
            "page=${pageWidth}x$pageHeight rotation=$rotation scale=$scale bleed=$bleed",
            0,
            differingPixels,
        )
        assertTrue(composed.none { it == SENTINEL })
    }

    /** Checkerboard, coordinate lines, QR-like blocks, text-like bars, and colored regions. */
    private fun pattern(x: Int, y: Int, width: Int, height: Int): Int {
        val checker = ((x / 16) + (y / 16)) and 1
        val qr = x in width / 8 until width / 3 && y in height / 8 until height / 3 &&
            (((x / 5) xor (y / 5)) and 1) == 0
        val axis = x % 97 == 0 || y % 89 == 0
        val textBar = y in height / 2 until height / 2 + 7 && (x / 11) % 3 != 0
        return when {
            qr -> 0xff000000.toInt()
            axis -> 0xff0066ff.toInt()
            textBar -> 0xff202020.toInt()
            checker == 0 -> 0xffffcc66.toInt()
            else -> 0xff55aa88.toInt()
        }
    }

    private companion object {
        const val SENTINEL: Int = 0x11223344
    }
}
