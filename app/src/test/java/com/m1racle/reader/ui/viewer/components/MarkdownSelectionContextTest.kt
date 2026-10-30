package com.m1racle.reader.ui.viewer.components

import com.m1racle.reader.ui.annotation.TextSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkdownSelectionContextTest {

    @Test
    fun longPressOffsetSelectsOneCharacterAfterImmediateRelease() {
        val range = markdownSelectionRange(
            text = "逐字选择",
            anchorOffset = 2,
            dragOffset = 2,
        )

        assertEquals(2, range.first)
        assertEquals(2, range.last)
    }

    @Test
    fun reverseDragKeepsBothEndpointsAndNeverEmitsEmptyRange() {
        val range = markdownSelectionRange(
            text = "拖到反向",
            anchorOffset = 3,
            dragOffset = 1,
        )

        assertEquals(1, range.first)
        assertEquals(3, range.last)
    }

    @Test
    fun surrogatePairIsSelectedAsOneUtf16Range() {
        val range = markdownSelectionRange(
            text = "A🌟B",
            anchorOffset = 1,
            dragOffset = 2,
        )

        assertEquals(1, range.first)
        assertEquals(2, range.last)
        assertEquals("🌟", "A🌟B".substring(range.first, range.last + 1))
    }

    @Test
    fun listAndTableCellOffsetsMapBackToCanonicalBlockText() {
        val context = MarkdownSelectionContext(
            block = 9,
            plainText = "First\nSecond",
            highlights = emptyList(),
            onHighlight = null,
        )

        val selection = context.selectionFor(
            range = 0..2,
            inlineOffset = 6,
        )

        assertEquals(
            TextSelection.MarkdownSelection(
                blockIndex = 9,
                charStart = 6,
                charEnd = 9,
                excerpt = "Sec",
            ),
            selection,
        )
        assertNull(context.selectionFor(0 until 0, inlineOffset = 6))
    }

    @Test
    fun sessionCanMoveEitherEndpointAndClearTheOwner() {
        val session = MarkdownSelectionSession()
        val owner = MarkdownSelectionOwner(block = 4, inlineOffset = 8)

        session.begin(owner, offset = 2)
        session.moveFocus(owner, offset = 5)
        session.moveAnchor(owner, offset = 1)

        assertEquals(
            MarkdownSelectionEndpoints(owner = owner, anchor = 1, focus = 5),
            session.endpoints,
        )
        session.clear()
        assertNull(session.endpoints)
    }
}
