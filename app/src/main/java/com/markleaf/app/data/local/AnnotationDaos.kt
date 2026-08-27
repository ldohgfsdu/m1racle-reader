package com.markleaf.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface HighlightDao {

    @Insert
    suspend fun insert(highlight: HighlightEntity): Long

    @Query("DELETE FROM highlights WHERE id = :id AND documentId = :documentId")
    suspend fun delete(documentId: String, id: Long)

    @Query("SELECT * FROM highlights WHERE id = :id AND documentId = :documentId LIMIT 1")
    suspend fun getById(documentId: String, id: Long): HighlightEntity?

    @Query(
        """
        SELECT * FROM highlights
        WHERE documentId = :documentId
          AND locationKind = :locationKind
          AND location = :location
        ORDER BY createdAt ASC, id ASC
        """,
    )
    suspend fun listAtLocation(
        documentId: String,
        locationKind: String,
        location: Int,
    ): List<HighlightEntity>

    @Query(
        """
        SELECT * FROM highlights
        WHERE documentId = :documentId
        ORDER BY location ASC, createdAt ASC, id ASC
        """,
    )
    suspend fun listForDocument(documentId: String): List<HighlightEntity>
}

@Dao
interface NoteDao {

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Query(
        """
        UPDATE notes SET content = :content, updatedAt = :updatedAt
        WHERE id = :id AND documentId = :documentId
        """,
    )
    suspend fun updateContent(documentId: String, id: Long, content: String, updatedAt: Long)

    @Query("DELETE FROM notes WHERE id = :id AND documentId = :documentId")
    suspend fun delete(documentId: String, id: Long)

    @Query("SELECT * FROM notes WHERE id = :id AND documentId = :documentId LIMIT 1")
    suspend fun getById(documentId: String, id: Long): NoteEntity?

    @Query(
        """
        SELECT * FROM notes
        WHERE documentId = :documentId
          AND locationKind = :locationKind
          AND location = :location
        ORDER BY createdAt ASC, id ASC
        """,
    )
    suspend fun listAtLocation(
        documentId: String,
        locationKind: String,
        location: Int,
    ): List<NoteEntity>

    @Query(
        """
        SELECT * FROM notes
        WHERE documentId = :documentId
        ORDER BY location ASC, createdAt ASC, id ASC
        """,
    )
    suspend fun listForDocument(documentId: String): List<NoteEntity>
}
