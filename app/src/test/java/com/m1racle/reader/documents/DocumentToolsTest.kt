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
}
