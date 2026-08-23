package com.markleaf.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Recent-read document record (Room).
 * Deleting a row never removes a user-owned source file. App-managed copies and
 * extracted package trees are lifecycle-owned by the repository and are cleaned
 * up with the record.
 *
 * Reading progress fields are shared:
 * - Markdown: [lastScrollIndex]/[lastScrollOffset] = LazyColumn position
 * - PDF: [lastScrollIndex] = 0-based page index; [lastScrollOffset] stores an encoded
 *   intra-page fraction; [pageCount] = total pages when last known;
 *   [readingProgress] ≈ page/(count-1)
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
    /** [DocumentType] name: MARKDOWN or PDF. */
    val documentType: String = DocumentType.MARKDOWN.name,
    /** PDF total page count when known; 0 for Markdown / unknown. */
    val pageCount: Int = 0,
    val pdfViewportSessionVersion: Int? = null,
    val pdfViewportReadingMode: String? = null,
    val pdfViewportPageIndex: Int? = null,
    val pdfPageAnchorXFraction: Float? = null,
    val pdfPageAnchorYFraction: Float? = null,
    val pdfViewportAnchorXFraction: Float? = null,
    val pdfViewportAnchorYFraction: Float? = null,
    val pdfViewportScale: Float? = null,
    val pdfViewportGeneration: Long? = null,
) {
    val type: DocumentType
        get() = DocumentType.fromStorage(documentType)

    val isPdf: Boolean
        get() = type == DocumentType.PDF

    val isMarkdownPackage: Boolean
        get() = type == DocumentType.MARKDOWN_PACKAGE
}
