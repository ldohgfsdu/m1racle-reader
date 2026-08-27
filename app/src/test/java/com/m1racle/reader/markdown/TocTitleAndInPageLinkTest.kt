package com.m1racle.reader.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TOC title normalization, heading-link fragments, in-page vs external routing.
 */
class TocTitleAndInPageLinkTest {

    @Test
    fun toc_titleFromLinkedHeading_showsLabelOnly() {
        val result = MarkdownParser.parse(
            """
            ## [德篇](#de-pian)
            ### [第一章](#chapter-01)
            #### [不应进目录](#skip)
            """.trimIndent(),
        )

        assertEquals(2, result.toc.size)
        assertEquals("德篇", result.toc[0].title)
        assertEquals("第一章", result.toc[1].title)
        assertFalse(result.toc[0].title.contains("["))
        assertFalse(result.toc[0].title.contains("]"))
        assertFalse(result.toc[0].title.contains("#de-pian"))
        assertFalse(result.toc[1].title.contains("#chapter-01"))
    }

    @Test
    fun toc_clickTarget_usesBlockIndexAndRegisteredFragments() {
        val result = MarkdownParser.parse(
            """
            导言

            ## [德篇](#de-pian)

            正文
            """.trimIndent(),
        )

        val toc = result.toc.single()
        assertEquals("德篇", toc.title)
        assertEquals(1, toc.blockIndex)
        // Generated slug from plain title
        assertEquals(1, result.anchorRegistry.resolve(toc.anchor)?.blockIndex)
        // Authored fragment in the heading link
        assertEquals(1, result.anchorRegistry.resolve("de-pian")?.blockIndex)
        assertEquals(1, result.anchorRegistry.resolve("#de-pian")?.blockIndex)
    }

    @Test
    fun bodyTocLinks_resolveToHtmlAnchorsAndHeadings() {
        val result = MarkdownParser.parse(
            """
            [德篇](#de-pian)
            [第一章](#chapter-01)
            [缺失](#missing-anchor)

            <a id="de-pian"></a>
            ## 德篇正文

            <a id="chapter-01"></a>
            ## 第一章标题
            """.trimIndent(),
        )

        assertNull(result.blocks.firstOrNull { it is MarkdownBlock.Paragraph && it.text.contains("<a") })
        // blocks: [0]=TOC paragraph, [1]=德篇正文, [2]=第一章标题
        assertEquals(1, result.anchorRegistry.resolve("de-pian")?.blockIndex)
        assertEquals(2, result.anchorRegistry.resolve("chapter-01")?.blockIndex)
        assertNull(result.anchorRegistry.resolve("missing-anchor"))

        val bodyLinks = InlineMarkdownParser.parse("[德篇](#de-pian) [第一章](#chapter-01)")
            .getStringAnnotations(
                tag = InlineMarkdownParser.LINK_ANNOTATION_TAG,
                start = 0,
                end = Int.MAX_VALUE,
            )
        assertEquals(listOf("#de-pian", "#chapter-01"), bodyLinks.map { it.item })
    }

    @Test
    fun linkRouter_splitsInPageAndExternal() {
        val inPage = MarkdownLinkRouter.classify("#chapter-01")
        assertTrue(inPage is MarkdownLinkRouter.Action.InPageAnchor)
        assertEquals("chapter-01", (inPage as MarkdownLinkRouter.Action.InPageAnchor).anchorId)

        val chinese = MarkdownLinkRouter.classify("#德篇")
        assertEquals("德篇", (chinese as MarkdownLinkRouter.Action.InPageAnchor).anchorId)

        val encoded = MarkdownLinkRouter.classify("#%E7%AB%A0")
        assertEquals("章", (encoded as MarkdownLinkRouter.Action.InPageAnchor).anchorId)

        val https = MarkdownLinkRouter.classify("https://example.com/path")
        assertTrue(https is MarkdownLinkRouter.Action.External)
        assertEquals(
            "https://example.com/path",
            (https as MarkdownLinkRouter.Action.External).uri,
        )

        val http = MarkdownLinkRouter.classify("http://example.com")
        assertTrue(http is MarkdownLinkRouter.Action.External)

        // Relative file path is external (not in-page fragment)
        val relative = MarkdownLinkRouter.classify("other.md#frag")
        assertTrue(relative is MarkdownLinkRouter.Action.External)
    }

    @Test
    fun missingAnchor_resolveIsNull_safe() {
        val result = MarkdownParser.parse("# Only")
        assertNull(result.anchorRegistry.resolve("no-such-id"))
        assertNull(result.anchorRegistry.resolve("#still-missing"))
        assertFalse(result.anchorRegistry.contains("no-such-id"))
    }

    @Test
    fun htmlAnchors_invisible_noExtraSpacing_andJump() {
        val result = MarkdownParser.parse(
            """
            # 前言

            <a id="de-pian"></a>
            <a name="chapter-01"></a>

            ## 德篇

            段落
            """.trimIndent(),
        )

        assertEquals(3, result.blocks.size)
        val visible = result.blocks.joinToString("\n") {
            when (it) {
                is MarkdownBlock.Heading -> it.text
                is MarkdownBlock.Paragraph -> it.text
                else -> ""
            }
        }
        assertFalse(visible.contains("<a"))
        assertFalse(visible.contains("de-pian"))
        assertEquals(1, result.anchorRegistry.resolve("de-pian")?.blockIndex)
        assertEquals(1, result.anchorRegistry.resolve("chapter-01")?.blockIndex)
        assertEquals("德篇", result.toc.single { it.title == "德篇" }.title)
    }

    @Test
    fun selfClosingHtmlAnchor_isRecognized() {
        val result = MarkdownParser.parse(
            """
            <a id="self-close"/>
            # Title
            """.trimIndent(),
        )
        assertEquals(1, result.blocks.size)
        assertEquals(0, result.anchorRegistry.resolve("self-close")?.blockIndex)
    }

    @Test
    fun trailingHtmlAnchor_isIgnoredNotBoundToLastBlockTop() {
        val result = MarkdownParser.parse(
            """
            # Last
            正文末段

            <a id="eof-anchor"></a>
            <a name="eof-name"></a>
            """.trimIndent(),
        )
        assertEquals(2, result.blocks.size)
        // Must NOT resolve to last block top (would be a wrong jump).
        assertNull(result.anchorRegistry.resolve("eof-anchor"))
        assertNull(result.anchorRegistry.resolve("eof-name"))
        assertEquals(listOf("eof-anchor", "eof-name"), result.ignoredTrailingHtmlAnchors)
        // Preceding heading still works.
        assertEquals(0, result.anchorRegistry.resolve("Last")?.blockIndex)
    }

    @Test
    fun trailingHtmlAnchor_onlyDocument_isIgnored() {
        val result = MarkdownParser.parse(
            """
            <a id="lonely"></a>
            """.trimIndent(),
        )
        assertTrue(result.blocks.isEmpty())
        assertNull(result.anchorRegistry.resolve("lonely"))
        assertEquals(listOf("lonely"), result.ignoredTrailingHtmlAnchors)
    }

    @Test
    fun anchorBeforeNextBlock_stillBindsToThatBlock() {
        val result = MarkdownParser.parse(
            """
            # A
            <a id="before-b"></a>
            # B
            <a id="eof-only"></a>
            """.trimIndent(),
        )
        assertEquals(0, result.anchorRegistry.resolve("A")?.blockIndex)
        assertEquals(1, result.anchorRegistry.resolve("before-b")?.blockIndex)
        assertEquals(1, result.anchorRegistry.resolve("B")?.blockIndex)
        assertNull(result.anchorRegistry.resolve("eof-only"))
        assertEquals(listOf("eof-only"), result.ignoredTrailingHtmlAnchors)
    }

    @Test
    fun codeFence_doesNotRegisterHtmlAnchor() {
        val result = MarkdownParser.parse(
            """
            ```
            <a id="in-code"></a>
            ```
            # Real
            """.trimIndent(),
        )
        assertNull(result.anchorRegistry.resolve("in-code"))
        assertNotNull(result.anchorRegistry.resolve("Real"))
    }

    @Test
    fun inlineCode_doesNotYieldLinkDestinations() {
        val dests = InlineMarkdownParser.extractLinkDestinations(
            "见 `[link](#x)` 与 [真链](#real)",
        )
        assertEquals(listOf("#real"), dests)
    }

    @Test
    fun headingWithBoldAndLink_plainTitleAndFragment() {
        val result = MarkdownParser.parse("## **[附录](#appendix-a)** 说明")
        assertEquals("附录 说明", result.toc.single().title)
        assertEquals(0, result.anchorRegistry.resolve("appendix-a")?.blockIndex)
    }

    @Test
    fun normalizeHeadingPlainTitle_stripsLinkMarkup() {
        assertEquals(
            "德篇",
            MarkdownParser.normalizeHeadingPlainTitle("[德篇](#de-pian)"),
        )
        assertEquals(
            "粗体标题",
            MarkdownParser.normalizeHeadingPlainTitle("**粗体标题**"),
        )
    }
}
