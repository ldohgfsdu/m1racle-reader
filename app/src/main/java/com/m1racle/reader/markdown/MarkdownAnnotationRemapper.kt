package com.m1racle.reader.markdown

/**
 * 正文修订后把 Markdown 批注的块序号映射到新修订。
 *
 * 先按块内容做 Hirschberg LCS 对齐：未改动的块精确对应；被改写的块落到前后两个已对齐块之间
 * 的同位置（夹在区间内）；整块删除时落到最近的块。非精确对应时块内字符区间按新块长度夹紧。
 * 对齐只保存长度为新块数的两条计数行，避免为大型文档分配 O(n*m) 矩阵。
 */
object MarkdownAnnotationRemapper {

    data class Mapped(val blockIndex: Int, val charStart: Int?, val charEnd: Int?)

    /**
     * [alignment] may be computed once for a save and shared by every annotation.
     * Call [alignment] directly when remapping multiple rows.
     */
    fun remap(
        oldBlocks: List<MarkdownBlock>,
        newBlocks: List<MarkdownBlock>,
        blockIndex: Int,
        charStart: Int? = null,
        charEnd: Int? = null,
    ): Mapped? = remap(alignment(oldBlocks, newBlocks), oldBlocks, newBlocks, blockIndex, charStart, charEnd)

    fun remap(
        alignment: Map<Int, Int>,
        oldBlocks: List<MarkdownBlock>,
        newBlocks: List<MarkdownBlock>,
        blockIndex: Int,
        charStart: Int? = null,
        charEnd: Int? = null,
    ): Mapped? {
        if (newBlocks.isEmpty()) return null
        val exact = alignment[blockIndex]
        val target = exact ?: run {
            var prev = -1
            var prevOld = -1
            for (i in blockIndex - 1 downTo 0) { val m = alignment[i]; if (m != null) { prev = m; prevOld = i; break } }
            var next = newBlocks.size
            for (i in blockIndex + 1 until oldBlocks.size) { val m = alignment[i]; if (m != null) { next = m; break } }
            val guess = prev + (blockIndex - prevOld)
            val upper = maxOf(prev + 1, next - 1)
            guess.coerceIn(prev + 1, upper).coerceIn(0, newBlocks.size - 1)
        }
        if (exact != null) return Mapped(target, charStart, charEnd)
        val len = plainLength(newBlocks[target])
        val s = charStart?.coerceIn(0, len)
        val e = charEnd?.coerceIn(s ?: 0, len)
        return Mapped(target, s, e)
    }

    /** Exact LCS with Hirschberg divide-and-conquer: O(n*m) time, O(n+m) space. */
    fun alignment(oldBlocks: List<MarkdownBlock>, newBlocks: List<MarkdownBlock>): Map<Int, Int> {
        if (oldBlocks.isEmpty() || newBlocks.isEmpty()) return emptyMap()
        val oldContent = oldBlocks.toHashSet()
        if (newBlocks.none { it in oldContent }) return emptyMap()
        val out = HashMap<Int, Int>()

        // Prefix/suffix trimming makes ordinary local edits small. The row arrays
        // die before recursion, so recursive frames never retain a matrix.
        fun splitAt(oldStart: Int, oldEnd: Int, middle: Int, newStart: Int, newEnd: Int): Int {
            val forward = lcsRow(oldBlocks, oldStart, middle, newBlocks, newStart, newEnd, false)
            val backward = lcsRow(oldBlocks, middle, oldEnd, newBlocks, newStart, newEnd, true)
            val size = newEnd - newStart
            var split = 0
            var best = -1
            for (offset in 0..size) {
                val length = forward[offset] + backward[size - offset]
                if (length > best) {
                    best = length
                    split = offset
                }
            }
            return newStart + split
        }

        fun align(oldFrom: Int, oldUntil: Int, newFrom: Int, newUntil: Int) {
            var oldStart = oldFrom
            var newStart = newFrom
            var oldEnd = oldUntil
            var newEnd = newUntil
            while (oldStart < oldEnd && newStart < newEnd && oldBlocks[oldStart] == newBlocks[newStart]) {
                out[oldStart++] = newStart++
            }
            while (oldStart < oldEnd && newStart < newEnd && oldBlocks[oldEnd - 1] == newBlocks[newEnd - 1]) {
                out[--oldEnd] = --newEnd
            }
            if (oldStart == oldEnd || newStart == newEnd) return
            if (oldEnd - oldStart == 1) {
                for (index in newStart until newEnd) {
                    if (oldBlocks[oldStart] == newBlocks[index]) {
                        out[oldStart] = index
                        break
                    }
                }
                return
            }
            val middle = oldStart + (oldEnd - oldStart) / 2
            val split = splitAt(oldStart, oldEnd, middle, newStart, newEnd)
            align(oldStart, middle, newStart, split)
            align(middle, oldEnd, split, newEnd)
        }
        align(0, oldBlocks.size, 0, newBlocks.size)
        return out
    }

    /** LCS lengths against every prefix, in either reading direction. */
    private fun lcsRow(
        oldBlocks: List<MarkdownBlock>, oldStart: Int, oldEnd: Int,
        newBlocks: List<MarkdownBlock>, newStart: Int, newEnd: Int,
        reverse: Boolean,
    ): IntArray {
        val row = IntArray(newEnd - newStart + 1)
        for (oldOffset in 0 until oldEnd - oldStart) {
            val oldIndex = if (reverse) oldEnd - 1 - oldOffset else oldStart + oldOffset
            var diagonal = 0
            for (newOffset in 1 until row.size) {
                val above = row[newOffset]
                val newIndex = if (reverse) newEnd - newOffset else newStart + newOffset - 1
                row[newOffset] = if (oldBlocks[oldIndex] == newBlocks[newIndex]) diagonal + 1
                    else maxOf(above, row[newOffset - 1])
                diagonal = above
            }
        }
        return row
    }

    private fun plainLength(block: MarkdownBlock): Int = when (block) {
        is MarkdownBlock.Paragraph -> block.text.length
        is MarkdownBlock.Heading -> block.text.length
        is MarkdownBlock.Quote -> block.text.length
        is MarkdownBlock.CodeBlock -> block.code.length
        else -> Int.MAX_VALUE
    }
}
