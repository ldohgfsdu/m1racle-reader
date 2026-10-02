package com.m1racle.reader.ui.epub

import androidx.compose.ui.text.TextStyle
import com.m1racle.reader.data.settings.ReaderSettings
import com.m1racle.reader.epub.*
import org.junit.Assert.*
import org.junit.Test

class EpubTextLayoutTest {

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
