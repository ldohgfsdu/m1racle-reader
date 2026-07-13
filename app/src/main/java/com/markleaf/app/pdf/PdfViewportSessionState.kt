package com.markleaf.app.pdf

data class PdfViewportSessionState(
    val version: Int = CURRENT_VERSION,
    val readingMode: PdfReadingMode,
    val pageIndex: Int,
    val pageAnchorXFraction: Float,
    val pageAnchorYFraction: Float,
    val viewportAnchorXFraction: Float = 0.5f,
    val viewportAnchorYFraction: Float = 0.5f,
    val scale: Float,
    val generation: Long,
) {
    fun normalized(pageCount: Int, fallbackPage: Int): PdfViewportSessionState? {
        if (version != CURRENT_VERSION || readingMode != PdfReadingMode.VERTICAL_CONTINUOUS) return null
        if (!scale.isFinite()) return null
        val safePage = if (pageIndex in 0 until pageCount) pageIndex else fallbackPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        return copy(
            pageIndex = safePage,
            pageAnchorXFraction = pageAnchorXFraction.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0.5f,
            pageAnchorYFraction = pageAnchorYFraction.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0.5f,
            viewportAnchorXFraction = viewportAnchorXFraction.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0.5f,
            viewportAnchorYFraction = viewportAnchorYFraction.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0.5f,
            scale = PdfRenderQuality.clampContinuousZoom(scale),
        )
    }

    companion object { const val CURRENT_VERSION = 1 }
}

class PdfViewportSessionTracker {
    private var stable: PdfViewportSessionState? = null
    private var lastFlushedGeneration = -1L
    fun seed(value: PdfViewportSessionState?) { stable = value }
    fun commit(value: PdfViewportSessionState) { if (value.generation >= (stable?.generation ?: -1L)) stable = value }
    fun stable(): PdfViewportSessionState? = stable
    fun takeForFlush(): PdfViewportSessionState? = stable?.takeIf { it.generation > lastFlushedGeneration }?.also {
        lastFlushedGeneration = it.generation
    }
}
