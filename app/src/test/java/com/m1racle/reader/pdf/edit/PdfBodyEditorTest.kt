package com.m1racle.reader.pdf.edit

import androidx.test.core.app.ApplicationProvider
import com.m1racle.reader.pdf.PdfBoxRuntime
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.ConscryptMode(org.robolectric.annotation.ConscryptMode.Mode.OFF)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.LEGACY)
@Config(sdk = [34])
class PdfBodyEditorTest {
    @get:Rule val files = TemporaryFolder()
    @Before fun init() { check(PdfBoxRuntime.ensureInit(ApplicationProvider.getApplicationContext())) }

    private fun fixture(adjusted: Boolean = false, shared: Boolean = false): java.io.File {
        val file = files.newFile("source.pdf")
        PDDocument().use { doc ->
            val page = PDPage(); doc.addPage(page)
            PDPageContentStream(doc, page).use { out ->
                out.beginText(); out.setFont(PDType1Font.HELVETICA, 16f)
                out.newLineAtOffset(50f, 700f); out.setLeading(25f)
                out.setCharacterSpacing(0.8f); out.setWordSpacing(2f); out.setHorizontalScaling(85f)
                if (adjusted) out.showTextWithPositioning(arrayOf("Original", -120f, " text"))
                else out.showText("Original text")
                out.showText(" FOLLOWING")
                out.newLine(); out.showText("Next line")
                out.endText()
            }
            if (shared) {
                val other = PDPage(); other.resources = page.resources
                other.cosObject.setItem(com.tom_roush.pdfbox.cos.COSName.CONTENTS, page.cosObject.getItem(com.tom_roush.pdfbox.cos.COSName.CONTENTS))
                doc.addPage(other)
            }
            doc.save(file)
        }
        return file
    }

    private fun assertReplacement(adjusted: Boolean, shared: Boolean) {
        val source = fixture(adjusted, shared); val original = source.readBytes()
        val before = PdfBodyEditor.inspect(source, 0)
        val output = files.newFile("edited.pdf")
        PdfBodyEditor.writeCopy(source, output, mapOf(before.runs.first().key to "New text"))
        val after = PdfBodyEditor.inspect(output, 0)
        assertEquals("New text", after.runs.first().text)
        before.runs.drop(1).zip(after.runs.drop(1)).forEach { (a,b) ->
            assertEquals(a.text,b.text); assertEquals(a.left,b.left,0.000001f)
            assertEquals(a.top,b.top,0.000001f); assertEquals(a.right,b.right,0.000001f)
        }
        PDDocument.load(output).use { doc ->
            val stripper = PDFTextStripper(); stripper.endPage = 1
            val text = stripper.getText(doc)
            assertTrue(text.contains("New text")); assertFalse(text.contains("Original"))
        }
        if (shared) assertEquals(PdfBodyEditor.inspect(source,1), PdfBodyEditor.inspect(output,1))
        assertArrayEquals(original,source.readBytes())
    }

    @Test fun replacementPreservesFollowingTextAndLineMatrix() = assertReplacement(false,false)
    @Test fun tjSpacingAndSharedPageContentsStayIsolated() = assertReplacement(true,true)
    @Test fun invalidEditsNeverProduceOutputOrChangeOriginal() {
        val source = fixture(); val original = source.readBytes()
        val key = PdfBodyEditor.inspect(source,0).runs.first().key
        for (invalid in listOf("", "line\nbreak", "Much too long to fit inside the original text", "中文")) {
            val output = files.newFile()
            assertThrows(PdfEditRejected::class.java) { PdfBodyEditor.writeCopy(source,output,mapOf(key to invalid)) }
            assertFalse(output.exists()); assertArrayEquals(original,source.readBytes())
        }
        assertThrows(IllegalArgumentException::class.java) { PdfBodyEditor.writeCopy(source,source,mapOf(key to "New")) }
        assertArrayEquals(original,source.readBytes())
    }
    @Test fun rotationAndScannedPagesGiveExplicitReasons() {
        val source = files.newFile()
        PDDocument().use { doc ->
            doc.addPage(PDPage().apply { rotation = 90 }); doc.addPage(PDPage()); doc.save(source)
        }
        assertNotNull(PdfBodyEditor.inspect(source,0).reason)
        assertNotNull(PdfBodyEditor.inspect(source,1).reason)
    }
    @Test fun noOpSavePreservesTextGeometry() {
        val source = fixture(); val output = files.newFile()
        PdfBodyEditor.writeCopy(source,output,emptyMap())
        assertEquals(PdfBodyEditor.inspect(source,0),PdfBodyEditor.inspect(output,0))
    }
    @Test fun embeddedFullAndSubsetFontsRoundTripSupportedCharacters() {
        for (subset in listOf(false,true)) {
            val source = files.newFile()
            PDDocument().use { doc ->
                val page=PDPage(); doc.addPage(page)
                val input=com.tom_roush.pdfbox.android.PDFBoxResourceLoader.getStream("com/tom_roush/pdfbox/resources/ttf/LiberationSans-Regular.ttf")
                val font=input.use { com.tom_roush.pdfbox.pdmodel.font.PDType0Font.load(doc,it,subset) }
                PDPageContentStream(doc,page).use { out ->
                    out.beginText(); out.setFont(font,16f); out.newLineAtOffset(50f,700f)
                    out.showText("Original text"); out.endText()
                }
                doc.save(source)
            }
            val output=files.newFile(); val key=PdfBodyEditor.inspect(source,0).runs.single().key
            PdfBodyEditor.writeCopy(source,output,mapOf(key to "text"))
            assertEquals("text",PdfBodyEditor.inspect(output,0).runs.single().text)
            if(subset) {
                val rejected=files.newFile()
                assertThrows(PdfEditRejected::class.java) { PdfBodyEditor.writeCopy(source,rejected,mapOf(key to "Z")) }
                assertFalse(rejected.exists())
            }
        }
    }

    @Test fun unsupportedFormsAndAlternateTextCannotBeRewritten() {
        for (form in listOf(false,true)) {
            val source=fixture()
            PDDocument.load(source).use { doc ->
                val page=doc.getPage(0)
                PDPageContentStream(doc,page,PDPageContentStream.AppendMode.APPEND,true,true).use { out ->
                    if(form) {
                        val nested=com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject(doc)
                        nested.bBox=com.tom_roush.pdfbox.pdmodel.common.PDRectangle(20f,20f)
                        nested.cosObject.createOutputStream().use { it.write(byteArrayOf()) }
                        out.drawForm(nested)
                    } else {
                        @Suppress("DEPRECATION")
                        out.appendRawCommands("/Span << /ActualText (alternative) >> BDC EMC\n")
                    }
                }
                doc.save(files.root.resolve("structured.pdf"))
            }
            val structured=files.root.resolve("structured.pdf")
            val page=PdfBodyEditor.inspect(structured,0)
            assertNotNull(page.reason)
            val output=files.newFile()
            assertThrows(PdfEditRejected::class.java) { PdfBodyEditor.writeCopy(structured,output,mapOf(page.runs.first().key to "New")) }
            assertFalse(output.exists())
            source.delete()
        }
    }

    @Test fun repeatedEditsUndoAndRedoUseTheImmutableSource() {
        val source=fixture(true,true); val original=source.readBytes()
        val before=PdfBodyEditor.inspect(source,0)
        val first=mapOf(before.runs[0].key to "New")
        val second=first+mapOf(before.runs[2].key to "Next")
        for(edits in listOf(first,second,first,emptyMap(),first,second)) {
            val output=files.newFile(); PdfBodyEditor.writeCopy(source,output,edits)
            val after=PdfBodyEditor.inspect(output,0)
            before.runs.zip(after.runs).forEach { (a,b) ->
                assertEquals(edits[a.key] ?: a.text,b.text)
                assertEquals(a.left,b.left,0.000001f); assertEquals(a.top,b.top,0.000001f)
            }
            assertEquals(PdfBodyEditor.inspect(source,1),PdfBodyEditor.inspect(output,1))
        }
        assertArrayEquals(original,source.readBytes())
    }

    @Test fun chineseAndMixedTextWithFullAndSubsetFonts() {
        for (subset in listOf(false,true)) {
            val source=files.newFile()
            PDDocument().use { doc ->
                val page=PDPage(); doc.addPage(page)
                val input=javaClass.getResourceAsStream("/fixtures/pdf-edit/ChineseFixture.ttf")!!
                val font=input.use { com.tom_roush.pdfbox.pdmodel.font.PDType0Font.load(doc,it,subset) }
                PDPageContentStream(doc,page).use { out ->
                    out.beginText(); out.setFont(font,16f); out.newLineAtOffset(50f,700f)
                    out.showText("原始正文 ABC"); out.showText("正文"); out.endText()
                }
                doc.save(source)
            }
            val before=PdfBodyEditor.inspect(source,0)
            val output=files.newFile(); val replacement=if(subset) "正文 ABC" else "中文 ABC"
            PdfBodyEditor.writeCopy(source,output,mapOf(before.runs.first().key to replacement))
            val after=PdfBodyEditor.inspect(output,0)
            assertEquals(replacement,after.runs.first().text)
            assertEquals(before.runs.last().left,after.runs.last().left,0.000001f)
            PDDocument.load(output).use { doc -> assertTrue(PDFTextStripper().getText(doc).contains(replacement)) }
        }
    }

    @Test fun passwordProtectedDocumentIsRejectedWithoutAnOutput() {
        val source=fixture(); val protected=files.newFile()
        PDDocument.load(source).use { doc ->
            val access=com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission()
            doc.protect(com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy("owner","reader",access))
            doc.save(protected)
        }
        assertThrows(PdfEditRejected::class.java) { PdfBodyEditor.inspect(protected,0) }
        val output=files.newFile()
        assertThrows(PdfEditRejected::class.java) { PdfBodyEditor.writeCopy(protected,output,emptyMap()) }
        assertFalse(output.exists())
    }

    @Test fun ordinaryTaggedTextCanBeEditedWithoutDroppingItsMarkers() {
        val source=files.newFile()
        PDDocument().use { doc ->
            val page=PDPage(); doc.addPage(page)
            PDPageContentStream(doc,page).use { out ->
                @Suppress("DEPRECATION")
                out.appendRawCommands("/P << /MCID 0 >> BDC\n")
                out.beginText(); out.setFont(PDType1Font.HELVETICA,16f); out.newLineAtOffset(50f,700f)
                out.showText("Original text"); out.endText()
                @Suppress("DEPRECATION")
                out.appendRawCommands("EMC\n")
            }
            doc.save(source)
        }
        val page=PdfBodyEditor.inspect(source,0); assertNull(page.reason)
        val output=files.newFile(); PdfBodyEditor.writeCopy(source,output,mapOf(page.runs.single().key to "New"))
        assertEquals("New",PdfBodyEditor.inspect(output,0).runs.single().text)
        PDDocument.load(output).use { doc ->
            val commands=doc.getPage(0).contents.use { it.readBytes().toString(Charsets.ISO_8859_1) }
            assertTrue(commands.contains("/MCID")); assertTrue(commands.contains("BDC")); assertTrue(commands.contains("EMC"))
        }
    }

}
