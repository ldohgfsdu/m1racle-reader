package com.markleaf.app.ui.home

import com.markleaf.app.data.local.DocumentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Optimistic delete reflow — drives [HomeListUpdate] used by [HomeViewModel].
 */
class HomeListUpdateTest {

    private fun doc(id: String, opened: Long = 0L) = DocumentEntity(
        id = id,
        displayName = "$id.md",
        uri = "content://x/$id",
        mimeType = "text/markdown",
        fileSize = 100L,
        lastOpenedAt = opened,
        createdAt = opened,
        lastScrollIndex = 0,
        lastScrollOffset = 0,
        readingProgress = 0f,
        title = id,
        headingCount = 0,
        documentType = "MARKDOWN",
        pageCount = 0,
    )

    @Test
    fun `visibleDocuments drops pending deletes immediately`() {
        val source = listOf(doc("a", 3), doc("b", 2), doc("c", 1))
        val visible = HomeListUpdate.visibleDocuments(source, setOf("b"))
        assertEquals(listOf("a", "c"), visible.map { it.id })
        assertEquals(listOf("a", "b", "c"), source.map { it.id }) // source unchanged
    }

    @Test
    fun `visibleDocuments empty pending returns source`() {
        val source = listOf(doc("a"), doc("b"))
        assertEquals(source, HomeListUpdate.visibleDocuments(source, emptySet()))
    }

    @Test
    fun `reconcilePending keeps only ids still in source`() {
        val source = listOf(doc("a"), doc("c"))
        // b deleted from Room → drop from pending; a still present → keep if pending
        val pending = setOf("a", "b")
        val next = HomeListUpdate.reconcilePending(pending, source)
        assertEquals(setOf("a"), next)
        assertFalse(next.contains("b"))
    }

    @Test
    fun `reconcilePending empty stays empty`() {
        assertTrue(HomeListUpdate.reconcilePending(emptySet(), listOf(doc("a"))).isEmpty())
    }

    @Test
    fun `isVisible false for pending delete`() {
        assertFalse(HomeListUpdate.isVisible("x", setOf("x", "y")))
        assertTrue(HomeListUpdate.isVisible("z", setOf("x")))
    }

    @Test
    fun `delete entire list leaves empty visible - no ghost slots`() {
        val source = listOf(doc("only"))
        val after = HomeListUpdate.visibleDocuments(source, setOf("only"))
        assertTrue(after.isEmpty())
    }
}
