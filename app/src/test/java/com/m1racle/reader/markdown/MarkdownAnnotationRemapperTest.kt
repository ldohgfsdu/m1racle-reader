package com.m1racle.reader.markdown

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownAnnotationRemapperTest {

    @Test
    fun insertedBlocksShiftFollowingMatches() {
        val old = blocks("same", "target", "same", "tail")
        val new = blocks("same", "inserted", "target", "same", "tail")

        assertEquals(
            mapOf(0 to 0, 1 to 2, 2 to 3, 3 to 4),
            MarkdownAnnotationRemapper.alignment(old, new),
        )
    }

    @Test
    fun replacementMovesDeletedBlockToNearbyEditedBlock() {
        val old = blocks("heading", "old target", "tail")
        val new = blocks("heading", "replacement", "tail")

        assertEquals(
            MarkdownAnnotationRemapper.Mapped(1, 0, 3),
            MarkdownAnnotationRemapper.remap(old, new, 1, 0, 3),
        )
    }

    @Test
    fun repeatedParagraphsUseAnOrderedMaximumAlignment() {
        val old = blocks("repeat", "repeat", "repeat")
        val new = blocks("repeat", "new", "repeat", "repeat")

        val alignment = MarkdownAnnotationRemapper.alignment(old, new)
        val entries = alignment.entries.sortedBy { it.key }

        assertEquals(3, alignment.size)
        assertTrue(entries.zipWithNext().all { (left, right) ->
            left.key < right.key && left.value < right.value
        })
    }

    @Test
    fun thousandsOfBlocksHaveLinearFrontierWithoutStackOverflow() {
        val old = (0 until 5_000).map { MarkdownBlock.Paragraph("block-$it") }
        val new = old.toMutableList().apply {
            add(2_500, MarkdownBlock.Paragraph("inserted"))
            this[4_000] = MarkdownBlock.Paragraph("changed")
        }

        val alignment = MarkdownAnnotationRemapper.alignment(old, new)

        assertEquals(4_999, alignment.size)
        assertEquals(2_501, alignment[2_500])
        assertEquals(4_001, alignment[4_000])
        assertNull(alignment[3_999])
    }

    @Test
    fun randomSmallInputsMatchDynamicProgrammingLcsAndStayMonotonic() {
        val random = Random(0x5eed)
        repeat(2_000) {
            val old = random.blocks(random.nextInt(0, 13))
            val new = random.blocks(random.nextInt(0, 13))
            val alignment = MarkdownAnnotationRemapper.alignment(old, new)
            val entries = alignment.entries.sortedBy { it.key }

            assertEquals(dpLcs(old, new), alignment.size)
            assertTrue(entries.all { (oldIndex, newIndex) ->
                oldIndex in old.indices && newIndex in new.indices && old[oldIndex] == new[newIndex]
            })
            assertTrue(entries.zipWithNext().all { (left, right) ->
                left.key < right.key && left.value < right.value
            })
        }
    }

    @Test
    fun fullyRewrittenDocumentReturnsNoFalseMatches() {
        val old = (0 until 2_000).map { MarkdownBlock.Paragraph("old-$it") }
        val new = (0 until 2_000).map { MarkdownBlock.Paragraph("new-$it") }

        assertTrue(MarkdownAnnotationRemapper.alignment(old, new).isEmpty())
    }

    @Test
    fun noNewBlocksDropsTheAnchor() {
        assertNull(MarkdownAnnotationRemapper.remap(blocks("old"), emptyList(), 0))
    }

    private fun blocks(vararg text: String): List<MarkdownBlock> =
        text.map(MarkdownBlock::Paragraph)

    private fun Random.blocks(size: Int): List<MarkdownBlock> =
        List(size) { MarkdownBlock.Paragraph("block-${nextInt(4)}") }

    /** Test oracle only: quadratic space is acceptable for the tiny random cases. */
    private fun dpLcs(old: List<MarkdownBlock>, new: List<MarkdownBlock>): Int {
        val lengths = Array(old.size + 1) { IntArray(new.size + 1) }
        for (oldIndex in old.indices.reversed()) {
            for (newIndex in new.indices.reversed()) {
                lengths[oldIndex][newIndex] = if (old[oldIndex] == new[newIndex]) {
                    lengths[oldIndex + 1][newIndex + 1] + 1
                } else {
                    maxOf(lengths[oldIndex + 1][newIndex], lengths[oldIndex][newIndex + 1])
                }
            }
        }
        return lengths[0][0]
    }
}
