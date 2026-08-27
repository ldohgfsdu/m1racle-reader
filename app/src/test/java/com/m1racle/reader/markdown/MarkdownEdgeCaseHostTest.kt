package com.m1racle.reader.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host-only edge cases for release gates: abnormal input, duplicate anchors,
 * Chinese / URL-encoded fragments, empty docs — all drive [MarkdownParser] /
 * [MarkdownLinkRouter] / [AnchorRegistry] production code.
 */
class MarkdownEdgeCaseHostTest {

    @Test
    fun emptyDocument_isSafe() {
        val r = MarkdownParser.parse("")
        assertTrue(r.blocks.isEmpty())
        assertTrue(r.toc.isEmpty())
        assertEquals(0, r.anchorRegistry.size)
    }

    @Test
    fun onlyWhitespace_isSafe() {
        val r = MarkdownParser.parse("   \n\n\t\n  ")
        assertTrue(r.blocks.isEmpty())
    }

    @Test
    fun binaryLikeGarbage_doesNotCrash() {
        val junk = ByteArray(256) { it.toByte() }.toString(Charsets.ISO_8859_1)
        val r = MarkdownParser.parse(junk)
        // May produce paragraphs; must not throw.
        assertNotNull(r.blocks)
    }

    @Test
    fun duplicateHtmlAnchors_lastWinsStable() {
        val r = MarkdownParser.parse(
            """
            <a id="dup"></a>
            # First
            <a id="dup"></a>
            # Second
            """.trimIndent(),
        )
        // associateBy keeps last registration for same id
        assertEquals(1, r.anchorRegistry.resolve("dup")?.blockIndex)
        val heading = r.blocks[1] as MarkdownBlock.Heading
        assertEquals("Second", heading.text)
    }

    @Test
    fun chineseFragment_resolvesHeadingSlug() {
        val r = MarkdownParser.parse("# 德篇\n\n正文")
        assertEquals(0, r.anchorRegistry.resolve("德篇")?.blockIndex)
        assertEquals(0, r.anchorRegistry.resolve("#德篇")?.blockIndex)
        when (val a = MarkdownLinkRouter.classify("#德篇")) {
            is MarkdownLinkRouter.Action.InPageAnchor ->
                assertEquals(0, r.anchorRegistry.resolve(a.anchorId)?.blockIndex)
            else -> error("expected in-page")
        }
    }

    @Test
    fun urlEncodedFragment_decodesBeforeResolve() {
        // 章 = %E7%AB%A0
        val r = MarkdownParser.parse("# 章\n")
        val action = MarkdownLinkRouter.classify("#%E7%AB%A0")
        assertTrue(action is MarkdownLinkRouter.Action.InPageAnchor)
        val id = (action as MarkdownLinkRouter.Action.InPageAnchor).anchorId
        assertEquals("章", id)
        assertEquals(0, r.anchorRegistry.resolve(id)?.blockIndex)
    }

    @Test
    fun missingFragment_isNull_notExternal() {
        val r = MarkdownParser.parse("# Only")
        assertNull(r.anchorRegistry.resolve("missing-xyz"))
        val action = MarkdownLinkRouter.classify("#missing-xyz")
        assertTrue(action is MarkdownLinkRouter.Action.InPageAnchor)
        // Caller must not open browser for InPageAnchor misses
        assertNull(r.anchorRegistry.resolve((action as MarkdownLinkRouter.Action.InPageAnchor).anchorId))
    }

    @Test
    fun relativeMdPath_isExternalNotInPage() {
        val action = MarkdownLinkRouter.classify("other.md#frag")
        assertTrue(action is MarkdownLinkRouter.Action.External)
    }

    @Test
    fun https_isExternal() {
        val action = MarkdownLinkRouter.classify("https://example.com/a")
        assertTrue(action is MarkdownLinkRouter.Action.External)
        assertEquals(
            "https://example.com/a",
            (action as MarkdownLinkRouter.Action.External).uri,
        )
    }

    @Test
    fun codeFence_htmlAnchorNotRegistered() {
        val r = MarkdownParser.parse(
            """
            ```
            <a id="fake"></a>
            ```
            # Real
            """.trimIndent(),
        )
        assertNull(r.anchorRegistry.resolve("fake"))
        assertNotNull(r.anchorRegistry.resolve("Real"))
    }

    @Test
    fun unclosedFence_doesNotCrash() {
        val r = MarkdownParser.parse("```\nno close\n# still in fence?")
        assertTrue(r.blocks.any { it is MarkdownBlock.CodeBlock })
    }

    @Test
    fun linkedHeading_tocPlainAndFragment() {
        val r = MarkdownParser.parse("## [附录甲](#app-a)\n")
        assertEquals("附录甲", r.toc.single().title)
        assertFalse(r.toc.single().title.contains("["))
        assertEquals(0, r.anchorRegistry.resolve("app-a")?.blockIndex)
    }
}
