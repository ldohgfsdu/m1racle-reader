package com.m1racle.reader.ui.reader

import com.m1racle.reader.data.local.BookmarkEntity
import com.m1racle.reader.data.local.BookmarkLocationKind
import com.m1racle.reader.data.repository.ReadingRepository

/**
 * R23 阅读器书签切换中介：**UI → 本控制器 → ReadingRepository → DAO** 是唯一
 * 写入路径（UI 禁止直访数据库，由源码钉测试锁定）。
 *
 * 每个阅读器实例持有一个：PDF 传 [BookmarkLocationKind.PAGE_INDEX]，
 * Markdown 传 SCROLL_POSITION；location 由各阅读器按自身坐标语义提供
 * （PDF=0-based 页索引；Markdown=LazyColumn 绝对 item index）。
 * 纯 JVM 可测（依赖仅 ReadingRepository 接口面 + 数据类）。
 */
class ReaderBookmarkController(
    private val readingRepository: ReadingRepository,
    private val documentId: String,
    private val locationKind: BookmarkLocationKind,
) {

    sealed class ToggleResult {
        /** 新增成功。 */
        data object Added : ToggleResult()

        /** 已存在并移除。 */
        data object Removed : ToggleResult()
    }

    /** 该文档在本坐标系下的全部已标注位置（用于图标/菜单态）。 */
    suspend fun bookmarkedLocations(): Set<Int> =
        readingRepository.bookmarkedLocations(documentId, locationKind.name)

    fun isBookmarked(locations: Set<Int>, location: Int): Boolean =
        locations.contains(location)

    /**
     * 切换当前位置书签：已存在 → 删除（[ToggleResult.Removed]）；
     * 不存在 → 创建（[ToggleResult.Added]）。
     */
    suspend fun toggle(
        location: Int,
        nowMs: Long = System.currentTimeMillis(),
        title: String? = null,
        note: String? = null,
        offset: Long? = null,
        target: com.m1racle.reader.data.anchor.BookmarkTarget? = null,
    ): ToggleResult {
        val existing = readingRepository.findBookmarkAt(
            documentId = documentId,
            locationKind = locationKind.name,
            location = location,
        )
        return if (existing != null) {
            readingRepository.removeBookmarkAt(documentId, locationKind.name, location)
            ToggleResult.Removed
        } else {
            readingRepository.createBookmark(
                BookmarkEntity(
                    documentId = documentId,
                    locationKind = locationKind.name,
                    location = location,
                    offset = offset,
                    anchor = target?.encode(),
                    title = title,
                    note = note,
                    createdAt = nowMs,
                ),
            )
            ToggleResult.Added
        }
    }
}
