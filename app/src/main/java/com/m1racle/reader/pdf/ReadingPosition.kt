package com.m1racle.reader.pdf

/** Source and priority of a logical PDF reading position. */
enum class ReadingPositionSource {
    USER_HORIZONTAL_SETTLED,
    USER_CONTINUOUS_SCROLL,
    STARTUP_RESTORE,
    ZOOM_SESSION_RESTORE,
    MODE_SWITCH_RESTORE,
    EXTERNAL_NAVIGATION,
    INITIAL_PLACEHOLDER,
}

/**
 * Device-independent PDF reading position. Page indexes are always internal 0-based
 * values; [intraPageFraction] anchors within that page instead of storing raw scrollY.
 */
data class ReadingPosition(
    val pageIndex: Int,
    val intraPageFraction: Float,
    val source: ReadingPositionSource,
    val generation: Long,
) {
    fun normalized(pageCount: Int): ReadingPosition = copy(
        pageIndex = PdfReadingNavigation.clampPageIndex(pageIndex, pageCount),
        intraPageFraction = intraPageFraction.coerceIn(0f, 1f),
    )
}

enum class ContinuousEntryPhase {
    WAITING_TARGET,
    WAITING_GEOMETRY,
    APPLYING_TARGET,
    READY,
}

data class ContinuousEntryState(
    val phase: ContinuousEntryPhase = ContinuousEntryPhase.WAITING_TARGET,
    val pendingRestorePosition: ReadingPosition? = null,
    val lastStableReadingPosition: ReadingPosition? = null,
)

/** Priority and persistence predicates shared by the VM and deterministic JVM tests. */
object ReadingPositionPolicy {
    fun priority(source: ReadingPositionSource): Int = when (source) {
        ReadingPositionSource.EXTERNAL_NAVIGATION -> 5
        ReadingPositionSource.MODE_SWITCH_RESTORE -> 4
        ReadingPositionSource.ZOOM_SESSION_RESTORE -> 3
        ReadingPositionSource.STARTUP_RESTORE -> 2
        ReadingPositionSource.INITIAL_PLACEHOLDER -> 1
        ReadingPositionSource.USER_HORIZONTAL_SETTLED,
        ReadingPositionSource.USER_CONTINUOUS_SCROLL,
        -> 0
    }

    fun canPersistReadingPosition(
        phase: ContinuousEntryPhase,
        pendingRestorePosition: ReadingPosition?,
        geometryReady: Boolean,
        position: ReadingPosition?,
    ): Boolean =
        phase == ContinuousEntryPhase.READY &&
            pendingRestorePosition == null &&
            geometryReady &&
            position != null &&
            position.source != ReadingPositionSource.INITIAL_PLACEHOLDER
}

/**
 * Single-owner entry state machine. It deliberately keeps pre-entry [lastStable] so
 * an onStop/dispose during restoration flushes the prior real position, never page 0.
 *
 * After [completeRestore] or [cancelRestore], exactly one of the following transitions
 * is atomic and idempotent:
 *
 * - [completeRestore]: phase → READY, pending → null, lastStable → restored position
 * - [cancelRestore]:  phase → READY, pending → null, preserves lastStable
 *
 * Every restore [requestRestore] bumps a monotonic [generation]. The generation that
 * actually completes or is cancelled is recorded in [consumedRestoreGeneration] so
 * re-entry or stale callbacks cannot replay.
 */
class ContinuousEntryRestoreState(
    initialGeneration: Long = 0L,
) {
    private var generation = initialGeneration
    private var phase = ContinuousEntryPhase.WAITING_TARGET
    private var pending: ReadingPosition? = null
    private var lastStable: ReadingPosition? = null
    /** Non-null after exactly one [completeRestore] or [cancelRestore] on a generation. */
    private var consumedRestoreGeneration: Long? = null

    fun snapshot(): ContinuousEntryState = ContinuousEntryState(phase, pending, lastStable)

    /** True once the state machine has entered READY and all suppression is released. */
    fun isReady(): Boolean = phase == ContinuousEntryPhase.READY

    /** Generation that was consumed; null if no restore has completed or been cancelled yet. */
    fun getConsumedGeneration(): Long? = consumedRestoreGeneration

    /** Seeds persisted history for safe flush; it does not make the viewport READY. */
    fun seedLastStable(position: ReadingPosition, pageCount: Int) {
        lastStable = position.normalized(pageCount)
    }

    /** Horizontal mode has no continuous geometry gate but still needs stable progress. */
    fun markReadyWithoutRestore() {
        if (pending == null) {
            phase = ContinuousEntryPhase.READY
            consumedRestoreGeneration = null // no restore was queued
        }
    }

    /** Queues a new restore only when it is not lower priority than the pending one. */
    fun requestRestore(
        pageIndex: Int,
        intraPageFraction: Float,
        source: ReadingPositionSource,
        pageCount: Int,
    ): ReadingPosition? {
        require(source == ReadingPositionSource.STARTUP_RESTORE ||
            source == ReadingPositionSource.MODE_SWITCH_RESTORE ||
            source == ReadingPositionSource.ZOOM_SESSION_RESTORE ||
            source == ReadingPositionSource.EXTERNAL_NAVIGATION ||
            source == ReadingPositionSource.INITIAL_PLACEHOLDER)
        val current = pending
        if (current != null && ReadingPositionPolicy.priority(source) < ReadingPositionPolicy.priority(current.source)) {
            return null
        }
        val request = ReadingPosition(
            pageIndex = pageIndex,
            intraPageFraction = intraPageFraction,
            source = source,
            generation = ++generation,
        ).normalized(pageCount)
        pending = request
        phase = ContinuousEntryPhase.WAITING_GEOMETRY
        consumedRestoreGeneration = null // new request resets consumed flag
        return request
    }

    /** Geometry is ready: keep the request active and hand it to the viewport once. */
    fun beginApplyIfReady(geometryReady: Boolean): ReadingPosition? {
        val request = pending ?: return null
        if (!geometryReady) return null
        phase = ContinuousEntryPhase.APPLYING_TARGET
        return request
    }

    /**
     * Atomic, idempotent READY transition.
     *
     * On success (returns true), the state machine performs exactly:
     * 1. phase = READY
     * 2. pending = null
     * 3. consumedRestoreGeneration = generation
     * 4. lastStable = restored position (with request source)
     *
     * A given generation can only complete once; stale callbacks return false.
     */
    fun completeRestore(
        generation: Long,
        restoredPosition: ReadingPosition,
        pageCount: Int,
    ): Boolean {
        val request = pending ?: return false
        // Idempotency: this generation was already consumed.
        if (consumedRestoreGeneration == generation) return false
        // Only the currently pending generation in APPLYING_TARGET may complete.
        if (request.generation != generation || phase != ContinuousEntryPhase.APPLYING_TARGET) return false
        lastStable = restoredPosition.copy(
            source = request.source,
            generation = request.generation,
        ).normalized(pageCount)
        pending = null
        consumedRestoreGeneration = generation
        phase = ContinuousEntryPhase.READY
        return true
    }

    /**
     * User-scroll escape hatch. Consumes the pending restore (if any), preserves
     * [lastStable], and transitions atomically to READY.
     *
     * Safe to call from any phase; idempotent when already READY. Does NOT update
     * [lastStable] — the caller must record the user's actual viewport position
     * separately via [recordUserStable] after this returns true.
     */
    fun cancelRestore(): Boolean {
        if (phase == ContinuousEntryPhase.READY) return false // already ready
        val request = pending
        if (request != null) {
            consumedRestoreGeneration = request.generation
        }
        pending = null
        phase = ContinuousEntryPhase.READY
        return true
    }

    /** User settles are authoritative only after entry restoration has completed. */
    fun recordUserStable(
        pageIndex: Int,
        intraPageFraction: Float,
        source: ReadingPositionSource,
        pageCount: Int,
    ): ReadingPosition? {
        if (phase != ContinuousEntryPhase.READY) return null
        require(source == ReadingPositionSource.USER_HORIZONTAL_SETTLED ||
            source == ReadingPositionSource.USER_CONTINUOUS_SCROLL ||
            source == ReadingPositionSource.EXTERNAL_NAVIGATION)
        val position = ReadingPosition(
            pageIndex = pageIndex,
            intraPageFraction = intraPageFraction,
            source = source,
            generation = ++generation,
        ).normalized(pageCount)
        lastStable = position
        return position
    }

    fun canPersist(geometryReady: Boolean): Boolean = ReadingPositionPolicy.canPersistReadingPosition(
        phase = phase,
        pendingRestorePosition = pending,
        geometryReady = geometryReady,
        position = lastStable,
    )
}

/** Encodes fractions in the existing PDF-only lastScrollOffset column without a schema change. */
object PdfReadingPositionCodec {
    private const val FRACTION_SCALE = 1_000_000

    fun encodeFraction(fraction: Float): Int =
        (fraction.coerceIn(0f, 1f) * FRACTION_SCALE).toInt()

    fun decodeFraction(encoded: Int): Float =
        (encoded.coerceAtLeast(0).toFloat() / FRACTION_SCALE).coerceIn(0f, 1f)
}
