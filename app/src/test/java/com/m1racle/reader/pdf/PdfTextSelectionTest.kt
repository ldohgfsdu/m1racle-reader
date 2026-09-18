package com.m1racle.reader.pdf

import androidx.test.core.app.ApplicationProvider
import com.m1racle.reader.data.anchor.AnchorCodec
import com.m1racle.reader.data.anchor.DocumentAnchor
import com.m1racle.reader.data.anchor.NormalizedRect
import com.m1racle.reader.ui.annotation.PdfHighlightGeometry
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
@org.robolectric.annotation.ConscryptMode(org.robolectric.annotation.ConscryptMode.Mode.OFF)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.LEGACY)
class PdfTextSelectionTest {
    @Before fun init() { check(PdfBoxRuntime.ensureInit(ApplicationProvider.getApplicationContext())) }

    private fun fixture(rotation: Int = 0, crop: Boolean = false, columns: Boolean = false): PdfSelectablePage = PDDocument().use { doc ->
        val page = PDPage(PDRectangle(400f,600f)).apply {
            this.rotation = rotation
            if (crop) cropBox = PDRectangle(20f,30f,300f,500f)
        }
        doc.addPage(page)
        PDPageContentStream(doc,page).use { out ->
            fun line(x: Float,y: Float,text: String) {
                out.beginText();out.setFont(PDType1Font.HELVETICA,16f)
                out.newLineAtOffset(x,y);out.showText(text);out.endText()
            }
            line(50f,450f,"First line")
            line(50f,420f,"Second line")
            if (columns) { line(250f,450f,"Other");line(250f,420f,"Column") }
        }
        // Exercise serialized input, matching reopening a real PDF.
        val bytes = java.io.ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
        PDDocument.load(bytes).use { PdfTextSelectionReader.extract(it,0) }
    }

    @Test fun multiLineSelectionUsesGlyphRangesNotTheDragBoundingBox() {
        val page=fixture()
        assertEquals(2,page.lines.size)
        val selection=page.selection(PdfSelectablePage.Hit(0,2),PdfSelectablePage.Hit(1,3))
        assertEquals("rst line\nSeco",selection.excerpt)
        assertEquals(2,selection.anchor.rects.size)
        assertTrue(selection.anchor.rects[0].y+selection.anchor.rects[0].height < selection.anchor.rects[1].y)
        assertTrue(selection.anchor.rects[0].x > selection.anchor.rects[1].x)
        val gapY=(selection.anchor.rects[0].y+selection.anchor.rects[0].height+selection.anchor.rects[1].y)/2
        assertNull(PdfHighlightGeometry.hitTest(selection.anchor.rects.map { 1 to it },0.15f,gapY,1f,1f))
    }
    @Test fun reverseDragSelectsTheSameCharacters() {
        val page=fixture();val a=PdfSelectablePage.Hit(0,1);val b=PdfSelectablePage.Hit(1,5)
        assertEquals(page.selection(a,b),page.selection(b,a))
    }
    @Test fun blankSpaceDoesNotStartASelectionButSingleGlyphCanBeHighlighted() {
        val page=fixture()
        assertNull(page.hit(0.95f,0.95f))
        val r=page.lines[0].glyphs[0].rect
        val hit=page.hit(r.x+r.width/2,r.y+r.height/2)!!
        assertEquals("F",page.selection(hit,hit).excerpt)
        assertEquals(1,page.selection(hit,hit).anchor.rects.size)
    }
    @Test fun neighboringColumnIsNotIncludedInLeftColumnSelection() {
        val page=fixture(columns=true)
        val first=page.lines.indexOfFirst { it.glyphs.joinToString(""){it.text}.startsWith("First") }
        val second=page.lines.indexOfFirst { it.glyphs.joinToString(""){it.text}.startsWith("Second") }
        val selection=page.selection(PdfSelectablePage.Hit(first,0),PdfSelectablePage.Hit(second,page.lines[second].glyphs.lastIndex))
        assertEquals("First line\nSecond line",selection.excerpt)
        assertEquals(2,selection.anchor.rects.size)
        assertTrue(selection.anchor.rects.all { it.x+it.width<0.6f })
    }
    @Test fun cropOriginIsRemovedExactlyOnce() {
        val full=fixture().lines[0].glyphs[0].rect
        val cropped=fixture(crop=true).lines[0].glyphs[0].rect
        assertEquals((full.x*400-20)/300,cropped.x,0.00001f)
        assertEquals((full.y*600-70)/500,cropped.y,0.00001f)
    }
    @Test fun pageRotationMatchesDisplayedCropCoordinates() {
        val r=fixture().lines[0].glyphs[0].rect
        val expected=listOf(
            90 to NormalizedRect(1-r.y-r.height,r.x,r.height,r.width),
            180 to NormalizedRect(1-r.x-r.width,1-r.y-r.height,r.width,r.height),
            270 to NormalizedRect(r.y,1-r.x-r.width,r.height,r.width),
        )
        expected.forEach { (rotation,e) ->
            val page=fixture(rotation);val actual=page.lines[0].glyphs[0].rect
            assertEquals(e.x,actual.x,0.00001f);assertEquals(e.y,actual.y,0.00001f)
            assertEquals(e.width,actual.width,0.00001f);assertEquals(e.height,actual.height,0.00001f)
            assertNotNull(page.hit(actual.x+actual.width/2,actual.y+actual.height/2))
        }
    }
    @Test fun multilineRangesRoundTripAndExistingSingleRectDataIsPreserved() {
        val page=fixture();val selection=page.selection(PdfSelectablePage.Hit(0,0),PdfSelectablePage.Hit(1,3))
        assertEquals(selection.anchor,AnchorCodec.decode(AnchorCodec.encode(selection.anchor)))
        val old="pdf:7|0.1,0.2,0.3,0.1"
        val anchor=AnchorCodec.decode(old) as DocumentAnchor.PdfPage
        assertEquals(listOf(NormalizedRect(0.1f,0.2f,0.3f,0.1f)),anchor.rects)
        assertEquals(old,AnchorCodec.encode(anchor))
        assertNull(AnchorCodec.decode("pdf:7|NaN,0.2,0.3,0.1"))
    }
    @Test fun noTextPageHasAnExplicitReasonAndNoRectangles() = PDDocument().use { doc ->
        doc.addPage(PDPage())
        val page=PdfTextSelectionReader.extract(doc,0)
        assertTrue(page.lines.isEmpty());assertNotNull(page.unavailableReason)
    }
    @Test fun chineseSubsetFontKeepsCharactersAndPerLineRanges() = PDDocument().use { doc ->
        val page=PDPage();doc.addPage(page)
        val font=javaClass.getResourceAsStream("/fixtures/pdf-edit/ChineseFixture.ttf")!!.use {
            com.tom_roush.pdfbox.pdmodel.font.PDType0Font.load(doc,it,true)
        }
        PDPageContentStream(doc,page).use { out ->
            out.beginText();out.setFont(font,16f);out.setLeading(25f);out.newLineAtOffset(50f,700f)
            out.showText("中文正文 ABC");out.newLine();out.showText("正文 ABC");out.endText()
        }
        val bytes=java.io.ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
        PDDocument.load(bytes).use { reopened ->
            val text=PdfTextSelectionReader.extract(reopened,0)
            val selection=text.selection(PdfSelectablePage.Hit(0,0),PdfSelectablePage.Hit(1,1))
            assertEquals("中文正文 ABC\n正文",selection.excerpt)
            assertEquals(2,selection.anchor.rects.size)
        }
    }
    @Test fun extractionHonorsCancellation(): Unit = PDDocument().use { doc ->
        val page=PDPage();doc.addPage(page)
        PDPageContentStream(doc,page).use { out ->out.beginText();out.setFont(PDType1Font.HELVETICA,12f);out.showText("text");out.endText() }
        assertThrows(kotlinx.coroutines.CancellationException::class.java) {
            PdfTextSelectionReader.extract(doc,0) { throw kotlinx.coroutines.CancellationException() }
        }
        Unit
    }
}
