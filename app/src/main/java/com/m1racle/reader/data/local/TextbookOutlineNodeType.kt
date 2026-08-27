package com.m1racle.reader.data.local

/**
 * 教材结构节点类型（不是知识点树）。
 * Stored as TEXT in Room.
 */
enum class TextbookOutlineNodeType {
    /** 篇 */
    PART,
    /** 章 */
    CHAPTER,
    /** 节 */
    SECTION,
    /** 小节 */
    SUBSECTION,
    /** 条目 */
    ITEM,
    /** 附录 */
    APPENDIX,
    ;

    fun displayLabelZh(): String = when (this) {
        PART -> "篇"
        CHAPTER -> "章"
        SECTION -> "节"
        SUBSECTION -> "小节"
        ITEM -> "条目"
        APPENDIX -> "附录"
    }

    companion object {
        fun fromStorage(value: String?): TextbookOutlineNodeType {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: SECTION
        }
    }
}
