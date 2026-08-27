package com.m1racle.reader.ui.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Per-kind preferred/max heights for foreground ChildCards (shipped
 * [AdvancedChildLayoutSpec]).
 */
class AdvancedChildLayoutSpecTest {

    @Test
    fun `each advanced kind has a distinct preferred height band`() {
        val page = AdvancedChildLayoutSpec.preferredHeightDp(ReaderMoreState.CHILD_PAGE_MAPPING)
        val toc = AdvancedChildLayoutSpec.preferredHeightDp(ReaderMoreState.CHILD_TOC)
        val thumbs = AdvancedChildLayoutSpec.preferredHeightDp(ReaderMoreState.CHILD_THUMBNAILS)
        val export = AdvancedChildLayoutSpec.preferredHeightDp(ReaderMoreState.CHILD_EXPORT)

        // PageMapping is wrap-ish / compact — no large empty slab.
        assertTrue("PageMapping preferred compact", page < toc)
        assertTrue("PageMapping preferred compact vs thumbs", page < thumbs)
        assertTrue("PageMapping preferred compact vs export", page < export)
        // TOC / Thumbs / Export need taller cards for list/grid/fields.
        assertTrue(toc >= AdvancedChildLayoutSpec.HeaderHeightDp + 280f)
        assertTrue(thumbs >= AdvancedChildLayoutSpec.HeaderHeightDp + 280f)
        assertTrue(export >= AdvancedChildLayoutSpec.HeaderHeightDp + 280f)
        // PageMapping includes header + field + full save action room.
        assertTrue(page >= AdvancedChildLayoutSpec.HeaderHeightDp + 160f)
        assertTrue("PageMapping stays wrap-ish", page <= AdvancedChildLayoutSpec.HeaderHeightDp + 190f)
    }

    @Test
    fun `toc and thumbs lists own remaining body height`() {
        val card = listOf(
            java.io.File("app/src/main/java/com/m1racle/reader/ui/pdf/ReaderAdvancedCard.kt"),
            java.io.File("src/main/java/com/m1racle/reader/ui/pdf/ReaderAdvancedCard.kt"),
            java.io.File("/root/code/m1racle-reader/app/src/main/java/com/m1racle/reader/ui/pdf/ReaderAdvancedCard.kt"),
        ).first { it.isFile }.readText()
        val pop = listOf(
            java.io.File("app/src/main/java/com/m1racle/reader/ui/pdf/AnchoredNestedPopover.kt"),
            java.io.File("src/main/java/com/m1racle/reader/ui/pdf/AnchoredNestedPopover.kt"),
            java.io.File("/root/code/m1racle-reader/app/src/main/java/com/m1racle/reader/ui/pdf/AnchoredNestedPopover.kt"),
        ).first { it.isFile }.readText()
        assertTrue(card.contains(".weight(1f)"))
        assertTrue(pop.contains("bodyMaxHeightDp = bodyMaxHeightDp"))
        assertFalse(pop.contains("bodyMaxHeightDp - 80"))
        assertFalse(pop.contains("bodyMaxHeightDp - 28"))
    }

    @Test
    fun `maxHeight subtracts status nav and margin never into gesture area`() {
        val max = AdvancedChildLayoutSpec.maxHeightDp(
            windowHeightDp = 800f,
            statusInsetDp = 48f,
            navInsetDp = 48f,
            marginDp = 20f,
        )
        assertEquals(800f - 48f - 48f - 20f, max, 0.01f)
        assertTrue(max < 800f - 48f)
    }

    @Test
    fun `clampedHeight never exceeds max and keeps header floor`() {
        val max = 300f
        val page = AdvancedChildLayoutSpec.clampedHeightDp(ReaderMoreState.CHILD_PAGE_MAPPING, max)
        val toc = AdvancedChildLayoutSpec.clampedHeightDp(ReaderMoreState.CHILD_TOC, max)
        assertTrue(page <= max)
        assertTrue(toc <= max)
        assertTrue(page >= AdvancedChildLayoutSpec.HeaderHeightDp + 80f)
        assertTrue(toc >= AdvancedChildLayoutSpec.HeaderHeightDp + 80f)
    }

    @Test
    fun `bodyMaxHeight subtracts header and divider`() {
        val body = AdvancedChildLayoutSpec.bodyMaxHeightDp(400f)
        assertEquals(400f - AdvancedChildLayoutSpec.HeaderHeightDp - 1f, body, 0.01f)
    }

    @Test
    fun `nestedMinContentHeightDp matches layout-spec preferred`() {
        ReaderMoreState.entries.filter { it.isChild() }.forEach { state ->
            val fromSpec = AdvancedChildLayoutSpec.preferredHeightDp(state)
            val fromPopover = nestedMinContentHeightDp(state).value
            assertEquals(
                "nestedMinContentHeightDp must track AdvancedChildLayoutSpec for $state",
                fromSpec,
                fromPopover,
                0.01f,
            )
        }
    }
}
