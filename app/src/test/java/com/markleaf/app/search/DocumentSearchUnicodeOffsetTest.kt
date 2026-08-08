package com.markleaf.app.search

import com.markleaf.app.markdown.MarkdownBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A1a regression: case-insensitive search must return match ranges in the
 * ORIGINAL visible-text UTF-16 space, even when ROOT lowercase expands a
 * code point (U+0130 `İ` -> `i` + U+0307, 1 -> 2 UTF-16 units).
 *
 * All tests drive the real production path: MarkdownBlock -> extractor ->
 * DocumentSearch.search -> collectMatches. No foldCase-helper-only tests.
 */
class DocumentSearchUnicodeOffsetTest {

    private fun hits(md: String, query: String): List<SearchMatch> =
        DocumentSearch.search("d", listOf(MarkdownBlock.Paragraph(md)), query).matches

    /** Full user-visible contract: range + matchedText must be consistent with the original text. */
    private fun assertMatch(
        text: String,
        m: SearchMatch,
        expectedText: String,
        expectedStart: Int,
        expectedEnd: Int,
        message: String,
    ) {
        assertEquals("$message: matchStart", expectedStart, m.matchStart)
        assertEquals("$message: matchEnd", expectedEnd, m.matchEnd)
        assertEquals("$message: matchedText", expectedText, m.matchedText)
        // Range is a legal UTF-16 half-open range over the original text and
        // matchedText is exactly the original slice it denotes.
        assertTrue("$message: start < end", m.matchStart < m.matchEnd)
        assertTrue("$message: end <= text.length", m.matchEnd <= text.length)
        assertEquals("$message: matchedText == original slice", text.substring(m.matchStart, m.matchEnd), m.matchedText)
    }

    @Test
    fun hit_after_expansion_no_crash() {
        // Original UTF-16 units: A=0, İ=1 (single code unit), B=2.
        val text = "AİB"
        val m = hits(text, "b")
        assertEquals(1, m.size)
        assertMatch(text, m[0], "B", 2, 3, "AİB+b")
    }

    @Test
    fun hit_is_whole_expanded_codepoint_not_two_chars() {
        val text = "AİB"
        val byUpper = hits(text, "İ")
        assertEquals(1, byUpper.size)
        assertMatch(text, byUpper[0], "İ", 1, 2, "AİB+İ")
        val byLowered = hits(text, "i\u0307")
        assertEquals(1, byLowered.size)
        assertMatch(text, byLowered[0], "İ", 1, 2, "AİB+i+combining-dot")
        // Query folded to a single unit must not split the expansion either.
        val byPlainI = hits(text, "i")
        assertEquals(1, byPlainI.size)
        assertMatch(text, byPlainI[0], "İ", 1, 2, "AİB+i")
    }

    @Test
    fun hit_before_expansion() {
        val text = "İhello"
        val m = hits(text, "hello")
        assertEquals(1, m.size)
        assertMatch(text, m[0], "hello", 1, 6, "İhello+hello")
        val caseM = hits(text, "Hello")
        assertEquals(1, caseM.size)
        assertMatch(text, caseM[0], "hello", 1, 6, "İhello+Hello")
    }

    @Test
    fun hit_after_expansion() {
        val text = "helloİ"
        val hello = hits(text, "hello")
        assertEquals(1, hello.size)
        assertMatch(text, hello[0], "hello", 0, 5, "helloİ+hello")
        assertMatch(text, hits(text, "İ")[0], "İ", 5, 6, "helloİ+İ")
    }

    @Test
    fun multiple_hits_after_expansion_no_offset_drift() {
        // Original UTF-16 units: İ=0,a=1,İ=2,b=3,İ=4,c=5,İ=6,d=7.
        val text = "İaİbİcİd"
        val m = hits(text, "İ")
        assertEquals(4, m.size)
        assertMatch(text, m[0], "İ", 0, 1, "hit0")
        assertMatch(text, m[1], "İ", 2, 3, "hit1")
        assertMatch(text, m[2], "İ", 4, 5, "hit2")
        assertMatch(text, m[3], "İ", 6, 7, "hit3")

        // Original UTF-16 units: İ=0,x=1,İ=2,x=3.
        val x = hits("İxİx", "x")
        assertEquals(2, x.size)
        assertMatch("İxİx", x[0], "x", 1, 2, "x hit0")
        assertMatch("İxİx", x[1], "x", 3, 4, "x hit1")
    }

    @Test
    fun query_spanning_expansion() {
        val text = "AİB"
        assertMatch(text, hits(text, "İB")[0], "İB", 1, 3, "AİB+İB")
    }

    @Test
    fun whole_word_with_expansion() {
        val text = "İstanbul"
        assertMatch(text, hits(text, "İSTANBUL")[0], "İstanbul", 0, 8, "İstanbul+İSTANBUL")
    }

    @Test
    fun match_inside_expansion_sibling_text() {
        // text = İ, i, combining-dot → folded "i̇i̇" (4 units).
        val text = "İi\u0307"
        val m = hits(text, "i\u0307")
        assertEquals(2, m.size)
        assertMatch(text, m[0], "İ", 0, 1, "expansion hit")
        assertMatch(text, m[1], "i\u0307", 1, 3, "literal i+dot hit")
    }

    @Test
    fun ascii_and_chinese_no_regression() {
        val ascii = "Hello WORLD"
        assertMatch(ascii, hits(ascii, "hello world")[0], "Hello WORLD", 0, 11, "ascii")
        val chinese = "心电图与波形"
        assertMatch(chinese, hits(chinese, "波形")[0], "波形", 4, 6, "chinese")
    }

    @Test
    fun supplementary_plane_around_hits() {
        val text = "😀AİB"
        assertMatch(text, hits(text, "B")[0], "B", 4, 5, "surrogate-pair before hit")
        val two = hits("😀İ😀", "😀")
        assertEquals(2, two.size)
        assertMatch("😀İ😀", two[0], "😀", 0, 2, "pair hit0")
        assertMatch("😀İ😀", two[1], "😀", 3, 5, "pair hit1")
        assertMatch("😀İB", hits("😀İB", "İ")[0], "İ", 2, 3, "hit after pair")
    }

    @Test
    fun empty_query_and_no_hit() {
        assertTrue(hits("AİB", "").isEmpty())
        assertTrue(hits("AİB", "zzz").isEmpty())
    }

    @Test
    fun truncation_still_detects_remaining_after_expansion() {
        val text = "İ" + "a".repeat(DocumentSearch.MAX_MATCHES + 1)
        val r = DocumentSearch.search("d", listOf(MarkdownBlock.Paragraph(text)), "a")
        assertEquals(DocumentSearch.MAX_MATCHES, r.matches.size)
        assertTrue(r.truncated)
        // Ranges stay legal for the truncated set as well.
        for (m in r.matches) {
            assertTrue(m.matchStart < m.matchEnd && m.matchEnd <= text.length)
        }
    }

    @Test
    fun fold_length_additive_over_code_points() {
        // Pins the invariant buildFoldOriginMap relies on: ROOT lowercase length
        // is additive per code point, incl. final sigma, combining marks,
        // supplementary planes and the İ expansion. Content of a context mapping
        // (e.g. ς vs σ) does not affect offsets — only lengths matter.
        val corpus = listOf(
            "AİB", "İstanbul", "ΟΣ", "ΣΣς", "ΌΣ", "😀İ😀", "é", "ﬁ", "ẞ", "ı",
            "ǅ", "e\u0301", "AaBbİi\u0307", "İ" + "a".repeat(64),
        )
        for (s in corpus) {
            var sum = 0
            var cursor = 0
            while (cursor < s.length) {
                val cpLength = Character.charCount(s.codePointAt(cursor))
                sum += DocumentSearch.foldCase(s.substring(cursor, cursor + cpLength)).length
                cursor += cpLength
            }
            assertEquals("fold length additive for '$s'", DocumentSearch.foldCase(s).length, sum)
        }
    }

    @Test
    fun non_overlap_advance_contract_unchanged_in_folded_space() {
        // Existing semantic: "aaa" + "aa" → one non-overlapping hit.
        val m = hits("aaa", "aa")
        assertEquals(1, m.size)
        assertMatch("aaa", m[0], "aa", 0, 2, "non-overlap")
    }

    @Test
    fun preview_is_original_text_slice() {
        val text = "İhello"
        val m = hits(text, "hello")[0]
        assertTrue("preview contains the match", m.preview.contains("hello"))
        assertFalse("preview not empty", m.preview.isEmpty())
    }
}
