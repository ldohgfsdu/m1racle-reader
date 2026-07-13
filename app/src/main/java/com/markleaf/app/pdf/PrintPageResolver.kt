package com.markleaf.app.pdf

import com.markleaf.app.data.local.PdfPrintPageAnchorEntity

/**
 * Infer textbook print page from sparse anchors and optional [PrintPageSection]s.
 *
 * Section rules:
 * 1. Resolve the section for [pdfPageIndex] first.
 * 2. Only anchors inside that section may be used.
 * 3. Never interpolate across section boundaries.
 * 4. Without sections, fall back to document-wide anchors (UI shows 推算).
 *
 * Never returns [pdfPageIndex] itself as a fake printed page when mapping is missing.
 */
object PrintPageResolver {

    data class Result(
        val printPageNumber: Int?,
        val isExactAnchor: Boolean,
        /** True when value comes from section base or nearest-anchor delta (推算). */
        val isInferred: Boolean,
        val section: PrintPageSection? = null,
    ) {
        val hasValue: Boolean get() = printPageNumber != null
    }

    fun resolve(
        pdfPageIndex: Int,
        anchors: List<PdfPrintPageAnchorEntity>,
    ): Int? {
        if (pdfPageIndex < 0 || anchors.isEmpty()) return null
        val anchor = anchors
            .asSequence()
            .filter { it.pdfPageIndex <= pdfPageIndex }
            .maxByOrNull { it.pdfPageIndex }
            ?: return null
        val delta = pdfPageIndex - anchor.pdfPageIndex
        return anchor.printPageNumber + delta
    }

    /**
     * Same as [resolve], filtered to [documentId] so callers cannot mix anchors.
     */
    fun resolve(
        documentId: String,
        pdfPageIndex: Int,
        anchors: List<PdfPrintPageAnchorEntity>,
    ): Int? {
        if (documentId.isBlank()) return null
        val filtered = anchors.filter { it.documentId == documentId }
        return resolve(pdfPageIndex, filtered)
    }

    /**
     * Section-aware resolution. Prefer this for the reader chrome.
     */
    fun resolveDetailed(
        documentId: String,
        pdfPageIndex: Int,
        anchors: List<PdfPrintPageAnchorEntity>,
        sections: List<PrintPageSection>,
    ): Result {
        if (documentId.isBlank() || pdfPageIndex < 0) {
            return Result(null, isExactAnchor = false, isInferred = false)
        }
        val docAnchors = anchors.filter { it.documentId == documentId }
        val docSections = sections
            .filter { it.documentId == documentId }
            .sortedWith(compareBy({ it.sortOrder }, { it.startPdfPageIndex }))

        val section = findSection(pdfPageIndex, docSections)
        if (section != null) {
            return resolveInSection(pdfPageIndex, docAnchors, section)
        }

        // No section: legacy document-wide anchors only (推算 if not exact).
        if (docAnchors.isEmpty()) {
            return Result(null, isExactAnchor = false, isInferred = false)
        }
        val exact = docAnchors.any { it.pdfPageIndex == pdfPageIndex }
        val value = resolve(pdfPageIndex, docAnchors)
        return Result(
            printPageNumber = value,
            isExactAnchor = exact && value != null,
            isInferred = value != null && !exact,
            section = null,
        )
    }

    fun resolve(
        documentId: String,
        pdfPageIndex: Int,
        anchors: List<PdfPrintPageAnchorEntity>,
        sections: List<PrintPageSection>,
    ): Int? = resolveDetailed(documentId, pdfPageIndex, anchors, sections).printPageNumber

    fun findSection(
        pdfPageIndex: Int,
        sections: List<PrintPageSection>,
    ): PrintPageSection? {
        if (sections.isEmpty() || pdfPageIndex < 0) return null
        return sections
            .filter { it.contains(pdfPageIndex) }
            .maxByOrNull { it.startPdfPageIndex }
    }

    private fun resolveInSection(
        pdfPageIndex: Int,
        documentAnchors: List<PdfPrintPageAnchorEntity>,
        section: PrintPageSection,
    ): Result {
        if (section.numberingType == PrintNumberingType.NONE) {
            return Result(null, isExactAnchor = false, isInferred = false, section = section)
        }

        // Anchors strictly inside this section only — never inherit other sections.
        val sectionAnchors = section.anchorsInSection(documentAnchors)
        val exact = sectionAnchors.any { it.pdfPageIndex == pdfPageIndex }

        val fromAnchor = resolve(pdfPageIndex, sectionAnchors)
        if (fromAnchor != null) {
            // Guard: anchor path must stay within section (no cross-section bleed).
            return Result(
                printPageNumber = fromAnchor,
                isExactAnchor = exact,
                isInferred = !exact,
                section = section,
            )
        }

        // No usable anchor in section: optional linear base (body seed).
        if (section.useSectionBaseWithoutAnchor &&
            (section.numberingType == PrintNumberingType.ARABIC ||
                section.numberingType == PrintNumberingType.ROMAN)
        ) {
            val delta = pdfPageIndex - section.startPdfPageIndex
            if (delta < 0) {
                return Result(null, isExactAnchor = false, isInferred = false, section = section)
            }
            val printed = section.printedPageStart + delta
            return Result(
                printPageNumber = printed,
                isExactAnchor = false,
                isInferred = true,
                section = section,
            )
        }

        return Result(null, isExactAnchor = false, isInferred = false, section = section)
    }

    /** True when [pdfPageIndex] is an explicit user-set anchor. */
    fun isExactAnchor(pdfPageIndex: Int, anchors: List<PdfPrintPageAnchorEntity>): Boolean {
        return anchors.any { it.pdfPageIndex == pdfPageIndex }
    }

    fun isExactAnchor(
        documentId: String,
        pdfPageIndex: Int,
        anchors: List<PdfPrintPageAnchorEntity>,
    ): Boolean {
        if (documentId.isBlank()) return false
        return anchors.any { it.documentId == documentId && it.pdfPageIndex == pdfPageIndex }
    }

    fun isExactAnchor(
        documentId: String,
        pdfPageIndex: Int,
        anchors: List<PdfPrintPageAnchorEntity>,
        sections: List<PrintPageSection>,
    ): Boolean = resolveDetailed(documentId, pdfPageIndex, anchors, sections).isExactAnchor
}
