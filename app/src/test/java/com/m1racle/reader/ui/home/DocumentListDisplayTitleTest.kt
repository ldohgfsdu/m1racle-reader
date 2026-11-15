package com.m1racle.reader.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * List titles for rows whose format is no longer readable.
 *
 * These rows still exist in the library, so the display name has to survive with
 * no Markdown parser available: the stored `title` column is cleaned as a string,
 * and a blank result falls back to the file-name stem, never to an empty cell.
 */
class DocumentListDisplayTitleTest {

    @Test
    fun storedTitle_stripsLeadingEmojiWithoutParsing() {
        assertEquals(
            "墨阅 M1racleReader｜PRD",
            DocumentListDisplayTitle.normalizeListDisplayTitle(
                "📄 墨阅 M1racleReader｜PRD",
                "prd.md",
            ),
        )
    }

    @Test
    fun nullTitle_fallsBackToFileStem() {
        assertEquals(
            "notes",
            DocumentListDisplayTitle.normalizeListDisplayTitle(null, "notes.markdown"),
        )
    }

    @Test
    fun blankTitle_neverReturnsEmpty() {
        assertEquals(
            "未命名文档",
            DocumentListDisplayTitle.normalizeListDisplayTitle(null, ""),
        )
        assertEquals(
            "未命名文档",
            DocumentListDisplayTitle.normalizeListDisplayTitle("   ", "   "),
        )
    }

    @Test
    fun pairedInlineMarkersAreUnwrapped() {
        assertEquals(
            "第一章 概述",
            DocumentListDisplayTitle.normalizeListDisplayTitle("**第一章 概述**", "a.md"),
        )
        assertEquals(
            "代码示例",
            DocumentListDisplayTitle.normalizeListDisplayTitle("`代码示例`", "a.md"),
        )
        assertEquals(
            "已废弃",
            DocumentListDisplayTitle.normalizeListDisplayTitle("~~已废弃~~", "a.md"),
        )
        assertEquals(
            "重点",
            DocumentListDisplayTitle.normalizeListDisplayTitle("*重点*", "a.md"),
        )
    }

    @Test
    fun underscoresAreLeftAlone() {
        // Underscores appear in real names; unwrapping them would corrupt text.
        assertEquals(
            "my_notes_v2",
            DocumentListDisplayTitle.normalizeListDisplayTitle("my_notes_v2", "a.md"),
        )
        assertEquals(
            "a__b",
            DocumentListDisplayTitle.normalizeListDisplayTitle("a__b", "a.md"),
        )
    }

    @Test
    fun unpairedMarkerIsLeftAlone() {
        assertEquals(
            "**未闭合",
            DocumentListDisplayTitle.normalizeListDisplayTitle("**未闭合", "a.md"),
        )
    }

    @Test
    fun hasUsableTitle_falseForEmojiOnly() {
        assertFalse(DocumentListDisplayTitle.hasUsableDocumentTitle("🚀", "a.md"))
        assertTrue(DocumentListDisplayTitle.hasUsableDocumentTitle("📄 Hello", "a.md"))
        assertFalse(DocumentListDisplayTitle.hasUsableDocumentTitle(null, "a.md"))
    }

    @Test
    fun fileStem_txtAndMarkdown() {
        assertEquals("readme", DocumentListDisplayTitle.fileNameStem("readme.TXT"))
        assertEquals("doc", DocumentListDisplayTitle.fileNameStem("doc.markdown"))
        assertEquals("book", DocumentListDisplayTitle.fileNameStem("book.md"))
    }

    @Test
    fun whitespaceIsCollapsed() {
        assertEquals("a b c", DocumentListDisplayTitle.normalizeWhitespace("  a \n b\t\tc  "))
    }
}
