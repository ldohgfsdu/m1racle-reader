package com.markleaf.app.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlCommentStripTest {

    @Test
    fun stripHtmlComments_removesInlineAndMultiline() {
        val src = """
            # Title
            <!-- hidden -->
            visible
            <!--
            multi
            line
            -->
            end
        """.trimIndent()
        val out = MarkdownParser.stripHtmlComments(src)
        assertFalse(out.contains("hidden"))
        assertFalse(out.contains("multi"))
        assertTrue(out.contains("visible"))
        assertTrue(out.contains("end"))
    }

    @Test
    fun parse_doesNotRenderCommentAsParagraph() {
        val src = "# H\n\n<!-- secret note -->\n\nBody text\n"
        val result = MarkdownParser.parse(src)
        val texts = result.blocks.joinToString("\n") {
            when (it) {
                is MarkdownBlock.Heading -> it.text
                is MarkdownBlock.Paragraph -> it.text
                else -> ""
            }
        }
        assertFalse(texts.contains("secret"))
        assertTrue(texts.contains("Body text"))
        assertEquals(1, result.blocks.count { it is MarkdownBlock.Heading })
    }
}
