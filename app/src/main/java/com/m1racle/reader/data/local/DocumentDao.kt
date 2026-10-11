package com.m1racle.reader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Query("SELECT * FROM documents ORDER BY lastOpenedAt DESC")
    fun observeRecentDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE contentSha256 = :contentSha256 LIMIT 1")
    suspend fun getByContentSha256(contentSha256: String): DocumentEntity?

    @Query("SELECT * FROM documents")
    suspend fun getAll(): List<DocumentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(document: DocumentEntity)

    /** Identity commits must use ABORT so a hash conflict can never cascade-delete. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDocument(document: DocumentEntity): Long

    /** Targeted existing-row write that preserves the stable primary key and children. */
    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Query(
        """
        UPDATE documents
        SET contentSha256 = :contentSha256, lastOpenedAt = :lastOpenedAt
        WHERE id = :id AND contentSha256 IS NULL
        """,
    )
    suspend fun assignContentSha256IfNull(
        id: String,
        contentSha256: String,
        lastOpenedAt: Long,
    ): Int

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

    /**
     * EPUB progress: [readingAnchor] is the restore authority; the flat row/pixel
     * pair and [readingProgress] are written in the same statement for downgrade
     * and as the missing-chapter fallback.
     */
    @Query(
        """
        UPDATE documents
        SET lastScrollIndex = :lastScrollIndex,
            lastScrollOffset = :lastScrollOffset,
            readingProgress = :readingProgress,
            readingAnchor = :readingAnchor
        WHERE id = :id
        """,
    )
    suspend fun updateEpubReadingPosition(
        id: String,
        lastScrollIndex: Int,
        lastScrollOffset: Int,
        readingProgress: Float,
        readingAnchor: String?,
    )

    /**
     * PDF progress: page index in [lastScrollIndex], total pages in [pageCount],
     * fractional progress in [readingProgress].
     */
    @Query(
        """
        UPDATE documents
        SET lastScrollIndex = :pageIndex,
            lastScrollOffset = :intraPageFractionEncoded,
            readingProgress = :readingProgress,
            pageCount = :pageCount
        WHERE id = :id
        """,
    )
    suspend fun updatePdfReadingPosition(
        id: String,
        pageIndex: Int,
        intraPageFractionEncoded: Int,
        pageCount: Int,
        readingProgress: Float,
    )

    @Query(
        """
        UPDATE documents SET
            pdfViewportSessionVersion = :version,
            pdfViewportReadingMode = :readingMode,
            pdfViewportPageIndex = :pageIndex,
            pdfPageAnchorXFraction = :pageAnchorXFraction,
            pdfPageAnchorYFraction = :pageAnchorYFraction,
            pdfViewportAnchorXFraction = :viewportAnchorXFraction,
            pdfViewportAnchorYFraction = :viewportAnchorYFraction,
            pdfViewportScale = :scale,
            pdfViewportGeneration = :generation
        WHERE id = :id
        """,
    )
    suspend fun updatePdfViewportSession(
        id: String,
        version: Int,
        readingMode: String,
        pageIndex: Int,
        pageAnchorXFraction: Float,
        pageAnchorYFraction: Float,
        viewportAnchorXFraction: Float,
        viewportAnchorYFraction: Float,
        scale: Float,
        generation: Long,
    )

    @Query("DELETE FROM documents")
    suspend fun deleteAll()
}
