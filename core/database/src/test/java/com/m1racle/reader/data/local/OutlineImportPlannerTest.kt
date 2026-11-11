package com.m1racle.reader.data.local

import com.m1racle.reader.core.model.OutlineImportDraft
import com.m1racle.reader.core.model.OutlineImportRules
import com.m1racle.reader.core.model.OutlineNodeSource
import com.m1racle.reader.core.model.TextbookOutlineNodeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutlineImportPlannerTest {

    private fun draft(
        order: Int,
        parent: Int,
        page: Int,
        title: String,
        end: Int = page + 5,
        depth: Int = if (parent < 0) 0 else 1,
    ) = OutlineImportDraft(
        title = title,
        pageIndex = page,
        endPageIndex = end,
        depth = depth,
        order = order,
        parentOrder = parent,
        type = TextbookOutlineNodeType.CHAPTER,
        importKey = "neutral|$depth|$order|$parent|$page|$title",
    )

    @Test
    fun replace_buildsAllWithParents() {
        val drafts = listOf(
            draft(0, -1, 0, "篇", depth = 0),
            draft(1, 0, 2, "章", depth = 1),
        )
        val result = OutlineImportPlanner.buildReplace("doc", drafts)
        assertEquals(2, result.inserted)
        val byOrder = result.nodes.associateBy { it.sortOrder }
        assertEquals(null, byOrder[0]!!.parentId)
        assertEquals(byOrder[0]!!.id, byOrder[1]!!.parentId)
        assertTrue(result.nodes.all { it.source == OutlineNodeSource.PDF_EMBEDDED.name })
        assertTrue(result.nodes.all { it.importKey != null })
    }

    @Test
    fun merge_skipsExistingImportKeys() {
        val drafts = listOf(
            draft(0, -1, 0, "A"),
            draft(1, 0, 5, "B"),
        )
        val existingKey = drafts[0].importKey
        val existing = listOf(
            TextbookOutlineNodeEntity(
                id = "x",
                documentId = "doc",
                parentId = null,
                type = "PART",
                title = "A",
                startPageIndex = 0,
                endPageIndex = 4,
                sortOrder = 0,
                createdAt = 0,
                updatedAt = 0,
                source = OutlineNodeSource.PDF_EMBEDDED.name,
                importKey = existingKey,
            ),
        )
        val result = OutlineImportPlanner.buildMerge("doc", drafts, existing)
        assertEquals(1, result.inserted)
        assertEquals(1, result.skipped)
        assertEquals("B", result.nodes.single().title)
        // B parent resolved to existing A id
        assertEquals("x", result.nodes.single().parentId)
    }

    @Test
    fun isAlreadyImported_trueWhenAllKeysPresent() {
        val drafts = listOf(draft(0, -1, 0, "A"), draft(1, 0, 1, "B"))
        val existing = drafts.map {
            TextbookOutlineNodeEntity(
                id = it.importKey,
                documentId = "d",
                parentId = null,
                type = "CHAPTER",
                title = it.title,
                startPageIndex = it.pageIndex,
                endPageIndex = it.endPageIndex,
                sortOrder = it.order,
                createdAt = 0,
                updatedAt = 0,
                source = OutlineNodeSource.PDF_EMBEDDED.name,
                importKey = it.importKey,
            )
        }
        assertTrue(OutlineImportRules.isAlreadyImported(drafts, existing.map { it.importKey }))
        assertFalse(
            OutlineImportRules.isAlreadyImported(
                drafts,
                existing.take(1).map { it.importKey },
            ),
        )
    }

    @Test
    fun merge_noDuplicatesOnSecondCall() {
        val drafts = listOf(draft(0, -1, 0, "Only"))
        val first = OutlineImportPlanner.buildMerge("doc", drafts, emptyList())
        assertEquals(1, first.inserted)
        val second = OutlineImportPlanner.buildMerge("doc", drafts, first.nodes)
        assertEquals(0, second.inserted)
        assertEquals(1, second.skipped)
    }
}
