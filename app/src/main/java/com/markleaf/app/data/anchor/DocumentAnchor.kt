package com.markleaf.app.data.anchor

/**
 * R24 跨格式标注锚点（抽象模型）。
 *
 * 设计边界：
 * - 只携带**格式中性、分辨率无关**的定位信息——禁止出现像素/渲染坐标；
 * - PDF = 页索引 + 页内归一化矩形（相对页面尺寸 0..1，缩放/旋转视口不变）；
 * - Markdown = 块索引（解析器块序）+ 可选块内字符区间精化；
 * - 实体层只存本模型的规范化投影（见 [AnchorProjection] 与 AnchorCodec），
 *   不绑定任何具体渲染实现。
 */
sealed class DocumentAnchor {

    /** PDF 页锚：page 为 0-based 页索引；rect 为 null 表示整页锚。 */
    data class PdfPage(
        val page: Int,
        val rect: NormalizedRect? = null,
    ) : DocumentAnchor() {
        init {
            require(page >= 0) { "page must be >= 0" }
        }
    }

    /**
     * Markdown 块锚：blockIndex 为解析器块序（0-based）；charStart/charEnd
     * 为可选的块内字符区间（半开区间语义由消费方定义），用于段内高亮精化。
     */
    data class MarkdownBlock(
        val blockIndex: Int,
        val charStart: Int? = null,
        val charEnd: Int? = null,
    ) : DocumentAnchor() {
        init {
            require(blockIndex >= 0) { "blockIndex must be >= 0" }
            require(charStart == null || charStart >= 0) { "charStart must be >= 0" }
            require(charEnd == null || charStart == null || charEnd >= charStart) {
                "charEnd must be >= charStart"
            }
        }
    }
}

/**
 * 页面归一化矩形：所有分量相对页面尺寸，恒定于缩放/密度/旋转视口。
 * 构造即钳制到 [0,1]×[0,1]，宽高非负——非法输入不可能进入存储。
 */
data class NormalizedRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
) {
    init {
        require(width >= 0f && height >= 0f) { "width/height must be non-negative" }
    }

    /** 钳制分量到合法区间（保持 w/h ≥ 0）。 */
    fun normalized(): NormalizedRect = NormalizedRect(
        x = x.coerceIn(0f, 1f),
        y = y.coerceIn(0f, 1f),
        width = width.coerceIn(0f, 1f),
        height = height.coerceIn(0f, 1f),
    )

    companion object {
        /** 由像素矩形与页面像素尺寸构造（缩放不变：分子分母同倍数）。 */
        fun fromPixel(
            px: Float,
            py: Float,
            pw: Float,
            ph: Float,
            pageW: Float,
            pageH: Float,
        ): NormalizedRect {
            require(pageW > 0f && pageH > 0f) { "page size must be positive" }
            return NormalizedRect(
                x = px / pageW,
                y = py / pageH,
                width = pw / pageW,
                height = ph / pageH,
            ).normalized()
        }
    }
}
