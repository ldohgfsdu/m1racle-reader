package com.markleaf.app.pdf

enum class ContinuousInteractionPhase { IDLE, DRAGGING, PINCHING, SETTLING }

enum class PointerEpochMode { SINGLE_PAN, TWO_POINTER_PINCH }

data class PointerSample(val id: Long, val x: Float, val y: Float)

data class ContinuousPointerEpoch(
    val epochId: Long,
    val mode: PointerEpochMode,
    val activePointerIds: List<Long>,
    val primaryPointerId: Long?,
    val secondaryPointerId: Long?,
    val baseScale: Float,
    val baseScrollX: Float,
    val baseScrollY: Float,
    val baseCentroidX: Float,
    val baseCentroidY: Float,
    val baseSpan: Float,
    val focalDocumentX: Float,
    val focalDocumentY: Float,
)

data class PointerEpochFrame(
    val epoch: ContinuousPointerEpoch?,
    val state: ContinuousDocumentState,
    val rebased: Boolean,
    val shouldSettle: Boolean = false,
)

/** Pure pointer-set state machine. Pointer-set changes are baseline-only frames. */
object ContinuousPointerEpochReducer {
    fun frame(
        previous: ContinuousPointerEpoch?,
        pointers: List<PointerSample>,
        state: ContinuousDocumentState,
        viewportWidth: Float,
        viewportHeight: Float,
        geometry: ContinuousDocumentGeometry,
    ): PointerEpochFrame {
        if (pointers.isEmpty()) return PointerEpochFrame(null, state, rebased = false, shouldSettle = previous != null)
        val ids = pointers.map { it.id }.sorted()
        val pointerSetChanged = previous == null || previous.activePointerIds != ids
        val pairMissing = previous?.mode == PointerEpochMode.TWO_POINTER_PINCH &&
            (previous.primaryPointerId !in ids || previous.secondaryPointerId !in ids)
        if (pointerSetChanged || pairMissing) {
            return PointerEpochFrame(rebase(previous, pointers, state), state, rebased = true)
        }
        val epoch = previous ?: return PointerEpochFrame(rebase(null, pointers, state), state, rebased = true)
        val next = when (epoch.mode) {
            PointerEpochMode.SINGLE_PAN -> {
                val pointer = pointers.first { it.id == epoch.primaryPointerId }
                geometry.clamp(
                    state.copy(
                        scrollX = epoch.baseScrollX - (pointer.x - epoch.baseCentroidX),
                        scrollY = epoch.baseScrollY - (pointer.y - epoch.baseCentroidY),
                    ),
                    viewportWidth,
                    viewportHeight,
                )
            }
            PointerEpochMode.TWO_POINTER_PINCH -> {
                val a = pointers.first { it.id == epoch.primaryPointerId }
                val b = pointers.first { it.id == epoch.secondaryPointerId }
                val cx = (a.x + b.x) / 2f
                val cy = (a.y + b.y) / 2f
                val span = kotlin.math.hypot(a.x - b.x, a.y - b.y).coerceAtLeast(0.0001f)
                geometry.zoomAndPanFromFocal(
                    state = state,
                    newScale = epoch.baseScale * (span / epoch.baseSpan.coerceAtLeast(0.0001f)),
                    focalDocumentX = epoch.focalDocumentX,
                    focalDocumentY = epoch.focalDocumentY,
                    centroidX = cx,
                    centroidY = cy,
                    viewportWidth = viewportWidth,
                    viewportHeight = viewportHeight,
                )
            }
        }
        return PointerEpochFrame(epoch, next, rebased = false)
    }

    private fun rebase(
        previous: ContinuousPointerEpoch?,
        pointers: List<PointerSample>,
        state: ContinuousDocumentState,
    ): ContinuousPointerEpoch {
        val sorted = pointers.sortedBy { it.id }
        val ids = sorted.map { it.id }
        val priorPair = listOfNotNull(previous?.primaryPointerId, previous?.secondaryPointerId)
        val pair = if (sorted.size >= 2) {
            val retained = priorPair.filter { it in ids }
            (retained + ids.filterNot { it in retained }).take(2)
        } else listOf(sorted.first().id)
        val selected = sorted.filter { it.id in pair }
        val cx = selected.sumOf { it.x.toDouble() }.toFloat() / selected.size
        val cy = selected.sumOf { it.y.toDouble() }.toFloat() / selected.size
        val span = if (selected.size == 2) {
            kotlin.math.hypot(selected[0].x - selected[1].x, selected[0].y - selected[1].y)
        } else 0f
        val safeScale = state.scale.coerceAtLeast(0.0001f)
        return ContinuousPointerEpoch(
            epochId = (previous?.epochId ?: 0L) + 1L,
            mode = if (selected.size == 2) PointerEpochMode.TWO_POINTER_PINCH else PointerEpochMode.SINGLE_PAN,
            activePointerIds = ids,
            primaryPointerId = pair.firstOrNull(),
            secondaryPointerId = pair.getOrNull(1),
            baseScale = state.scale,
            baseScrollX = state.scrollX,
            baseScrollY = state.scrollY,
            baseCentroidX = cx,
            baseCentroidY = cy,
            baseSpan = span,
            focalDocumentX = (state.scrollX + cx) / safeScale,
            focalDocumentY = (state.scrollY + cy) / safeScale,
        )
    }
}

data class ContinuousInteractionTransaction(
    val interactionId: Long = 0L,
    val phase: ContinuousInteractionPhase = ContinuousInteractionPhase.IDLE,
    val stableVisiblePages: Set<Int> = emptySet(),
    val stableDisplayedBitmapIds: Map<Int, Int> = emptyMap(),
    val transientScale: Float = 1f,
    val transientScrollX: Float = 0f,
    val transientScrollY: Float = 0f,
    val lastGestureEndTimestamp: Long = 0L,
    val settleCommitCount: Int = 0,
    // P0B1: gesture baseline for focal-point-preserving transform
    val gestureId: Long = 0L,
    val pointerCount: Int = 0,
    val baseScale: Float = 1f,
    val baseScrollX: Float = 0f,
    val baseScrollY: Float = 0f,
    val baseCentroidX: Float = 0f,
    val baseCentroidY: Float = 0f,
    val focalDocumentX: Float = 0f,
    val focalDocumentY: Float = 0f,
)

object ContinuousInteractionReducer {
    const val SETTLE_DEBOUNCE_MS = 450L

    fun pinchStart(
        previous: ContinuousInteractionTransaction,
        visiblePages: Set<Int>,
        displayedBitmapIds: Map<Int, Int>,
        document: ContinuousDocumentState,
    ): ContinuousInteractionTransaction {
        val reuse = previous.phase == ContinuousInteractionPhase.SETTLING ||
            previous.phase == ContinuousInteractionPhase.PINCHING
        return if (reuse) {
            previous.copy(
                phase = ContinuousInteractionPhase.PINCHING,
                transientScale = document.scale,
                transientScrollX = document.scrollX,
                transientScrollY = document.scrollY,
            )
        } else {
            ContinuousInteractionTransaction(
                interactionId = previous.interactionId + 1L,
                phase = ContinuousInteractionPhase.PINCHING,
                stableVisiblePages = visiblePages,
                stableDisplayedBitmapIds = displayedBitmapIds,
                transientScale = document.scale,
                transientScrollX = document.scrollX,
                transientScrollY = document.scrollY,
                settleCommitCount = previous.settleCommitCount,
            )
        }
    }

    fun transform(
        transaction: ContinuousInteractionTransaction,
        document: ContinuousDocumentState,
    ): ContinuousInteractionTransaction = transaction.copy(
        transientScale = document.scale,
        transientScrollX = document.scrollX,
        transientScrollY = document.scrollY,
    )

    /** Existing pages are never removed during one transaction. */
    fun appendPages(
        transaction: ContinuousInteractionTransaction,
        necessaryPages: Set<Int>,
    ): ContinuousInteractionTransaction = transaction.copy(
        stableVisiblePages = transaction.stableVisiblePages + necessaryPages,
    )

    fun pinchEnd(
        transaction: ContinuousInteractionTransaction,
        document: ContinuousDocumentState,
        nowMillis: Long,
    ): ContinuousInteractionTransaction = transform(transaction, document).copy(
        phase = ContinuousInteractionPhase.SETTLING,
        lastGestureEndTimestamp = nowMillis,
    )

    fun canSettle(
        transaction: ContinuousInteractionTransaction,
        nowMillis: Long,
    ): Boolean = transaction.phase == ContinuousInteractionPhase.SETTLING &&
        nowMillis - transaction.lastGestureEndTimestamp >= SETTLE_DEBOUNCE_MS

    fun settleCommit(
        transaction: ContinuousInteractionTransaction,
        nowMillis: Long,
    ): ContinuousInteractionTransaction = if (canSettle(transaction, nowMillis)) {
        transaction.copy(
            phase = ContinuousInteractionPhase.IDLE,
            stableVisiblePages = emptySet(),
            stableDisplayedBitmapIds = emptyMap(),
            settleCommitCount = transaction.settleCommitCount + 1,
        )
    } else {
        transaction
    }

    /** Cancellation/exception path: leave no frozen pages or settling transaction behind. */
    fun cancel(transaction: ContinuousInteractionTransaction): ContinuousInteractionTransaction =
        transaction.copy(
            phase = ContinuousInteractionPhase.IDLE,
            stableVisiblePages = emptySet(),
            stableDisplayedBitmapIds = emptyMap(),
        )

    // ── P0B1: drag tracking + pointer rebase ──────────────────────────

    /** Start a 1-finger drag transaction (IDLE → DRAGGING). */
    fun dragStart(
        previous: ContinuousInteractionTransaction,
    ): ContinuousInteractionTransaction = if (previous.phase == ContinuousInteractionPhase.IDLE) {
        previous.copy(
            interactionId = previous.interactionId + 1L,
            phase = ContinuousInteractionPhase.DRAGGING,
            gestureId = previous.interactionId + 1L,
        )
    } else {
        previous
    }

    /** End a 1-finger drag transaction (DRAGGING → IDLE). */
    fun dragEnd(
        previous: ContinuousInteractionTransaction,
    ): ContinuousInteractionTransaction = if (previous.phase == ContinuousInteractionPhase.DRAGGING) {
        previous.copy(phase = ContinuousInteractionPhase.IDLE)
    } else {
        previous
    }

    /**
     * Rebase the gesture baseline on pointer count change.
     *
     * Recomputes focalDocumentX/Y from current transform + new centroid so that
     * the visual content under the new centroid stays fixed — no jump.
     * Does NOT change phase, does NOT settle, does NOT start a new transaction.
     */
    fun rebase(
        transaction: ContinuousInteractionTransaction,
        document: ContinuousDocumentState,
        centroidX: Float,
        centroidY: Float,
        pointerCount: Int,
    ): ContinuousInteractionTransaction {
        val safeScale = document.scale.coerceAtLeast(0.0001f)
        return transaction.copy(
            pointerCount = pointerCount,
            baseScale = document.scale,
            baseScrollX = document.scrollX,
            baseScrollY = document.scrollY,
            baseCentroidX = centroidX,
            baseCentroidY = centroidY,
            focalDocumentX = (document.scrollX + centroidX) / safeScale,
            focalDocumentY = (document.scrollY + centroidY) / safeScale,
        )
    }
}

/** UI-side displayed/pending model; a pending result never clears last-good content. */
data class StableBitmapSlot<T>(
    val displayed: T?,
    val pending: T? = null,
) {
    fun receive(value: T): StableBitmapSlot<T> = copy(pending = value)
    fun settle(): StableBitmapSlot<T> = if (pending == null) this else
        StableBitmapSlot(displayed = pending, pending = null)
}

// ── P0B1 display merge policy ──────────────────────────────────────

/** Tier + widthBucket metadata for a rendered page bitmap. */
data class BitmapMetadata(
    val tier: QualityTier,
    val widthBucket: Int,
)

/** A displayed bitmap with its quality metadata. */
data class DisplayedBitmap(
    val bitmap: Any,
    val metadata: BitmapMetadata,
)

/** Result of attempting to merge a new render result into display. */
enum class DisplayMergeResult { ACCEPT, REJECT, PENDING, SKIP }

/** Phase-aware display merge policy enforcing quality monotonicity. */
object DisplayMergePolicy {
    /** Returns >0 if a is higher quality, <0 if b is higher, 0 if equal. */
    fun compareQuality(a: BitmapMetadata, b: BitmapMetadata): Int = when {
        a.tier.ordinal != b.tier.ordinal -> a.tier.ordinal - b.tier.ordinal
        else -> a.widthBucket - b.widthBucket
    }

    /**
     * Decide whether a new render result should replace the currently displayed bitmap.
     *
     * Quality ordering: HIGH > MEDIUM > PREVIEW > SCROLL_PREVIEW > PLACEHOLDER(null)
     * Same tier: higher widthBucket wins.
     *
     * Phase rules:
     *  - IDLE/DRAGGING: accept upgrade or equal, reject downgrade, accept any for new page
     *  - PINCHING/SETTLING: freeze existing identity, queue as pending
     */
    fun merge(
        phase: ContinuousInteractionPhase,
        displayed: DisplayedBitmap?,
        newBitmap: Any,
        newMetadata: BitmapMetadata,
    ): DisplayMergeResult {
        // Same bitmap object: no change needed
        if (displayed?.bitmap === newBitmap) return DisplayMergeResult.SKIP

        // PINCHING/SETTLING: freeze both displayed identity and the composed page set.
        // This includes results for pages that did not have a displayed bitmap at
        // interaction start: accepting those would silently expand composition.
        if (phase == ContinuousInteractionPhase.PINCHING ||
            phase == ContinuousInteractionPhase.SETTLING
        ) return DisplayMergeResult.PENDING

        // IDLE/DRAGGING may accept a newly entering page at any tier.
        if (displayed == null) return DisplayMergeResult.ACCEPT

        // IDLE/DRAGGING: enforce quality monotonicity
        val comparison = compareQuality(newMetadata, displayed.metadata)
        return if (comparison >= 0) DisplayMergeResult.ACCEPT else DisplayMergeResult.REJECT
    }

    /**
     * Settle commit: pick best-of(displayed, pending) per page.
     * Never downgrades displayed; only upgrades or keeps.
     */
    fun settle(
        displayed: DisplayedBitmap?,
        pending: DisplayedBitmap?,
    ): DisplayedBitmap? {
        if (pending == null) return displayed
        if (displayed == null) return pending
        return if (compareQuality(pending.metadata, displayed.metadata) >= 0) pending else displayed
    }

    /** Accumulate only the best pending result for one page. */
    fun bestPending(
        current: DisplayedBitmap?,
        candidate: DisplayedBitmap,
    ): DisplayedBitmap = settle(current, candidate) ?: candidate

    /**
     * Atomically computes the post-settle display map for pages still required by
     * composition. Stale pending pages never force themselves into composition.
     */
    fun settleCommit(
        displayed: Map<Int, DisplayedBitmap>,
        pending: Map<Int, DisplayedBitmap>,
        requiredPages: Set<Int>,
    ): Map<Int, DisplayedBitmap> = buildMap {
        for (page in requiredPages) {
            settle(displayed[page], pending[page])?.let { put(page, it) }
        }
    }
}
