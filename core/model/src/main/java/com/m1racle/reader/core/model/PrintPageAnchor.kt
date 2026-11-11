package com.m1racle.reader.core.model

/**
 * Persistence-free print-page anchor used by PDF numbering resolution.
 * Room entities stay in the database module and map onto this value.
 */
data class PrintPageAnchor(
    val documentId: String,
    /** 0-based PDF page index. */
    val pdfPageIndex: Int,
    /** 1-based textbook print page. */
    val printPageNumber: Int,
)
