package com.markleaf.app.search

import com.markleaf.app.markdown.MarkdownBlock
import com.markleaf.app.markdown.MarkdownParser
import com.markleaf.app.markdown.TableAlignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownSearchTextExtractorTest {

    private val doc = "doc-a"

    private fun texts(blocks: List<MarkdownBlock>): List<String> =
        MarkdownSearchTextExtractor.extract(doc, blocks).map { it.visibleText }

    @Test
    fun heading_visibleOnly() {
        val blocks = listOf(MarkdownBlock.Heading(1, "Hello World", "hello-world"))
        assertEquals(listOf("Hello World"), texts(blocks))
    }

    @Test
    fun paragraph_stripsBoldMarkers_joinsSpans() {
        val blocks = listOf(MarkdownBlock.Paragraph("hello **world**"))
        assertEquals(listOf("hello world"), texts(blocks))
    }

    @Test
    fun paragraph_stripsItalicAndCodeMarkers() {
        val blocks = listOf(MarkdownBlock.Paragraph("a *b* and `c`"))
        assertEquals(listOf("a b and c"), texts(blocks))
    }

    @Test
    fun link_labelOnly_notUrl() {
        val blocks = listOf(MarkdownBlock.Paragraph("see [Docs](https://example.com/path)"))
        val t = texts(blocks).single()
        assertEquals("see Docs", t)
        assertFalse(t.contains("https"))
        assertFalse(t.contains("example.com"))
    }

    @Test
    fun list_items_without_markers() {
        val blocks = listOf(MarkdownBlock.ListBlock(ordered = false, items = listOf("**one**", "two")))
        val segs = MarkdownSearchTextExtractor.extract(doc, blocks)
        assertEquals(2, segs.size)
        assertEquals("one", segs[0].visibleText)
        assertEquals(0, segs[0].listItemIndex)
        assertEquals("two", segs[1].visibleText)
        assertEquals(1, segs[1].listItemIndex)
    }

    @Test
    fun quote_body_only() {
        // Parser stores quote without leading >
        val blocks = listOf(MarkdownBlock.Quote("quoted **text**"))
        assertEquals(listOf("quoted text"), texts(blocks))
    }

    @Test
    fun codeBlock_body_notLanguage() {
        val blocks = listOf(MarkdownBlock.CodeBlock(language = "kotlin", code = "fun main() {}"))
        assertEquals(listOf("fun main() {}"), texts(blocks))
    }

    @Test
    fun table_cells_as_segments() {
        val blocks = listOf(
            MarkdownBlock.Table(
                headers = listOf("Name", "Age"),
                rows = listOf(listOf("Ann", "3")),
                alignments = listOf(TableAlignment.Start, TableAlignment.Start),
            ),
        )
        val segs = MarkdownSearchTextExtractor.extract(doc, blocks)
        assertEquals(listOf("Name", "Age", "Ann", "3"), segs.map { it.visibleText })
    }

    @Test
    fun image_excludes_source_and_unstable_alt_title() {
        // P0: Image blocks are not indexed (load-state decoupled; alt not always on-screen).
        val blocks = listOf(
            MarkdownBlock.Image(
                alt = "diagram-alt-secret",
                source = "https://cdn.example/unique-uri.png",
                title = "Figure-title-secret",
            ),
        )
        assertTrue(texts(blocks).isEmpty())
        assertTrue(
            MarkdownSearchTextExtractor.extract(doc, blocks).none {
                it.visibleText.contains("diagram") ||
                    it.visibleText.contains("Figure") ||
                    it.visibleText.contains("unique-uri") ||
                    it.visibleText.contains("cdn.example")
            },
        )
    }

    @Test
    fun image_empty_alt_no_source_segment() {
        val blocks = listOf(MarkdownBlock.Image(alt = "", source = "./secret.png", title = null))
        assertTrue(texts(blocks).isEmpty())
    }

    @Test
    fun divider_excluded() {
        val blocks = listOf(MarkdownBlock.Divider, MarkdownBlock.Paragraph("keep"))
        assertEquals(listOf("keep"), texts(blocks))
    }

    @Test
    fun yaml_front_matter_blocks_skipped() {
        val blocks = listOf(
            MarkdownBlock.Paragraph("---"),
            MarkdownBlock.Paragraph("title: secret-meta"),
            MarkdownBlock.Paragraph("---"),
            MarkdownBlock.Paragraph("Visible body"),
        )
        assertEquals(listOf("Visible body"), texts(blocks))
        assertFalse(texts(blocks).any { it.contains("secret-meta") })
    }

    @Test
    fun parser_roundtrip_bold_link_not_controls() {
        val parsed = MarkdownParser.parse(
            """
            # Title **X**
            
            Hello **world** and [lab](https://evil.test/a)
            
            ![alt text](./no-search.png "cap")
            """.trimIndent(),
        )
        val joined = texts(parsed.blocks).joinToString(" | ")
        assertTrue(joined.contains("Title X") || joined.contains("Title") && joined.contains("X"))
        assertTrue(joined.contains("Hello world"))
        assertTrue(joined.contains("lab"))
        assertFalse(joined.contains("evil.test"))
        assertFalse(joined.contains("no-search.png"))
        // Image alt/title not indexed at P0
        assertFalse(joined.contains("alt text"))
        assertFalse(joined.contains("cap"))
    }
}
