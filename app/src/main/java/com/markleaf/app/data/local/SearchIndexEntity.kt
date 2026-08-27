package com.markleaf.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** R29 local-search content categories. Stored as [name] without a converter. */
enum class SearchContentType {
    DOCUMENT,
    HIGHLIGHT,
    NOTE,
    ;

    companion object {
        fun fromStorage(value: String): SearchContentType? =
            entries.firstOrNull { it.name == value }
    }
}

/**
 * Derived local-search row. The source models remain authoritative; this table
 * only stores searchable text plus a complete navigation locator.
 *
 * The composite primary key scopes every source identity to its document and
 * content type. This permits safe re-index/delete operations without assuming
 * that a Room auto id is globally meaningful across source types.
 */
@Entity(
    tableName = "search_index",
    primaryKeys = ["documentId", "contentType", "sourceId"],
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
        Index(value = ["contentType"]),
    ],
)
data class SearchIndexEntity(
    val documentId: String,
    val contentType: String,
    val sourceId: String,
    val content: String,
    val locationKind: String,
    val location: Int,
    /** Canonical AnchorCodec payload; non-null for every searchable result. */
    val anchor: String,
    val indexedAt: Long,
)

/** Search row joined with the current document title for result presentation. */
data class SearchIndexWithDocument(
    val documentId: String,
    val contentType: String,
    val sourceId: String,
    val content: String,
    val locationKind: String,
    val location: Int,
    val anchor: String,
    val indexedAt: Long,
    val documentTitle: String,
)
