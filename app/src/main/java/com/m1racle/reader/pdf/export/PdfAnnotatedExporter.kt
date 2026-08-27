package com.m1racle.reader.pdf.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.util.Log
import com.m1racle.reader.pdf.PdfBoxRuntime
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.min

/**
 * Export a page range as a **single annotated PDF**:
 * - cover page: chapter path, PDF page range, total pages
 * - content pages: PDFBox importPage (vector, not screenshots)
 * - footer stamp on each content page: chapter · export i/n · source p.x
 */
class PdfAnnotatedExporter(
    context: Context,
) {
    private val appContext = context.applicationContext

    suspend fun export(
        sourceUri: Uri,
        documentDisplayName: String,
        plan: ExportRangePlan,
        outputUri: Uri,
        cancellation: ExportCancellationToken,
        onState: (ChapterExportState) -> Unit,
    ): ChapterExportState = withContext(Dispatchers.IO) {
        val workDir = File(appContext.cacheDir, "pdf_export_${System.currentTimeMillis()}")
        var destinationCommitted = false
        try {
            onState(ChapterExportState.Preparing)
            if (!workDir.mkdirs()) {
                return@withContext fail("无法创建临时目录", workDir, onState)
            }
            throwIfExportCancelled(cancellation) { coroutineContext.isActive }
            if (!PdfBoxRuntime.ensureInit(appContext)) {
                return@withContext fail("PDF 组件初始化失败", workDir, onState)
            }

            val sourceTemp = File(workDir, "_source.tmp")
            val outPdf = File(workDir, "annotated.pdf")
            val input = appContext.contentResolver.openInputStream(sourceUri)
                ?: return@withContext fail("无法打开源 PDF", workDir, onState)
            input.use { src ->
                FileOutputStream(sourceTemp).use { dst -> src.copyTo(dst) }
            }
            throwIfExportCancelled(cancellation) { coroutineContext.isActive }

            val chapterTitle = plan.pathTitles.lastOrNull()?.ifBlank { null }
                ?: plan.node.title
            val pathLabel = plan.pathTitles.joinToString(" / ").ifBlank { chapterTitle }
            val total = plan.pageCount

            onState(ChapterExportState.CopyingPages(0, total))
            val mem = PdfBoxRuntime.memorySetting()
            FileInputStream(sourceTemp).use { srcIn ->
                PDDocument.load(srcIn, mem).use { source ->
                    val srcPages = source.numberOfPages
                    if (plan.endPageIndex >= srcPages) {
                        return@withContext fail(
                            "结束页超出源文档 (共 $srcPages 页)",
                            workDir,
                            onState,
                        )
                    }
                    PDDocument().use { dest ->
                        val font = loadBestFont(dest)

                        // Cover page (Android canvas → image → PDF page) for reliable CJK.
                        addCoverPage(
                            dest = dest,
                            documentDisplayName = documentDisplayName,
                            pathLabel = pathLabel,
                            chapterTitle = chapterTitle,
                            plan = plan,
                        )

                        for (i in 0 until total) {
                            throwIfExportCancelled(cancellation) { coroutineContext.isActive }
                            val srcIndex = plan.startPageIndex + i
                            try {
                                val page = source.getPage(srcIndex)
                                val imported = dest.importPage(page)
                                stampFooter(
                                    doc = dest,
                                    page = imported,
                                    font = font,
                                    chapterTitle = chapterTitle,
                                    exportIndex1 = i + 1,
                                    exportTotal = total,
                                    sourcePage1 = srcIndex + 1,
                                )
                            } catch (t: Throwable) {
                                Log.e(TAG, "import/stamp failed at $srcIndex", t)
                                return@withContext fail(
                                    "复制第 ${srcIndex + 1} 页失败: ${t.message ?: t.javaClass.simpleName}",
                                    workDir,
                                    onState,
                                )
                            }
                            onState(ChapterExportState.CopyingPages(i + 1, total))
                        }

                        throwIfExportCancelled(cancellation) { coroutineContext.isActive }
                        onState(ChapterExportState.WritingArchive)
                        dest.save(outPdf)
                    }
                }
            }

            throwIfExportCancelled(cancellation) { coroutineContext.isActive }

            // A successful stream close commits the destination. Any cancellation
            // before this boundary is handled by the outer cleanup path.
            appContext.contentResolver.openOutputStream(outputUri)?.use { out ->
                FileInputStream(outPdf).use { inp ->
                    copyWithExportCancellation(inp, out, cancellation) { coroutineContext.isActive }
                }
            } ?: return@withContext fail("无法写入所选位置", workDir, onState)
            destinationCommitted = true

            val name = ExportRangeResolver.defaultPdfName(documentDisplayName, plan.pathTitles)
            val done = ChapterExportState.Completed(displayName = name)
            onState(done)
            cleanupDir(workDir)
            done
        } catch (e: CancellationException) {
            if (!destinationCommitted) {
                deleteSafDocumentBestEffort(appContext.contentResolver, outputUri)
            }
            cancel(workDir, onState)
            throw e
        } catch (t: Throwable) {
            Log.e(TAG, "annotated export failed", t)
            fail(t.message ?: "导出失败", workDir, onState)
        }
    }

    private fun addCoverPage(
        dest: PDDocument,
        documentDisplayName: String,
        pathLabel: String,
        chapterTitle: String,
        plan: ExportRangePlan,
    ) {
        val w = 1240
        val h = 1754
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 20, 20)
            textSize = 64f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(50, 50, 50)
            textSize = 40f
            typeface = Typeface.DEFAULT
        }
        val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(110, 110, 110)
            textSize = 34f
        }

        var y = 180f
        canvas.drawText("章节导出", 96f, y, mutedPaint)
        y += 100f
        for (line in wrapText(chapterTitle, titlePaint, w - 192f)) {
            canvas.drawText(line, 96f, y, titlePaint)
            y += 76f
        }
        y += 40f
        canvas.drawText("来源文档", 96f, y, mutedPaint)
        y += 56f
        for (line in wrapText(documentDisplayName, bodyPaint, w - 192f)) {
            canvas.drawText(line, 96f, y, bodyPaint)
            y += 52f
        }
        y += 36f
        canvas.drawText("目录路径", 96f, y, mutedPaint)
        y += 56f
        for (line in wrapText(pathLabel, bodyPaint, w - 192f)) {
            canvas.drawText(line, 96f, y, bodyPaint)
            y += 52f
        }
        y += 48f
        canvas.drawText(
            "PDF 页码：第 ${plan.pdfPageStart1Based}–${plan.pdfPageEnd1Based} 页（共 ${plan.pageCount} 页）",
            96f,
            y,
            bodyPaint,
        )
        y += 56f
        if (plan.printPageStart != null && plan.printPageEnd != null) {
            canvas.drawText(
                "教材印刷页：第 ${plan.printPageStart}–${plan.printPageEnd} 页",
                96f,
                y,
                bodyPaint,
            )
            y += 56f
        }
        y += 24f
        canvas.drawText("结束页规则：${plan.endSource.labelZh()}", 96f, y, mutedPaint)
        y += 80f
        canvas.drawText("说明：后续页面为原文抽取；页脚标注章节与源页码。", 96f, y, mutedPaint)

        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 90, baos)
        bmp.recycle()
        val bytes = baos.toByteArray()
        val page = PDPage(PDRectangle.A4)
        dest.addPage(page)
        val img = JPEGFactory.createFromByteArray(dest, bytes)
        PDPageContentStream(dest, page).use { cs ->
            val box = page.mediaBox
            cs.drawImage(img, 0f, 0f, box.width, box.height)
        }
    }

    private fun stampFooter(
        doc: PDDocument,
        page: PDPage,
        font: com.tom_roush.pdfbox.pdmodel.font.PDFont,
        chapterTitle: String,
        exportIndex1: Int,
        exportTotal: Int,
        sourcePage1: Int,
    ) {
        val box = page.mediaBox
        val margin = 18f
        val fontSize = 8f
        val text = buildString {
            append(chapterTitle.take(40))
            if (chapterTitle.length > 40) append('…')
            append("  ·  ")
            append(exportIndex1)
            append('/')
            append(exportTotal)
            append("  ·  源 PDF p.")
            append(sourcePage1)
        }
        try {
            // pdfbox-android 2.0.x: (doc, page, append, compress, resetContext)
            PDPageContentStream(doc, page, true, true, true).use { cs ->
                cs.beginText()
                cs.setFont(font, fontSize)
                cs.setNonStrokingColor(0.35f, 0.35f, 0.35f)
                // Bottom-left footer in page space
                cs.setTextMatrix(Matrix.getTranslateInstance(margin, margin))
                cs.showText(sanitizeForPdfString(text, font))
                cs.endText()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "footer stamp failed p.$sourcePage1", t)
        }
    }

    private fun loadBestFont(doc: PDDocument): com.tom_roush.pdfbox.pdmodel.font.PDFont {
        val candidates = listOf(
            "/system/fonts/NotoSansCJK-Regular.ttc",
            "/system/fonts/NotoSansSC-Regular.otf",
            "/system/fonts/NotoSansSC-Regular.ttf",
            "/system/fonts/DroidSansFallback.ttf",
            "/system/fonts/Roboto-Regular.ttf",
        )
        for (path in candidates) {
            val f = File(path)
            if (!f.isFile) continue
            try {
                return PDType0Font.load(doc, f)
            } catch (_: Throwable) {
                // try next
            }
        }
        @Suppress("DEPRECATION")
        return PDType1Font.HELVETICA
    }

    /** Drop characters the embedded font cannot encode (rare when CJK font loads). */
    private fun sanitizeForPdfString(
        raw: String,
        font: com.tom_roush.pdfbox.pdmodel.font.PDFont,
    ): String {
        return try {
            // Probe encode; if fails, strip non-latin
            font.encode(raw)
            raw
        } catch (_: Exception) {
            raw.map { ch ->
                if (ch.code < 128) ch else '?'
            }.joinToString("")
        }
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isEmpty()) return listOf("—")
        val lines = ArrayList<String>()
        var rest = text
        while (rest.isNotEmpty()) {
            var end = rest.length
            while (end > 0 && paint.measureText(rest.substring(0, end)) > maxWidth) {
                end--
            }
            if (end <= 0) end = 1
            // Prefer break at punctuation/space
            if (end < rest.length) {
                val slice = rest.substring(0, end)
                val br = maxOf(slice.lastIndexOf(' '), slice.lastIndexOf('/'), slice.lastIndexOf('、'))
                if (br > end / 3) end = br + 1
            }
            lines.add(rest.substring(0, end).trimEnd())
            rest = rest.substring(end).trimStart()
            if (lines.size >= 8) {
                if (rest.isNotEmpty()) lines[lines.lastIndex] = lines.last() + "…"
                break
            }
        }
        return lines
    }

    private fun fail(
        msg: String,
        workDir: File,
        onState: (ChapterExportState) -> Unit,
    ): ChapterExportState {
        cleanupDir(workDir)
        val s = ChapterExportState.Failed(msg)
        onState(s)
        return s
    }

    private fun cancel(
        workDir: File,
        onState: (ChapterExportState) -> Unit,
    ): ChapterExportState {
        cleanupDir(workDir)
        onState(ChapterExportState.Cancelled)
        return ChapterExportState.Cancelled
    }

    companion object {
        private const val TAG = "PdfAnnotatedExport"

        fun cleanupDir(dir: File?) {
            ChapterPackageExporter.cleanupDir(dir)
        }
    }
}
