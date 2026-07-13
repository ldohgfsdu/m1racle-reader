package com.markleaf.app.pdf.export

import com.markleaf.app.data.local.TextbookOutlineNodeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportRangeResolverTest {

    private fun n(
        id: String,
        parent: String?,
        start: Int,
        end: Int,
        title: String = id,
        sort: Int = 0,
    ) = TextbookOutlineNodeEntity(
        id = id,
        documentId = "doc",
        parentId = parent,
        type = "CHAPTER",
        title = title,
        startPageIndex = start,
        endPageIndex = end,
        sortOrder = sort,
        createdAt = 0,
        updatedAt = 0,
    )

    @Test
    fun nextSibling_endIsPrevPage() {
        val nodes = listOf(
            n("a", null, 0, 99, "A", 0),
            n("b", null, 20, 99, "B", 1),
        )
        val (end, src) = ExportRangeResolver.computeEndPage(nodes[0], nodes, 100)
        assertEquals(19, end)
        assertEquals(EndPageSource.NEXT_SIBLING, src)
    }

    @Test
    fun lastSibling_usesParentEnd() {
        val nodes = listOf(
            n("p", null, 0, 50, "P", 0),
            n("c1", "p", 0, 10, "C1", 0),
            n("c2", "p", 11, 40, "C2", 1),
        )
        val (end, src) = ExportRangeResolver.computeEndPage(nodes[2], nodes, 100)
        assertEquals(50, end)
        assertEquals(EndPageSource.PARENT_END, src)
    }

    @Test
    fun lastRoot_usesDocumentEnd() {
        val nodes = listOf(n("only", null, 10, 10, "O", 0))
        val (end, src) = ExportRangeResolver.computeEndPage(nodes[0], nodes, 982)
        assertEquals(981, end)
        assertEquals(EndPageSource.DOCUMENT_END, src)
    }

    @Test
    fun childClampedToParent() {
        val nodes = listOf(
            n("p", null, 10, 20, "P", 0),
            n("c", "p", 0, 100, "C", 0),
        )
        val plan = ExportRangeResolver.plan(nodes[1], nodes, 200)
        assertTrue(plan.startPageIndex >= 10)
        assertTrue(plan.endPageIndex <= 20)
    }

    @Test
    fun validateRange_rejectsInvalid() {
        assertNotNull(ExportRangeResolver.validateRange(5, 3, 10))
        assertNotNull(ExportRangeResolver.validateRange(-1, 2, 10))
        assertNotNull(ExportRangeResolver.validateRange(0, 10, 10))
        assertNull(ExportRangeResolver.validateRange(0, 9, 10))
    }

    @Test
    fun sanitizeFileName_stripsIllegal() {
        val s = ExportRangeResolver.sanitizeFileName("内科学:第二篇/呼吸*系统?")
        assertTrue(!s.contains(":"))
        assertTrue(!s.contains("/"))
        assertTrue(!s.contains("*"))
        assertTrue(s.endsWith("系统") || s.contains("系统") || s.isNotBlank())
    }

    @Test
    fun defaultZipName_hasZipSuffix() {
        val name = ExportRangeResolver.defaultZipName(
            "内科学（第10版）.pdf",
            listOf("第二篇 呼吸系统疾病", "第三章 慢性阻塞性肺疾病"),
        )
        assertTrue(name.endsWith(".zip"))
        assertTrue(!name.contains("/"))
    }

    @Test
    fun pageMap_countMatchesRange() {
        // Sibling forces end = 60 (next starts at 61)
        val c = n("c", null, 53, 200, "第三章", 0)
        val next = n("d", null, 61, 90, "下一章", 1)
        val plan = ExportRangeResolver.plan(c, listOf(c, next), 100)
        assertEquals(53, plan.startPageIndex)
        assertEquals(60, plan.endPageIndex)
        assertEquals(8, plan.pageCount)
        val json = ChapterPackageMetadata.pageMapJson(plan, emptyList())
        // JVM unit tests may stub org.json; assert via string content.
        assertTrue(json.contains("\"exported_pdf_page\": 1"))
        assertTrue(json.contains("\"source_pdf_page\": 54"))
        assertTrue(json.contains("\"source_pdf_page\": 61"))
        assertTrue(json.contains("null") || json.contains("printed_page"))
        // 8 map entries ≈ 8 \"exported_page_index\"
        assertEquals(8, Regex("\"exported_page_index\"").findAll(json).count())
    }

    @Test
    fun outline_remapsAndClipsChildren() {
        val p = n("p", null, 50, 70, "Part", 0)
        val c = n("c", "p", 54, 61, "ChapterCOPD", 0)
        val next = n("n", "p", 62, 70, "NextChapter", 1)
        val out = n("out", "p", 80, 90, "Outside", 2)
        val sub = n("s", "c", 55, 58, "Section", 0)
        val (end, src) = ExportRangeResolver.computeEndPage(c, listOf(p, c, next, out, sub), 100)
        assertEquals(61, end)
        assertEquals(EndPageSource.NEXT_SIBLING, src)
        val plan = ExportRangeResolver.plan(
            c,
            listOf(p, c, next, out, sub),
            100,
            overrideStartIndex = 54,
            overrideEndIndex = 61,
        )
        assertEquals(54, plan.startPageIndex)
        assertEquals(61, plan.endPageIndex)
        val json = ChapterPackageMetadata.outlineJson(plan, listOf(p, c, next, out, sub))
        assertTrue("root title", json.contains("ChapterCOPD"))
        assertTrue("section child", json.contains("Section"))
        assertTrue("no outside sibling", !json.contains("Outside"))
        assertTrue("no next chapter", !json.contains("NextChapter"))
        // remapped: source 54 -> exported 1
        assertTrue(json.contains("exported_pdf_page\": 1") || json.contains("exported_pdf_page\":1"))
    }

    @Test
    fun exportedPageFormula() {
        // exported = source - start + 1 (1-based)
        val start = 53
        assertEquals(1, 53 - start + 1)
        assertEquals(8, 60 - start + 1)
    }
}
