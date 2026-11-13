package com.m1racle.reader.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cross-platform parse of a small, synthetic fixture with the same anchor/TOC shapes
 * as the human-acceptance 老子 document.
 */
class LaoziAnchorFixtureTest {

    private fun loadFixture(): String = requireNotNull(
        javaClass.getResource("/fixtures/laozi-anchor-minimal.md"),
    ).readText(Charsets.UTF_8)

    @Test
    fun laozi_fixture_anchorsAndTocPlainTitles() {
        val result = MarkdownParser.parse(loadFixture())

        // HTML empty anchors not visible as paragraphs
        val visible = result.blocks.joinToString("\n") {
            when (it) {
                is MarkdownBlock.Heading -> it.text
                is MarkdownBlock.Paragraph -> it.text
                is MarkdownBlock.Table -> (it.headers + it.rows.flatten()).joinToString("|")
                else -> ""
            }
        }
        assertFalse(visible.contains("<a id="))
        assertFalse(visible.contains("<a name="))

        // Side TOC plain titles (linked headings)
        val tocTitles = result.toc.map { it.title }
        assertTrue(tocTitles.contains("德篇"))
        assertTrue(tocTitles.contains("道篇"))
        assertFalse(tocTitles.any { it.contains("[德篇]") || it.contains("#de-pian") })
        assertFalse(tocTitles.any { it.contains("[道篇]") || it.contains("#dao-pian") })

        // Authored HTML anchors bind to following headings
        val dePian = result.anchorRegistry.resolve("de-pian")
        assertNotNull(dePian)
        val deHeading = result.blocks[dePian!!.blockIndex] as MarkdownBlock.Heading
        assertTrue(deHeading.text.contains("德篇"))

        val ch01 = result.anchorRegistry.resolve("chapter-01")
        assertNotNull(ch01)
        val chHeading = result.blocks[ch01!!.blockIndex] as MarkdownBlock.Heading
        assertTrue(
            "expected 第一章 heading, got: ${chHeading.text}",
            chHeading.text.contains("第一章") && chHeading.text.contains("论德"),
        )

        val dao = result.anchorRegistry.resolve("dao-pian")
        assertNotNull(dao)

        // Table of contents links keep destinations
        val table = result.blocks.filterIsInstance<MarkdownBlock.Table>().first()
        val flatCells = table.headers + table.rows.flatten()
        assertTrue(flatCells.any { it.contains("[第一章](#chapter-01)") })
        val dests = flatCells.flatMap { InlineMarkdownParser.extractLinkDestinations(it) }
        assertTrue(dests.contains("#chapter-01"))
        assertTrue(dests.contains("#chapter-02"))

        // Missing fragment safe
        assertNull(result.anchorRegistry.resolve("this-fragment-does-not-exist"))

        // No silent trailing-bind pollution for this fixture (ends with content, not orphan anchors)
        assertTrue(
            "unexpected trailing ignores: ${result.ignoredTrailingHtmlAnchors}",
            result.ignoredTrailingHtmlAnchors.isEmpty(),
        )

        assertTrue(result.anchorRegistry.contains("德篇"))
        assertTrue(result.anchorRegistry.contains("道篇"))
        assertTrue(result.anchorRegistry.contains("chapter-01"))
        assertTrue(result.anchorRegistry.contains("chapter-02"))
    }
}
