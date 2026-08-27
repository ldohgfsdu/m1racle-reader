package com.m1racle.reader.ui.annotation

import com.m1racle.reader.data.anchor.NormalizedRect

/**
 * R26.1 PDF 高亮几何纯函数（宿主可测）：拖拽矩形 → 合法选区。
 * 最小面积门槛防止误触长按产生退化高亮。
 */
object PdfHighlightGeometry {

    /** 归一化面积下限（页面占比），低于此视为误触。 */
    const val MIN_NORMALIZED_AREA: Float = 0.0004f

    /** 绝对像素下限（任一边）。 */
    const val MIN_EDGE_PX: Float = 8f

    /**
     * 由拖拽起止点构造选择；无效（零面积/倒序/低于门槛）返回 null。
     * 起止点任意方向均可（用户可从四角任一方向拖出）。
     */
    fun selectionOrNull(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        pageWidthPx: Float,
        pageHeightPx: Float,
        page: Int,
    ): TextSelection.PdfSelection? {
        if (pageWidthPx <= 0f || pageHeightPx <= 0f) return null
        val left = minOf(startX, endX)
        val top = minOf(startY, endY)
        val right = maxOf(startX, endX)
        val bottom = maxOf(startY, endY)
        if ((right - left) < MIN_EDGE_PX || (bottom - top) < MIN_EDGE_PX) return null
        return try {
            TextSelection.PdfSelection(
                page = page,
                leftPx = left,
                topPx = top,
                rightPx = right,
                bottomPx = bottom,
                pageWidthPx = pageWidthPx,
                pageHeightPx = pageHeightPx,
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** 面积门槛校验（基于归一化结果，供选择完成后二次确认）。 */
    fun meetsAreaGate(selection: TextSelection.PdfSelection): Boolean {
        val n = selection.normalizedOrNull() ?: return false
        return n.width * n.height >= MIN_NORMALIZED_AREA ||
            n.width >= 0.9f || n.height >= 0.9f // 贴边整行/整页拖拽豁免
    }

    /** 屏幕矩形换算：归一化锚点 × 当前渲染尺寸（缩放无关的恢复端）。 */
    fun toScreenRect(rect: NormalizedRect, widthPx: Float, heightPx: Float): QuadPoints =
        QuadPoints(
            left = rect.x * widthPx,
            top = rect.y * heightPx,
            right = (rect.x + rect.width) * widthPx,
            bottom = (rect.y + rect.height) * heightPx,
        )

    /**
     * 点选命中测试：返回命中的最高层项 id（列表末位视为最上层），未命中 null。
     */
    fun <T> hitTest(
        itemsInDrawOrder: List<Pair<T, NormalizedRect>>,
        x: Float,
        y: Float,
        widthPx: Float,
        heightPx: Float,
    ): T? {
        for ((id, rect) in itemsInDrawOrder.asReversed()) {
            val s = toScreenRect(rect, widthPx, heightPx)
            if (x >= s.left && x <= s.right && y >= s.top && y <= s.bottom) return id
        }
        return null
    }

    /** 删除角标（✕）的命中圆心与半径（依附于选中矩形右上角）。 */
    fun deleteBadgeHit(
        rect: NormalizedRect,
        x: Float,
        y: Float,
        widthPx: Float,
        heightPx: Float,
        badgeRadiusPx: Float,
    ): Boolean {
        val s = toScreenRect(rect, widthPx, heightPx)
        val cx = s.right - badgeRadiusPx
        val cy = s.top - badgeRadiusPx * 0.6f
        val dx = x - cx
        val dy = y - cy
        return dx * dx + dy * dy <= badgeRadiusPx * badgeRadiusPx
    }

    /** 批注角标（✎）命中：依附于选中矩形左上角外侧。 */
    fun noteBadgeHit(
        rect: NormalizedRect,
        x: Float,
        y: Float,
        widthPx: Float,
        heightPx: Float,
        badgeRadiusPx: Float,
    ): Boolean {
        val s = toScreenRect(rect, widthPx, heightPx)
        val cx = s.left + badgeRadiusPx
        val cy = s.top - badgeRadiusPx * 0.6f
        val dx = x - cx
        val dy = y - cy
        return dx * dx + dy * dy <= badgeRadiusPx * badgeRadiusPx
    }

    data class QuadPoints(val left: Float, val top: Float, val right: Float, val bottom: Float)
}
