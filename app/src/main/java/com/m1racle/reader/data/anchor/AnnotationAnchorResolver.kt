package com.m1racle.reader.data.anchor

/**
 * R24 标注锚点解析器：负责锚点信息的**保存投影**与**恢复解析**。
 *
 * - 保存：[DocumentAnchor] → [AnchorProjection]（locationKind/location 派生列
 *   用作索引排序键 + anchor 列的规范化序列化原文）。派生列是写入时单点计算，
 *   不是第二事实源——恢复一律以 anchor 列优先。
 * - 恢复仅解码 canonical anchor；历史字段由 Room v17 一次性迁移补全。
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
    ): DocumentAnchor? {
        if (locationKind != "PAGE_INDEX") return null
        return AnchorCodec.decode(anchorRaw) as? DocumentAnchor.PdfPage
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
    ): DocumentAnchor? {
        if (locationKind != "SCROLL_POSITION") return null
        return AnchorCodec.decode(anchorRaw) as? DocumentAnchor.MarkdownBlock
    }
}

/**
 * EPUB：EPUB_TEXT 坐标系，location = 章节 spine 序号（仅排序键，章内顺序靠解码锚点）。
 *
 * spine 序号是派生值、不进锚点身份，所以投影必须由知道书目结构的写入方提供
 * （[projectInBook]）。无书目上下文的 [project] 会明确失败，而不是写入错误的排序键。
 * 本步只建模型：EPUB 书签 / 标注的写入 UI 在下一步接入。
 */
object EpubTextAnchorResolver : AnnotationAnchorResolver {

    const val LOCATION_KIND = "EPUB_TEXT"

    fun projectInBook(anchor: DocumentAnchor.EpubText, spineIndex: Int): AnchorProjection {
        require(spineIndex >= 0) { "spineIndex must be >= 0" }
        return AnchorProjection(
            locationKind = LOCATION_KIND,
            location = spineIndex,
            encoded = AnchorCodec.encode(anchor),
        )
    }

    override fun project(anchor: DocumentAnchor): AnchorProjection {
        require(anchor is DocumentAnchor.EpubText) { "expected EpubText anchor, got $anchor" }
        throw IllegalStateException("EPUB_TEXT projection needs the chapter order; use projectInBook")
    }

    override fun resolve(
        locationKind: String,
        location: Int,
        anchorRaw: String?,
    ): DocumentAnchor? {
        if (locationKind != LOCATION_KIND) return null
        // No historical EPUB rows exist, so there is nothing to synthesize.
        return AnchorCodec.decode(anchorRaw) as? DocumentAnchor.EpubText
    }
}

/** 按坐标系取解析器；未知 kind 返回 null（未来格式以注册扩展）。 */
object AnnotationAnchorResolvers {
    fun forLocationKind(locationKind: String): AnnotationAnchorResolver? = when (locationKind) {
        "PAGE_INDEX" -> PdfPageAnchorResolver
        "SCROLL_POSITION" -> MarkdownBlockAnchorResolver
        EpubTextAnchorResolver.LOCATION_KIND -> EpubTextAnchorResolver
        else -> null
    }

    fun forAnchor(anchor: DocumentAnchor): AnnotationAnchorResolver = when (anchor) {
        is DocumentAnchor.PdfPage -> PdfPageAnchorResolver
        is DocumentAnchor.MarkdownBlock -> MarkdownBlockAnchorResolver
        is DocumentAnchor.EpubText -> EpubTextAnchorResolver
    }
}
