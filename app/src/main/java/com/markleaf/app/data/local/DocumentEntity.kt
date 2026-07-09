package com.markleaf.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Recent-read document record (Room).
 * Deleting a row only removes the history entry — never the source file.
 */
@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val uri: String,
    val mimeType: String?,
    val fileSize: Long?,
    val lastOpenedAt: Long,
    val createdAt: Long,
    val lastScrollIndex: Int,
    val lastScrollOffset: Int,
    val readingProgress: Float,
    val title: String?,
    val headingCount: Int,
)
