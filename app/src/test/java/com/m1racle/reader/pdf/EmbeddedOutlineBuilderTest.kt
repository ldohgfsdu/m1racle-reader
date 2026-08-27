package com.m1racle.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedOutlineBuilderTest {

    private fun e(
        order: Int,
        parent: Int,
        page: Int,
        title: String,
        depth: Int = if (parent < 0) 0 else 1,
    ) = EmbeddedOutlineEntry(
        title = title,
        pageIndex = page,
        depth = depth,
        order = order,
        parentOrder = parent,
    )

    @Test
    fun inferEndPages_usesNextSibling() {
        val entries = listOf(
            e(0, -1, 0, "第一篇", 0),
            e(1, 0, 5, "第一章", 1),
            e(2, 0, 20, "第二章", 1),
            e(3, -1, 50, "第二篇", 0),
        )
        val drafts = EmbeddedOutlineBuilder.inferEndPages(entries, pageCount = 100)
        assertEquals(4, drafts.size)
        // 第一章 ends before 第二章
        assertEquals(19, drafts.first { it.order == 1 }.endPageIndex)
        // 第二章 ends before 第二篇
        assertEquals(49, drafts.first { it.order == 2 }.endPageIndex)
        // last root extends to end
        assertEquals(99, drafts.first { it.order == 3 }.endPageIndex)
        // first root ends before next root
        assertEquals(49, drafts.first { it.order == 0 }.endPageIndex)
    }

    @Test
    fun inferEndPages_childUsesParentBoundary() {
        val entries = listOf(
            e(0, -1, 0, "章", 0),
            e(1, 0, 2, "节A", 1),
            e(2, 0, 10, "节B", 1),
        )
        val drafts = EmbeddedOutlineBuilder.inferEndPages(entries, 30)
        assertEquals(9, drafts.first { it.order == 1 }.endPageIndex)
        assertEquals(29, drafts.first { it.order == 2 }.endPageIndex)
    }

    @Test
    fun typeMapper_appendixAndDepth() {
        assertEquals(
            com.m1racle.reader.data.local.TextbookOutlineNodeType.APPENDIX,
            EmbeddedOutlineTypeMapper.map(0, "附录 A"),
        )
        assertEquals(
            com.m1racle.reader.data.local.TextbookOutlineNodeType.CHAPTER,
            EmbeddedOutlineTypeMapper.map(1, "第三章 慢性阻塞性肺疾病"),
        )
    }

    @Test
    fun importKey_stable() {
        val k1 = EmbeddedOutlineKeys.importKey(1, 3, 0, "第三章", 42)
        val k2 = EmbeddedOutlineKeys.importKey(1, 3, 0, "第三章", 42)
        assertEquals(k1, k2)
        assertTrue(k1.startsWith("PDF_EMBEDDED|"))
    }
}
