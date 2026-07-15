package com.markleaf.app.share

import android.app.Application
import android.content.Intent
import android.net.Uri
import com.markleaf.app.data.repository.DocumentRepository
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Two-phase external open owner:
 *
 * PHASE 1 prepare — old viewer stays fully active.
 * PHASE 2 commit  — only after prepare succeeds: flush → switch active → one nav replace.
 */
class ExternalOpenCoordinator(
    private val app: Application,
    private val repository: DocumentRepository,
    importer: ExternalDocumentImporter? = null,
) {
    private val importer = importer ?: ExternalDocumentImporter(app, repository)
    private val mutex = Mutex()
    private val handledEventIds = ConcurrentHashMap.newKeySet<String>()

    private val _activeDocumentId = MutableStateFlow<String?>(null)
    val activeDocumentId: StateFlow<String?> = _activeDocumentId.asStateFlow()

    private val _activeViewerSessionId = MutableStateFlow<String?>(null)
    val activeViewerSessionId: StateFlow<String?> = _activeViewerSessionId.asStateFlow()

    /** Diagnostic: peak concurrent active renderer sessions claimed via onViewerOpened. */
    private val liveRendererCount = AtomicInteger(0)
    private val _peakRendererCount = AtomicInteger(0)
    val peakRendererCount: Int get() = _peakRendererCount.get()

    private val _pending = MutableStateFlow<PendingExternalOpen?>(null)
    val pending: StateFlow<PendingExternalOpen?> = _pending.asStateFlow()

    private val _effects = MutableSharedFlow<ExternalOpenUiEffect>(extraBufferCapacity = 8)
    val effects: SharedFlow<ExternalOpenUiEffect> = _effects.asSharedFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Must succeed before session switch; failure aborts commit and keeps old viewer. */
    @Volatile
    var progressFlushHook: (suspend (documentId: String) -> Unit)? = null

    fun onViewerOpened(documentId: String, sessionId: String) {
        _activeDocumentId.value = documentId
        _activeViewerSessionId.value = sessionId
        val n = liveRendererCount.incrementAndGet()
        _peakRendererCount.updateAndGet { cur -> maxOf(cur, n) }
    }

    fun onViewerClosed(documentId: String, sessionId: String) {
        liveRendererCount.updateAndGet { c -> (c - 1).coerceAtLeast(0) }
        if (_activeViewerSessionId.value == sessionId) {
            _activeViewerSessionId.value = null
            if (_activeDocumentId.value == documentId) {
                _activeDocumentId.value = null
            }
        }
    }

    fun makeEventId(action: String?, uri: Uri?, type: String?): ExternalOpenEventId {
        val raw = listOf(action.orEmpty(), uri?.toString().orEmpty(), type.orEmpty()).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }.take(32)
        return ExternalOpenEventId(hex)
    }

    /**
     * @param allowActivityRecreateReplay false for MainActivity onCreate when
     * savedInstanceState != null (rotation must not re-fire). onNewIntent always true.
     */
    suspend fun onIncomingIntent(intent: Intent?, allowActivityRecreateReplay: Boolean = true) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_SEND_MULTIPLE || ExternalIntentUri.isSendMultiple(intent)) {
            _effects.emit(ExternalOpenUiEffect.ToastSendMultiple)
            return
        }
        if (!ExternalIntentUri.isExternalOpenAction(intent.action)) return
        val uri = ExternalIntentUri.extract(intent) ?: return
        val eventId = makeEventId(intent.action, uri, intent.type)

        mutex.withLock {
            // Same in-flight event only. Do not permanently ban a URI after cancel/success:
            // user may open the same file again from a third party (onNewIntent).
            if (_pending.value?.eventId == eventId) return
            if (_busy.value) return
            // Activity recreate replay: skip if this event was already consumed (cancel/success).
            if (!allowActivityRecreateReplay && handledEventIds.contains(eventId.value)) return

            val activeId = _activeDocumentId.value
            val currentDoc = activeId?.let { repository.getDocumentById(it) }
            val currentCard = currentDoc?.let {
                DocumentIdentityCard(
                    documentId = it.id,
                    displayName = it.displayName,
                    sizeBytes = it.fileSize,
                    sizeUnknownUntilOpen = false,
                    isPdf = it.isPdf,
                )
            }
            val incoming = importer.probeIncomingCard(uri, intent.type)

            // Reject unsupported before any confirm dialog or copy.
            if (!ExternalFileIntake.isAllowed(intent.type, incoming.displayName, uri.lastPathSegment)) {
                handledEventIds.add(eventId.value)
                _effects.emit(
                    ExternalOpenUiEffect.ToastUnsupported(
                        incoming.displayName,
                        intent.type,
                    ),
                )
                return
            }

            // Quick path: probe already knows library id and matches active
            val path = ExternalOpenSessionRules.classify(
                activeDocumentId = currentDoc?.id,
                incomingLibraryDocumentId = incoming.documentId,
            )
            if (path == ExternalOpenPath.BRING_TO_FRONT && currentDoc != null) {
                handledEventIds.add(eventId.value)
                _effects.emit(
                    ExternalOpenUiEffect.NavigateToDocument(
                        documentId = currentDoc.id,
                        isPdf = currentDoc.isPdf,
                        bringToFrontOnly = true,
                    ),
                )
                return
            }

            val requiresConfirm = path == ExternalOpenPath.CONFIRM_REPLACE
            val pending = PendingExternalOpen(
                eventId = eventId,
                uriString = uri.toString(),
                intentAction = intent.action.orEmpty(),
                intentType = intent.type,
                grantFlags = intent.flags,
                incoming = incoming,
                current = currentCard,
                phase = if (requiresConfirm) ExternalOpenPhase.AWAITING_CONFIRM else ExternalOpenPhase.PREPARING,
                requiresConfirm = requiresConfirm,
            )
            _pending.value = pending

            if (!requiresConfirm) {
                // Cold open or no active viewer: prepare then commit without dialog.
                runPrepareAndCommitLocked(pending)
            }
        }
    }

    suspend fun cancelPending() {
        mutex.withLock {
            val p = _pending.value ?: return
            importer.discardPrepared(p.prepared)
            // Consumed for recreate replay only. onNewIntent clears this via
            // [clearEventHandledForNewDelivery] so the same URI can confirm later.
            handledEventIds.add(p.eventId.value)
            _pending.value = null
        }
    }

    /** Call from onNewIntent before onIncomingIntent so re-open after cancel works. */
    fun clearEventHandledForNewDelivery(intent: Intent?) {
        if (intent == null) return
        if (!ExternalIntentUri.isExternalOpenAction(intent.action)) return
        if (ExternalIntentUri.isSendMultiple(intent)) return
        val uri = ExternalIntentUri.extract(intent) ?: return
        val eventId = makeEventId(intent.action, uri, intent.type)
        handledEventIds.remove(eventId.value)
    }

    /**
     * User confirmed replace. PHASE 1 prepare while old viewer still active, then PHASE 2 commit.
     */
    suspend fun confirmPending() {
        mutex.withLock {
            val p = _pending.value ?: return
            if (p.phase != ExternalOpenPhase.AWAITING_CONFIRM &&
                p.phase != ExternalOpenPhase.READY_TO_COMMIT
            ) {
                return
            }
            runPrepareAndCommitLocked(p)
        }
    }

    private suspend fun runPrepareAndCommitLocked(pending: PendingExternalOpen) {
        _busy.value = true
        try {
            // ----- PHASE 1: PREPARE (old session untouched) -----
            _pending.value = pending.copy(phase = ExternalOpenPhase.PREPARING, prepared = null)
            val uri = Uri.parse(pending.uriString)
            val activeIdBefore = _activeDocumentId.value
            val prepareOutcome = importer.prepare(
                sourceUri = uri,
                intentType = pending.intentType,
                grantFlags = pending.grantFlags,
                activeDocumentId = activeIdBefore,
            )

            when (prepareOutcome) {
                is PrepareOutcome.Success -> {
                    val prepared = prepareOutcome.prepared
                    // A: already current
                    if (prepared is PreparedOpen.CurrentActive) {
                        handledEventIds.add(pending.eventId.value)
                        _pending.value = null
                        _effects.emit(
                            ExternalOpenUiEffect.NavigateToDocument(
                                documentId = prepared.documentId,
                                isPdf = prepared.isPdf,
                                bringToFrontOnly = true,
                            ),
                        )
                        return
                    }

                    _pending.value = pending.copy(
                        phase = ExternalOpenPhase.READY_TO_COMMIT,
                        prepared = prepared,
                        incoming = pending.incoming.copy(
                            documentId = when (prepared) {
                                is PreparedOpen.ExistingLibrary -> prepared.documentId
                                is PreparedOpen.NewContent -> prepared.documentId
                                is PreparedOpen.CurrentActive -> prepared.documentId
                            },
                            sizeBytes = when (prepared) {
                                is PreparedOpen.ExistingLibrary -> prepared.sizeBytes
                                is PreparedOpen.NewContent -> prepared.sizeBytes
                                is PreparedOpen.CurrentActive -> prepared.sizeBytes
                            },
                            sizeUnknownUntilOpen = false,
                        ),
                    )

                    // ----- PHASE 2: COMMIT -----
                    // Flush first; on failure keep old session and discard prepared new content.
                    val oldId = pending.current?.documentId ?: activeIdBefore
                    if (oldId != null && prepared !is PreparedOpen.CurrentActive) {
                        try {
                            progressFlushHook?.invoke(oldId)
                        } catch (e: Exception) {
                            importer.discardPrepared(prepared)
                            handledEventIds.add(pending.eventId.value)
                            _pending.value = null
                            _effects.emit(ExternalOpenUiEffect.Toast("无法保存当前进度，已取消切换"))
                            return
                        }
                    }

                    _pending.value = _pending.value?.copy(phase = ExternalOpenPhase.COMMITTING)
                    when (val commit = importer.commitPrepared(prepared)) {
                        is CommitOutcome.Success -> {
                            // Atomic session bookkeeping update BEFORE nav effect.
                            val sessionId = "sess-${commit.documentId}-${System.nanoTime()}"
                            _activeDocumentId.value = commit.documentId
                            _activeViewerSessionId.value = sessionId
                            handledEventIds.add(pending.eventId.value)
                            _pending.value = null
                            _effects.emit(
                                ExternalOpenUiEffect.NavigateToDocument(
                                    documentId = commit.documentId,
                                    isPdf = commit.isPdf,
                                    bringToFrontOnly = commit.bringToFrontOnly,
                                ),
                            )
                        }
                        is CommitOutcome.Failed -> {
                            importer.discardPrepared(prepared)
                            handledEventIds.add(pending.eventId.value)
                            // Old session variables unchanged if we never cleared them.
                            _pending.value = null
                            _effects.emit(ExternalOpenUiEffect.Toast(commit.message))
                        }
                    }
                }
                PrepareOutcome.Unsupported -> {
                    handledEventIds.add(pending.eventId.value)
                    _pending.value = null
                    _effects.emit(
                        ExternalOpenUiEffect.ToastUnsupported(
                            pending.incoming.displayName,
                            pending.intentType,
                        ),
                    )
                }
                PrepareOutcome.TooLarge -> {
                    handledEventIds.add(pending.eventId.value)
                    _pending.value = null
                    _effects.emit(ExternalOpenUiEffect.Toast("文件过大"))
                }
                is PrepareOutcome.Failed -> {
                    handledEventIds.add(pending.eventId.value)
                    _pending.value = null
                    // Old viewer remains active — we never cleared active ids.
                    _effects.emit(ExternalOpenUiEffect.Toast(prepareOutcome.message))
                }
            }
        } finally {
            _busy.value = false
        }
    }

    fun dismissError() {
        _pending.value = null
    }
}
