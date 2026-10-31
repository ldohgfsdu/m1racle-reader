package com.m1racle.reader.markdown

/**
 * 正文修订后把 Markdown 批注的块序号映射到新修订。
 *
 * 先按块内容做 LCS 对齐：未改动的块精确对应；被改写的块落到前后两个已对齐块之间
 * 的同位置（夹在区间内）；整块删除时落到最近的块。非精确对应时块内字符区间按新块长度夹紧。
 */
object MarkdownAnnotationRemapper {

    data class Mapped(val blockIndex: Int, val charStart: Int?, val charEnd: Int?)

    fun remap(
        oldBlocks: List<MarkdownBlock>,
        newBlocks: List<MarkdownBlock>,
        blockIndex: Int,
        charStart: Int? = null,
        charEnd: Int? = null,
    ): Mapped? {
        if (newBlocks.isEmpty()) return null
        val map = alignment(oldBlocks, newBlocks)
        val exact = map[blockIndex]
        val target = exact ?: run {
            var prev = -1
            var prevOld = -1
            for (i in blockIndex - 1 downTo 0) { val m = map[i]; if (m != null) { prev = m; prevOld = i; break } }
            var next = newBlocks.size
            for (i in blockIndex + 1 until oldBlocks.size) { val m = map[i]; if (m != null) { next = m; break } }
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

    /** old index -> new index for blocks with identical content (LCS). */
    fun alignment(oldBlocks: List<MarkdownBlock>, newBlocks: List<MarkdownBlock>): Map<Int, Int> {
        val n = oldBlocks.size
        val m = newBlocks.size
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) for (j in m - 1 downTo 0) {
            dp[i][j] = if (oldBlocks[i] == newBlocks[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
        }
        val out = HashMap<Int, Int>()
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                oldBlocks[i] == newBlocks[j] -> { out[i] = j; i++; j++ }
                dp[i + 1][j] >= dp[i][j + 1] -> i++
                else -> j++
            }
        }
        return out
    }

    private fun plainLength(block: MarkdownBlock): Int = when (block) {
        is MarkdownBlock.Paragraph -> block.text.length
        is MarkdownBlock.Heading -> block.text.length
        is MarkdownBlock.Quote -> block.text.length
        is MarkdownBlock.CodeBlock -> block.code.length
        else -> Int.MAX_VALUE
    }
}