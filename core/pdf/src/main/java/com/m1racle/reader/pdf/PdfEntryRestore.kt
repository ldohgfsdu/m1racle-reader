package com.m1racle.reader.pdf

import com.m1racle.reader.core.model.PdfReadingMode
import com.m1racle.reader.core.model.PdfReadingNavigation
import com.m1racle.reader.core.model.PdfViewportSessionState

/**
 * Entry restore resolved from the two persisted authorities.
 *
 * [PdfViewportSessionState] refines the in-page anchor and zoom for a page. The
 * document row's progress (`lastScrollIndex` / `lastScrollOffset`) is the
 * authority for which page the reader was on. A session may only refine the
 * entry when it describes the same page in the same reading mode; otherwise it
 * is stale (for example left over from an older mode or an interrupted write)
 * and must not move the reader back to an older page or be written back.
 */
data class PdfEntryRestore(
    val pageIndex: Int,
    val intraPageFraction: Float,
    val session: PdfViewportSessionState?,
)

fun resolvePdfEntryRestore(
    progressPageIndex: Int,
    progressIntraPageFraction: Float,
    session: PdfViewportSessionState?,
    pageCount: Int,
    readingMode: PdfReadingMode,
): PdfEntryRestore {
    val page = PdfReadingNavigation.clampPageIndex(progressPageIndex, pageCount)
    val progressFraction = progressIntraPageFraction.coerceIn(0f, 1f)
    val consistent = session != null &&
        session.readingMode == readingMode &&
        session.pageIndex == page
    if (!consistent) {
        return PdfEntryRestore(
            pageIndex = page,
            intraPageFraction = progressFraction,
            session = null,
        )
    }
    return PdfEntryRestore(
        pageIndex = page,
        intraPageFraction = session.pageAnchorYFraction.coerceIn(0f, 1f),
        session = session,
    )
}
