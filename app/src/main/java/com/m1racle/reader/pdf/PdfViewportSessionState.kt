package com.m1racle.reader.pdf

data class PdfViewportSessionState(
    val version: Int = CURRENT_VERSION,
    val readingMode: PdfReadingMode,
    val pageIndex: Int,
    val pageAnchorXFraction: Float,
    val pageAnchorYFraction: Float,
    val viewportAnchorXFraction: Float = 0.5f,
    val viewportAnchorYFraction: Float = 0.5f,
    val sourceViewportWidthPx: Float = 1f,
    val sourceViewportHeightPx: Float = 1f,
    val scale: Float,
    val generation: Long,
    val legalOffsetX: Float = 0f,
    val legalOffsetY: Float = 0f,
    val visualOverscrollX: Float = 0f,
    val visualOverscrollY: Float = 0f,
    val interactionPhase: PdfGesturePhase = PdfGesturePhase.IDLE,
    val pointerEpoch: Long? = null,
) {
    fun normalized(pageCount: Int, fallbackPage: Int): PdfViewportSessionState? {
        if (version != CURRENT_VERSION) return null
        if (!scale.isFinite()) return null
        val safePage = if (pageIndex in 0 until pageCount) pageIndex else fallbackPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        return copy(
            pageIndex = safePage,
            pageAnchorXFraction = pageAnchorXFraction.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0.5f,
            pageAnchorYFraction = pageAnchorYFraction.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0.5f,
            viewportAnchorXFraction = viewportAnchorXFraction.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0.5f,
            viewportAnchorYFraction = viewportAnchorYFraction.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0.5f,
            sourceViewportWidthPx = sourceViewportWidthPx.takeIf { it.isFinite() && it > 0f } ?: 1f,
            sourceViewportHeightPx = sourceViewportHeightPx.takeIf { it.isFinite() && it > 0f } ?: 1f,
            scale = PdfRenderQuality.clampContinuousZoom(scale),
            legalOffsetX = legalOffsetX.takeIf(Float::isFinite) ?: 0f,
            legalOffsetY = legalOffsetY.takeIf(Float::isFinite) ?: 0f,
            visualOverscrollX = visualOverscrollX.takeIf(Float::isFinite) ?: 0f,
            visualOverscrollY = visualOverscrollY.takeIf(Float::isFinite) ?: 0f,
        )
    }

    fun forMode(mode: PdfReadingMode, nextGeneration: Long = generation): PdfViewportSessionState = copy(
        readingMode = mode,
        generation = nextGeneration,
        visualOverscrollX = 0f,
        visualOverscrollY = 0f,
        interactionPhase = PdfGesturePhase.IDLE,
        pointerEpoch = null,
    )

    companion object { const val CURRENT_VERSION = 1 }
}

/** One page-local anchor captured from the actually visible source frame before a mode swap. */
data class CanonicalPdfAnchor(
    val pageIndex: Int,
    val pageU: Float,
    val pageV: Float,
    val viewportFocalX: Float,
    val viewportFocalY: Float,
    val sourceViewportWidthPx: Float,
    val sourceViewportHeightPx: Float,
    val displayScale: Float,
    val generation: Long,
) {
    fun toViewportSession(mode: PdfReadingMode, nextGeneration: Long): PdfViewportSessionState =
        PdfViewportSessionState(
            readingMode = mode,
            pageIndex = pageIndex,
            pageAnchorXFraction = pageU.coerceIn(0f, 1f),
            pageAnchorYFraction = pageV.coerceIn(0f, 1f),
            viewportAnchorXFraction = (
                viewportFocalX / sourceViewportWidthPx.coerceAtLeast(1f)
                ).coerceIn(0f, 1f),
            viewportAnchorYFraction = (
                viewportFocalY / sourceViewportHeightPx.coerceAtLeast(1f)
                ).coerceIn(0f, 1f),
            sourceViewportWidthPx = sourceViewportWidthPx.coerceAtLeast(1f),
            sourceViewportHeightPx = sourceViewportHeightPx.coerceAtLeast(1f),
            scale = displayScale,
            generation = nextGeneration,
        )
}

fun PdfViewportSessionState.toCanonicalAnchor(): CanonicalPdfAnchor = CanonicalPdfAnchor(
    pageIndex = pageIndex,
    pageU = pageAnchorXFraction,
    pageV = pageAnchorYFraction,
    viewportFocalX = viewportAnchorXFraction * sourceViewportWidthPx.coerceAtLeast(1f),
    viewportFocalY = viewportAnchorYFraction * sourceViewportHeightPx.coerceAtLeast(1f),
    sourceViewportWidthPx = sourceViewportWidthPx.coerceAtLeast(1f),
    sourceViewportHeightPx = sourceViewportHeightPx.coerceAtLeast(1f),
    displayScale = scale,
    generation = generation,
)

class PdfViewportSessionTracker {
    private var stable: PdfViewportSessionState? = null
    private var latestPreview: PdfViewportSessionState? = null
    private var lastFlushedGeneration = -1L
    fun seed(value: PdfViewportSessionState?) { stable = value; latestPreview = value }
    fun preview(value: PdfViewportSessionState) {
        if (value.generation >= (latestPreview?.generation ?: -1L)) latestPreview = value
    }
    fun commit(value: PdfViewportSessionState) {
        if (value.generation >= (stable?.generation ?: -1L)) stable = value
        preview(value)
    }
    fun stable(): PdfViewportSessionState? = stable
    fun latestForMode(mode: PdfReadingMode): PdfViewportSessionState? =
        latestPreview?.takeIf { it.readingMode == mode }
    fun takeForFlush(): PdfViewportSessionState? = stable?.takeIf { it.generation > lastFlushedGeneration }?.also {
        lastFlushedGeneration = it.generation
    }
}
