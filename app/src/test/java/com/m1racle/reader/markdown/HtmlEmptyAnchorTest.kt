package com.m1racle.reader.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlEmptyAnchorTest {

    @Test
    fun emptyHtmlAnchors_areNotVisibleText() {
        val result = MarkdownParser.parse(
            """
            # 前言
            <a id="de-pian"></a>
            <a id="chapter-01"></a>
            <a name="chapter-02"></a>
            ## 第一章
            正文
            """.trimIndent(),
        )

        val visible = result.blocks.joinToString("\n") { block ->
            when (block) {
                is MarkdownBlock.Heading -> block.text
                is MarkdownBlock.Paragraph -> block.text
                is MarkdownBlock.Quote -> block.text
                is MarkdownBlock.CodeBlock -> block.code
                is MarkdownBlock.ListBlock -> block.items.joinToString("\n")
                is MarkdownBlock.Table -> (block.headers + block.rows.flatten()).joinToString("|")
                is MarkdownBlock.Image -> block.alt
                MarkdownBlock.Divider -> "---"
            }
        }
        assertFalse(visible.contains("<a"))
        assertFalse(visible.contains("de-pian"))
        assertFalse(visible.contains("chapter-01"))
        assertFalse(visible.contains("chapter-02"))
        assertTrue(visible.contains("前言"))
        assertTrue(visible.contains("第一章"))
        assertTrue(visible.contains("正文"))
    }

    @Test
    fun emptyHtmlAnchors_doNotCreateExtraBlocksOrSpacing() {
        val result = MarkdownParser.parse(
            """
            # A

            <a id="x"></a>

            <a name="y"></a>

            # B
            """.trimIndent(),
        )

        assertEquals(2, result.blocks.size)
        assertTrue(result.blocks[0] is MarkdownBlock.Heading)
        assertTrue(result.blocks[1] is MarkdownBlock.Heading)
        assertEquals(0, result.blocks.count { it is MarkdownBlock.Paragraph })
    }

    @Test
    fun idAndName_areBothRegistered() {
        val result = MarkdownParser.parse(
            """
            <a id="de-pian"></a>
            <a name="chapter-02"></a>
            # 标题
            """.trimIndent(),
        )

        assertEquals(0, result.anchorRegistry.resolve("de-pian")?.blockIndex)
        assertEquals(0, result.anchorRegistry.resolve("#chapter-02")?.blockIndex)
        assertEquals(0, result.anchorRegistry.resolve("标题")?.blockIndex)
    }

    @Test
    fun anchorBeforeHeading_jumpsToThatHeading() {
        val result = MarkdownParser.parse(
            """
            导言段落

            <a id="section-alpha"></a>
            ## Alpha 节

            内容
            """.trimIndent(),
        )

        val target = result.anchorRegistry.resolve("section-alpha")
        assertNotNull(target)
        assertEquals(1, target!!.blockIndex)
        val heading = result.blocks[target.blockIndex] as MarkdownBlock.Heading
        assertEquals("Alpha 节", heading.text)
        assertEquals(1, result.toc.single { it.title == "Alpha 节" }.blockIndex)
    }

    @Test
    fun anchorBeforeParagraph_bindsToNextRenderableBlock() {
        val result = MarkdownParser.parse(
            """
            <a id="note-1"></a>
            这是普通段落
            """.trimIndent(),
        )

        assertEquals(1, result.blocks.size)
        assertTrue(result.blocks[0] is MarkdownBlock.Paragraph)
        assertEquals(0, result.anchorRegistry.resolve("note-1")?.blockIndex)
    }

    @Test
    fun caseQuotesAndWhitespace_areAccepted() {
        val result = MarkdownParser.parse(
            """
            <A  ID = "Mixed-Case" ></A>
            <a name='single-q'></a>
            # H
            """.trimIndent(),
        )

        assertEquals(0, result.anchorRegistry.resolve("Mixed-Case")?.blockIndex)
        assertEquals(0, result.anchorRegistry.resolve("single-q")?.blockIndex)
        assertEquals(1, result.blocks.size)
    }

    @Test
    fun ordinaryHtmlText_isNotStripped() {
        val result = MarkdownParser.parse(
            """
            请使用 <a href="https://example.com">链接</a> 访问
            <div>not-an-anchor</div>
            """.trimIndent(),
        )

        val paragraphs = result.blocks.filterIsInstance<MarkdownBlock.Paragraph>()
        assertTrue(paragraphs.any { it.text.contains("<a href=") })
        assertTrue(paragraphs.any { it.text.contains("<div>") })
        assertNull(result.anchorRegistry.resolve("https://example.com"))
    }

    @Test
    fun fencedCodeBlock_preservesAnchorMarkup() {
        val result = MarkdownParser.parse(
            """
            ```html
            <a id="de-pian"></a>
            <a name="chapter-02"></a>
            ```
            # Real
            """.trimIndent(),
        )

        val code = result.blocks.filterIsInstance<MarkdownBlock.CodeBlock>().single()
        assertTrue(code.code.contains("""<a id="de-pian"></a>"""))
        assertTrue(code.code.contains("""<a name="chapter-02"></a>"""))
        // Code fence is block 0; HTML ids inside must not bind to the heading.
        assertNull(result.anchorRegistry.resolve("de-pian"))
        assertNull(result.anchorRegistry.resolve("chapter-02"))
        assertEquals(1, result.anchorRegistry.resolve("Real")?.blockIndex)
    }

    @Test
    fun matchEmptyHtmlAnchorId_rejectsNonEmptyAnchors() {
        assertNull(MarkdownParser.matchEmptyHtmlAnchorId("""<a id="x">label</a>"""))
        assertNull(MarkdownParser.matchEmptyHtmlAnchorId("""<a href="#x"></a>"""))
        assertEquals("ok", MarkdownParser.matchEmptyHtmlAnchorId("""<a id="ok"></a>"""))
        assertEquals("n1", MarkdownParser.matchEmptyHtmlAnchorId("""<a name="n1"></a>"""))
        assertEquals("sc", MarkdownParser.matchEmptyHtmlAnchorId("""<a id="sc"/>"""))
    }

    @Test
    fun tocAndHtmlAnchor_shareSameHeadingBlock() {
        val result = MarkdownParser.parse(
            """
            <a id="custom-toc-target"></a>
            ### 目录项
            """.trimIndent(),
        )

        val toc = result.toc.single()
        assertEquals("目录项", toc.title)
        assertEquals(0, toc.blockIndex)
        assertEquals(0, result.anchorRegistry.resolve("custom-toc-target")?.blockIndex)
        assertEquals(0, result.anchorRegistry.resolve(toc.anchor)?.blockIndex)
    }

    @Test
    fun trailingOrphanAnchors_areNotBoundToLastBlockTop() {
        val result = MarkdownParser.parse(
            """
            ## 德篇
            最后一段内容

            <a id="doc-end"></a>
            """.trimIndent(),
        )

        val lastIndex = result.blocks.lastIndex
        assertTrue(lastIndex >= 0)
        // Binding to lastIndex would jump to the heading/paragraph *top* — wrong.
        assertNull(
            "EOF orphan must not resolve (would land on last block top)",
            result.anchorRegistry.resolve("doc-end"),
        )
        assertEquals(listOf("doc-end"), result.ignoredTrailingHtmlAnchors)
        // Normal pre-heading anchors still bind to the next block.
        val withNext = MarkdownParser.parse(
            """
            <a id="has-next"></a>
            ## 道篇
            """.trimIndent(),
        )
        assertEquals(0, withNext.anchorRegistry.resolve("has-next")?.blockIndex)
        assertTrue(withNext.ignoredTrailingHtmlAnchors.isEmpty())
    }
}
