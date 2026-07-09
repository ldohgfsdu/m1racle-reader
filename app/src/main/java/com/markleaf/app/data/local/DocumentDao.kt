package com.markleaf.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Query("SELECT * FROM documents ORDER BY lastOpenedAt DESC")
    fun observeRecentDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(document: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE documents SET lastOpenedAt = :time WHERE id = :id")
    suspend fun updateLastOpenedAt(id: String, time: Long)

    @Query(
        """
        UPDATE documents
        SET title = :title, headingCount = :headingCount
        WHERE id = :id
        """,
    )
    suspend fun updateParseMetadata(id: String, title: String?, headingCount: Int)

    @Query(
        """
        UPDATE documents
        SET lastScrollIndex = :lastScrollIndex,
            lastScrollOffset = :lastScrollOffset,
            readingProgress = :readingProgress
        WHERE id = :id
        """,
    )
    suspend fun updateReadingPosition(
        id: String,
        lastScrollIndex: Int,
        lastScrollOffset: Int,
        readingProgress: Float,
    )

    @Query("DELETE FROM documents")
    suspend fun deleteAll()
}
