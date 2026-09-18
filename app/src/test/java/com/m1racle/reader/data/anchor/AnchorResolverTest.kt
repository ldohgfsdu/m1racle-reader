package com.m1racle.reader.data.anchor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * R24 锚点解析器稳定性契约：
 * 1. 同一锚点多次解析/投影结果完全一致（确定性）；
 * 2. PDF 归一化坐标与渲染尺度无关；
 * 3. Markdown 块索引可恢复；v11 前历史行按 kind/location 合成等价锚点。
 */
class AnchorResolverTest {

    @Test
    fun `same pdf anchor resolves identically across repeated calls`() {
        val anchor = DocumentAnchor.PdfPage(
            page = 9,
            rects = listOf(NormalizedRect(0.05f, 0.4f, 0.6f, 0.2f)),
        )
        val first = PdfPageAnchorResolver.project(anchor)
        repeat(10) {
            val again = PdfPageAnchorResolver.project(anchor)
            assertEquals(first, again)
        }
        repeat(10) {
            val restored = PdfPageAnchorResolver.resolve(
                locationKind = first.locationKind,
                location = first.location,
                anchorRaw = first.encoded,
            )
            assertEquals(anchor, restored)
        }
    }

    @Test
    fun `same markdown anchor resolves identically across repeated calls`() {
        val anchor = DocumentAnchor.MarkdownBlock(blockIndex = 88, charStart = 3, charEnd = 27)
        val projection = MarkdownBlockAnchorResolver.project(anchor)
        repeat(10) {
            assertEquals(projection, MarkdownBlockAnchorResolver.project(anchor))
            assertEquals(
                anchor,
                MarkdownBlockAnchorResolver.resolve(
                    projection.locationKind,
                    projection.location,
                    projection.encoded,
                ),
            )
        }
    }

    @Test
    fun `pdf normalized coordinates are unaffected by render scale`() {
        // 视口 scale=1 与 scale=3 下同一页面区域：归一化矩形逐分量一致。
        val pageW = 1190f
        val pageH = 1684f
        val rectPx = NormalizedRect.fromPixel(120f, 300f, 500f, 220f, pageW, pageH)
        val zoomedRectPx = NormalizedRect.fromPixel(
            120f * 3f,
            300f * 3f,
            500f * 3f,
            220f * 3f,
            pageW * 3f,
            pageH * 3f,
        )
        assertEquals(rectPx, zoomedRectPx)
        // 且经编解码往返后仍然一致（存储不引入尺度敏感信息）。
        val stored = DocumentAnchor.PdfPage(page = 5, rects = listOf(rectPx))
        val restored = AnchorCodec.decode(AnchorCodec.encode(stored)) as DocumentAnchor.PdfPage
        assertEquals(rectPx, restored.rects.single())
    }

    @Test
    fun `markdown block index survives save restore cycle`() {
        val original = DocumentAnchor.MarkdownBlock(blockIndex = 512)
        val p = MarkdownBlockAnchorResolver.project(original)
        assertEquals("SCROLL_POSITION", p.locationKind)
        assertEquals(512, p.location)
        val restored = MarkdownBlockAnchorResolver.resolve(p.locationKind, p.location, p.encoded)
        assertEquals(original, restored)
    }

    @Test
    fun `legacy v10 rows synthesize equivalent anchors from kind and location`() {
        // 历史高亮：kind+location + 旧字符区间，anchor 列为 null。
        val legacyMd = MarkdownBlockAnchorResolver.resolve(
            locationKind = "SCROLL_POSITION",
            location = 64,
            anchorRaw = null,
            legacyAnchorStart = 8,
            legacyAnchorEnd = 55,
        )
        assertEquals(DocumentAnchor.MarkdownBlock(blockIndex = 64, charStart = 8, charEnd = 55), legacyMd)

        val legacyPdf = PdfPageAnchorResolver.resolve(
            locationKind = "PAGE_INDEX",
            location = 23,
            anchorRaw = null,
        )
        assertEquals(DocumentAnchor.PdfPage(page = 23), legacyPdf)

        // 未知 kind 与非法位置安全拒绝。
        assertNull(PdfPageAnchorResolver.resolve("SCROLL_POSITION", 1, null))
        assertNull(MarkdownBlockAnchorResolver.resolve("PAGE_INDEX", 1, null))
        assertNull(MarkdownBlockAnchorResolver.resolve("ANCHOR", 1, null))
    }

    @Test
    fun `resolver registry maps kinds to implementations`() {
        assertEquals(PdfPageAnchorResolver, AnnotationAnchorResolvers.forLocationKind("PAGE_INDEX"))
        assertEquals(
            MarkdownBlockAnchorResolver,
            AnnotationAnchorResolvers.forLocationKind("SCROLL_POSITION"),
        )
        assertNull(AnnotationAnchorResolvers.forLocationKind("FUTURE"))
        assertEquals(
            PdfPageAnchorResolver,
            AnnotationAnchorResolvers.forAnchor(DocumentAnchor.PdfPage(page = 0)),
        )
        assertEquals(
            MarkdownBlockAnchorResolver,
            AnnotationAnchorResolvers.forAnchor(DocumentAnchor.MarkdownBlock(blockIndex = 0)),
        )
    }
}
