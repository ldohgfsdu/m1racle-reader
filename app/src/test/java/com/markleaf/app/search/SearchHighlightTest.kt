package com.markleaf.app.search

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import com.markleaf.app.markdown.InlineMarkdownParser
import com.markleaf.app.markdown.MarkdownBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchHighlightTest {

    private val normal = Color(0xFFAAAA00)
    private val active = Color(0xFFFFFF00)

    @Test
    fun parseTextEqualsToPlainText_forCommonInline() {
        val samples = listOf(
            "hello **world**",
            "plain",
            "a *b* c",
            "`code` tail",
            "[Visible Link](https://hidden.example)",
            "mix **bold** and `code`",
            "临床检索验证词 出现第一次",
            "A😀B😀C",
        )
        for (s in samples) {
            val plain = InlineMarkdownParser.toPlainText(s)
            val parsed = InlineMarkdownParser.parse(s)
            assertEquals("parity for: $s", plain, parsed.text)
            assertEquals(plain.length, parsed.length)
        }
    }

    @Test
    fun helloWorldBold_searchRangeCoversWholeVisibleAndKeepsBoldSpan() {
        val source = "hello **world**"
        val plain = InlineMarkdownParser.toPlainText(source)
        assertEquals("hello world", plain)
        val base = InlineMarkdownParser.parse(source)
        val ranges = listOf(SearchHighlightRange(0, plain.length, isActive = true))
        val out = base.withSearchHighlights(ranges, normal, active)
        assertEquals("hello world", out.text)
        // Bold still on "world" (indices 6..11)
        val boldOnWorld = out.spanStyles.any { span ->
            span.start <= 6 && span.end >= 11 && span.item.fontWeight != null
        }
        assertTrue("world should remain bold", boldOnWorld)
        // Active background covers full phrase
        val bgFull = out.spanStyles.any { span ->
            span.start == 0 && span.end == plain.length && span.item.background == active
        }
        assertTrue(bgFull)
    }

    @Test
    fun linkLabel_highlightPreservesLinkAnnotation() {
        val source = "[Visible Link](https://hidden.example)"
        val plain = InlineMarkdownParser.toPlainText(source)
        assertEquals("Visible Link", plain)
        val base = InlineMarkdownParser.parse(source)
        val ranges = listOf(SearchHighlightRange(0, plain.length, isActive = false))
        val out = base.withSearchHighlights(ranges, normal, active)
        val ann = out.getStringAnnotations(
            InlineMarkdownParser.LINK_ANNOTATION_TAG,
            0,
            out.length,
        )
        assertTrue(ann.isNotEmpty())
        assertEquals("https://hidden.example", ann.first().item)
    }

    @Test
    fun sameBlockTwoChineseMatches_activeTogglesWithoutRescan() {
        val text = "临床检索验证词 填充 临床检索验证词"
        val blocks = listOf(MarkdownBlock.Paragraph(text))
        val result = DocumentSearch.search(
            documentKey = "doc",
            blocks = blocks,
            rawQuery = "临床检索验证词",
            blockIds = listOf("b0"),
        )
        assertEquals(2, result.matches.size)
        assertEquals(0, result.matches[0].blockIndex)
        assertEquals(0, result.matches[1].blockIndex)
        assertTrue(result.matches[0].matchStart < result.matches[1].matchStart)

        val grouped = groupMatchesByBlockIndex(result.matches)
        val h0 = blockHighlightsFromGrouped(0, grouped[0], result.matches[0].stableKey)!!
        assertEquals(2, h0.wholeBlockRanges.size)
        assertTrue(h0.wholeBlockRanges[0].isActive)
        assertFalse(h0.wholeBlockRanges[1].isActive)

        val h1 = blockHighlightsFromGrouped(0, grouped[0], result.matches[1].stableKey)!!
        assertFalse(h1.wholeBlockRanges[0].isActive)
        assertTrue(h1.wholeBlockRanges[1].isActive)
    }

    @Test
    fun emojiSurrogatePairs_twoMatches() {
        val text = "A😀B😀C"
        val blocks = listOf(MarkdownBlock.Paragraph(text))
        val result = DocumentSearch.search(
            documentKey = "doc",
            blocks = blocks,
            rawQuery = "😀",
            blockIds = listOf("b0"),
        )
        assertEquals(2, result.matches.size)
        // Each emoji is one code point = 2 UTF-16 units
        assertEquals(2, result.matches[0].matchEnd - result.matches[0].matchStart)
        assertEquals(2, result.matches[1].matchEnd - result.matches[1].matchStart)
        val base = InlineMarkdownParser.parse(text)
        val ranges = result.matches.mapIndexed { i, m ->
            SearchHighlightRange(m.matchStart, m.matchEnd, isActive = i == 0)
        }
        val out = base.withSearchHighlights(ranges, normal, active)
        assertEquals(text, out.text)
        assertEquals(text.length, out.length)
    }

    @Test
    fun invalidRange_skippedNotCoerced() {
        val base = InlineMarkdownParser.parse("abc")
        val ranges = listOf(
            SearchHighlightRange(0, 2, isActive = true),
            SearchHighlightRange(-1, 1, isActive = false),
            SearchHighlightRange(1, 99, isActive = false),
            SearchHighlightRange(2, 2, isActive = false),
        )
        val out = base.withSearchHighlights(ranges, normal, active)
        assertEquals("abc", out.text)
        // Only valid [0,2) applied
        val bgs = out.spanStyles.filter { it.item.background != null }
        assertTrue(bgs.any { it.start == 0 && it.end == 2 })
        assertFalse(bgs.any { it.end > out.length })
    }

    @Test
    fun groupMatches_doesNotRebuildOnActiveOnly_identityStable() {
        val m1 = SearchMatch("d", "b", 0, 0, 2, listItemIndex = -1)
        val m2 = SearchMatch("d", "b", 0, 4, 6, listItemIndex = -1)
        val list = listOf(m1, m2)
        val g1 = groupMatchesByBlockIndex(list)
        val g2 = groupMatchesByBlockIndex(list)
        assertEquals(g1[0]!!.size, g2[0]!!.size)
        assertEquals(1, g1.size)
    }

    @Test
    fun tableCellOrdinal_localOffsets() {
        val table = MarkdownBlock.Table(
            headers = listOf("Name", "Val"),
            alignments = emptyList(),
            rows = listOf(listOf("alpha", "beta")),
        )
        val result = DocumentSearch.search(
            documentKey = "doc",
            blocks = listOf(table),
            rawQuery = "beta",
            blockIds = listOf("t0"),
        )
        assertEquals(1, result.matches.size)
        val m = result.matches.single()
        // headers 0,1 then row cells 2,3 → beta is ordinal 3
        assertEquals(3, m.listItemIndex)
        assertEquals(0, m.matchStart)
        assertEquals(4, m.matchEnd)
    }

    @Test
    fun isValidHighlightRange_gate() {
        assertTrue(isValidHighlightRange(0, 1, 1))
        assertFalse(isValidHighlightRange(0, 0, 1))
        assertFalse(isValidHighlightRange(-1, 1, 2))
        assertFalse(isValidHighlightRange(0, 3, 2))
    }

    @Test
    fun activeSpan_overlaysAfterNormal() {
        val base = InlineMarkdownParser.parse("abcdefgh")
        val ranges = listOf(
            SearchHighlightRange(0, 4, isActive = false),
            SearchHighlightRange(2, 6, isActive = true),
        )
        val out = base.withSearchHighlights(ranges, normal, active)
        val activeSpans = out.spanStyles.filter { it.item.background == active }
        assertTrue(activeSpans.any { it.start == 2 && it.end == 6 })
    }

    @Test
    fun documentSearch_helloWorld_offsets() {
        val blocks = listOf(MarkdownBlock.Paragraph("hello **world**"))
        val hits = DocumentSearch.search(blocks, "hello world")
        assertEquals(1, hits.size)
        assertEquals(0, hits[0].matchStart)
        assertEquals(11, hits[0].matchEnd)
    }

    @Test
    fun crossBlock_activeMove_onlyActiveFlagChangesOnTargetBlocks() {
        val blocks = listOf(
            MarkdownBlock.Paragraph("alpha TOKEN here"),
            MarkdownBlock.Paragraph("beta TOKEN there"),
        )
        val result = DocumentSearch.search(
            documentKey = "doc",
            blocks = blocks,
            rawQuery = "TOKEN",
            blockIds = listOf("b0", "b1"),
        )
        assertEquals(2, result.matches.size)
        assertEquals(0, result.matches[0].blockIndex)
        assertEquals(1, result.matches[1].blockIndex)

        val grouped = groupMatchesByBlockIndex(result.matches)
        // Same grouped map for both active indices (no regroup).
        val hA0 = blockHighlightsFromGrouped(0, grouped[0], result.matches[0].stableKey)!!
        val hB0 = blockHighlightsFromGrouped(1, grouped[1], result.matches[0].stableKey)!!
        assertTrue(hA0.wholeBlockRanges.single().isActive)
        assertFalse(hB0.wholeBlockRanges.single().isActive)

        val hA1 = blockHighlightsFromGrouped(0, grouped[0], result.matches[1].stableKey)!!
        val hB1 = blockHighlightsFromGrouped(1, grouped[1], result.matches[1].stableKey)!!
        assertFalse(hA1.wholeBlockRanges.single().isActive)
        assertTrue(hB1.wholeBlockRanges.single().isActive)

        // Ranges positions unchanged across active switch.
        assertEquals(hA0.wholeBlockRanges.single().start, hA1.wholeBlockRanges.single().start)
        assertEquals(hB0.wholeBlockRanges.single().start, hB1.wholeBlockRanges.single().start)
    }

    @Test
    fun emptyMatches_andInactive_yieldNoHighlights() {
        assertTrue(groupMatchesByBlockIndex(emptyList()).isEmpty())
        assertEquals(null, blockHighlightsFromGrouped(0, null, "x"))
        assertEquals(null, blockHighlightsFromGrouped(0, emptyList(), "x"))
    }

    @Test
    fun stableKey_distinguishesIdenticalTextOccurrences() {
        val text = "xx xx"
        val result = DocumentSearch.search(
            documentKey = "doc",
            blocks = listOf(MarkdownBlock.Paragraph(text)),
            rawQuery = "xx",
            blockIds = listOf("b0"),
        )
        assertEquals(2, result.matches.size)
        assertTrue(result.matches[0].stableKey != result.matches[1].stableKey)
        val grouped = groupMatchesByBlockIndex(result.matches)
        val a0 = blockHighlightsFromGrouped(0, grouped[0], result.matches[0].stableKey)!!
        val a1 = blockHighlightsFromGrouped(0, grouped[0], result.matches[1].stableKey)!!
        assertTrue(a0.wholeBlockRanges[0].isActive && !a0.wholeBlockRanges[1].isActive)
        assertTrue(!a1.wholeBlockRanges[0].isActive && a1.wholeBlockRanges[1].isActive)
    }

    @Test
    fun table_duplicateCellText_usesOrdinalNotSharedOffset() {
        val table = MarkdownBlock.Table(
            headers = listOf("A", "B"),
            alignments = emptyList(),
            rows = listOf(listOf("same", "same")),
        )
        val result = DocumentSearch.search(
            documentKey = "doc",
            blocks = listOf(table),
            rawQuery = "same",
            blockIds = listOf("t0"),
        )
        assertEquals(2, result.matches.size)
        // body cells: ordinals 2 and 3 after headers 0,1
        assertEquals(2, result.matches[0].listItemIndex)
        assertEquals(3, result.matches[1].listItemIndex)
        assertEquals(0, result.matches[0].matchStart)
        assertEquals(0, result.matches[1].matchStart)
        val hl = blockHighlightsFromGrouped(
            0,
            groupMatchesByBlockIndex(result.matches)[0],
            result.matches[1].stableKey,
        )!!
        val cell2 = hl.forCell(2)
        val cell3 = hl.forCell(3)
        assertEquals(1, cell2.size)
        assertEquals(1, cell3.size)
        assertFalse(cell2.single().isActive)
        assertTrue(cell3.single().isActive)
    }

    @Test
    fun table_emptyHeaderCell_ordinalStillAligned() {
        val table = MarkdownBlock.Table(
            headers = listOf("", "Val"),
            alignments = emptyList(),
            rows = listOf(listOf("x", "y")),
        )
        val segs = MarkdownSearchTextExtractor.extract("d", listOf(table), listOf("t0"))
        // empty header skipped; ordinals still advanced: Val=1, x=2, y=3
        assertTrue(segs.none { it.listItemIndex == 0 })
        assertEquals(1, segs.single { it.visibleText == "Val" }.listItemIndex)
        assertEquals(2, segs.single { it.visibleText == "x" }.listItemIndex)
        assertEquals(3, segs.single { it.visibleText == "y" }.listItemIndex)
        val hits = DocumentSearch.search("d", listOf(table), "y", listOf("t0"))
        assertEquals(1, hits.matches.size)
        assertEquals(3, hits.matches.single().listItemIndex)
    }

    @Test
    fun groupMap_reusedAcrossActiveKeys_sameMatchesList() {
        val matches = DocumentSearch.search(
            documentKey = "doc",
            blocks = listOf(
                MarkdownBlock.Paragraph("one HIT"),
                MarkdownBlock.Paragraph("two HIT"),
            ),
            rawQuery = "HIT",
            blockIds = listOf("a", "b"),
        ).matches
        val map = groupMatchesByBlockIndex(matches)
        // Active-only derivation uses same map entries (identity preserved).
        val list0 = map[0]!!
        val list1 = map[1]!!
        blockHighlightsFromGrouped(0, list0, matches[0].stableKey)
        blockHighlightsFromGrouped(1, list1, matches[0].stableKey)
        blockHighlightsFromGrouped(0, list0, matches[1].stableKey)
        blockHighlightsFromGrouped(1, list1, matches[1].stableKey)
        assertTrue(map[0] === list0)
        assertTrue(map[1] === list1)
    }

    @Test
    fun inlineCode_highlightKeepsFontFamilySpan() {
        val source = "before `code` after"
        val plain = InlineMarkdownParser.toPlainText(source)
        assertEquals("before code after", plain)
        val base = InlineMarkdownParser.parse(source)
        val codeStart = plain.indexOf("code")
        val ranges = listOf(SearchHighlightRange(codeStart, codeStart + 4, isActive = true))
        val out = base.withSearchHighlights(ranges, normal, active)
        assertEquals(plain, out.text)
        val hasMono = out.spanStyles.any { span ->
            span.start <= codeStart && span.end >= codeStart + 4 &&
                span.item.fontFamily != null
        }
        assertTrue("inline code fontFamily should remain", hasMono)
    }

    @Test
    fun hiddenUrl_notSearchable_andLabelStillHighlightable() {
        val source = "[Visible Link](https://hidden.example/path)"
        val hitsUrl = DocumentSearch.search(
            listOf(MarkdownBlock.Paragraph(source)),
            "hidden.example",
        )
        assertTrue(hitsUrl.isEmpty())
        val hitsLabel = DocumentSearch.search(
            listOf(MarkdownBlock.Paragraph(source)),
            "Visible Link",
        )
        assertEquals(1, hitsLabel.size)
        val base = InlineMarkdownParser.parse(source)
        val r = SearchHighlightRange(hitsLabel[0].matchStart, hitsLabel[0].matchEnd, true)
        val out = base.withSearchHighlights(listOf(r), normal, active)
        assertTrue(
            out.getStringAnnotations(InlineMarkdownParser.LINK_ANNOTATION_TAG, 0, out.length)
                .isNotEmpty(),
        )
    }

    @Test
    fun forWholeBlock_excludesListItemRanges() {
        val whole = listOf(SearchHighlightRange(0, 1, false, listItemIndex = -1))
        val item0 = listOf(SearchHighlightRange(0, 1, true, listItemIndex = 0))
        val hl = BlockSearchHighlights(
            blockIndex = 0,
            wholeBlockRanges = whole,
            byListItem = mapOf(0 to item0),
        )
        assertEquals(1, hl.forWholeBlock().size)
        assertEquals(-1, hl.forWholeBlock().single().listItemIndex)
        assertEquals(1, hl.forListItem(0).size)
    }

    @Test
    fun withSearchHighlights_emptyRanges_returnsSameInstance() {
        val base = InlineMarkdownParser.parse("hello **world**")
        val out = base.withSearchHighlights(emptyList(), normal, active)
        assertTrue(base === out)
    }
}
