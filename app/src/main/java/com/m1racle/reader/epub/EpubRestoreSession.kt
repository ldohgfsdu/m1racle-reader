package com.m1racle.reader.epub

import com.m1racle.reader.data.anchor.DocumentAnchor

/** One book opening. Reflow never resets this session or authorizes anchor writes. */
class EpubRestoreSession(val result: EpubRestoreResult) {
    var preservesOriginalAnchor: Boolean = when (result.tier) {
        EpubRestoreTier.Exact, EpubRestoreTier.LegacyPosition, EpubRestoreTier.BookStart -> false
        else -> true
    }
        private set

    /** Called only by explicit page turns, progress seeks or table-of-contents jumps. */
    fun onUserNavigation() {
        preservesOriginalAnchor = false
    }

    /** Null means omit the anchor write, for reading progress and bookmarks alike. */
    fun anchorForSave(candidate: DocumentAnchor.EpubText): DocumentAnchor.EpubText? =
        candidate.takeUnless { preservesOriginalAnchor }
}
