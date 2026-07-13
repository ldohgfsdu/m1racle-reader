package com.markleaf.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PdfPrintPageAnchorDao {

    @Query(
        """
        SELECT * FROM pdf_print_page_anchors
        WHERE documentId = :documentId
        ORDER BY pdfPageIndex ASC
        """,
    )
    suspend fun listForDocument(documentId: String): List<PdfPrintPageAnchorEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(anchor: PdfPrintPageAnchorEntity)

    @Query(
        """
        DELETE FROM pdf_print_page_anchors
        WHERE documentId = :documentId AND pdfPageIndex = :pdfPageIndex
        """,
    )
    suspend fun delete(documentId: String, pdfPageIndex: Int)

    @Query("DELETE FROM pdf_print_page_anchors WHERE documentId = :documentId")
    suspend fun deleteAllForDocument(documentId: String)
}
