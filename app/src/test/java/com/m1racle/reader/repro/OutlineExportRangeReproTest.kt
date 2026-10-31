package com.m1racle.reader.repro

import com.m1racle.reader.data.local.TextbookOutlineNodeEntity
import com.m1racle.reader.pdf.export.EndPageSource
import com.m1racle.reader.pdf.export.ExportRangeResolver
import org.junit.Assert.assertEquals
import org.junit.Test

/** REPRO 20261101 H1/H2：目录排序与父章节范围导致章节导出范围错误。 */
class OutlineExportRangeReproTest {

    private fun n(id: String, parent: String?, start: Int, end: Int, sort: Int) = TextbookOutlineNodeEntity(
        id = id, documentId = "doc", parentId = parent, type = "CHAPTER", title = id,
        startPageIndex = start, endPageIndex = end, sortOrder = sort, createdAt = 0, updatedAt = 0,
    )

    @Test
    fun h1_siblingEndFollowsPageOrderNotStaleSortOrder() {
        // 编辑后 b 的 sortOrder 被重置为 0，排到 a 前面
        val a = n("a", null, 0, 0, 5)
        val b = n("b", null, 20, 20, 0)
        val (end, src) = ExportRangeResolver.computeEndPage(a, listOf(a, b), 100)
        assertEquals(19, end)
        assertEquals(EndPageSource.NEXT_SIBLING, src)
    }

    @Test
    fun h2_childOfSinglePageParentIsNotCollapsedToParentStart() {
        // 手工新建父章节默认 start=end=当前页
        val p = n("p", null, 10, 10, 0)
        val c = n("c", "p", 30, 30, 0)
        val plan = ExportRangeResolver.plan(c, listOf(p, c), 100)
        assertEquals(30, plan.startPageIndex)
        assertEquals(99, plan.endPageIndex)
    }

    @Test
    fun h2_userOverrideIsNotSilentlyRewritten() {
        val p = n("p", null, 10, 20, 0)
        val c = n("c", "p", 12, 15, 0)
        val plan = ExportRangeResolver.plan(c, listOf(p, c), 100, overrideStartIndex = 12, overrideEndIndex = 40)
        assertEquals(12, plan.startPageIndex)
        assertEquals(40, plan.endPageIndex)
        assertEquals(EndPageSource.USER_OVERRIDE, plan.endSource)
    }

    @Test
    fun h1_newNodeSortOrderAppendsAfterSiblings() {
        assertEquals(0, com.m1racle.reader.data.repository.outlineNextSortOrder(emptyList()))
        assertEquals(6, com.m1racle.reader.data.repository.outlineNextSortOrder(listOf(n("a", null, 0, 0, 5), n("b", null, 9, 9, 2))))
    }
}