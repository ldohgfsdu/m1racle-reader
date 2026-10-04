package com.m1racle.reader.epub

import com.m1racle.reader.data.anchor.DocumentAnchor
import org.junit.Assert.*
import org.junit.Test

class EpubRestoreSessionTest {
    private val original = DocumentAnchor.EpubText("one.xhtml", 5, 42, parserVersion = 1, quote = "original")
    private val nearby = original.copy(blockIndex = 0, charStart = 0, quote = "nearby")

    @Test fun `exact restore permits normal anchor saves`() {
        val session = EpubRestoreSession(EpubRestoreResult(EpubRestoreTier.Exact))
        assertFalse(session.preservesOriginalAnchor)
        assertEquals(nearby, session.anchorForSave(nearby))
    }

    @Test fun `non exact restores preserve both reading and bookmark anchors across automatic saves`() {
        listOf(EpubRestoreTier.Quote, EpubRestoreTier.QuotePrefix, EpubRestoreTier.ChapterStart,
            EpubRestoreTier.Progress).forEach { tier ->
            val session = EpubRestoreSession(EpubRestoreResult(tier))
            var readingAnchor = original
            var bookmarkAnchor = original
            repeat(3) {
                // A reflow only supplies a new candidate, never a user-navigation event.
                val candidate = nearby.copy(charStart = it)
                session.anchorForSave(candidate)?.let { readingAnchor = it; bookmarkAnchor = it }
            }
            assertTrue(session.preservesOriginalAnchor)
            assertEquals(original, readingAnchor)
            assertEquals(original, bookmarkAnchor)
            session.onUserNavigation()
            assertFalse(session.preservesOriginalAnchor)
            assertEquals(nearby, session.anchorForSave(nearby))
        }
    }

    @Test fun `first open and legacy migration can create anchors`() {
        listOf(EpubRestoreTier.BookStart, EpubRestoreTier.LegacyPosition).forEach {
            assertEquals(nearby, EpubRestoreSession(EpubRestoreResult(it)).anchorForSave(nearby))
        }
    }

    @Test fun `fallback notice is shown once per opening and prefix matches stay silent`() {
        EpubRestoreTier.entries.forEach { tier ->
            val session = EpubRestoreSession(EpubRestoreResult(tier))
            val fallback = tier == EpubRestoreTier.ChapterStart || tier == EpubRestoreTier.Progress
            assertEquals(fallback, session.takeDegradedNotice())
            assertFalse(session.takeDegradedNotice())
            session.onUserNavigation()
            assertFalse(session.takeDegradedNotice())
            assertEquals(fallback, EpubRestoreSession(EpubRestoreResult(tier)).takeDegradedNotice())
        }
    }
}
