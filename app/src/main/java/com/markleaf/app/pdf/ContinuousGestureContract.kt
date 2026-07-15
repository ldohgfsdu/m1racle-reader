package com.markleaf.app.pdf

/**
 * One committed viewport snapshot. A long-lived pointer coroutine captures this object
 * at gesture start instead of closing over recomposition-local collections.
 */
data class ContinuousGestureSnapshotPayload(
    val documentId: String,
    val composedPages: Set<Int>,
    val visiblePages: Set<Int>,
    val viewportPage: Int,
    val geometry: ContinuousDocumentGeometry,
    val documentState: ContinuousDocumentState,
    val viewportWidth: Int,
    val viewportHeight: Int,
    val displayedBitmapIds: Map<Int, Int>,
    val pageBounds: Map<Int, OverlayRect>,
) {
    val isUsable: Boolean
        get() = viewportWidth > 0 && viewportHeight > 0 && composedPages.isNotEmpty()
}

data class ContinuousGestureSnapshot(
    val version: Long,
    val payload: ContinuousGestureSnapshotPayload,
)

/** Latest-successful-composition registry used by the stable document-key pointer detector. */
class ContinuousGestureSnapshotRegistry {
    private var version = 0L
    private var current: ContinuousGestureSnapshot? = null

    fun publish(payload: ContinuousGestureSnapshotPayload): ContinuousGestureSnapshot {
        val snapshot = ContinuousGestureSnapshot(++version, payload)
        current = snapshot
        return snapshot
    }

    fun capture(): ContinuousGestureSnapshot? = current
}

data class ContinuousGestureCapture(
    val generation: Long,
    val snapshot: ContinuousGestureSnapshot,
)

/**
 * Owns the exact true/false pairing for one continuous-viewport gesture lifecycle.
 * A rapid re-pinch advances generation without emitting a second `true`; stale settle
 * completions therefore cannot end the newer gesture.
 */
class ContinuousGestureCoordinator(
    private val ownerDocumentId: String? = null,
    private val onGestureActiveChanged: (Boolean) -> Unit,
) {
    val snapshots = ContinuousGestureSnapshotRegistry()

    private var generation = 0L
    private var active = false

    val isActive: Boolean get() = active
    val currentGeneration: Long get() = generation
    var lastCapture: ContinuousGestureCapture? = null
        private set

    fun beginCapture(): ContinuousGestureCapture? {
        val snapshot = snapshots.capture()?.takeIf {
            it.payload.isUsable &&
                (ownerDocumentId == null || it.payload.documentId == ownerDocumentId)
        } ?: return null
        generation += 1L
        if (!active) {
            active = true
            onGestureActiveChanged(true)
        }
        return ContinuousGestureCapture(generation, snapshot).also { lastCapture = it }
    }

    fun isCurrent(captureGeneration: Long): Boolean =
        active && generation == captureGeneration

    /** Returns true only for the single owner allowed to commit and emit false. */
    fun finish(captureGeneration: Long): Boolean {
        if (!isCurrent(captureGeneration)) return false
        active = false
        onGestureActiveChanged(false)
        return true
    }

    /** Atomically grants the current owner its single stable-transform commit. */
    fun finishAndCommit(
        captureGeneration: Long,
        stableState: ContinuousDocumentState,
        onStableCommit: (ContinuousDocumentState) -> Unit,
    ): Boolean {
        if (!finish(captureGeneration)) return false
        onStableCommit(clampContinuousDocumentState(stableState))
        return true
    }

    /** Dispose/exception cleanup. Repeated calls are intentionally side-effect free. */
    fun cleanup(): Boolean {
        if (!active) return false
        generation += 1L
        active = false
        onGestureActiveChanged(false)
        return true
    }
}

/** Stable VM-side R7 session. Active UI transforms never read back from this object. */
class ContinuousDocumentSession(
    initialState: ContinuousDocumentState = ContinuousDocumentState(),
) {
    var stableState: ContinuousDocumentState = clampContinuousDocumentState(initialState)
        private set
    var stablePageIndex: Int = 0
        private set

    fun commit(
        state: ContinuousDocumentState,
        pageIndex: Int = stablePageIndex,
    ): ContinuousDocumentState {
        stableState = clampContinuousDocumentState(state)
        stablePageIndex = pageIndex.coerceAtLeast(0)
        return stableState
    }

    fun reset(): ContinuousDocumentState {
        stableState = ContinuousDocumentState()
        stablePageIndex = 0
        return stableState
    }
}

data class ContinuousModeZoomState(
    val scale: Float,
    val qualityTier: QualityTier,
)

object ContinuousModeZoomContract {
    fun horizontalEntry(stableState: ContinuousDocumentState): ContinuousModeZoomState =
        verticalEntry(stableState)

    fun verticalEntry(stableState: ContinuousDocumentState): ContinuousModeZoomState {
        val scale = PdfRenderQuality.clampContinuousZoom(stableState.scale)
        return ContinuousModeZoomState(
            scale = scale,
            qualityTier = ContinuousQualityHysteresis.resolve(scale, QualityTier.PREVIEW),
        )
    }
}

fun clampContinuousDocumentState(state: ContinuousDocumentState): ContinuousDocumentState =
    state.copy(
        scale = state.scale.coerceIn(ContinuousDocumentGeometry.MIN_SCALE, MAX_CONTINUOUS_SCALE),
        scrollX = state.scrollX.coerceAtLeast(0f),
        scrollY = state.scrollY.coerceAtLeast(0f),
        gestureActive = false,
    )
