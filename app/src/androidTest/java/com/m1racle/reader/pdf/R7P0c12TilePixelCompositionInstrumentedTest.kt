package com.m1racle.reader.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class R7P0c12TilePixelCompositionInstrumentedTest {
    @Test
    fun wholePageReferenceMatchesTileCompositionAcrossBucketsEdgesBleedAndOrientation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val pdfFile = File(context.cacheDir, "r7-p0c1-2-tile-geometry-${System.nanoTime()}.pdf")
        createGeometryFixture(pdfFile)
        val renderer = PdfPageRenderer(context)
        try {
            val opened = renderer.open(Uri.fromFile(pdfFile).toString())
            assertTrue(opened is PdfPageRenderer.OpenResult.Success)
            for (pageIndex in 0..1) {
                val pdfSize = renderer.pageSizePts(pageIndex)!!
                val fittedWidth = 256f
                val fittedHeight = fittedWidth * pdfSize.second / pdfSize.first
                for ((bucket, scale) in PdfZoomBuckets.scales.withIndex()) {
                    val referenceResult = renderer.renderPage(
                        pageIndex = pageIndex,
                        targetWidthPx = (fittedWidth * scale).roundToInt(),
                        maxEdgePx = 8192,
                        minEdgePx = 1,
                        maxPixels = 32_000_000,
                    ) as PdfPageRenderer.RenderResult.Success
                    val reference = referenceResult.image.asAndroidBitmap()
                    for (bleed in listOf(1, 2)) {
                        val composed = Bitmap.createBitmap(
                            reference.width,
                            reference.height,
                            Bitmap.Config.ARGB_8888,
                        ).apply { eraseColor(Color.WHITE) }
                        val canvas = Canvas(composed)
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                        val rotation = if (pageIndex == 1) 90 else 0
                        val view = PageTileViewport(
                            pageIndex = pageIndex,
                            pageWidth = fittedWidth,
                            pageHeight = fittedHeight,
                            visibleRect = PageLayoutRect(0f, 0f, fittedWidth, fittedHeight),
                            geometryGeneration = pageGeometryGeneration(fittedWidth, fittedHeight, rotation),
                            fittedPageWidthVersion = fittedWidth.toBits(),
                            rotation = rotation,
                        )
                        val requests = PdfTilePlanner.requests(
                            "fixture",
                            1L,
                            view,
                            bucket,
                            1L,
                            null,
                            null,
                        )
                        for (planned in requests) {
                            val request = planned.copy(
                                bleedPx = TilePixelInsets(bleed, bleed, bleed, bleed),
                            )
                            val tileResult = renderer.renderTile(request, view.pageSize)
                                as PdfPageRenderer.TileRenderResult.Success
                            val tile = tileResult.image.asAndroidBitmap()
                            val source = request.contentRectPx
                            val destination = Rect(
                                (request.pageRect.left * scale).roundToInt(),
                                (request.pageRect.top * scale).roundToInt(),
                                (request.pageRect.right * scale).roundToInt(),
                                (request.pageRect.bottom * scale).roundToInt(),
                            )
                            canvas.drawBitmap(
                                tile,
                                Rect(source.left, source.top, source.right, source.bottom),
                                destination,
                                paint,
                            )
                        }
                        assertOnlyAntialiasLevelDiff(
                            reference,
                            composed,
                            "page=$pageIndex rotation=$rotation scale=$scale bleed=$bleed",
                        )
                        composed.recycle()
                    }
                    reference.recycle()
                }
            }
        } finally {
            renderer.closeAsync()
            pdfFile.delete()
        }
    }

    private fun assertOnlyAntialiasLevelDiff(reference: Bitmap, actual: Bitmap, label: String) {
        assertTrue("$label width", reference.width == actual.width)
        assertTrue("$label height", reference.height == actual.height)
        val count = reference.width * reference.height
        val expected = IntArray(count)
        val observed = IntArray(count)
        reference.getPixels(expected, 0, reference.width, 0, 0, reference.width, reference.height)
        actual.getPixels(observed, 0, actual.width, 0, 0, actual.width, actual.height)
        var sumDelta = 0L
        var significantPixels = 0
        for (index in expected.indices) {
            val a = expected[index]
            val b = observed[index]
            val delta = max(
                max(abs(Color.red(a) - Color.red(b)), abs(Color.green(a) - Color.green(b))),
                abs(Color.blue(a) - Color.blue(b)),
            )
            sumDelta += delta
            if (delta > 48) significantPixels++
        }
        val meanDelta = sumDelta.toDouble() / count
        val significantFraction = significantPixels.toDouble() / count
        assertTrue("$label meanDelta=$meanDelta", meanDelta <= 2.0)
        assertTrue("$label significantFraction=$significantFraction", significantFraction <= .002)
    }

    private fun createGeometryFixture(file: File) {
        val document = PdfDocument()
        try {
            drawFixturePage(document, pageNumber = 1, width = 595, height = 842)
            drawFixturePage(document, pageNumber = 2, width = 842, height = 595)
            FileOutputStream(file).use(document::writeTo)
        } finally {
            document.close()
        }
    }

    private fun drawFixturePage(document: PdfDocument, pageNumber: Int, width: Int, height: Int) {
        val page = document.startPage(PdfDocument.PageInfo.Builder(width, height, pageNumber).create())
        val canvas = page.canvas
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(Color.WHITE)

        val cell = 32
        for (top in 0 until height step cell) {
            for (left in 0 until width step cell) {
                fill.color = if (((left / cell) + (top / cell)) and 1 == 0) 0xffffe0a0.toInt() else 0xffb0e0d0.toInt()
                canvas.drawRect(left.toFloat(), top.toFloat(), (left + cell).toFloat(), (top + cell).toFloat(), fill)
            }
        }

        fill.color = 0xff0066cc.toInt()
        fill.strokeWidth = 2f
        for (x in 0 until width step 73) canvas.drawLine(x.toFloat(), 0f, x.toFloat(), height.toFloat(), fill)
        for (y in 0 until height step 67) canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), fill)

        fill.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        fill.textSize = 26f
        fill.color = Color.BLACK
        canvas.drawText("CROSS TILE BOUNDARY  NOTES  PAGE $pageNumber", 41f, height * .55f, fill)
        canvas.drawText("TL", 4f, 28f, fill)
        canvas.drawText("TR", width - 42f, 28f, fill)
        canvas.drawText("BL", 4f, height - 8f, fill)
        canvas.drawText("BR", width - 42f, height - 8f, fill)

        val qrLeft = width * 3 / 5
        val qrTop = height / 8
        val module = 7
        fill.color = Color.WHITE
        canvas.drawRect(qrLeft.toFloat(), qrTop.toFloat(), (qrLeft + module * 21).toFloat(), (qrTop + module * 21).toFloat(), fill)
        fill.color = Color.BLACK
        for (row in 0 until 21) {
            for (column in 0 until 21) {
                val finder = finderModule(column, row, 0, 0) || finderModule(column, row, 14, 0) || finderModule(column, row, 0, 14)
                val data = ((column * 13 + row * 7 + column * row) % 5) < 2
                if (finder || data) {
                    canvas.drawRect(
                        (qrLeft + column * module).toFloat(),
                        (qrTop + row * module).toFloat(),
                        (qrLeft + (column + 1) * module).toFloat(),
                        (qrTop + (row + 1) * module).toFloat(),
                        fill,
                    )
                }
            }
        }

        fill.color = 0xffe53935.toInt()
        canvas.drawRect(width * .08f, height * .72f, width * .31f, height * .88f, fill)
        fill.color = 0xff43a047.toInt()
        canvas.drawRect(width * .34f, height * .72f, width * .57f, height * .88f, fill)
        fill.color = 0xff5e35b1.toInt()
        canvas.drawRect(width * .60f, height * .72f, width * .92f, height * .88f, fill)
        document.finishPage(page)
    }

    private fun finderModule(x: Int, y: Int, left: Int, top: Int): Boolean {
        if (x !in left until left + 7 || y !in top until top + 7) return false
        val localX = x - left
        val localY = y - top
        return localX == 0 || localY == 0 || localX == 6 || localY == 6 ||
            (localX in 2..4 && localY in 2..4)
    }
}
