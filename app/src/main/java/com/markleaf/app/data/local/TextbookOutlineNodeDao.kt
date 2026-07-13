package com.markleaf.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface TextbookOutlineNodeDao {

    @Query(
        """
        SELECT * FROM textbook_outline_nodes
        WHERE documentId = :documentId
        ORDER BY sortOrder ASC, startPageIndex ASC, title ASC
        """,
    )
    suspend fun listForDocument(documentId: String): List<TextbookOutlineNodeEntity>

    @Query(
        """
        SELECT * FROM textbook_outline_nodes
        WHERE id = :id
        LIMIT 1
        """,
    )
    suspend fun getById(id: String): TextbookOutlineNodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(node: TextbookOutlineNodeEntity)

    @Update
    suspend fun update(node: TextbookOutlineNodeEntity)

    @Query("DELETE FROM textbook_outline_nodes WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM textbook_outline_nodes WHERE documentId = :documentId")
    suspend fun deleteAllForDocument(documentId: String)

    @Query(
        """
        SELECT * FROM textbook_outline_nodes
        WHERE documentId = :documentId AND parentId = :parentId
        ORDER BY sortOrder ASC, startPageIndex ASC
        """,
    )
    suspend fun listChildren(documentId: String, parentId: String): List<TextbookOutlineNodeEntity>

    @Query(
        """
        SELECT * FROM textbook_outline_nodes
        WHERE documentId = :documentId AND parentId IS NULL
        ORDER BY sortOrder ASC, startPageIndex ASC
        """,
    )
    suspend fun listRoots(documentId: String): List<TextbookOutlineNodeEntity>
}
