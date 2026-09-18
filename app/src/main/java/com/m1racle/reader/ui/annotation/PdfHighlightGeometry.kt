package com.m1racle.reader.ui.annotation

import com.m1racle.reader.data.anchor.NormalizedRect

/**
 * PDF 高亮显示坐标、逐段命中与操作按钮定位。
 */
object PdfHighlightGeometry {

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
        val (cx,cy) = badgeCenter(rect,widthPx,heightPx,badgeRadiusPx,true)
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
        val (cx,cy) = badgeCenter(rect,widthPx,heightPx,badgeRadiusPx,false)
        val dx = x - cx
        val dy = y - cy
        return dx * dx + dy * dy <= badgeRadiusPx * badgeRadiusPx
    }

    /** One shared placement for drawing and hit testing, including a one-character selection. */
    fun badgeCenter(rect: NormalizedRect, width: Float, height: Float, radius: Float, delete: Boolean): Pair<Float,Float> {
        val left = (rect.x*width).coerceIn(0f, (width-radius*4.5f).coerceAtLeast(0f))
        val y = (rect.y*height-radius*1.2f).coerceIn(radius, (height-radius).coerceAtLeast(radius))
        return left+radius*(if(delete) 3.5f else 1f) to y
    }

    data class QuadPoints(val left: Float, val top: Float, val right: Float, val bottom: Float)
}
