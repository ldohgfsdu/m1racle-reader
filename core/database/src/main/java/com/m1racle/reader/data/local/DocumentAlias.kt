package com.m1racle.reader.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update

@Entity(
    tableName = "document_aliases",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["documentId"])],
)
data class DocumentAliasEntity(
    @PrimaryKey val aliasKey: String,
    val documentId: String,
    val kind: String,
    val createdAt: Long,
    val lastSeenAt: Long,
)

@Dao
interface DocumentAliasDao {
    @Query("SELECT * FROM document_aliases WHERE aliasKey = :aliasKey LIMIT 1")
    suspend fun getByKey(aliasKey: String): DocumentAliasEntity?

    @Query(
        """
        SELECT * FROM document_aliases
        WHERE documentId = :documentId
        ORDER BY createdAt ASC, aliasKey ASC
        """,
    )
    suspend fun listForDocument(documentId: String): List<DocumentAliasEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAlias(alias: DocumentAliasEntity): Long

    /** Runtime-only rebind after current bytes have been staged and verified. */
    @Update
    suspend fun updateAlias(alias: DocumentAliasEntity)

    /** One-time migration cleanup; runtime code never deletes aliases. */
    @Query("DELETE FROM document_aliases WHERE kind = :kind")
    suspend fun deleteByKind(kind: String): Int
}
