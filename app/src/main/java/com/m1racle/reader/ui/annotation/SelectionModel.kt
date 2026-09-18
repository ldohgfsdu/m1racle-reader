package com.m1racle.reader.ui.annotation

import com.m1racle.reader.data.anchor.DocumentAnchor
import com.m1racle.reader.data.anchor.NormalizedRect

/**
 * R25 用户选择区域抽象（交互层瞬时概念，永不持久化）。
 *
 * PDF：字符命中得到的逐行归一化范围；同一列表用于预览、锚点和持久化。
 * Markdown：块索引 + 块内字符区间 [charStart, charEnd)。
 */
sealed class TextSelection {

    abstract val excerpt: String?

    /** 是否可转换为合法锚点（空选区无效）。 */
    abstract fun isValid(): Boolean

    /** 转换为存储锚点；非法选择返回 null。 */
    fun toAnchor(): DocumentAnchor? = when (this) {
        is PdfSelection -> if (isValid()) DocumentAnchor.PdfPage(page, rects) else null
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
        val rects: List<NormalizedRect>,
        override val excerpt: String? = null,
    ) : TextSelection() {
        init { require(page >= 0) }
        override fun isValid(): Boolean = rects.isNotEmpty() && rects.all {
            it.x.isFinite() && it.y.isFinite() && it.width.isFinite() && it.height.isFinite() &&
                it.x >= 0f && it.y >= 0f && it.width > 0f && it.height > 0f &&
                it.x + it.width <= 1.00001f && it.y + it.height <= 1.00001f
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
