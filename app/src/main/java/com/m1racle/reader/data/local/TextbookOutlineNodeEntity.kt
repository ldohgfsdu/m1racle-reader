package com.m1racle.reader.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 原教材目录节点（TextbookOutlineNode）— 绑定 PDF 页范围，不修改 PDF 文件。
 *
 * 层级：part / chapter / section / subsection / item / appendix
 * [source] 区分手工编目与 PDF 内置目录导入。
 */
@Entity(
    tableName = "textbook_outline_nodes",
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
        Index(value = ["documentId", "parentId"]),
        Index(value = ["documentId", "startPageIndex", "endPageIndex"]),
        Index(value = ["documentId", "importKey"], unique = false),
    ],
)
data class TextbookOutlineNodeEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    /** Null = root-level node under the document. */
    val parentId: String?,
    /** [TextbookOutlineNodeType] name. */
    val type: String,
    val title: String,
    /** Inclusive 0-based PDF page indices. */
    val startPageIndex: Int,
    val endPageIndex: Int,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    /**
     * [OutlineNodeSource] name: MANUAL or PDF_EMBEDDED.
     */
    val source: String = OutlineNodeSource.MANUAL.name,
    /**
     * Stable key for idempotent embedded import / merge dedupe.
     * Null for pure manual nodes without import origin.
     */
    val importKey: String? = null,
) {
    val nodeType: TextbookOutlineNodeType
        get() = TextbookOutlineNodeType.fromStorage(type)

    val nodeSource: OutlineNodeSource
        get() = OutlineNodeSource.fromStorage(source)

    fun containsPage(pageIndex: Int): Boolean =
        pageIndex in startPageIndex..endPageIndex
}
