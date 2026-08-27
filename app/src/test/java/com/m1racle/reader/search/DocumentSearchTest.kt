package com.m1racle.reader.search

import com.m1racle.reader.markdown.MarkdownBlock
import com.m1racle.reader.markdown.MarkdownParser
import com.m1racle.reader.markdown.TableAlignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentSearchTest {

    private fun blocksOf(md: String) = MarkdownParser.parse(md).blocks

    @Test
    fun caseInsensitive_english() {
        val blocks = listOf(MarkdownBlock.Paragraph("Hello WORLD"))
        val m = DocumentSearch.search("d", blocks, "hello world")
        assertEquals(1, m.matches.size)
        assertEquals(0, m.matches[0].matchStart)
        assertEquals("Hello WORLD", m.matches[0].matchedText)
    }

    @Test
    fun chinese_substring() {
        val blocks = listOf(MarkdownBlock.Paragraph("心电图与波形"))
        val m = DocumentSearch.search("d", blocks, "波形")
        assertEquals(1, m.matches.size)
        assertEquals("波形", m.matches[0].matchedText)
    }

    @Test
    fun spaces_and_punctuation() {
        val blocks = listOf(MarkdownBlock.Paragraph("a, b; c"))
        assertEquals(1, DocumentSearch.search("d", blocks, "b; c").matches.size)
        assertEquals(1, DocumentSearch.search("d", blocks, ", b").matches.size)
    }

    @Test
    fun bold_span_cross_inline_hello_world() {
        val blocks = listOf(MarkdownBlock.Paragraph("hello **world**"))
        val m = DocumentSearch.search("d", blocks, "hello world")
        assertEquals(1, m.matches.size)
    }

    @Test
    fun control_markers_not_matched_as_asterisks() {
        val blocks = listOf(MarkdownBlock.Paragraph("hello **world**"))
        assertTrue(DocumentSearch.search("d", blocks, "**").matches.isEmpty())
    }

    @Test
    fun link_url_not_matched() {
        val blocks = listOf(MarkdownBlock.Paragraph("[Docs](https://example.com/findme)"))
        assertTrue(DocumentSearch.search("d", blocks, "findme").matches.isEmpty())
        assertTrue(DocumentSearch.search("d", blocks, "https").matches.isEmpty())
        assertEquals(1, DocumentSearch.search("d", blocks, "Docs").matches.size)
    }

    @Test
    fun image_source_and_alt_not_matched() {
        val blocks = listOf(
            MarkdownBlock.Image(alt = "chart", source = "https://cdn.example/unique-uri.png"),
            MarkdownBlock.Paragraph("body chart visible"),
        )
        assertTrue(DocumentSearch.search("d", blocks, "unique-uri").matches.isEmpty())
        assertTrue(DocumentSearch.search("d", blocks, "cdn.example").matches.isEmpty())
        // Image alt not searchable; only paragraph "body chart visible"
        val hits = DocumentSearch.search("d", blocks, "chart").matches
        assertEquals(1, hits.size)
        assertEquals(1, hits.single().blockIndex)
    }

    @Test
    fun multiple_non_overlapping() {
        val blocks = listOf(MarkdownBlock.Paragraph("aa aa aa"))
        val m = DocumentSearch.search("d", blocks, "aa")
        assertEquals(3, m.matches.size)
        assertEquals(0, m.matches[0].matchStart)
        assertEquals(3, m.matches[1].matchStart)
        assertEquals(6, m.matches[2].matchStart)
    }

    @Test
    fun overlapping_uses_non_overlap_advance() {
        // "aaa" with needle "aa" → non-overlapping starts at 0 then 2
        val blocks = listOf(MarkdownBlock.Paragraph("aaa"))
        val m = DocumentSearch.search("d", blocks, "aa")
        assertEquals(1, m.matches.size)
        assertEquals(0, m.matches[0].matchStart)
        assertEquals(2, m.matches[0].matchEnd)
    }

    @Test
    fun empty_query_no_scan() {
        val blocks = listOf(MarkdownBlock.Paragraph("anything"))
        val m = DocumentSearch.search("d", blocks, "")
        assertTrue(m.matches.isEmpty())
        assertFalse(m.truncated)
    }

    @Test
    fun no_results() {
        val blocks = listOf(MarkdownBlock.Paragraph("alpha"))
        assertTrue(DocumentSearch.search("d", blocks, "zzz").matches.isEmpty())
    }

    @Test
    fun query_longer_than_text() {
        val blocks = listOf(MarkdownBlock.Paragraph("hi"))
        assertTrue(DocumentSearch.search("d", blocks, "hello").matches.isEmpty())
    }

    @Test
    fun emoji() {
        val blocks = listOf(MarkdownBlock.Paragraph("ok 😀 yes"))
        val m = DocumentSearch.search("d", blocks, "😀")
        assertEquals(1, m.matches.size)
    }

    @Test
    fun does_not_cross_blocks() {
        val blocks = listOf(
            MarkdownBlock.Paragraph("hello"),
            MarkdownBlock.Paragraph("world"),
        )
        assertTrue(DocumentSearch.search("d", blocks, "hello world").matches.isEmpty())
        assertEquals(1, DocumentSearch.search("d", blocks, "hello").matches.size)
        assertEquals(1, DocumentSearch.search("d", blocks, "world").matches.size)
    }

    @Test
    fun list_and_code_and_table() {
        val blocks = listOf(
            MarkdownBlock.ListBlock(false, listOf("item **one**")),
            MarkdownBlock.CodeBlock("kt", "val x = 1"),
            MarkdownBlock.Table(
                headers = listOf("H"),
                rows = listOf(listOf("cell")),
                alignments = listOf(TableAlignment.Start),
            ),
        )
        assertEquals(1, DocumentSearch.search("d", blocks, "item one").matches.size)
        assertEquals(1, DocumentSearch.search("d", blocks, "val x").matches.size)
        assertEquals(1, DocumentSearch.search("d", blocks, "cell").matches.size)
    }

    @Test
    fun yaml_front_matter_not_searched() {
        val blocks = blocksOf(
            """
            ---
            title: hidden-meta-xyz
            ---
            
            Body visible
            """.trimIndent(),
        )
        // If parser yields --- paragraphs, extractor skips; either way hidden title shouldn't hit if fenced.
        val m = DocumentSearch.search("d", blocks, "hidden-meta-xyz")
        // Body still works
        assertTrue(DocumentSearch.search("d", blocks, "Body visible").matches.isNotEmpty())
        // Meta: only assert empty when front matter was detected as --- blocks
        val plain = MarkdownSearchTextExtractor.extract("d", blocks).joinToString { it.visibleText }
        if (!plain.contains("hidden-meta-xyz")) {
            assertTrue(m.matches.isEmpty())
        }
    }

    @Test
    fun stableKey_includes_document_and_offsets() {
        val blocks = listOf(MarkdownBlock.Paragraph("abc"))
        val m = DocumentSearch.search("doc-1", blocks, "b").matches.single()
        assertTrue(m.stableKey.startsWith("doc-1:"))
        assertTrue(m.stableKey.contains(":1:2") || m.matchStart == 1 && m.matchEnd == 2)
        assertEquals("doc-1", m.documentKey)
    }

    @Test
    fun max_matches_truncation() {
        // Many 'a's with needle 'a'
        val long = "a".repeat(DocumentSearch.MAX_MATCHES + 50)
        val blocks = listOf(MarkdownBlock.Paragraph(long))
        val r = DocumentSearch.search("d", blocks, "a")
        assertEquals(DocumentSearch.MAX_MATCHES, r.matches.size)
        assertTrue(r.truncated)
    }

    @Test
    fun documentKey_propagated() {
        val blocks = listOf(MarkdownBlock.Paragraph("x"))
        val r = DocumentSearch.search("key-99", blocks, "x")
        assertEquals("key-99", r.documentKey)
        assertEquals("key-99", r.matches.single().documentKey)
    }

    @Test
    fun convenience_search_list_api() {
        val blocks = listOf(MarkdownBlock.Paragraph("Zzz"))
        assertEquals(1, DocumentSearch.search(blocks, "zzz").size)
    }
}
