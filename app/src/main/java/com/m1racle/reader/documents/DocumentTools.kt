package com.m1racle.reader.documents

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.coroutineContext

/** Produces new files only; the caller owns explicit destination selection and import. */
class DocumentTools(private val context: Context) {
    private fun output(suffix: String) = File(context.cacheDir, "document-tools").apply { mkdirs() }
        .resolve("${UUID.randomUUID()}.$suffix")

    suspend fun createPdf(text: String, blankPages: Int = 1): File = withContext(Dispatchers.IO) {
        require(blankPages in 1..1000) { "页数须为1至1000" }
        val file = output("pdf")
        try {
            val doc = PdfDocument()
            try {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f; color = android.graphics.Color.BLACK }
                var number = 0
                var page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, ++number).create())
                var y = 54f
                if (text.isNotEmpty()) for (paragraph in text.split('\n')) {
                    var remaining = paragraph
                    do {
                        coroutineContext.ensureActive()
                        if (y > 790f) {
                            doc.finishPage(page)
                            page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, ++number).create()); y = 54f
                        }
                        var count = paint.breakText(remaining, true, 499f, null).coerceAtLeast(1).coerceAtMost(remaining.length)
                        if (count < remaining.length && count > 0 && Character.isHighSurrogate(remaining[count - 1])) count--
                        if (count == 0 && remaining.isNotEmpty()) count = Character.charCount(remaining.codePointAt(0))
                        page.canvas.drawText(remaining.take(count), 48f, y, paint)
                        remaining = remaining.drop(count); y += 19f
                    } while (remaining.isNotEmpty())
                }
                doc.finishPage(page)
                if (text.isEmpty()) repeat(blankPages - 1) {
                    coroutineContext.ensureActive()
                    doc.finishPage(doc.startPage(PdfDocument.PageInfo.Builder(595, 842, ++number).create()))
                }
                file.outputStream().use(doc::writeTo)
            } finally { doc.close() }
            file
        } catch (e: Exception) { file.delete(); throw e }
    }

    suspend fun merge(inputs: List<Uri>): File = withContext(Dispatchers.IO) {
        require(inputs.size >= 2) { "请至少选择两个PDF" }
        PDFBoxResourceLoader.init(context)
        val file = output("pdf")
        try {
            PDDocument().use { result ->
                val merger = PDFMergerUtility()
                for (uri in inputs) {
                    coroutineContext.ensureActive()
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        PDDocument.load(stream).use { source ->
                            check(!source.isEncrypted) { "请先提供未加密的PDF副本" }
                            merger.appendDocument(result, source)
                        }
                    } ?: error("无法读取所选文件")
                }
                check(result.numberOfPages > 0) { "PDF没有页面" }
                result.save(file)
            }
            PDDocument.load(file).use { check(it.numberOfPages > 0) }
            file
        } catch (e: Exception) { file.delete(); throw e }
    }

    suspend fun encrypt(input: Uri, password: String): File = withContext(Dispatchers.IO) {
        require(password.isNotBlank()) { "请输入打开密码" }
        PDFBoxResourceLoader.init(context)
        val file = output("pdf")
        try {
            context.contentResolver.openInputStream(input)?.use { stream -> PDDocument.load(stream).use { doc ->
                check(!doc.isEncrypted) { "文件已加密，请使用未加密副本" }
                val owner = ByteArray(32).also(java.security.SecureRandom()::nextBytes)
                    .joinToString("") { "%02x".format(it) }
                doc.protect(StandardProtectionPolicy(owner, password, AccessPermission()).apply {
                    encryptionKeyLength = 256; isPreferAES = true
                })
                doc.save(file)
            } } ?: error("无法读取文件")
            PDDocument.load(file, password).use { check(it.isEncrypted) }
            file
        } catch (e: Exception) { file.delete(); throw e }
    }

    suspend fun toMarkdown(input: Uri): File = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context)
        val file = output("md")
        try {
            context.contentResolver.openInputStream(input)?.use { stream -> PDDocument.load(stream).use { doc ->
                check(doc.currentAccessPermission.canExtractContent()) { "此PDF不允许提取文字" }
                val stripper = PDFTextStripper().apply { sortByPosition = true }
                file.bufferedWriter(Charsets.UTF_8).use { writer ->
                    var hasText = false
                    for (page in 1..doc.numberOfPages) {
                        coroutineContext.ensureActive()
                        stripper.startPage = page; stripper.endPage = page
                        val text = stripper.getText(doc).trim()
                        hasText = hasText || text.isNotEmpty()
                        writer.append("## 第 $page 页\n\n").append(text).append("\n\n")
                    }
                    check(hasText) { "未找到可提取文字；扫描件需要OCR" }
                }
            } } ?: error("无法读取文件")
            file
        } catch (e: Exception) { file.delete(); throw e }
    }
}
