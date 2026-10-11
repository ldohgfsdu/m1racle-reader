package com.m1racle.reader.ui.epub

import androidx.compose.ui.text.TextStyle
import com.m1racle.reader.data.settings.ReaderSettings
import com.m1racle.reader.epub.*
import org.junit.Assert.*
import org.junit.Test

class EpubTextLayoutTest {

    @Test fun `list items have individual hanging paragraphs without shifting source identities`() {
        val items = List(12) { listOf(EpubInline("项目 $it " + "正文".repeat(30), bold = it == 0)) }
        val book = EpubBook(EpubMetadata(null, emptyList(), null, null, null, false, false),
            listOf(EpubSpineItem("0", "0.xhtml", true, 0)), emptyList(),
            listOf(EpubChapter(0, "0.xhtml", null, listOf(
                EpubBlock.ListBlock(true, items), EpubBlock.Paragraph(listOf(EpubInline("接续正文"))),
            ), emptyMap())))
        val rows = epubTextRows(book, ReaderSettings(), TextStyle.Default)
        val listRows = rows.filter { it.sourceRow == 0 }
        assertEquals(12, listRows.size)
        assertEquals(1, rows.count { it.chapterStart })
        assertEquals(1, rows.last().sourceRow)
        val expected = items.mapIndexed { i, item -> "${i + 1}. " + item.joinToString("") { it.text } }.joinToString("\n")
        assertEquals(expected, listRows.joinToString("\n") { it.text.text })
        var offset = 0
        listRows.forEach {
            assertEquals(offset, it.sourceTextStart)
            assertFalse(it.continuation)
            val indent = requireNotNull(it.style.textIndent)
            assertEquals(0f, indent.firstLine.value, 0f)
            assertTrue(indent.restLine.value > 0f)
            offset += it.text.length + 1
        }
        assertTrue(listRows[9].style.textIndent!!.restLine.value > listRows[0].style.textIndent!!.restLine.value)
        assertEquals(androidx.compose.ui.text.font.FontWeight.Normal, rows.last().style.fontWeight)
    }

    @Test fun `long list item continuation retains hanging indent but does not gain item spacing`() {
        val book = EpubBook(EpubMetadata(null, emptyList(), null, null, null, false, false),
            listOf(EpubSpineItem("0", "0.xhtml", true, 0)), emptyList(),
            listOf(EpubChapter(0, "0.xhtml", null, listOf(EpubBlock.ListBlock(false, listOf(
                listOf(EpubInline("文字".repeat(2_000))), listOf(EpubInline("后一条")),
            ))), emptyMap())))
        val rows = epubTextRows(book, ReaderSettings(), TextStyle.Default)
        assertTrue(rows.size > 2)
        assertEquals(listOf(false, false), listOf(rows.first().continuation, rows.last().continuation))
        assertTrue(rows.drop(1).dropLast(1).all { it.continuation })
        val indent = rows[1].style.textIndent!!
        assertEquals(indent.restLine, indent.firstLine)
        assertEquals("• " + "文字".repeat(2_000), rows.dropLast(1).joinToString("") { it.text.text })
    }

    @Test fun `EPUB preserves heading hierarchy and reader metrics across font presets`() {
        val book = EpubBook(EpubMetadata(null, emptyList(), null, null, null, false, false),
            listOf(EpubSpineItem("0", "0.xhtml", true, 0)), emptyList(),
            listOf(EpubChapter(0, "0.xhtml", null,
                (1..6).map { EpubBlock.Heading(it, "Heading $it") } +
                    EpubBlock.Paragraph(listOf(EpubInline("Body"))) + EpubBlock.Code("Code"), emptyMap())))
        for (scale in listOf(ReaderSettings.FONT_SMALL, ReaderSettings.FONT_DEFAULT, ReaderSettings.FONT_LARGE)) {
            val rows = epubTextRows(book, ReaderSettings(fontScale = scale), TextStyle.Default)
            val headings = rows.take(6)
            assertTrue(headings.zipWithNext().all { (a, b) -> a.style.fontSize.value >= b.style.fontSize.value })
            assertTrue(headings.first().style.fontSize.value > rows[6].style.fontSize.value)
            assertTrue(headings.all { it.text.spanStyles.isEmpty() }) // row style must control the heading weight
            assertEquals(com.m1racle.reader.ui.viewer.components.ReaderTypography.bodyLineHeight(
                ReaderSettings(fontScale = scale)), rows[6].style.lineHeight)
            assertEquals(androidx.compose.ui.text.style.TextIndent.None, rows[6].style.textIndent)
            assertEquals(androidx.compose.ui.text.font.FontWeight.Normal, rows[6].style.fontWeight)
            assertTrue(rows[7].style.fontSize.value <= com.m1racle.reader.ui.viewer.components.ReaderTypography.CODE_BLOCK_MAX_SP)
        }
    }

    @Test fun `long paragraphs have bounded measurement rows and stable source offsets`() {
        val text = "a".repeat(MAX_MEASURE_ROW_CHARS - 1) + "\uD83D\uDE00" + "b".repeat(5_000)
        val book = EpubBook(
            EpubMetadata("Book", emptyList(), null, null, "3.0", false, false),
            listOf(EpubSpineItem("0", "0.xhtml", true, 0)), emptyList(),
            listOf(EpubChapter(0, "0.xhtml", null, listOf(
                EpubBlock.Paragraph(listOf(EpubInline(text, bold = true))),
                EpubBlock.Heading(2, "Target"),
            ), mapOf("target" to 1))),
        )
        val rows = epubTextRows(book, ReaderSettings(), TextStyle.Default)
        val paragraph = rows.filter { it.sourceRow == 0 }
        assertTrue(paragraph.size > 1)
        assertTrue(paragraph.all { it.text.length <= MAX_MEASURE_ROW_CHARS })
        assertEquals(text, paragraph.joinToString("") { it.text.text })
        assertEquals(1, rows.count { it.chapterStart })
        var offset = 0
        paragraph.forEach {
            assertEquals(offset, it.sourceTextStart)
            assertFalse(it.text.text.last().isHighSurrogate())
            assertTrue(it.text.spanStyles.any { span -> span.item.fontWeight == androidx.compose.ui.text.font.FontWeight.Bold })
            offset += it.text.length
        }
        assertEquals(1, rows.last().sourceRow)
        assertEquals(1, rows.last().blockIndex)
    }

    @Test fun `long image alternative text does not duplicate the image`() {
        val book = EpubBook(EpubMetadata(null, emptyList(), null, null, null, false, false),
            listOf(EpubSpineItem("0", "0.xhtml", true, 0)), emptyList(),
            listOf(EpubChapter(0, "0.xhtml", null,
                listOf(EpubBlock.Image("a".repeat(10_000), resolvedPath = "/image.png")), emptyMap())))
        val rows = epubTextRows(book, ReaderSettings(), TextStyle.Default)
        assertEquals(1, rows.size)
        assertTrue(rows.single().text.length <= MAX_MEASURE_ROW_CHARS)
    }

    @Test fun `rows keep spine order and mark chapter starts, so pagination can break pages there`() {
        val book = EpubBook(
            EpubMetadata("Book", emptyList(), null, null, "3.0", false, false),
            List(3) { EpubSpineItem("$it", "$it.xhtml", it != 1, it) }, emptyList(),
            List(3) { index -> EpubChapter(index, "$index.xhtml", "Chapter $index",
                List(2) { EpubBlock.Paragraph(listOf(EpubInline("text"))) }, mapOf("target" to 1)) },
        )
        val rows = epubTextRows(book, ReaderSettings(), TextStyle.Default)
        assertEquals(listOf(0, 0, 2, 2), rows.map { it.spineIndex })
        assertEquals(listOf(true, false, true, false), rows.map { it.chapterStart })
        val pages = paginateEpub(rows.map {
            EpubRowMetrics(it.spineIndex, it.chapterStart, listOf(EpubLine(0f, 20f, 0, it.text.length)))
        }, 40f, 0f)
        // A chapter start always breaks the page, and a 20px line never gets cut.
        assertEquals(listOf(0, 2), pages.map { it.slices.first().row })
        assertEquals(listOf(listOf(0, 1), listOf(2, 3)), pages.map { it.slices.map { s -> s.row } })
    }

    @Test fun `unlisted top-level chapter headings also break pages without splitting ordinary subheadings`() {
        val book = EpubBook(EpubMetadata(null, emptyList(), null, null, null, false, false),
            listOf(EpubSpineItem("0", "0.xhtml", true, 0)), emptyList(),
            listOf(EpubChapter(0, "0.xhtml", null, listOf(
                EpubBlock.Heading(1, "第一章"), EpubBlock.Paragraph(listOf(EpubInline("正文一"))),
                EpubBlock.Heading(2, "普通小标题"), EpubBlock.Heading(1, "第二章"),
                EpubBlock.Paragraph(listOf(EpubInline("正文二"))),
            ), emptyMap())))
        val rows = epubTextRows(book, ReaderSettings(), TextStyle.Default)
        assertEquals(listOf(true, false, false, true, false), rows.map { it.chapterStart })
        assertEquals("第二章", rows.last().chapterTitle)
        assertEquals(listOf(0, 3), paginateEpub(rows.map {
            EpubRowMetrics(it.spineIndex, it.chapterStart, listOf(EpubLine(0f, 20f, 0, it.text.length)))
        }, 500f, 10f).map { it.slices.first().row })
    }

    @Test fun `chapter heading is not duplicated as a synthetic title row`() {
        val book = EpubBook(
            EpubMetadata("Book", emptyList(), null, null, "3.0", false, false),
            listOf(EpubSpineItem("0", "0.xhtml", true, 0)), emptyList(),
            listOf(EpubChapter(0, "0.xhtml", "第一章", listOf(
                EpubBlock.Heading(1, "第一章"),
                EpubBlock.Paragraph(listOf(EpubInline("正文"))),
            ), emptyMap())),
        )
        val rows = epubTextRows(book, ReaderSettings(), TextStyle.Default)
        assertEquals(listOf("第一章", "正文"), rows.map { it.text.text })
    }
}
