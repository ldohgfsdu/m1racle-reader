package com.m1racle.reader.pdf

import com.m1racle.reader.core.model.PrintPageAnchor
import com.m1racle.reader.data.local.PdfPrintPageAnchorEntity

/** App-side Room adapter. Core PDF resolution consumes [PrintPageAnchor] only. */
fun PdfPrintPageAnchorEntity.toPrintPageAnchor(): PrintPageAnchor = PrintPageAnchor(
    documentId = documentId,
    pdfPageIndex = pdfPageIndex,
    printPageNumber = printPageNumber,
)

fun List<PdfPrintPageAnchorEntity>.toPrintPageAnchors(): List<PrintPageAnchor> =
    map(PdfPrintPageAnchorEntity::toPrintPageAnchor)
