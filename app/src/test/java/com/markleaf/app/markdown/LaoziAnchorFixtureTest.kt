package com.markleaf.app.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Offline parse of the human-acceptance 老子 fixture when present on the host/device FS.
 * Skips (not fails) if the file is unavailable in CI.
 */
class LaoziAnchorFixtureTest {

    private val fixtureCandidates = listOf(
        "/storage/emulated/0/Download/老子（帛书精堪版）-整理排版版.md",
        "/storage/emulated/0/老子（帛书精堪版）-整理排版版.md",
    )

    private fun loadFixture(): String? {
        for (path in fixtureCandidates) {
            val f = File(path)
            if (f.isFile && f.canRead()) return f.readText()
        }
        return null
    }

    @Test
    fun laozi_fixture_anchorsAndTocPlainTitles() {
        val source = loadFixture()
        assumeTrue("老子整理排版版 fixture not mounted", source != null)

        val result = MarkdownParser.parse(source!!)

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

        // Duplicate registration of same HTML id should not explode; last wins is OK
        assertTrue(result.anchorRegistry.size > 80)
    }
}
