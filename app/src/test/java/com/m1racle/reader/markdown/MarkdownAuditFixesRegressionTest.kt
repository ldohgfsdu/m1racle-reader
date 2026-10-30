package com.m1racle.reader.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownAuditFixesRegressionTest {

    @Test
    fun htmlComments_areRemovedOutsideCodeButPreservedInsideCode() {
        val source = """
            # Title
            <!-- remove me -->
            Inline `<!-- keep inline -->` text.

            ```html
            <!-- keep fenced -->
            ```

            Tail
        """.trimIndent()

        val result = MarkdownParser.parse(source)
        val visible = result.blocks.joinToString("\n") {
            when (it) {
                is MarkdownBlock.Heading -> it.text
                is MarkdownBlock.Paragraph -> InlineMarkdownParser.toPlainText(it.text)
                is MarkdownBlock.CodeBlock -> it.code
                else -> ""
            }
        }

        assertTrue(!visible.contains("remove me"))
        assertTrue(visible.contains("Inline <!-- keep inline --> text."))
        assertEquals("<!-- keep fenced -->", result.blocks.filterIsInstance<MarkdownBlock.CodeBlock>().single().code)
        assertTrue(visible.contains("Tail"))
    }

    @Test
    fun backslash_escapesOnlyAsciiPunctuation() {
        val source = """\path \a \*literal\* and \|"""
        val expected = """\path \a *literal* and |"""

        assertEquals(expected, InlineMarkdownParser.toPlainText(source))
        assertEquals(expected, InlineMarkdownParser.parse(source).text)
    }

    @Test
    fun autolinks_preserveVisibleTextAndExposeClickDestinations() {
        val source = "Angle <https://example.org> and <foo@example.com>."
        val plain = InlineMarkdownParser.toPlainText(source)
        val parsed = InlineMarkdownParser.parse(source)

        assertEquals(
            "Angle https://example.org and foo@example.com.",
            plain,
        )
        assertEquals(plain, parsed.text)
        assertEquals(
            listOf(
                "https://example.org",
                "mailto:foo@example.com",
            ),
            parsed.getStringAnnotations(
                InlineMarkdownParser.LINK_ANNOTATION_TAG,
                0,
                parsed.length,
            ).map { it.item },
        )
        assertEquals(
            listOf(
                "https://example.org",
                "mailto:foo@example.com",
            ),
            InlineMarkdownParser.extractLinkDestinations(source),
        )
    }

    @Test
    fun table_splitsOnlyOnUnescapedPipes_andKeepsFollowingParagraph() {
        val source = """
            | Name | Value |
            | --- | --- |
            | A | left \| right |

            After table
        """.trimIndent()
        val result = MarkdownParser.parse(source)
        val table = result.blocks.filterIsInstance<MarkdownBlock.Table>().single()
        val cell = table.rows.single()[1]

        assertEquals(listOf("Name", "Value"), table.headers)
        assertEquals(listOf("A", "left \\| right"), table.rows.single())
        assertEquals("left | right", InlineMarkdownParser.toPlainText(cell))
        assertEquals("left | right", InlineMarkdownParser.parse(cell).text)
        assertTrue(result.blocks.any { it is MarkdownBlock.Paragraph && it.text == "After table" })
    }

    @Test
    fun variableLengthFence_closesWithMatchingRun_andKeepsFollowingText() {
        val source = """
            ````kotlin
            val literal = "```"
            ```
            still code
            ````

            After fence
        """.trimIndent()
        val result = MarkdownParser.parse(source)
        val code = result.blocks.filterIsInstance<MarkdownBlock.CodeBlock>().single()

        assertEquals("kotlin", code.language)
        assertEquals(
            "val literal = \"```\"\n```\nstill code",
            code.code,
        )
        assertTrue(result.blocks.any { it is MarkdownBlock.Paragraph && it.text == "After fence" })
    }

    @Test
    fun doubleBacktickSpan_matchesItsOwnRun_andKeepsFollowingText() {
        val source = "Use ``code ` inner`` after"
        val plain = InlineMarkdownParser.toPlainText(source)
        val parsed = InlineMarkdownParser.parse(source)

        assertEquals("Use code ` inner after", plain)
        assertEquals(plain, parsed.text)
        assertTrue(parsed.spanStyles.any { parsed.text.substring(it.start, it.end) == "code ` inner" })
        assertEquals("Use ``unclosed", InlineMarkdownParser.toPlainText("Use ``unclosed"))
    }

    @Test fun multilineInlineCodeAndCrOnlyFencesKeepCommentText() {
        val inline = "Before `first\n<!-- keep -->\nlast` after"
        assertEquals(inline, MarkdownParser.stripHtmlComments(inline))
        assertEquals("Before first <!-- keep --> last after", InlineMarkdownParser.toPlainText(inline))
        val fenced = "```html\r<!-- keep -->\r```\r\rAfter"
        val parsed = MarkdownParser.parse(fenced)
        assertEquals("<!-- keep -->", parsed.blocks.filterIsInstance<MarkdownBlock.CodeBlock>().single().code)
        assertTrue(parsed.blocks.any { it is MarkdownBlock.Paragraph && it.text == "After" })
    }

    @Test fun commentContentsCannotChangeFenceStateOrHideFollowingCode() {
        val source = "<!-- hidden\n```html\n<!-- nested -->\n\n```html\n<!-- keep -->\n```\n\nAfter"
        val parsed = MarkdownParser.parse(source)
        assertEquals("<!-- keep -->", parsed.blocks.filterIsInstance<MarkdownBlock.CodeBlock>().single().code)
        assertTrue(parsed.blocks.any { it is MarkdownBlock.Paragraph && it.text == "After" })
        assertEquals("Before <!-- unfinished\nAfter", MarkdownParser.stripHtmlComments("Before <!-- unfinished\nAfter"))
    }

    @Test fun nonHtmlAngleTextStaysVisible() {
        val source = """Keep <x + y> and <C:\Users\reader>."""
        assertEquals(source, InlineMarkdownParser.toPlainText(source))
        assertEquals(source, InlineMarkdownParser.parse(source).text)
    }
}
