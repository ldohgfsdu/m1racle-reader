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
        val shared = ZoomPanPointerEpochReducer.frame(
            previous = previous?.toZoomPanPointerEpoch(),
            pointers = pointers.map { ZoomPanPointer(it.id, ZoomPanPoint(it.x, it.y)) },
            transform = ZoomPanTransform(
                scale = state.scale,
                legalOffsetPx = ZoomPanPoint(-state.scrollX, -state.scrollY),
            ),
            transformOriginViewportPx = ZoomPanPoint.Zero,
            boundsForScale = { scale ->
                geometry.zoomPanBounds(scale, viewportWidth, viewportHeight)
            },
            scaleRange = ZoomPanPhysicsProfile.ScaleRange,
            overscrollPolicy = ZoomPanPhysicsProfile.ContinuousOverscrollPolicy,
        )
        return PointerEpochFrame(
            epoch = shared.epoch?.toContinuousPointerEpoch(),
            state = state.copy(
                scale = shared.transform.scale,
                scrollX = -shared.transform.legalOffsetPx.x,
                scrollY = -shared.transform.legalOffsetPx.y,
            ),
            rebased = shared.rebased,
            shouldSettle = shared.shouldSettle,
        )
    }
}

private fun ContinuousPointerEpoch.toZoomPanPointerEpoch(): ZoomPanPointerEpoch =
    ZoomPanPointerEpoch(
        epochId = epochId,
        mode = when (mode) {
            PointerEpochMode.SINGLE_PAN -> ZoomPanPointerMode.SINGLE_PAN
            PointerEpochMode.TWO_POINTER_PINCH -> ZoomPanPointerMode.TWO_POINTER_PINCH
        },
        activePointerIds = activePointerIds,
        primaryPointerId = primaryPointerId,
        secondaryPointerId = secondaryPointerId,
        baseTransform = ZoomPanTransform(
            scale = baseScale,
            legalOffsetPx = ZoomPanPoint(-baseScrollX, -baseScrollY),
        ),
        baseCentroidPx = ZoomPanPoint(baseCentroidX, baseCentroidY),
        baseSpanPx = baseSpan,
        focalAnchor = ZoomPanFocalAnchor(focalDocumentX, focalDocumentY),
    )

private fun ZoomPanPointerEpoch.toContinuousPointerEpoch(): ContinuousPointerEpoch =
    ContinuousPointerEpoch(
        epochId = epochId,
        mode = when (mode) {
            ZoomPanPointerMode.SINGLE_PAN -> PointerEpochMode.SINGLE_PAN
            ZoomPanPointerMode.TWO_POINTER_PINCH -> PointerEpochMode.TWO_POINTER_PINCH
        },
        activePointerIds = activePointerIds,
        primaryPointerId = primaryPointerId,
        secondaryPointerId = secondaryPointerId,
        baseScale = baseTransform.scale,
        baseScrollX = -baseTransform.legalOffsetPx.x,
        baseScrollY = -baseTransform.legalOffsetPx.y,
        baseCentroidX = baseCentroidPx.x,
        baseCentroidY = baseCentroidPx.y,
        baseSpan = baseSpanPx,
        focalDocumentX = focalAnchor.contentX,
        focalDocumentY = focalAnchor.contentY,
    )

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
