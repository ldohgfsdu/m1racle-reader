package com.m1racle.reader.epub

import com.m1racle.reader.data.anchor.DocumentAnchor

/** One book opening. Reflow never resets this session or authorizes anchor writes. */
class EpubRestoreSession(val result: EpubRestoreResult) {
    private var noticeConsumed = false

    /** Claim before showing so rotation or another layout cannot repeat the notice. */
    fun takeDegradedNotice(): Boolean {
        if (!result.needsDegradedNotice || noticeConsumed) return false
        noticeConsumed = true
        return true
    }

    var preservesOriginalAnchor: Boolean = result.degraded
        private set

    /** Called only by explicit page turns, progress seeks or table-of-contents jumps. */
    fun onUserNavigation() {
        preservesOriginalAnchor = false
    }

    /** Null means omit the anchor write, for reading progress and bookmarks alike. */
    fun anchorForSave(candidate: DocumentAnchor.EpubText): DocumentAnchor.EpubText? =
        candidate.takeUnless { preservesOriginalAnchor }
}
