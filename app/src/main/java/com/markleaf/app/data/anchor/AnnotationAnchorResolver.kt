package com.markleaf.app.data.anchor

/**
 * R24 标注锚点解析器：负责锚点信息的**保存投影**与**恢复解析**。
 *
 * - 保存：[DocumentAnchor] → [AnchorProjection]（locationKind/location 派生列
 *   用作索引排序键 + anchor 列的规范化序列化原文）。派生列是写入时单点计算，
 *   不是第二事实源——恢复一律以 anchor 列优先。
 * - 恢复：行字段 → [DocumentAnchor]。anchor 列存在且合法时精确还原；
 *   为 null（v11 前历史行）时按 kind/location(+旧字符区间) 合成等价锚点——
 *   这是全量纯函数回退，不是遗留运行路径。
 */
interface AnnotationAnchorResolver {

    fun project(anchor: DocumentAnchor): AnchorProjection

    /**
     * 行字段 → 抽象锚点。无法解释的输入返回 null（调用方按无锚处理）。
     */
    fun resolve(
        locationKind: String,
        location: Int,
        anchorRaw: String?,
        legacyAnchorStart: Int? = null,
        legacyAnchorEnd: Int? = null,
    ): DocumentAnchor?
}

/** 锚点的存储投影（写时单点派生）。 */
data class AnchorProjection(
    val locationKind: String,
    val location: Int,
    /** 规范化序列化原文，直接落 anchor 列。 */
    val encoded: String,
)

/** PDF：PAGE_INDEX 坐标系，location = 页索引。 */
object PdfPageAnchorResolver : AnnotationAnchorResolver {

    override fun project(anchor: DocumentAnchor): AnchorProjection {
        val pdf = anchor as? DocumentAnchor.PdfPage
            ?: throw IllegalArgumentException("expected PdfPage anchor, got $anchor")
        return AnchorProjection(
            locationKind = "PAGE_INDEX",
            location = pdf.page,
            encoded = AnchorCodec.encode(pdf),
        )
    }

    override fun resolve(
        locationKind: String,
        location: Int,
        anchorRaw: String?,
        legacyAnchorStart: Int?,
        legacyAnchorEnd: Int?,
    ): DocumentAnchor? {
        if (locationKind != "PAGE_INDEX") return null
        AnchorCodec.decode(anchorRaw)?.let { return it }
        // 历史行合成：整页锚。
        return if (location >= 0) {
            DocumentAnchor.PdfPage(page = location)
        } else {
            null
        }
    }
}

/** Markdown：SCROLL_POSITION 坐标系，location = 块索引。 */
object MarkdownBlockAnchorResolver : AnnotationAnchorResolver {

    override fun project(anchor: DocumentAnchor): AnchorProjection {
        val md = anchor as? DocumentAnchor.MarkdownBlock
            ?: throw IllegalArgumentException("expected MarkdownBlock anchor, got $anchor")
        return AnchorProjection(
            locationKind = "SCROLL_POSITION",
            location = md.blockIndex,
            encoded = AnchorCodec.encode(md),
        )
    }

    override fun resolve(
        locationKind: String,
        location: Int,
        anchorRaw: String?,
        legacyAnchorStart: Int?,
        legacyAnchorEnd: Int?,
    ): DocumentAnchor? {
        if (locationKind != "SCROLL_POSITION") return null
        AnchorCodec.decode(anchorRaw)?.let { return it }
        // 历史行合成：块索引 + 旧字符区间精化。
        return if (location >= 0) {
            DocumentAnchor.MarkdownBlock(
                blockIndex = location,
                charStart = legacyAnchorStart,
                charEnd = legacyAnchorEnd,
            )
        } else {
            null
        }
    }
}

/** 按坐标系取解析器；未知 kind 返回 null（未来格式以注册扩展）。 */
object AnnotationAnchorResolvers {
    fun forLocationKind(locationKind: String): AnnotationAnchorResolver? = when (locationKind) {
        "PAGE_INDEX" -> PdfPageAnchorResolver
        "SCROLL_POSITION" -> MarkdownBlockAnchorResolver
        else -> null
    }

    fun forAnchor(anchor: DocumentAnchor): AnnotationAnchorResolver = when (anchor) {
        is DocumentAnchor.PdfPage -> PdfPageAnchorResolver
        is DocumentAnchor.MarkdownBlock -> MarkdownBlockAnchorResolver
    }
}
