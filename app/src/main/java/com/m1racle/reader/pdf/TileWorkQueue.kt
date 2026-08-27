package com.m1racle.reader.pdf

import androidx.compose.ui.graphics.ImageBitmap

/**
 * P0A lifecycle hardening: bounded, generation-aware tile work queue with a
 * serial worker loop that survives STALE_GENERATION / OUTSIDE_ACTIVE_REGION
 * without exiting.
 *
 * This abstraction replaces the inline worker previously embedded in
 * [com.m1racle.reader.ui.pdf.PdfViewerViewModel.requestPredictiveTiles]. The
 * pre-hardening worker used `return@launch` on STALE_GENERATION and
 * OUTSIDE_ACTIVE_REGION, which exited the entire coroutine and left
 * subsequent pending tiles (B, C, …) unprocessed in `tilePending`. The fix
 * is to `continue` instead of `return@launch`, release the `running` flag
 * in a `finally` block, and never clear `tilePending` from `finally`.
 *
 * Concurrency model:
 * - All public state (tilePending, activeTileKeys, documentGeneration,
 *   running) is touched only on the VM's main dispatcher between suspend
 *   points. The only suspend point inside [runWorker] is [renderFn], which
 *   the caller routes through [PdfPageRenderer] (strictly serial via its
 *   internal mutex).
 *
 * PdfRenderer remains strictly serial: [runWorker] awaits each [renderFn]
 * before pulling the next pending entry, and [PdfPageRenderer.renderTile]
 * holds its own IO mutex.
 *
 * ## P0A1 dispatcher confinement (read-only audit, no rewrite)
 *
 * Confirmed call sites in [com.m1racle.reader.ui.pdf.PdfViewerViewModel]:
 * - [submit] is called from `requestPredictiveTiles`, a plain fun invoked
 *   from a Compose callback → Main thread.
 * - [runWorker] is called inside `viewModelScope.launch { ... }` →
 *   Dispatchers.Main.immediate. The only suspend point inside [runWorker]
 *   is [renderFn]; all `tilePending` / `activeTileKeys` / `documentGeneration`
 *   / `running` reads and writes happen on Main.immediate between suspend
 *   points. `renderFn` switches to IO internally (PdfPageRenderer mutex);
 *   on resume, control returns to Main.immediate before the next iteration
 *   touches queue state.
 * - [updateDocumentGeneration] and [clear] are called inside
 *   `viewModelScope.launch { ... }` (openDocument reset path) →
 *   Dispatchers.Main.immediate.
 * - [pendingKeys] / [pendingSize] / [activeKeys] / [currentGeneration] are
 *   read-only and called from Main-thread funs or inside the same
 *   viewModelScope.launch block.
 *
 * Conclusion: all mutable-state touches are confined to a single dispatcher
 * (Main.immediate). No internal synchronization is required. The `@Volatile`
 * on [running] is retained only as a cheap cross-thread visibility hint for
 * any future reader; it is not load-bearing under the current confinement.
 *
 * This is an audit-only annotation. TileWorkQueue is NOT rewritten this round.
 */
class TileWorkQueue {

    /** Render outcome mirroring [com.m1racle.reader.pdf.PdfPageRenderer.TileRenderResult]. */
    sealed class RenderOutcome {
        data class Success(val image: ImageBitmap, val byteSize: Long) : RenderOutcome()
        data object Closed : RenderOutcome()
        data object InvalidPage : RenderOutcome()
        data class Failed(val message: String) : RenderOutcome()
    }

    enum class DropReason {
        STALE_DOCUMENT,
        STALE_GEOMETRY,
        OUTSIDE_ACTIVE_REGION,
        NOT_READY,
        RENDER_FAILED,
    }

    /** One queued tile request with the page geometry snapshot needed by the renderer. */
    data class PendingTile(
        val request: PdfTileRequest,
        val pageSize: PageLayoutSize,
        val phase: String,
    )

    data class SubmitResult(
        val enqueuedKeys: Set<PdfTileKey>,
        val pendingSize: Int,
        val activeKeys: Set<PdfTileKey>,
    )

    private val tilePending = linkedMapOf<PdfTileKey, PendingTile>()
    private var activeTileKeys: Set<PdfTileKey> = emptySet()
    private var documentGeneration: Long = 0L
    private var runningTileKey: PdfTileKey? = null
    @Volatile private var running: Boolean = false

    fun pendingSize(): Int = tilePending.size
    fun pendingKeys(): Set<PdfTileKey> = tilePending.keys.toSet()
    fun isRunning(): Boolean = running
    fun activeKeys(): Set<PdfTileKey> = activeTileKeys
    fun currentGeneration(): Long = documentGeneration

    /** Set the initial generation. Does NOT drop pending (use [updateDocumentGeneration] for that). */
    fun setDocumentGeneration(gen: Long) {
        documentGeneration = gen
    }

    /**
     * Roll the document generation forward. Old-generation pending requests
     * are dropped (they would be STALE_GENERATION at apply time anyway, but
     * dropping them now avoids wasted renderer cycles). New-generation
     * requests are unaffected (there are none yet at roll time; subsequent
     * [submit] calls carry the new generation in their [PdfTileKey]s).
     */
    fun updateDocumentGeneration(newGen: Long) {
        if (newGen == documentGeneration) return
        val staleKeys = tilePending.keys.filter { it.documentGeneration != newGen }
        staleKeys.forEach { tilePending.remove(it) }
        documentGeneration = newGen
    }

    /**
     * Bounded admission. Mirrors the original VM policy:
     * - current visible (FOCUS..VISIBLE) capped at 20
     * - leading/near corridor capped at 8
     * - page-ahead capped at 4
     * - total bounded at 32
     * - up to 8 previous active keys survive when they are still in the latest planner region;
     *   this prevents a small viewport move from invalidating useful in-flight work
     * - same-key priority is replaced with the latest planner priority
     *
     * [cacheHitKeys] excludes tiles already in the LRU; they are not re-queued.
     */
    fun submit(
        requests: List<PdfTileRequest>,
        pageSizes: Map<Int, PageLayoutSize>,
        interactionPhase: String,
        cacheHitKeys: Set<PdfTileKey> = emptySet(),
    ): SubmitResult {
        val unique = TileRenderPipelinePolicy.ordered(requests.distinctBy { it.key })
        val currentVisible = unique.filter {
            TileRenderPipelinePolicy.isCurrentVisible(it.priority)
        }.take(20)
        val leadingAndNear = unique.filter {
            it.priority == TilePriority.LEADING_EDGE || it.priority == TilePriority.NEAR
        }.take(8)
        val pageAhead = unique.filter { it.priority == TilePriority.PAGE_AHEAD }.take(4)
        val bounded = (currentVisible + leadingAndNear + pageAhead + unique)
            .distinctBy { it.key }
            .take(32)
        val latestRequestedKeys = unique.mapTo(linkedSetOf()) { it.key }
        val retained = TileRenderPipelinePolicy.retainActive(activeTileKeys, latestRequestedKeys)
            .filter { retainedKey -> bounded.none { it.key == retainedKey } }
            .take(8)
        activeTileKeys = (bounded.map { it.key } + retained).toCollection(linkedSetOf())
        // Drop only work outside the latest region plus hysteresis set. Document-generation
        // invalidation remains owned by updateDocumentGeneration.
        tilePending.keys
            .filter { it !in activeTileKeys && it.documentGeneration == documentGeneration }
            .forEach { tilePending.remove(it) }
        val latestByKey = unique.associateBy { it.key }
        val enqueued = linkedSetOf<PdfTileKey>()
        activeTileKeys.forEach { key ->
            // The serial renderer already owns this key. A new viewport snapshot may keep it
            // active, but must never enqueue a duplicate render behind the in-flight one.
            if (key == runningTileKey) return@forEach
            if (key in cacheHitKeys) {
                tilePending.remove(key)
                return@forEach
            }
            val request = latestByKey[key] ?: return@forEach
            val size = pageSizes[request.key.pageIndex] ?: return@forEach
            val next = PendingTile(request, size, interactionPhase)
            if (tilePending[request.key] == null) enqueued += request.key
            tilePending[request.key] = next
        }
        return SubmitResult(enqueued, tilePending.size, activeTileKeys)
    }

    /** Clear all queue state. Used on document open/close. */
    fun clear() {
        tilePending.clear()
        activeTileKeys = emptySet()
    }

    /**
     * Run the serial worker loop until [tilePending] is empty.
     *
     * Contract:
     * - STALE_GENERATION → drop current tile, `continue` (do NOT exit worker)
     * - OUTSIDE_ACTIVE_REGION → drop current tile, `continue`
     * - [RenderOutcome.Failed]/Closed/InvalidPage → drop current tile, `continue`
     * - [isReady] == false → drop current tile, `continue`
     * - exception propagates; `finally` releases the `running` flag and
     *   NEVER clears [tilePending]
     * - if the worker exits with pending still present (e.g. [isReady]
     *   returned false for every item), a subsequent [runWorker] call is
     *   safe and will resume draining
     *
     * [renderFn] must be serial (PdfRenderer constraint). The caller is
     * responsible for routing it through [PdfPageRenderer.renderTile].
     */
    suspend fun runWorker(
        renderFn: suspend (PdfTileRequest, PageLayoutSize) -> RenderOutcome,
        onTileReady: (PdfTileEntry) -> Unit,
        onTileDropped: (PdfTileKey, DropReason) -> Unit = { _, _ -> },
        isReady: () -> Boolean = { true },
    ) {
        if (running) return
        running = true
        try {
            while (tilePending.isNotEmpty()) {
                val pending = tilePending.values.minByOrNull { it.request.priority.ordinal } ?: break
                tilePending.remove(pending.request.key)
                val request = pending.request
                val renderStartNanos = System.nanoTime()
                runningTileKey = request.key
                try {
                    when (val result = renderFn(request, pending.pageSize)) {
                    is RenderOutcome.Success -> {
                        // STALE_GENERATION: drop this tile, continue the loop.
                        if (request.key.documentGeneration != documentGeneration) {
                            onTileDropped(request.key, DropReason.STALE_DOCUMENT)
                            continue
                        }
                        // A base-page aspect/fitted-width change invalidates every old tile,
                        // including an in-flight render that completed after the new geometry.
                        val activeGeometryGenerations = activeTileKeys.mapTo(mutableSetOf()) {
                            it.geometryGeneration
                        }
                        if (request.key.geometryGeneration !in activeGeometryGenerations) {
                            onTileDropped(request.key, DropReason.STALE_GEOMETRY)
                            continue
                        }
                        // OUTSIDE_ACTIVE_REGION: drop this tile, continue.
                        if (request.key !in activeTileKeys) {
                            onTileDropped(request.key, DropReason.OUTSIDE_ACTIVE_REGION)
                            continue
                        }
                        // VM no longer Ready (document closing, etc.): drop, continue.
                        if (!isReady()) {
                            onTileDropped(request.key, DropReason.NOT_READY)
                            continue
                        }
                        val readyNanos = System.nanoTime()
                        val entry = PdfTileEntry(
                            key = request.key,
                            bitmap = result.image,
                            pageRect = request.pageRect,
                            contentRectPx = request.contentRectPx,
                            renderedAtNanos = readyNanos,
                            requestGeneration = request.requestGeneration,
                            byteSize = result.byteSize,
                            snapshotSequence = request.snapshotSequence,
                            targetedAtNanos = request.targetedAtNanos,
                            requestTimeNanos = request.requestedAtNanos,
                            renderStartNanos = renderStartNanos,
                            interactionPhaseAtRequest = runCatching {
                                TileInteractionPhase.valueOf(pending.phase)
                            }.getOrDefault(TileInteractionPhase.IDLE),
                            priorityAtRequest = request.priority,
                        )
                        onTileReady(entry)
                    }
                    is RenderOutcome.Closed,
                    is RenderOutcome.InvalidPage,
                    is RenderOutcome.Failed -> {
                        // Render failed for this tile: drop and continue.
                        onTileDropped(request.key, DropReason.RENDER_FAILED)
                        continue
                    }
                    }
                } finally {
                    runningTileKey = null
                }
            }
        } finally {
            // Release ownership ONLY. Never clear pending — a future submit
            // or restart must still see valid pending entries.
            running = false
            runningTileKey = null
        }
    }
}
