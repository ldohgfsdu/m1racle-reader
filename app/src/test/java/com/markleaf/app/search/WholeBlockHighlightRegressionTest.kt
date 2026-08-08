package com.markleaf.app.search

import androidx.compose.ui.graphics.Color
import com.markleaf.app.markdown.InlineMarkdownParser
import com.markleaf.app.markdown.MarkdownBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end regression for "whole paragraph highlighted" and "no highlight"
 * reports: searching a word must highlight ONLY the matched words.
 *
 * Root cause: the rendered text (InlineMarkdownParser.parse) and the search
 * text (toPlainText) drifted apart on breaks/escapes/HTML/entities — search
 * offsets no longer matched the rendered string. The invariant below must
 * hold: parse output text == toPlainText output.
 */
class WholeBlockHighlightRegressionTest {

    private fun backgroundSpans(text: String, ranges: List<SearchHighlightRange>): List<Pair<Int, Int>> {
        val out = InlineMarkdownParser.parse(text).withSearchHighlights(
            ranges, Color.Yellow, Color.Red,
        )
        return out.spanStyles
            .filter { it.item.background == Color.Yellow || it.item.background == Color.Red }
            .map { it.start to it.end }
            .sortedBy { it.first }
    }

    @Test
    fun renderedTextEqualsSearchPlainText_invariant() {
        val cases = listOf(
            "**成功**是**成功**",
            "这是[链接](https://x)和`代码`",
            "a&amp;b",
            "行1\n行2",
            "转义\\*星号",
            "<b>粗标签</b>文本",
            "普通文本",
            "下划线_中间_词",
            "![图片alt](img.png)文字",
        )
        for (c in cases) {
            val parsed = InlineMarkdownParser.parse(c).text
            val plain = InlineMarkdownParser.toPlainText(c)
            assertTrue(
                "parse != toPlainText for '$c': parsed='$parsed' plain='$plain'",
                parsed == plain,
            )
        }
    }

    @Test
    fun twoMatchesInSameParagraph_highlightOnlyWords() {
        val text = "成功是检验成功的手段"
        val blocks = listOf(MarkdownBlock.Paragraph(text))
        val result = DocumentSearch.search("doc", blocks, "成功", listOf("b0"))
        assertEquals(2, result.matches.size)
        val grouped = groupMatchesByBlockIndex(result.matches)
        val highlights = blockHighlightsFromGrouped(0, grouped[0], null)!!
        val spans = backgroundSpans(text, highlights.wholeBlockRanges)
        assertEquals(listOf(0 to 2, 5 to 7), spans)
    }

    @Test
    fun listItemMatch_highlightWordOnly() {
        val item = "导入导出成功率"
        val blocks = listOf(MarkdownBlock.ListBlock(ordered = false, items = listOf(item)))
        val result = DocumentSearch.search("doc", blocks, "成功", listOf("b0"))
        assertEquals(1, result.matches.size)
        val grouped = groupMatchesByBlockIndex(result.matches)
        val highlights = blockHighlightsFromGrouped(0, grouped[0], null)!!
        val spans = backgroundSpans(item, highlights.forListItem(0))
        assertEquals(listOf(4 to 6), spans)
    }

    @Test
    fun twoMatchesWithMarkers_highlightOnlyWords() {
        val text = "**成功**是**成功**"
        val blocks = listOf(MarkdownBlock.Paragraph(text))
        val result = DocumentSearch.search("doc", blocks, "成功", listOf("b0"))
        assertEquals(2, result.matches.size)
        val grouped = groupMatchesByBlockIndex(result.matches)
        val highlights = blockHighlightsFromGrouped(0, grouped[0], null)!!
        // Both matches highlight exactly the two words (plain offsets, since
        // rendered text == plain text).
        assertEquals(listOf(0 to 2, 3 to 5), highlights.wholeBlockRanges.map { it.start to it.end })
        val spans = backgroundSpans(text, highlights.wholeBlockRanges)
        assertEquals(listOf(0 to 2, 3 to 5), spans)
    }

    @Test
    fun highlightAfterEntity_offsetsStillAligned() {
        // The old bug: parse kept "&amp;" (5 chars) while search used "&" (1),
        // shifting every offset after an entity.
        val text = "A&amp;B成功C"
        val plain = InlineMarkdownParser.toPlainText(text)
        assertEquals("A&B成功C", plain)
        val blocks = listOf(MarkdownBlock.Paragraph(text))
        val result = DocumentSearch.search("doc", blocks, "成功", listOf("b0"))
        assertEquals(1, result.matches.size)
        val grouped = groupMatchesByBlockIndex(result.matches)
        val highlights = blockHighlightsFromGrouped(0, grouped[0], null)!!
        val spans = backgroundSpans(text, highlights.wholeBlockRanges)
        assertEquals(listOf(3 to 5), spans)
    }
}
