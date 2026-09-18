package com.m1racle.reader.ui.annotation

import com.m1racle.reader.data.anchor.AnnotationAnchorResolvers
import com.m1racle.reader.data.anchor.DocumentAnchor
import com.m1racle.reader.data.anchor.NormalizedRect
import com.m1racle.reader.data.repository.ReadingRepository

/**
 * R25 覆盖层绘制**数据契约**（只定义消费什么，不定义怎么画）。
 *
 * 渲染层（未来任务）按坐标系取快照后自行换算：
 * - PDF：[AnnotationOverlayItem.pdfRectsNormalized] 给出页内归一化矩形，
 *   乘以当前页面渲染尺寸即得屏幕矩形——与缩放无关；
 * - Markdown：blockIndex 定位块，字符区间由消费方映射到文本布局。
 */
data class AnnotationOverlayItem(
    val id: Long,
    val type: AnnotationType,
    val anchor: DocumentAnchor,
    val excerpt: String?,
) {
    /** PDF 页内归一化矩形；非 PDF 锚点返回 null。 */
    fun pdfRectsNormalized(): List<NormalizedRect> =
        (anchor as? DocumentAnchor.PdfPage)?.rects.orEmpty()

    fun markdownRange(): Pair<Int, Int>? =
        (anchor as? DocumentAnchor.MarkdownBlock)
            ?.let { it.charStart?.let { s -> s to (it.charEnd ?: s) } }
}

interface AnnotationOverlaySource {
    /** 指定文档在给定坐标系/位置上的覆盖项（location 升序）。 */
    suspend fun itemsFor(documentId: String, locationKind: String, location: Int): List<AnnotationOverlayItem>
}

/** 基于仓库复合索引精确查询 kind+location，并恢复抽象锚点。 */
class RepositoryAnnotationOverlaySource(
    private val readingRepository: ReadingRepository,
) : AnnotationOverlaySource {

    override suspend fun itemsFor(
        documentId: String,
        locationKind: String,
        location: Int,
    ): List<AnnotationOverlayItem> =
        highlightItemsFor(documentId, locationKind, location) +
            noteItemsFor(documentId, locationKind, location)

    /** PDF 高亮层专用：只查询 Highlight，Note 不参与黄色覆盖绘制。 */
    suspend fun highlightItemsFor(
        documentId: String,
        locationKind: String,
        location: Int,
    ): List<AnnotationOverlayItem> {
        val resolver = AnnotationAnchorResolvers.forLocationKind(locationKind)
            ?: return emptyList()
        return readingRepository.highlightsAtLocation(documentId, locationKind, location)
            .mapNotNull { row ->
                val anchor = resolver.resolve(
                    row.locationKind, row.location, row.anchor, row.anchorStart, row.anchorEnd,
                ) ?: return@mapNotNull null
                AnnotationOverlayItem(id = row.id, type = AnnotationType.HIGHLIGHT, anchor = anchor, excerpt = row.excerpt)
            }
    }

    suspend fun noteItemsFor(
        documentId: String,
        locationKind: String,
        location: Int,
    ): List<AnnotationOverlayItem> {
        val resolver = AnnotationAnchorResolvers.forLocationKind(locationKind)
            ?: return emptyList()
        return readingRepository.notesAtLocation(documentId, locationKind, location)
            .mapNotNull { row ->
                val anchor = resolver.resolve(
                    row.locationKind, row.location, row.anchor, row.anchorStart, row.anchorEnd,
                ) ?: return@mapNotNull null
                AnnotationOverlayItem(id = row.id, type = AnnotationType.NOTE, anchor = anchor, excerpt = null)
            }
    }
}
