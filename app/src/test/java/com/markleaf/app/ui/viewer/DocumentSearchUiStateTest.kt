package com.markleaf.app.ui.viewer

import com.markleaf.app.search.SearchMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure UI-state semantics for R9 P0 (no Android ViewModel runtime).
 */
class DocumentSearchUiStateTest {

    private fun match(i: Int) = SearchMatch(
        documentKey = "d",
        blockId = "paragraph-0",
        blockIndex = 0,
        matchStart = i,
        matchEnd = i + 1,
        matchedText = "x",
    )

    @Test
    fun counter_empty_query_is_em_dash() {
        assertEquals("—", DocumentSearchUiState().counterLabel)
        assertNull(DocumentSearchUiState().currentMatch)
        assertFalse(DocumentSearchUiState().hasMatches)
    }

    @Test
    fun counter_query_no_matches() {
        assertEquals(
            "0 / 0",
            DocumentSearchUiState(query = "xyz", matches = emptyList()).counterLabel,
        )
    }

    @Test
    fun counter_with_matches() {
        val s = DocumentSearchUiState(
            query = "x",
            matches = listOf(match(0), match(1), match(2)),
            currentIndex = 1,
        )
        assertEquals("2 / 3", s.counterLabel)
        assertEquals(1, s.currentMatch?.matchStart)
        assertTrue(s.hasMatches)
    }

    @Test
    fun counter_truncated() {
        val s = DocumentSearchUiState(
            query = "x",
            matches = listOf(match(0), match(1)),
            currentIndex = 0,
            resultsTruncated = true,
        )
        assertEquals("1 / 2+", s.counterLabel)
    }

    @Test
    fun documentKey_defaults_and_binding() {
        val s = DocumentSearchUiState(isActive = true, documentKey = "doc-a", query = "q")
        assertEquals("doc-a", s.documentKey)
        assertEquals("q", s.query)
        assertTrue(s.isActive)
    }
}
