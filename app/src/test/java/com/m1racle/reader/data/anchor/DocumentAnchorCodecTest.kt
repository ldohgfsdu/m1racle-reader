package com.m1racle.reader.data.anchor

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * R24 锚点编解码契约：确定性、往返一致、非法输入安全返回 null。
 */
class DocumentAnchorCodecTest {

    @Test
    fun `encode decode round trip is identical for pdf page anchors`() {
        val wholePage = DocumentAnchor.PdfPage(page = 41)
        val withRect = DocumentAnchor.PdfPage(
            page = 77,
            rects = listOf(NormalizedRect(0.1f, 0.25f, 0.5f, 0.125f)),
        )
        listOf(wholePage, withRect).forEach { anchor ->
            val encoded = AnchorCodec.encode(anchor)
            repeat(3) { assertEquals(encoded, AnchorCodec.encode(anchor)) }
            assertEquals(anchor, AnchorCodec.decode(encoded))
        }
    }

    @Test
    fun `encode decode round trip is identical for markdown block anchors`() {
        val plain = DocumentAnchor.MarkdownBlock(blockIndex = 340)
        val refined = DocumentAnchor.MarkdownBlock(blockIndex = 12, charStart = 5, charEnd = 40)
        listOf(plain, refined).forEach { anchor ->
            val encoded = AnchorCodec.encode(anchor)
            repeat(3) { assertEquals(encoded, AnchorCodec.encode(anchor)) }
            assertEquals(anchor, AnchorCodec.decode(encoded))
        }
    }

    @Test
    fun `decode rejects malformed input without throwing`() {
        listOf(
            null,
            "",
            "  ",
            "pdf:",
            "pdf:x|0.1,0.2,0.3,0.4",
            "pdf:-1|-",
            "md:a|-",
            "unknown:1|-",
            "pdf:1|0.1,0.2",
            "md:1|5",
            "::",
        ).forEach { raw ->
            assertNull("expected null for '$raw'", AnchorCodec.decode(raw))
        }
    }

    @Test
    fun `normalized rect construction clamps into unit cube`() {
        val r = NormalizedRect(-0.5f, 0.2f, 1.7f, 0.4f).normalized()
        assertEquals(0f, r.x, 0f)
        assertEquals(1f, r.width, 0f)
        assertEquals(0.2f, r.y, 0f)
        assertEquals(0.4f, r.height, 0f)
    }

    @Test
    fun `normalized rect from pixel coordinates is scale invariant`() {
        // 同一视觉区域在两种渲染尺度下归一化结果必须完全一致。
        val base = NormalizedRect.fromPixel(100f, 250f, 300f, 80f, pageW = 1000f, pageH = 1414f)
        val scaled = NormalizedRect.fromPixel(200f, 500f, 600f, 160f, pageW = 2000f, pageH = 2828f)
        assertEquals(base.x, scaled.x, 1e-6f)
        assertEquals(base.y, scaled.y, 1e-6f)
        assertEquals(base.width, scaled.width, 1e-6f)
        assertEquals(base.height, scaled.height, 1e-6f)
    }
}
