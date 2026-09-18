package com.m1racle.reader.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Recent-read document record (Room).
 * Deleting a row never removes a user-owned source file. App-managed copies and
 * extracted package trees are lifecycle-owned by the repository and are cleaned
 * up with the record.
 *
 * 唯一阅读位置事实源（H2_1_READING_MODEL_AUTHORITY_REPORT.md）：下列阅读字段族由
 * DocumentRepository 保存路径单点写入，禁止任何第二阅读状态存储或投影表：
 * - Markdown: [lastScrollIndex]/[lastScrollOffset] = LazyColumn position
 * - PDF: [lastScrollIndex] = 0-based page index; [lastScrollOffset] stores an encoded
 *   intra-page fraction; [pageCount] = total pages when last known;
 *   [readingProgress] ≈ page/(count-1)
 */
@Entity(
    tableName = "documents",
    indices = [
        Index(value = ["contentSha256"], unique = true),
    ],
)
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
    /**
     * SHA-256 of the current reading revision when known.
     * The last verified external source version is stored separately in sourceSha256.
     *
     * Nullable for documents created before the v6 -> v7 migration. Multiple
     * legacy NULL values are permitted by SQLite's UNIQUE index semantics.
     */
    val contentSha256: String? = null,
    /** Original external source; independent from the current managed reading revision. */
    val sourceUri: String? = null,
    /** Last verified external-source bytes, used to reject overwriting outside edits. */
    val sourceSha256: String? = null,
) {
    val type: DocumentType
        get() = DocumentType.fromStorage(documentType)

    val isPdf: Boolean
        get() = type == DocumentType.PDF

    val isMarkdownPackage: Boolean
        get() = type == DocumentType.MARKDOWN_PACKAGE
}
