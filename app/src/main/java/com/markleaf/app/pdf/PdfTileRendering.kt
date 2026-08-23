package com.markleaf.app.pdf

import androidx.compose.ui.graphics.ImageBitmap
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A rectangle in the fitted page's scale-1 layout coordinate space. */
data class PageLayoutRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    fun intersects(other: PageLayoutRect): Boolean =
        left < other.right && right > other.left && top < other.bottom && bottom > other.top

    /** Round edges, not origin + size, so adjacent destinations cannot open a 1 px gap. */
    fun roundedPixelRect(): TilePixelRect = TilePixelRect(
        left = left.roundToInt(),
        top = top.roundToInt(),
        right = right.roundToInt(),
        bottom = bottom.roundToInt(),
    )
}

/** A rectangle in scale-1 document coordinates, before the document transform. */
data class DocumentViewportRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
}

/** A rectangle in the PdfRenderer page coordinate space. */
data class PdfPageRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
}

/** An integer rectangle in an output tile bitmap. */
data class TilePixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
}

data class TilePixelInsets(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    init {
        require(left >= 0 && top >= 0 && right >= 0 && bottom >= 0)
    }
}

data class PageLayoutSize(val width: Float, val height: Float) {
    init {
        require(width > 0f && height > 0f)
    }
}

data class PdfPageSize(val width: Int, val height: Int) {
    init {
        require(width > 0 && height > 0)
    }
}

/**
 * Immutable cache identity for one detail tile.
 *
 * Geometry identity is intentionally independent of request generation. A tile rendered for an
 * old fitted width/aspect must never be reused after the base page adopts new geometry.
 */
data class PdfTileKey(
    val documentId: String,
    val documentGeneration: Long,
    val geometryGeneration: Long,
    val pageIndex: Int,
    val rotation: Int,
    val fittedPageWidthVersion: Int,
    val zoomBucket: Int,
    val tileX: Int,
    val tileY: Int,
)

/**
 * Ready tile metadata.
 *
 * Coordinate contract: [pageRect] is always UNSCALED_PAGE_LOCAL_COORDINATES:
 * - origin is the current fitted PDF page's top-left;
 * - units are the page layout's scale-1 local units;
 * - it contains no viewport offset, document scroll, Compose scale, zoom-bucket pixels, output
 *   bitmap pixels, or bleed.
 */
data class PdfTileEntry(
    val key: PdfTileKey,
    val bitmap: ImageBitmap,
    val pageRect: PageLayoutRect,
    val contentRectPx: TilePixelRect,
    val renderedAtNanos: Long,
    val requestGeneration: Long,
    val byteSize: Long,
    val snapshotSequence: Long = 0L,
    val targetedAtNanos: Long = 0L,
    val requestTimeNanos: Long = 0L,
    val renderStartNanos: Long = 0L,
    val interactionPhaseAtRequest: TileInteractionPhase = TileInteractionPhase.IDLE,
    val priorityAtRequest: TilePriority = TilePriority.VISIBLE,
)

enum class TilePriority { FOCUS, CENTER, VISIBLE, LEADING_EDGE, NEAR, PAGE_AHEAD, MARGIN, SPECULATIVE }

data class PdfTileRequest(
    val key: PdfTileKey,
    /** See [PdfTileEntry.pageRect]. */
    val pageRect: PageLayoutRect,
    /** target tile pixels per scale-1 page-local unit. */
    val renderScale: Float,
    val contentWidthPx: Int,
    val contentHeightPx: Int,
    val bleedPx: TilePixelInsets,
    val priority: TilePriority,
    val requestGeneration: Long,
    val requestedAtNanos: Long,
    val snapshotSequence: Long = 0L,
    val targetedAtNanos: Long = requestedAtNanos,
) {
    val outputWidth: Int get() = contentWidthPx + bleedPx.left + bleedPx.right
    val outputHeight: Int get() = contentHeightPx + bleedPx.top + bleedPx.bottom
    val contentRectPx: TilePixelRect
        get() = TilePixelRect(
            bleedPx.left,
            bleedPx.top,
            bleedPx.left + contentWidthPx,
            bleedPx.top + contentHeightPx,
        )
}

data class PageTileViewport(
    val pageIndex: Int,
    val pageWidth: Float,
    val pageHeight: Float,
    val visibleRect: PageLayoutRect,
    val geometryGeneration: Long = pageGeometryGeneration(pageWidth, pageHeight, rotation = 0),
    val fittedPageWidthVersion: Int = pageWidth.roundToInt(),
    val rotation: Int = 0,
) {
    val pageSize: PageLayoutSize get() = PageLayoutSize(pageWidth, pageHeight)
}

enum class TileNavigationDirection { IDLE, FORWARD, BACKWARD }

data class ViewportMotionEstimate(
    val velocityXDocumentPxPerSec: Float = 0f,
    val velocityYDocumentPxPerSec: Float = 0f,
    val zoomVelocityPerSec: Float = 0f,
    val direction: TileNavigationDirection = TileNavigationDirection.IDLE,
    val confidence: Float = 0f,
    val timestampNanos: Long = 0L,
    val lastRect: DocumentViewportRect? = null,
)

data class PrefetchCorridor(
    val current: DocumentViewportRect,
    val near: DocumentViewportRect,
    val far: DocumentViewportRect,
    val direction: TileNavigationDirection,
    val confidence: Float,
)

/** Deterministic page-local geometry identity. */
fun pageGeometryGeneration(pageWidth: Float, pageHeight: Float, rotation: Int): Long {
    var hash = 0xcbf29ce484222325UL
    fun mix(value: Int) {
        hash = (hash xor value.toUInt().toULong()) * 0x100000001b3UL
    }
    mix(pageWidth.toBits())
    mix(pageHeight.toBits())
    mix(rotation)
    return hash.toLong()
}

object PdfViewportMotion {
    fun update(
        old: ViewportMotionEstimate,
        rect: DocumentViewportRect,
        scale: Float,
        oldScale: Float,
        nanos: Long,
        rebased: Boolean = false,
    ): ViewportMotionEstimate {
        if (rebased || old.timestampNanos == 0L) return old.copy(timestampNanos = nanos, lastRect = rect)
        val dt = (nanos - old.timestampNanos) / 1e9f
        if (dt !in .005f..1f) return old.copy(timestampNanos = nanos, lastRect = rect)
        val previous = old.lastRect ?: rect
        val rawX = (rect.left - previous.left) / dt
        val rawY = (rect.top - previous.top) / dt
        val rawZoom = (scale - oldScale) / dt
        if (abs(rawX) > 30_000 || abs(rawY) > 30_000 || abs(rawZoom) > 20) {
            return old.copy(timestampNanos = nanos, lastRect = rect)
        }
        val velocityX = old.velocityXDocumentPxPerSec * .65f + rawX * .35f
        val velocityY = old.velocityYDocumentPxPerSec * .65f + rawY * .35f
        val direction = when {
            abs(velocityY) < 40 -> TileNavigationDirection.IDLE
            velocityY > 0 -> TileNavigationDirection.FORWARD
            else -> TileNavigationDirection.BACKWARD
        }
        val reversed = old.direction != TileNavigationDirection.IDLE &&
            direction != TileNavigationDirection.IDLE && old.direction != direction
        val confidence = when {
            direction == TileNavigationDirection.IDLE -> old.confidence * .7f
            reversed -> .25f
            else -> (old.confidence + .2f).coerceAtMost(1f)
        }
        return ViewportMotionEstimate(
            velocityX,
            velocityY,
            old.zoomVelocityPerSec * .7f + rawZoom * .3f,
            direction,
            confidence,
            nanos,
            rect,
        )
    }

    fun corridor(rect: DocumentViewportRect, motion: ViewportMotionEstimate): PrefetchCorridor {
        fun shifted(seconds: Float) = DocumentViewportRect(
            rect.left + motion.velocityXDocumentPxPerSec * seconds,
            rect.top + motion.velocityYDocumentPxPerSec * seconds,
            rect.right + motion.velocityXDocumentPxPerSec * seconds,
            rect.bottom + motion.velocityYDocumentPxPerSec * seconds,
        )
        return PrefetchCorridor(rect, shifted(.1f), shifted(.24f), motion.direction, motion.confidence)
    }
}

object PdfZoomBuckets {
    val scales = floatArrayOf(1f, 1.4f, 2f, 2.8f, 4f)

    fun index(scale: Float, previous: Int? = null): Int {
        val safeScale = scale.coerceIn(1f, 4f)
        val nearest = scales.indices.minBy { abs(scales[it] - safeScale) }
        val previousBucket = previous ?: return nearest
        if (previousBucket !in scales.indices) return nearest
        val midpoint = (scales[previousBucket] + scales[nearest]) / 2f
        return if (
            nearest > previousBucket && safeScale < midpoint * 1.04f ||
            nearest < previousBucket && safeScale > midpoint * .96f
        ) previousBucket else nearest
    }
}

/**
 * On-screen tile coverage across zoom-bucket changes.
 *
 * Tile grids differ per bucket, so last-good coverage cannot be matched by
 * tileX/tileY. Draw every ready tile for the page, low-res first, so a live
 * bucket jump never opens a hole of upsampled 1× text (the white-glyph flash).
 */
object PdfTileDisplay {
    fun forPage(
        tiles: Collection<PdfTileEntry>,
        pageIndex: Int,
    ): List<PdfTileEntry> = tiles.filter { it.key.pageIndex == pageIndex }
        .sortedWith(
            compareBy<PdfTileEntry> { it.key.zoomBucket }
                .thenBy { it.key.tileY }
                .thenBy { it.key.tileX },
        )

    /**
     * Live pinch/zoom requests the current and predicted buckets. At 1×, also
     * preheat the next sharper bucket so the first upsample is already covered.
     */
    fun bucketsToRequest(
        scale: Float,
        currentBucket: Int,
        targetBucket: Int,
        pinching: Boolean,
    ): List<Int> {
        val last = PdfZoomBuckets.scales.lastIndex
        val current = currentBucket.coerceIn(0, last)
        val target = targetBucket.coerceIn(0, last)
        val buckets = linkedSetOf<Int>()
        val live = scale > 1.01f || pinching
        if (live) {
            buckets += current
            buckets += target
        }
        if (!live || current == 0) {
            buckets += (current + 1).coerceAtMost(last)
        }
        return buckets.toList()
    }
}

data class ZoomPrediction(
    val velocity: Float = 0f,
    val lastScale: Float = 1f,
    val lastNanos: Long = 0,
    val direction: Int = 0,
)

object PdfZoomPredictor {
    fun update(old: ZoomPrediction, scale: Float, nanos: Long, rebased: Boolean): ZoomPrediction {
        if (rebased || old.lastNanos == 0L) return old.copy(lastScale = scale, lastNanos = nanos)
        val dt = (nanos - old.lastNanos) / 1e9f
        val raw = if (dt in .001f..1f) (scale - old.lastScale) / dt else 0f
        if (!raw.isFinite() || abs(raw) > 20f) return old.copy(lastScale = scale, lastNanos = nanos)
        val velocity = old.velocity * .7f + raw * .3f
        return ZoomPrediction(
            velocity,
            scale,
            nanos,
            if (abs(velocity) < .05f) 0 else if (velocity > 0) 1 else -1,
        )
    }

    fun predicted(prediction: ZoomPrediction, lookAhead: Float = .12f): Float =
        (prediction.lastScale + prediction.velocity * lookAhead).coerceIn(1f, 4f)
}

/** Pure transform math used by PdfRenderer and pixel-composition tests. */
object PdfTileRenderMath {
    data class Transform(
        val pdfPageRect: PdfPageRect,
        val matrixValues: FloatArray,
        val contentRectPx: TilePixelRect,
        val bitmapWidthPx: Int,
        val bitmapHeightPx: Int,
    )

    fun create(request: PdfTileRequest, layoutSize: PageLayoutSize, pdfSize: PdfPageSize): Transform {
        require(request.pageRect.right <= layoutSize.width + .01f)
        require(request.pageRect.bottom <= layoutSize.height + .01f)
        val pdfRect = PdfPageRect(
            left = request.pageRect.left / layoutSize.width * pdfSize.width,
            top = request.pageRect.top / layoutSize.height * pdfSize.height,
            right = request.pageRect.right / layoutSize.width * pdfSize.width,
            bottom = request.pageRect.bottom / layoutSize.height * pdfSize.height,
        )
        val scaleX = request.renderScale * layoutSize.width / pdfSize.width
        val scaleY = request.renderScale * layoutSize.height / pdfSize.height
        // Android Matrix values are written directly to avoid ambiguous pre/post order:
        // bitmapX = pdfX*scaleX - pageRect.left*renderScale + bleedLeft
        // bitmapY = pdfY*scaleY - pageRect.top*renderScale + bleedTop
        val translateX = request.bleedPx.left - request.pageRect.left * request.renderScale
        val translateY = request.bleedPx.top - request.pageRect.top * request.renderScale
        return Transform(
            pdfPageRect = pdfRect,
            matrixValues = floatArrayOf(
                scaleX, 0f, translateX,
                0f, scaleY, translateY,
                0f, 0f, 1f,
            ),
            contentRectPx = request.contentRectPx,
            bitmapWidthPx = request.outputWidth,
            bitmapHeightPx = request.outputHeight,
        )
    }
}

object PdfTilePlanner {
    const val TILE_PX = 512
    const val BLEED_PX = 2

    fun visiblePages(
        geometry: ContinuousDocumentGeometry,
        state: ContinuousDocumentState,
        viewportWidth: Float,
        viewportHeight: Float,
    ): List<PageTileViewport> {
        val scale = state.scale.coerceAtLeast(.0001f)
        val documentLeft = state.scrollX / scale
        val documentRight = (state.scrollX + viewportWidth) / scale
        val documentTop = state.scrollY / scale
        val documentBottom = (state.scrollY + viewportHeight) / scale
        return geometry.composedPages(state, viewportHeight, 0).mapNotNull { page ->
            val pageTop = geometry.pageTop(page)
            val pageHeight = geometry.pageHeight(page)
            val rect = PageLayoutRect(
                max(0f, documentLeft),
                max(0f, documentTop - pageTop),
                min(geometry.basePageWidth, documentRight),
                min(pageHeight, documentBottom - pageTop),
            )
            rect.takeIf { it.width > 0 && it.height > 0 }?.let {
                PageTileViewport(
                    pageIndex = page,
                    pageWidth = geometry.basePageWidth,
                    pageHeight = pageHeight,
                    visibleRect = it,
                    geometryGeneration = geometry.pageGeometryGeneration(page),
                    fittedPageWidthVersion = geometry.fittedPageWidthVersion,
                )
            }
        }
    }

    fun documentViewport(
        state: ContinuousDocumentState,
        viewportWidth: Float,
        viewportHeight: Float,
    ): DocumentViewportRect {
        val scale = state.scale.coerceAtLeast(.0001f)
        return DocumentViewportRect(
            state.scrollX / scale,
            state.scrollY / scale,
            (state.scrollX + viewportWidth) / scale,
            (state.scrollY + viewportHeight) / scale,
        )
    }

    fun pageAhead(
        geometry: ContinuousDocumentGeometry,
        corridor: PrefetchCorridor,
        currentPage: Int,
    ): PageTileViewport? {
        val target = when (corridor.direction) {
            TileNavigationDirection.FORWARD -> currentPage + 1
            TileNavigationDirection.BACKWARD -> currentPage - 1
            TileNavigationDirection.IDLE -> return null
        }
        if (target !in 0 until geometry.pageCount) return null
        val pageTop = geometry.pageTop(target)
        val pageHeight = geometry.pageHeight(target)
        val bandHeight = corridor.current.height.coerceAtMost(pageHeight)
        val pageY = if (corridor.direction == TileNavigationDirection.FORWARD) 0f else pageHeight - bandHeight
        val left = corridor.current.left.coerceIn(0f, geometry.basePageWidth)
        val right = corridor.current.right.coerceIn(0f, geometry.basePageWidth)
        val farIntersects = corridor.far.bottom > pageTop && corridor.far.top < pageTop + pageHeight
        if (!farIntersects && corridor.confidence < .45f) return null
        return PageTileViewport(
            pageIndex = target,
            pageWidth = geometry.basePageWidth,
            pageHeight = pageHeight,
            visibleRect = PageLayoutRect(left, pageY, right, pageY + bandHeight),
            geometryGeneration = geometry.pageGeometryGeneration(target),
            fittedPageWidthVersion = geometry.fittedPageWidthVersion,
        )
    }

    fun requests(
        documentId: String,
        documentGeneration: Long,
        view: PageTileViewport,
        bucket: Int,
        requestGeneration: Long,
        focusX: Float?,
        focusY: Float?,
    ): List<PdfTileRequest> {
        val renderScale = PdfZoomBuckets.scales[bucket]
        val logicalTile = TILE_PX / renderScale
        val maxX = ceil(view.pageWidth / logicalTile).toInt()
        val maxY = ceil(view.pageHeight / logicalTile).toInt()
        val x0 = floor(view.visibleRect.left / logicalTile).toInt().coerceAtLeast(0)
        val x1 = floor((view.visibleRect.right - .001f) / logicalTile).toInt().coerceAtMost(maxX - 1)
        val y0 = floor(view.visibleRect.top / logicalTile).toInt().coerceAtLeast(0)
        val y1 = floor((view.visibleRect.bottom - .001f) / logicalTile).toInt().coerceAtMost(maxY - 1)
        val centerX = (view.visibleRect.left + view.visibleRect.right) / 2
        val centerY = (view.visibleRect.top + view.visibleRect.bottom) / 2
        val bleed = TilePixelInsets(BLEED_PX, BLEED_PX, BLEED_PX, BLEED_PX)
        val result = mutableListOf<PdfTileRequest>()
        for (tileY in (y0 - 1).coerceAtLeast(0)..(y1 + 1).coerceAtMost(maxY - 1)) {
            for (tileX in (x0 - 1).coerceAtLeast(0)..(x1 + 1).coerceAtMost(maxX - 1)) {
                val rect = PageLayoutRect(
                    tileX * logicalTile,
                    tileY * logicalTile,
                    min(view.pageWidth, (tileX + 1) * logicalTile),
                    min(view.pageHeight, (tileY + 1) * logicalTile),
                )
                val visible = tileX in x0..x1 && tileY in y0..y1
                val containsFocus = focusX != null && focusY != null &&
                    focusX >= rect.left && focusX <= rect.right && focusY >= rect.top && focusY <= rect.bottom
                val containsCenter = centerX >= rect.left && centerX <= rect.right &&
                    centerY >= rect.top && centerY <= rect.bottom
                val priority = when {
                    containsFocus -> TilePriority.FOCUS
                    containsCenter -> TilePriority.CENTER
                    visible -> TilePriority.VISIBLE
                    else -> TilePriority.MARGIN
                }
                val key = PdfTileKey(
                    documentId = documentId,
                    documentGeneration = documentGeneration,
                    geometryGeneration = view.geometryGeneration,
                    pageIndex = view.pageIndex,
                    rotation = view.rotation,
                    fittedPageWidthVersion = view.fittedPageWidthVersion,
                    zoomBucket = bucket,
                    tileX = tileX,
                    tileY = tileY,
                )
                result += PdfTileRequest(
                    key = key,
                    pageRect = rect,
                    renderScale = renderScale,
                    contentWidthPx = (rect.width * renderScale).roundToInt().coerceAtLeast(1),
                    contentHeightPx = (rect.height * renderScale).roundToInt().coerceAtLeast(1),
                    bleedPx = bleed,
                    priority = priority,
                    requestGeneration = requestGeneration,
                    requestedAtNanos = System.nanoTime(),
                )
            }
        }
        return result.sortedWith(
            compareBy<PdfTileRequest> { it.priority.ordinal }.thenBy {
                hypot(
                    (it.pageRect.left + it.pageRect.right) / 2 - centerX,
                    (it.pageRect.top + it.pageRect.bottom) / 2 - centerY,
                )
            },
        )
    }
}

class PdfTileLru(private val maxBytes: Long = 56L * 1024 * 1024) {
    private val map = LinkedHashMap<PdfTileKey, PdfTileEntry>(16, .75f, true)
    var bytes = 0L
        private set

    fun get(key: PdfTileKey): PdfTileEntry? = map[key]
    fun snapshot(): Map<PdfTileKey, PdfTileEntry> = map.toMap()

    fun put(entry: PdfTileEntry, pinned: Set<PdfTileKey>): List<PdfTileEntry> {
        map.remove(entry.key)?.let { bytes -= it.byteSize }
        map[entry.key] = entry
        bytes += entry.byteSize
        val evicted = mutableListOf<PdfTileEntry>()
        while (bytes > maxBytes) {
            val victim = map.entries.firstOrNull { it.key !in pinned } ?: break
            map.remove(victim.key)
            bytes -= victim.value.byteSize
            evicted += victim.value
        }
        return evicted
    }

    fun clear() {
        map.clear()
        bytes = 0
    }

    /**
     * OOM recovery hook: evict oldest entries (access order) until the cache
     * holds at most [budgetBytes]. Never throws; called at the renderer OOM
     * boundary so the next tile request has headroom.
     */
    fun trimTo(budgetBytes: Long) {
        val budget = budgetBytes.coerceAtLeast(0)
        val it = map.entries.iterator()
        while (bytes > budget && it.hasNext()) {
            val entry = it.next()
            it.remove()
            bytes -= entry.value.byteSize
        }
    }
}

object PdfTileLruPolicy {
    fun evictOldest(
        entries: List<Pair<PdfTileKey, Long>>,
        pinned: Set<PdfTileKey>,
        budget: Long,
    ): List<PdfTileKey> {
        var bytes = entries.sumOf { it.second }
        val evicted = mutableListOf<PdfTileKey>()
        for ((key, size) in entries) {
            if (bytes <= budget) break
            if (key !in pinned) {
                evicted += key
                bytes -= size
            }
        }
        return evicted
    }
}
