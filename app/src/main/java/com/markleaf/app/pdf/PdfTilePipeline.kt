package com.markleaf.app.pdf

import androidx.compose.ui.geometry.Offset
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

enum class TileInteractionPhase { IDLE, DRAGGING, PINCHING, SETTLING, FLINGING, DISABLED }

/**
 * Lightweight producer payload. It contains transform/viewport facts only — never a Bitmap and
 * never a suspend operation. Pointer input publishes this through latest-state conflation.
 */
data class PdfViewportSnapshot(
    val documentGeneration: Long,
    val geometryGeneration: Long,
    val viewportRectDocument: DocumentViewportRect,
    val scale: Float,
    val focalPointViewport: Offset?,
    val velocityX: Float,
    val velocityY: Float,
    val zoomVelocity: Float,
    val motionConfidence: Float,
    val interactionPhase: TileInteractionPhase,
    val timestampNanos: Long,
    val sequence: Long,
    val pointerDown: Boolean,
    val currentPage: Int,
    val rebaseFrame: Boolean = false,
)

data class PdfTilePlan(
    val snapshot: PdfViewportSnapshot,
    val requests: List<PdfTileRequest>,
    val pageSizes: Map<Int, PageLayoutSize>,
    val currentVisibleKeys: Set<PdfTileKey>,
)

data class TileOverlaySnapshot(
    val sequence: Long = 0L,
    val entries: Map<PdfTileKey, PdfTileEntry> = emptyMap(),
    val publishedAtNanos: Long = 0L,
)

/** Lock-free one-slot conflation used by the production viewport producer. */
class LatestViewportConflator<T> {
    private val latest = AtomicReference<T?>(null)
    private val offers = AtomicLong(0L)
    private val polls = AtomicLong(0L)

    fun offer(value: T) {
        latest.set(value)
        offers.incrementAndGet()
    }

    fun pollLatest(): T? = latest.getAndSet(null)?.also { polls.incrementAndGet() }
    fun hasPending(): Boolean = latest.get() != null
    fun offerCount(): Long = offers.get()
    fun pollCount(): Long = polls.get()
}

object TileRenderPipelinePolicy {
    const val PLANNER_THROTTLE_MS = 16L
    const val TILE_PUBLISH_BATCH_MS = 16L
    const val HEALTH_WINDOW_NANOS = 500_000_000L

    fun plannerAllowed(phase: TileInteractionPhase): Boolean =
        phase != TileInteractionPhase.DISABLED

    fun swapAllowed(phase: TileInteractionPhase): Boolean =
        phase != TileInteractionPhase.DISABLED

    fun isCurrentVisible(priority: TilePriority): Boolean =
        priority == TilePriority.FOCUS || priority == TilePriority.CENTER || priority == TilePriority.VISIBLE

    /** Current focal/center/visible always sorts before corridor and page-ahead work. */
    fun ordered(requests: List<PdfTileRequest>): List<PdfTileRequest> =
        requests.sortedWith(
            compareBy<PdfTileRequest> { it.priority.ordinal }
                .thenByDescending { it.snapshotSequence }
                .thenBy { it.requestedAtNanos },
        )

    /** A small move retains a key while the latest planner still considers it useful. */
    fun retainActive(previous: Set<PdfTileKey>, latestRequested: Set<PdfTileKey>): Set<PdfTileKey> =
        previous.intersect(latestRequested)

    fun requiresFullInvalidation(old: PdfTileKey, latest: PdfTileKey): Boolean =
        old.documentId != latest.documentId ||
            old.documentGeneration != latest.documentGeneration ||
            old.geometryGeneration != latest.geometryGeneration ||
            old.pageIndex != latest.pageIndex ||
            old.rotation != latest.rotation ||
            old.fittedPageWidthVersion != latest.fittedPageWidthVersion ||
            old.zoomBucket != latest.zoomBucket

    fun coverage(currentVisible: Set<PdfTileKey>, ready: Set<PdfTileKey>): Float =
        if (currentVisible.isEmpty()) 1f
        else currentVisible.count { it in ready }.toFloat() / currentVisible.size.toFloat()
}

data class GestureRenderHealthSnapshot(
    val pointerDown: Boolean,
    val plannerActive: Boolean,
    val rendererActive: Boolean,
    val requestsLast500ms: Int,
    val swapsLast500ms: Int,
    val currentCoverage: Float,
    val centerLatencyP50Ms: Long?,
    val centerLatencyP95Ms: Long?,
)

/** Pure timestamp accounting; callers decide when to log. */
class TileRenderHealthTracker {
    private val requestTimes = ArrayDeque<Long>()
    private val swapTimes = ArrayDeque<Long>()
    private val centerLatenciesMs = ArrayDeque<Long>()
    private var pointerDown = false
    private var coverage = 0f

    fun onViewport(snapshot: PdfViewportSnapshot, currentCoverage: Float) {
        pointerDown = snapshot.pointerDown
        coverage = currentCoverage.coerceIn(0f, 1f)
    }

    fun onRequests(count: Int, nowNanos: Long) {
        repeat(count.coerceAtLeast(0)) { requestTimes.addLast(nowNanos) }
        prune(nowNanos)
    }

    fun onSwap(nowNanos: Long, centerLatencyMs: Long? = null) {
        swapTimes.addLast(nowNanos)
        centerLatencyMs?.let {
            centerLatenciesMs.addLast(it)
            while (centerLatenciesMs.size > 256) centerLatenciesMs.removeFirst()
        }
        prune(nowNanos)
    }

    fun reset() {
        requestTimes.clear()
        swapTimes.clear()
        centerLatenciesMs.clear()
        pointerDown = false
        coverage = 0f
    }

    fun snapshot(nowNanos: Long, plannerActive: Boolean, rendererActive: Boolean): GestureRenderHealthSnapshot {
        prune(nowNanos)
        val sortedLatency = centerLatenciesMs.sorted()
        return GestureRenderHealthSnapshot(
            pointerDown = pointerDown,
            plannerActive = plannerActive,
            rendererActive = rendererActive,
            requestsLast500ms = requestTimes.size,
            swapsLast500ms = swapTimes.size,
            currentCoverage = coverage,
            centerLatencyP50Ms = percentile(sortedLatency, .50f),
            centerLatencyP95Ms = percentile(sortedLatency, .95f),
        )
    }

    private fun prune(nowNanos: Long) {
        val cutoff = nowNanos - TileRenderPipelinePolicy.HEALTH_WINDOW_NANOS
        while (requestTimes.firstOrNull()?.let { it < cutoff } == true) requestTimes.removeFirst()
        while (swapTimes.firstOrNull()?.let { it < cutoff } == true) swapTimes.removeFirst()
    }

    private fun percentile(sorted: List<Long>, percentile: Float): Long? {
        if (sorted.isEmpty()) return null
        val index = ((sorted.lastIndex) * percentile).toInt().coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }
}
