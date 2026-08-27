package com.markleaf.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * H2.1 Reading Library —— 书签与收藏（文档中性定位）。
 *
 * 权威收口（H2_1_READING_MODEL_AUTHORITY_REPORT.md）：
 * - 阅读位置/进度的唯一事实源是 documents 表的阅读字段族
 *   （lastOpenedAt / lastScrollIndex / lastScrollOffset / readingProgress /
 *   PDF viewport session），由 DocumentRepository 保存路径单点写入；
 *   曾短暂存在的第二阅读状态投影表已在 v8→v9 删除，禁止重建。
 *
 * PRD 边界：产品范围是 Markdown + PDF；本定位模型不绑定 PDF 分页语义，
 * 坐标系由 [BookmarkLocationKind] 显式声明，未来任何新文档类型以新增枚举值
 * （加表列不动行）扩展。
 */

/** 书签定位坐标系（存储为 name 字符串，新增值 = 纯增量演进）。 */
enum class BookmarkLocationKind {
    /** 分页坐标系：location = 0-based 页索引（PDF）。 */
    PAGE_INDEX,

    /** 流式坐标系：location = 块/滚动索引（Markdown）。 */
    SCROLL_POSITION,
}

/**
 * Hub 书签行投影：bookmark 行 + 所属文档显示名（JOIN documents）。
 * 仅作查询投影，不是第二事实源——写入仍只经 BookmarkEntity。
 */
data class BookmarkWithDocument(
    val id: Long,
    val documentId: String,
    val locationKind: String,
    val location: Int,
    val offset: Long?,
    val title: String?,
    val note: String?,
    val createdAt: Long,
    val documentName: String,
) {
    val kind: BookmarkLocationKind
        get() = BookmarkLocationKind.valueOf(locationKind)
}

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["documentId"]),
    ],
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: String,
    /** 定位坐标系声明（见 [BookmarkLocationKind]），不默认——写入方必须显式选择。 */
    val locationKind: String,
    /** 主定位值：PAGE_INDEX=0-based 页索引；SCROLL_POSITION=块/滚动索引。 */
    val location: Int,
    /** 可选次级精定位（如字符偏移）。 */
    val offset: Long? = null,
    val title: String? = null,
    val note: String? = null,
    val createdAt: Long,
) {
    val kind: BookmarkLocationKind
        get() = BookmarkLocationKind.valueOf(locationKind)
}

@Entity(
    tableName = "favorite_documents",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["createdAt"]),
    ],
)
data class FavoriteDocumentEntity(
    /** documents.id。 */
    @PrimaryKey val documentId: String,
    val createdAt: Long,
)
