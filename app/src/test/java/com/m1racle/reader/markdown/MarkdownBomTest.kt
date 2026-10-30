package com.m1racle.reader.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownBomTest {
    @Test fun leadingBomDoesNotChangeBlocksTocAnchorsOrSourceLines() {
        val source = "# Title\r\n\r\nParagraph\r\n\r\n## Next\rTail"
        val expected = MarkdownParser.parse(source)
        val actual = MarkdownParser.parse("\uFEFF$source")
        assertEquals(expected.blocks, actual.blocks)
        assertEquals(expected.toc, actual.toc)
        assertEquals(expected.anchorRegistry.targets, actual.anchorRegistry.targets)
        assertEquals(expected.blockIds, actual.blockIds)
        assertEquals(listOf(1, 3, 5, 6), actual.blockStartLines)
    }

    @Test fun leadingBomIsRemovedBeforeFenceAndCommentParsing() {
        val source = "```html\r\n<!-- keep -->\r\n\uFEFFliteral\r\n```\r\n\r\nAfter"
        val expected = MarkdownParser.parse(source)
        val actual = MarkdownParser.parse("\uFEFF$source")
        assertEquals(expected.blocks, actual.blocks)
        assertEquals(expected.blockStartLines, actual.blockStartLines)
        assertEquals("<!-- keep -->\n\uFEFFliteral", actual.blocks.filterIsInstance<MarkdownBlock.CodeBlock>().single().code)
    }

    @Test fun onlyTheDocumentLeadingBomIsIgnored() {
        val result = MarkdownParser.parse("\uFEFFFirst\uFEFFword\n\n\uFEFFSecond")
        assertEquals(listOf(MarkdownBlock.Paragraph("First\uFEFFword"), MarkdownBlock.Paragraph("\uFEFFSecond")), result.blocks)
        assertTrue(MarkdownParser.parse("\uFEFF").blocks.isEmpty())
    }
}
