package com.m1racle.reader.ui.viewer.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPositionCaptionTest {
    @Test fun `opening heading identical to title does not repeat in toolbar`() {
        assertEquals("0%", readerPositionCaption("把阅读还给内容", "把阅读还给内容", 0))
        assertEquals("20%", readerPositionCaption("  文档  ", "文档 ", 20))
    }

    @Test fun `different chapter still provides reading context`() {
        assertEquals("第二章 · 37%", readerPositionCaption("文档", "第二章", 37))
    }

    @Test fun `absent chapter leaves honest progress only`() {
        assertEquals("0%", readerPositionCaption("文档", null, 0))
        assertEquals("100%", readerPositionCaption("文档", "  ", 100))
    }
}
