package com.m1racle.reader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {

    @Insert
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query(
        """
        SELECT * FROM bookmarks
        WHERE documentId = :documentId
        ORDER BY createdAt ASC, id ASC
        """,
    )
    suspend fun listForDocument(documentId: String): List<BookmarkEntity>

    @Query(
        """
        SELECT b.id AS id, b.documentId AS documentId, b.locationKind AS locationKind,
               b.location AS location, b.`offset` AS `offset`, b.title AS title,
               b.note AS note, b.createdAt AS createdAt, d.displayName AS documentName, b.anchor AS anchor
        FROM bookmarks b
        INNER JOIN documents d ON d.id = b.documentId
        ORDER BY b.createdAt DESC, b.id DESC
        """,
    )
    fun observeAll(): Flow<List<BookmarkWithDocument>>

    @Query(
        """
        SELECT * FROM bookmarks
        WHERE documentId = :documentId AND locationKind = :locationKind AND location = :location
        LIMIT 1
        """,
    )
    suspend fun getAt(documentId: String, locationKind: String, location: Int): BookmarkEntity?

    /** 删除同一 (文档, 坐标系, 位置) 上的全部书签，返回删除行数。 */
    @Query(
        """
        DELETE FROM bookmarks
        WHERE documentId = :documentId AND locationKind = :locationKind AND location = :location
        """,
    )
    suspend fun deleteAt(documentId: String, locationKind: String, location: Int): Int

    @Query("SELECT * FROM bookmarks WHERE documentId = :documentId ORDER BY createdAt ASC")
    suspend fun listForDocumentAllKinds(documentId: String): List<BookmarkEntity>
}

@Dao
interface FavoriteDocumentDao {

    /** IGNORE：重复收藏是幂等空操作，保留首次 createdAt。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(favorite: FavoriteDocumentEntity): Long

    @Query("DELETE FROM favorite_documents WHERE documentId = :documentId")
    suspend fun delete(documentId: String)

    @Query("SELECT * FROM favorite_documents")
    suspend fun getAll(): List<FavoriteDocumentEntity>

    @Query("SELECT * FROM favorite_documents WHERE documentId = :documentId")
    suspend fun get(documentId: String): FavoriteDocumentEntity?
}
