package com.m1racle.reader.ui.epub

import androidx.compose.ui.text.TextStyle
import com.m1racle.reader.data.settings.ReaderSettings
import com.m1racle.reader.epub.*
import org.junit.Assert.*
import org.junit.Test

class EpubTextLayoutTest {
    @Test fun `toc fragment resolves against real spine rather than filtered chapter ordinal`() {
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
        val layout = EpubTextLayout(rows, emptyList(), pages)
        assertEquals(1, layout.pageForToc(book, EpubTocNode("chapter", "2.xhtml", 2, emptyList())))
        assertEquals(1, layout.pageForToc(book, EpubTocNode("target", "2.xhtml#target", 2, emptyList())))
        assertNull(layout.pageForToc(book, EpubTocNode("nonlinear", "1.xhtml", 1, emptyList())))
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
