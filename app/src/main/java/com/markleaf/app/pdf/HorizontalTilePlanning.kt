package com.markleaf.app.pdf

import kotlin.math.roundToInt

/** Horizontal adapter for the VM-lifetime detail-tile pipeline used by continuous mode. */
object HorizontalTilePlanning {
    fun visiblePage(
        pageIndex: Int,
        layout: HorizontalPageLayout,
        scale: Float,
        pan: HorizontalPanState,
    ): PageTileViewport? {
        if (layout.fittedWidthPx <= 0f || layout.fittedHeightPx <= 0f) return null
        val safeScale = scale.coerceAtLeast(0.0001f)
        val offsetX = pan.displayOffsetX
        val offsetY = pan.displayOffsetY
        val left = (
            (0f - layout.viewportWidthPx / 2f - offsetX) / safeScale +
                layout.fittedWidthPx / 2f
            ).coerceIn(0f, layout.fittedWidthPx)
        val right = (
            (layout.viewportWidthPx - layout.viewportWidthPx / 2f - offsetX) / safeScale +
                layout.fittedWidthPx / 2f
            ).coerceIn(0f, layout.fittedWidthPx)
        val top = ((0f - layout.contentTopPx - offsetY) / safeScale)
            .coerceIn(0f, layout.fittedHeightPx)
        val bottom = ((layout.viewportHeightPx - layout.contentTopPx - offsetY) / safeScale)
            .coerceIn(0f, layout.fittedHeightPx)
        if (right <= left || bottom <= top) return null
        return PageTileViewport(
            pageIndex = pageIndex,
            pageWidth = layout.fittedWidthPx,
            pageHeight = layout.fittedHeightPx,
            visibleRect = PageLayoutRect(left, top, right, bottom),
            geometryGeneration = pageGeometryGeneration(
                layout.fittedWidthPx,
                layout.fittedHeightPx,
                rotation = 0,
            ),
            fittedPageWidthVersion = layout.fittedWidthPx.roundToInt(),
        )
    }

    fun pageAhead(
        pageIndex: Int,
        pageCount: Int,
        direction: HorizontalPageTransitionDirection?,
        layoutForPage: (Int) -> HorizontalPageLayout,
        visible: PageTileViewport,
    ): PageTileViewport? {
        val target = when (direction) {
            HorizontalPageTransitionDirection.PREVIOUS -> pageIndex - 1
            HorizontalPageTransitionDirection.NEXT -> pageIndex + 1
            null -> if (pageIndex + 1 < pageCount) pageIndex + 1 else pageIndex - 1
        }
        if (target !in 0 until pageCount) return null
        val layout = layoutForPage(target)
        if (layout.fittedWidthPx <= 0f || layout.fittedHeightPx <= 0f) return null
        val bandWidth = visible.visibleRect.width.coerceAtMost(layout.fittedWidthPx)
        val bandHeight = visible.visibleRect.height.coerceAtMost(layout.fittedHeightPx)
        val left = if (direction == HorizontalPageTransitionDirection.PREVIOUS) {
            (layout.fittedWidthPx - bandWidth).coerceAtLeast(0f)
        } else {
            0f
        }
        val normalizedTop = if (visible.pageHeight > 0f) {
            visible.visibleRect.top / visible.pageHeight
        } else {
            0f
        }
        val top = (normalizedTop * layout.fittedHeightPx)
            .coerceIn(0f, (layout.fittedHeightPx - bandHeight).coerceAtLeast(0f))
        return PageTileViewport(
            pageIndex = target,
            pageWidth = layout.fittedWidthPx,
            pageHeight = layout.fittedHeightPx,
            visibleRect = PageLayoutRect(left, top, left + bandWidth, top + bandHeight),
            geometryGeneration = pageGeometryGeneration(
                layout.fittedWidthPx,
                layout.fittedHeightPx,
                rotation = 0,
            ),
            fittedPageWidthVersion = layout.fittedWidthPx.roundToInt(),
        )
    }

    fun plan(
        documentId: String,
        documentGeneration: Long,
        snapshot: PdfViewportSnapshot,
        visible: PageTileViewport,
        currentBucket: Int,
        targetBucket: Int,
        ahead: PageTileViewport?,
    ): PdfTilePlan {
        val now = System.nanoTime()
        fun requests(view: PageTileViewport, bucket: Int) = PdfTilePlanner.requests(
            documentId = documentId,
            documentGeneration = documentGeneration,
            view = view,
            bucket = bucket,
            requestGeneration = snapshot.timestampNanos,
            focusX = null,
            focusY = null,
        ).map {
            it.copy(
                requestedAtNanos = now,
                snapshotSequence = snapshot.sequence,
                targetedAtNanos = snapshot.timestampNanos,
            )
        }
        val current = buildList {
            if (snapshot.scale > HorizontalZoomPanPhysics.PAGE_STRIP_SCALE_THRESHOLD) {
                addAll(requests(visible, currentBucket))
                if (targetBucket != currentBucket) addAll(requests(visible, targetBucket))
            }
        }
        val pageAhead = ahead?.let { view ->
            requests(view, currentBucket)
                .filter { it.priority != TilePriority.MARGIN }
                .take(8)
                .map { it.copy(priority = TilePriority.PAGE_AHEAD) }
        }.orEmpty()
        val ordered = TileRenderPipelinePolicy.ordered((current + pageAhead).distinctBy { it.key })
        return PdfTilePlan(
            snapshot = snapshot,
            requests = ordered,
            pageSizes = buildMap {
                put(visible.pageIndex, visible.pageSize)
                ahead?.let { put(it.pageIndex, it.pageSize) }
            },
            currentVisibleKeys = ordered
                .filter { TileRenderPipelinePolicy.isCurrentVisible(it.priority) }
                .mapTo(linkedSetOf()) { it.key },
        )
    }
}
