package com.m1racle.reader.ui.annotation

import com.m1racle.reader.data.anchor.DocumentAnchor
import com.m1racle.reader.data.anchor.NormalizedRect

/**
 * R25 用户选择区域抽象（交互层瞬时概念，永不持久化）。
 *
 * PDF：手势像素矩形 + 捕获时刻页面像素尺寸——**在捕获点即完成归一化**，
 * 因此选择结果天然与缩放/设备密度无关（R24 缩放不变性的上游保证）。
 * Markdown：块索引 + 块内字符区间 [charStart, charEnd)。
 */
sealed class TextSelection {

    abstract val excerpt: String?

    /** 是否可转换为合法锚点（空选区无效）。 */
    abstract fun isValid(): Boolean

    /** 转换为存储锚点；非法选择返回 null。 */
    fun toAnchor(): DocumentAnchor? = when (this) {
        is PdfSelection -> {
            val n = normalizedOrNull() ?: return null
            DocumentAnchor.PdfPage(page = page, rect = n)
        }
        is MarkdownSelection ->
            if (isValid()) {
                DocumentAnchor.MarkdownBlock(
                    blockIndex = blockIndex,
                    charStart = charStart,
                    charEnd = charEnd,
                )
            } else {
                null
            }
    }

    data class PdfSelection(
        val page: Int,
        val leftPx: Float,
        val topPx: Float,
        val rightPx: Float,
        val bottomPx: Float,
        val pageWidthPx: Float,
        val pageHeightPx: Float,
        override val excerpt: String? = null,
    ) : TextSelection() {

        init {
            require(page >= 0)
            require(pageWidthPx > 0f && pageHeightPx > 0f)
        }

        override fun isValid(): Boolean =
            normalizedOrNull() != null && hasArea()

        private fun hasArea(): Boolean =
            (rightPx - leftPx) > 0f && (bottomPx - topPx) > 0f

        internal fun normalizedOrNull(): NormalizedRect? {
            if (!(leftPx >= 0f && topPx >= 0f && rightPx > leftPx && bottomPx > topPx)) return null
            return NormalizedRect.fromPixel(
                px = leftPx,
                py = topPx,
                pw = rightPx - leftPx,
                ph = bottomPx - topPx,
                pageW = pageWidthPx,
                pageH = pageHeightPx,
            )
        }
    }

    data class MarkdownSelection(
        val blockIndex: Int,
        val charStart: Int,
        val charEnd: Int,
        override val excerpt: String? = null,
    ) : TextSelection() {

        init {
            require(blockIndex >= 0)
        }

        /** 半开区间：charEnd 必须大于 charStart 才构成有效选区。 */
        override fun isValid(): Boolean = charEnd > charStart
    }
}
