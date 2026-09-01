package com.m1racle.reader.share

import com.m1racle.reader.data.repository.ResolvedImportRequest

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
 * Result of PHASE 1 prepare. It deliberately contains no document id: only
 * `commitResolvedImport` may select an existing id or mint a new one.
 */
data class PreparedOpen(
    val importRequest: ResolvedImportRequest,
    val activeDocumentId: String?,
) {
    val displayName: String get() = importRequest.content.metadata.displayName
    val sizeBytes: Long get() = importRequest.content.byteCount
    val isPdf: Boolean get() = importRequest.documentType == com.m1racle.reader.data.local.DocumentType.PDF
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

/**
 * Replay-latest navigation request held by [ExternalOpenCoordinator.pendingNavigation]
 * until the UI performs it and calls [ExternalOpenCoordinator.ackNavigation].
 *
 * [requestId] ties ack to the delivery that produced this request, so a stale ack
 * (e.g. a slower UI from a previous rotation) can never clear a newer request.
 */
data class NavigationRequest(
    val requestId: String,
    val documentId: String,
    val isPdf: Boolean,
    val bringToFrontOnly: Boolean = false,
)

sealed class ExternalOpenUiEffect {
    data class Toast(val message: String) : ExternalOpenUiEffect()
    data class ToastUnsupported(val fileName: String, val mime: String?) : ExternalOpenUiEffect()
    data object ToastSendMultiple : ExternalOpenUiEffect()
    data object ToastTextRequiresFile : ExternalOpenUiEffect()
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
