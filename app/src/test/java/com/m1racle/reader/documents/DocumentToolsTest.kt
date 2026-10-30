package com.m1racle.reader.documents

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.m1racle.reader.pdf.PdfBoxRuntime
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

@RunWith(RobolectricTestRunner::class)
class DocumentToolsTest {
    @get:Rule val folder = TemporaryFolder()
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    @Before fun init() { check(PdfBoxRuntime.ensureInit(context)) }
    private fun pdf(name: String, text: String): java.io.File = folder.newFile(name).also { file ->
        PDDocument().use { doc ->
            val page = PDPage(); doc.addPage(page)
            PDPageContentStream(doc, page).use {
                it.beginText(); it.setFont(PDType1Font.HELVETICA, 12f); it.newLineAtOffset(40f, 700f)
                it.showText(text); it.endText()
            }
            doc.save(file)
        }
    }
    private fun twoPagePdf(name: String): File = folder.newFile(name).also { file ->
        PDDocument().use { doc ->
            doc.addPage(PDPage())
            doc.addPage(PDPage())
            doc.save(file)
        }
    }
    private fun ownedTempFiles(): Set<String> =
        listOf("document-tools", "document-tools-staging").flatMap { rootName ->
            File(context.cacheDir, rootName).walkTopDown()
                .filter(File::isFile)
                .map { it.relativeTo(context.cacheDir).invariantSeparatorsPath }
                .toList()
        }.toSet()

    @Test fun mergePreservesOrderAndOriginalBytes() = runBlocking {
        val one = pdf("one.pdf", "First document"); val two = pdf("two.pdf", "Second document")
        val before = one.readBytes()
        val result = DocumentTools(context).merge(listOf(Uri.fromFile(one), Uri.fromFile(two)))
        try { PDDocument.load(result).use { doc ->
            assertEquals(2, doc.numberOfPages)
            val text = PDFTextStripper().getText(doc)
            assertTrue(text.indexOf("First document") < text.indexOf("Second document"))
        }; assertArrayEquals(before, one.readBytes()) } finally { result.delete() }
    }
    @Test fun encryptionRequiresPasswordAndPreservesPages() = runBlocking {
        val source = pdf("source.pdf", "Protected text"); val before = source.readBytes()
        val result = DocumentTools(context).encrypt(Uri.fromFile(source), "correct-password")
        try {
            assertTrue(runCatching { PDDocument.load(result, "wrong").close() }.isFailure)
            PDDocument.load(result, "correct-password").use { assertTrue(it.isEncrypted); assertEquals(1, it.numberOfPages) }
            assertArrayEquals(before, source.readBytes())
        } finally { result.delete() }
    }
    @Test fun conversionContainsPageTextAndRejectsImageOnlyDocument() = runBlocking {
        val source = pdf("text.pdf", "Actual source text")
        val result = DocumentTools(context).toMarkdown(Uri.fromFile(source))
        try { assertTrue(result.readText().contains("Actual source text")); assertTrue(result.readText().contains("第 1 页")) } finally { result.delete() }
        val blank = folder.newFile("blank.pdf")
        PDDocument().use { it.addPage(PDPage()); it.save(blank) }
        assertTrue(runCatching { DocumentTools(context).toMarkdown(Uri.fromFile(blank)) }.isFailure)
    }
    @Test fun discoveryExcludesPrivateAndUnsupportedFiles() = runBlocking {
        val root = folder.newFolder("shared")
        root.resolve("a.PDF").writeText("pdf"); root.resolve("b.epub").writeText("epub")
        root.resolve("picture.png").writeText("image")
        root.resolve("Android/data").mkdirs(); root.resolve("Android/data/private.pdf").writeText("private")
        root.resolve(".hidden").mkdir(); root.resolve(".hidden/secret.md").writeText("secret")
        assertEquals(setOf("a.PDF", "b.epub"), DocumentDiscovery.scan(root).map { it.name }.toSet())
    }

    @Test fun mergeRejectsInputCountBeforeReadingSources() = runBlocking {
        val limits = PdfToolLimits(maxInputs = 2)
        val inputs = List(3) { folder.newFile("input-$it.pdf").apply { writeText("untouched-$it") } }
        val before = inputs.map { it.readBytes() }

        val failure = runCatching {
            DocumentTools(context, limits).merge(inputs.map(Uri::fromFile))
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals("最多合并 2 份PDF", failure?.message)
        inputs.forEachIndexed { index, file -> assertArrayEquals(before[index], file.readBytes()) }
    }

    @Test fun mergeRejectsSingleLimitWhileStreamingAndPreservesSource() = runBlocking {
        val limits = PdfToolLimits(maxSingleInputBytes = 8, maxTotalInputBytes = 32)
        val source = folder.newFile("large.pdf").apply { writeBytes(ByteArray(9) { it.toByte() }) }
        val other = folder.newFile("other.pdf").apply { writeText("other") }
        val before = source.readBytes()
        val beforeOutputs = ownedTempFiles()

        val failure = runCatching {
            DocumentTools(context, limits).merge(listOf(Uri.fromFile(source), Uri.fromFile(other)))
        }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertTrue(failure?.message.orEmpty().contains("单个PDF超过"))
        assertArrayEquals(before, source.readBytes())
        assertEquals(beforeOutputs, ownedTempFiles())
    }

    @Test fun mergeRejectsUnknownSizeStreamsAtCombinedLimitAndPreservesSources() = runBlocking {
        val limits = PdfToolLimits(maxSingleInputBytes = 16, maxTotalInputBytes = 12)
        val first = folder.newFile("first.pdf").apply { writeBytes(ByteArray(8)) }
        val second = folder.newFile("second.pdf").apply { writeBytes(ByteArray(8)) }
        val before = listOf(first, second).map { it.readBytes() }

        val failure = runCatching {
            DocumentTools(context, limits).merge(listOf(Uri.fromFile(first), Uri.fromFile(second)))
        }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertTrue(failure?.message.orEmpty().contains("总大小超过"))
        listOf(first, second).forEachIndexed { index, file -> assertArrayEquals(before[index], file.readBytes()) }
    }

    @Test fun unknownLengthInputStopsWritingAtTheLimit() {
        val limits = PdfToolLimits(maxSingleInputBytes = 10, maxTotalInputBytes = 100)
        val source = object : InputStream() {
            private var emitted = 0
            override fun read(): Int = throw UnsupportedOperationException()
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                val count = minOf(length, 7)
                emitted += count
                buffer.fill(1, offset, offset + count)
                return count
            }
        }
        val destination = ByteArrayOutputStream()

        val failure = runCatching { copyPdfInputBounded(source, destination, limits) }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertEquals(7, destination.size())
        assertTrue(destination.size() <= limits.maxSingleInputBytes)
    }

    @Test fun pageLimitRejectsOversizedPdfAndCleansOwnedOutput() = runBlocking {
        val limits = PdfToolLimits(maxPagesPerInput = 1, maxTotalPages = 1)
        val source = twoPagePdf("two-pages.pdf")
        val before = source.readBytes()
        val beforeOutputs = ownedTempFiles()

        val failure = runCatching {
            DocumentTools(context, limits).encrypt(Uri.fromFile(source), "password")
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("页上限"))
        assertArrayEquals(before, source.readBytes())
        assertEquals(beforeOutputs, ownedTempFiles())
    }

    @Test fun mergeRejectsAggregatePageLimitAndPreservesInputs() = runBlocking {
        val limits = PdfToolLimits(maxPagesPerInput = 2, maxTotalPages = 1)
        val first = pdf("first.pdf", "First")
        val second = pdf("second.pdf", "Second")
        val before = listOf(first, second).map { it.readBytes() }
        val beforeOutputs = ownedTempFiles()

        val failure = runCatching {
            DocumentTools(context, limits).merge(listOf(Uri.fromFile(first), Uri.fromFile(second)))
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("合并结果超过 1 页上限"))
        listOf(first, second).forEachIndexed { index, file -> assertArrayEquals(before[index], file.readBytes()) }
        assertEquals(beforeOutputs, ownedTempFiles())
    }
}
