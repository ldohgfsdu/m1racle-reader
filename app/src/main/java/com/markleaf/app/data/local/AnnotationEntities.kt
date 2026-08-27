package com.markleaf.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * R23 Annotation Foundation —— 标注数据模型（schema/repository/测试先行，
 * 本轮不接任何 UI）。定位语义与书签一致：locationKind 显式声明坐标系，
 * 文档中性、不绑定 PDF 分页。
 *
 * anchorStart/anchorEnd 为该坐标系内的可选次级精定位（如字符区间），
 * excerpt 保存被标注原文快照——三者均可空，保证最简创建路径。
 */

/** 文本高亮。 */
@Entity(
    tableName = "highlights",
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
        Index(value = ["documentId", "locationKind", "location"]),
    ],
)
data class HighlightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: String,
    val locationKind: String,
    val location: Int,
    /**
     * R24：规范化锚点原文（AnchorCodec 编码）。存在时为恢复的权威来源；
     * locationKind/location 是写时派生的索引投影列。
     */
    val anchor: String? = null,
    val anchorStart: Int? = null,
    val anchorEnd: Int? = null,
    val excerpt: String? = null,
    val createdAt: Long,
)

/** 独立批注（可无选区，挂在位置上）。 */
@Entity(
    tableName = "notes",
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
        Index(value = ["documentId", "locationKind", "location"]),
    ],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: String,
    val locationKind: String,
    val location: Int,
    /** R24：同 HighlightEntity.anchor。 */
    val anchor: String? = null,
    val anchorStart: Int? = null,
    val anchorEnd: Int? = null,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
)
