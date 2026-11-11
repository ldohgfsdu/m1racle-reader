package com.m1racle.reader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SearchIndexDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: SearchIndexEntity)

    @Query(
        """
        DELETE FROM search_index
        WHERE documentId = :documentId
          AND contentType = :contentType
          AND sourceId = :sourceId
        """,
    )
    suspend fun delete(documentId: String, contentType: String, sourceId: String)

    @Query("DELETE FROM search_index WHERE documentId = :documentId")
    suspend fun deleteForDocument(documentId: String)

    @Query(
        """
        SELECT search_index.*,
               COALESCE(NULLIF(TRIM(documents.title), ''), documents.displayName) AS documentTitle
        FROM search_index
        INNER JOIN documents ON documents.id = search_index.documentId
        WHERE search_index.content LIKE '%' || :escapedQuery || '%' ESCAPE '\'
        ORDER BY
            CASE contentType
                WHEN 'DOCUMENT' THEN 0
                WHEN 'HIGHLIGHT' THEN 1
                WHEN 'NOTE' THEN 2
                ELSE 3
            END ASC,
            indexedAt DESC,
            documentId ASC,
            sourceId ASC
        LIMIT :limit
        """,
    )
    suspend fun searchAll(escapedQuery: String, limit: Int): List<SearchIndexWithDocument>

    @Query(
        """
        SELECT search_index.*,
               COALESCE(NULLIF(TRIM(documents.title), ''), documents.displayName) AS documentTitle
        FROM search_index
        INNER JOIN documents ON documents.id = search_index.documentId
        WHERE search_index.documentId = :documentId
          AND search_index.content LIKE '%' || :escapedQuery || '%' ESCAPE '\'
        ORDER BY
            CASE contentType
                WHEN 'DOCUMENT' THEN 0
                WHEN 'HIGHLIGHT' THEN 1
                WHEN 'NOTE' THEN 2
                ELSE 3
            END ASC,
            indexedAt DESC,
            sourceId ASC
        LIMIT :limit
        """,
    )
    suspend fun searchInDocument(
        documentId: String,
        escapedQuery: String,
        limit: Int,
    ): List<SearchIndexWithDocument>
}
