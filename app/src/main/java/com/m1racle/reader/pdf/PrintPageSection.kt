package com.m1racle.reader.pdf

/**
 * Print-page numbering segment within a PDF.
 *
 * Internal indices are 0-based; UI display remains 1-based.
 * Resolution must never interpolate anchors across section boundaries.
 */
data class PrintPageSection(
    val documentId: String,
    /** Inclusive 0-based PDF page index where this section starts. */
    val startPdfPageIndex: Int,
    /** Inclusive 0-based end; null means open-ended through document end. */
    val endPdfPageIndex: Int?,
    /** Printed page number corresponding to [startPdfPageIndex]. */
    val printedPageStart: Int,
    val numberingType: PrintNumberingType = PrintNumberingType.ARABIC,
    val sortOrder: Int = 0,
    /**
     * When true, pages with no in-section anchor use
     * printedPageStart + (pdfIndex - startPdfPageIndex).
     * Front-matter keeps this false so only real anchors apply.
     */
    val useSectionBaseWithoutAnchor: Boolean = true,
) {
    fun contains(pdfPageIndex: Int): Boolean {
        if (pdfPageIndex < startPdfPageIndex) return false
        val end = endPdfPageIndex
        return end == null || pdfPageIndex <= end
    }

    fun anchorsInSection(anchors: List<com.m1racle.reader.data.local.PdfPrintPageAnchorEntity>) =
        anchors.filter { anchor ->
            anchor.documentId == documentId && contains(anchor.pdfPageIndex)
        }
}

enum class PrintNumberingType {
    ARABIC,
    ROMAN,
    NONE,
}

/**
 * Built-in section seeds for known textbooks (no Room migration).
 */
object PrintPageSectionCatalog {

    /**
     * 《内科学（第10版）》:
     * - Front matter: PDF 0..30 (UI 1..31), anchors only
     * - Body: PDF 31+ (UI 32+) → printed page 1 at UI 32
     */
    fun sectionsFor(documentId: String, displayName: String?): List<PrintPageSection> {
        if (documentId.isBlank()) return emptyList()
        val name = displayName.orEmpty()
        if (name.contains("内科学")) {
            return listOf(
                PrintPageSection(
                    documentId = documentId,
                    startPdfPageIndex = 0,
                    endPdfPageIndex = 30,
                    printedPageStart = 1,
                    numberingType = PrintNumberingType.ARABIC,
                    sortOrder = 0,
                    // Front matter: keep existing anchors only; do not invent.
                    useSectionBaseWithoutAnchor = false,
                ),
                PrintPageSection(
                    documentId = documentId,
                    startPdfPageIndex = 31,
                    endPdfPageIndex = null,
                    printedPageStart = 1,
                    numberingType = PrintNumberingType.ARABIC,
                    sortOrder = 1,
                    // Body: UI PDF 32 (index 31) = printed 1.
                    useSectionBaseWithoutAnchor = true,
                ),
            )
        }
        return emptyList()
    }
}
