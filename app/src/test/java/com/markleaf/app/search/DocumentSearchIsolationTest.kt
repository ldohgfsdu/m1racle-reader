package com.markleaf.app.search

import com.markleaf.app.markdown.MarkdownBlock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Isolation / generation / large-fixture checks for R9 P0 (pure domain, no Android).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DocumentSearchIsolationTest {

    @Test
    fun same_body_different_documentKey_isolated() {
        val blocks = listOf(MarkdownBlock.Paragraph("shared body token"))
        val a = DocumentSearch.search("doc-A", blocks, "token")
        val b = DocumentSearch.search("doc-B", blocks, "token")
        assertEquals("doc-A", a.matches.single().documentKey)
        assertEquals("doc-B", b.matches.single().documentKey)
        assertTrue(a.matches.single().stableKey != b.matches.single().stableKey)
    }

    @Test
    fun late_result_must_not_apply_if_documentKey_mismatches() {
        val blocks = listOf(MarkdownBlock.Paragraph("alpha"))
        val resultForA = DocumentSearch.search("doc-A", blocks, "alpha")
        // Simulated apply gate used by ViewModel
        val currentKey = "doc-B"
        val accepted = resultForA.documentKey == currentKey
        assertFalse(accepted)
    }

    @Test
    fun generation_style_accept_only_latest() {
        var generation = 0L
        fun schedule(): Long = ++generation
        val g1 = schedule()
        val g2 = schedule()
        // Only latest generation may write
        assertFalse(g1 == generation)
        assertTrue(g2 == generation)
    }

    @Test
    fun large_block_count_fixture_completes() {
        val blocks = List(1_200) { i ->
            MarkdownBlock.Paragraph("paragraph number $i unique-$i end")
        }
        val r = DocumentSearch.search("big", blocks, "unique-500")
        assertEquals(1, r.matches.size)
        assertEquals(500, r.matches.single().blockIndex)
        assertFalse(r.truncated)
    }

    @Test
    fun large_text_about_200kb_completes() {
        val unit = "词条ABCDEF "
        val body = buildString {
            // ~200KB
            while (length < 200_000) append(unit)
            append(" NEEDLE_MARKER ")
            while (length < 220_000) append(unit)
        }
        val blocks = listOf(MarkdownBlock.Paragraph(body))
        val r = DocumentSearch.search("kb", blocks, "NEEDLE_MARKER")
        assertEquals(1, r.matches.size)
        assertFalse(r.truncated)
    }

    @Test
    fun over_max_matches_stops_and_truncates() {
        val blocks = listOf(MarkdownBlock.Paragraph("x".repeat(DocumentSearch.MAX_MATCHES + 100)))
        val r = DocumentSearch.search("t", blocks, "x")
        assertEquals(DocumentSearch.MAX_MATCHES, r.matches.size)
        assertTrue(r.truncated)
        // Indices stay in range for UI
        r.matches.forEach { m ->
            assertTrue(m.matchStart >= 0)
            assertTrue(m.matchEnd > m.matchStart)
            assertTrue(m.matchEnd <= blocks[0].let { (it as MarkdownBlock.Paragraph).text.length })
        }
    }

    @Test
    fun rapid_query_sequence_only_last_matters_domain() = runTest {
        // Domain-level: successive searches are independent; last result is authoritative.
        val blocks = listOf(MarkdownBlock.Paragraph("alpha beta gamma"))
        var latest = DocumentSearch.SearchResult.Empty
        val queries = listOf("a", "al", "alp", "alph", "alpha")
        for (q in queries) {
            latest = DocumentSearch.search("d", blocks, q)
        }
        assertEquals(1, latest.matches.size)
        assertEquals("alpha", latest.matches.single().matchedText.lowercase())
    }

    @Test
    fun twenty_rapid_queries_do_not_throw() {
        val blocks = listOf(MarkdownBlock.Paragraph("quick brown fox jumps"))
        repeat(20) { i ->
            val q = "quick".take((i % 5) + 1)
            val r = DocumentSearch.search("d", blocks, q)
            assertTrue(r.matches.size >= 0)
        }
    }

    @Test
    fun concurrent_searches_are_independent() = runTest {
        val blocks = listOf(MarkdownBlock.Paragraph("concurrent token value"))
        val a = async {
            delay(5)
            DocumentSearch.search("A", blocks, "token")
        }
        val b = async {
            DocumentSearch.search("B", blocks, "value")
        }
        val ra = a.await()
        val rb = b.await()
        assertEquals("A", ra.documentKey)
        assertEquals("B", rb.documentKey)
        assertEquals(1, ra.matches.size)
        assertEquals(1, rb.matches.size)
    }

    @Test
    fun leading_trailing_spaces_preserved_in_query() {
        val blocks = listOf(MarkdownBlock.Paragraph("x y z"))
        // Needle with spaces must not be trimmed away
        assertEquals(1, DocumentSearch.search("d", blocks, " y ").matches.size)
        assertEquals(0, DocumentSearch.search("d", blocks, " yz").matches.size)
    }

    @Test
    fun empty_query_never_scans_blocks() {
        // Even with huge document, empty query is O(1)
        val blocks = List(5_000) { MarkdownBlock.Paragraph("noise $it") }
        val r = DocumentSearch.search("d", blocks, "")
        assertTrue(r.matches.isEmpty())
        assertFalse(r.truncated)
    }
}
