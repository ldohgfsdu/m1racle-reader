package com.markleaf.app.share

import java.io.File

/** Stable identity for a single external-open delivery (survives config change; one-shot). */
data class ExternalOpenEventId(val value: String)

enum class ExternalOpenPhase {
    IDLE,
    AWAITING_CONFIRM,
    PREPARING,
    READY_TO_COMMIT,
    COMMITTING,
    NAVIGATING,
    ERROR,
    FAILED,
}

data class DocumentIdentityCard(
    val documentId: String?,
    val displayName: String,
    val sizeBytes: Long?,
    /** When true, confirm dialog shows “大小：打开后自动计算” instead of a byte size. */
    val sizeUnknownUntilOpen: Boolean = false,
    val isPdf: Boolean,
)

/**
 * Result of PHASE 1 prepare — old viewer must still be fully active when this is produced.
 */
sealed class PreparedOpen {
    /** Target is the currently active document — bring to front only. */
    data class CurrentActive(
        val documentId: String,
        val isPdf: Boolean,
        val displayName: String,
        val sizeBytes: Long?,
    ) : PreparedOpen()

    /** Library already has this content/uri; switch without re-copy. */
    data class ExistingLibrary(
        val documentId: String,
        val isPdf: Boolean,
        val displayName: String,
        val sizeBytes: Long?,
        val contentSha256: String,
        val externalKey: String,
        val mimeType: String?,
    ) : PreparedOpen()

    /** New content copied to a validated temp/final staging path; Room not active-switched yet. */
    data class NewContent(
        val documentId: String,
        val isPdf: Boolean,
        val displayName: String,
        val sizeBytes: Long,
        val contentSha256: String,
        val externalKey: String,
        val mimeType: String?,
        val stagedFile: File,
        val documentTypeName: String,
    ) : PreparedOpen()
}

/**
 * Pending external open held outside one-shot Compose state so rotation cannot re-fire imports.
 */
data class PendingExternalOpen(
    val eventId: ExternalOpenEventId,
    val uriString: String,
    val intentAction: String,
    val intentType: String?,
    val grantFlags: Int,
    val incoming: DocumentIdentityCard,
    val current: DocumentIdentityCard?,
    val phase: ExternalOpenPhase,
    val errorMessage: String? = null,
    val requiresConfirm: Boolean,
    val prepared: PreparedOpen? = null,
)

sealed class ExternalOpenUiEffect {
    /**
     * Atomic navigation: replace any current viewer with this document in one nav transaction.
     * [bringToFrontOnly] means do not reset route state if already showing this document.
     */
    data class NavigateToDocument(
        val documentId: String,
        val isPdf: Boolean,
        val bringToFrontOnly: Boolean = false,
    ) : ExternalOpenUiEffect()

    data class Toast(val message: String) : ExternalOpenUiEffect()
    data class ToastUnsupported(val fileName: String, val mime: String?) : ExternalOpenUiEffect()
    data object ToastSendMultiple : ExternalOpenUiEffect()
}

sealed class PrepareOutcome {
    data class Success(val prepared: PreparedOpen) : PrepareOutcome()
    data object Unsupported : PrepareOutcome()
    data object TooLarge : PrepareOutcome()
    data class Failed(val message: String) : PrepareOutcome()
}

sealed class CommitOutcome {
    data class Success(
        val documentId: String,
        val isPdf: Boolean,
        val displayName: String,
        val sizeBytes: Long,
        val contentSha256: String,
        val reusedExisting: Boolean,
        val bringToFrontOnly: Boolean,
    ) : CommitOutcome()

    data class Failed(val message: String) : CommitOutcome()
}

/** @deprecated Prefer [PrepareOutcome] + [CommitOutcome]; kept for older call sites/tests. */
sealed class ExternalImportOutcome {
    data class Success(
        val documentId: String,
        val isPdf: Boolean,
        val displayName: String,
        val sizeBytes: Long,
        val contentSha256: String,
        val reusedExisting: Boolean,
    ) : ExternalImportOutcome()

    data object Unsupported : ExternalImportOutcome()
    data object TooLarge : ExternalImportOutcome()
    data class Failed(val message: String) : ExternalImportOutcome()
    data object Cancelled : ExternalImportOutcome()
}
