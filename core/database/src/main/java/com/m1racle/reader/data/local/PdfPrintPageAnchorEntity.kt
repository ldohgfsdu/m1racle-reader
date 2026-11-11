package com.m1racle.reader.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Anchor mapping: one PDF page (0-based index) → textbook print page number.
 * Subsequent pages are inferred linearly until the next anchor.
 */
@Entity(
    tableName = "pdf_print_page_anchors",
    primaryKeys = ["documentId", "pdfPageIndex"],
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
    ],
)
data class PdfPrintPageAnchorEntity(
    val documentId: String,
    /** 0-based PDF page index. */
    val pdfPageIndex: Int,
    /** 1-based textbook print page (user-facing). */
    val printPageNumber: Int,
    val updatedAt: Long,
)
