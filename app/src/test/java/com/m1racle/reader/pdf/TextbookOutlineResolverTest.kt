package com.m1racle.reader.pdf

import com.m1racle.reader.data.local.TextbookOutlineNodeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextbookOutlineResolverTest {

    private fun node(
        id: String,
        parentId: String?,
        start: Int,
        end: Int,
        title: String = id,
        sort: Int = 0,
    ) = TextbookOutlineNodeEntity(
        id = id,
        documentId = "doc",
        parentId = parentId,
        type = "SECTION",
        title = title,
        startPageIndex = start,
        endPageIndex = end,
        sortOrder = sort,
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun deepestContaining_prefersNested() {
        val nodes = listOf(
            node("ch", null, 0, 99, "章"),
            node("sec", "ch", 10, 20, "节"),
            node("sub", "sec", 12, 15, "小节"),
        )
        val deep = TextbookOutlineResolver.deepestContaining(13, nodes)
        assertEquals("sub", deep?.id)
        // Page 50 only covered by chapter
        assertEquals("ch", TextbookOutlineResolver.deepestContaining(50, nodes)?.id)
        // Outside all ranges
        assertNull(TextbookOutlineResolver.deepestContaining(200, nodes))
    }

    @Test
    fun pathLabel_buildsBreadcrumb() {
        val nodes = listOf(
            node("a", null, 0, 10, "第一篇"),
            node("b", "a", 0, 10, "第一章"),
            node("c", "b", 2, 5, "1.1"),
        )
        val deep = TextbookOutlineResolver.deepestContaining(3, nodes)!!
        val path = TextbookOutlineResolver.pathToRoot(deep, nodes)
        assertEquals("第一篇 / 第一章 / 1.1", TextbookOutlineResolver.pathLabel(path))
    }

    @Test
    fun flattenTree_ordersChildren() {
        val nodes = listOf(
            node("r", null, 0, 10, "根", sort = 1),
            node("c2", "r", 5, 6, "后", sort = 2),
            node("c1", "r", 1, 2, "前", sort = 1),
        )
        val flat = TextbookOutlineResolver.flattenTree(nodes)
        assertEquals(listOf("r", "c1", "c2"), flat.map { it.node.id })
        assertEquals(0, flat[0].depth)
        assertEquals(1, flat[1].depth)
    }

    @Test
    fun subtreeIds_includesDescendants() {
        val nodes = listOf(
            node("a", null, 0, 10),
            node("b", "a", 0, 5),
            node("c", "b", 0, 2),
            node("x", null, 20, 30),
        )
        val ids = TextbookOutlineResolver.subtreeIds("a", nodes)
        assertTrue(ids.containsAll(listOf("a", "b", "c")))
        assertTrue("x" !in ids)
    }

    @Test
    fun normalizeRange_swapsAndClamps() {
        assertEquals(2 to 5, TextbookOutlineResolver.normalizeRange(5, 2, 10))
        assertEquals(0 to 9, TextbookOutlineResolver.normalizeRange(-1, 99, 10))
    }

    @Test
    fun outlineScrollStartIndex_chapterAtTopThenSectionFollows() {
        val nodes = listOf(
            node("ch3", null, 0, 99, "第三章", sort = 1),
            node("s1", "ch3", 0, 9, "第一节", sort = 1),
            node("s2", "ch3", 10, 19, "第二节", sort = 2),
            node("s5", "ch3", 40, 49, "第五节", sort = 5),
        )
        val flat = TextbookOutlineResolver.flattenTree(nodes)
        val current = TextbookOutlineResolver.deepestContaining(42, nodes)!!
        assertEquals("s5", current.id)
        val start = TextbookOutlineResolver.outlineScrollStartIndex(flat, current.id)
        assertEquals("ch3", flat[start].node.id)
        assertEquals("s5", flat[TextbookOutlineResolver.outlineCurrentIndex(flat, current.id)].node.id)
        assertTrue(start < TextbookOutlineResolver.outlineCurrentIndex(flat, current.id))
    }

    @Test
    fun outlineScrollStartIndex_rootChapterIsFirstRow() {
        val nodes = listOf(
            node("ch1", null, 0, 20, "第一章"),
            node("ch2", null, 21, 40, "第二章"),
        )
        val flat = TextbookOutlineResolver.flattenTree(nodes)
        val current = TextbookOutlineResolver.deepestContaining(25, nodes)!!
        assertEquals("ch2", current.id)
        assertEquals(1, TextbookOutlineResolver.outlineScrollStartIndex(flat, current.id))
        assertEquals(
            null,
            TextbookOutlineResolver.outlineImmediateParentIndex(flat, 1),
        )
    }

    @Test
    fun outlineScrollStartIndex_threeLevelUsesImmediateParent() {
        val nodes = listOf(
            node("part", null, 0, 99, "第一篇"),
            node("ch", "part", 0, 50, "第三章"),
            node("sec", "ch", 20, 30, "第五节"),
        )
        val flat = TextbookOutlineResolver.flattenTree(nodes)
        val current = TextbookOutlineResolver.deepestContaining(22, nodes)!!
        val start = TextbookOutlineResolver.outlineScrollStartIndex(flat, current.id)
        assertEquals("sec", current.id)
        assertEquals("ch", flat[start].node.id)
    }

    @Test
    fun flattenTree_isCompleteCatalogAcrossChapters() {
        val nodes = listOf(
            node("ch1", null, 0, 19, "第一章", sort = 1),
            node("s1", "ch1", 0, 9, "第一节", sort = 1),
            node("ch3", null, 20, 99, "第三章", sort = 2),
            node("s1b", "ch3", 20, 29, "第一节", sort = 1),
            node("s5", "ch3", 40, 49, "第五节", sort = 5),
            node("ch4", null, 100, 120, "第四章", sort = 3),
        )
        val flat = TextbookOutlineResolver.flattenTree(nodes)
        assertEquals(
            listOf("ch1", "s1", "ch3", "s1b", "s5", "ch4"),
            flat.map { it.node.id },
        )
        val current = TextbookOutlineResolver.deepestContaining(42, nodes)!!
        assertEquals("s5", current.id)
        val start = TextbookOutlineResolver.outlineScrollStartIndex(flat, current.id)
        assertEquals("ch3", flat[start].node.id)
        assertTrue(flat.drop(start).map { it.node.id }.contains("ch4"))
        assertTrue(flat.take(start).map { it.node.id }.contains("ch1"))
    }
}
