package com.m1racle.reader.markdown

import org.junit.Assert.*
import org.junit.Test

class MarkdownSourceTest {
    @Test fun replacementPreservesUntouchedUnicodeAndLineEndings() {
        val source = "# 你好🌟\r\n\r\n第二段\r\n\r\n末段"
        val chunks = MarkdownSource.chunks(source)
        assertEquals(3, chunks.size)
        assertEquals("# 你好🌟\r\n", source.substring(chunks[0]))
        assertEquals("# 你好🌟\r\n\r\n替换\r\n\r\n末段", MarkdownSource.replace(source, chunks[1], "替换\r\n"))
    }
    @Test fun blankLinesInsideCodeRemainOneEditableChunk() {
        val source = "```kotlin\nval x = 1\n\nval y = 2\n```\n\n尾部"
        assertEquals(listOf("```kotlin\nval x = 1\n\nval y = 2\n```\n", "尾部"), MarkdownSource.chunks(source).map(source::substring))
    }
    @Test fun emptyAndWhitespaceOnlyHaveNoRanges() {
        assertTrue(MarkdownSource.chunks("").isEmpty())
        assertTrue(MarkdownSource.chunks(" \r\n\n").isEmpty())
    }
}
