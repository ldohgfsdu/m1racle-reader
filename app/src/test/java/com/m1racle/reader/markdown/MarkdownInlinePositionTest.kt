package com.m1racle.reader.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownInlinePositionTest {
    @Test fun blockPositionsFollowOriginalLinesAcrossCommentsAndMixedEndings() {
        val source = "# Title\r\n<!-- hidden\rcomment -->\n\nRepeated\n\nRepeated\r\n\r\n```kotlin\rval x = 1\r```\n\n> quote\n> next\n\n- one\n- two\n\n| A | B |\n| --- | --- |\n| C | D |"
        val parsed = MarkdownParser.parse(source)
        assertEquals(listOf(1, 5, 7, 9, 13, 16, 19), parsed.blockStartLines)
        assertEquals(parsed.blocks.size, parsed.blockStartLines.size)
        assertEquals(parsed.blocks[1], parsed.blocks[2])
    }

    @Test fun invisibleAnchorsAndBlankLinesDoNotBecomeEditTargets() {
        val result = MarkdownParser.parse("\n<a id=\"target\"></a>\n\n## Section\n\nText\n")
        assertEquals(listOf(4, 6), result.blockStartLines)
        assertEquals(0, result.toc.single().blockIndex)
    }
}
