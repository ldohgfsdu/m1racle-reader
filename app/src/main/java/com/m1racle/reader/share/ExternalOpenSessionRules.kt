package com.m1racle.reader.share

/**
 * Pure decision helpers for single-active external open (JVM-testable, no Android).
 */
object ExternalOpenSessionRules {

    /**
     * Whether the confirm dialog is required before importing/switching.
     * Same active document → no dialog (bring to front only).
     */
    fun requiresConfirm(activeDocumentId: String?, incomingLibraryDocumentId: String?): Boolean {
        if (activeDocumentId == null) return false
        if (incomingLibraryDocumentId != null && incomingLibraryDocumentId == activeDocumentId) {
            return false
        }
        return true
    }

    fun classify(
        activeDocumentId: String?,
        incomingLibraryDocumentId: String?,
    ): ExternalOpenPath {
        if (activeDocumentId != null &&
            incomingLibraryDocumentId != null &&
            incomingLibraryDocumentId == activeDocumentId
        ) {
            return ExternalOpenPath.BRING_TO_FRONT
        }
        if (activeDocumentId != null) return ExternalOpenPath.CONFIRM_REPLACE
        return ExternalOpenPath.DIRECT_IMPORT
    }

    /**
     * Canonical two-phase switch order after user confirms (prepare already success):
     * flush old → commit room → set active → nav replace (old session disposed by nav).
     */
    fun switchStepOrder(): List<String> = listOf(
        "PHASE1_PREPARE",
        "PHASE2_FLUSH_OLD",
        "PHASE2_COMMIT_ROOM",
        "PHASE2_SET_ACTIVE",
        "PHASE2_NAV_REPLACE",
        "OLD_SESSION_DISPOSE",
    )
}

enum class ExternalOpenPath {
    BRING_TO_FRONT,
    CONFIRM_REPLACE,
    DIRECT_IMPORT,
}
