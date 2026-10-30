package com.m1racle.reader.pdf

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfRenderMetadataInstrumentedTest {
    @Test fun nativeDimensionsSurviveDownsamplingAndAvoidMetadataPreflight() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "render-metadata-${System.nanoTime()}.pdf")
        val dimensions = listOf(613 to 797, 997 to 431)
        val pdf = PdfDocument()
        try {
            dimensions.forEachIndexed { index, (width, height) ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, index + 1).create())
                val paint = Paint().apply { color = Color.BLACK; textSize = 13f }
                repeat(25) { page.canvas.drawText("PDF reading sample $it", 20f, 20f + it * 15f, paint) }
                pdf.finishPage(page)
            }
            file.outputStream().use(pdf::writeTo)
        } finally {
            pdf.close()
        }
        val renderer = PdfPageRenderer(context)
        try {
            assertTrue(renderer.open(Uri.fromFile(file).toString()) is PdfPageRenderer.OpenResult.Success)
            dimensions.forEachIndexed { index, (width, height) ->
                for (target in listOf(137, 800)) {
                    val expectedSize = renderer.pageSizePts(index)!!
                    val reference = renderer.renderPage(index, target, minEdgePx = 1) as PdfPageRenderer.RenderResult.Success
                    val direct = renderer.renderPage(index, target, minEdgePx = 1) as PdfPageRenderer.RenderResult.Success
                    try {
                        assertEquals(width, direct.pageSize.width)
                        assertEquals(height, direct.pageSize.height)
                        assertEquals(expectedSize.first, direct.pageSize.width)
                        assertEquals(expectedSize.second, direct.pageSize.height)
                        assertTrue(reference.image.asAndroidBitmap().sameAs(direct.image.asAndroidBitmap()))
                    } finally {
                        reference.image.asAndroidBitmap().recycle()
                        direct.image.asAndroidBitmap().recycle()
                    }
                }
            }
            // Alternate order to reduce warmup/order bias. Timing is evidence, not a flaky gate.
            val preflight = mutableListOf<Long>()
            val direct = mutableListOf<Long>()
            repeat(26) { iteration ->
                val orders = if (iteration % 2 == 0) listOf(true, false) else listOf(false, true)
                for (withPreflight in orders) {
                    val start = System.nanoTime()
                    if (withPreflight) renderer.pageSizePts(iteration % 2)
                    val result = renderer.renderPage(iteration % 2, 800) as PdfPageRenderer.RenderResult.Success
                    val elapsed = System.nanoTime() - start
                    result.image.asAndroidBitmap().recycle()
                    if (iteration >= 6) (if (withPreflight) preflight else direct).add(elapsed)
                }
            }
            Log.i("PDF_METADATA_PERF", "preflightNs=$preflight directNs=$direct")
            assertEquals(PdfPageRenderer.RenderResult.InvalidPage, renderer.renderPage(2, 800))
            renderer.closeAsync()
            assertEquals(PdfPageRenderer.RenderResult.Closed, renderer.renderPage(0, 800))
        } finally {
            renderer.closeAsync()
            file.delete()
        }
    }
}
