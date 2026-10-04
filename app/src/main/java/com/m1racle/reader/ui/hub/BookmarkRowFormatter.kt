package com.m1racle.reader.ui.hub

import com.m1racle.reader.data.local.BookmarkLocationKind
import java.util.Locale

/**
 * 书签行展示格式化（纯函数，宿主可测）。locationKind 决定语义：
 * PAGE_INDEX → 页码；SCROLL_POSITION → 滚动位置；未知 kind 走中性回退，
 * 不假设任何文档类型的分页概念。
 */
object BookmarkRowFormatter {

    fun locationLine(locationKind: String, location: Int): String = when (locationKind) {
        BookmarkLocationKind.PAGE_INDEX.name -> "页码 ${location + 1}"
        BookmarkLocationKind.SCROLL_POSITION.name -> "滚动位置 $location"
        BookmarkLocationKind.EPUB_TEXT.name -> "第 ${location + 1} 章"
        else -> "位置 $location"
    }

    /** 创建时间：同日显示 HH:mm，否则 yyyy-MM-dd。[nowMs] 注入保证可测。 */
    fun createdAtLine(createdAtMs: Long, nowMs: Long): String {
        val locale = Locale.getDefault()
        val created = java.util.Calendar.getInstance().apply { timeInMillis = createdAtMs }
        val now = java.util.Calendar.getInstance().apply { timeInMillis = nowMs }
        val sameDay = created.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR) &&
            created.get(java.util.Calendar.DAY_OF_YEAR) == now.get(java.util.Calendar.DAY_OF_YEAR)
        return if (sameDay) {
            String.format(
                locale,
                "%02d:%02d",
                created.get(java.util.Calendar.HOUR_OF_DAY),
                created.get(java.util.Calendar.MINUTE),
            )
        } else {
            String.format(
                locale,
                "%d-%02d-%02d",
                created.get(java.util.Calendar.YEAR),
                created.get(java.util.Calendar.MONTH) + 1,
                created.get(java.util.Calendar.DAY_OF_MONTH),
            )
        }
    }

    /** 主标题：书签 title 优先，缺省用文档名。 */
    fun headline(title: String?, documentName: String): String =
        title?.takeIf { it.isNotBlank() } ?: documentName
}
