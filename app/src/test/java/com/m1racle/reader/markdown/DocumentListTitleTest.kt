package com.m1racle.reader.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentListTitleTest {

    @Test
    fun leadingEmojiStripped_keepsMidPunctuation() {
        val md = "# 📄 墨阅 M1racleReader｜Android Markdown 阅读器 PRD\n\nbody"
        assertEquals(
            "墨阅 M1racleReader｜Android Markdown 阅读器 PRD",
            DocumentListTitle.extractDocumentTitle(md, "prd.md"),
        )
    }

    @Test
    fun midEmojiPreserved_withCpp() {
        val md = "# C++ 入门 🚀\n"
        assertEquals("C++ 入门 🚀", DocumentListTitle.extractDocumentTitle(md, "cpp.md"))
    }

    @Test
    fun multipleLeadingEmojiStripped() {
        val md = "# 🚀📘 章节一\n"
        assertEquals("章节一", DocumentListTitle.extractDocumentTitle(md, "ch.md"))
    }

    @Test
    fun emojiOnlyH1_fallsBackToFileStem() {
        val md = "# 🚀\n\nbody"
        assertEquals(
            "chapter-one",
            DocumentListTitle.extractDocumentTitle(md, "chapter-one.md"),
        )
    }

    @Test
    fun noH1_usesFileStem() {
        val md = "just a paragraph\n"
        assertEquals(
            "split_report",
            DocumentListTitle.extractDocumentTitle(md, "split_report.md"),
        )
    }

    @Test
    fun ignoresFencedFakeH1() {
        val md = """
            ```markdown
            # Fake title
            ```
            
            # Real title
        """.trimIndent()
        assertEquals("Real title", DocumentListTitle.extractDocumentTitle(md, "x.md"))
    }

    @Test
    fun ignoresYamlFrontMatter() {
        val md = """
            ---
            title: front
            # Not a heading
            ---
            
            # Actual
        """.trimIndent()
        assertEquals("Actual", DocumentListTitle.extractDocumentTitle(md, "x.md"))
    }

    @Test
    fun setextH1() {
        val md = """
            Setext Title
            ============
            
            body
        """.trimIndent()
        assertEquals("Setext Title", DocumentListTitle.extractDocumentTitle(md, "x.md"))
    }

    @Test
    fun inlineStrongAndEmoji() {
        val md = "# **📄 墨阅 M1racleReader**\n"
        assertEquals("墨阅 M1racleReader", DocumentListTitle.extractDocumentTitle(md, "x.md"))
    }

    @Test
    fun inlineLinkLabelOnly() {
        val md = "# [墨阅](https://example.com)\n"
        assertEquals("墨阅", DocumentListTitle.extractDocumentTitle(md, "x.md"))
    }

    @Test
    fun inlineCodePreservesCpp() {
        val md = "# `C++` 入门\n"
        assertEquals("C++ 入门", DocumentListTitle.extractDocumentTitle(md, "x.md"))
    }

    @Test
    fun emphasisWithTrailingEmoji() {
        val md = "# *重点章节* 🚀\n"
        assertEquals("重点章节 🚀", DocumentListTitle.extractDocumentTitle(md, "x.md"))
    }

    @Test
    fun normalizeFromRoomTitle_stripsLeadingEmojiWithoutFullMarkdown() {
        assertEquals(
            "墨阅 M1racleReader｜PRD",
            DocumentListTitle.normalizeListDisplayTitle(
                "📄 墨阅 M1racleReader｜PRD",
                "prd.md",
            ),
        )
    }

    @Test
    fun normalizeNull_fallsBackToStem() {
        assertEquals(
            "notes",
            DocumentListTitle.normalizeListDisplayTitle(null, "notes.markdown"),
        )
    }

    @Test
    fun hasUsableDocumentTitle_falseForEmojiOnly() {
        assertFalse(
            DocumentListTitle.hasUsableDocumentTitle("🚀", "a.md"),
        )
        assertTrue(
            DocumentListTitle.hasUsableDocumentTitle("📄 Hello", "a.md"),
        )
    }

    @Test
    fun fileNameStem_txtAndMarkdown() {
        assertEquals("readme", DocumentListTitle.fileNameStem("readme.TXT"))
        assertEquals("doc", DocumentListTitle.fileNameStem("doc.markdown"))
    }
}
